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
 * Docker daemon connection information.
 *
 * @see [DockerClientConfiguration.Builder]
 * @see [DockerClient.create]
 */
public data class DockerClientConfiguration(
    val host: String? = null,
    val tls: DockerClientTLSConfiguration? = null,
    val configurationDirectory: Path? = null
) {
    public companion object {
        /**
         * Retrieve the default Docker daemon connection settings.
         *
         * The settings returned from this method take into consideration any Docker-related
         * environment variables such as `DOCKER_HOST`.
         */
        public fun default(): DockerClientConfiguration = fromCLIContext("default")

        /**
         * Retrieve the Docker daemon connection settings used by the given Docker CLI context.
         *
         * @param name the Docker CLI context name
         * @param dockerConfigurationDirectory the path to the Docker CLI's configuration files, or pass `null` to use the default (usually `~/.docker`)
         */
        public fun fromCLIContext(name: String, dockerConfigurationDirectory: Path? = null): DockerClientConfiguration = loadConfigurationFromCLIContext(name, dockerConfigurationDirectory)
    }

    /**
     * Use to create an instance of [DockerClientConfiguration] to connect to a Docker daemon.
     *
     * @see [DockerClient.create]
     */
    public class Builder {
        private var configuration = DockerClientConfiguration()

        /**
         * Configures the Docker client to use the provided host name.
         *
         * @param host host name to use, in `proto://name` format.
         */
        public fun withHost(host: String): Builder {
            configuration = configuration.copy(host = host)

            return this
        }

        /**
         * Configures the Docker client to use TLS when connecting to the Docker daemon, and sets the
         * certificate and key files used when establishing the connection.
         *
         * @param caFilePath path to a file containing certificate authority certificates
         * @param certFilePath path to a file containing certificate to present to the Docker daemon
         * @param keyFilePath path to a file containing the private key for the certificate in [certFilePath]
         * @param daemonIdentityVerification whether or not to validate the server's identity against the provided certificates
         */
        public fun withTLSConfiguration(
            caFilePath: Path,
            certFilePath: Path,
            keyFilePath: Path,
            daemonIdentityVerification: TLSVerification = TLSVerification.Enabled
        ): Builder {
            configuration = configuration.copy(
                tls = DockerClientTLSConfiguration(
                    caFilePath,
                    certFilePath,
                    keyFilePath,
                    daemonIdentityVerification
                )
            )

            return this
        }

        /**
         * Configures the Docker client to use the provided directory for client-side configuration such as
         * registry authentication.
         *
         * @param configurationDirectory path to a directory containing a Docker client configuration file
         */
        public fun withConfigurationDirectory(configurationDirectory: Path): Builder {
            configuration = configuration.copy(configurationDirectory = configurationDirectory)

            return this
        }

        public fun build(): DockerClientConfiguration = configuration
    }
}

/**
 * Docker daemon connection encryption settings.
 *
 * @see [DockerClientConfiguration.Builder.withTLSConfiguration]
 */
public data class DockerClientTLSConfiguration(
    val caFilePath: Path,
    val certFilePath: Path,
    val keyFilePath: Path,
    val daemonIdentityVerification: TLSVerification = TLSVerification.Enabled
)

/**
 * TLS verification modes for connecting to a Docker daemon.
 *
 * @see [DockerClientConfiguration.Builder.withTLSConfiguration]
 */
public enum class TLSVerification(internal val insecureSkipVerify: Boolean) {
    Enabled(false),
    InsecureDisabled(true);

    internal companion object {
        internal fun fromInsecureSkipVerify(value: Boolean): TLSVerification = when (value) {
            true -> InsecureDisabled
            false -> Enabled
        }
    }
}

internal expect fun loadConfigurationFromCLIContext(name: String, dockerConfigurationDirectory: Path?): DockerClientConfiguration
