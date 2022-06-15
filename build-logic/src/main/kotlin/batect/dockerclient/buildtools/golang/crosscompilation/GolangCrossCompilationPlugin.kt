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
import java.io.ByteArrayOutputStream
import java.io.File
import javax.inject.Inject

class GolangCrossCompilationPlugin @Inject constructor(private val execActionFactory: ExecActionFactory) : Plugin<Project> {
    override fun apply(target: Project) {
        target.apply<ZigPlugin>()

        val zigExtension = target.extensions.getByType<ZigPluginExtension>()
        val golangExtension = createExtension(target)
        val environmentVariablesProvider = GolangCrossCompilationEnvironmentVariablesProvider(golangExtension, zigExtension, target)

        registerGolangDownloadTasks(target, golangExtension)
        configureGolangBuildTaskDefaults(target, golangExtension, environmentVariablesProvider)
        registerCleanTask(target, golangExtension)

        registerLintDownloadTasks(target, golangExtension)
        configureLintingTaskDefaults(target, environmentVariablesProvider)
        registerLintTask(target, golangExtension)
    }

    private fun createExtension(target: Project): GolangCrossCompilationPluginExtension {
        val extension = target.extensions.create<GolangCrossCompilationPluginExtension>("golang")
        extension.sourceDirectory.convention(target.layout.projectDirectory.dir("src"))
        extension.outputDirectory.convention(target.layout.buildDirectory.dir("libs"))
        extension.macOSSystemRootDirectory.convention(target.layout.dir(target.provider { macOSSystemRoot }))

        extension.rootZigCacheDirectory.convention(
            target.layout.buildDirectory.map { buildDirectory ->
                buildDirectory
                    .dir("zig")
                    .dir("cache")
            }
        )

        return extension
    }

    private fun configureGolangBuildTaskDefaults(
        target: Project,
        golangExtension: GolangCrossCompilationPluginExtension,
        environmentVariablesProvider: GolangCrossCompilationEnvironmentVariablesProvider
    ) {
        target.tasks.withType<GolangBuild>() {
            sourceDirectory.convention(golangExtension.sourceDirectory)
            golangCompilerExecutablePath.convention(golangExtension.golangCompilerExecutablePath)

            outputDirectory.convention(
                golangExtension.outputDirectory.map { outputDirectory ->
                    outputDirectory
                        .dir(targetOperatingSystem.map { it.name.lowercase() }).get()
                        .dir(targetArchitecture.map { it.name.lowercase() }).get()
                        .dir(targetBinaryType.map { it.name.lowercase() }).get()
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

            environmentVariablesProvider.configureEnvironmentVariablesForTarget(
                compilationEnvironmentVariables,
                targetOperatingSystem,
                targetArchitecture,
                this.name
            )
        }
    }

    private fun configureLintingTaskDefaults(
        target: Project,
        environmentVariablesProvider: GolangCrossCompilationEnvironmentVariablesProvider
    ) {
        target.tasks.withType<GolangLint> {
            environmentVariablesProvider.configureEnvironmentVariablesForTarget(
                additionalEnvironmentVariables,
                project.provider { OperatingSystem.current },
                project.provider { Architecture.current },
                this.name
            )

            systemPath.convention(target.providers.environmentVariable("PATH"))
        }
    }

    private fun registerLintTask(target: Project, extension: GolangCrossCompilationPluginExtension) {
        target.tasks.register<GolangLint>("lint") {
            executablePath.set(extension.linterExecutablePath)
            sourceDirectory.set(extension.sourceDirectory)
            goRootDirectory.set(extension.golangRoot)
            upToDateCheckFilePath.set(target.layout.buildDirectory.file("lint/upToDate"))
        }
    }

    private fun registerGolangDownloadTasks(target: Project, extension: GolangCrossCompilationPluginExtension) {
        val rootUrl = "https://dl.google.com/go"
        val archiveFileName = extension.golangVersion
            .map { "go$it.${OperatingSystem.current.name.lowercase()}-${Architecture.current.golangName}.$archiveFileExtension" }

        val downloadArchive = target.tasks.register<Download>("downloadGolangArchive") {
            src(archiveFileName.map { "$rootUrl/$it" })
            dest(target.layout.buildDirectory.file(archiveFileName.map { "tools/downloads/${this.name}/$it" }))
            overwrite(false)
        }

        val downloadChecksumFile = target.tasks.register<Download>("downloadGolangChecksum") {
            val checksumFileName = archiveFileName.map { "$it.sha256" }

            src(checksumFileName.map { "$rootUrl/$it" })
            dest(target.layout.buildDirectory.file(checksumFileName.map { "tools/downloads/${this.name}/$it" }))
            overwrite(false)
        }

        val verifyChecksum = target.tasks.register<VerifyChecksumFromSingleChecksumFile>("verifyGolangChecksum") {
            checksumFile.set(target.layout.file(downloadChecksumFile.map { it.dest }))
            fileToVerify.set(target.layout.file(downloadArchive.map { it.dest }))
        }

        val extractGolang = target.tasks.register<Sync>("extractGolang") {
            dependsOn(downloadArchive)
            dependsOn(verifyChecksum)

            val targetDirectory = target.layout.buildDirectory.dir(extension.golangVersion.map { "tools/golang-$it" })

            // Gradle always reads the entire archive, even if it hasn't changed, which can be quite time-consuming, especially if Sophos is active -
            // this allows us to skip that if we're 90% sure it's not necessary.
            // See https://gradle-community.slack.com/archives/CALL1EXGT/p1646637432358399?thread_ts=1646520443.914959&cid=CALL1EXGT for further discussion.
            onlyIf { downloadArchive.get().didWork || !targetDirectory.get().asFile.exists() }
            doNotTrackState("Tracking state takes 80+ seconds on my machine, workaround in place")

            val source = when (OperatingSystem.current) {
                OperatingSystem.Windows -> target.zipTree(downloadArchive.map { it.dest })
                else -> target.tarTree(downloadArchive.map { it.dest })
            }

            from(source) {
                eachFile {
                    it.relativePath = RelativePath(true, *it.relativePath.segments.drop(1).toTypedArray())
                }

                includeEmptyDirs = false
            }

            into(targetDirectory)
        }

        val executableName = when (OperatingSystem.current) {
            OperatingSystem.Windows -> "go.exe"
            else -> "go"
        }

        extension.golangRoot.set(target.layout.dir(extractGolang.map { it.destinationDir }))
        extension.golangCompilerExecutablePath.set(extension.golangRoot.map { it.dir("bin").file(executableName) })
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

    private fun registerCleanTask(target: Project, extension: GolangCrossCompilationPluginExtension) {
        target.tasks.register<GolangCacheClean>("cleanGolangCache") {
            golangCompilerExecutablePath.set(extension.golangCompilerExecutablePath)
        }
    }

    private val archiveFileExtension = when (OperatingSystem.current) {
        OperatingSystem.Windows -> "zip"
        else -> "tar.gz"
    }

    private val macOSSystemRoot: File? by lazy { findMacOSSystemRoot() }

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
