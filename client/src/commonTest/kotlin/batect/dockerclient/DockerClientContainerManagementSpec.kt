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
import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.common.ExperimentalKotest
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.comparables.shouldBeLessThan
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okio.Buffer
import okio.Path
import okio.Path.Companion.toPath
import kotlin.random.Random
import kotlin.time.Duration.Companion.seconds
import kotlin.time.ExperimentalTime
import kotlin.time.measureTime

@ExperimentalTime
@OptIn(ExperimentalKotest::class)
class DockerClientContainerManagementSpec : ShouldSpec({
    context("when working with Linux containers").onlyIfDockerDaemonSupportsLinuxContainers {
        val client = closeAfterTest(DockerClient.Builder().build())
        val image = client.pullImage("alpine:3.15.0")
        val hostMountDirectory: Path = systemFileSystem.canonicalize("./src/commonTest/resources/container-mount-directory".toPath())

        context("using low-level methods") {
            should("be able to create, start, wait for and remove a container") {
                val spec = ContainerCreationSpec.Builder(image)
                    .withCommand("sh", "-c", "exit 123")
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
                    .withCommand("sh", "-c", "sleep 9999") // This command does not respond to signals and so must be forcibly terminated
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
                    .withCommand("sh", "-c", "echo 'Hello stdout' >/dev/stdout && echo 'Hello stderr' >/dev/stderr")
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
                    .withCommand("sh", "-c", "exit 123")
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
                    .withCommand("sh", "-c", "sleep 9999")
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
                    .withCommand("sh", "-c", "sleep 9999")
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
                    .withCommand("sh", "-c", "sleep 9999")
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

            should("be able to use Kotlin timeouts to abort waiting for a container to exit") {
                val spec = ContainerCreationSpec.Builder(image)
                    .withCommand("sh", "-c", "sleep 5")
                    .build()

                val container = client.createContainer(spec)

                try {
                    val duration = measureTime {
                        shouldThrow<TimeoutCancellationException> {
                            withContext(IODispatcher) {
                                withTimeout(2.seconds) {
                                    val waitingForContainerToExit = ReadyNotification()

                                    launch { client.waitForContainerToExit(container, waitingForContainerToExit) }

                                    launch {
                                        waitingForContainerToExit.waitForReady()
                                        client.startContainer(container)
                                    }
                                }
                            }
                        }
                    }

                    duration shouldBeLessThan 3.seconds
                } finally {
                    client.removeContainer(container, force = true)
                }
            }

            should("be able to use Kotlin timeouts to abort streaming output from a container while still receiving any output from before the timeout") {
                val spec = ContainerCreationSpec.Builder(image)
                    .withCommand("sh", "-c", "echo 'Hello stdout' >/dev/stdout && echo 'Hello stderr' >/dev/stderr && sleep 5 && echo 'Stdout should never receive this' >/dev/stdout && echo 'Stderr should never receive this' >/dev/stderr")
                    .build()

                val container = client.createContainer(spec)

                try {
                    val stdout = Buffer()
                    val stderr = Buffer()

                    val duration = measureTime {
                        shouldThrow<TimeoutCancellationException> {
                            withContext(IODispatcher) {
                                withTimeout(2.seconds) {
                                    val listeningToOutput = ReadyNotification()

                                    launch {
                                        client.attachToContainerOutput(container, SinkTextOutput(stdout), SinkTextOutput(stderr), listeningToOutput)
                                    }

                                    launch {
                                        listeningToOutput.waitForReady()
                                        client.startContainer(container)
                                    }
                                }
                            }
                        }
                    }

                    val stdoutText = stdout.readUtf8()
                    val stderrText = stderr.readUtf8()

                    stdoutText shouldBe "Hello stdout\n"
                    stderrText shouldBe "Hello stderr\n"

                    duration shouldBeLessThan 5.seconds
                } finally {
                    client.removeContainer(container, force = true)
                }
            }
        }

        context("using the run() helper method") {
            should("be able to run a basic container") {
                val spec = ContainerCreationSpec.Builder(image)
                    .withCommand("sh", "-c", "echo 'Hello stdout' >/dev/stdout && echo 'Hello stderr' >/dev/stderr && exit 123")
                    .build()

                val container = client.createContainer(spec)

                try {
                    val stdout = Buffer()
                    val stderr = Buffer()

                    val exitCode = client.run(container, SinkTextOutput(stdout), SinkTextOutput(stderr))
                    val stdoutText = stdout.readUtf8()
                    val stderrText = stderr.readUtf8()

                    exitCode shouldBe 123
                    stdoutText shouldBe "Hello stdout\n"
                    stderrText shouldBe "Hello stderr\n"
                } finally {
                    client.removeContainer(container, force = true)
                }
            }

            should("be able to use Kotlin timeouts to abort running a container while still receiving any output from before the timeout") {
                val spec = ContainerCreationSpec.Builder(image)
                    .withCommand("sh", "-c", "echo 'Hello stdout' >/dev/stdout && echo 'Hello stderr' >/dev/stderr && sleep 5 && echo 'Stdout should never receive this' >/dev/stdout && echo 'Stderr should never receive this' >/dev/stderr")
                    .build()

                val container = client.createContainer(spec)

                try {
                    val stdout = Buffer()
                    val stderr = Buffer()

                    val duration = measureTime {
                        shouldThrow<TimeoutCancellationException> {
                            withTimeout(2.seconds) {
                                client.run(container, SinkTextOutput(stdout), SinkTextOutput(stderr))
                            }
                        }
                    }

                    val stdoutText = stdout.readUtf8()
                    val stderrText = stderr.readUtf8()

                    stdoutText shouldBe "Hello stdout\n"
                    stderrText shouldBe "Hello stderr\n"

                    duration shouldBeLessThan 5.seconds
                } finally {
                    client.removeContainer(container, force = true)
                }
            }

            data class TestScenario(
                val description: String,
                val creationSpec: ContainerCreationSpec,
                val expectedOutput: String,
                val expectedErrorOutput: String = "",
                val shouldExitWithZeroExitCode: Boolean = true
            )

            setOf(
                TestScenario(
                    "set a hostname for a container",
                    ContainerCreationSpec.Builder(image)
                        .withHostname("my-container-name")
                        .withCommand("hostname")
                        .build(),
                    "my-container-name"
                ),
                TestScenario(
                    "set environment variables for a container",
                    ContainerCreationSpec.Builder(image)
                        .withEnvironmentVariables("FIRST_VAR" to "the first value", "SECOND_VAR" to "the second value")
                        .withEnvironmentVariable("THIRD_VAR", "the third value")
                        .withCommand("sh", "-c", "echo First variable: \$FIRST_VAR && echo Second variable: \$SECOND_VAR && echo Third variable: \$THIRD_VAR")
                        .build(),
                    """
                        First variable: the first value
                        Second variable: the second value
                        Third variable: the third value
                    """.trimIndent()
                ),
                TestScenario(
                    "mount a file from the local machine into a container",
                    ContainerCreationSpec.Builder(image)
                        .withHostMount(hostMountDirectory.resolve("some-file.txt"), "/files/some-file.txt")
                        .withCommand("cat", "/files/some-file.txt")
                        .build(),
                    "This is the file mounted into the container."
                ),
                TestScenario(
                    "mount a directory from the local machine into a container",
                    ContainerCreationSpec.Builder(image)
                        .withHostMount(hostMountDirectory, "/files")
                        .withCommand("cat", "/files/some-file.txt")
                        .build(),
                    "This is the file mounted into the container."
                ),
                TestScenario(
                    "mount a file from the local machine into a container read-only",
                    ContainerCreationSpec.Builder(image)
                        .withHostMount(hostMountDirectory.resolve("some-file.txt"), "/files/some-file.txt", "ro")
                        .withCommand("rm", "/files/some-file.txt")
                        .build(),
                    expectedOutput = "",
                    expectedErrorOutput = "rm: can't remove '/files/some-file.txt': Resource busy",
                    shouldExitWithZeroExitCode = false
                ),
                TestScenario(
                    "mount a directory from the local machine into a container read-only",
                    ContainerCreationSpec.Builder(image)
                        .withHostMount(hostMountDirectory, "/files", "ro")
                        .withCommand("touch", "/files/some-other-file.txt")
                        .build(),
                    expectedOutput = "",
                    expectedErrorOutput = "touch: /files/some-other-file.txt: Read-only file system",
                    shouldExitWithZeroExitCode = false
                ),
                TestScenario(
                    "set the entrypoint for a container",
                    ContainerCreationSpec.Builder(image)
                        .withEntrypoint("echo", "Hello from the entrypoint")
                        .withCommand("Hello from the command")
                        .build(),
                    "Hello from the entrypoint Hello from the command"
                ),
                TestScenario(
                    "set the working directory for a container",
                    ContainerCreationSpec.Builder(image)
                        .withWorkingDirectory("/foo/bar")
                        .withCommand("pwd")
                        .build(),
                    "/foo/bar"
                ),
            ).forEach { scenario ->
                should("be able to ${scenario.description}") {
                    val container = client.createContainer(scenario.creationSpec)

                    try {
                        val stdout = Buffer()
                        val stderr = Buffer()

                        val exitCode = client.run(container, SinkTextOutput(stdout), SinkTextOutput(stderr))
                        val stdoutText = stdout.readUtf8()
                        val stderrText = stderr.readUtf8()

                        stdoutText.trim() shouldBe scenario.expectedOutput
                        stderrText.trim() shouldBe scenario.expectedErrorOutput

                        if (scenario.shouldExitWithZeroExitCode) {
                            exitCode shouldBe 0
                        } else {
                            exitCode shouldNotBe 0
                        }
                    } finally {
                        client.removeContainer(container, force = true)
                    }
                }
            }

            should("be able to add additional hosts to a container's hosts file") {
                val spec = ContainerCreationSpec.Builder(image)
                    .withExtraHost("host-1", "1.2.3.1")
                    .withExtraHost("host-2", "1.2.3.2")
                    .withCommand("cat", "/etc/hosts")
                    .build()

                val container = client.createContainer(spec)

                try {
                    val stdout = Buffer()
                    val stderr = Buffer()

                    val exitCode = client.run(container, SinkTextOutput(stdout), SinkTextOutput(stderr))
                    val stdoutText = stdout.readUtf8()
                    val stderrText = stderr.readUtf8()

                    exitCode shouldBe 0
                    stdoutText shouldContain """^1.2.3.1\s+host-1$""".toRegex(RegexOption.MULTILINE)
                    stdoutText shouldContain """^1.2.3.2\s+host-2$""".toRegex(RegexOption.MULTILINE)
                    stderrText shouldBe ""
                } finally {
                    client.removeContainer(container, force = true)
                }
            }

            suspend fun createFileInVolume(volume: VolumeReference, path: String) {
                val spec = ContainerCreationSpec.Builder(image)
                    .withVolumeMount(volume, "/volume")
                    .withCommand("sh", "-c", "echo 'This is the file in the volume.' > $path")
                    .build()

                val container = client.createContainer(spec)

                try {
                    val stdout = Buffer()
                    val stderr = Buffer()

                    val exitCode = client.run(container, SinkTextOutput(stdout), SinkTextOutput(stderr))
                    val stdoutText = stdout.readUtf8()
                    val stderrText = stderr.readUtf8()

                    exitCode shouldBe 0
                    stdoutText.trim() shouldBe ""
                    stderrText.trim() shouldBe ""
                } finally {
                    client.removeContainer(container, force = true)
                }
            }

            should("be able to mount a volume into a container") {
                val volume = client.createVolume("${DockerClientContainerManagementSpec::class.simpleName}-volume-test-${Random.nextInt()}")

                try {
                    createFileInVolume(volume, "/volume/some-other-file.txt")

                    val spec = ContainerCreationSpec.Builder(image)
                        .withVolumeMount(volume, "/volume")
                        .withCommand("cat", "/volume/some-other-file.txt")
                        .build()

                    val container = client.createContainer(spec)

                    try {
                        val stdout = Buffer()
                        val stderr = Buffer()

                        val exitCode = client.run(container, SinkTextOutput(stdout), SinkTextOutput(stderr))
                        val stdoutText = stdout.readUtf8()
                        val stderrText = stderr.readUtf8()

                        exitCode shouldBe 0
                        stdoutText.trim() shouldBe "This is the file in the volume."
                        stderrText.trim() shouldBe ""
                    } finally {
                        client.removeContainer(container, force = true)
                    }
                } finally {
                    client.deleteVolume(volume)
                }
            }

            should("be able to mount a volume into a container read-only") {
                val volume = client.createVolume("${DockerClientContainerManagementSpec::class.simpleName}-read-only-volume-test-${Random.nextInt()}")

                try {
                    val spec = ContainerCreationSpec.Builder(image)
                        .withVolumeMount(volume, "/files", "ro")
                        .withCommand("touch", "/files/some-other-file.txt")
                        .build()

                    val container = client.createContainer(spec)

                    try {
                        val stdout = Buffer()
                        val stderr = Buffer()

                        val exitCode = client.run(container, SinkTextOutput(stdout), SinkTextOutput(stderr))
                        val stdoutText = stdout.readUtf8()
                        val stderrText = stderr.readUtf8()

                        exitCode shouldNotBe 0
                        stdoutText.trim() shouldBe ""
                        stderrText.trim() shouldBe "touch: /files/some-other-file.txt: Read-only file system"
                    } finally {
                        client.removeContainer(container, force = true)
                    }
                } finally {
                    client.deleteVolume(volume)
                }
            }

            should("be able to mount the Docker socket into a container") {
                val imageWithDockerCLI = client.pullImage("docker:20.10.15")

                val spec = ContainerCreationSpec.Builder(imageWithDockerCLI)
                    .withHostMount("/var/run/docker.sock".toPath(), "/var/run/docker.sock")
                    .withCommand("docker", "version", "--format", "{{ .Server.Arch }}")
                    .build()

                val container = client.createContainer(spec)

                try {
                    val stdout = Buffer()
                    val stderr = Buffer()

                    val exitCode = client.run(container, SinkTextOutput(stdout), SinkTextOutput(stderr))
                    val stdoutText = stdout.readUtf8()
                    val stderrText = stderr.readUtf8()

                    exitCode shouldBe 0
                    stdoutText.trim() shouldNotBe ""
                    stderrText.trim() shouldBe ""
                } finally {
                    client.removeContainer(container, force = true)
                }
            }
        }
    }
})
