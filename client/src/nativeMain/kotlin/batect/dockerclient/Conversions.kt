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
import batect.dockerclient.native.PullImageProgressDetail
import batect.dockerclient.native.PullImageProgressUpdate
import batect.dockerclient.native.StreamEventsRequest
import batect.dockerclient.native.StringPair
import batect.dockerclient.native.StringToStringListPair
import batect.dockerclient.native.TLSConfiguration
import batect.dockerclient.native.UploadToContainerRequest
import kotlinx.cinterop.CPointed
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.CPointerVar
import kotlinx.cinterop.MemScope
import kotlinx.cinterop.alloc
import kotlinx.cinterop.allocArrayOf
import kotlinx.cinterop.allocArrayOfPointersTo
import kotlinx.cinterop.cstr
import kotlinx.cinterop.get
import kotlinx.cinterop.pointed
import kotlinx.cinterop.ptr
import kotlinx.cinterop.readBytes
import kotlinx.cinterop.toCValues
import kotlinx.cinterop.toKString
import kotlinx.datetime.Instant
import okio.Path
import okio.Path.Companion.toPath
import kotlin.time.Duration.Companion.nanoseconds

internal fun DockerClientConfiguration(native: batect.dockerclient.native.ClientConfiguration): DockerClientConfiguration {
    val configDirectory = native.ConfigDirectoryPath!!.toKString()

    return DockerClientConfiguration(
        native.Host!!.toKString(),
        if (native.TLS == null) null else DockerClientTLSConfiguration(native.TLS!!.pointed),
        TLSVerification.fromInsecureSkipVerify(native.InsecureSkipVerify),
        if (configDirectory == "") null else configDirectory.toPath()
    )
}

internal fun DockerClientTLSConfiguration(native: TLSConfiguration): DockerClientTLSConfiguration =
    DockerClientTLSConfiguration(
        native.CAFile!!.readBytes(native.CAFileSize),
        native.CertFile!!.readBytes(native.CertFileSize),
        native.KeyFile!!.readBytes(native.KeyFileSize)
    )

internal fun VolumeReference(native: batect.dockerclient.native.VolumeReference): VolumeReference =
    VolumeReference(native.Name!!.toKString())

internal fun NetworkReference(native: batect.dockerclient.native.NetworkReference): NetworkReference =
    NetworkReference(native.ID!!.toKString())

internal fun ImageReference(native: batect.dockerclient.native.ImageReference): ImageReference =
    ImageReference(native.ID!!.toKString())

internal fun ImagePullProgressUpdate(native: PullImageProgressUpdate): ImagePullProgressUpdate =
    ImagePullProgressUpdate(
        native.Message!!.toKString(),
        if (native.Detail == null) null else ImagePullProgressDetail(native.Detail!!.pointed),
        native.ID!!.toKString()
    )

internal fun ImagePullProgressDetail(native: PullImageProgressDetail): ImagePullProgressDetail =
    ImagePullProgressDetail(native.Current, native.Total)

internal fun ImageBuildProgressUpdate(native: BuildImageProgressUpdate): ImageBuildProgressUpdate = when {
    native.ImageBuildContextUploadProgress != null -> contextUploadProgress(native.ImageBuildContextUploadProgress!!.pointed)
    native.StepStarting != null -> StepStarting(native.StepStarting!!.pointed)
    native.StepOutput != null -> StepOutput(native.StepOutput!!.pointed)
    native.StepPullProgressUpdate != null -> StepPullProgressUpdate(native.StepPullProgressUpdate!!.pointed)
    native.StepDownloadProgressUpdate != null -> StepDownloadProgressUpdate(native.StepDownloadProgressUpdate!!.pointed)
    native.StepFinished != null -> StepFinished(native.StepFinished!!.pointed)
    native.BuildFailed != null -> BuildFailed(native.BuildFailed!!.pointed)
    else -> throw DockerClientException("${BuildImageProgressUpdate::class.qualifiedName} did not contain an update")
}

internal fun contextUploadProgress(native: BuildImageProgressUpdate_ImageBuildContextUploadProgress): ImageBuildProgressUpdate =
    when (native.StepNumber) {
        0L -> ImageBuildContextUploadProgress(native.BytesUploaded)
        else -> StepContextUploadProgress(native.StepNumber, native.BytesUploaded)
    }

internal fun StepStarting(native: BuildImageProgressUpdate_StepStarting): StepStarting =
    StepStarting(
        native.StepNumber,
        native.StepName!!.toKString()
    )

