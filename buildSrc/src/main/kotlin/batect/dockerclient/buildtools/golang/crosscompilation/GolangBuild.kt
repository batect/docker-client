/*
    Copyright 2017-2021 Charles Korn.

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
import batect.dockerclient.buildtools.BinaryType
import batect.dockerclient.buildtools.OperatingSystem
import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.workers.WorkerExecutor
import javax.inject.Inject

abstract class GolangBuild @Inject constructor(private val workerExecutor: WorkerExecutor) : DefaultTask() {
    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val sourceDirectory: DirectoryProperty

    @get:Input
    abstract val targetArchitecture: Property<Architecture>

    @get:Input
    abstract val targetOperatingSystem: Property<OperatingSystem>

    @get:Input
    abstract val targetBinaryType: Property<BinaryType>

    @get:Internal
    abstract val libraryName: Property<String>

    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val golangCompilerExecutablePath: RegularFileProperty

    @get:Input
    abstract val compilationEnvironmentVariables: MapProperty<String, String>

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @get:OutputFile
    abstract val outputLibraryFile: RegularFileProperty

    @get:OutputFile
    abstract val outputHeaderFile: RegularFileProperty

    @get:Internal
    abstract val baseOutputName: Property<String>

    @get:Internal
    abstract val zigCacheDirectory: DirectoryProperty

    init {
        group = "build"

        onlyIf {
            when (targetOperatingSystem.get()) {
                OperatingSystem.Darwin -> OperatingSystem.current == OperatingSystem.Darwin
                OperatingSystem.Windows, OperatingSystem.Linux -> true
                else -> throw UnsupportedOperationException("Unknown target operating system ${targetOperatingSystem.get()}")
            }
        }
    }

    @TaskAction
    fun run() {
        workerExecutor.noIsolation().submit(GolangBuildAction::class.java) {
            it.outputDirectory.set(outputDirectory)
            it.sourceDirectory.set(sourceDirectory)
            it.compilationCommand.set(compilationCommand)
            it.compilationCommandEnvironment.set(compilationEnvironmentVariables.get())
            it.outputLibraryFile.set(outputLibraryFile)
            it.outputHeaderFile.set(outputHeaderFile)
        }
    }

    private val compilationCommand: List<String>
        get() = listOf(
            golangCompilerExecutablePath.get().asFile.absolutePath,
            "build",
            "-buildmode=c-${targetBinaryType.get().name.lowercase()}",
            *targetSpecificGoBuildArgs,
            "-o",
            outputLibraryFile.get().toString()
        )

    // -ldflags "-s" below is inspired by https://github.com/ziglang/zig/issues/9050#issuecomment-859939664
    // and fixes errors like the following when building the shared library for darwin/amd64:
    // /opt/homebrew/Cellar/go/1.17.5/libexec/pkg/tool/darwin_arm64/link: /opt/homebrew/Cellar/go/1.17.5/libexec/pkg/tool/darwin_arm64/link: running strip failed: exit status 1
    // /Applications/Xcode.app/Contents/Developer/Toolchains/XcodeDefault.xctoolchain/usr/bin/strip: error: bad n_sect for symbol table entry 556 in: /private/var/folders/7t/_rsz39554vvgvq2t6b4ztktc0000gn/T/go-build3526877506/b001/exe/libmain.dylib
    private val targetSpecificGoBuildArgs: Array<String>
        get() = if (targetOperatingSystem.get() == OperatingSystem.Darwin && targetBinaryType.get() == BinaryType.Shared) {
            arrayOf("-ldflags", "-s")
        } else {
            emptyArray()
        }
}
