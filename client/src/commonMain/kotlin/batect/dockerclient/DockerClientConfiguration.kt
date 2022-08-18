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

import okio.FileSystem
import okio.Path

/**
 * Docker daemon connection information.
 *
 * @see [DockerClientConfiguration.Builder]
 * @see [DockerClient.create]
 */
public data class DockerClientConfiguration(
    val host: String,
    val tls: DockerClientTLSConfiguration? = null,
    val daemonIdentityVerification: TLSVerification = TLSVerification.Enabled,
    val configurationDirectory: Path? = null
) {
    public companion object {
        /**
         * Retrieve the default Docker daemon connection settings.
         *
         * The settings returned from this method take into consideration any Docker-related
         * environment variables such as `DOCKER_HOST` and `DOCKER_CONTEXT`, as well as the active Docker CLI context, if any.
         */
        public fun default(): DockerClientConfiguration = fromCLIContext(DockerCLIContext.getActiveCLIContext())

        /**
         * Retrieve the Docker daemon connection settings used by the given Docker CLI context.
         *
         * Note that the `default` Docker CLI context is not static: it reflects the default settings for
         * the host operating system and the effects of environment variables such as `DOCKER_HOST` and `DOCKER_TLS`.
         *
         * @param context the Docker CLI context
         * @param dockerConfigurationDirectory the path to the Docker CLI's configuration files, or pass `null` to use the default (usually `~/.docker`)
         */
        public fun fromCLIContext(context: DockerCLIContext, dockerConfigurationDirectory: Path? = null): DockerClientConfiguration =
            loadConfigurationFromCLIContext(context.name, dockerConfigurationDirectory)
    }

    /**
     * Use to create an instance of [DockerClientConfiguration] to connect to a Docker daemon.
     *
     * @param host host name to use, in `proto://name` format.
     * @see [DockerClient.create]
     */
    public class Builder(host: String) {
        private var configuration = DockerClientConfiguration(host)

        /**
         * Configures the Docker client to use TLS when connecting to the Docker daemon, and sets the
         * certificate and key files used when establishing the connection.
         *
         * @param caFilePath path to a file containing certificate authority certificates
         * @param certFilePath path to a file containing certificate to present to the Docker daemon
         * @param keyFilePath path to a file containing the private key for the certificate in [certFilePath]
         */
        public fun withTLSConfiguration(
            caFilePath: Path,
            certFilePath: Path,
            keyFilePath: Path
        ): Builder {
            configuration = configuration.copy(
                tls = DockerClientTLSConfiguration(
                    systemFileSystem.readAllBytes(caFilePath),
                    systemFileSystem.readAllBytes(certFilePath),
                    systemFileSystem.readAllBytes(keyFilePath)
                )
            )

            return this
        }

        public fun withDaemonIdentityVerificationDisabled(): Builder {
            configuration = configuration.copy(daemonIdentityVerification = TLSVerification.InsecureDisabled)

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
    val caFile: ByteArray,
    val certFile: ByteArray,
    val keyFile: ByteArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false

        other as DockerClientTLSConfiguration

        if (!caFile.contentEquals(other.caFile)) return false
        if (!certFile.contentEquals(other.certFile)) return false
        if (!keyFile.contentEquals(other.keyFile)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = caFile.contentHashCode()
        result = 31 * result + certFile.contentHashCode()
        result = 31 * result + keyFile.contentHashCode()
        return result
    }
}

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

internal fun FileSystem.readAllBytes(path: Path): ByteArray {
    return this.read(path) { readByteArray() }
}