internal fun StepOutput(native: BuildImageProgressUpdate_StepOutput): StepOutput =
    StepOutput(
        native.StepNumber,
        native.Output!!.toKString()
    )

internal fun StepPullProgressUpdate(native: BuildImageProgressUpdate_StepPullProgressUpdate): StepPullProgressUpdate =
    StepPullProgressUpdate(
        native.StepNumber,
        ImagePullProgressUpdate(native.PullProgress!!.pointed)
    )

internal fun StepDownloadProgressUpdate(native: BuildImageProgressUpdate_StepDownloadProgressUpdate): StepDownloadProgressUpdate =
    StepDownloadProgressUpdate(
        native.StepNumber,
        native.DownloadedBytes,
        native.TotalBytes
    )

internal fun StepFinished(native: BuildImageProgressUpdate_StepFinished): StepFinished =
    StepFinished(native.StepNumber)

internal fun BuildFailed(native: BuildImageProgressUpdate_BuildFailed): BuildFailed =
    BuildFailed(native.Message!!.toKString())

internal fun ContainerInspectionResult(native: batect.dockerclient.native.ContainerInspectionResult): ContainerInspectionResult = ContainerInspectionResult(
    ContainerReference(native.ID!!.toKString()),
    native.Name!!.toKString(),
    ContainerHostConfig(native.HostConfig!!.pointed),
    ContainerState(native.State!!.pointed),
    ContainerConfig(native.Config!!.pointed)
)

internal fun ContainerHostConfig(native: batect.dockerclient.native.ContainerHostConfig): ContainerHostConfig =
    ContainerHostConfig(ContainerLogConfig(native.LogConfig!!.pointed))

internal fun ContainerLogConfig(native: batect.dockerclient.native.ContainerLogConfig): ContainerLogConfig =
    ContainerLogConfig(
        native.Type!!.toKString(),
        mapFromStringPairs(native.Config!!, native.ConfigCount)
    )

internal fun ContainerState(native: batect.dockerclient.native.ContainerState): ContainerState =
    ContainerState(
        if (native.Health == null) null else ContainerHealthState(native.Health!!.pointed)
    )

internal fun ContainerHealthState(native: batect.dockerclient.native.ContainerHealthState): ContainerHealthState =
    ContainerHealthState(
        native.Status!!.toKString(),
        fromArray(native.Log!!, native.LogCount) { ContainerHealthLogEntry(it) }
    )

internal fun ContainerHealthLogEntry(native: batect.dockerclient.native.ContainerHealthLogEntry): ContainerHealthLogEntry =
    ContainerHealthLogEntry(
        Instant.fromEpochMilliseconds(native.Start),
        Instant.fromEpochMilliseconds(native.End),
        native.ExitCode,
        native.Output!!.toKString()
    )

internal fun ContainerConfig(native: batect.dockerclient.native.ContainerConfig): ContainerConfig =
    ContainerConfig(
        mapFromStringPairs(native.Labels!!, native.LabelsCount),
        if (native.Healthcheck == null) null else ContainerHealthcheckConfig(native.Healthcheck!!.pointed)
    )

internal fun ContainerHealthcheckConfig(native: batect.dockerclient.native.ContainerHealthcheckConfig): ContainerHealthcheckConfig =
    ContainerHealthcheckConfig(
        fromArray(native.Test!!, native.TestCount) { it.ptr.toKString() },
        native.Interval.nanoseconds,
        native.Timeout.nanoseconds,
        native.StartPeriod.nanoseconds,
        native.Retries.toInt()
    )

internal fun Event(native: batect.dockerclient.native.Event): Event =
    Event(
        native.Type!!.toKString(),
        native.Action!!.toKString(),
        Actor(native.Actor!!.pointed),
        native.Scope!!.toKString(),
        fromEpochNanoseconds(native.Timestamp)
    )

internal fun Actor(native: batect.dockerclient.native.Actor): Actor =
    Actor(
        native.ID!!.toKString(),
        mapFromStringPairs(native.Attributes!!, native.AttributesCount)
    )

internal inline fun <reified NativeType : CPointed, KotlinType> fromArray(
    source: CPointer<CPointerVar<NativeType>>,
    count: ULong,
    creator: (NativeType) -> KotlinType
): List<KotlinType> {
    return (0.toULong().until(count))
        .map { i -> creator(source[i.toLong()]!!.pointed) }
}

private fun mapFromStringPairs(array: CPointer<CPointerVar<StringPair>>, count: ULong): Map<String, String> =
    fromArray(array, count) { it.Key!!.toKString() to it.Value!!.toKString() }.associate { it }

