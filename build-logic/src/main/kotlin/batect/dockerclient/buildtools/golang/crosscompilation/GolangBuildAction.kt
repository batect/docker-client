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

import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.MapProperty
import org.gradle.process.ExecOperations
import org.gradle.workers.WorkAction
import org.gradle.workers.WorkParameters
import java.nio.file.Files
import javax.inject.Inject

abstract class GolangBuildAction @Inject constructor(private val execOperations: ExecOperations) : WorkAction<GolangBuildActionParameters> {
    private val outputDirectory = parameters.outputDirectory
    private val sourceDirectory = parameters.sourceDirectory
    private val compilationCommand = parameters.compilationCommand
    private val compilationCommandEnvironment = parameters.compilationCommandEnvironment
    private val outputLibraryFile = parameters.outputLibraryFile
    private val outputHeaderFile = parameters.outputHeaderFile

    override fun execute() {
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
        val result = execOperations.exec {
            it.workingDir = sourceDirectory.asFile.get()
            it.environment(compilationCommandEnvironment.get())
            it.commandLine = compilationCommand.get()
        }

        result.assertNormalExitValue()
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
}

interface GolangBuildActionParameters : WorkParameters {
    val outputDirectory: DirectoryProperty
    val sourceDirectory: DirectoryProperty

    val compilationCommand: ListProperty<String>
    val compilationCommandEnvironment: MapProperty<String, String>

    val outputLibraryFile: RegularFileProperty
    val outputHeaderFile: RegularFileProperty
}
