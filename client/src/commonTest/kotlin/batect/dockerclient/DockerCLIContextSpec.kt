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
import okio.Path.Companion.toPath

class DockerCLIContextSpec : ShouldSpec({
    val testConfigurationDirectories = systemFileSystem.canonicalize("./src/commonTest/resources/cli-configuration".toPath())

    context("getting the active CLI context") {
        context("given there is a context selected in the Docker CLI's configuration file") {
            val dockerConfigurationDirectory = testConfigurationDirectories / "with-active-context"

            context("when both the DOCKER_HOST and DOCKER_CONTEXT environment variables are set") {
                val activeCLIContext = withNoDockerEnvironmentVariables {
                    withEnvironmentVariable("DOCKER_HOST", "proto://host/docker.sock") {
                        withEnvironmentVariable("DOCKER_CONTEXT", "my-environment-context") {
                            DockerCLIContext.getActiveCLIContext(dockerConfigurationDirectory)
                        }
                    }
                }

                should("return the 'default' context to use the DOCKER_HOST environment variable") {
                    activeCLIContext shouldBe DockerCLIContext("default")
                }
            }

            context("when only the DOCKER_HOST environment variable is set") {
                val activeCLIContext = withNoDockerEnvironmentVariables {
                    withEnvironmentVariable("DOCKER_HOST", "proto://host/docker.sock") {
                        DockerCLIContext.getActiveCLIContext(dockerConfigurationDirectory)
                    }
                }

                should("return the 'default' context to use the DOCKER_HOST environment variable") {
                    activeCLIContext shouldBe DockerCLIContext("default")
                }
            }

            context("when only the DOCKER_CONTEXT environment variable is set") {
                val activeCLIContext = withNoDockerEnvironmentVariables {
                    withEnvironmentVariable("DOCKER_CONTEXT", "my-environment-context") {
                        DockerCLIContext.getActiveCLIContext(dockerConfigurationDirectory)
                    }
                }

                should("return the context from the DOCKER_CONTEXT environment variable") {
                    activeCLIContext shouldBe DockerCLIContext("my-environment-context")
                }
            }

            context("when neither environment variable is set") {
                val activeCLIContext = withNoDockerEnvironmentVariables {
                    DockerCLIContext.getActiveCLIContext(dockerConfigurationDirectory)
                }

                should("return the selected context from the configuration file") {
                    activeCLIContext shouldBe DockerCLIContext("my-configured-context")
                }
            }
        }

        context("given there is not a context selected in the Docker CLI's configuration file") {
            val dockerConfigurationDirectory = testConfigurationDirectories / "without-active-context"

            context("when neither environment variable is set") {
                val activeCLIContext = withNoDockerEnvironmentVariables {
                    DockerCLIContext.getActiveCLIContext(dockerConfigurationDirectory)
                }

                should("return the 'default' context") {
                    activeCLIContext shouldBe DockerCLIContext("default")
                }
            }
        }
    }

    context("getting the selected CLI context") {
        context("given there is a context selected in the Docker CLI's configuration file") {
            val dockerConfigurationDirectory = testConfigurationDirectories / "with-active-context"

            context("when only the DOCKER_HOST environment variable is set") {
                val selectedCLIContext = withNoDockerEnvironmentVariables {
                    withEnvironmentVariable("DOCKER_HOST", "proto://host/docker.sock") {
                        DockerCLIContext.getSelectedCLIContext(dockerConfigurationDirectory)
                    }
                }

                should("return the selected context from the configuration file") {
                    selectedCLIContext shouldBe DockerCLIContext("my-configured-context")
                }
            }

            context("when only the DOCKER_CONTEXT environment variable is set") {
                val selectedCLIContext = withNoDockerEnvironmentVariables {
                    withEnvironmentVariable("DOCKER_CONTEXT", "my-environment-context") {
                        DockerCLIContext.getSelectedCLIContext(dockerConfigurationDirectory)
                    }
                }

                should("return the selected context from the configuration file") {
                    selectedCLIContext shouldBe DockerCLIContext("my-configured-context")
                }
            }

            context("when neither environment variable is set") {
                val selectedCLIContext = withNoDockerEnvironmentVariables {
                    DockerCLIContext.getSelectedCLIContext(dockerConfigurationDirectory)
                }

                should("return the selected context from the configuration file") {
                    selectedCLIContext shouldBe DockerCLIContext("my-configured-context")
                }
            }
        }

        context("given there is not a context selected in the Docker CLI's configuration file") {
            val dockerConfigurationDirectory = testConfigurationDirectories / "without-active-context"

            context("when neither environment variable is set") {
                val selectedCLIContext = withNoDockerEnvironmentVariables {
                    DockerCLIContext.getSelectedCLIContext(dockerConfigurationDirectory)
                }

                should("return the 'default' context") {
                    selectedCLIContext shouldBe DockerCLIContext("default")
                }
            }
        }
    }
})
