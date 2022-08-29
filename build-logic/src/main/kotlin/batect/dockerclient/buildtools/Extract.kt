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

import org.gradle.api.Task
import org.gradle.api.file.CopySpec
import org.gradle.api.file.FileTree
import org.gradle.api.file.RelativePath
import java.nio.file.Path

internal fun extract(task: Task, source: FileTree, destination: Path, applyFromOptions: CopySpec.() -> Unit = {}) {
    task.project.sync { sync ->
        sync.from(source) {
            it.applyFromOptions()

            it.eachFile { f ->
                f.relativePath = RelativePath(true, *f.relativePath.segments.drop(1).toTypedArray())
            }

            it.includeEmptyDirs = false
        }

        sync.into(destination)
    }
}
