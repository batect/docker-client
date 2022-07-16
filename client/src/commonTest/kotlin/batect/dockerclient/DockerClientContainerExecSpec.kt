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
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.assertions.timing.eventually
import io.kotest.common.ExperimentalKotest
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.collections.shouldBeOneOf
import io.kotest.matchers.comparables.shouldBeLessThan
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.withTimeout
import okio.Buffer
import okio.Sink
import okio.Source
import okio.Timeout
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.ExperimentalTime
import kotlin.time.measureTime

@ExperimentalTime
@OptIn(ExperimentalKotest::class)
class DockerClientContainerExecSpec : ShouldSpec({
    val client = closeAfterTest(DockerClient.Builder().build())

    context("when working with Linux containers").onlyIfDockerDaemonSupportsLinuxContainers {
        val image = client.pullImage("alpine:3.15.0")

        suspend fun withCreatedTestContainer(user: suspend (ContainerReference) -> Unit) {
            val spec = ContainerCreationSpec.Builder(image)
                .withCommand("sh", "-c", "sleep 60")
                .withEnvironmentVariable("FIRST_VARIABLE", "first provided by container")
                .withEnvironmentVariable("SECOND_VARIABLE", "second provided by container")
                .build()

            val container = client.createContainer(spec)

            try {
                user(container)
            } finally {
                client.stopContainer(container, 1.seconds)
                client.removeContainer(container, force = true)
            }
        }

        suspend fun withRunningTestContainer(user: suspend (ContainerReference) -> Unit) {
            withCreatedTestContainer { container ->
                client.startContainer(container)
                user(container)
            }
        }

        should("be able to create, run and stream output from a basic exec command") {
            withRunningTestContainer { container ->
                val spec = ContainerExecSpec.Builder(container)
                    .withCommand("sh", "-c", "echo 'Hello stdout' >/dev/stdout; echo 'Hello stderr' >/dev/stderr; exit 123")
                    .withStdoutAttached()
                    .withStderrAttached()
                    .build()

                val exec = client.createExec(spec)
                val stdout = Buffer()
                val stderr = Buffer()

                client.startAndAttachToExec(exec, false, SinkTextOutput(stdout), SinkTextOutput(stderr), null)

                val stdoutText = stdout.readUtf8()
                val stderrText = stderr.readUtf8()
                val inspectionResult = client.inspectExec(exec)

                stdoutText shouldBe "Hello stdout\n"
                stderrText shouldBe "Hello stderr\n"
                inspectionResult.exitCode shouldBe 123
            }
        }

        should("be able to use Kotlin timeouts to abort streaming I/O from an exec instance, while still receiving any output from before the timeout") {
            withRunningTestContainer { container ->
                val spec = ContainerExecSpec.Builder(container)
                    .withCommand(
                        "sh",
                        "-c",
                        "echo 'Hello stdout' >/dev/stdout && echo 'Hello stderr' >/dev/stderr && sleep 5 && echo 'Stdout should never receive this' >/dev/stdout && echo 'Stderr should never receive this' >/dev/stderr"
                    )
                    .withStdoutAttached()
                    .withStderrAttached()
                    .build()

                val exec = client.createExec(spec)
                val stdout = Buffer()
                val stderr = Buffer()

                val duration = measureTime {
                    shouldThrow<TimeoutCancellationException> {
                        withTimeout(2.seconds) {
                            client.startAndAttachToExec(exec, false, SinkTextOutput(stdout), SinkTextOutput(stderr), null)
                        }
                    }
                }

                val stdoutText = stdout.readUtf8()
                val stderrText = stderr.readUtf8()

                stdoutText shouldBe "Hello stdout\n"
                stderrText shouldBe "Hello stderr\n"

                duration shouldBeLessThan 5.seconds
            }
        }

        should("be able to write output to the provided output sink without any buffering") {
            withRunningTestContainer { container ->
                val spec = ContainerExecSpec.Builder(container)
                    .withCommand("sh", "-c", "for i in 1 2; do sleep 1; echo Line \$i; done")
                    .withStdoutAttached()
                    .build()

                val exec = client.createExec(spec)
                val stdout = object : Sink {
                    val writesReceived = mutableListOf<String>()

                    override fun timeout(): Timeout = Timeout.NONE
                    override fun flush() {}
                    override fun close() {}

                    override fun write(source: Buffer, byteCount: Long) {
                        writesReceived += source.readUtf8(byteCount)
                    }
                }

                client.startAndAttachToExec(exec, false, SinkTextOutput(stdout), null, null)

                // If the output is streamed with any kind of buffering, it will be received by our test sink above as one write.
                stdout.writesReceived shouldBe listOf(
                    "Line 1\n",
                    "Line 2\n"
                )
            }
        }

        should("be able to stream input to an exec instance") {
            withRunningTestContainer { container ->
                val spec = ContainerExecSpec.Builder(container)
                    .withCommand("sh", "-c", "cat >/input.txt && echo \"Size of file: $(stat -c %s input.txt)\" && cat input.txt && exit 123")
                    .withStdoutAttached()
                    .withStderrAttached()
                    .withStdinAttached()
                    .build()

                val exec = client.createExec(spec)
                val stdout = Buffer()
                val stderr = Buffer()
                val stdin = Buffer()
                stdin.writeUtf8("Hello world!\nThis is some input.")
                stdin.close()

                client.startAndAttachToExec(exec, false, SinkTextOutput(stdout), SinkTextOutput(stderr), SourceTextInput(stdin))

                val stdoutText = stdout.readUtf8()
                val stderrText = stderr.readUtf8()
                val inspectionResult = client.inspectExec(exec)

                stdoutText shouldBe "Size of file: 32\nHello world!\nThis is some input."
                stderrText shouldBe ""
                inspectionResult.exitCode shouldBe 123
            }
        }

        should("be able to stream input to an exec instance without closing the input stream") {
            withRunningTestContainer { container ->
                val spec = ContainerExecSpec.Builder(container)
                    .withCommand("sh", "-c", "read input_received && echo \"Received input: '\$input_received'\" && exit 123")
                    .withStdoutAttached()
                    .withStderrAttached()
                    .withStdinAttached()
                    .build()

                val exec = client.createExec(spec)
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

                client.startAndAttachToExec(exec, false, SinkTextOutput(stdout), SinkTextOutput(stderr), SourceTextInput(stdin))

                val stdoutText = stdout.readUtf8()
                val stderrText = stderr.readUtf8()
                val inspectionResult = client.inspectExec(exec)

                stdoutText shouldBe "Received input: 'Hello world!'\n"
                stderrText shouldBe ""
                inspectionResult.exitCode shouldBe 123
            }
        }

        should("be able to reuse output streams") {
            withRunningTestContainer { container ->
                suspend fun runExec(stdout: SinkTextOutput, stderr: SinkTextOutput, name: String) {
                    val spec = ContainerExecSpec.Builder(container)
                        .withCommand("sh", "-c", "echo 'Hello stdout from $name' >/dev/stdout && echo 'Hello stderr from $name' >/dev/stderr && exit 123")
                        .withStdoutAttached()
                        .withStderrAttached()
                        .build()

                    val exec = client.createExec(spec)
                    client.startAndAttachToExec(exec, false, stdout, stderr, null)

                    val inspectionResult = client.inspectExec(exec)
                    inspectionResult.exitCode shouldBe 123
                }

                val stdout = Buffer()
                val stdoutOutput = SinkTextOutput(stdout)
                val stderr = Buffer()
                val stderrOutput = SinkTextOutput(stderr)

                runExec(stdoutOutput, stderrOutput, "first")
                runExec(stdoutOutput, stderrOutput, "second")

                val stdoutText = stdout.readUtf8()
                val stderrText = stderr.readUtf8()

                stdoutText shouldBe "Hello stdout from first\nHello stdout from second\n"
                stderrText shouldBe "Hello stderr from first\nHello stderr from second\n"
            }
        }

        should("be able to create and run a basic exec command without streaming I/O") {
            withRunningTestContainer { container ->
                val spec = ContainerExecSpec.Builder(container)
                    .withCommand("sh", "-c", "exit 123")
                    .withStdoutAttached()
                    .withStderrAttached()
                    .build()

                val exec = client.createExec(spec)
                client.startExecDetached(exec, false)

                eventually(5.seconds, poll = 100.milliseconds) {
                    val inspectionResult = client.inspectExec(exec)
                    inspectionResult.exitCode shouldBe 123
                }
            }
        }

        should("be able to inspect an exec instance that hasn't been started yet") {
            withRunningTestContainer { container ->
                val spec = ContainerExecSpec.Builder(container)
                    .withCommand("sh", "-c", "exit 123")
                    .withStdoutAttached()
                    .withStderrAttached()
                    .build()

                val exec = client.createExec(spec)

                val result = client.inspectExec(exec)
                result.running shouldBe false
                result.exitCode shouldBe 0
            }
        }

        should("be able to inspect an exec instance that hasn't finished yet") {
            withRunningTestContainer { container ->
                val spec = ContainerExecSpec.Builder(container)
                    .withCommand("sh", "-c", "sleep 10")
                    .withStdoutAttached()
                    .withStderrAttached()
                    .build()

                val exec = client.createExec(spec)
                client.startExecDetached(exec, false)

                val result = client.inspectExec(exec)
                result.running shouldBe true
                result.exitCode shouldBe null
            }
        }

        should("throw an appropriate exception when attempting to create an exec instance for a container that hasn't been started") {
            withCreatedTestContainer { container ->
                val spec = ContainerExecSpec.Builder(container)
                    .withCommand("sh", "-c", "echo 'Hello stdout' >/dev/stdout; echo 'Hello stderr' >/dev/stderr; exit 123")
                    .withStdoutAttached()
                    .withStderrAttached()
                    .build()

                val exception = shouldThrow<ContainerExecCreationFailedException> {
                    client.createExec(spec)
                }

                exception.message shouldBe "Error response from daemon: Container ${container.id} is not running"
            }
        }

        should("throw an appropriate exception when attempting to create an exec instance for a container that has been stopped") {
            withCreatedTestContainer { container ->
                client.stopContainer(container, 1.seconds)

                val spec = ContainerExecSpec.Builder(container)
                    .withCommand("sh", "-c", "echo 'Hello stdout' >/dev/stdout; echo 'Hello stderr' >/dev/stderr; exit 123")
                    .withStdoutAttached()
                    .withStderrAttached()
                    .build()

                val exception = shouldThrow<ContainerExecCreationFailedException> {
                    client.createExec(spec)
                }

                exception.message shouldBe "Error response from daemon: Container ${container.id} is not running"
            }
        }

        should("throw an appropriate exception when attempting to create an exec instance with no command") {
            withRunningTestContainer { container ->
                val spec = ContainerExecSpec.Builder(container)
                    .withStdoutAttached()
                    .withStderrAttached()
                    .build()

                val exception = shouldThrow<ContainerExecCreationFailedException> {
                    client.createExec(spec)
                }

                exception.message shouldBe "Error response from daemon: No exec command specified"
            }
        }

        should("throw an appropriate exception when attempting to create an exec instance with an invalid command") {
            withRunningTestContainer { container ->
                val spec = ContainerExecSpec.Builder(container)
                    .withCommand("this-command-does-not-exist")
                    .withStdoutAttached()
                    .withStderrAttached()
                    .build()

                val exec = client.createExec(spec)

                val exception = shouldThrow<StartingContainerExecFailedException> {
                    client.startExecDetached(exec, false)
                }

                exception.message shouldBeOneOf setOf(
                    "Error response from daemon: OCI runtime exec failed: exec failed: container_linux.go:380: starting container process caused: exec: \"this-command-does-not-exist\": executable file not found in \$PATH: unknown",
                    "Error response from daemon: OCI runtime exec failed: exec failed: unable to start container process: exec: \"this-command-does-not-exist\": executable file not found in \$PATH: unknown"
                )
            }
        }

        should("be able to specify environment variables for an exec instance") {
            withRunningTestContainer { container ->
                val spec = ContainerExecSpec.Builder(container)
                    .withCommand("sh", "-c", "echo \"First variable: \$FIRST_VARIABLE\" && echo \"Second variable: \$SECOND_VARIABLE\" && echo \"Third variable: \$THIRD_VARIABLE\"")
                    .withStdoutAttached()
                    .withStderrAttached()
                    .withEnvironmentVariable("SECOND_VARIABLE", "second overridden by exec")
                    .withEnvironmentVariable("THIRD_VARIABLE", "third provided by exec")
                    .build()

                val exec = client.createExec(spec)
                val stdout = Buffer()
                val stderr = Buffer()

                client.startAndAttachToExec(exec, false, SinkTextOutput(stdout), SinkTextOutput(stderr), null)

                val stdoutText = stdout.readUtf8()
                val stderrText = stderr.readUtf8()
                val inspectionResult = client.inspectExec(exec)

                stdoutText shouldBe "First variable: first provided by container\nSecond variable: second overridden by exec\nThird variable: third provided by exec\n"
                stderrText shouldBe ""
                inspectionResult.exitCode shouldBe 0
            }
        }

        should("be able to set the working directory for an exec instance") {
            withRunningTestContainer { container ->
                val spec = ContainerExecSpec.Builder(container)
                    .withCommand("pwd")
                    .withWorkingDirectory("/tmp")
                    .withStdoutAttached()
                    .withStderrAttached()
                    .build()

                val exec = client.createExec(spec)
                val stdout = Buffer()
                val stderr = Buffer()

                client.startAndAttachToExec(exec, false, SinkTextOutput(stdout), SinkTextOutput(stderr), null)

                val stdoutText = stdout.readUtf8()
                val stderrText = stderr.readUtf8()
                val inspectionResult = client.inspectExec(exec)

                stdoutText shouldBe "/tmp\n"
                stderrText shouldBe ""
                inspectionResult.exitCode shouldBe 0
            }
        }
    }
})
