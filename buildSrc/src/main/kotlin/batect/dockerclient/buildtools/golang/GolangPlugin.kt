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

package batect.dockerclient.buildtools.golang

import batect.dockerclient.buildtools.Architecture
import batect.dockerclient.buildtools.OperatingSystem
import de.undercouch.gradle.tasks.download.Download
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.file.RelativePath
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.Sync
import org.gradle.kotlin.dsl.create
import org.gradle.kotlin.dsl.register
import java.io.File

class GolangPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        val extension = createExtension(target)

        registerGolangDownloadTasks(target, extension)

        val linterExecutable = registerLintDownloadTasks(target, extension)
        registerLintTask(target, extension, linterExecutable)
    }

    private fun registerGolangDownloadTasks(target: Project, extension: GolangPluginExtension) {
        val archiveFileName = extension.golangVersion.map { "go$it.${OperatingSystem.current.name.lowercase()}-${Architecture.current.golangName}.$archiveFileExtension" }
        val rootUrl = "https://dl.google.com/go"

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

        target.tasks.register<Sync>("extractGolang") {
            dependsOn(downloadArchive)
            dependsOn(verifyChecksum)

            val targetDirectory = target.layout.buildDirectory.dir(extension.golangVersion.map { "tools/golang-$it" })

            // Gradle always reads the entire archive, even if it hasn't changed, which can be quite time-consuming -
            // this allows us to skip that if we're 90% sure it's not necessary.
            onlyIf { downloadArchive.get().didWork || !targetDirectory.get().asFile.exists() }

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
    }

    private fun createExtension(target: Project): GolangPluginExtension {
        val extension = target.extensions.create<GolangPluginExtension>("golang")

        extension.sourceDirectory.convention(target.layout.projectDirectory.dir("src"))

        return extension
    }

    private fun registerLintDownloadTasks(target: Project, extension: GolangPluginExtension): Provider<File> {
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

        return extractExecutable.map { it.outputs.files.singleFile.resolve(executableName) }
    }

    private fun registerLintTask(target: Project, extension: GolangPluginExtension, executable: Provider<File>) {
        target.tasks.register<GolangLint>("lint") {
            executablePath.set(executable)
            sourceDirectory.set(extension.sourceDirectory)
            upToDateCheckFilePath.set(target.layout.buildDirectory.file("lint/upToDate"))
        }
    }

    private val archiveFileExtension = when (OperatingSystem.current) {
        OperatingSystem.Windows -> "zip"
        else -> "tar.gz"
    }
}
