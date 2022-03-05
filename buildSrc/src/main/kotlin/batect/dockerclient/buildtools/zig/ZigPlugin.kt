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

package batect.dockerclient.buildtools.zig

import batect.dockerclient.buildtools.Architecture
import batect.dockerclient.buildtools.OperatingSystem
import de.undercouch.gradle.tasks.download.Download
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.file.RelativePath
import org.gradle.api.internal.file.FileResolver
import org.gradle.api.internal.resources.DefaultResourceResolver
import org.gradle.api.tasks.Sync
import org.gradle.internal.nativeintegration.filesystem.FileSystem
import org.gradle.kotlin.dsl.create
import org.gradle.kotlin.dsl.register
import javax.inject.Inject

class ZigPlugin @Inject constructor(fileResolver: FileResolver, fileSystem: FileSystem) : Plugin<Project> {
    private val resourceResolver = DefaultResourceResolver(fileResolver, fileSystem)

    override fun apply(target: Project) {
        val extension = createExtension(target)

        registerDownloadTasks(target, extension)
    }

    private fun createExtension(target: Project): ZigPluginExtension {
        return target.extensions.create("zig")
    }

    private fun registerDownloadTasks(target: Project, extension: ZigPluginExtension) {
        val archiveFileExtension = when (OperatingSystem.current) {
            OperatingSystem.Windows -> "zip"
            else -> "tar.xz"
        }

        val platformName = "${OperatingSystem.current.zigName}-${Architecture.current.zigName}"
        val archiveFileName = extension.zigVersion.map { "zig-$platformName-$it.$archiveFileExtension" }
        val downloadUrl = extension.zigVersion.map { "https://ziglang.org/download/$it/${archiveFileName.get()}" }

        val downloadArchive = target.tasks.register<Download>("downloadZigArchive") {
            src(downloadUrl)
            dest(target.layout.buildDirectory.file(archiveFileName.map { "tools/downloads/${this.name}/$it" }))
            overwrite(false)
        }

        val downloadChecksumFile = target.tasks.register<Download>("downloadZigChecksum") {
            src("https://ziglang.org/download/index.json")
            dest(target.layout.buildDirectory.file(extension.zigVersion.map { "tools/downloads/${this.name}/$it/index.json" }))
            overwrite(false)
        }

        val verifyChecksum = target.tasks.register<VerifyZigChecksum>("verifyZigChecksum") {
            checksumFile.set(target.layout.file(downloadChecksumFile.map { it.dest }))
            fileToVerify.set(target.layout.file(downloadArchive.map { it.dest }))
            zigVersion.set(extension.zigVersion)
            zigPlatformName.set("${Architecture.current.zigName}-${OperatingSystem.current.zigName}")
        }

        target.tasks.register<Sync>("extractZig") {
            dependsOn(downloadArchive)
            dependsOn(verifyChecksum)

            val targetDirectory = target.layout.buildDirectory.dir(extension.zigVersion.map { "tools/zig-$it" })

            // Gradle always reads the entire archive, even if it hasn't changed, which can be quite time-consuming -
            // this allows us to skip that if we're 90% sure it's not necessary.
            onlyIf { downloadArchive.get().didWork || !targetDirectory.get().asFile.exists() }

            val source = when (OperatingSystem.current) {
                OperatingSystem.Windows -> target.zipTree(downloadArchive.map { it.dest })
                else -> target.tarTree(XzArchiver(resourceResolver.resolveResource(downloadArchive.map { it.dest })))
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
}
