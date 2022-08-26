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
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.process.internal.ExecActionFactory
import java.nio.file.Path
import javax.inject.Inject
import kotlin.io.path.absolutePathString

@CacheableTask
abstract class GolangLint @Inject constructor(private val execActionFactory: ExecActionFactory) : GolangCrossCompilationTask() {
    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val sourceDirectory: DirectoryProperty

    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val executablePath: RegularFileProperty

    @get:Input
    abstract val systemPath: Property<String>

    @get:OutputFile
    abstract val upToDateCheckFilePath: RegularFileProperty

    init {
        group = "verification"
    }

    @TaskAction
    fun run() {
        val env = environmentService.get().getOrPrepareEnvironment(
            this,
            golangVersion.get(),
            zigVersion.get(),
            OperatingSystem.current,
            Architecture.current,
        )

        val action = execActionFactory.newExecAction()
        action.workingDir = sourceDirectory.asFile.get()
        action.environment("GOROOT", env.golangRoot)
        action.environment("PATH", combinePath(env.golangRoot, systemPath.get()))
        action.environment(env.environmentVariables)

        action.commandLine = listOf(
            executablePath.get().toString(),
            "run",
            "--timeout",
            "5m"
        )

        action.execute().assertNormalExitValue()

        val upToDateCheckFile = upToDateCheckFilePath.get().asFile

        if (upToDateCheckFile.exists()) {
            upToDateCheckFile.delete()
        }

        upToDateCheckFile.createNewFile()
    }

    private fun combinePath(golangRoot: Path, systemPath: String): String {
        val goBinDirectory = golangRoot.resolve("bin").absolutePathString()
        val separator = if (OperatingSystem.current == OperatingSystem.Windows) ";" else ":"

        return "$goBinDirectory$separator$systemPath"
    }
}
