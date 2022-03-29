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

package batect.dockerclient

import batect.dockerclient.io.SinkTextOutput
import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.common.ExperimentalKotest
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.comparables.shouldBeLessThan
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okio.Buffer
import kotlin.time.Duration.Companion.seconds
import kotlin.time.ExperimentalTime
import kotlin.time.measureTime

@ExperimentalTime
@OptIn(ExperimentalKotest::class)
class DockerClientContainerManagementSpec : ShouldSpec({
    xcontext("when working with Linux containers").onlyIfDockerDaemonSupportsLinuxContainers {
        val client = closeAfterTest(DockerClient.Builder().build())
        val image = client.pullImage("alpine:3.15.0")

        should("be able to create, start, wait for and remove a container") {
            val spec = ContainerCreationSpec.Builder(image)
                .withCommand(listOf("sh", "-c", "exit 123"))
                .build()

            val container = client.createContainer(spec)

            try {
                val exitCode = withContext(IODispatcher) {
                    val waitingForContainerToExit = ReadyNotification()
                    val exitCodeSource = async { client.waitForContainerToExit(container, waitingForContainerToExit) }

                    launch {
                        waitingForContainerToExit.waitForReady()
                        client.startContainer(container)
                    }

                    exitCodeSource.await()
                }

                exitCode shouldBe 123
            } finally {
                client.removeContainer(container, force = true)
            }
        }

        should("be able to stop a container, respecting any timeout provided") {
            val spec = ContainerCreationSpec.Builder(image)
                .withCommand(listOf("sh", "-c", "sleep 9999")) // This command does not respond to signals and so must be forcibly terminated
                .build()

            val container = client.createContainer(spec)

            try {
                client.startContainer(container)

                val stopDuration = measureTime { client.stopContainer(container, 2.seconds) }
                stopDuration shouldBeLessThan 5.seconds
            } finally {
                client.removeContainer(container, force = true)
            }
        }

        should("be able to stream output from a container") {
            val spec = ContainerCreationSpec.Builder(image)
                .withCommand(listOf("sh", "-c", "echo 'Hello stdout' >/dev/stdout && echo 'Hello stderr' >/dev/stderr"))
                .build()

            val container = client.createContainer(spec)

            try {
                val stdout = Buffer()
                val stderr = Buffer()

                withContext(IODispatcher) {
                    val listeningToOutput = ReadyNotification()

                    launch {
                        client.attachToContainerOutput(container, SinkTextOutput(stdout), SinkTextOutput(stderr), listeningToOutput)
                    }

                    launch {
                        listeningToOutput.waitForReady()
                        client.startContainer(container)
                    }
                }

                val stdoutText = stdout.readUtf8()
                val stderrText = stderr.readUtf8()

                stdoutText shouldBe "Hello stdout\n"
                stderrText shouldBe "Hello stderr\n"
            } finally {
                client.removeContainer(container, force = true)
            }
        }

        should("throw an appropriate exception when creating a container with an image that doesn't exist") {
            val spec = ContainerCreationSpec.Builder(ImageReference("batect/this-image-does-not-exist:abc123"))
                .withCommand(listOf("sh", "-c", "exit 123"))
                .build()

            val exception = shouldThrow<ContainerCreationFailedException> { client.createContainer(spec) }

            exception.message shouldBe "Error response from daemon: No such image: batect/this-image-does-not-exist:abc123"
        }

        should("throw an appropriate exception when starting a container that doesn't exist") {
            val exception = shouldThrow<ContainerStartFailedException> { client.startContainer(ContainerReference("does-not-exist")) }

            exception.message shouldBe "Error response from daemon: No such container: does-not-exist"
        }

        should("not throw an exception when starting a container that has already been started") {
            val spec = ContainerCreationSpec.Builder(image)
                .withCommand(listOf("sh", "-c", "sleep 9999"))
                .build()

            val container = client.createContainer(spec)

            try {
                client.startContainer(container)

                shouldNotThrowAny { client.startContainer(container) }
            } finally {
                client.removeContainer(container, force = true)
            }
        }

        should("throw an appropriate exception when stopping a container that doesn't exist") {
            val exception = shouldThrow<ContainerStopFailedException> { client.stopContainer(ContainerReference("does-not-exist"), 10.seconds) }

            exception.message shouldBe "Error response from daemon: No such container: does-not-exist"
        }

        should("not throw an exception when stopping a container that's already been stopped") {
            val spec = ContainerCreationSpec.Builder(image)
                .withCommand(listOf("sh", "-c", "sleep 9999"))
                .build()

            val container = client.createContainer(spec)

            try {
                client.startContainer(container)
                client.stopContainer(container, 1.seconds)

                shouldNotThrowAny { client.stopContainer(container, 1.seconds) }
            } finally {
                client.removeContainer(container, force = true)
            }
        }

        should("not throw an exception when stopping a container that's never been started") {
            val spec = ContainerCreationSpec.Builder(image)
                .withCommand(listOf("sh", "-c", "sleep 9999"))
                .build()

            val container = client.createContainer(spec)

            try {
                shouldNotThrowAny { client.stopContainer(container, 1.seconds) }
            } finally {
                client.removeContainer(container, force = true)
            }
        }

        should("throw an appropriate exception when removing a container that doesn't exist") {
            val exception = shouldThrow<ContainerRemovalFailedException> { client.removeContainer(ContainerReference("does-not-exist")) }

            exception.message shouldBe "No such container: does-not-exist"
        }
    }
})
