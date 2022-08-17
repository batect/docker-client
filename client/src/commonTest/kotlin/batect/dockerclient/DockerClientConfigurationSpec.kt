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

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import okio.Path.Companion.toPath

class DockerClientConfigurationSpec : ShouldSpec({
    val testConfigurationDirectories = systemFileSystem.canonicalize("./src/commonTest/resources/cli-configuration".toPath())

    context("getting client configuration from a CLI context") {
        context("getting the default context") {
            context("when no Docker-related environment variables are set") {
                val configuration = withNoDockerEnvironmentVariables {
                    DockerClientConfiguration.fromCLIContext("default")
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

            context("when the DOCKER_HOST environment variable is set") {
                val configuration = withNoDockerEnvironmentVariables {
                    withEnvironmentVariable("DOCKER_HOST", "tcp://host:1234/docker.sock") {
                        DockerClientConfiguration.fromCLIContext("default")
                    }
                }

                should("use the Docker endpoint from that environment variable") {
                    configuration.host shouldBe "tcp://host:1234/docker.sock"
                }

                should("return the default Docker CLI configuration directory") {
                    configuration.configurationDirectory shouldBe userHomeDirectory / ".docker"
                }

                should("disable TLS") {
                    configuration.tls shouldBe null
                }
            }

            context("when the DOCKER_HOST environment variable is set to an invalid value") {
                should("throw an appropriate exception") {
                    val exception = shouldThrow<DockerClientException> {
                        withNoDockerEnvironmentVariables {
                            withEnvironmentVariable("DOCKER_HOST", "nonsense://host/docker.sock") {
                                DockerClientConfiguration.fromCLIContext("default")
                            }
                        }
                    }

                    exception.message shouldBe """value 'nonsense://host/docker.sock' for DOCKER_HOST environment variable is invalid: Invalid bind address format: nonsense://host/docker.sock"""
                }
            }

        }

        context("getting a non-default context") {
            val dockerConfigurationDirectory = testConfigurationDirectories / "with-context-metadata"

            context("when the context does not have TLS enabled") {
                val configuration = DockerClientConfiguration.fromCLIContext("no-tls", dockerConfigurationDirectory = dockerConfigurationDirectory)

                should("return the configured Docker endpoint") {
                    configuration.host shouldBe "unix:///Users/theuser/some/docker.sock"
                }

                should("return the Docker CLI configuration directory provided") {
                    configuration.configurationDirectory shouldBe dockerConfigurationDirectory
                }

                should("disable TLS") {
                    configuration.tls shouldBe null
                }
            }

            context("when the context does have TLS enabled") {
                val configuration = DockerClientConfiguration.fromCLIContext("tls", dockerConfigurationDirectory = dockerConfigurationDirectory)

                should("return the configured Docker endpoint") {
                    configuration.host shouldBe "tcp://myserver:2376"
                }

                should("return the Docker CLI configuration directory provided") {
                    configuration.configurationDirectory shouldBe dockerConfigurationDirectory
                }

                should("enable identity verification") {
                    configuration.daemonIdentityVerification shouldBe TLSVerification.Enabled
                }

                should("return the TLS keys and certificates configured for the context") {
                    val tlsDirectory = dockerConfigurationDirectory / "contexts" / "tls" / "b7e651cbb43ba0ca3498759c8c3596c3a11a199004cd9e5a198d50d4585ec8c5" / "docker"

                    configuration.tls shouldBe DockerClientTLSConfiguration(
                        systemFileSystem.readAllBytes(tlsDirectory / "ca.pem"),
                        systemFileSystem.readAllBytes(tlsDirectory / "cert.pem"),
                        systemFileSystem.readAllBytes(tlsDirectory / "key.pem")
                    )
                }
            }
        }

        context("attempting to retrieve a context that does not exist") {
            should("throw an appropriate exception") {
                val exception = shouldThrow<DockerClientException> { DockerClientConfiguration.fromCLIContext("this-context-does-not-exist") }

                exception.message shouldBe """context "this-context-does-not-exist" does not exist"""
            }
        }
    }

    context("getting the active CLI context") {
        context("given there is a context selected in the Docker CLI's configuration file") {
            val dockerConfigurationDirectory = testConfigurationDirectories / "with-active-context"

            context("when both the DOCKER_HOST and DOCKER_CONTEXT environment variables are set") {
                val activeCLIContext = withNoDockerEnvironmentVariables {
                    withEnvironmentVariable("DOCKER_HOST", "proto://host/docker.sock") {
                        withEnvironmentVariable("DOCKER_CONTEXT", "my-environment-context") {
                            DockerClientConfiguration.getActiveCLIContext(dockerConfigurationDirectory)
                        }
                    }
                }

                should("return the 'default' context to use the DOCKER_HOST environment variable") {
                    activeCLIContext shouldBe "default"
                }
            }

            context("when only the DOCKER_HOST environment variable is set") {
                val activeCLIContext = withNoDockerEnvironmentVariables {
                    withEnvironmentVariable("DOCKER_HOST", "proto://host/docker.sock") {
                        DockerClientConfiguration.getActiveCLIContext(dockerConfigurationDirectory)
                    }
                }

                should("return the 'default' context to use the DOCKER_HOST environment variable") {
                    activeCLIContext shouldBe "default"
                }
            }

            context("when only the DOCKER_CONTEXT environment variable is set") {
                val activeCLIContext = withNoDockerEnvironmentVariables {
                    withEnvironmentVariable("DOCKER_CONTEXT", "my-environment-context") {
                        DockerClientConfiguration.getActiveCLIContext(dockerConfigurationDirectory)
                    }
                }

                should("return the context from the DOCKER_CONTEXT environment variable") {
                    activeCLIContext shouldBe "my-environment-context"
                }
            }

            context("when neither environment variable is set") {
                val activeCLIContext = withNoDockerEnvironmentVariables {
                    DockerClientConfiguration.getActiveCLIContext(dockerConfigurationDirectory)
                }

                should("return the selected context from the configuration file") {
                    activeCLIContext shouldBe "my-configured-context"
                }
            }
        }

        context("given there is not a context selected in the Docker CLI's configuration file") {
            val dockerConfigurationDirectory = testConfigurationDirectories / "without-active-context"

            context("when neither environment variable is set") {
                val activeCLIContext = withNoDockerEnvironmentVariables {
                    DockerClientConfiguration.getActiveCLIContext(dockerConfigurationDirectory)
                }

                should("return the 'default' context") {
                    activeCLIContext shouldBe "default"
                }
            }
        }
    }
})

private fun <R> withNoDockerEnvironmentVariables(block: () -> R): R {
    withoutEnvironmentVariable("DOCKER_HOST") {
        withoutEnvironmentVariable("DOCKER_CONTEXT") {
            return block()
        }
    }
}
