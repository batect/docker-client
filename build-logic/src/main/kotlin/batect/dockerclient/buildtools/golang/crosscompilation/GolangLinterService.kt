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
import batect.dockerclient.buildtools.DownloadResult
import batect.dockerclient.buildtools.OperatingSystem
import batect.dockerclient.buildtools.checksums.ChecksumsFile
import batect.dockerclient.buildtools.checksums.verifyChecksum
import batect.dockerclient.buildtools.download
import batect.dockerclient.buildtools.extract
import org.gradle.api.Task
import org.gradle.api.services.BuildService
import org.gradle.api.services.BuildServiceParameters
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import kotlin.io.path.name

abstract class GolangLinterService : BuildService<BuildServiceParameters.None> {
    private val linters = ConcurrentHashMap<String, CompletableFuture<GolangLinter>>()

    fun getOrPrepareLinter(version: String, task: Task): CompletableFuture<GolangLinter> {
        return linters.computeIfAbsent(version) {
            prepareLinter(version, task)
        }
    }

    private fun prepareLinter(version: String, task: Task): CompletableFuture<GolangLinter> {
        val toolsDir = task.project.buildDir.toPath().resolve("tools").toAbsolutePath()
        val linterRoot = toolsDir.resolve("golangci-lint-$version")
        val downloadsDir = toolsDir.resolve("downloads")

        return downloadAndExtract(version, task, linterRoot, downloadsDir).thenApply {
            GolangLinter(linterRoot.resolve(executableName))
        }
    }

    private fun downloadAndExtract(version: String, task: Task, linterRoot: Path, downloadsDir: Path): CompletableFuture<Void> {
        val rootUrl = "https://github.com/golangci/golangci-lint/releases/download/v$version"
        val filePrefix = "golangci-lint-$version"

        val archiveFileName = "$filePrefix-${OperatingSystem.current.name.lowercase()}-${Architecture.current.golangName}.$archiveFileExtension"
        val archivePath = downloadsDir.resolve(archiveFileName)
        val checksumFileName = "$filePrefix-checksums.txt"
        val checksumPath = downloadsDir.resolve(checksumFileName)

        val archiveDownload = download(task, "$rootUrl/$archiveFileName", archivePath)
        val checksumDownload = download(task, "$rootUrl/$checksumFileName", checksumPath)

        return CompletableFuture.allOf(archiveDownload, checksumDownload).thenRun {
            val archiveDownloadResult = archiveDownload.get()
            val checksumDownloadResult = checksumDownload.get()

            if (archiveDownloadResult == DownloadResult.UpToDate && checksumDownloadResult == DownloadResult.UpToDate && Files.isDirectory(linterRoot)) {
                // Up to date, don't need to do anything further.
                return@thenRun
            }

            verifyArchiveChecksum(archivePath, checksumPath)

            val source = when (OperatingSystem.current) {
                OperatingSystem.Windows -> task.project.zipTree(archivePath)
                else -> task.project.tarTree(archivePath)
            }

            extract(task, source, linterRoot) {
                include("**/$executableName")
            }
        }
    }

    private fun verifyArchiveChecksum(archivePath: Path, checksumPath: Path) {
        val expectedChecksum = ChecksumsFile(checksumPath).checksumForFile(archivePath.name)

        verifyChecksum(archivePath, expectedChecksum)
    }

    private val executableName = when (OperatingSystem.current) {
        OperatingSystem.Windows -> "golangci-lint.exe"
        else -> "golangci-lint"
    }

    private val archiveFileExtension = when (OperatingSystem.current) {
        OperatingSystem.Windows -> "zip"
        else -> "tar.gz"
    }
}

data class GolangLinter(val binary: Path)
