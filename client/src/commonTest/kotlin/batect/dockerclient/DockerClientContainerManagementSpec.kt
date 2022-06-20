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
import batect.dockerclient.io.SourceTextInput
import io.kotest.assertions.asClue
import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.assertions.timing.eventually
import io.kotest.common.ExperimentalKotest
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.inspectors.forAll
import io.kotest.matchers.collections.shouldHaveAtLeastSize
import io.kotest.matchers.collections.shouldHaveAtMostSize
import io.kotest.matchers.comparables.shouldBeGreaterThan
import io.kotest.matchers.comparables.shouldBeLessThan
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldStartWith
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.withTimeout
import kotlinx.datetime.Clock
import okio.Buffer
import okio.Path
import okio.Path.Companion.toPath
import okio.Sink
import okio.Source
import okio.Timeout
import kotlin.random.Random
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.ExperimentalTime
import kotlin.time.measureTime

@ExperimentalTime
@OptIn(ExperimentalKotest::class)
class DockerClientContainerManagementSpec : ShouldSpec({
    val client = closeAfterTest(DockerClient.Builder().build())

    context("when working with Linux containers").onlyIfDockerDaemonSupportsLinuxContainers {
        val image = client.pullImage("alpine:3.15.0")

        suspend fun buildTestImage(name: String): ImageReference {
            val path = systemFileSystem.canonicalize("./src/commonTest/resources/images/$name".toPath())
            val spec = ImageBuildSpec.Builder(path).build()

            return client.buildImage(spec, SinkTextOutput(Buffer()))
        }

        context("using low-level methods") {
            should("be able to create, start, wait for and remove a container") {
                val spec = ContainerCreationSpec.Builder(image)
                    .withCommand("sh", "-c", "exit 123")
                    .build()

                val container = client.createContainer(spec)

                try {
                    val waitingForContainerToExit = ReadyNotification()
                    val exitCodeSource = async { client.waitForContainerToExit(container, waitingForContainerToExit) }

                    launch {
                        waitingForContainerToExit.waitForReady()
                        client.startContainer(container)
                    }

                    exitCodeSource.await() shouldBe 123
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

                    coroutineScope {
                        val listeningToOutput = ReadyNotification()

                        launch {
                            client.attachToContainerIO(container, SinkTextOutput(stdout), SinkTextOutput(stderr), null, listeningToOutput)
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

            should("write container output to the provided output sink without any buffering") {
                val spec = ContainerCreationSpec.Builder(image)
                    .withCommand("sh", "-c", "for i in 1 2; do sleep 1; echo Line \$i; done")
                    .build()

                val container = client.createContainer(spec)

                try {
                    val stdout = object : Sink {
                        val writesReceived = mutableListOf<String>()

                        override fun timeout(): Timeout = Timeout.NONE
                        override fun flush() {}
                        override fun close() {}

                        override fun write(source: Buffer, byteCount: Long) {
                            writesReceived += source.readUtf8(byteCount)
                        }
                    }

                    coroutineScope {
                        val listeningToOutput = ReadyNotification()

                        launch {
                            client.attachToContainerIO(container, SinkTextOutput(stdout), null, null, listeningToOutput)
                        }

                        launch {
                            listeningToOutput.waitForReady()
                            client.startContainer(container)
                        }
                    }

                    // If the output is streamed with any kind of buffering, it will be received by our test sink above as one write.
                    stdout.writesReceived shouldBe listOf(
                        "Line 1\n",
                        "Line 2\n"
                    )
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

            should("throw an appropriate exception when creating a container with network aliases but no explicit network") {
                val spec = ContainerCreationSpec.Builder(image)
                    .withNetworkAlias("some-alias")
                    .build()

                val exception = shouldThrow<ContainerCreationFailedException> { client.createContainer(spec) }

                exception.message shouldBe "Container creation spec is not valid: must provide explicit network if using network aliases."
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
                            withTimeout(2.seconds) {
                                val listeningToOutput = ReadyNotification()

                                launch {
                                    client.attachToContainerIO(container, SinkTextOutput(stdout), SinkTextOutput(stderr), null, listeningToOutput)
                                }

                                launch {
                                    listeningToOutput.waitForReady()
                                    client.startContainer(container)
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

            should("be able to use Kotlin timeouts to abort stopping a container") {
                val spec = ContainerCreationSpec.Builder(image)
                    .withCommand("sh", "-c", "sleep 5") // `sleep` does not respond to signals, so this will wait for five seconds then exit
                    .build()

                val container = client.createContainer(spec)

                try {
                    client.startContainer(container)

                    val duration = measureTime {
                        shouldThrow<TimeoutCancellationException> {
                            withTimeout(500.milliseconds) {
                                client.stopContainer(container, 1.seconds)
                            }
                        }
                    }

                    duration shouldBeLessThan 1.seconds
                } finally {
                    client.removeContainer(container, force = true)
                }
            }

            should("throw an appropriate exception when inspecting a container that doesn't exist") {
                val exception = shouldThrow<ContainerInspectionFailedException> { client.inspectContainer("does-not-exist") }

                exception.message shouldBe "No such container: does-not-exist"
            }

            should("be able to set a container's name") {
                val spec = ContainerCreationSpec.Builder(image)
                    .withName("my-test-container")
                    .build()

                val container = client.createContainer(spec)

                try {
                    val inspectionResult = client.inspectContainer(container.id)

                    inspectionResult.reference shouldBe container
                    inspectionResult.name shouldBe "/my-test-container"
                } finally {
                    client.removeContainer(container, force = true)
                }
            }

            should("be able to inspect a container by name") {
                val spec = ContainerCreationSpec.Builder(image)
                    .withName("my-test-container")
                    .build()

                val container = client.createContainer(spec)

                try {
                    val inspectionResult = client.inspectContainer("my-test-container")

                    inspectionResult.reference shouldBe container
                    inspectionResult.name shouldBe "/my-test-container"
                } finally {
                    client.removeContainer(container, force = true)
                }
            }

            should("be able to configure logging for a container") {
                val loggingOptions = mapOf("gelf-address" to "udp://127.0.0.1:12201")

                val spec = ContainerCreationSpec.Builder(image)
                    .withLogDriver("gelf")
                    .withLoggingOptions(loggingOptions)
                    .build()

                val container = client.createContainer(spec)

                try {
                    val inspectionResult = client.inspectContainer(container)

                    inspectionResult.hostConfig.logConfig.asClue {
                        it.type shouldBe "gelf"
                        it.config shouldBe loggingOptions
                    }
                } finally {
                    client.removeContainer(container, force = true)
                }
            }

            should("be able to create a container that uses healthcheck configuration from its image") {
                val healthcheckImage = buildTestImage("healthcheck")

                val spec = ContainerCreationSpec.Builder(healthcheckImage)
                    .build()

                val container = client.createContainer(spec)

                try {
                    val inspectionResult = client.inspectContainer(container)

                    inspectionResult.config.healthcheck.shouldNotBeNull()
                    inspectionResult.config.healthcheck!!.asClue {
                        it.test shouldBe listOf("CMD-SHELL", "/healthcheck.sh")
                        it.interval shouldBe 200.milliseconds
                        it.timeout shouldBe 1.seconds
                        it.startPeriod shouldBe 500.milliseconds
                        it.retries shouldBe 2
                    }
                } finally {
                    client.removeContainer(container, force = true)
                }
            }

            should("be able to create a container that overrides the healthcheck configuration embedded in its image") {
                val healthcheckImage = buildTestImage("healthcheck")

                val spec = ContainerCreationSpec.Builder(healthcheckImage)
                    .withHealthcheckCommand("/alternative-healthcheck.sh")
                    .withHealthcheckInterval(250.milliseconds)
                    .withHealthcheckTimeout(1500.milliseconds)
                    .withHealthcheckStartPeriod(550.milliseconds)
                    .withHealthcheckRetries(3)
                    .build()

                val container = client.createContainer(spec)

                try {
                    val inspectionResult = client.inspectContainer(container)

                    inspectionResult.config.healthcheck.shouldNotBeNull()
                    inspectionResult.config.healthcheck!!.asClue {
                        it.test shouldBe listOf("CMD-SHELL", "/alternative-healthcheck.sh")
                        it.interval shouldBe 250.milliseconds
                        it.timeout shouldBe 1500.milliseconds
                        it.startPeriod shouldBe 550.milliseconds
                        it.retries shouldBe 3
                    }
                } finally {
                    client.removeContainer(container, force = true)
                }
            }

            should("be able to inspect a container's healthcheck results") {
                val healthcheckImage = buildTestImage("healthcheck")

                val spec = ContainerCreationSpec.Builder(healthcheckImage)
                    .withCommand("sh", "-c", "sleep 1; ls /healthchecks")
                    .build()

                val container = client.createContainer(spec)

                try {
                    val stdout = Buffer()
                    val stderr = Buffer()
                    val startedAt = Clock.System.now()

                    val listeningToOutput = ReadyNotification()
                    val waitingForExitCode = ReadyNotification()

                    launch {
                        client.attachToContainerIO(container, SinkTextOutput(stdout), SinkTextOutput(stderr), null, listeningToOutput)
                    }

                    val exitCodeSource = async {
                        client.waitForContainerToExit(container, waitingForExitCode)
                    }

                    listeningToOutput.waitForReady()
                    waitingForExitCode.waitForReady()
                    client.startContainer(container)

                    eventually(1.seconds, poll = 100.milliseconds) {
                        // We must inspect the container while it is running - otherwise Docker will always consider the container unhealthy if it is stopped.
                        val inspectionResult = client.inspectContainer(container)

                        inspectionResult.state.health.shouldNotBeNull()
                        inspectionResult.state.health!!.asClue {
                            it.status shouldBe "healthy"
                            it.log shouldHaveAtLeastSize 1

                            // On Windows and macOS machines where Docker is running in a VM, it's possible that the daemon's clock is out of sync with the machine's clock.
                            val clockSkewFudgeFactor = 60.seconds

                            it.log.forAll { log ->
                                log.start shouldBeLessThan log.end
                                log.start shouldBeGreaterThan startedAt.minus(clockSkewFudgeFactor)
                                log.end shouldBeLessThan Clock.System.now()
                                log.exitCode shouldBe 0
                                log.output shouldBe "Healthy!\n"
                            }
                        }
                    }

                    val exitCode = exitCodeSource.await()
                    val stdoutText = stdout.readUtf8()
                    val stderrText = stderr.readUtf8()

                    stdoutText.lines() shouldHaveAtLeastSize 4
                    stdoutText.lines() shouldHaveAtMostSize 6
                    stderrText shouldBe ""
                    exitCode shouldBe 0
                } finally {
                    client.removeContainer(container, force = true)
                }
            }

            should("be able to add labels to containers") {
                val spec = ContainerCreationSpec.Builder(image)
                    .withLabels("first-key" to "first-value", "second-key" to "second-value")
                    .build()

                val container = client.createContainer(spec)

                try {
                    val inspectionResult = client.inspectContainer(container)

                    inspectionResult.config.labels shouldBe mapOf(
                        "first-key" to "first-value",
                        "second-key" to "second-value"
                    )
                } finally {
                    client.removeContainer(container, force = true)
                }
            }
        }

        context("using the run() helper method") {
            val privilegesCheckImage = buildTestImage("privileges-check")
            val defaultCommandImage = buildTestImage("default-command")
            val defaultEntrypointImage = buildTestImage("default-entrypoint")
            val hostMountDirectory: Path = systemFileSystem.canonicalize("./src/commonTest/resources/container-mount-directory".toPath())

            should("be able to run a basic container") {
                val spec = ContainerCreationSpec.Builder(image)
                    .withCommand("sh", "-c", "echo 'Hello stdout' >/dev/stdout && echo 'Hello stderr' >/dev/stderr && exit 123")
                    .build()

                val container = client.createContainer(spec)

                try {
                    val stdout = Buffer()
                    val stderr = Buffer()

                    val exitCode = client.run(container, SinkTextOutput(stdout), SinkTextOutput(stderr), null)
                    val stdoutText = stdout.readUtf8()
                    val stderrText = stderr.readUtf8()

                    exitCode shouldBe 123
                    stdoutText shouldBe "Hello stdout\n"
                    stderrText shouldBe "Hello stderr\n"
                } finally {
                    client.removeContainer(container, force = true)
                }
            }

            should("be able to reuse output streams") {
                suspend fun runContainer(stdout: SinkTextOutput, stderr: SinkTextOutput, name: String) {
                    val spec = ContainerCreationSpec.Builder(image)
                        .withCommand("sh", "-c", "echo 'Hello stdout from $name' >/dev/stdout && echo 'Hello stderr from $name' >/dev/stderr && exit 123")
                        .build()

                    val container = client.createContainer(spec)

                    try {
                        val exitCode = client.run(container, stdout, stderr, null)
                        exitCode shouldBe 123
                    } finally {
                        client.removeContainer(container, force = true)
                    }
                }

                val stdout = Buffer()
                val stdoutOutput = SinkTextOutput(stdout)
                val stderr = Buffer()
                val stderrOutput = SinkTextOutput(stderr)

                runContainer(stdoutOutput, stderrOutput, "first")
                runContainer(stdoutOutput, stderrOutput, "second")

                val stdoutText = stdout.readUtf8()
                val stderrText = stderr.readUtf8()

                stdoutText shouldBe "Hello stdout from first\nHello stdout from second\n"
                stderrText shouldBe "Hello stderr from first\nHello stderr from second\n"
            }

            should("be able to stream input to a container") {
                val spec = ContainerCreationSpec.Builder(image)
                    .withCommand("sh", "-c", "cat >/input.txt && echo \"Size of file: $(stat -c %s input.txt)\" && cat input.txt && exit 123")
                    .withStdinAttached()
                    .build()

                val container = client.createContainer(spec)

                try {
                    val stdout = Buffer()
                    val stderr = Buffer()
                    val stdin = Buffer()
                    stdin.writeUtf8("Hello world!\nThis is some input.")
                    stdin.close()

                    val exitCode = client.run(container, SinkTextOutput(stdout), SinkTextOutput(stderr), SourceTextInput(stdin))
                    val stdoutText = stdout.readUtf8()
                    val stderrText = stderr.readUtf8()

                    exitCode shouldBe 123
                    stdoutText shouldBe "Size of file: 32\nHello world!\nThis is some input."
                    stderrText shouldBe ""
                } finally {
                    client.removeContainer(container, force = true)
                }
            }

            should("be able to stream input to a container without closing the input stream") {
                val spec = ContainerCreationSpec.Builder(image)
                    .withCommand("sh", "-c", "read input_received && echo \"Received input: '\$input_received'\" && exit 123")
                    .withStdinAttached()
                    .build()

                val container = client.createContainer(spec)

                try {
                    val stdout = Buffer()
                    val stderr = Buffer()
                    val stdin = object : Source {
                        private var haveSentOutput = false
                        private var closedLatch = Semaphore(1, 1)

                        override fun timeout(): Timeout = Timeout.NONE

                        override fun read(sink: Buffer, byteCount: Long): Long {
                            return when (haveSentOutput) {
                                false -> readInput(sink, byteCount)
                                true -> {
                                    waitForClose()
                                    return -1
                                }
                            }
                        }

                        private fun readInput(sink: Buffer, @Suppress("UNUSED_PARAMETER") byteCount: Long): Long {
                            haveSentOutput = true

                            val bytes = "Hello world!\n".encodeToByteArray()
                            sink.write(bytes)

                            return bytes.size.toLong()
                        }

                        private fun waitForClose() = runBlocking {
                            closedLatch.acquire()
                        }

                        override fun close() {
                            closedLatch.release()
                        }
                    }

                    val exitCode = client.run(container, SinkTextOutput(stdout), SinkTextOutput(stderr), SourceTextInput(stdin))
                    val stdoutText = stdout.readUtf8()
                    val stderrText = stderr.readUtf8()

                    exitCode shouldBe 123
                    stdoutText shouldBe "Received input: 'Hello world!'\n"
                    stderrText shouldBe ""
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
                                client.run(container, SinkTextOutput(stdout), SinkTextOutput(stderr), null)
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
                TestScenario(
                    "use the default working directory",
                    ContainerCreationSpec.Builder(image)
                        .withCommand("pwd")
                        .build(),
                    "/"
                ),
                TestScenario(
                    "mount a tmpfs filesystem into a container",
                    ContainerCreationSpec.Builder(image)
                        .withTmpfsMount("/files")
                        .withCommand("touch", "/files/some-file.txt")
                        .build(),
                    expectedOutput = "",
                    expectedErrorOutput = "",
                ),
                TestScenario(
                    "mount a tmpfs filesystem into a container with options",
                    ContainerCreationSpec.Builder(image)
                        .withTmpfsMount("/files", "ro")
                        .withCommand("touch", "/files/some-other-file.txt")
                        .build(),
                    expectedOutput = "",
                    expectedErrorOutput = "touch: /files/some-other-file.txt: Read-only file system",
                    shouldExitWithZeroExitCode = false
                ),
                TestScenario(
                    "mount a device into a container",
                    ContainerCreationSpec.Builder(image)
                        .withDeviceMount("/dev/null".toPath(), "/dev/my-other-null")
                        .withCommand("cat", "/dev/my-other-null")
                        .build(),
                    expectedOutput = "",
                    expectedErrorOutput = "",
                ),
                TestScenario(
                    "set the user and group for a container",
                    ContainerCreationSpec.Builder(image)
                        .withUserAndGroup(123, 456)
                        .withCommand("sh", "-c", "id -u && id -g")
                        .build(),
                    "123\n456"
                ),
                TestScenario(
                    "use the default user and group",
                    ContainerCreationSpec.Builder(image)
                        .withCommand("sh", "-c", "id -u && id -g")
                        .build(),
                    "0\n0"
                ),
                TestScenario(
                    "run a container without an init process",
                    ContainerCreationSpec.Builder(image)
                        .withCommand("ps", "-o", "pid,comm")
                        .build(),
                    """
                        |PID   COMMAND
                        |    1 ps
                    """.trimMargin()
                ),
                TestScenario(
                    "use the default size for /dev/shm",
                    ContainerCreationSpec.Builder(image)
                        .withCommand("df", "-k", "/dev/shm")
                        .build(),
                    """
                        |Filesystem           1K-blocks      Used Available Use% Mounted on
                        |shm                      65536         0     65536   0% /dev/shm
                    """.trimMargin()
                ),
                TestScenario(
                    "use a custom size for /dev/shm",
                    ContainerCreationSpec.Builder(image)
                        .withCommand("df", "-k", "/dev/shm")
                        .withShmSize(20480)
                        .build(),
                    """
                        |Filesystem           1K-blocks      Used Available Use% Mounted on
                        |shm                         20         0        20   0% /dev/shm
                    """.trimMargin()
                ),
                TestScenario(
                    "run without a TTY attached",
                    ContainerCreationSpec.Builder(image)
                        .withCommand(
                            "sh",
                            "-c",
                            """
                                if [ -t 0 ]; then
                                    echo 'Is a TTY'
                                else
                                    echo 'Is not a TTY'
                                fi

                                echo "This is stdout" >/dev/stdout
                                echo "This is stderr" >/dev/stderr
                            """.trimIndent()
                        )
                        .build(),
                    expectedOutput = """
                            Is not a TTY
                            This is stdout
                    """.trimIndent(),
                    expectedErrorOutput = "This is stderr"
                ),
                TestScenario(
                    "run with a TTY attached",
                    ContainerCreationSpec.Builder(image)
                        .withCommand(
                            "sh",
                            "-c",
                            """
                                if [ -t 0 ]; then
                                    echo 'Is a TTY'
                                else
                                    echo 'Is not a TTY'
                                fi

                                echo "This is stdout" >/dev/stdout
                                echo "This is stderr" >/dev/stderr
                            """.trimIndent()
                        )
                        .withTTY()
                        .build(),
                    expectedOutput = "Is a TTY\r\nThis is stdout\r\nThis is stderr",
                    expectedErrorOutput = ""
                ),
                TestScenario(
                    "run an unprivileged container",
                    ContainerCreationSpec.Builder(privilegesCheckImage)
                        .build(),
                    """
                        Container does not have NET_ADMIN capability
                        Container has CHOWN capability
                    """.trimIndent()
                ),
                TestScenario(
                    "run a privileged container",
                    ContainerCreationSpec.Builder(privilegesCheckImage)
                        .withPrivileged()
                        .build(),
                    """
                        Container has NET_ADMIN capability
                        Container has CHOWN capability
                    """.trimIndent()
                ),
                TestScenario(
                    "run a container with additional capabilities",
                    ContainerCreationSpec.Builder(privilegesCheckImage)
                        .withCapabilityAdded(Capability.NET_ADMIN)
                        .build(),
                    """
                        Container has NET_ADMIN capability
                        Container has CHOWN capability
                    """.trimIndent()
                ),
                TestScenario(
                    "run a container with reduced capabilities",
                    ContainerCreationSpec.Builder(privilegesCheckImage)
                        .withCapabilityDropped(Capability.CHOWN)
                        .build(),
                    """
                        Container does not have NET_ADMIN capability
                        Container does not have CHOWN capability
                    """.trimIndent()
                ),
                TestScenario(
                    "use the default command on a container",
                    ContainerCreationSpec.Builder(defaultCommandImage)
                        .build(),
                    "This is the default command"
                ),
                TestScenario(
                    "override the default command on a container",
                    ContainerCreationSpec.Builder(defaultCommandImage)
                        .withCommand("echo", "This is the overriding command")
                        .build(),
                    "This is the overriding command"
                ),
                TestScenario(
                    "use the default entrypoint on a container",
                    ContainerCreationSpec.Builder(defaultEntrypointImage)
                        .build(),
                    "This is the default entrypoint"
                ),
                TestScenario(
                    "override the default entrypoint on a container",
                    ContainerCreationSpec.Builder(defaultEntrypointImage)
                        .withEntrypoint("echo", "This is the overriding entrypoint")
                        .build(),
                    "This is the overriding entrypoint"
                ),
            ).forEach { scenario ->
                should("be able to ${scenario.description}") {
                    val container = client.createContainer(scenario.creationSpec)

                    try {
                        val stdout = Buffer()
                        val stderr = Buffer()

                        val exitCode = client.run(container, SinkTextOutput(stdout), SinkTextOutput(stderr), null)
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

                    val exitCode = client.run(container, SinkTextOutput(stdout), SinkTextOutput(stderr), null)
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

                    val exitCode = client.run(container, SinkTextOutput(stdout), SinkTextOutput(stderr), null)
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

                        val exitCode = client.run(container, SinkTextOutput(stdout), SinkTextOutput(stderr), null)
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

                        val exitCode = client.run(container, SinkTextOutput(stdout), SinkTextOutput(stderr), null)
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

            should("be able to mount the Docker socket into a container").onlyIfNotConnectingToDaemonOverTCP {
                val imageWithDockerCLI = client.pullImage("docker:20.10.15")

                val spec = ContainerCreationSpec.Builder(imageWithDockerCLI)
                    .withHostMount("/var/run/docker.sock".toPath(), "/var/run/docker.sock")
                    .withCommand("docker", "version", "--format", "{{ .Server.Arch }}")
                    .build()

                val container = client.createContainer(spec)

                try {
                    val stdout = Buffer()
                    val stderr = Buffer()

                    val exitCode = client.run(container, SinkTextOutput(stdout), SinkTextOutput(stderr), null)
                    val stdoutText = stdout.readUtf8()
                    val stderrText = stderr.readUtf8()

                    exitCode shouldBe 0
                    stdoutText.trim() shouldNotBe ""
                    stderrText.trim() shouldBe ""
                } finally {
                    client.removeContainer(container, force = true)
                }
            }

            should("be able to run a container with an init process") {
                val spec = ContainerCreationSpec.Builder(image)
                    .withCommand("ps", "-o", "pid,comm")
                    .withInitProcess()
                    .build()

                val container = client.createContainer(spec)

                try {
                    val stdout = Buffer()
                    val stderr = Buffer()

                    val exitCode = client.run(container, SinkTextOutput(stdout), SinkTextOutput(stderr), null)
                    val stdoutText = stdout.readUtf8()
                    val stderrText = stderr.readUtf8()

                    exitCode shouldBe 0

                    stdoutText.trim() shouldStartWith """
                        |PID   COMMAND
                        |    1 docker-init
                    """.trimMargin()

                    stdoutText shouldContain """^\s+\d+\sps$""".toRegex(RegexOption.MULTILINE)

                    stderrText.trim() shouldBe ""
                } finally {
                    client.removeContainer(container, force = true)
                }
            }

            should("be able to run a container in a non-default network with an alias") {
                val networkingImage = buildTestImage("networking")
                val network = client.createNetwork("container-networking-test-${Random.nextInt()}", "bridge")

                try {
                    val spec = ContainerCreationSpec.Builder(networkingImage)
                        .withNetwork(network)
                        .withNetworkAlias("the-test-container")
                        .withCommand(
                            "sh",
                            "-c",
                            """
                                ETH0_IP=$(ifconfig eth0 | grep 'inet ' | sed -E 's/\s+inet ([0-9]+\.[0-9]+\.[0-9]+\.[0-9]+).*/\1/g')
                                ALIAS_IP=$(nslookup the-test-container | tail -n2 | grep 'Address: ' | cut -d: -f2)

                                echo "eth0 is ${'$'}ETH0_IP"
                                echo "alias is ${'$'}{ALIAS_IP## }"
                            """.trimIndent()
                        )
                        .build()

                    val container = client.createContainer(spec)

                    try {
                        val stdout = Buffer()
                        val stderr = Buffer()

                        val exitCode = client.run(container, SinkTextOutput(stdout), SinkTextOutput(stderr), null)
                        val stdoutText = stdout.readUtf8()
                        val stderrText = stderr.readUtf8()

                        stdoutText shouldContain """
                            eth0 is (\d+\.\d+\.\d+\.\d+)
                            alias is \1
                        """.trimIndent().toRegex()

                        stderrText.trim() shouldBe ""
                        exitCode shouldBe 0
                    } finally {
                        client.removeContainer(container, force = true)
                    }
                } finally {
                    client.deleteNetwork(network)
                }
            }
        }
    }

    context("when working with Windows containers").onlyIfDockerDaemonSupportsWindowsContainers {
        should("be able to run a basic container") {
            val imageTag = "mcr.microsoft.com/windows/nanoserver@sha256:4f06e1d8263b934d2e88dc1c6ff402f5b499c4d19ad6d0e2a5b9ee945f782928" // This is nanoserver:1809
            val image = client.getImage(imageTag) ?: client.pullImage(imageTag)

            val spec = ContainerCreationSpec.Builder(image)
                .withCommand("cmd", "/c", "echo Hello stdout && echo Hello stderr 1>&2 && exit 123")
                .build()

            val container = client.createContainer(spec)

            try {
                val stdout = Buffer()
                val stderr = Buffer()

                val exitCode = client.run(container, SinkTextOutput(stdout), SinkTextOutput(stderr), null)
                val stdoutText = stdout.readUtf8()
                val stderrText = stderr.readUtf8()

                exitCode shouldBe 123
                stdoutText shouldBe "Hello stdout \r\n"
                stderrText shouldBe "Hello stderr  \r\n"
            } finally {
                client.removeContainer(container, force = true)
            }
        }
    }
})
