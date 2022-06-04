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

import batect.dockerclient.io.TextOutput
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okio.Path
import kotlin.time.Duration

public interface DockerClient : AutoCloseable {
    public suspend fun ping(): PingResponse
    public suspend fun getDaemonVersionInformation(): DaemonVersionInformation

    public suspend fun listAllVolumes(): Set<VolumeReference>
    public suspend fun createVolume(name: String): VolumeReference
    public suspend fun deleteVolume(volume: VolumeReference)

    public suspend fun createNetwork(name: String, driver: String): NetworkReference
    public suspend fun deleteNetwork(network: NetworkReference)
    public suspend fun getNetworkByNameOrID(searchFor: String): NetworkReference?

    public suspend fun pullImage(name: String, onProgressUpdate: ImagePullProgressReceiver = {}): ImageReference
    public suspend fun deleteImage(image: ImageReference, force: Boolean = false)
    public suspend fun getImage(name: String): ImageReference?

    public suspend fun buildImage(spec: ImageBuildSpec, output: TextOutput, onProgressUpdate: ImageBuildProgressReceiver = {}): ImageReference
    public suspend fun pruneImageBuildCache()

    public suspend fun createContainer(spec: ContainerCreationSpec): ContainerReference
    public suspend fun startContainer(container: ContainerReference)
    public suspend fun stopContainer(container: ContainerReference, timeout: Duration)
    public suspend fun removeContainer(container: ContainerReference, force: Boolean = false, removeVolumes: Boolean = false)
    public suspend fun attachToContainerOutput(container: ContainerReference, stdout: TextOutput, stderr: TextOutput, attachedNotification: ReadyNotification? = null)
    public suspend fun inspectContainer(idOrName: String): ContainerInspectionResult
    public suspend fun inspectContainer(container: ContainerReference): ContainerInspectionResult = inspectContainer(container.id)

    /**
     * Wait for a container to exit, and then return its exit code.
     *
     * @return the container's exit code
     */
    public suspend fun waitForContainerToExit(container: ContainerReference, waitingNotification: ReadyNotification? = null): Long

    public class Builder internal constructor(internal val factory: DockerClientFactory) {
        public constructor() : this({ config -> RealDockerClient(config) })

        private var configuration = DockerClientConfiguration()

        /**
         * Use values in environment variables as fallback values to configure the Docker client.
         *
         * If any of `DOCKER_HOST`, `DOCKER_CERT_PATH`, `DOCKER_TLS_VERIFY` and `DOCKER_CONFIG` are set,
         * the values provided will be used if no other value is provided (eg. by calling [withHost],
         * [withTLSConfiguration] or [withConfigDirectory]).
         *
         * This is the default behaviour. To disable this behaviour, call [doNotUseDefaultConfigurationFromEnvironment].
         */
        public fun useDefaultConfigurationFromEnvironment(): Builder {
            configuration = configuration.copy(useConfigurationFromEnvironment = true)

            return this
        }

        /**
         * Do not use values in environment variables as fallback values to configure the Docker client.
         *
         * The Docker client will use the default configuration for this platform unless configured with
         * the other methods on this class such as [withHost], [withTLSConfiguration] or [withConfigDirectory].
         */
        public fun doNotUseDefaultConfigurationFromEnvironment(): Builder {
            configuration = configuration.copy(useConfigurationFromEnvironment = false)

            return this
        }

        /**
         * Configures the Docker client to use the provided host name.
         *
         * This value takes precedence over the value configured in the `DOCKER_HOST` environment variable,
         * even if [useDefaultConfigurationFromEnvironment] is used.
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
         * These values take precedence over the values configured in the `DOCKER_CERT_PATH` and `DOCKER_TLS_VERIFY`
         * environment variables, even if [useDefaultConfigurationFromEnvironment] is used.
         *
         * @param caFilePath path to a file containing certificate authority certificates
         * @param certFilePath path to a file containing certificate to present to the Docker daemon
         * @param keyFilePath path to a file containing the private key for the certificate in [certFilePath]
         * @param serverIdentityVerification whether or not to validate the server's identity against the provided certificates
         */
        public fun withTLSConfiguration(
            caFilePath: Path,
            certFilePath: Path,
            keyFilePath: Path,
            serverIdentityVerification: TLSVerification = TLSVerification.Enabled
        ): Builder {
            val insecureSkipVerify = when (serverIdentityVerification) {
                TLSVerification.Enabled -> false
                TLSVerification.InsecureDisabled -> true
            }

            configuration = configuration.copy(
                tls = DockerClientTLSConfiguration(
                    caFilePath.toString(),
                    certFilePath.toString(),
                    keyFilePath.toString(),
                    insecureSkipVerify
                )
            )

            return this
        }

        /**
         * Configures the Docker client to use the provided directory for client-side configuration such as
         * registry authentication.
         *
         * This value takes precedence over the value configured in the `DOCKER_CONFIG` environment variable,
         * even if [useDefaultConfigurationFromEnvironment] is used.
         *
         * @param configDirectoryPath path to a directory containing a Docker client configuration file
         */
        public fun withConfigDirectory(configDirectoryPath: Path): Builder {
            configuration = configuration.copy(configDirectoryPath = configDirectoryPath.toString())

            return this
        }

        public fun build(): DockerClient = factory(configuration)
    }
}

internal expect class RealDockerClient(configuration: DockerClientConfiguration) : DockerClient, AutoCloseable

internal typealias DockerClientFactory = (DockerClientConfiguration) -> DockerClient

/**
 * Runs the provided container until it exits, streaming output to the provided output streams.
 *
 * The container is not stopped or removed when it exits.
 *
 * @param container the container to run
 * @param stdout the output stream to stream stdout to
 * @param stderr the output stream to stream stderr to, not used if the container is configured to use a TTY
 * @return the exit code from the container
 */
public suspend fun DockerClient.run(container: ContainerReference, stdout: TextOutput, stderr: TextOutput): Long {
    return coroutineScope {
        val listeningToOutput = ReadyNotification()
        val waitingForExitCode = ReadyNotification()

        launch {
            attachToContainerOutput(container, stdout, stderr, listeningToOutput)
        }

        val exitCodeSource = async {
            waitForContainerToExit(container, waitingForExitCode)
        }

        launch {
            listeningToOutput.waitForReady()
            waitingForExitCode.waitForReady()

            startContainer(container)
        }

        exitCodeSource.await()
    }
}
