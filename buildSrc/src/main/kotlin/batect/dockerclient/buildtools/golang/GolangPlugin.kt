/*
    Copyright 2017-2021 Charles Korn.

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

package batect.dockerclient.buildtools.golang

import batect.dockerclient.buildtools.Architecture
import batect.dockerclient.buildtools.OperatingSystem
import de.undercouch.gradle.tasks.download.Download
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.file.RelativePath
import org.gradle.api.tasks.Sync
import org.gradle.kotlin.dsl.create
import org.gradle.kotlin.dsl.register

class GolangPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        val extension = createExtension(target)

        registerGolangDownloadTasks(target, extension)
        registerLintDownloadTasks(target, extension)
        registerCleanTask(target, extension)
    }

    private fun createExtension(target: Project): GolangPluginExtension {
        val extension = target.extensions.create<GolangPluginExtension>("golang")

        extension.sourceDirectory.convention(target.layout.projectDirectory.dir("src"))

        return extension
    }

    private fun registerGolangDownloadTasks(target: Project, extension: GolangPluginExtension) {
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

    private fun registerLintDownloadTasks(target: Project, extension: GolangPluginExtension) {
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

    private fun registerCleanTask(target: Project, extension: GolangPluginExtension) {
        target.tasks.register<GolangCacheClean>("cleanGolangCache") {
            golangCompilerExecutablePath.set(extension.golangCompilerExecutablePath)
        }
    }

    private val archiveFileExtension = when (OperatingSystem.current) {
        OperatingSystem.Windows -> "zip"
        else -> "tar.gz"
    }
}
