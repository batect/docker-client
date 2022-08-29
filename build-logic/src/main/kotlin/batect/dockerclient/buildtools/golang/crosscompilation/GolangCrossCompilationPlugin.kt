/*
    Copyright 2017-2022 Charles Korn.

    Licensed under the Apache License, Version 2.0 (the "License");
    you may not use this file except in compliance with the License.
    You may obtain a copy of the License at

        https://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and
    limitations under the License.
*/

package batect.dockerclient.buildtools.golang.crosscompilation

import batect.dockerclient.buildtools.BinaryType
import batect.dockerclient.buildtools.OperatingSystem
import batect.dockerclient.buildtools.zig.ZigEnvironmentService
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.provider.Provider
import org.gradle.kotlin.dsl.create
import org.gradle.kotlin.dsl.register
import org.gradle.kotlin.dsl.withType
import kotlin.reflect.jvm.jvmName

class GolangCrossCompilationPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        val extension = createExtension(target)
        val golangEnvironmentServiceProvider = registerGolangEnvironmentService(target)
        val zigEnvironmentServiceProvider = registerZigEnvironmentService(target)
        val linterServiceProvider = registerLinterService(target)

        configureTaskDefaults(target, extension, golangEnvironmentServiceProvider, zigEnvironmentServiceProvider)
        configureGolangBuildTaskDefaults(target, extension)
        registerCleanTask(target)
        registerLintTask(target, extension, linterServiceProvider)
    }

    private fun createExtension(target: Project): GolangCrossCompilationPluginExtension {
        val extension = target.extensions.create<GolangCrossCompilationPluginExtension>("golang")
        extension.sourceDirectory.convention(target.layout.projectDirectory.dir("src"))
        extension.outputDirectory.convention(target.layout.buildDirectory.dir("libs"))

        return extension
    }

    private fun registerGolangEnvironmentService(target: Project): Provider<GolangEnvironmentService> {
        return target.gradle.sharedServices.registerIfAbsent(
            GolangEnvironmentService::class.jvmName,
            GolangEnvironmentService::class.java
        ) {}
    }

    private fun registerZigEnvironmentService(target: Project): Provider<ZigEnvironmentService> {
        return target.gradle.sharedServices.registerIfAbsent(
            ZigEnvironmentService::class.jvmName,
            ZigEnvironmentService::class.java
        ) {}
    }

    private fun registerLinterService(target: Project): Provider<GolangLinterService> {
        return target.gradle.sharedServices.registerIfAbsent(
            GolangLinterService::class.jvmName,
            GolangLinterService::class.java
        ) {}
    }

    private fun configureTaskDefaults(
        target: Project,
        extension: GolangCrossCompilationPluginExtension,
        golangEnvironmentServiceProvider: Provider<GolangEnvironmentService>,
        zigEnvironmentServiceProvider: Provider<ZigEnvironmentService>
    ) {
        target.tasks.withType<GolangTask>().configureEach { task ->
            task.usesService(golangEnvironmentServiceProvider)
            task.golangEnvironmentService.set(golangEnvironmentServiceProvider)
            task.golangVersion.set(extension.golangVersion)
        }

        target.tasks.withType<GolangCrossCompilationTask>().configureEach { task ->
            task.usesService(zigEnvironmentServiceProvider)
            task.zigEnvironmentService.set(zigEnvironmentServiceProvider)
            task.zigVersion.set(extension.zigVersion)
        }
    }

    private fun configureGolangBuildTaskDefaults(
        target: Project,
        golangExtension: GolangCrossCompilationPluginExtension
    ) {
        target.tasks.withType<GolangBuild>().configureEach { task ->
            task.sourceDirectory.convention(golangExtension.sourceDirectory)

            task.outputDirectory.convention(
                golangExtension.outputDirectory.map { outputDirectory ->
                    outputDirectory
                        .dir(task.targetOperatingSystem.map { it.name.lowercase() }).get()
                        .dir(task.targetArchitecture.map { it.name.lowercase() }).get()
                        .dir(task.targetBinaryType.map { it.name.lowercase() }).get()
                }
            )

            task.baseOutputName.convention(
                task.targetOperatingSystem.zip(task.libraryName) { targetOperatingSystem, libraryName ->
                    when (targetOperatingSystem) {
                        OperatingSystem.Windows -> libraryName
                        OperatingSystem.Linux, OperatingSystem.Darwin -> "lib$libraryName"
                        else -> throw IllegalArgumentException("Unknown operating system: $targetOperatingSystem")
                    }
                }
            )

            task.outputLibraryFile.convention(
                task.targetBinaryType.flatMap { targetBinaryType ->
                    val fileName: Provider<String> = when (targetBinaryType) {
                        BinaryType.Shared -> task.targetOperatingSystem.flatMap { targetOperatingSystem ->
                            val extension = when (targetOperatingSystem) {
                                OperatingSystem.Windows -> "dll"
                                OperatingSystem.Linux -> "so"
                                OperatingSystem.Darwin -> "dylib"
                                else -> throw IllegalArgumentException("Unknown operating system: $targetOperatingSystem")
                            }

                            task.baseOutputName.map { "$it.$extension" }
                        }

                        BinaryType.Archive -> task.libraryName.map { "$it.a" } // Yes, this should be .lib on Windows, but having everything with .a makes the cinterop configuration for Kotlin much easier.
                        else -> throw IllegalArgumentException("Unknown binary type: $targetBinaryType")
                    }

                    task.outputDirectory.zip(fileName) { outputDirectory, name -> outputDirectory.file(name) }
                }
            )

            task.outputHeaderFile.convention(task.outputDirectory.file(task.libraryName.map { "$it.h" }))
        }
    }

    private fun registerLintTask(target: Project, extension: GolangCrossCompilationPluginExtension, linterServiceProvider: Provider<GolangLinterService>) {
        target.tasks.register<GolangLint>("lint") {
            this.usesService(linterServiceProvider)
            this.linterService.set(linterServiceProvider)

            linterVersion.set(extension.golangCILintVersion)
            sourceDirectory.set(extension.sourceDirectory)
            systemPath.convention(target.providers.environmentVariable("PATH"))
            upToDateCheckFilePath.set(target.layout.buildDirectory.file("lint/upToDate"))
        }
    }

    private fun registerCleanTask(target: Project) {
        target.tasks.register<GolangCacheClean>("cleanGolangCache")
    }
}
