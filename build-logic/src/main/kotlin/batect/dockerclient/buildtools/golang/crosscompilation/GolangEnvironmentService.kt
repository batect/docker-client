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
import kotlin.io.path.readText

abstract class GolangEnvironmentService : BuildService<BuildServiceParameters.None> {
    private val environments = ConcurrentHashMap<String, CompletableFuture<GolangEnvironment>>()

    fun getOrPrepareEnvironment(version: String, task: Task): CompletableFuture<GolangEnvironment> {
        return environments.computeIfAbsent(version) {
            prepareEnvironment(version, task)
        }
    }

    private fun prepareEnvironment(version: String, task: Task): CompletableFuture<GolangEnvironment> {
        val toolsDir = task.project.buildDir.toPath().resolve("tools").toAbsolutePath()
        val goRoot = toolsDir.resolve("golang-$version")
        val downloadsDir = toolsDir.resolve("downloads")

        val executableName = when (OperatingSystem.current) {
            OperatingSystem.Windows -> "go.exe"
            else -> "go"
        }

        return downloadAndExtract(version, task, goRoot, downloadsDir).thenApply {
            GolangEnvironment(goRoot, goRoot.resolve("bin").resolve(executableName))
        }
    }

    private fun downloadAndExtract(version: String, task: Task, goRoot: Path, downloadsDir: Path): CompletableFuture<Void> {
        val rootUrl = "https://dl.google.com/go"
        val archiveFileName = "go$version.${OperatingSystem.current.name.lowercase()}-${Architecture.current.golangName}.$golangArchiveFileExtension"
        val archivePath = downloadsDir.resolve(archiveFileName)
        val checksumFileName = "$archiveFileName.sha256"
        val checksumPath = downloadsDir.resolve(checksumFileName)

        val archiveDownload = download(task, "$rootUrl/$archiveFileName", archivePath)
        val checksumDownload = download(task, "$rootUrl/$checksumFileName", checksumPath)

        return CompletableFuture.allOf(archiveDownload, checksumDownload).thenRun {
            val archiveDownloadResult = archiveDownload.get()
            val checksumDownloadResult = checksumDownload.get()

            if (archiveDownloadResult == DownloadResult.UpToDate && checksumDownloadResult == DownloadResult.UpToDate && Files.isDirectory(goRoot)) {
                // Up to date, don't need to do anything further.
                return@thenRun
            }

            verifyArchiveChecksum(archivePath, checksumPath)

            val source = when (OperatingSystem.current) {
                OperatingSystem.Windows -> task.project.zipTree(archivePath)
                else -> task.project.tarTree(archivePath)
            }

            extract(task, source, goRoot)
        }
    }

    private fun verifyArchiveChecksum(archivePath: Path, checksumPath: Path) {
        val expectedChecksum = checksumPath.readText(Charsets.UTF_8)

        verifyChecksum(archivePath, expectedChecksum)
    }

    private val golangArchiveFileExtension = when (OperatingSystem.current) {
        OperatingSystem.Windows -> "zip"
        else -> "tar.gz"
    }
}

data class GolangEnvironment(val root: Path, val compiler: Path)
