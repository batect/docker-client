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

package batect.dockerclient.buildtools.golang

import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.process.internal.ExecActionFactory
import javax.inject.Inject

abstract class GolangLint @Inject constructor(private val execActionFactory: ExecActionFactory) : DefaultTask() {
    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val sourceDirectory: DirectoryProperty

    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val executablePath: RegularFileProperty

    @get:OutputFile
    abstract val upToDateCheckFilePath: RegularFileProperty

    init {
        group = "verification"
    }

    @TaskAction
    fun run() {
        val action = execActionFactory.newExecAction()
        action.workingDir = sourceDirectory.asFile.get()

        action.commandLine = listOf(
            executablePath.get().toString(),
            "run"
        )

        action.execute().assertNormalExitValue()

        val upToDateCheckFile = upToDateCheckFilePath.get().asFile

        if (upToDateCheckFile.exists()) {
            upToDateCheckFile.delete()
        }

        upToDateCheckFile.createNewFile()
    }
}
