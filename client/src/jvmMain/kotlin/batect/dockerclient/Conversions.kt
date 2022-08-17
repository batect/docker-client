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

@file:JvmName("JVMConversions")
@file:Suppress("FunctionName", "FunctionNaming")

package batect.dockerclient

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
import batect.dockerclient.native.CreateExecRequest
import batect.dockerclient.native.StreamEventsRequest
import batect.dockerclient.native.StringPair
import batect.dockerclient.native.StringToStringListPair
import batect.dockerclient.native.TLSConfiguration
import batect.dockerclient.native.UploadToContainerRequest
import batect.dockerclient.native.attributes
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
import batect.dockerclient.native.filters
import batect.dockerclient.native.healthcheckCommand
import batect.dockerclient.native.imageTags
import batect.dockerclient.native.labels
import batect.dockerclient.native.log
import batect.dockerclient.native.loggingOptions
import batect.dockerclient.native.nativeAPI
import batect.dockerclient.native.networkAliases
import batect.dockerclient.native.test
import batect.dockerclient.native.tmpfsMounts
import batect.dockerclient.native.values
import jnr.ffi.Runtime
import jnr.ffi.Struct
import kotlinx.datetime.Instant
import okio.Path.Companion.toPath
import kotlin.time.Duration.Companion.nanoseconds

internal fun DockerClientConfiguration(native: ClientConfiguration): DockerClientConfiguration =
    DockerClientConfiguration(
        native.host.get(),
        if (native.tls == null) null else DockerClientTLSConfiguration(native.tls!!),
        TLSVerification.fromInsecureSkipVerify(native.insecureSkipVerify.get()),
        native.configDirectoryPath.get().toPath()
    )

internal fun DockerClientTLSConfiguration(native: TLSConfiguration): DockerClientTLSConfiguration {
    val caFileSize = native.caFileSize.get().toInt()
    val caFile = ByteArray(caFileSize)
    native.caFile.get().get(0, caFile, 0, caFileSize)

    val certFileSize = native.certFileSize.get().toInt()
    val certFile = ByteArray(certFileSize)
    native.certFile.get().get(0, certFile, 0, certFileSize)

    val keyFileSize = native.keyFileSize.get().toInt()
    val keyFile = ByteArray(keyFileSize)
    native.keyFile.get().get(0, keyFile, 0, keyFileSize)

    return DockerClientTLSConfiguration(caFile, certFile, keyFile)
}

internal fun VolumeReference(native: batect.dockerclient.native.VolumeReference): VolumeReference = VolumeReference(native.name.get())
internal fun NetworkReference(native: batect.dockerclient.native.NetworkReference): NetworkReference = NetworkReference(native.id.get())
internal fun ImageReference(native: batect.dockerclient.native.ImageReference): ImageReference = ImageReference(native.id.get())

internal fun ImagePullProgressUpdate(native: batect.dockerclient.native.PullImageProgressUpdate): ImagePullProgressUpdate =
    ImagePullProgressUpdate(native.message.get(), ImagePullProgressDetail(native.detail), native.id.get())

internal fun ImagePullProgressDetail(native: batect.dockerclient.native.PullImageProgressDetail?): ImagePullProgressDetail? =
    when (native) {
        null -> null
        else -> ImagePullProgressDetail(native.current.get(), native.total.get())
    }

internal fun ImageBuildProgressUpdate(native: BuildImageProgressUpdate): ImageBuildProgressUpdate = when {
    native.imageBuildContextUploadProgress != null -> contextUploadProgress(native.imageBuildContextUploadProgress!!)
    native.stepStarting != null -> StepStarting(native.stepStarting!!)
    native.stepOutput != null -> StepOutput(native.stepOutput!!)
    native.stepPullProgressUpdate != null -> StepPullProgressUpdate(native.stepPullProgressUpdate!!)
    native.stepDownloadProgressUpdate != null -> StepDownloadProgressUpdate(native.stepDownloadProgressUpdate!!)
    native.stepFinished != null -> StepFinished(native.stepFinished!!)
    native.buildFailed != null -> BuildFailed(native.buildFailed!!)
    else -> throw DockerClientException("${BuildImageProgressUpdate::class.qualifiedName} did not contain an update")
}

