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
import batect.dockerclient.native.BuildImageProgressCallback
import batect.dockerclient.native.BuildImageProgressUpdate
import batect.dockerclient.native.CreateExecRequest
import batect.dockerclient.native.DockerClientHandle
import batect.dockerclient.native.Error
import batect.dockerclient.native.EventCallback
import batect.dockerclient.native.PullImageProgressCallback
import batect.dockerclient.native.PullImageProgressUpdate
import batect.dockerclient.native.nativeAPI
import batect.dockerclient.native.volumes
import jnr.ffi.Pointer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.datetime.Instant
import kotlin.time.Duration

internal actual class RealDockerClient actual constructor(configuration: DockerClientConfiguration) : DockerClient, AutoCloseable {
    // This property is internally visible so that tests can get this value to establish scenarios
    // by communicating with the Golang wrapper.
    internal val clientHandle: DockerClientHandle = createClient(configuration)

    private fun createClient(configuration: DockerClientConfiguration): DockerClientHandle {
        nativeAPI.CreateClient(ClientConfiguration(configuration))!!.use { ret ->
            if (ret.error != null) {
                throw DockerClientException(ret.error!!)
            }

            return ret.client.get()
        }
    }

    override suspend fun ping(): PingResponse {
        return launchWithGolangContext { context ->
            nativeAPI.Ping(clientHandle, context.handle)!!.use { ret ->
                if (ret.error != null) {
                    throw PingFailedException(ret.error!!)
                }

                val response = ret.response!!

                PingResponse(
                    response.apiVersion.get(),
                    response.osType.get(),
                    response.experimental.get(),
                    BuilderVersion.fromAPI(response.builderVersion.get())
                )
            }
        }
    }

    override suspend fun getDaemonVersionInformation(): DaemonVersionInformation {
        return launchWithGolangContext { context ->
            nativeAPI.GetDaemonVersionInformation(clientHandle, context.handle)!!.use { ret ->
                if (ret.error != null) {
                    throw GetDaemonVersionInformationFailedException(ret.error!!)
                }

                val response = ret.response!!

                DaemonVersionInformation(
                    response.version.get(),
                    response.apiVersion.get(),
                    response.minAPIVersion.get(),
                    response.gitCommit.get(),
                    response.operatingSystem.get(),
                    response.architecture.get(),
                    response.experimental.get()
                )
            }
        }
    }

    override suspend fun listAllVolumes(): Set<VolumeReference> {
        return launchWithGolangContext { context ->
            nativeAPI.ListAllVolumes(clientHandle, context.handle)!!.use { ret ->
                if (ret.error != null) {
                    throw ListAllVolumesFailedException(ret.error!!)
                }

                ret.volumes.map { VolumeReference(it) }.toSet()
            }
        }
    }

    override suspend fun createVolume(name: String): VolumeReference {
        return launchWithGolangContext { context ->
            nativeAPI.CreateVolume(clientHandle, context.handle, name)!!.use { ret ->
                if (ret.error != null) {
                    throw VolumeCreationFailedException(ret.error!!)
                }

                VolumeReference(ret.response!!)
            }
        }
    }

    override suspend fun deleteVolume(volume: VolumeReference) {
        return launchWithGolangContext { context ->
            nativeAPI.DeleteVolume(clientHandle, context.handle, volume.name).ifFailed { error ->
                throw VolumeDeletionFailedException(error)
            }
        }
    }

    override suspend fun createNetwork(name: String, driver: String): NetworkReference {
        return launchWithGolangContext { context ->
            nativeAPI.CreateNetwork(clientHandle, context.handle, name, driver)!!.use { ret ->
                if (ret.error != null) {
                    throw NetworkCreationFailedException(ret.error!!)
                }

                NetworkReference(ret.response!!)
            }
        }
    }

    override suspend fun deleteNetwork(network: NetworkReference) {
        return launchWithGolangContext { context ->
            nativeAPI.DeleteNetwork(clientHandle, context.handle, network.id).ifFailed { error ->
                throw NetworkDeletionFailedException(error)
            }
        }
    }

    override suspend fun getNetworkByNameOrID(searchFor: String): NetworkReference? {
        return launchWithGolangContext { context ->
            nativeAPI.GetNetworkByNameOrID(clientHandle, context.handle, searchFor)!!.use { ret ->
                if (ret.error != null) {
                    throw NetworkRetrievalFailedException(ret.error!!)
                }

                if (ret.response == null) {
                    null
                } else {
                    NetworkReference(ret.response!!)
                }
            }
        }
    }

    override suspend fun pullImage(name: String, onProgressUpdate: ImagePullProgressReceiver): ImageReference {
        var exceptionThrownInCallback: Throwable? = null

        val callback = object : PullImageProgressCallback {
            override fun invoke(userData: Pointer?, progressPointer: Pointer?): Boolean {
                try {
                    val progress = PullImageProgressUpdate(progressPointer!!)
                    onProgressUpdate(ImagePullProgressUpdate(progress))

                    return true
                } catch (t: Throwable) {
                    exceptionThrownInCallback = t

                    return false
                }
            }
        }

        return launchWithGolangContext { context ->
            nativeAPI.PullImage(clientHandle, context.handle, name, callback, null)!!.use { ret ->
                if (ret.error != null) {
                    if (ret.error!!.type.get() == "main.ProgressCallbackFailedError") {
                        throw ImagePullFailedException("Image pull progress receiver threw an exception: $exceptionThrownInCallback", exceptionThrownInCallback, ret.error!!.type.get())
                    }

                    throw ImagePullFailedException(ret.error!!)
                }

                ImageReference(ret.response!!)
            }
        }
    }

    override suspend fun deleteImage(image: ImageReference, force: Boolean) {
        launchWithGolangContext { context ->
            nativeAPI.DeleteImage(clientHandle, context.handle, image.id, force).ifFailed { error ->
                throw ImageDeletionFailedException(error)
            }
        }
    }

    override suspend fun getImage(name: String): ImageReference? {
        return launchWithGolangContext { context ->
            nativeAPI.GetImage(clientHandle, context.handle, name)!!.use { ret ->
                if (ret.error != null) {
                    throw ImageRetrievalFailedException(ret.error!!)
                }

                if (ret.response == null) {
                    null
                } else {
                    ImageReference(ret.response!!)
                }
            }
        }
    }

    override suspend fun buildImage(spec: ImageBuildSpec, output: TextOutput, onProgressUpdate: ImageBuildProgressReceiver): ImageReference {
        var exceptionThrownInCallback: Throwable? = null

        val callback = object : BuildImageProgressCallback {
            override fun invoke(userData: Pointer?, progressPointer: Pointer?): Boolean {
                try {
                    val progress = BuildImageProgressUpdate(progressPointer!!)
                    onProgressUpdate(ImageBuildProgressUpdate(progress))

                    return true
                } catch (t: Throwable) {
                    exceptionThrownInCallback = t

                    return false
                }
            }
        }

        output.prepareStream().use { stream ->
            return withContext(Dispatchers.IO) {
                launch { stream.run() }

                launchWithGolangContext { context ->
                    nativeAPI.BuildImage(clientHandle, context.handle, BuildImageRequest(spec), stream.outputStreamHandle.toLong(), callback, null)!!.use { ret ->
                        if (ret.error != null) {
                            if (ret.error!!.type.get() == "main.ProgressCallbackFailedError") {
                                throw ImageBuildFailedException(
                                    "Image build progress receiver threw an exception: $exceptionThrownInCallback",
                                    exceptionThrownInCallback,
                                    ret.error!!.type.get()
                                )
                            }

                            throw ImageBuildFailedException(ret.error!!)
                        }

                        val imageReference = ImageReference(ret.response!!)
                        onProgressUpdate(BuildComplete(imageReference))

                        imageReference
                    }
                }
            }
        }
    }

    override suspend fun pruneImageBuildCache() {
        launchWithGolangContext { context ->
            nativeAPI.PruneImageBuildCache(clientHandle, context.handle).ifFailed { error ->
                throw ImageBuildCachePruneFailedException(error)
            }
        }
    }

    override suspend fun createContainer(spec: ContainerCreationSpec): ContainerReference {
        spec.ensureValid()

        return launchWithGolangContext { context ->
            nativeAPI.CreateContainer(clientHandle, context.handle, CreateContainerRequest(spec))!!.use { ret ->
                if (ret.error != null) {
                    throw ContainerCreationFailedException(ret.error!!)
                }

                ContainerReference(ret.response!!.id.get())
            }
        }
    }

    override suspend fun startContainer(container: ContainerReference) {
        launchWithGolangContext { context ->
            nativeAPI.StartContainer(clientHandle, context.handle, container.id).ifFailed { error ->
                throw ContainerStartFailedException(error)
            }
        }
    }

    override suspend fun stopContainer(container: ContainerReference, timeout: Duration) {
        launchWithGolangContext { context ->
            nativeAPI.StopContainer(clientHandle, context.handle, container.id, timeout.inWholeSeconds).ifFailed { error ->
                throw ContainerStopFailedException(error)
            }
        }
    }

    override suspend fun removeContainer(container: ContainerReference, force: Boolean, removeVolumes: Boolean) {
        launchWithGolangContext { context ->
            nativeAPI.RemoveContainer(clientHandle, context.handle, container.id, force, removeVolumes).ifFailed { error ->
                throw ContainerRemovalFailedException(error)
            }
        }
    }

    override suspend fun attachToContainerIO(
        container: ContainerReference,
        stdout: TextOutput?,
        stderr: TextOutput?,
        stdin: TextInput?,
        attachedNotification: ReadyNotification?
    ) {
        val callback = ReadyCallback(attachedNotification)

        stdout?.prepareStream().use { stdoutStream ->
            stderr?.prepareStream().use { stderrStream ->
                stdin?.prepareStream().use { stdinStream ->
                    coroutineScope {
                        launch(IODispatcher) { stdoutStream?.run() }
                        launch(IODispatcher) { stderrStream?.run() }
                        launch(IODispatcher) { stdinStream?.run() }

                        launchWithGolangContext { context ->
                            nativeAPI.AttachToContainerOutput(
                                clientHandle,
                                context.handle,
                                container.id,
                                stdoutStream?.outputStreamHandle?.toLong() ?: 0,
                                stderrStream?.outputStreamHandle?.toLong() ?: 0,
                                stdinStream?.inputStreamHandle?.toLong() ?: 0,
                                callback,
                                null
                            ).ifFailed { error ->
                                if (error.type.get() == "main.ReadyCallbackFailedError") {
                                    throw callback.exceptionThrownInCallback!!
                                }

                                throw AttachToContainerFailedException(error)
                            }
                        }
                    }
                }
            }
        }
    }

    override suspend fun waitForContainerToExit(container: ContainerReference, waitingNotification: ReadyNotification?): Long {
        return launchWithGolangContext { context ->
            val callback = ReadyCallback(waitingNotification)

            nativeAPI.WaitForContainerToExit(clientHandle, context.handle, container.id, callback, null)!!.use { ret ->
                if (ret.error != null) {
                    if (ret.error!!.type.get() == "main.ReadyCallbackFailedError") {
                        throw callback.exceptionThrownInCallback!!
                    }

                    throw ContainerWaitFailedException(ret.error!!)
                }

                ret.exitCode.longValue()
            }
        }
    }

    override suspend fun inspectContainer(idOrName: String): ContainerInspectionResult {
        return launchWithGolangContext { context ->
            nativeAPI.InspectContainer(clientHandle, context.handle, idOrName)!!.use { ret ->
                if (ret.error != null) {
                    throw ContainerInspectionFailedException(ret.error!!)
                }

                ContainerInspectionResult(ret.response!!)
            }
        }
    }

    override suspend fun uploadToContainer(container: ContainerReference, items: Set<UploadItem>, destinationPath: String) {
        return launchWithGolangContext { context ->
            nativeAPI.UploadToContainer(clientHandle, context.handle, container.id, UploadToContainerRequest(items), destinationPath).ifFailed { error ->
                throw ContainerUploadFailedException(error)
            }
        }
    }

    override suspend fun streamEvents(since: Instant?, until: Instant?, filters: Map<String, Set<String>>, onEventReceived: EventHandler) {
        var exceptionThrownInCallback: Throwable? = null
        val streamingAbortedException = Exception("Event handler aborted streaming.")

        val callback = object : EventCallback {
            override fun invoke(userData: Pointer?, eventPointer: Pointer?): Boolean {
                try {
                    val event = batect.dockerclient.native.Event(eventPointer!!)

                    if (onEventReceived(Event(event)) == EventHandlerAction.Stop) {
                        throw streamingAbortedException
                    }

                    return true
                } catch (t: Throwable) {
                    exceptionThrownInCallback = t

                    return false
                }
            }
        }

        launchWithGolangContext { context ->
            nativeAPI.StreamEvents(clientHandle, context.handle, StreamEventsRequest(since, until, filters), callback, null).ifFailed { error ->
                if (error.type.get() != "main.EventCallbackFailedError") {
                    throw StreamingEventsFailedException(error)
                }

                if (exceptionThrownInCallback != streamingAbortedException) {
                    throw StreamingEventsFailedException("Event receiver threw an exception: $exceptionThrownInCallback", exceptionThrownInCallback, error.type.get())
                } else {
                    // Event receiver aborted streaming - do not propagate the exception.
                }
            }
        }
    }

    override suspend fun createExec(spec: ContainerExecSpec): ContainerExecReference {
        return launchWithGolangContext { context ->
            nativeAPI.CreateExec(clientHandle, context.handle, CreateExecRequest(spec))!!.use { ret ->
                if (ret.error != null) {
                    throw ContainerExecCreationFailedException(ret.error!!)
                }

                ContainerExecReference(ret.response!!.id.get())
            }
        }
    }

    override suspend fun startExecDetached(exec: ContainerExecReference, attachTTY: Boolean) {
        launchWithGolangContext { context ->
            nativeAPI.StartExecDetached(clientHandle, context.handle, exec.id, attachTTY).ifFailed { error ->
                throw StartingContainerExecFailedException(error)
            }
        }
    }

    override suspend fun inspectExec(exec: ContainerExecReference): ContainerExecInspectionResult {
        return launchWithGolangContext { context ->
            nativeAPI.InspectExec(clientHandle, context.handle, exec.id)!!.use { ret ->
                if (ret.error != null) {
                    throw ContainerExecInspectionFailedException(ret.error!!)
                }

                ContainerExecInspectionResult(ret.response!!)
            }
        }
    }

    override suspend fun startAndAttachToExec(exec: ContainerExecReference, attachTTY: Boolean, stdout: TextOutput?, stderr: TextOutput?, stdin: TextInput?) {
        stdout?.prepareStream().use { stdoutStream ->
            stderr?.prepareStream().use { stderrStream ->
                stdin?.prepareStream().use { stdinStream ->
                    coroutineScope {
                        launch(IODispatcher) { stdoutStream?.run() }
                        launch(IODispatcher) { stderrStream?.run() }
                        launch(IODispatcher) { stdinStream?.run() }

                        launchWithGolangContext { context ->
                            nativeAPI.StartAndAttachToExec(
                                clientHandle,
                                context.handle,
                                exec.id,
                                attachTTY,
                                stdoutStream?.outputStreamHandle?.toLong() ?: 0,
                                stderrStream?.outputStreamHandle?.toLong() ?: 0,
                                stdinStream?.inputStreamHandle?.toLong() ?: 0
                            ).ifFailed { error ->
                                throw StartingContainerExecFailedException(error)
                            }
                        }
                    }
                }
            }
        }
    }

    override fun close() {
        nativeAPI.DisposeClient(clientHandle).ifFailed { error ->
            throw DockerClientException(error)
        }
    }

    private class ReadyCallback(private val notification: ReadyNotification?) : batect.dockerclient.native.ReadyCallback {
        var exceptionThrownInCallback: Throwable? = null

        override fun invoke(userData: Pointer?): Boolean {
            try {
                notification?.markAsReady()
                return true
            } catch (t: Throwable) {
                exceptionThrownInCallback = t
                return false
            }
        }
    }
}

private fun Error?.ifFailed(handler: (Error) -> Unit) {
    if (this != null) {
        handler(this)
    }
}
