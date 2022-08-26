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

import batect.dockerclient.buildtools.Architecture
import batect.dockerclient.buildtools.BinaryType
import batect.dockerclient.buildtools.OperatingSystem
import batect.dockerclient.buildtools.zig.ZigPlugin
import batect.dockerclient.buildtools.zig.ZigPluginExtension
import de.undercouch.gradle.tasks.download.Download
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.file.RelativePath
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.Sync
import org.gradle.kotlin.dsl.apply
import org.gradle.kotlin.dsl.create
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.register
import org.gradle.kotlin.dsl.withType
import org.gradle.process.internal.ExecActionFactory
import javax.inject.Inject
import kotlin.reflect.jvm.jvmName

class GolangCrossCompilationPlugin @Inject constructor(private val execActionFactory: ExecActionFactory) : Plugin<Project> {
    override fun apply(target: Project) {
        target.apply<ZigPlugin>()

        val zigExtension = target.extensions.getByType<ZigPluginExtension>()
        val golangExtension = createExtension(target)
        val environmentServiceProvider = registerEnvironmentService(target)

        configureTaskDefaults(target, golangExtension, zigExtension, environmentServiceProvider)
        configureGolangBuildTaskDefaults(target, golangExtension)
        registerCleanTask(target)
        registerLintDownloadTasks(target, golangExtension)
        registerLintTask(target, golangExtension)
    }

    private fun createExtension(target: Project): GolangCrossCompilationPluginExtension {
        val extension = target.extensions.create<GolangCrossCompilationPluginExtension>("golang")
        extension.sourceDirectory.convention(target.layout.projectDirectory.dir("src"))
        extension.outputDirectory.convention(target.layout.buildDirectory.dir("libs"))

        return extension
    }

    private fun registerEnvironmentService(target: Project): Provider<GolangCrossCompilationEnvironmentService> {
        return target.gradle.sharedServices.registerIfAbsent(
            GolangCrossCompilationEnvironmentService::class.jvmName,
            GolangCrossCompilationEnvironmentService::class.java
        ) {}
    }

    private fun configureTaskDefaults(
        target: Project,
        golangExtension: GolangCrossCompilationPluginExtension,
        zigExtension: ZigPluginExtension,
        environmentServiceProvider: Provider<GolangCrossCompilationEnvironmentService>
    ) {
        target.tasks.withType<GolangCrossCompilationTask>().configureEach { task ->
            task.usesService(environmentServiceProvider)
            task.environmentService.set(environmentServiceProvider)

            task.golangVersion.set(golangExtension.golangVersion)
            task.zigVersion.set(zigExtension.zigVersion)
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

    private fun registerLintTask(target: Project, extension: GolangCrossCompilationPluginExtension) {
        target.tasks.register<GolangLint>("lint") {
            executablePath.set(extension.linterExecutablePath)
            sourceDirectory.set(extension.sourceDirectory)
            systemPath.convention(target.providers.environmentVariable("PATH"))
            upToDateCheckFilePath.set(target.layout.buildDirectory.file("lint/upToDate"))
        }
    }

    private fun registerLintDownloadTasks(target: Project, extension: GolangCrossCompilationPluginExtension) {
        val extensionProvider = target.provider { extension }
        val rootUrl = extension.golangCILintVersion.map { "https://github.com/golangci/golangci-lint/releases/download/v$it" }
        val filePrefix = extension.golangCILintVersion.map { "golangci-lint-$it" }

        val downloadArchive = target.tasks.register<Download>("downloadGolangCILintArchive") {
            val archiveFileName = filePrefix.map { "$it-${OperatingSystem.current.name.lowercase()}-${Architecture.current.golangName}.$archiveFileExtension" }

            src(extensionProvider.map { "${rootUrl.get()}/${archiveFileName.get()}" })
            dest(target.layout.buildDirectory.file(archiveFileName.map { "tools/downloads/${this.name}/$it" }))
            overwrite(false)
        }

        val downloadChecksumFile = target.tasks.register<Download>("downloadGolangCILintChecksum") {
            val checksumFileName = filePrefix.map { "$it-checksums.txt" }

            src(extensionProvider.map { "${rootUrl.get()}/${checksumFileName.get()}" })
            dest(target.layout.buildDirectory.file(checksumFileName.map { "tools/downloads/${this.name}/$it" }))
            overwrite(false)
        }

        val verifyChecksum = target.tasks.register<VerifyChecksumFromMultiChecksumFile>("verifyGolangCILintChecksum") {
            checksumFile.set(target.layout.file(downloadChecksumFile.map { it.dest }))
            fileToVerify.set(target.layout.file(downloadArchive.map { it.dest }))
        }

        val executableName = when (OperatingSystem.current) {
            OperatingSystem.Windows -> "golangci-lint.exe"
            else -> "golangci-lint"
        }

        val extractExecutable = target.tasks.register<Sync>("extractGolangCILintExecutable") {
            dependsOn(verifyChecksum)

            val source = when (OperatingSystem.current) {
                OperatingSystem.Windows -> target.zipTree(downloadArchive.map { it.dest })
                else -> target.tarTree(downloadArchive.map { it.dest })
            }

            from(source) {
                include("**/$executableName")

                eachFile {
                    it.relativePath = RelativePath(true, *it.relativePath.segments.takeLast(1).toTypedArray())
                }

                includeEmptyDirs = false
            }

            val targetDirectory = target.layout.buildDirectory.dir(extension.golangCILintVersion.map { "tools/golangci-lint-$it" })

            into(targetDirectory)
        }

        extension.linterExecutablePath.set(target.layout.file(extractExecutable.map { it.outputs.files.singleFile.resolve(executableName) }))
    }

    private fun registerCleanTask(target: Project) {
        target.tasks.register<GolangCacheClean>("cleanGolangCache")
    }

    private val archiveFileExtension = when (OperatingSystem.current) {
        OperatingSystem.Windows -> "zip"
        else -> "tar.gz"
    }
}
