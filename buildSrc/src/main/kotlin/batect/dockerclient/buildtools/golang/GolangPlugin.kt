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
import org.gradle.api.tasks.Copy
import org.gradle.kotlin.dsl.create
import org.gradle.kotlin.dsl.register
import java.io.File

class GolangPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        val extension = createExtension(target)

        val linterExecutable = registerLintDownloadTasks(target, extension)
        registerLintTask(target, extension, linterExecutable)
    }

    private fun createExtension(target: Project): GolangPluginExtension {
        val extension = target.extensions.create<GolangPluginExtension>("golang")

        extension.sourceDirectory.convention(target.layout.projectDirectory.dir("src"))

        return extension
    }

    private fun registerLintDownloadTasks(target: Project, extension: GolangPluginExtension): Provider<File> {
        val extensionProvider = target.provider { extension }
        val rootUrl = extension.golangCILintToolVersion.map { "https://github.com/golangci/golangci-lint/releases/download/$it" }
        val filePrefix = extension.golangCILintToolVersion.map { "golangci-lint-${it.removePrefix("v")}" }

        val downloadArchive = target.tasks.register<Download>("downloadGolangCILintArchive") {
            val archiveFileExtension = when (OperatingSystem.current) {
                OperatingSystem.Windows -> "zip"
                else -> "tar.gz"
            }

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

        val extractExecutable = target.tasks.register<Copy>("extractGolangCILintExecutable") {
            dependsOn(verifyChecksum)

            from(target.tarTree(downloadArchive.map { it.dest })) {
                include("**/$executableName")

                eachFile {
                    it.relativePath = RelativePath(true, *it.relativePath.segments.takeLast(1).toTypedArray())
                }

                includeEmptyDirs = false
            }

            into(target.layout.buildDirectory.dir("tools/bin/golangci-lint"))
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
}
