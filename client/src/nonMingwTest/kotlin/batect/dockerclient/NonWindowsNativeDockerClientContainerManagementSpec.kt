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
import io.kotest.assertions.timing.eventually
import io.kotest.common.ExperimentalKotest
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import io.ktor.utils.io.core.use
import kotlinx.coroutines.withTimeout
import okio.Buffer
import okio.Path.Companion.toPath
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

// HACK
// These tests are here (instead of in commonTest's DockerClientContainerManagementSpec) because of https://youtrack.jetbrains.com/issue/KTOR-4307.
// Once that issue is resolved (or an alternative client is available for Windows), we can move these tests back to that class.
@OptIn(ExperimentalKotest::class)
class NonWindowsNativeDockerClientContainerManagementSpec : ShouldSpec({
    val client = closeAfterTest(DockerClient.create())

    context("when working with Linux containers").onlyIfDockerDaemonSupportsLinuxContainers {
        should("be able to connect to a published port from a container with a corresponding EXPOSE instruction in the image") {
            val httpServerImage = client.pullImage("nginx:1.21.6")

            val spec = ContainerCreationSpec.Builder(httpServerImage)
                .withExposedPort(9000, 80) // Port 80 has a corresponding EXPOSE instruction in the nginx image referenced above.
                .build()

            val container = client.createContainer(spec)

            try {
                client.startContainer(container)

                eventually(3.seconds, 200.milliseconds) {
                    withTimeout(200) {
                        HttpClient().use { httpClient ->
                            val response = httpClient.get("http://localhost:9000")
                            response.status shouldBe HttpStatusCode.OK
                        }
                    }
                }
            } finally {
                client.removeContainer(container, force = true)
            }
        }

        should("be able to connect to a published port from a container without a corresponding EXPOSE instruction in the image") {
            val imagePath = systemFileSystem.canonicalize("./src/commonTest/resources/images/http-server-without-expose".toPath())
            val httpServerImage = client.buildImage(ImageBuildSpec.Builder(imagePath).build(), SinkTextOutput(Buffer()))

            val spec = ContainerCreationSpec.Builder(httpServerImage)
                .withExposedPort(9000, 81) // Port 81 does not a corresponding EXPOSE instruction in the image built above.
                .build()

            val container = client.createContainer(spec)

            try {
                client.startContainer(container)

                eventually(3.seconds, 200.milliseconds) {
                    withTimeout(200) {
                        HttpClient().use { httpClient ->
                            val response = httpClient.get("http://localhost:9000")
                            response.status shouldBe HttpStatusCode.OK
                        }
                    }
                }
            } finally {
                client.removeContainer(container, force = true)
            }
        }
    }
})
