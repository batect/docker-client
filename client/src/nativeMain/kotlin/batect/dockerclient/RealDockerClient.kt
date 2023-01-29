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

import batect.dockerclient.io.PreparedOutputStream
import batect.dockerclient.io.TextInput
import batect.dockerclient.io.TextOutput
import batect.dockerclient.native.AttachToContainerOutput
import batect.dockerclient.native.BuildImage
import batect.dockerclient.native.BuildImageProgressUpdate
import batect.dockerclient.native.CreateClient
import batect.dockerclient.native.CreateContainer
import batect.dockerclient.native.CreateExec
import batect.dockerclient.native.CreateNetwork
import batect.dockerclient.native.CreateVolume
import batect.dockerclient.native.DeleteImage
import batect.dockerclient.native.DeleteNetwork
import batect.dockerclient.native.DeleteVolume
import batect.dockerclient.native.DisposeClient
import batect.dockerclient.native.DockerClientHandle
import batect.dockerclient.native.GetDaemonVersionInformation
import batect.dockerclient.native.GetImage
import batect.dockerclient.native.GetNetworkByNameOrID
import batect.dockerclient.native.InspectContainer
import batect.dockerclient.native.InspectExec
import batect.dockerclient.native.ListAllVolumes
import batect.dockerclient.native.Ping
import batect.dockerclient.native.PruneImageBuildCache
import batect.dockerclient.native.PullImage
import batect.dockerclient.native.PullImageProgressUpdate
import batect.dockerclient.native.RemoveContainer
import batect.dockerclient.native.StartAndAttachToExec
import batect.dockerclient.native.StartContainer
import batect.dockerclient.native.StartExecDetached
import batect.dockerclient.native.StopContainer
import batect.dockerclient.native.StreamEvents
import batect.dockerclient.native.UploadToContainer
import batect.dockerclient.native.WaitForContainerToExit
import kotlinx.cinterop.cstr
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.pointed
import kotlinx.cinterop.ptr
import kotlinx.cinterop.toKString
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.datetime.Instant
import kotlin.time.Duration