internal fun contextUploadProgress(native: BuildImageProgressUpdate_ImageBuildContextUploadProgress): ImageBuildProgressUpdate =
    when (native.stepNumber.get()) {
        0L -> ImageBuildContextUploadProgress(native.bytesUploaded.get())
        else -> StepContextUploadProgress(native.stepNumber.get(), native.bytesUploaded.get())
    }

internal fun StepStarting(native: BuildImageProgressUpdate_StepStarting): StepStarting =
    StepStarting(native.stepNumber.get(), native.stepName.get())

internal fun StepOutput(native: BuildImageProgressUpdate_StepOutput): StepOutput =
    StepOutput(native.stepNumber.get(), native.output.get())

internal fun StepPullProgressUpdate(native: BuildImageProgressUpdate_StepPullProgressUpdate): StepPullProgressUpdate =
    StepPullProgressUpdate(native.stepNumber.get(), ImagePullProgressUpdate(native.pullProgress!!))

internal fun StepDownloadProgressUpdate(native: BuildImageProgressUpdate_StepDownloadProgressUpdate): StepDownloadProgressUpdate =
    StepDownloadProgressUpdate(native.stepNumber.get(), native.downloadedBytes.get(), native.totalBytes.get())

internal fun StepFinished(native: BuildImageProgressUpdate_StepFinished): StepFinished =
    StepFinished(native.stepNumber.get())

internal fun BuildFailed(native: BuildImageProgressUpdate_BuildFailed): BuildFailed =
    BuildFailed(native.message.get())

internal fun ClientConfiguration(jvm: DockerClientConfiguration): ClientConfiguration {
    val config = ClientConfiguration(Runtime.getRuntime(nativeAPI))
    config.host.set(jvm.host)
    config.configDirectoryPath.set(jvm.configurationDirectory?.toString())
    config.insecureSkipVerify.set(jvm.daemonIdentityVerification.insecureSkipVerify)

    if (jvm.tls != null) {
        config.tlsPointer.set(Struct.getMemory(TLSConfiguration(jvm.tls)))
    } else {
        config.tlsPointer.set(0)
    }

    return config
}

internal fun TLSConfiguration(jvm: DockerClientTLSConfiguration): TLSConfiguration {
    val runtime = Runtime.getRuntime(nativeAPI)
    val memoryManager = runtime.memoryManager
    val tls = TLSConfiguration(runtime)

    tls.caFile.set(memoryManager.allocateDirect(jvm.caFile.size))
    tls.caFile.get().put(0, jvm.caFile, 0, jvm.caFile.size)
    tls.caFileSize.set(jvm.caFile.size)

    tls.certFile.set(memoryManager.allocateDirect(jvm.certFile.size))
    tls.certFile.get().put(0, jvm.certFile, 0, jvm.certFile.size)
    tls.certFileSize.set(jvm.certFile.size)

    tls.keyFile.set(memoryManager.allocateDirect(jvm.keyFile.size))
    tls.keyFile.get().put(0, jvm.keyFile, 0, jvm.keyFile.size)
    tls.keyFileSize.set(jvm.keyFile.size)

    return tls
}

