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

import batect.dockerclient.io.TextInput
import batect.dockerclient.io.TextOutput
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.datetime.Instant
import kotlin.time.Duration

/**
 * A client for the Docker API.
 *
 * A single [DockerClient] instance is safe to use from multiple threads simultaneously.
 *
 * Some methods take callback functions. These methods are not guaranteed to be called from any particular thread, but are
 * guaranteed to only be called from one thread at a time.
 *
 */
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
    public suspend fun uploadToContainer(container: ContainerReference, items: Set<UploadItem>, destinationPath: String)

    /**
     * Streams input and output to/from the provided container.
     *
     * If [stdin] is a [batect.dockerclient.io.SourceTextInput], the underlying source will **not** be closed when the container exits. You must call [TextInput.abortRead] to
     * cancel any pending read from [stdin] once the container exits: this method does not exit until all streams have been exhausted or aborted.
     *
     * If [stdout] and [stderr] refer to the same [batect.dockerclient.io.SinkTextOutput], the underlying [okio.Sink] must be thread-safe, as it may receive writes from
     * multiple threads.
     *
     * To capture all output from a container, call this method, wait for [attachedNotification] to be marked as ready, then call [startContainer].
     *
     * See also: [run], which handles these details for you.
     *
     * @param container the container to stream input and output to
     * @param stdout the output stream to stream stdout to
     * @param stderr the output stream to stream stderr to. Not used if the container is configured to use a TTY with [ContainerCreationSpec.Builder.withTTY].
     * @param stdin the input stream to stream stdin from. Only used if the container is configured to have stdin attached with [ContainerCreationSpec.Builder.withStdinAttached].
     * @param attachedNotification marked as ready when streaming has been established
     */
    public suspend fun attachToContainerIO(container: ContainerReference, stdout: TextOutput?, stderr: TextOutput?, stdin: TextInput?, attachedNotification: ReadyNotification? = null)

    public suspend fun inspectContainer(idOrName: String): ContainerInspectionResult
    public suspend fun inspectContainer(container: ContainerReference): ContainerInspectionResult = inspectContainer(container.id)

    /**
     * Wait for a container to exit, and then return its exit code.
     *
     * @return the container's exit code
     */
    public suspend fun waitForContainerToExit(container: ContainerReference, waitingNotification: ReadyNotification? = null): Long

    public suspend fun createExec(spec: ContainerExecSpec): ContainerExecReference
    public suspend fun startExecDetached(exec: ContainerExecReference)
    public suspend fun inspectExec(exec: ContainerExecReference): ContainerExecInspectionResult

    /**
     * Starts the provided exec instance and then streams input and output to and from it.
     *
     * If [stdin] is a [batect.dockerclient.io.SourceTextInput], the underlying source will be closed when the exec instance exits.
     *
     * If [stdout] and [stderr] refer to the same [batect.dockerclient.io.SinkTextOutput], the underlying [okio.Sink] must be thread-safe, as it may receive writes from
     * multiple threads.
     *
     * @param exec the exec instance to stream input and output to
     * @param attachTTY if `true`, attach a TTY to the exec instance. Has no effect if the exec instance does not have a TTY attached with [ContainerExecSpec.Builder.withTTYAttached].
     * @param stdout the output stream to stream stdout to
     * @param stderr the output stream to stream stderr to. Not used if [attachTTY] is `true`.
     * @param stdin the input stream to stream stdin from. Only used if the exec instance is configured to have stdin attached with [ContainerExecSpec.Builder.withStdinAttached].
     */
    public suspend fun startAndAttachToExec(exec: ContainerExecReference, attachTTY: Boolean, stdout: TextOutput?, stderr: TextOutput?, stdin: TextInput?)

    public suspend fun streamEvents(since: Instant?, until: Instant?, filters: Map<String, Set<String>>, onEventReceived: EventHandler)

    /**
     * Runs the provided container until it exits, streaming output to the provided output streams.
     *
     * The container is not stopped or removed when it exits.
     *
     * If [stdin] is a [batect.dockerclient.io.SourceTextInput], the underlying source will be closed when the container exits.
     *
     * If [stdout] and [stderr] refer to the same [batect.dockerclient.io.SinkTextOutput], the underlying [okio.Sink] must be thread-safe, as it may receive writes from
     * multiple threads.
     *
     * @param container the container to run
     * @param stdout the output stream to stream stdout to
     * @param stderr the output stream to stream stderr to. Not used if the container is configured to use a TTY with [ContainerCreationSpec.Builder.withTTY].
     * @param stdin the input stream to stream stdin from. Only used if the container is configured to have stdin attached with [ContainerCreationSpec.Builder.withStdinAttached].
     * @param startedNotification marked as ready once the container has started
     * @return the exit code from the container
     */
    public suspend fun run(container: ContainerReference, stdout: TextOutput?, stderr: TextOutput?, stdin: TextInput?, startedNotification: ReadyNotification? = null): Long {
        return coroutineScope {
            val listeningToOutput = ReadyNotification()
            val waitingForExitCode = ReadyNotification()

            try {
                launch {
                    attachToContainerIO(container, stdout, stderr, stdin, listeningToOutput)
                }

                val exitCodeSource = async {
                    waitForContainerToExit(container, waitingForExitCode)
                }

                launch {
                    listeningToOutput.waitForReady()
                    waitingForExitCode.waitForReady()

                    startContainer(container)

                    startedNotification?.markAsReady()
                }

                exitCodeSource.await()
            } finally {
                stdin?.abortRead()
            }
        }
    }

    public companion object {
        /**
         * Create a [DockerClient] using the default Docker daemon connection settings.
         *
         * @see [DockerClientConfiguration.default]
         */
        public fun create(): DockerClient = create(DockerClientConfiguration.default())

        /**
         * Create a [DockerClient] using the provided connection information.
         *
         * @param config Docker daemon connection configuration
         */
        public fun create(config: DockerClientConfiguration): DockerClient = RealDockerClient(config)
    }
}

internal expect class RealDockerClient(configuration: DockerClientConfiguration) : DockerClient, AutoCloseable