internal fun MemScope.allocArrayOfPointersTo(strings: Iterable<String>) = allocArrayOf(strings.map { it.cstr.ptr })
internal fun MemScope.allocStringPair(mount: TmpfsMount): StringPair = allocStringPair(mount.containerPath, mount.options)
internal fun MemScope.allocStringPair(entry: Map.Entry<String, String>): StringPair = allocStringPair(entry.key, entry.value)

internal fun MemScope.allocClientConfiguration(configuration: DockerClientConfiguration): ClientConfiguration {
    return alloc<ClientConfiguration> {
        Host = configuration.host.cstr.ptr
        ConfigDirectoryPath = configuration.configurationDirectory?.toString()?.cstr?.ptr
        InsecureSkipVerify = configuration.daemonIdentityVerification.insecureSkipVerify

        TLS = if (configuration.tls != null) {
            allocTLSConfiguration(configuration.tls as DockerClientTLSConfiguration).ptr
        } else {
            null
        }
    }
}

internal fun MemScope.allocTLSConfiguration(configuration: DockerClientTLSConfiguration): TLSConfiguration {
    return alloc<TLSConfiguration> {
        CAFile = configuration.caFile.toCValues().ptr
        CAFileSize = configuration.caFile.size
        CertFile = configuration.certFile.toCValues().ptr
        CertFileSize = configuration.certFile.size
        KeyFile = configuration.keyFile.toCValues().ptr
        KeyFileSize = configuration.keyFile.size
    }
}

internal fun MemScope.allocBuildImageRequest(spec: ImageBuildSpec): BuildImageRequest {
    return alloc<BuildImageRequest> {
        ContextDirectory = spec.contextDirectory.toString().cstr.ptr
        PathToDockerfile = spec.pathToDockerfile.toString().cstr.ptr
        BuildArgs = allocArrayOfPointersTo(spec.buildArgs.map { allocStringPair(it) })
        BuildArgsCount = spec.buildArgs.size.toULong()
        ImageTags = allocArrayOfPointersTo(spec.imageTags)
        ImageTagsCount = spec.imageTags.size.toULong()
        AlwaysPullBaseImages = spec.alwaysPullBaseImages
        NoCache = spec.noCache
        TargetBuildStage = spec.targetBuildStage.cstr.ptr
        BuilderVersion = spec.builderApiVersion?.cstr?.ptr
        SSHAgents = allocArrayOfPointersTo(spec.sshAgents.map { allocSSHAgent(it) })
        SSHAgentsCount = spec.sshAgents.size.toULong()

        val fileSecrets = spec.secrets
            .filterValues { it is FileBuildSecret }
            .map { (key, secret) ->
                allocFileBuildSecret(key, (secret as FileBuildSecret).source)
            }

        FileSecrets = allocArrayOfPointersTo(fileSecrets)
        FileSecretsCount = fileSecrets.size.toULong()

        val environmentSecrets = spec.secrets
            .filterValues { it is EnvironmentBuildSecret }
            .map { (key, secret) ->
                allocEnvironmentBuildSecret(key, (secret as EnvironmentBuildSecret).sourceEnvironmentVariableName)
            }

        EnvironmentSecrets = allocArrayOfPointersTo(environmentSecrets)
        EnvironmentSecretsCount = environmentSecrets.size.toULong()

        CacheFrom = allocArrayOfPointersTo(spec.cacheFrom.map { allocBuildCache(it) })
        CacheFromCount = spec.cacheFrom.size.toULong()

        CacheTo = allocArrayOfPointersTo(spec.cacheTo.map { allocBuildCache(it) })
        CacheToCount = spec.cacheTo.size.toULong()

        if (spec.builder is ImageBuilder.BuildKit) {
            BuildKitInstanceType = spec.builder.instance.golangConstantValue.toLong()

            if (spec.builder.instance is BuildKitInstance.Named) {
                BuildKitInstanceName = spec.builder.instance.name.cstr.ptr
            }
        }
    }
}

internal fun MemScope.allocFileBuildSecret(id: String, source: Path): batect.dockerclient.native.FileBuildSecret {
    return alloc<batect.dockerclient.native.FileBuildSecret> {
        ID = id.cstr.ptr
        Path = source.toString().cstr.ptr
    }
}

internal fun MemScope.allocEnvironmentBuildSecret(id: String, source: String): batect.dockerclient.native.EnvironmentBuildSecret {
    return alloc<batect.dockerclient.native.EnvironmentBuildSecret> {
        ID = id.cstr.ptr
        SourceEnvironmentVariableName = source.cstr.ptr
    }
}

