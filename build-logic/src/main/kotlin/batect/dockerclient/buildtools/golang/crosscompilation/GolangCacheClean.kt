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
import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.process.ExecOperations
import javax.inject.Inject

abstract class GolangCacheClean @Inject constructor(private val execOperations: ExecOperations) : DefaultTask() {
    @get:Input
    abstract val golangVersion: Property<String>

    // TODO: remove the need for this - this task doesn't need a Zig compiler
    @get:Input
    abstract val zigVersion: Property<String>

    @get:Internal
    abstract val environmentService: Property<GolangCrossCompilationEnvironmentService>

    @TaskAction
    fun run() {
        val env = environmentService.get().getOrPrepareEnvironment(
            this,
            golangVersion.get(),
            zigVersion.get(),
            OperatingSystem.current,
            Architecture.current,
        )

        val result = execOperations.exec {
            it.commandLine(env.golangCompiler, "clean", "-cache")
        }

        result.assertNormalExitValue()
    }
}
