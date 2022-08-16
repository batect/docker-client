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

import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe

class DockerClientConfigurationSpec : ShouldSpec({
    context("getting the default client configuration") {
        context("when no Docker-related environment variables are set") {
            val configuration = withNoDockerEnvironmentVariables {
                DockerClientConfiguration.default()
            }

            should("return the default Docker endpoint for the operating system") {
                val defaultDockerHost = when (testEnvironmentOperatingSystem) {
                    OperatingSystem.Windows -> "npipe:////./pipe/docker_engine"
                    OperatingSystem.Linux, OperatingSystem.MacOS -> "unix:///var/run/docker.sock"
                }

                configuration.host shouldBe defaultDockerHost
            }

            should("return the default Docker CLI configuration directory") {
                configuration.configurationDirectory shouldBe userHomeDirectory / ".docker"
            }

            should("disable TLS") {
                configuration.tls shouldBe null
            }
        }
    }
})

private fun <R> withNoDockerEnvironmentVariables(block: () -> R): R {
    withoutEnvironmentVariable("DOCKER_HOST") {
        return block()
    }
}