internal fun MemScope.allocSSHAgent(agent: SSHAgent): batect.dockerclient.native.SSHAgent {
    return alloc<batect.dockerclient.native.SSHAgent> {
        ID = agent.id.cstr.ptr
        Paths = allocArrayOfPointersTo(agent.paths.map { it.toString() })
        PathsCount = agent.paths.size.toULong()
    }
}

internal fun MemScope.allocBuildCache(cache: ImageBuildCache): batect.dockerclient.native.ImageBuildCache {
    return alloc<batect.dockerclient.native.ImageBuildCache> {
        Type = cache.type.cstr.ptr
        Attributes = allocArrayOfPointersTo(cache.attributes.map { allocStringPair(it) })
        AttributesCount = cache.attributes.size.toULong()
    }
}

internal fun MemScope.allocCreateContainerRequest(spec: ContainerCreationSpec): CreateContainerRequest {
    return alloc<CreateContainerRequest> {
        ImageReference = spec.image.id.cstr.ptr
        Name = spec.name?.cstr?.ptr
        Command = allocArrayOfPointersTo(spec.command)
        CommandCount = spec.command.size.toULong()
        Entrypoint = allocArrayOfPointersTo(spec.entrypoint)
        EntrypointCount = spec.entrypoint.size.toULong()
        WorkingDirectory = spec.workingDirectory?.cstr?.ptr
        Hostname = spec.hostname?.cstr?.ptr
        ExtraHosts = allocArrayOfPointersTo(spec.extraHostsFormattedForDocker)
        ExtraHostsCount = spec.extraHostsFormattedForDocker.size.toULong()
        EnvironmentVariables = allocArrayOfPointersTo(spec.environmentVariablesFormattedForDocker)
        EnvironmentVariablesCount = spec.environmentVariablesFormattedForDocker.size.toULong()
        BindMounts = allocArrayOfPointersTo(spec.bindMountsFormattedForDocker)
        BindMountsCount = spec.bindMountsFormattedForDocker.size.toULong()
        TmpfsMounts = allocArrayOfPointersTo(spec.tmpfsMounts.map { allocStringPair(it) })
        TmpfsMountsCount = spec.tmpfsMounts.size.toULong()
        DeviceMounts = allocArrayOfPointersTo(spec.deviceMounts.map { allocDeviceMount(it) })
        DeviceMountsCount = spec.deviceMounts.size.toULong()
        ExposedPorts = allocArrayOfPointersTo(spec.exposedPorts.map { allocExposedPort(it) })
        ExposedPortsCount = spec.exposedPorts.size.toULong()
        User = spec.userAndGroupFormattedForDocker?.cstr?.ptr
        UseInitProcess = spec.useInitProcess
        ShmSizeInBytes = spec.shmSizeInBytes ?: 0
        AttachTTY = spec.attachTTY
        Privileged = spec.privileged
        CapabilitiesToAdd = allocArrayOfPointersTo(spec.capabilitiesToAdd.map { it.name })
        CapabilitiesToAddCount = spec.capabilitiesToAdd.size.toULong()
        CapabilitiesToDrop = allocArrayOfPointersTo(spec.capabilitiesToDrop.map { it.name })
        CapabilitiesToDropCount = spec.capabilitiesToDrop.size.toULong()
        NetworkReference = spec.network?.id?.cstr?.ptr
        NetworkAliases = allocArrayOfPointersTo(spec.networkAliases)
        NetworkAliasesCount = spec.networkAliases.size.toULong()
        LogDriver = spec.logDriver?.cstr?.ptr
        LoggingOptions = allocArrayOfPointersTo(spec.loggingOptions.map { allocStringPair(it) })
        LoggingOptionsCount = spec.loggingOptions.size.toULong()
        HealthcheckCommand = allocArrayOfPointersTo(spec.healthcheckCommand)
        HealthcheckCommandCount = spec.healthcheckCommand.size.toULong()
        HealthcheckInterval = spec.healthcheckInterval?.inWholeNanoseconds ?: 0
        HealthcheckTimeout = spec.healthcheckTimeout?.inWholeNanoseconds ?: 0
        HealthcheckStartPeriod = spec.healthcheckStartPeriod?.inWholeNanoseconds ?: 0
        HealthcheckRetries = spec.healthcheckRetries?.toLong() ?: 0
        Labels = allocArrayOfPointersTo(spec.labels.map { allocStringPair(it.key, it.value) })
        LabelsCount = spec.labels.size.toULong()
        AttachStdin = spec.attachStdin
        StdinOnce = spec.stdinOnce
        OpenStdin = spec.openStdin
    }
}