internal fun BuildImageRequest(jvm: ImageBuildSpec): BuildImageRequest {
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

internal fun CreateContainerRequest(jvm: ContainerCreationSpec): CreateContainerRequest {
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

internal fun ContainerInspectionResult(native: batect.dockerclient.native.ContainerInspectionResult) =
    ContainerInspectionResult(
        ContainerReference(native.id.get()),
        native.name.get(),
        ContainerHostConfig(native.hostConfig!!),
        ContainerState(native.state!!),
        ContainerConfig(native.config!!)
    )

internal fun ContainerHostConfig(native: batect.dockerclient.native.ContainerHostConfig): ContainerHostConfig =
    ContainerHostConfig(ContainerLogConfig(native.logConfig!!))

internal fun ContainerLogConfig(native: batect.dockerclient.native.ContainerLogConfig): ContainerLogConfig =
    ContainerLogConfig(
        native.type.get(),
        native.config.associate { it.key.get() to it.value.get() }
    )

internal fun ContainerState(native: batect.dockerclient.native.ContainerState): ContainerState =
    ContainerState(
        if (native.health == null) null else ContainerHealthState(native.health!!)
    )

internal fun ContainerHealthState(native: batect.dockerclient.native.ContainerHealthState): ContainerHealthState =
    ContainerHealthState(
        native.status.get(),
        native.log.map { ContainerHealthLogEntry(it) }
    )

internal fun ContainerHealthLogEntry(native: batect.dockerclient.native.ContainerHealthLogEntry): ContainerHealthLogEntry =
    ContainerHealthLogEntry(
        Instant.fromEpochMilliseconds(native.start.get()),
        Instant.fromEpochMilliseconds(native.end.get()),
        native.exitCode.get(),
        native.output.get()
    )

internal fun ContainerConfig(native: batect.dockerclient.native.ContainerConfig): ContainerConfig =
    ContainerConfig(
        native.labels.associate { it.key.get() to it.value.get() },
        if (native.healthcheck == null) null else ContainerHealthcheckConfig(native.healthcheck!!)
    )

internal fun ContainerHealthcheckConfig(native: batect.dockerclient.native.ContainerHealthcheckConfig): ContainerHealthcheckConfig =
    ContainerHealthcheckConfig(
        native.test,
        native.interval.get().nanoseconds,
        native.timeout.get().nanoseconds,
        native.startPeriod.get().nanoseconds,
        native.retries.intValue()
    )

internal fun UploadToContainerRequest(items: Set<UploadItem>): UploadToContainerRequest {
    val directories = items.filterIsInstance<UploadDirectory>()
    val files = items.filterIsInstance<UploadFile>()
    val request = UploadToContainerRequest(Runtime.getRuntime(nativeAPI))
    request.directories = directories
    request.files = files

    return request
}

internal fun Event(native: batect.dockerclient.native.Event): Event = Event(
    native.type.get(),
    native.action.get(),
    Actor(native.actor!!),
    native.scope.get(),
    fromEpochNanoseconds(native.timestamp.get())
)

internal fun Actor(native: batect.dockerclient.native.Actor): Actor = Actor(
    native.id.get(),
    native.attributes.associate { it.key.get() to it.value.get() }
)

internal fun StreamEventsRequest(since: Instant?, until: Instant?, filters: Map<String, Set<String>>): StreamEventsRequest {
    val request = StreamEventsRequest(Runtime.getRuntime(nativeAPI))
    request.haveSinceFilter.set(since != null)
    request.sinceSeconds.set(since?.epochSeconds ?: 0)
    request.sinceNanoseconds.set(since?.nanosecondsOfSecond?.toLong() ?: 0)
    request.haveUntilFilter.set(until != null)
    request.untilSeconds.set(until?.epochSeconds ?: 0)
    request.untilNanoseconds.set(until?.nanosecondsOfSecond?.toLong() ?: 0)
    request.filters = filters.map { StringToStringListPair(it.key, it.value) }

    return request
}

internal fun CreateExecRequest(jvm: ContainerExecSpec): CreateExecRequest {
    val request = CreateExecRequest(Runtime.getRuntime(nativeAPI))
    request.containerID.set(jvm.container.id)
    request.command = jvm.command
    request.attachStdout.set(jvm.attachStdout)
    request.attachStderr.set(jvm.attachStderr)
    request.attachStdin.set(jvm.attachStdin)
    request.attachTTY.set(jvm.attachTTY)
    request.environmentVariables = jvm.environmentVariablesFormattedForDocker
    request.workingDirectory.set(jvm.workingDirectory)
    request.user.set(jvm.userAndGroupFormattedForDocker)
    request.privileged.set(jvm.privileged)

    return request
}

internal fun ContainerExecInspectionResult(native: batect.dockerclient.native.InspectExecResult): ContainerExecInspectionResult = ContainerExecInspectionResult(
    if (native.running.get()) null else native.exitCode.get(),
    native.running.get()
)

internal fun StringToStringListPair(key: String, values: Set<String>): StringToStringListPair {
    val pair = StringToStringListPair(Runtime.getRuntime(nativeAPI))
    pair.key.set(key)
    pair.values = values

    return pair
}
