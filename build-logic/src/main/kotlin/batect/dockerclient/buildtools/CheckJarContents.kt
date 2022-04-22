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

package batect.dockerclient.buildtools

import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFile
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.SetProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.process.internal.ExecActionFactory
import java.io.ByteArrayOutputStream
import javax.inject.Inject

abstract class CheckJarContents @Inject constructor(private val execActionFactory: ExecActionFactory) : DefaultTask() {
    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val jarFile: RegularFileProperty

    @get:Input
    abstract val expectedFiles: SetProperty<String>

    @get:OutputFile
    abstract val upToDateCheckFilePath: RegularFileProperty

    init {
        group = "verification"

        upToDateCheckFilePath.convention(project.layout.buildDirectory.file("checkJarContents/upToDate"))
    }

    @TaskAction
    fun run() {
        val filesInJar = getJarContents(jarFile.get())

        val missingFiles = expectedFiles.get().minus(filesInJar)

        if (missingFiles.isNotEmpty()) {
            throw RuntimeException("${jarFile.get()} is missing expected files $missingFiles.\nIt contains the following files:\n${filesInJar.sorted().joinToString("\n")}")
        }
    }

    private fun getJarContents(jar: RegularFile): Set<String> {
        val action = execActionFactory.newExecAction()
        val output = ByteArrayOutputStream()
        action.commandLine = listOf("jar", "--list", "--file=$jar")
        action.standardOutput = output
        action.execute().assertNormalExitValue()

        return output.toString().trim().lines().toSet()
    }
}
