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
import batect.dockerclient.buildtools.zig.ZigEnvironment
import batect.dockerclient.buildtools.zig.ZigEnvironmentService
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import java.nio.file.Path
import java.nio.file.Paths
import java.util.concurrent.CompletableFuture
import kotlin.io.path.absolutePathString

abstract class GolangCrossCompilationTask : GolangTask() {
    @get:Input
    abstract val zigVersion: Property<String>

    @get:Internal
    abstract val zigEnvironmentService: Property<ZigEnvironmentService>

    protected fun prepareEnvironment(
        targetOperatingSystem: OperatingSystem,
        targetArchitecture: Architecture
    ): CompletableFuture<GolangCrossCompilationEnvironment> {
        val golangEnvironmentProvider = golangEnvironmentService.get().getOrPrepareEnvironment(golangVersion.get(), this)
        val zigEnvironmentProvider = zigEnvironmentService.get().getOrPrepareEnvironment(zigVersion.get(), this)

        return CompletableFuture.allOf(golangEnvironmentProvider, zigEnvironmentProvider).thenApply {
            val golangEnvironment = golangEnvironmentProvider.get()
            val zigEnvironment = zigEnvironmentProvider.get()

            GolangCrossCompilationEnvironment(
                golangEnvironment.root,
                golangEnvironment.compiler,
                environmentVariablesForTarget(zigEnvironment, targetOperatingSystem, targetArchitecture)
            )
        }
    }

    private fun environmentVariablesForTarget(
        zigEnvironment: ZigEnvironment,
        targetOperatingSystem: OperatingSystem,
        targetArchitecture: Architecture
    ): Map<String, String> {
        val rootCacheDirectory = project.buildDir.resolve("zig").resolve("cache").resolve(this.name)

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

    companion object {
        private val macOSSystemRootDirectory: String by lazy {
            val process = ProcessBuilder("xcrun", "--show-sdk-path")
                .redirectOutput(ProcessBuilder.Redirect.PIPE)
                .redirectErrorStream(true)
                .start()

            val exitCode = process.waitFor()

            val output = process.inputStream.use { input ->
                input.bufferedReader(Charsets.UTF_8).use { reader ->
                    reader.readText()
                }
            }

            if (exitCode != 0) {
                throw RuntimeException("Retrieving macOS system root failed: command 'xcrun --show-sdk-path' exited with code $exitCode and output: $output")
            }

            Paths.get(output.trim()).absolutePathString()
        }
    }
}

data class GolangCrossCompilationEnvironment(
    val golangRoot: Path,
    val golangCompiler: Path,
    val environmentVariables: Map<String, String>
)
