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
import batect.dockerclient.buildtools.OperatingSystem
import batect.dockerclient.buildtools.zig.XzArchiver
import de.undercouch.gradle.tasks.download.DownloadAction
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.file.FileTree
import org.gradle.api.file.RelativePath
import org.gradle.api.internal.file.FileResolver
import org.gradle.api.internal.resources.DefaultResourceResolver
import org.gradle.api.services.BuildService
import org.gradle.api.services.BuildServiceParameters
import org.gradle.configurationcache.extensions.serviceOf
import org.gradle.internal.nativeintegration.filesystem.FileSystem
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import kotlin.io.path.absolutePathString

abstract class GolangCrossCompilationEnvironmentService : BuildService<BuildServiceParameters.None> {
    private val golangVersions = ConcurrentHashMap<GolangVersion, CompletableFuture<GolangEnvironment>>()
    private val zigVersions = ConcurrentHashMap<ZigVersion, CompletableFuture<ZigEnvironment>>()

    fun getOrPrepareEnvironment(
        task: Task,
        golangVersion: String,
        zigVersion: String,
        targetOperatingSystem: OperatingSystem,
        targetArchitecture: Architecture
    ): GolangCrossCompilationEnvironment {
        val toolsDir = task.project.buildDir.toPath().resolve("tools").toAbsolutePath()
        val downloadsDir = toolsDir.resolve("downloads")

        val golangEnvironmentProvider = getOrPrepareGolang(GolangVersion(golangVersion), task, toolsDir, downloadsDir)
        val zigEnvironmentProvider = getOrPrepareZig(ZigVersion(zigVersion), task, toolsDir, downloadsDir)

        CompletableFuture.allOf(golangEnvironmentProvider, zigEnvironmentProvider).get()

        val golangEnvironment = golangEnvironmentProvider.get()
        val zigEnvironment = zigEnvironmentProvider.get()

        return GolangCrossCompilationEnvironment(
            golangEnvironment.root,
            golangEnvironment.compiler,
            environmentVariablesForTarget(task, zigEnvironment, targetOperatingSystem, targetArchitecture)
        )
    }

    private fun getOrPrepareGolang(version: GolangVersion, task: Task, toolsDir: Path, downloadsDir: Path): CompletableFuture<GolangEnvironment> {
        return golangVersions.computeIfAbsent(version) {
            prepareGolang(version, task, toolsDir, downloadsDir)
        }
    }

    private fun prepareGolang(version: GolangVersion, task: Task, toolsDir: Path, downloadsDir: Path): CompletableFuture<GolangEnvironment> {
        val goRoot = toolsDir.resolve("golang-$version")

        val executableName = when (OperatingSystem.current) {
            OperatingSystem.Windows -> "go.exe"
            else -> "go"
        }

        return downloadAndExtractGolang(version, task, goRoot, downloadsDir).thenApply {
            GolangEnvironment(goRoot, goRoot.resolve("bin").resolve(executableName))
        }
    }

    private fun getOrPrepareZig(version: ZigVersion, task: Task, toolsDir: Path, downloadsDir: Path): CompletableFuture<ZigEnvironment> {
        return zigVersions.computeIfAbsent(version) {
            prepareZig(version, task, toolsDir, downloadsDir)
        }
    }

    private fun prepareZig(version: ZigVersion, task: Task, toolsDir: Path, downloadsDir: Path): CompletableFuture<ZigEnvironment> {
        val zigRoot = toolsDir.resolve("zig-$version")

        val executableName = when (OperatingSystem.current) {
            OperatingSystem.Windows -> "zig.exe"
            else -> "zig"
        }

        return downloadAndExtractZig(version, task, zigRoot, downloadsDir).thenApply {
            ZigEnvironment(zigRoot, zigRoot.resolve(executableName))
        }
    }

