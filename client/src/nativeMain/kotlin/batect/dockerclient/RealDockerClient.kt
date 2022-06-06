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
import batect.dockerclient.native.BuildImageProgressUpdate_BuildFailed
import batect.dockerclient.native.BuildImageProgressUpdate_ImageBuildContextUploadProgress
import batect.dockerclient.native.BuildImageProgressUpdate_StepDownloadProgressUpdate
import batect.dockerclient.native.BuildImageProgressUpdate_StepFinished
import batect.dockerclient.native.BuildImageProgressUpdate_StepOutput
import batect.dockerclient.native.BuildImageProgressUpdate_StepPullProgressUpdate
import batect.dockerclient.native.BuildImageProgressUpdate_StepStarting
import batect.dockerclient.native.BuildImageRequest
import batect.dockerclient.native.ClientConfiguration
import batect.dockerclient.native.CreateClient
import batect.dockerclient.native.CreateContainer
import batect.dockerclient.native.CreateContainerRequest
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
import batect.dockerclient.native.ListAllVolumes
import batect.dockerclient.native.Ping
import batect.dockerclient.native.PruneImageBuildCache
import batect.dockerclient.native.PullImage
import batect.dockerclient.native.PullImageProgressDetail
import batect.dockerclient.native.PullImageProgressUpdate
import batect.dockerclient.native.RemoveContainer
import batect.dockerclient.native.StartContainer
import batect.dockerclient.native.StopContainer
import batect.dockerclient.native.StringPair
import batect.dockerclient.native.TLSConfiguration
import batect.dockerclient.native.WaitForContainerToExit
import kotlinx.cinterop.CFunction
import kotlinx.cinterop.COpaquePointer
import kotlinx.cinterop.CPointed
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.CPointerVar
import kotlinx.cinterop.MemScope
import kotlinx.cinterop.StableRef
import kotlinx.cinterop.alloc
import kotlinx.cinterop.allocArrayOf
import kotlinx.cinterop.asStableRef
import kotlinx.cinterop.cstr
import kotlinx.cinterop.get
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.pointed
import kotlinx.cinterop.ptr
import kotlinx.cinterop.staticCFunction
import kotlinx.cinterop.toKString
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.datetime.Instant
import kotlin.time.Duration
import kotlin.time.Duration.Companion.nanoseconds

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

    private fun MemScope.allocClientConfiguration(configuration: DockerClientConfiguration): ClientConfiguration {
        return alloc {
            UseConfigurationFromEnvironment = configuration.useConfigurationFromEnvironment
            Host = configuration.host?.cstr?.ptr
            ConfigDirectoryPath = configuration.configDirectoryPath?.cstr?.ptr

            if (configuration.tls != null) {
                TLS = alloc<TLSConfiguration> {
                    CAFilePath = configuration.tls.caFilePath.cstr.ptr
                    CertFilePath = configuration.tls.certFilePath.cstr.ptr
                    KeyFilePath = configuration.tls.keyFilePath.cstr.ptr
                    InsecureSkipVerify = configuration.tls.insecureSkipVerify
                }.ptr
            } else {
                TLS = null
            }
        }
    }

    public override suspend fun ping(): PingResponse {
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
                    BuilderVersion.fromAPI(response.BuilderVersion!!.toKString())
                )
            }
        }
    }

    public override suspend fun getDaemonVersionInformation(): DaemonVersionInformation {
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
                    response.Experimental
                )
            }
        }
    }

    public override suspend fun listAllVolumes(): Set<VolumeReference> {
        return launchWithGolangContext { context ->
            ListAllVolumes(clientHandle, context.handle)!!.use { ret ->
                if (ret.pointed.Error != null) {
                    throw ListAllVolumesFailedException(ret.pointed.Error!!.pointed)
                }

                fromArray(ret.pointed.Volumes!!, ret.pointed.VolumesCount) { VolumeReference(it) }.toSet()
            }
        }
    }

    public override suspend fun createVolume(name: String): VolumeReference {
        return launchWithGolangContext { context ->
            CreateVolume(clientHandle, context.handle, name.cstr)!!.use { ret ->
                if (ret.pointed.Error != null) {
                    throw VolumeCreationFailedException(ret.pointed.Error!!.pointed)
                }

                VolumeReference(ret.pointed.Response!!.pointed)
            }
        }
    }

    public override suspend fun deleteVolume(volume: VolumeReference) {
        return launchWithGolangContext { context ->
            DeleteVolume(clientHandle, context.handle, volume.name.cstr).ifFailed { error ->
                throw VolumeDeletionFailedException(error.pointed)
            }
        }
    }

    public override suspend fun createNetwork(name: String, driver: String): NetworkReference {
        return launchWithGolangContext { context ->
            CreateNetwork(clientHandle, context.handle, name.cstr, driver.cstr)!!.use { ret ->
                if (ret.pointed.Error != null) {
                    throw NetworkCreationFailedException(ret.pointed.Error!!.pointed)
                }

                NetworkReference(ret.pointed.Response!!.pointed)
            }
        }
    }

    public override suspend fun deleteNetwork(network: NetworkReference) {
        return launchWithGolangContext { context ->
            DeleteNetwork(clientHandle, context.handle, network.id.cstr).ifFailed { error ->
                throw NetworkDeletionFailedException(error.pointed)
            }
        }
    }

    public override suspend fun getNetworkByNameOrID(searchFor: String): NetworkReference? {
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

    public override suspend fun pullImage(name: String, onProgressUpdate: ImagePullProgressReceiver): ImageReference {
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

    public override suspend fun deleteImage(image: ImageReference, force: Boolean) {
        launchWithGolangContext { context ->
            DeleteImage(clientHandle, context.handle, image.id.cstr, force).ifFailed { error ->
                throw ImageDeletionFailedException(error.pointed)
            }
        }
    }

    public override suspend fun getImage(name: String): ImageReference? {
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

    public override suspend fun buildImage(spec: ImageBuildSpec, output: TextOutput, onProgressUpdate: ImageBuildProgressReceiver): ImageReference {
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
                                errorType
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

    private fun MemScope.allocBuildImageRequest(spec: ImageBuildSpec): BuildImageRequest {
        return alloc {
            ContextDirectory = spec.contextDirectory.toString().cstr.ptr
            PathToDockerfile = spec.pathToDockerfile.toString().cstr.ptr
            BuildArgs = allocArrayOf(spec.buildArgs.map { allocStringPair(it).ptr })
            BuildArgsCount = spec.buildArgs.size.toULong()
            ImageTags = allocArrayOf(spec.imageTags.map { it.cstr.ptr })
            ImageTagsCount = spec.imageTags.size.toULong()
            AlwaysPullBaseImages = spec.alwaysPullBaseImages
            NoCache = spec.noCache
            TargetBuildStage = spec.targetBuildStage.cstr.ptr
            BuilderVersion = spec.builderApiVersion?.cstr?.ptr
        }
    }

    public override suspend fun pruneImageBuildCache() {
        launchWithGolangContext { context ->
            PruneImageBuildCache(clientHandle, context.handle).ifFailed { error ->
                throw ImageBuildCachePruneFailedException(error.pointed)
            }
        }
    }

    public override suspend fun createContainer(spec: ContainerCreationSpec): ContainerReference {
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

    private fun MemScope.allocCreateContainerRequest(spec: ContainerCreationSpec): CreateContainerRequest {
        return alloc {
            ImageReference = spec.image.id.cstr.ptr
            Name = spec.name?.cstr?.ptr
            Command = allocArrayOf(spec.command.map { it.cstr.ptr })
            CommandCount = spec.command.size.toULong()
            Entrypoint = allocArrayOf(spec.entrypoint.map { it.cstr.ptr })
            EntrypointCount = spec.entrypoint.size.toULong()
            WorkingDirectory = spec.workingDirectory?.cstr?.ptr
            Hostname = spec.hostname?.cstr?.ptr
            ExtraHosts = allocArrayOf(spec.extraHostsFormattedForDocker.map { it.cstr.ptr })
            ExtraHostsCount = spec.extraHostsFormattedForDocker.size.toULong()
            EnvironmentVariables = allocArrayOf(spec.environmentVariablesFormattedForDocker.map { it.cstr.ptr })
            EnvironmentVariablesCount = spec.environmentVariablesFormattedForDocker.size.toULong()
            BindMounts = allocArrayOf(spec.bindMountsFormattedForDocker.map { it.cstr.ptr })
            BindMountsCount = spec.bindMountsFormattedForDocker.size.toULong()
            TmpfsMounts = allocArrayOf(spec.tmpfsMounts.map { allocStringPair(it).ptr })
            TmpfsMountsCount = spec.tmpfsMounts.size.toULong()
            DeviceMounts = allocArrayOf(spec.deviceMounts.map { allocDeviceMount(it).ptr })
            DeviceMountsCount = spec.deviceMounts.size.toULong()
            ExposedPorts = allocArrayOf(spec.exposedPorts.map { allocExposedPort(it).ptr })
            ExposedPortsCount = spec.exposedPorts.size.toULong()
            User = spec.userAndGroupFormattedForDocker?.cstr?.ptr
            UseInitProcess = spec.useInitProcess
            ShmSizeInBytes = spec.shmSizeInBytes ?: 0
            AttachTTY = spec.attachTTY
            Privileged = spec.privileged
            CapabilitiesToAdd = allocArrayOf(spec.capabilitiesToAdd.map { it.name.cstr.ptr })
            CapabilitiesToAddCount = spec.capabilitiesToAdd.size.toULong()
            CapabilitiesToDrop = allocArrayOf(spec.capabilitiesToDrop.map { it.name.cstr.ptr })
            CapabilitiesToDropCount = spec.capabilitiesToDrop.size.toULong()
            NetworkReference = spec.network?.id?.cstr?.ptr
            NetworkAliases = allocArrayOf(spec.networkAliases.map { it.cstr.ptr })
            NetworkAliasesCount = spec.networkAliases.size.toULong()
            LogDriver = spec.logDriver?.cstr?.ptr
            LoggingOptions = allocArrayOf(spec.loggingOptions.map { allocStringPair(it).ptr })
            LoggingOptionsCount = spec.loggingOptions.size.toULong()
            HealthcheckCommand = allocArrayOf(spec.healthcheckCommand.map { it.cstr.ptr })
            HealthcheckCommandCount = spec.healthcheckCommand.size.toULong()
            HealthcheckInterval = spec.healthcheckInterval?.inWholeNanoseconds ?: 0
            HealthcheckTimeout = spec.healthcheckTimeout?.inWholeNanoseconds ?: 0
            HealthcheckStartPeriod = spec.healthcheckStartPeriod?.inWholeNanoseconds ?: 0
            HealthcheckRetries = spec.healthcheckRetries?.toLong() ?: 0
            Labels = allocArrayOf(spec.labels.map { allocStringPair(it.key, it.value).ptr })
            LabelsCount = spec.labels.size.toULong()
        }
    }

    private fun MemScope.allocStringPair(mount: TmpfsMount): StringPair = allocStringPair(mount.containerPath, mount.options)
    private fun MemScope.allocStringPair(entry: Map.Entry<String, String>): StringPair = allocStringPair(entry.key, entry.value)

    private fun MemScope.allocStringPair(key: String, value: String): StringPair {
        return alloc {
            Key = key.cstr.ptr
            Value = value.cstr.ptr
        }
    }

    private fun MemScope.allocDeviceMount(mount: DeviceMount): batect.dockerclient.native.DeviceMount {
        return alloc {
            LocalPath = mount.localPath.toString().cstr.ptr
            ContainerPath = mount.containerPath.cstr.ptr
            Permissions = mount.permissions.cstr.ptr
        }
    }

    private fun MemScope.allocExposedPort(port: ExposedPort): batect.dockerclient.native.ExposedPort {
        return alloc {
            LocalPort = port.localPort
            ContainerPort = port.containerPort
            Protocol = port.protocol.cstr.ptr
        }
    }

    public override suspend fun startContainer(container: ContainerReference) {
        launchWithGolangContext { context ->
            StartContainer(clientHandle, context.handle, container.id.cstr).ifFailed { error ->
                throw ContainerStartFailedException(error.pointed)
            }
        }
    }

    public override suspend fun stopContainer(container: ContainerReference, timeout: Duration) {
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
        attachedNotification: ReadyNotification?
    ) {
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
                                    callbackUserData
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

    override fun close() {
        DisposeClient(clientHandle).ifFailed { error ->
            throw DockerClientException(error.pointed)
        }
    }
}

private fun VolumeReference(native: batect.dockerclient.native.VolumeReference): VolumeReference =
    VolumeReference(native.Name!!.toKString())

private fun NetworkReference(native: batect.dockerclient.native.NetworkReference): NetworkReference =
    NetworkReference(native.ID!!.toKString())

private fun ImageReference(native: batect.dockerclient.native.ImageReference): ImageReference =
    ImageReference(native.ID!!.toKString())

private fun ImagePullProgressUpdate(native: PullImageProgressUpdate): ImagePullProgressUpdate =
    ImagePullProgressUpdate(
        native.Message!!.toKString(),
        if (native.Detail == null) null else ImagePullProgressDetail(native.Detail!!.pointed),
        native.ID!!.toKString()
    )

private fun ImagePullProgressDetail(native: PullImageProgressDetail): ImagePullProgressDetail =
    ImagePullProgressDetail(native.Current, native.Total)

private fun ImageBuildProgressUpdate(native: BuildImageProgressUpdate): ImageBuildProgressUpdate = when {
    native.ImageBuildContextUploadProgress != null -> contextUploadProgress(native.ImageBuildContextUploadProgress!!.pointed)
    native.StepStarting != null -> StepStarting(native.StepStarting!!.pointed)
    native.StepOutput != null -> StepOutput(native.StepOutput!!.pointed)
    native.StepPullProgressUpdate != null -> StepPullProgressUpdate(native.StepPullProgressUpdate!!.pointed)
    native.StepDownloadProgressUpdate != null -> StepDownloadProgressUpdate(native.StepDownloadProgressUpdate!!.pointed)
    native.StepFinished != null -> StepFinished(native.StepFinished!!.pointed)
    native.BuildFailed != null -> BuildFailed(native.BuildFailed!!.pointed)
    else -> throw RuntimeException("${BuildImageProgressUpdate::class.qualifiedName} did not contain an update")
}

private fun contextUploadProgress(native: BuildImageProgressUpdate_ImageBuildContextUploadProgress): ImageBuildProgressUpdate =
    when (native.StepNumber) {
        0L -> ImageBuildContextUploadProgress(native.BytesUploaded)
        else -> StepContextUploadProgress(native.StepNumber, native.BytesUploaded)
    }

private fun StepStarting(native: BuildImageProgressUpdate_StepStarting): StepStarting =
    StepStarting(
        native.StepNumber,
        native.StepName!!.toKString()
    )

private fun StepOutput(native: BuildImageProgressUpdate_StepOutput): StepOutput =
    StepOutput(
        native.StepNumber,
        native.Output!!.toKString()
    )

private fun StepPullProgressUpdate(native: BuildImageProgressUpdate_StepPullProgressUpdate): StepPullProgressUpdate =
    StepPullProgressUpdate(
        native.StepNumber,
        ImagePullProgressUpdate(native.PullProgress!!.pointed)
    )

private fun StepDownloadProgressUpdate(native: BuildImageProgressUpdate_StepDownloadProgressUpdate): StepDownloadProgressUpdate =
    StepDownloadProgressUpdate(
        native.StepNumber,
        native.DownloadedBytes,
        native.TotalBytes
    )

private fun StepFinished(native: BuildImageProgressUpdate_StepFinished): StepFinished =
    StepFinished(native.StepNumber)

private fun BuildFailed(native: BuildImageProgressUpdate_BuildFailed): BuildFailed =
    BuildFailed(native.Message!!.toKString())

private fun ContainerInspectionResult(native: batect.dockerclient.native.ContainerInspectionResult): ContainerInspectionResult = ContainerInspectionResult(
    ContainerReference(native.ID!!.toKString()),
    native.Name!!.toKString(),
    ContainerHostConfig(native.HostConfig!!.pointed),
    ContainerState(native.State!!.pointed),
    ContainerConfig(native.Config!!.pointed)
)

private fun ContainerHostConfig(native: batect.dockerclient.native.ContainerHostConfig): ContainerHostConfig =
    ContainerHostConfig(ContainerLogConfig(native.LogConfig!!.pointed))

private fun ContainerLogConfig(native: batect.dockerclient.native.ContainerLogConfig): ContainerLogConfig =
    ContainerLogConfig(
        native.Type!!.toKString(),
        fromArray(native.Config!!, native.ConfigCount) { it.Key!!.toKString() to it.Value!!.toKString() }.associate { it }
    )

private fun ContainerState(native: batect.dockerclient.native.ContainerState): ContainerState =
    ContainerState(
        if (native.Health == null) null else ContainerHealthState(native.Health!!.pointed)
    )

private fun ContainerHealthState(native: batect.dockerclient.native.ContainerHealthState): ContainerHealthState =
    ContainerHealthState(
        native.Status!!.toKString(),
        fromArray(native.Log!!, native.LogCount) { ContainerHealthLogEntry(it) }
    )

private fun ContainerHealthLogEntry(native: batect.dockerclient.native.ContainerHealthLogEntry): ContainerHealthLogEntry =
    ContainerHealthLogEntry(
        Instant.fromEpochMilliseconds(native.Start),
        Instant.fromEpochMilliseconds(native.End),
        native.ExitCode,
        native.Output!!.toKString()
    )

private fun ContainerConfig(native: batect.dockerclient.native.ContainerConfig): ContainerConfig =
    ContainerConfig(
        fromArray(native.Labels!!, native.LabelsCount) { it.Key!!.toKString() to it.Value!!.toKString() }.associate { it },
        if (native.Healthcheck == null) null else ContainerHealthcheckConfig(native.Healthcheck!!.pointed)
    )

private fun ContainerHealthcheckConfig(native: batect.dockerclient.native.ContainerHealthcheckConfig): ContainerHealthcheckConfig =
    ContainerHealthcheckConfig(
        fromArray(native.Test!!, native.TestCount) { it.ptr.toKString() },
        native.Interval.nanoseconds,
        native.Timeout.nanoseconds,
        native.StartPeriod.nanoseconds,
        native.Retries.toInt()
    )

private inline fun <reified NativeType : CPointed, KotlinType> fromArray(
    source: CPointer<CPointerVar<NativeType>>,
    count: ULong,
    creator: (NativeType) -> KotlinType
): List<KotlinType> {
    return (0.toULong().until(count))
        .map { i -> creator(source[i.toLong()]!!.pointed) }
}

// What's this for?
// Kotlin/Native does not handle exceptions that propagate out of Kotlin/Native well.
// For example, if a C function invokes a Kotlin function, and that Kotlin function throws an exception,
// the process crashes.
// While we can make sure our own code invoked by C functions don't throw exceptions, we can't make the
// same guarantee for functions provided by users of this library, such as progress reporting callback
// functions.
// This is a helper class that helps us capture exceptions and report them later on.
private class CallbackState<ParameterType : CPointed>(private val callbackFunction: (CPointer<ParameterType>?) -> Unit) {
    var exceptionThrown: Throwable? = null

    fun <R> use(user: (CPointer<CFunction<(COpaquePointer?, CPointer<ParameterType>?) -> Boolean>>, COpaquePointer) -> R): R = StableRef.create(this).use { userDataRef ->
        val callback = staticCFunction { userData: COpaquePointer?, param: CPointer<ParameterType>? ->
            val callbackState = userData!!.asStableRef<CallbackState<ParameterType>>().get()

            try {
                callbackState.callbackFunction(param)
                true
            } catch (t: Throwable) {
                callbackState.exceptionThrown = t
                false
            }
        }

        user(callback, userDataRef.asCPointer())
    }
}

private class ReadyNotificationCallbackState(private val readyNotification: ReadyNotification?) {
    var exceptionThrown: Throwable? = null

    fun <R> use(user: (CPointer<CFunction<(COpaquePointer?) -> Boolean>>, COpaquePointer) -> R): R = StableRef.create(this).use { userDataRef ->
        val callback = staticCFunction { userData: COpaquePointer? ->
            val callbackState = userData!!.asStableRef<ReadyNotificationCallbackState>().get()

            try {
                callbackState.readyNotification?.markAsReady()
                true
            } catch (t: Throwable) {
                callbackState.exceptionThrown = t
                false
            }
        }

        user(callback, userDataRef.asCPointer())
    }
}
