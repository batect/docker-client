/*
    Copyright 2017-2021 Charles Korn.

    Licensed under the Apache License, Version 2.0 (the "License");
    you may not use this file except in compliance with the License.
    You may obtain a copy of the License at

        http://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and
    limitations under the License.
*/

package batect.dockerclient.buildtools.golang.crosscompilation

import batect.dockerclient.buildtools.BinaryType
import batect.dockerclient.buildtools.OperatingSystem
import batect.dockerclient.buildtools.golang.GolangPlugin
import batect.dockerclient.buildtools.golang.GolangPluginExtension
import batect.dockerclient.buildtools.zig.ZigPlugin
import batect.dockerclient.buildtools.zig.ZigPluginExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.provider.Provider
import org.gradle.kotlin.dsl.apply
import org.gradle.kotlin.dsl.create
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.withType
import org.gradle.process.internal.ExecActionFactory
import java.io.ByteArrayOutputStream
import java.io.File
import javax.inject.Inject

class GolangCrossCompilationPlugin @Inject constructor(private val execActionFactory: ExecActionFactory) : Plugin<Project> {
    override fun apply(target: Project) {
        target.apply<ZigPlugin>()
        target.apply<GolangPlugin>()

        val golangExtension = target.extensions.getByType<GolangPluginExtension>()
        val zigExtension = target.extensions.getByType<ZigPluginExtension>()
        val crossCompilationExtension = createExtension(target)

        target.tasks.withType<GolangBuild>() {
            sourceDirectory.convention(golangExtension.sourceDirectory)
            golangCompilerExecutablePath.convention(golangExtension.golangCompilerExecutablePath)
            zigCompilerExecutablePath.convention(zigExtension.zigCompilerExecutablePath)
            macOSSystemRoot.convention(crossCompilationExtension.macOSSystemRootDirectory)

            outputDirectory.convention(
                crossCompilationExtension.outputDirectory.map { outputDirectory ->
                    outputDirectory
                        .dir(targetOperatingSystem.map { it.name.lowercase() }).get()
                        .dir(targetArchitecture.map { it.name.lowercase() }).get()
                        .dir(targetBinaryType.map { it.name.lowercase() }).get()
                }
            )

            zigCacheDirectory.convention(
                target.layout.buildDirectory.map { buildDirectory ->
                    buildDirectory
                        .dir("zig")
                        .dir("cache")
                        .dir(this.name)
                }
            )

            baseOutputName.convention(
                targetOperatingSystem.zip(libraryName) { targetOperatingSystem, libraryName ->
                    when (targetOperatingSystem) {
                        OperatingSystem.Windows -> libraryName
                        OperatingSystem.Linux, OperatingSystem.Darwin -> "lib$libraryName"
                        else -> throw IllegalArgumentException("Unknown operating system: $targetOperatingSystem")
                    }
                }
            )

            outputLibraryFile.convention(
                targetBinaryType.flatMap { targetBinaryType ->
                    val fileName: Provider<String> = when (targetBinaryType) {
                        BinaryType.Shared -> targetOperatingSystem.flatMap { targetOperatingSystem ->
                            val extension = when (targetOperatingSystem) {
                                OperatingSystem.Windows -> "dll"
                                OperatingSystem.Linux -> "so"
                                OperatingSystem.Darwin -> "dylib"
                                else -> throw IllegalArgumentException("Unknown operating system: $targetOperatingSystem")
                            }

                            baseOutputName.map { "$it.$extension" }
                        }

                        BinaryType.Archive -> libraryName.map { "$it.a" } // Yes, this should be .lib on Windows, but having everything with .a makes the cinterop configuration for Kotlin much easier.
                        else -> throw IllegalArgumentException("Unknown binary type: $targetBinaryType")
                    }

                    outputDirectory.zip(fileName) { outputDirectory, name -> outputDirectory.file(name) }
                }
            )

            outputHeaderFile.convention(outputDirectory.file(libraryName.map { "$it.h" }))
        }
    }

    private fun createExtension(target: Project): GolangCrossCompilationPluginExtension {
        val extension = target.extensions.create<GolangCrossCompilationPluginExtension>("golangCrossCompilation")
        extension.outputDirectory.convention(target.layout.buildDirectory.dir("libs"))
        extension.macOSSystemRootDirectory.convention(target.layout.dir(target.provider { findMacOSSystemRoot() }))

        return extension
    }

    private fun findMacOSSystemRoot(): File? {
        if (OperatingSystem.current != OperatingSystem.Darwin) {
            return null
        }

        val action = execActionFactory.newExecAction()
        val outputStream = ByteArrayOutputStream()
        action.commandLine = listOf("xcrun", "--show-sdk-path")
        action.standardOutput = outputStream
        action.errorOutput = outputStream

        val result = action.execute()
        result.assertNormalExitValue()

        return File(outputStream.toString(Charsets.UTF_8).trim())
    }
}