    private fun downloadAndExtractGolang(version: GolangVersion, task: Task, goRoot: Path, downloadsDir: Path): CompletableFuture<Void> {
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

            // TODO: verify checksum

            val source = when (OperatingSystem.current) {
                OperatingSystem.Windows -> task.project.zipTree(archivePath)
                else -> task.project.tarTree(archivePath)
            }

            extract(task, source, goRoot)
        }
    }

    private fun downloadAndExtractZig(zigVersion: ZigVersion, task: Task, zigRoot: Path, downloadsDir: Path): CompletableFuture<Void> {
        val rootUrl = "https://ziglang.org/download"
        val platformName = "${OperatingSystem.current.zigName}-${Architecture.current.zigName}"
        val archiveFileName = "zig-$platformName-$zigVersion.$zigArchiveFileExtension"
        val archivePath = downloadsDir.resolve(archiveFileName)
        val checksumFileName = "$archiveFileName.checksums.json"
        val checksumPath = downloadsDir.resolve(checksumFileName)

        val archiveDownload = download(task, "$rootUrl/$zigVersion/$archiveFileName", archivePath)
        val checksumDownload = download(task, "$rootUrl/index.json", checksumPath)

        return CompletableFuture.allOf(archiveDownload, checksumDownload).thenRun {
            val archiveDownloadResult = archiveDownload.get()
            val checksumDownloadResult = checksumDownload.get()

            if (archiveDownloadResult == DownloadResult.UpToDate && checksumDownloadResult == DownloadResult.UpToDate && Files.isDirectory(zigRoot)) {
                // Up to date, don't need to do anything further.
                return@thenRun
            }

            // TODO: verify checksum

            val source = when (OperatingSystem.current) {
                OperatingSystem.Windows -> task.project.zipTree(archivePath)
                else -> task.project.tarTree(XzArchiver(task.project.resourceResolver.resolveResource(archivePath)))
            }

            extract(task, source, zigRoot)
        }
    }

    private fun download(task: Task, url: String, destination: Path): CompletableFuture<DownloadResult> {
        if (Files.exists(destination)) {
            return CompletableFuture.completedFuture(DownloadResult.UpToDate)
        }

        val action = DownloadAction(task.project, task)
        action.src(url)
        action.dest(destination.toFile())
        action.overwrite(false)

        return action.execute().thenApply { DownloadResult.fromUpToDate(action.isUpToDate) }
    }

    private fun extract(task: Task, source: FileTree, destination: Path) {
        Files.deleteIfExists(destination)

        task.project.sync { sync ->
            sync.from(source) {
                it.eachFile { f ->
                    f.relativePath = RelativePath(true, *f.relativePath.segments.drop(1).toTypedArray())
                }

                it.includeEmptyDirs = false
            }

            sync.into(destination)
        }
    }

    private fun environmentVariablesForTarget(
        task: Task,
        zigEnvironment: ZigEnvironment,
        targetOperatingSystem: OperatingSystem,
        targetArchitecture: Architecture
    ): Map<String, String> {
        val rootCacheDirectory = task.project.buildDir.resolve("zig").resolve("cache").resolve(task.name)

        return mapOf(
            "CGO_ENABLED" to "1",
            "GOOS" to targetOperatingSystem.name.lowercase(),
            "GOARCH" to targetArchitecture.golangName,
            "CC" to zigCompilerCommandLine(zigEnvironment, "cc", targetOperatingSystem, targetArchitecture),
            "CXX" to zigCompilerCommandLine(zigEnvironment, "c++", targetOperatingSystem, targetArchitecture),
            "ZIG_LOCAL_CACHE_DIR" to rootCacheDirectory.resolve("local").absolutePath,
            "ZIG_GLOBAL_CACHE_DIR" to rootCacheDirectory.resolve("global").absolutePath
        )
    }

    private fun zigCompilerCommandLine(
        zigEnvironment: ZigEnvironment,
        compilerType: String,
        targetOperatingSystem: OperatingSystem,
        targetArchitecture: Architecture
    ): String {
        val target = "${targetArchitecture.zigName}-${targetOperatingSystem.zigName}-gnu"

        val targetSpecificArgs = when (targetOperatingSystem) {
            OperatingSystem.Darwin -> """--sysroot "$macOSSystemRootDirectory" "-I/usr/include" "-F/System/Library/Frameworks" "-L/usr/lib""""
            else -> ""
        }

        return """"${zigEnvironment.compiler}" $compilerType -target $target $targetSpecificArgs"""
    }

    private val golangArchiveFileExtension = when (OperatingSystem.current) {
        OperatingSystem.Windows -> "zip"
        else -> "tar.gz"
    }

    private val zigArchiveFileExtension = when (OperatingSystem.current) {
        OperatingSystem.Windows -> "zip"
        else -> "tar.xz"
    }

    private enum class DownloadResult {
        UpToDate,
        Downloaded;

        companion object {
            fun fromUpToDate(value: Boolean): DownloadResult = when (value) {
                true -> UpToDate
                false -> Downloaded
            }
        }
    }

    private val Project.resourceResolver: DefaultResourceResolver
        get() {
            val fileResolver = this.serviceOf<FileResolver>()
            val fileSystem = this.serviceOf<FileSystem>()

            return DefaultResourceResolver(fileResolver, fileSystem)
        }

    private val macOSSystemRootDirectory: String by lazy {
        val process = ProcessBuilder("xcrun", "--show-sdk-path")
            .redirectOutput(ProcessBuilder.Redirect.PIPE)
            .redirectErrorStream(true)
            .start()

        val exitCode = process.waitFor()
        val output = process.inputReader().readText().trim()

        if (exitCode != 0) {
            throw RuntimeException("Retrieving macOS system root failed: command 'xcrun --show-sdk-path' exited with code $exitCode and output: $output")
        }

        Paths.get(output).absolutePathString()
    }

    private data class GolangVersion(val version: String) {
        override fun toString(): String = version
    }

    private data class GolangEnvironment(val root: Path, val compiler: Path)

    private data class ZigVersion(val version: String) {
        override fun toString(): String = version
    }

    private data class ZigEnvironment(val root: Path, val compiler: Path)
}

data class GolangCrossCompilationEnvironment(
    val golangRoot: Path,
    val golangCompiler: Path,
    val environmentVariables: Map<String, String>,
)