internal actual class RealDockerClient actual constructor(configuration: DockerClientConfiguration) : DockerClient, AutoCloseable {
    // This property is internally visible so that tests can get this value to establish scenarios
    // by communicating with the Golang wrapper.
    internal val clientHandle: DockerClientHandle = createClient(configuration)

    private fun createClient(configuration: DockerClientConfiguration): DockerClientHandle {
        memScoped {
            CreateClient(allocClientConfiguration(configuration).ptr)!!.use { ret ->
                if (ret.pointed.Error != null) {
                    throw DockerClientException(ret.pointed.Error!!.pointed)
                }

                return ret.pointed.Client
            }
        }
    }

    override suspend fun ping(): PingResponse {
        return launchWithGolangContext { context ->
            Ping(clientHandle, context.handle)!!.use { ret ->
                if (ret.pointed.Error != null) {
                    throw PingFailedException(ret.pointed.Error!!.pointed)
                }

                val response = ret.pointed.Response!!.pointed

                PingResponse(
                    response.APIVersion!!.toKString(),
                    response.OSType!!.toKString(),
                    response.Experimental,
                    BuilderVersion.fromAPI(response.BuilderVersion!!.toKString()),
                )
            }
        }
    }

    override suspend fun getDaemonVersionInformation(): DaemonVersionInformation {
        return launchWithGolangContext { context ->
            GetDaemonVersionInformation(clientHandle, context.handle)!!.use { ret ->
                if (ret.pointed.Error != null) {
                    throw GetDaemonVersionInformationFailedException(ret.pointed.Error!!.pointed)
                }

                val response = ret.pointed.Response!!.pointed

                DaemonVersionInformation(
                    response.Version!!.toKString(),
                    response.APIVersion!!.toKString(),
                    response.MinAPIVersion!!.toKString(),
                    response.GitCommit!!.toKString(),
                    response.OperatingSystem!!.toKString(),
                    response.Architecture!!.toKString(),
                    response.Experimental,
                )
            }
        }
    }

    override suspend fun listAllVolumes(): Set<VolumeReference> {
        return launchWithGolangContext { context ->
            ListAllVolumes(clientHandle, context.handle)!!.use { ret ->
                if (ret.pointed.Error != null) {
                    throw ListAllVolumesFailedException(ret.pointed.Error!!.pointed)
                }

                fromArray(ret.pointed.Volumes!!, ret.pointed.VolumesCount) { VolumeReference(it) }.toSet()
            }
        }
    }

    override suspend fun createVolume(name: String): VolumeReference {
        return launchWithGolangContext { context ->
            CreateVolume(clientHandle, context.handle, name.cstr)!!.use { ret ->
                if (ret.pointed.Error != null) {
                    throw VolumeCreationFailedException(ret.pointed.Error!!.pointed)
                }

                VolumeReference(ret.pointed.Response!!.pointed)
            }
        }
    }

    override suspend fun deleteVolume(volume: VolumeReference) {
        return launchWithGolangContext { context ->
            DeleteVolume(clientHandle, context.handle, volume.name.cstr).ifFailed { error ->
                throw VolumeDeletionFailedException(error.pointed)
            }
        }
    }

    override suspend fun createNetwork(name: String, driver: String): NetworkReference {
        return launchWithGolangContext { context ->
            CreateNetwork(clientHandle, context.handle, name.cstr, driver.cstr)!!.use { ret ->
                if (ret.pointed.Error != null) {
                    throw NetworkCreationFailedException(ret.pointed.Error!!.pointed)
                }

                NetworkReference(ret.pointed.Response!!.pointed)
            }
        }
    }

    override suspend fun deleteNetwork(network: NetworkReference) {
        return launchWithGolangContext { context ->
            DeleteNetwork(clientHandle, context.handle, network.id.cstr).ifFailed { error ->
                throw NetworkDeletionFailedException(error.pointed)
            }
        }
    }

    override suspend fun getNetworkByNameOrID(searchFor: String): NetworkReference? {
        return launchWithGolangContext { context ->
            GetNetworkByNameOrID(clientHandle, context.handle, searchFor.cstr)!!.use { ret ->
                if (ret.pointed.Error != null) {
                    throw NetworkRetrievalFailedException(ret.pointed.Error!!.pointed)
                }

                if (ret.pointed.Response == null) {
                    null
                } else {
                    NetworkReference(ret.pointed.Response!!.pointed)
                }
            }
        }
    }

    override suspend fun pullImage(name: String, onProgressUpdate: ImagePullProgressReceiver): ImageReference {
        return launchWithGolangContext { context ->
            val callbackState = CallbackState<PullImageProgressUpdate> { progress ->
                onProgressUpdate.invoke(ImagePullProgressUpdate(progress!!.pointed))
            }

            callbackState.use { callback, callbackUserData ->
                PullImage(clientHandle, context.handle, name.cstr, callback, callbackUserData)!!.use { ret ->
                    if (ret.pointed.Error != null) {
                        val errorType = ret.pointed.Error!!.pointed.Type!!.toKString()

                        if (errorType == "main.ProgressCallbackFailedError") {
                            throw ImagePullFailedException("Image pull progress receiver threw an exception: ${callbackState.exceptionThrown}", callbackState.exceptionThrown, errorType)
                        }

                        throw ImagePullFailedException(ret.pointed.Error!!.pointed)
                    }

                    ImageReference(ret.pointed.Response!!.pointed)
                }
            }
        }
    }

    override suspend fun deleteImage(image: ImageReference, force: Boolean) {
        launchWithGolangContext { context ->
            DeleteImage(clientHandle, context.handle, image.id.cstr, force).ifFailed { error ->
                throw ImageDeletionFailedException(error.pointed)
            }
        }
    }

    override suspend fun getImage(name: String): ImageReference? {
        return launchWithGolangContext { context ->
            GetImage(clientHandle, context.handle, name.cstr)!!.use { ret ->
                if (ret.pointed.Error != null) {
                    throw ImageRetrievalFailedException(ret.pointed.Error!!.pointed)
                }

                if (ret.pointed.Response == null) {
                    null
                } else {
                    ImageReference(ret.pointed.Response!!.pointed)
                }
            }
        }
    }

    override suspend fun buildImage(spec: ImageBuildSpec, output: TextOutput, onProgressUpdate: ImageBuildProgressReceiver): ImageReference {
        output.prepareStream().use { stream ->
            val callbackState = CallbackState<BuildImageProgressUpdate> { progress ->
                onProgressUpdate.invoke(ImageBuildProgressUpdate(progress!!.pointed))
            }

            return coroutineScope {
                launch(IODispatcher) { stream.run() }

                launchWithGolangContext { context ->
                    buildImage(spec, stream, callbackState, context, onProgressUpdate)
                }
            }
        }
    }

    private fun buildImage(spec: ImageBuildSpec, stream: PreparedOutputStream, callbackState: CallbackState<BuildImageProgressUpdate>, context: GolangContext, onProgressUpdate: ImageBuildProgressReceiver): ImageReference {
        return memScoped {
            callbackState.use { callback, callbackUserData ->
                BuildImage(clientHandle, context.handle, allocBuildImageRequest(spec).ptr, stream.outputStreamHandle, callback, callbackUserData)!!.use { ret ->
                    if (ret.pointed.Error != null) {
                        val errorType = ret.pointed.Error!!.pointed.Type!!.toKString()

                        if (errorType == "main.ProgressCallbackFailedError") {
                            throw ImageBuildFailedException(
                                "Image build progress receiver threw an exception: ${callbackState.exceptionThrown}",
                                callbackState.exceptionThrown,
                                errorType,
                            )
                        }

                        throw ImageBuildFailedException(ret.pointed.Error!!.pointed)
                    }

                    val imageReference = ImageReference(ret.pointed.Response!!.pointed)
                    onProgressUpdate(BuildComplete(imageReference))

                    imageReference
                }
            }
        }
    }

    override suspend fun pruneImageBuildCache() {
        launchWithGolangContext { context ->
            PruneImageBuildCache(clientHandle, context.handle).ifFailed { error ->
                throw ImageBuildCachePruneFailedException(error.pointed)
            }
        }
    }

    override suspend fun createContainer(spec: ContainerCreationSpec): ContainerReference {
        spec.ensureValid()

        return launchWithGolangContext { context ->
            memScoped {
                CreateContainer(clientHandle, context.handle, allocCreateContainerRequest(spec).ptr)!!.use { ret ->
                    if (ret.pointed.Error != null) {
                        throw ContainerCreationFailedException(ret.pointed.Error!!.pointed)
                    }

                    ContainerReference(ret.pointed.Response!!.pointed.ID!!.toKString())
                }
            }
        }
    }

    override suspend fun startContainer(container: ContainerReference) {
        launchWithGolangContext { context ->
            StartContainer(clientHandle, context.handle, container.id.cstr).ifFailed { error ->
                throw ContainerStartFailedException(error.pointed)
            }
        }
    }

    override suspend fun stopContainer(container: ContainerReference, timeout: Duration) {
        launchWithGolangContext { context ->
            StopContainer(clientHandle, context.handle, container.id.cstr, timeout.inWholeSeconds).ifFailed { error ->
                throw ContainerStopFailedException(error.pointed)
            }
        }
    }

    override suspend fun attachToContainerIO(
        container: ContainerReference,
        stdout: TextOutput?,
        stderr: TextOutput?,
        stdin: TextInput?,
        attachedNotification: ReadyNotification?,
    ) {
        if (stdout == null && stderr == null && stdin == null) {
            attachedNotification?.markAsReady()
            return
        }

        stdout?.prepareStream().use { stdoutStream ->
            stderr?.prepareStream().use { stderrStream ->
                stdin?.prepareStream().use { stdinStream ->
                    coroutineScope {
                        launch(IODispatcher) { stdoutStream?.run() }
                        launch(IODispatcher) { stderrStream?.run() }
                        launch(IODispatcher) { stdinStream?.run() }

                        launchWithGolangContext { context ->
                            val callbackState = ReadyNotificationCallbackState(attachedNotification)

                            callbackState.use { callback, callbackUserData ->
                                AttachToContainerOutput(
                                    clientHandle,
                                    context.handle,
                                    container.id.cstr,
                                    stdoutStream?.outputStreamHandle ?: 0.toULong(),
                                    stderrStream?.outputStreamHandle ?: 0.toULong(),
                                    stdinStream?.inputStreamHandle ?: 0.toULong(),
                                    callback,
                                    callbackUserData,
                                ).ifFailed { error ->
                                    if (error.pointed.Type!!.toKString() == "main.ReadyCallbackFailedError") {
                                        throw callbackState.exceptionThrown!!
                                    }

                                    throw AttachToContainerFailedException(error.pointed)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    override suspend fun removeContainer(container: ContainerReference, force: Boolean, removeVolumes: Boolean) {
        launchWithGolangContext { context ->
            RemoveContainer(clientHandle, context.handle, container.id.cstr, force, removeVolumes).ifFailed { error ->
                throw ContainerRemovalFailedException(error.pointed)
            }
        }
    }

    override suspend fun waitForContainerToExit(container: ContainerReference, waitingNotification: ReadyNotification?): Long {
        return launchWithGolangContext { context ->
            val callbackState = ReadyNotificationCallbackState(waitingNotification)

            callbackState.use { callback, callbackUserData ->
                WaitForContainerToExit(clientHandle, context.handle, container.id.cstr, callback, callbackUserData)!!.use { ret ->
                    if (ret.pointed.Error != null) {
                        if (ret.pointed.Error!!.pointed.Type!!.toKString() == "main.ReadyCallbackFailedError") {
                            throw callbackState.exceptionThrown!!
                        }

                        throw ContainerWaitFailedException(ret.pointed.Error!!.pointed)
                    }

                    ret.pointed.ExitCode
                }
            }
        }
    }

    override suspend fun inspectContainer(idOrName: String): ContainerInspectionResult {
        return launchWithGolangContext { context ->
            InspectContainer(clientHandle, context.handle, idOrName.cstr)!!.use { ret ->
                if (ret.pointed.Error != null) {
                    throw ContainerInspectionFailedException(ret.pointed.Error!!.pointed)
                }

                ContainerInspectionResult(ret.pointed.Response!!.pointed)
            }
        }
    }

    override suspend fun uploadToContainer(container: ContainerReference, items: Set<UploadItem>, destinationPath: String) {
        return launchWithGolangContext { context ->
            memScoped {
                UploadToContainer(clientHandle, context.handle, container.id.cstr, allocUploadToContainerRequest(items).ptr, destinationPath.cstr).ifFailed { error ->
                    throw ContainerUploadFailedException(error.pointed)
                }
            }
        }
    }

    override suspend fun createExec(spec: ContainerExecSpec): ContainerExecReference {
        return launchWithGolangContext { context ->
            memScoped {
                CreateExec(clientHandle, context.handle, allocCreateExecRequest(spec).ptr)!!.use { ret ->
                    if (ret.pointed.Error != null) {
                        throw ContainerExecCreationFailedException(ret.pointed.Error!!.pointed)
                    }

                    ContainerExecReference(ret.pointed.Response!!.pointed.ID!!.toKString())
                }
            }
        }
    }

    override suspend fun startExecDetached(exec: ContainerExecReference) {
        launchWithGolangContext { context ->
            StartExecDetached(clientHandle, context.handle, exec.id.cstr).ifFailed { err ->
                throw StartingContainerExecFailedException(err.pointed)
            }
        }
    }

    override suspend fun inspectExec(exec: ContainerExecReference): ContainerExecInspectionResult {
        return launchWithGolangContext { context ->
            memScoped {
                InspectExec(clientHandle, context.handle, exec.id.cstr.ptr)!!.use { ret ->
                    if (ret.pointed.Error != null) {
                        throw ContainerExecInspectionFailedException(ret.pointed.Error!!.pointed)
                    }

                    ContainerExecInspectionResult(ret.pointed.Response!!.pointed)
                }
            }
        }
    }

    override suspend fun startAndAttachToExec(
        exec: ContainerExecReference,
        attachTTY: Boolean,
        stdout: TextOutput?,
        stderr: TextOutput?,
        stdin: TextInput?,
    ) {
        stdout?.prepareStream().use { stdoutStream ->
            stderr?.prepareStream().use { stderrStream ->
                stdin?.prepareStream().use { stdinStream ->
                    coroutineScope {
                        launch(IODispatcher) { stdoutStream?.run() }
                        launch(IODispatcher) { stderrStream?.run() }
                        launch(IODispatcher) { stdinStream?.run() }

                        launchWithGolangContext { context ->
                            try {
                                StartAndAttachToExec(
                                    clientHandle,
                                    context.handle,
                                    exec.id.cstr,
                                    attachTTY,
                                    stdoutStream?.outputStreamHandle ?: 0.toULong(),
                                    stderrStream?.outputStreamHandle ?: 0.toULong(),
                                    stdinStream?.inputStreamHandle ?: 0.toULong(),
                                ).ifFailed { error ->
                                    throw StartingContainerExecFailedException(error.pointed)
                                }
                            } finally {
                                stdin?.abortRead()
                            }
                        }
                    }
                }
            }
        }
    }

    override suspend fun streamEvents(since: Instant?, until: Instant?, filters: Map<String, Set<String>>, onEventReceived: EventHandler) {
        launchWithGolangContext { context ->
            val streamingAbortedException = Exception("Event handler aborted streaming.")

            val callbackState = CallbackState<batect.dockerclient.native.Event> { event ->
                if (onEventReceived.invoke(Event(event!!.pointed)) == EventHandlerAction.Stop) {
                    throw streamingAbortedException
                }
            }

            callbackState.use { callback, callbackUserData ->
                memScoped {
                    StreamEvents(
                        clientHandle,
                        context.handle,
                        allocStreamEventsRequest(since, until, filters).ptr,
                        callback,
                        callbackUserData,
                    ).ifFailed { error ->
                        val errorType = error.pointed.Type!!.toKString()

                        if (errorType != "main.EventCallbackFailedError") {
                            throw StreamingEventsFailedException(error.pointed)
                        }

                        if (callbackState.exceptionThrown != streamingAbortedException) {
                            throw StreamingEventsFailedException(
                                "Event receiver threw an exception: ${callbackState.exceptionThrown}",
                                callbackState.exceptionThrown,
                                errorType,
                            )
                        } else {
                            // Event receiver aborted streaming - do not propagate the exception.
                        }
                    }
                }
            }
        }
    }

    override fun close() {
        DisposeClient(clientHandle).ifFailed { error ->
            throw DockerClientException(error.pointed)
        }
    }
}
