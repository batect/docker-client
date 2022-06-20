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
import batect.dockerclient.native.BuildImageProgressUpdate_BuildFailed
import batect.dockerclient.native.BuildImageProgressUpdate_ImageBuildContextUploadProgress
import batect.dockerclient.native.BuildImageProgressUpdate_StepDownloadProgressUpdate
import batect.dockerclient.native.BuildImageProgressUpdate_StepFinished
import batect.dockerclient.native.BuildImageProgressUpdate_StepOutput
import batect.dockerclient.native.BuildImageProgressUpdate_StepPullProgressUpdate
import batect.dockerclient.native.BuildImageProgressUpdate_StepStarting
import batect.dockerclient.native.BuildImageRequest
import batect.dockerclient.native.ClientConfiguration
import batect.dockerclient.native.CreateContainerRequest
import batect.dockerclient.native.DockerClientHandle
import batect.dockerclient.native.Error
import batect.dockerclient.native.PullImageProgressCallback
import batect.dockerclient.native.PullImageProgressUpdate
import batect.dockerclient.native.StringPair
import batect.dockerclient.native.TLSConfiguration
import batect.dockerclient.native.UploadToContainerRequest
import batect.dockerclient.native.bindMounts
import batect.dockerclient.native.buildArgs
import batect.dockerclient.native.capabilitiesToAdd
import batect.dockerclient.native.capabilitiesToDrop
import batect.dockerclient.native.command
import batect.dockerclient.native.config
import batect.dockerclient.native.deviceMounts
import batect.dockerclient.native.directories
import batect.dockerclient.native.entrypoint
import batect.dockerclient.native.environmentVariables
import batect.dockerclient.native.exposedPorts
import batect.dockerclient.native.extraHosts
import batect.dockerclient.native.files
import batect.dockerclient.native.healthcheckCommand
import batect.dockerclient.native.imageTags
import batect.dockerclient.native.labels
import batect.dockerclient.native.log
import batect.dockerclient.native.loggingOptions
import batect.dockerclient.native.nativeAPI
import batect.dockerclient.native.networkAliases
import batect.dockerclient.native.test
import batect.dockerclient.native.tmpfsMounts
import batect.dockerclient.native.volumes
import jnr.ffi.Pointer
import jnr.ffi.Runtime
import jnr.ffi.Struct
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.datetime.Instant
import kotlin.time.Duration
import kotlin.time.Duration.Companion.nanoseconds

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

    public override suspend fun ping(): PingResponse {
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

    public override suspend fun getDaemonVersionInformation(): DaemonVersionInformation {
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

    public override suspend fun listAllVolumes(): Set<VolumeReference> {
        return launchWithGolangContext { context ->
            nativeAPI.ListAllVolumes(clientHandle, context.handle)!!.use { ret ->
                if (ret.error != null) {
                    throw ListAllVolumesFailedException(ret.error!!)
                }

                ret.volumes.map { VolumeReference(it) }.toSet()
            }
        }
    }

    public override suspend fun createVolume(name: String): VolumeReference {
        return launchWithGolangContext { context ->
            nativeAPI.CreateVolume(clientHandle, context.handle, name)!!.use { ret ->
                if (ret.error != null) {
                    throw VolumeCreationFailedException(ret.error!!)
                }

                VolumeReference(ret.response!!)
            }
        }
    }

    public override suspend fun deleteVolume(volume: VolumeReference) {
        return launchWithGolangContext { context ->
            nativeAPI.DeleteVolume(clientHandle, context.handle, volume.name).ifFailed { error ->
                throw VolumeDeletionFailedException(error)
            }
        }
    }

    public override suspend fun createNetwork(name: String, driver: String): NetworkReference {
        return launchWithGolangContext { context ->
            nativeAPI.CreateNetwork(clientHandle, context.handle, name, driver)!!.use { ret ->
                if (ret.error != null) {
                    throw NetworkCreationFailedException(ret.error!!)
                }

                NetworkReference(ret.response!!)
            }
        }
    }

    public override suspend fun deleteNetwork(network: NetworkReference) {
        return launchWithGolangContext { context ->
            nativeAPI.DeleteNetwork(clientHandle, context.handle, network.id).ifFailed { error ->
                throw NetworkDeletionFailedException(error)
            }
        }
    }

    public override suspend fun getNetworkByNameOrID(searchFor: String): NetworkReference? {
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

    public override suspend fun pullImage(name: String, onProgressUpdate: ImagePullProgressReceiver): ImageReference {
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
                        throw ImagePullFailedException("Image pull progress receiver threw an exception: $exceptionThrownInCallback", exceptionThrownInCallback)
                    }

                    throw ImagePullFailedException(ret.error!!)
                }

                ImageReference(ret.response!!)
            }
        }
    }

    public override suspend fun deleteImage(image: ImageReference, force: Boolean) {
        launchWithGolangContext { context ->
            nativeAPI.DeleteImage(clientHandle, context.handle, image.id, force).ifFailed { error ->
                throw ImageDeletionFailedException(error)
            }
        }
    }

    public override suspend fun getImage(name: String): ImageReference? {
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
                                    exceptionThrownInCallback
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

    override fun close() {
        nativeAPI.DisposeClient(clientHandle).ifFailed { error ->
            throw DockerClientException(error)
        }
    }

    private fun VolumeReference(native: batect.dockerclient.native.VolumeReference): VolumeReference = VolumeReference(native.name.get())
    private fun NetworkReference(native: batect.dockerclient.native.NetworkReference): NetworkReference = NetworkReference(native.id.get())
    private fun ImageReference(native: batect.dockerclient.native.ImageReference): ImageReference = ImageReference(native.id.get())

    private fun ImagePullProgressUpdate(native: batect.dockerclient.native.PullImageProgressUpdate): ImagePullProgressUpdate =
        ImagePullProgressUpdate(native.message.get(), ImagePullProgressDetail(native.detail), native.id.get())

    private fun ImagePullProgressDetail(native: batect.dockerclient.native.PullImageProgressDetail?): ImagePullProgressDetail? =
        when (native) {
            null -> null
            else -> ImagePullProgressDetail(native.current.get(), native.total.get())
        }

    private fun ImageBuildProgressUpdate(native: BuildImageProgressUpdate): ImageBuildProgressUpdate = when {
        native.imageBuildContextUploadProgress != null -> contextUploadProgress(native.imageBuildContextUploadProgress!!)
        native.stepStarting != null -> StepStarting(native.stepStarting!!)
        native.stepOutput != null -> StepOutput(native.stepOutput!!)
        native.stepPullProgressUpdate != null -> StepPullProgressUpdate(native.stepPullProgressUpdate!!)
        native.stepDownloadProgressUpdate != null -> StepDownloadProgressUpdate(native.stepDownloadProgressUpdate!!)
        native.stepFinished != null -> StepFinished(native.stepFinished!!)
        native.buildFailed != null -> BuildFailed(native.buildFailed!!)
        else -> throw RuntimeException("${BuildImageProgressUpdate::class.qualifiedName} did not contain an update")
    }

    private fun contextUploadProgress(native: BuildImageProgressUpdate_ImageBuildContextUploadProgress): ImageBuildProgressUpdate =
        when (native.stepNumber.get()) {
            0L -> ImageBuildContextUploadProgress(native.bytesUploaded.get())
            else -> StepContextUploadProgress(native.stepNumber.get(), native.bytesUploaded.get())
        }

    private fun StepStarting(native: BuildImageProgressUpdate_StepStarting): StepStarting =
        StepStarting(native.stepNumber.get(), native.stepName.get())

    private fun StepOutput(native: BuildImageProgressUpdate_StepOutput): StepOutput =
        StepOutput(native.stepNumber.get(), native.output.get())

    private fun StepPullProgressUpdate(native: BuildImageProgressUpdate_StepPullProgressUpdate): StepPullProgressUpdate =
        StepPullProgressUpdate(native.stepNumber.get(), ImagePullProgressUpdate(native.pullProgress!!))

    private fun StepDownloadProgressUpdate(native: BuildImageProgressUpdate_StepDownloadProgressUpdate): StepDownloadProgressUpdate =
        StepDownloadProgressUpdate(native.stepNumber.get(), native.downloadedBytes.get(), native.totalBytes.get())

    private fun StepFinished(native: BuildImageProgressUpdate_StepFinished): StepFinished =
        StepFinished(native.stepNumber.get())

    private fun BuildFailed(native: BuildImageProgressUpdate_BuildFailed): BuildFailed =
        BuildFailed(native.message.get())

    private fun ClientConfiguration(jvm: DockerClientConfiguration): ClientConfiguration {
        val config = ClientConfiguration(Runtime.getRuntime(nativeAPI))
        config.useConfigurationFromEnvironment.set(jvm.useConfigurationFromEnvironment)
        config.host.set(jvm.host)
        config.configDirectoryPath.set(jvm.configDirectoryPath)

        if (jvm.tls != null) {
            config.tlsPointer.set(Struct.getMemory(TLSConfiguration(jvm.tls)))
        } else {
            config.tlsPointer.set(0)
        }

        return config
    }

    private fun TLSConfiguration(jvm: DockerClientTLSConfiguration): TLSConfiguration {
        val tls = TLSConfiguration(Runtime.getRuntime(nativeAPI))
        tls.caFilePath.set(jvm.caFilePath)
        tls.certFilePath.set(jvm.certFilePath)
        tls.keyFilePath.set(jvm.keyFilePath)
        tls.insecureSkipVerify.set(jvm.insecureSkipVerify)

        return tls
    }

    private fun BuildImageRequest(jvm: ImageBuildSpec): BuildImageRequest {
        val request = BuildImageRequest(Runtime.getRuntime(nativeAPI))
        request.contextDirectory.set(jvm.contextDirectory.toString())
        request.pathToDockerfile.set(jvm.pathToDockerfile.toString())
        request.buildArgs = jvm.buildArgs.map { StringPair(it.key, it.value) }
        request.imageTags = jvm.imageTags
        request.alwaysPullBaseImages.set(jvm.alwaysPullBaseImages)
        request.noCache.set(jvm.noCache)
        request.targetBuildStage.set(jvm.targetBuildStage)
        request.builderVersion.set(jvm.builderApiVersion)

        return request
    }

    private fun CreateContainerRequest(jvm: ContainerCreationSpec): CreateContainerRequest {
        val request = CreateContainerRequest(Runtime.getRuntime(nativeAPI))
        request.imageReference.set(jvm.image.id)
        request.name.set(jvm.name)
        request.command = jvm.command
        request.entrypoint = jvm.entrypoint
        request.workingDirectory.set(jvm.workingDirectory)
        request.hostname.set(jvm.hostname)
        request.extraHosts = jvm.extraHostsFormattedForDocker
        request.environmentVariables = jvm.environmentVariablesFormattedForDocker
        request.bindMounts = jvm.bindMountsFormattedForDocker
        request.tmpfsMounts = jvm.tmpfsMounts.map { StringPair(it.containerPath, it.options) }
        request.deviceMounts = jvm.deviceMounts
        request.exposedPorts = jvm.exposedPorts
        request.user.set(jvm.userAndGroupFormattedForDocker)
        request.useInitProcess.set(jvm.useInitProcess)
        request.shmSizeInBytes.set(jvm.shmSizeInBytes ?: 0)
        request.attachTTY.set(jvm.attachTTY)
        request.privileged.set(jvm.privileged)
        request.capabilitiesToAdd = jvm.capabilitiesToAdd.map { it.name }
        request.capabilitiesToDrop = jvm.capabilitiesToDrop.map { it.name }
        request.networkReference.set(jvm.network?.id)
        request.networkAliases = jvm.networkAliases
        request.logDriver.set(jvm.logDriver)
        request.loggingOptions = jvm.loggingOptions.map { StringPair(it.key, it.value) }
        request.healthcheckCommand = jvm.healthcheckCommand
        request.healthcheckInterval.set(jvm.healthcheckInterval?.inWholeNanoseconds ?: 0)
        request.healthcheckTimeout.set(jvm.healthcheckTimeout?.inWholeNanoseconds ?: 0)
        request.healthcheckStartPeriod.set(jvm.healthcheckStartPeriod?.inWholeNanoseconds ?: 0)
        request.healthcheckRetries.set(jvm.healthcheckRetries ?: 0)
        request.labels = jvm.labels.map { StringPair(it.key, it.value) }
        request.attachStdin.set(jvm.attachStdin)
        request.stdinOnce.set(jvm.stdinOnce)
        request.openStdin.set(jvm.openStdin)

        return request
    }

    private fun ContainerInspectionResult(native: batect.dockerclient.native.ContainerInspectionResult) =
        ContainerInspectionResult(
            ContainerReference(native.id.get()),
            native.name.get(),
            ContainerHostConfig(native.hostConfig!!),
            ContainerState(native.state!!),
            ContainerConfig(native.config!!)
        )

    private fun ContainerHostConfig(native: batect.dockerclient.native.ContainerHostConfig): ContainerHostConfig =
        ContainerHostConfig(ContainerLogConfig(native.logConfig!!))

    private fun ContainerLogConfig(native: batect.dockerclient.native.ContainerLogConfig): ContainerLogConfig =
        ContainerLogConfig(
            native.type.get(),
            native.config.associate { it.key.get() to it.value.get() }
        )

    private fun ContainerState(native: batect.dockerclient.native.ContainerState): ContainerState =
        ContainerState(
            if (native.health == null) null else ContainerHealthState(native.health!!)
        )

    private fun ContainerHealthState(native: batect.dockerclient.native.ContainerHealthState): ContainerHealthState =
        ContainerHealthState(
            native.status.get(),
            native.log.map { ContainerHealthLogEntry(it) }
        )

    private fun ContainerHealthLogEntry(native: batect.dockerclient.native.ContainerHealthLogEntry): ContainerHealthLogEntry =
        ContainerHealthLogEntry(
            Instant.fromEpochMilliseconds(native.start.get()),
            Instant.fromEpochMilliseconds(native.end.get()),
            native.exitCode.get(),
            native.output.get()
        )

    private fun ContainerConfig(native: batect.dockerclient.native.ContainerConfig): ContainerConfig =
        ContainerConfig(
            native.labels.associate { it.key.get() to it.value.get() },
            if (native.healthcheck == null) null else ContainerHealthcheckConfig(native.healthcheck!!)
        )

    private fun ContainerHealthcheckConfig(native: batect.dockerclient.native.ContainerHealthcheckConfig): ContainerHealthcheckConfig =
        ContainerHealthcheckConfig(
            native.test,
            native.interval.get().nanoseconds,
            native.timeout.get().nanoseconds,
            native.startPeriod.get().nanoseconds,
            native.retries.intValue()
        )

    private fun UploadToContainerRequest(items: Set<UploadItem>): UploadToContainerRequest {
        val directories = items.filterIsInstance<UploadDirectory>()
        val files = items.filterIsInstance<UploadFile>()
        val request = UploadToContainerRequest(Runtime.getRuntime(nativeAPI))
        request.directories = directories
        request.files = files

        return request
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