internal fun MemScope.allocStringPair(key: String, value: String): StringPair {
    return alloc<StringPair> {
        Key = key.cstr.ptr
        Value = value.cstr.ptr
    }
}

internal fun MemScope.allocDeviceMount(mount: DeviceMount): batect.dockerclient.native.DeviceMount {
    return alloc<batect.dockerclient.native.DeviceMount> {
        LocalPath = mount.localPath.toString().cstr.ptr
        ContainerPath = mount.containerPath.cstr.ptr
        Permissions = mount.permissions.cstr.ptr
    }
}

internal fun MemScope.allocExposedPort(port: ExposedPort): batect.dockerclient.native.ExposedPort {
    return alloc<batect.dockerclient.native.ExposedPort> {
        LocalPort = port.localPort
        ContainerPort = port.containerPort
        Protocol = port.protocol.cstr.ptr
    }
}

internal fun MemScope.allocUploadToContainerRequest(items: Set<UploadItem>): UploadToContainerRequest {
    return alloc<UploadToContainerRequest> {
        val directories = items.filterIsInstance<UploadDirectory>()
        val files = items.filterIsInstance<UploadFile>()

        Directories = allocArrayOfPointersTo(directories.map { allocUploadDirectory(it) })
        DirectoriesCount = directories.size.toULong()
        Files = allocArrayOfPointersTo(files.map { allocUploadFile(it) })
        FilesCount = files.size.toULong()
    }
}

internal fun MemScope.allocUploadDirectory(directory: UploadDirectory): batect.dockerclient.native.UploadDirectory {
    return alloc<batect.dockerclient.native.UploadDirectory> {
        Path = directory.path.cstr.ptr
        Owner = directory.owner
        Group = directory.group
        Mode = directory.mode
    }
}

internal fun MemScope.allocUploadFile(file: UploadFile): batect.dockerclient.native.UploadFile {
    return alloc<batect.dockerclient.native.UploadFile> {
        Path = file.path.cstr.ptr
        Owner = file.owner
        Group = file.group
        Mode = file.mode
        Contents = file.contents.toCValues().ptr
        ContentsSize = file.contents.size
    }
}

internal fun MemScope.allocFilters(filters: Map<String, Set<String>>) = allocArrayOfPointersTo(filters.map { (key, value) -> allocStringToStringListPair(key, value) })

internal fun MemScope.allocStringToStringListPair(key: String, values: Set<String>) = alloc<StringToStringListPair> {
    Key = key.cstr.ptr
    Values = allocArrayOfPointersTo(values)
    ValuesCount = values.size.toULong()
}

internal fun MemScope.allocStreamEventsRequest(since: Instant?, until: Instant?, filters: Map<String, Set<String>>): StreamEventsRequest = alloc<StreamEventsRequest> {
    HaveSinceFilter = since != null
    SinceSeconds = since?.epochSeconds ?: 0
    SinceNanoseconds = since?.nanosecondsOfSecond?.toLong() ?: 0
    HaveUntilFilter = until != null
    UntilSeconds = until?.epochSeconds ?: 0
    UntilNanoseconds = until?.nanosecondsOfSecond?.toLong() ?: 0
    Filters = allocFilters(filters)
    FiltersCount = filters.size.toULong()
}

internal fun MemScope.allocCreateExecRequest(spec: ContainerExecSpec): CreateExecRequest = alloc<CreateExecRequest> {
    ContainerID = spec.container.id.cstr.ptr
    Command = allocArrayOfPointersTo(spec.command)
    CommandCount = spec.command.size.toULong()
    AttachStdout = spec.attachStdout
    AttachStderr = spec.attachStderr
    AttachStdin = spec.attachStdin
    AttachTTY = spec.attachTTY
    EnvironmentVariables = allocArrayOfPointersTo(spec.environmentVariablesFormattedForDocker)
    EnvironmentVariablesCount = spec.environmentVariables.size.toULong()
    WorkingDirectory = spec.workingDirectory?.cstr?.ptr
    User = spec.userAndGroupFormattedForDocker?.cstr?.ptr
    Privileged = spec.privileged
}

internal fun ContainerExecInspectionResult(native: batect.dockerclient.native.InspectExecResult): ContainerExecInspectionResult = ContainerExecInspectionResult(
    if (native.Running) null else native.ExitCode,
    native.Running
)
