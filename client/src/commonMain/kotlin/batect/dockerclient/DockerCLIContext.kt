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

import okio.Path

/**
 * Docker CLI configuration container.
 */
public data class DockerCLIContext(val name: String) {
    public companion object {
        /**
         * The `default` Docker CLI context.
         *
         * Note that the `default` Docker CLI context is not static: it reflects the default settings for
         * the host operating system and the effects of environment variables such as `DOCKER_HOST` and `DOCKER_TLS`.
         */
        public val default: DockerCLIContext = DockerCLIContext("default")

        /**
         * Retrieve the currently selected Docker CLI context.
         *
         * This mirrors the context selection behaviour of the Docker CLI, and takes into consideration
         * Docker-related environment variables such as `DOCKER_HOST` and `DOCKER_CONTEXT`,
         * as well as the Docker CLI context selected with `docker context use`.
         */
        public fun getActiveCLIContext(dockerConfigurationDirectory: Path? = null): DockerCLIContext = DockerCLIContext(determineActiveCLIContext(dockerConfigurationDirectory))

        /**
         * Retrieve the currently selected Docker CLI context, without considering any environment variables such as `DOCKER_CONTEXT`.
         *
         * This will return the Docker CLI context most recently selected with `docker context use`.
         */
        public fun getSelectedCLIContext(dockerConfigurationDirectory: Path? = null): DockerCLIContext = DockerCLIContext(determineSelectedCLIContext(dockerConfigurationDirectory))
    }
}

internal expect fun determineActiveCLIContext(dockerConfigurationDirectory: Path?): String
internal expect fun determineSelectedCLIContext(dockerConfigurationDirectory: Path?): String
