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
import okio.Buffer
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.ExperimentalTime

@ExperimentalTime
@OptIn(ExperimentalKotest::class)
class DockerClientContainerExecSpec : ShouldSpec({
    val client = closeAfterTest(DockerClient.Builder().build())

    context("when working with Linux containers").onlyIfDockerDaemonSupportsLinuxContainers {
        val image = client.pullImage("alpine:3.15.0")

        suspend fun withTestContainer(user: suspend (ContainerReference) -> Unit) {
            val spec = ContainerCreationSpec.Builder(image)
                .withCommand("sh", "-c", "sleep 60")
                .build()

            val container = client.createContainer(spec)

            try {
                client.startContainer(container)

                user(container)
            } finally {
                client.stopContainer(container, 1.seconds)
                client.removeContainer(container, force = true)
            }
        }

        should("be able to create, run and stream output from a basic exec command") {
            withTestContainer { container ->
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

        should("be able to create and run a basic exec command without streaming I/O") {
            withTestContainer { container ->
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
            withTestContainer { container ->
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
            withTestContainer { container ->
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
    }
})
