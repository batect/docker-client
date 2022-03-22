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

package batect.dockerclient.buildtools.golang

import batect.dockerclient.buildtools.VerifyChecksum
import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault
import org.gradle.workers.WorkerExecutor
import javax.inject.Inject

@DisableCachingByDefault(because = "Not worth caching")
abstract class VerifyChecksumFromSingleChecksumFile @Inject constructor(private val workerExecutor: WorkerExecutor) : DefaultTask() {
    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val checksumFile: RegularFileProperty

    @get:InputFile
    @get:PathSensitive(PathSensitivity.NAME_ONLY)
    abstract val fileToVerify: RegularFileProperty

    @TaskAction
    fun run() {
        workerExecutor.noIsolation().submit(VerifyChecksum::class.java) {
            it.expectedChecksum.set(loadExpectedChecksum())
            it.fileToVerify.set(fileToVerify)
        }
    }

    private fun loadExpectedChecksum(): String {
        return checksumFile.get().asFile
            .readText(Charsets.UTF_8)
            .trim()
    }
}
