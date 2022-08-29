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

@file:Suppress("ktlint:filename")

package batect.dockerclient.buildtools

import de.undercouch.gradle.tasks.download.DownloadAction
import org.gradle.api.Task
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.CompletableFuture

internal fun download(task: Task, url: String, destination: Path): CompletableFuture<DownloadResult> {
    if (Files.exists(destination)) {
        return CompletableFuture.completedFuture(DownloadResult.UpToDate)
    }

    val action = DownloadAction(task.project, task)
    action.src(url)
    action.dest(destination.toFile())
    action.overwrite(false)

    return action.execute().thenApply { DownloadResult.fromUpToDate(action.isUpToDate) }
}

internal enum class DownloadResult {
    UpToDate,
    Downloaded;

    companion object {
        fun fromUpToDate(value: Boolean): DownloadResult = when (value) {
            true -> UpToDate
            false -> Downloaded
        }
    }
}
