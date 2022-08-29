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

package batect.dockerclient.buildtools.zig

import batect.dockerclient.buildtools.Architecture
import batect.dockerclient.buildtools.DownloadResult
import batect.dockerclient.buildtools.OperatingSystem
import batect.dockerclient.buildtools.checksums.verifyChecksum
import batect.dockerclient.buildtools.download
import batect.dockerclient.buildtools.extract
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.internal.file.FileResolver
import org.gradle.api.internal.resources.DefaultResourceResolver
import org.gradle.api.services.BuildService
import org.gradle.api.services.BuildServiceParameters
import org.gradle.configurationcache.extensions.serviceOf
import org.gradle.internal.nativeintegration.filesystem.FileSystem
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap

abstract class ZigEnvironmentService : BuildService<BuildServiceParameters.None> {
    private val environments = ConcurrentHashMap<String, CompletableFuture<ZigEnvironment>>()

    fun getOrPrepareEnvironment(version: String, task: Task, toolsDir: Path, downloadsDir: Path): CompletableFuture<ZigEnvironment> {
        return environments.computeIfAbsent(version) {
            prepareEnvironment(version, task, toolsDir, downloadsDir)
        }
    }

    private fun prepareEnvironment(version: String, task: Task, toolsDir: Path, downloadsDir: Path): CompletableFuture<ZigEnvironment> {
        val zigRoot = toolsDir.resolve("zig-$version")

        val executableName = when (OperatingSystem.current) {
            OperatingSystem.Windows -> "zig.exe"
            else -> "zig"
        }

        return downloadAndExtract(version, task, zigRoot, downloadsDir).thenApply {
            ZigEnvironment(zigRoot, zigRoot.resolve(executableName))
        }
    }

    private fun downloadAndExtract(version: String, task: Task, zigRoot: Path, downloadsDir: Path): CompletableFuture<Void> {
        val rootUrl = "https://ziglang.org/download"
        val platformName = "${OperatingSystem.current.zigName}-${Architecture.current.zigName}"
        val archiveFileName = "zig-$platformName-$version.$archiveFileExtension"
        val archivePath = downloadsDir.resolve(archiveFileName)
        val checksumFileName = "$archiveFileName.checksums.json"
        val checksumPath = downloadsDir.resolve(checksumFileName)

        val archiveDownload = download(task, "$rootUrl/$version/$archiveFileName", archivePath)
        val checksumDownload = download(task, "$rootUrl/index.json", checksumPath)

        return CompletableFuture.allOf(archiveDownload, checksumDownload).thenRun {
            val archiveDownloadResult = archiveDownload.get()
            val checksumDownloadResult = checksumDownload.get()

            if (archiveDownloadResult == DownloadResult.UpToDate && checksumDownloadResult == DownloadResult.UpToDate && Files.isDirectory(zigRoot)) {
                // Up to date, don't need to do anything further.
                return@thenRun
            }

            verifyArchiveChecksum(archivePath, checksumPath, version)

            val source = when (OperatingSystem.current) {
                OperatingSystem.Windows -> task.project.zipTree(archivePath)
                else -> task.project.tarTree(XzArchiver(task.project.resourceResolver.resolveResource(archivePath)))
            }

            extract(task, source, zigRoot)
        }
    }

    private fun verifyArchiveChecksum(archivePath: Path, checksumPath: Path, version: String) {
        val expectedChecksum = findExpectedChecksum(checksumPath, version)

        verifyChecksum(archivePath, expectedChecksum)
    }

    private fun findExpectedChecksum(checksumPath: Path, version: String): String {
        val platformName = "${Architecture.current.zigName}-${OperatingSystem.current.zigName}"

        return ZigChecksumsFile(checksumPath).archiveChecksumForVersionAndPlatform(version, platformName)
    }

    private val archiveFileExtension = when (OperatingSystem.current) {
        OperatingSystem.Windows -> "zip"
        else -> "tar.xz"
    }

    private val Project.resourceResolver: DefaultResourceResolver
        get() {
            val fileResolver = this.serviceOf<FileResolver>()
            val fileSystem = this.serviceOf<FileSystem>()

            return DefaultResourceResolver(fileResolver, fileSystem)
        }
}

data class ZigEnvironment(val root: Path, val compiler: Path)
