package batect.dockerclient.buildtools

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

        outputDirectory.convention(project.provider {
            project.layout.buildDirectory
                .dir("libs").get()
                .dir(targetOperatingSystem.get().name.lowercase())
                .dir(targetArchitecture.get().name.lowercase())
                .dir(targetBinaryType.get().name.lowercase())
        })

        baseOutputName.convention(project.provider {
            when (targetOperatingSystem.get()) {
                OperatingSystem.Windows -> libraryName.get()
                OperatingSystem.Linux, OperatingSystem.Darwin -> "lib${libraryName.get()}"
                else -> throw IllegalArgumentException("Unknown operating system: ${targetOperatingSystem.get()}")
            }
        })

        outputLibraryFile.convention(project.provider {
            val libraryFileName = when (targetBinaryType.get()) {
                BinaryType.Shared -> when (targetOperatingSystem.get()) {
                    OperatingSystem.Windows -> "${baseOutputName.get()}.dll"
                    OperatingSystem.Linux -> "${baseOutputName.get()}.so"
                    OperatingSystem.Darwin -> "${baseOutputName.get()}.dylib"
                    else -> throw IllegalArgumentException("Unknown operating system: ${targetOperatingSystem.get()}")
                }
                BinaryType.Archive -> when (targetOperatingSystem.get()) {
                    OperatingSystem.Windows -> "${baseOutputName.get()}.lib"
                    OperatingSystem.Linux, OperatingSystem.Darwin -> "${baseOutputName.get()}.a"
                    else -> throw IllegalArgumentException("Unknown operating system: ${targetOperatingSystem.get()}")
                }
                else -> throw IllegalArgumentException("Unknown binary type: ${targetBinaryType.get()}")
            }

            outputDirectory.file(libraryFileName).get()
        })

        outputHeaderFile.convention(project.provider { outputDirectory.file("${baseOutputName.get()}.h").get() })

        dockerImage.convention(project.provider {
            if (targetOperatingSystem.get() == OperatingSystem.Linux) {
                when (targetArchitecture.get()) {
                    Architecture.X86, Architecture.X64 -> "docker.elastic.co/beats-dev/golang-crossbuild:1.16.4-main-debian10"
                    Architecture.Arm64 -> "docker.elastic.co/beats-dev/golang-crossbuild:1.16.4-arm-debian10"
                    else -> throw IllegalArgumentException("Cannot get Docker image for architecture ${targetArchitecture.get().name}")
                }
            } else {
                null
            }
        })

        onlyIf {
            when (targetOperatingSystem.get()) {
                OperatingSystem.Darwin -> org.gradle.internal.os.OperatingSystem.current().isMacOsX
                OperatingSystem.Windows -> org.gradle.internal.os.OperatingSystem.current().isWindows
                OperatingSystem.Linux -> true
                else -> throw UnsupportedOperationException("Unknown target operating system ${targetOperatingSystem.get()}")
            }

        }
    }

    @TaskAction
    fun run() {
        val environment = mapOf(
            "CGO_ENABLED" to "1",
            "GOOS" to targetOperatingSystem.get().name.lowercase(),
            "GOARCH" to targetArchitecture.get().golangName
        )

        val useDocker = targetOperatingSystem.get() == OperatingSystem.Linux

        val outputPathInBuildEnvironment = if (useDocker) {
            "/code/" + project.projectDir.toPath().relativize(outputLibraryFile.get().asFile.toPath()).toString()
        } else {
            outputLibraryFile.get().toString()
        }

        val goBuildCommand = listOf("go", "build", "-buildmode=c-${targetBinaryType.get().name.lowercase()}", "-o", outputPathInBuildEnvironment)

        val action = execActionFactory.newExecAction()
        action.workingDir = sourceDirectory.asFile.get()
        action.environment(environment)

        action.commandLine = if (useDocker) {
            val sourceDirectoryInBuildEnvironment = "/code/" + project.projectDir.toPath().relativize(sourceDirectory.get().asFile.toPath())
            val environmentArgs = environment.flatMap { listOf("-e", "${it.key}=${it.value}") }

            listOf(
                "docker",
                "run",
                "--rm",
                "-i",
                "-v", "${project.projectDir}:/code",
                "-v", "docker-client-cache-${targetOperatingSystem.get().name}-${targetArchitecture.get().name}:/go",
                "-e", "GOCACHE=/go/cache",
                "-w", sourceDirectoryInBuildEnvironment,
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
}

