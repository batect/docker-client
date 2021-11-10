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

package batect.dockerclient.buildtools

import jnr.posix.POSIXFactory
import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.process.internal.ExecActionFactory
import java.nio.file.Files
import javax.inject.Inject

abstract class GolangBuild @Inject constructor(private val execActionFactory: ExecActionFactory) : DefaultTask() {
    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val sourceDirectory: DirectoryProperty

    @get:Input
    abstract val targetArchitecture: Property<Architecture>

    @get:Input
    abstract val targetOperatingSystem: Property<OperatingSystem>

    @get:Input
    abstract val targetBinaryType: Property<BinaryType>

    @get:Input
    abstract val libraryName: Property<String>

    @get:Input
    @get:Optional
    abstract val dockerImage: Property<String>

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @get:OutputFile
    abstract val outputLibraryFile: RegularFileProperty

    @get:OutputFile
    abstract val outputHeaderFile: RegularFileProperty

    @get:Internal
    protected abstract val baseOutputName: Property<String>

    init {
        group = "build"

        sourceDirectory.convention(project.layout.projectDirectory.dir("src"))

        outputDirectory.convention(
            project.provider {
                project.layout.buildDirectory
                    .dir("libs").get()
                    .dir(targetOperatingSystem.get().name.lowercase())
                    .dir(targetArchitecture.get().name.lowercase())
                    .dir(targetBinaryType.get().name.lowercase())
            }
        )

        baseOutputName.convention(
            project.provider {
                when (targetOperatingSystem.get()) {
                    OperatingSystem.Windows -> libraryName.get()
                    OperatingSystem.Linux, OperatingSystem.Darwin -> "lib${libraryName.get()}"
                    else -> throw IllegalArgumentException("Unknown operating system: ${targetOperatingSystem.get()}")
                }
            }
        )

        outputLibraryFile.convention(
            project.provider {
                val libraryFileName = when (targetBinaryType.get()) {
                    BinaryType.Shared -> when (targetOperatingSystem.get()) {
                        OperatingSystem.Windows -> "${baseOutputName.get()}.dll"
                        OperatingSystem.Linux -> "${baseOutputName.get()}.so"
                        OperatingSystem.Darwin -> "${baseOutputName.get()}.dylib"
                        else -> throw IllegalArgumentException("Unknown operating system: ${targetOperatingSystem.get()}")
                    }
                    BinaryType.Archive -> "${libraryName.get()}.a" // Yes, this should be .lib on Windows, but having everything with .a makes the cinterop configuration for Kotlin much easier.
                    else -> throw IllegalArgumentException("Unknown binary type: ${targetBinaryType.get()}")
                }

                outputDirectory.file(libraryFileName).get()
            }
        )

        outputHeaderFile.convention(project.provider { outputDirectory.file("${libraryName.get()}.h").get() })

        dockerImage.convention(
            project.provider {
                if (targetOperatingSystem.get() == OperatingSystem.Linux) {
                    when (targetArchitecture.get()) {
                        Architecture.X86, Architecture.X64 -> "docker.elastic.co/beats-dev/golang-crossbuild:$golangVersion-main-debian10"
                        Architecture.Arm64 -> "docker.elastic.co/beats-dev/golang-crossbuild:$golangVersion-arm-debian10"
                        else -> throw IllegalArgumentException("Cannot get Docker image for architecture ${targetArchitecture.get().name}")
                    }
                } else {
                    null
                }
            }
        )

        onlyIf {
            when (targetOperatingSystem.get()) {
                OperatingSystem.Darwin -> org.gradle.internal.os.OperatingSystem.current().isMacOsX
                OperatingSystem.Windows -> org.gradle.internal.os.OperatingSystem.current().isWindows
                OperatingSystem.Linux -> org.gradle.internal.os.OperatingSystem.current().isLinux
                else -> throw UnsupportedOperationException("Unknown target operating system ${targetOperatingSystem.get()}")
            }
        }
    }

    private val goBuildCommand: List<String>
        get() = listOf("go", "build", "-buildmode=c-${targetBinaryType.get().name.lowercase()}", "-o", outputLibraryPathInBuildEnvironment)

    @TaskAction
    fun run() {
        cleanOutputDirectory()
        runBuild()
        moveHeaderFileToExpectedLocation()
    }

    private fun cleanOutputDirectory() {
        if (!outputDirectory.get().asFile.deleteRecursively()) {
            throw RuntimeException("Could not remove directory ${outputDirectory.get()}")
        }
    }

    private fun runBuild() {
        val action = execActionFactory.newExecAction()
        action.workingDir = sourceDirectory.asFile.get()
        action.environment(environment)

        action.commandLine = if (useDocker) {
            val sourceDirectoryInBuildEnvironment = "/code/" + project.projectDir.toPath().relativize(sourceDirectory.get().asFile.toPath())
            val environmentArgs = environment.flatMap { listOf("-e", "${it.key}=${it.value}") }
            val uid = POSIXFactory.getNativePOSIX().getuid()
            val gid = POSIXFactory.getNativePOSIX().getgid()

            listOf(
                "docker",
                "run",
                "--rm",
                "-i",
                "-v", "${project.projectDir}:/code",
                "-v", "docker-client-cache-${targetOperatingSystem.get().name}-${targetArchitecture.get().name}:/go",
                "-e", "GOCACHE=/go/cache",
                "-w", sourceDirectoryInBuildEnvironment,
                "-u", "$uid:$gid"
            ) + environmentArgs + listOf(
                dockerImage.get(),
                "--build-cmd", goBuildCommand.joinToString(" "),
                "--platforms", "${targetOperatingSystem.get().name.lowercase()}/${targetArchitecture.get().golangName}"
            )
        } else {
            goBuildCommand
        }

        action.execute().assertNormalExitValue()
    }

    private fun moveHeaderFileToExpectedLocation() {
        val outputLibraryPath = outputLibraryFile.get().asFile
        val outputHeaderPath = outputHeaderFile.get().asFile.toPath()
        val headerFileGeneratedByGolangCompiler = outputLibraryPath.parentFile.resolve(outputLibraryPath.nameWithoutExtension + ".h").toPath()

        if (headerFileGeneratedByGolangCompiler == outputHeaderPath) {
            // Header file already has expected name, nothing to do.
            return
        }

        Files.move(headerFileGeneratedByGolangCompiler, outputHeaderPath)
    }

    private val environment: Map<String, String>
        get() = mapOf(
            "CGO_ENABLED" to "1",
            "GOOS" to targetOperatingSystem.get().name.lowercase(),
            "GOARCH" to targetArchitecture.get().golangName
        )

    private val useDocker: Boolean
        get() = targetOperatingSystem.get() == OperatingSystem.Linux

    private val outputLibraryPathInBuildEnvironment: String
        get() = if (useDocker) {
            "/code/" + project.projectDir.toPath().relativize(outputLibraryFile.get().asFile.toPath()).toString()
        } else {
            outputLibraryFile.get().toString()
        }

    companion object {
        private const val golangVersion = "1.17.3"
    }
}
