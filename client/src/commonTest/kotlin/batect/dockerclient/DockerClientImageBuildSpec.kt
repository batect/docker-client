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

package batect.dockerclient

import batect.dockerclient.io.SinkTextOutput
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.collections.shouldEndWith
import okio.Buffer
import okio.Path
import okio.Path.Companion.toPath

class DockerClientImageBuildSpec : ShouldSpec({
    val rootTestImagesDirectory: Path = systemFileSystem.canonicalize("./src/commonTest/resources/images".toPath())
    val client = closeAfterTest(DockerClient.create())

    context("when no particular image builder is specified") {
        should("be able to build a basic Linux container image").onlyIfDockerDaemonSupportsLinuxContainers {
            val spec = ImageBuildSpec.Builder(rootTestImagesDirectory.resolve("basic-image"))
                .build()

            val output = Buffer()
            val progressUpdatesReceived = mutableListOf<ImageBuildProgressUpdate>()

            val image = client.buildImage(spec, SinkTextOutput(output)) { update ->
                progressUpdatesReceived.add(update)
            }

            progressUpdatesReceived shouldEndWith listOf(
                BuildComplete(image)
            )
        }
    }
})
