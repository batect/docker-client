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

// AUTOGENERATED
// This file is autogenerated by the :client:generateJvmTypes Gradle task.
// Do not edit this file, as it will be regenerated automatically next time this project is built.

@file:Suppress("ClassNaming", "ClassName", "FunctionNaming", "MaxLineLength")

package batect.dockerclient.native

import jnr.ffi.Pointer
import jnr.ffi.Runtime
import jnr.ffi.Struct
import jnr.ffi.annotations.Delegate

internal typealias DockerClientHandle = Long

internal typealias OutputStreamHandle = Long

internal typealias InputStreamHandle = Long

internal typealias FileDescriptor = ULong

internal typealias ContextHandle = Long

internal class Error(runtime: Runtime) : Struct(runtime), AutoCloseable {
    constructor(pointer: jnr.ffi.Pointer) : this(pointer.runtime) {
        this.useMemory(pointer)
    }

    val type = UTF8StringRef()
    val message = UTF8StringRef()

    override fun close() {
        nativeAPI.FreeError(this)
    }
}

internal class TLSConfiguration(runtime: Runtime) : Struct(runtime), AutoCloseable {
    constructor(pointer: jnr.ffi.Pointer) : this(pointer.runtime) {
        this.useMemory(pointer)
    }

    val caFile = Pointer()
    val caFileSize = int32_t()
    val certFile = Pointer()
    val certFileSize = int32_t()
    val keyFile = Pointer()
    val keyFileSize = int32_t()

    override fun close() {
        nativeAPI.FreeTLSConfiguration(this)
    }
}

internal class ClientConfiguration(runtime: Runtime) : Struct(runtime), AutoCloseable {
    constructor(pointer: jnr.ffi.Pointer) : this(pointer.runtime) {
        this.useMemory(pointer)
    }

    val host = UTF8StringRef()
    val tlsPointer = Pointer()
    val tls: TLSConfiguration? by lazy { if (tlsPointer.intValue() == 0) null else TLSConfiguration(tlsPointer.get()) }
    val insecureSkipVerify = Boolean()
    val configDirectoryPath = UTF8StringRef()

    override fun close() {
        nativeAPI.FreeClientConfiguration(this)
    }
}

internal class DetermineCLIContextReturn(runtime: Runtime) : Struct(runtime), AutoCloseable {
    constructor(pointer: jnr.ffi.Pointer) : this(pointer.runtime) {
        this.useMemory(pointer)
    }

    val contextName = UTF8StringRef()
    val errorPointer = Pointer()
    val error: Error? by lazy { if (errorPointer.intValue() == 0) null else Error(errorPointer.get()) }

    override fun close() {
        nativeAPI.FreeDetermineCLIContextReturn(this)
    }
}

internal class LoadClientConfigurationFromCLIContextReturn(runtime: Runtime) : Struct(runtime), AutoCloseable {
    constructor(pointer: jnr.ffi.Pointer) : this(pointer.runtime) {
        this.useMemory(pointer)
    }

    val configurationPointer = Pointer()
    val configuration: ClientConfiguration? by lazy { if (configurationPointer.intValue() == 0) null else ClientConfiguration(configurationPointer.get()) }
    val errorPointer = Pointer()
    val error: Error? by lazy { if (errorPointer.intValue() == 0) null else Error(errorPointer.get()) }

    override fun close() {
        nativeAPI.FreeLoadClientConfigurationFromCLIContextReturn(this)
    }
}

internal class CreateClientReturn(runtime: Runtime) : Struct(runtime), AutoCloseable {
    constructor(pointer: jnr.ffi.Pointer) : this(pointer.runtime) {
        this.useMemory(pointer)
    }

    val client = u_int64_t()
    val errorPointer = Pointer()
    val error: Error? by lazy { if (errorPointer.intValue() == 0) null else Error(errorPointer.get()) }

    override fun close() {
        nativeAPI.FreeCreateClientReturn(this)
    }
}

internal class CreateOutputPipeReturn(runtime: Runtime) : Struct(runtime), AutoCloseable {
    constructor(pointer: jnr.ffi.Pointer) : this(pointer.runtime) {
        this.useMemory(pointer)
    }

    val outputStream = u_int64_t()
    val readFileDescriptor = uintptr_t()
    val errorPointer = Pointer()
    val error: Error? by lazy { if (errorPointer.intValue() == 0) null else Error(errorPointer.get()) }

    override fun close() {
        nativeAPI.FreeCreateOutputPipeReturn(this)
    }
}

internal class CreateInputPipeReturn(runtime: Runtime) : Struct(runtime), AutoCloseable {
    constructor(pointer: jnr.ffi.Pointer) : this(pointer.runtime) {
        this.useMemory(pointer)
    }

    val inputStream = u_int64_t()
    val writeFileDescriptor = uintptr_t()
    val errorPointer = Pointer()
    val error: Error? by lazy { if (errorPointer.intValue() == 0) null else Error(errorPointer.get()) }

    override fun close() {
        nativeAPI.FreeCreateInputPipeReturn(this)
    }
}

internal class PingResponse(runtime: Runtime) : Struct(runtime), AutoCloseable {
    constructor(pointer: jnr.ffi.Pointer) : this(pointer.runtime) {
        this.useMemory(pointer)
    }

    val apiVersion = UTF8StringRef()
    val osType = UTF8StringRef()
    val experimental = Boolean()
    val builderVersion = UTF8StringRef()

    override fun close() {
        nativeAPI.FreePingResponse(this)
    }
}

internal class PingReturn(runtime: Runtime) : Struct(runtime), AutoCloseable {
    constructor(pointer: jnr.ffi.Pointer) : this(pointer.runtime) {
        this.useMemory(pointer)
    }

    val responsePointer = Pointer()
    val response: PingResponse? by lazy { if (responsePointer.intValue() == 0) null else PingResponse(responsePointer.get()) }
    val errorPointer = Pointer()
    val error: Error? by lazy { if (errorPointer.intValue() == 0) null else Error(errorPointer.get()) }

    override fun close() {
        nativeAPI.FreePingReturn(this)
    }
}

internal class DaemonVersionInformation(runtime: Runtime) : Struct(runtime), AutoCloseable {
    constructor(pointer: jnr.ffi.Pointer) : this(pointer.runtime) {
        this.useMemory(pointer)
    }

    val version = UTF8StringRef()
    val apiVersion = UTF8StringRef()
    val minAPIVersion = UTF8StringRef()
    val gitCommit = UTF8StringRef()
    val operatingSystem = UTF8StringRef()
    val architecture = UTF8StringRef()
    val experimental = Boolean()

    override fun close() {
        nativeAPI.FreeDaemonVersionInformation(this)
    }
}

internal class GetDaemonVersionInformationReturn(runtime: Runtime) : Struct(runtime), AutoCloseable {
    constructor(pointer: jnr.ffi.Pointer) : this(pointer.runtime) {
        this.useMemory(pointer)
    }

    val responsePointer = Pointer()
    val response: DaemonVersionInformation? by lazy { if (responsePointer.intValue() == 0) null else DaemonVersionInformation(responsePointer.get()) }
    val errorPointer = Pointer()
    val error: Error? by lazy { if (errorPointer.intValue() == 0) null else Error(errorPointer.get()) }

    override fun close() {
        nativeAPI.FreeGetDaemonVersionInformationReturn(this)
    }
}

internal class VolumeReference(runtime: Runtime) : Struct(runtime), AutoCloseable {
    constructor(pointer: jnr.ffi.Pointer) : this(pointer.runtime) {
        this.useMemory(pointer)
    }

    val name = UTF8StringRef()

    override fun close() {
        nativeAPI.FreeVolumeReference(this)
    }
}

internal class CreateVolumeReturn(runtime: Runtime) : Struct(runtime), AutoCloseable {
    constructor(pointer: jnr.ffi.Pointer) : this(pointer.runtime) {
        this.useMemory(pointer)
    }

    val responsePointer = Pointer()
    val response: VolumeReference? by lazy { if (responsePointer.intValue() == 0) null else VolumeReference(responsePointer.get()) }
    val errorPointer = Pointer()
    val error: Error? by lazy { if (errorPointer.intValue() == 0) null else Error(errorPointer.get()) }

    override fun close() {
        nativeAPI.FreeCreateVolumeReturn(this)
    }
}

internal class ListAllVolumesReturn(runtime: Runtime) : Struct(runtime), AutoCloseable {
    constructor(pointer: jnr.ffi.Pointer) : this(pointer.runtime) {
        this.useMemory(pointer)
    }

    val volumesCount = u_int64_t()
    val volumesPointer = Pointer()
    val errorPointer = Pointer()
    val error: Error? by lazy { if (errorPointer.intValue() == 0) null else Error(errorPointer.get()) }

    override fun close() {
        nativeAPI.FreeListAllVolumesReturn(this)
    }
}

internal class NetworkReference(runtime: Runtime) : Struct(runtime), AutoCloseable {
    constructor(pointer: jnr.ffi.Pointer) : this(pointer.runtime) {
        this.useMemory(pointer)
    }

    val id = UTF8StringRef()

    override fun close() {
        nativeAPI.FreeNetworkReference(this)
    }
}

internal class CreateNetworkReturn(runtime: Runtime) : Struct(runtime), AutoCloseable {
    constructor(pointer: jnr.ffi.Pointer) : this(pointer.runtime) {
        this.useMemory(pointer)
    }

    val responsePointer = Pointer()
    val response: NetworkReference? by lazy { if (responsePointer.intValue() == 0) null else NetworkReference(responsePointer.get()) }
    val errorPointer = Pointer()
    val error: Error? by lazy { if (errorPointer.intValue() == 0) null else Error(errorPointer.get()) }

    override fun close() {
        nativeAPI.FreeCreateNetworkReturn(this)
    }
}

internal class GetNetworkByNameOrIDReturn(runtime: Runtime) : Struct(runtime), AutoCloseable {
    constructor(pointer: jnr.ffi.Pointer) : this(pointer.runtime) {
        this.useMemory(pointer)
    }

    val responsePointer = Pointer()
    val response: NetworkReference? by lazy { if (responsePointer.intValue() == 0) null else NetworkReference(responsePointer.get()) }
    val errorPointer = Pointer()
    val error: Error? by lazy { if (errorPointer.intValue() == 0) null else Error(errorPointer.get()) }

    override fun close() {
        nativeAPI.FreeGetNetworkByNameOrIDReturn(this)
    }
}

internal class ImageReference(runtime: Runtime) : Struct(runtime), AutoCloseable {
    constructor(pointer: jnr.ffi.Pointer) : this(pointer.runtime) {
        this.useMemory(pointer)
    }

    val id = UTF8StringRef()

    override fun close() {
        nativeAPI.FreeImageReference(this)
    }
}

internal class PullImageReturn(runtime: Runtime) : Struct(runtime), AutoCloseable {
    constructor(pointer: jnr.ffi.Pointer) : this(pointer.runtime) {
        this.useMemory(pointer)
    }

    val responsePointer = Pointer()
    val response: ImageReference? by lazy { if (responsePointer.intValue() == 0) null else ImageReference(responsePointer.get()) }
    val errorPointer = Pointer()
    val error: Error? by lazy { if (errorPointer.intValue() == 0) null else Error(errorPointer.get()) }

    override fun close() {
        nativeAPI.FreePullImageReturn(this)
    }
}

internal class PullImageProgressDetail(runtime: Runtime) : Struct(runtime), AutoCloseable {
    constructor(pointer: jnr.ffi.Pointer) : this(pointer.runtime) {
        this.useMemory(pointer)
    }

    val current = int64_t()
    val total = int64_t()

    override fun close() {
        nativeAPI.FreePullImageProgressDetail(this)
    }
}

internal class PullImageProgressUpdate(runtime: Runtime) : Struct(runtime), AutoCloseable {
    constructor(pointer: jnr.ffi.Pointer) : this(pointer.runtime) {
        this.useMemory(pointer)
    }

    val message = UTF8StringRef()
    val detailPointer = Pointer()
    val detail: PullImageProgressDetail? by lazy { if (detailPointer.intValue() == 0) null else PullImageProgressDetail(detailPointer.get()) }
    val id = UTF8StringRef()

    override fun close() {
        nativeAPI.FreePullImageProgressUpdate(this)
    }
}

internal interface PullImageProgressCallback {
    @Delegate
    fun invoke(userData: Pointer?, progressPointer: Pointer?): Boolean
}

internal class GetImageReturn(runtime: Runtime) : Struct(runtime), AutoCloseable {
    constructor(pointer: jnr.ffi.Pointer) : this(pointer.runtime) {
        this.useMemory(pointer)
    }

    val responsePointer = Pointer()
    val response: ImageReference? by lazy { if (responsePointer.intValue() == 0) null else ImageReference(responsePointer.get()) }
    val errorPointer = Pointer()
    val error: Error? by lazy { if (errorPointer.intValue() == 0) null else Error(errorPointer.get()) }

    override fun close() {
        nativeAPI.FreeGetImageReturn(this)
    }
}

internal class StringPair(runtime: Runtime) : Struct(runtime), AutoCloseable {
    constructor(pointer: jnr.ffi.Pointer) : this(pointer.runtime) {
        this.useMemory(pointer)
    }

    val key = UTF8StringRef()
    val value = UTF8StringRef()

    override fun close() {
        nativeAPI.FreeStringPair(this)
    }
}

internal class FileBuildSecret(runtime: Runtime) : Struct(runtime), AutoCloseable {
    constructor(pointer: jnr.ffi.Pointer) : this(pointer.runtime) {
        this.useMemory(pointer)
    }

    val id = UTF8StringRef()
    val path = UTF8StringRef()

    override fun close() {
        nativeAPI.FreeFileBuildSecret(this)
    }
}

internal class EnvironmentBuildSecret(runtime: Runtime) : Struct(runtime), AutoCloseable {
    constructor(pointer: jnr.ffi.Pointer) : this(pointer.runtime) {
        this.useMemory(pointer)
    }

    val id = UTF8StringRef()
    val sourceEnvironmentVariableName = UTF8StringRef()

    override fun close() {
        nativeAPI.FreeEnvironmentBuildSecret(this)
    }
}

internal class SSHAgent(runtime: Runtime) : Struct(runtime), AutoCloseable {
    constructor(pointer: jnr.ffi.Pointer) : this(pointer.runtime) {
        this.useMemory(pointer)
    }

    val id = UTF8StringRef()
    val pathsCount = u_int64_t()
    val pathsPointer = Pointer()

    override fun close() {
        nativeAPI.FreeSSHAgent(this)
    }
}

internal class BuildImageRequest(runtime: Runtime) : Struct(runtime), AutoCloseable {
    constructor(pointer: jnr.ffi.Pointer) : this(pointer.runtime) {
        this.useMemory(pointer)
    }

    val contextDirectory = UTF8StringRef()
    val pathToDockerfile = UTF8StringRef()
    val buildArgsCount = u_int64_t()
    val buildArgsPointer = Pointer()
    val imageTagsCount = u_int64_t()
    val imageTagsPointer = Pointer()
    val alwaysPullBaseImages = Boolean()
    val noCache = Boolean()
    val targetBuildStage = UTF8StringRef()
    val builderVersion = UTF8StringRef()
    val fileSecretsCount = u_int64_t()
    val fileSecretsPointer = Pointer()
    val environmentSecretsCount = u_int64_t()
    val environmentSecretsPointer = Pointer()
    val sshAgentsCount = u_int64_t()
    val sshAgentsPointer = Pointer()

    override fun close() {
        nativeAPI.FreeBuildImageRequest(this)
    }
}

internal class BuildImageReturn(runtime: Runtime) : Struct(runtime), AutoCloseable {
    constructor(pointer: jnr.ffi.Pointer) : this(pointer.runtime) {
        this.useMemory(pointer)
    }

    val responsePointer = Pointer()
    val response: ImageReference? by lazy { if (responsePointer.intValue() == 0) null else ImageReference(responsePointer.get()) }
    val errorPointer = Pointer()
    val error: Error? by lazy { if (errorPointer.intValue() == 0) null else Error(errorPointer.get()) }

    override fun close() {
        nativeAPI.FreeBuildImageReturn(this)
    }
}

internal class BuildImageProgressUpdate_ImageBuildContextUploadProgress(runtime: Runtime) : Struct(runtime), AutoCloseable {
    constructor(pointer: jnr.ffi.Pointer) : this(pointer.runtime) {
        this.useMemory(pointer)
    }

    val stepNumber = int64_t()
    val bytesUploaded = int64_t()

    override fun close() {
        nativeAPI.FreeBuildImageProgressUpdate_ImageBuildContextUploadProgress(this)
    }
}

internal class BuildImageProgressUpdate_StepStarting(runtime: Runtime) : Struct(runtime), AutoCloseable {
    constructor(pointer: jnr.ffi.Pointer) : this(pointer.runtime) {
        this.useMemory(pointer)
    }

    val stepNumber = int64_t()
    val stepName = UTF8StringRef()

    override fun close() {
        nativeAPI.FreeBuildImageProgressUpdate_StepStarting(this)
    }
}

internal class BuildImageProgressUpdate_StepOutput(runtime: Runtime) : Struct(runtime), AutoCloseable {
    constructor(pointer: jnr.ffi.Pointer) : this(pointer.runtime) {
        this.useMemory(pointer)
    }

    val stepNumber = int64_t()
    val output = UTF8StringRef()

    override fun close() {
        nativeAPI.FreeBuildImageProgressUpdate_StepOutput(this)
    }
}

internal class BuildImageProgressUpdate_StepPullProgressUpdate(runtime: Runtime) : Struct(runtime), AutoCloseable {
    constructor(pointer: jnr.ffi.Pointer) : this(pointer.runtime) {
        this.useMemory(pointer)
    }

    val stepNumber = int64_t()
    val pullProgressPointer = Pointer()
    val pullProgress: PullImageProgressUpdate? by lazy { if (pullProgressPointer.intValue() == 0) null else PullImageProgressUpdate(pullProgressPointer.get()) }

    override fun close() {
        nativeAPI.FreeBuildImageProgressUpdate_StepPullProgressUpdate(this)
    }
}

internal class BuildImageProgressUpdate_StepDownloadProgressUpdate(runtime: Runtime) : Struct(runtime), AutoCloseable {
    constructor(pointer: jnr.ffi.Pointer) : this(pointer.runtime) {
        this.useMemory(pointer)
    }

    val stepNumber = int64_t()
    val downloadedBytes = int64_t()
    val totalBytes = int64_t()

    override fun close() {
        nativeAPI.FreeBuildImageProgressUpdate_StepDownloadProgressUpdate(this)
    }
}

internal class BuildImageProgressUpdate_StepFinished(runtime: Runtime) : Struct(runtime), AutoCloseable {
    constructor(pointer: jnr.ffi.Pointer) : this(pointer.runtime) {
        this.useMemory(pointer)
    }

    val stepNumber = int64_t()

    override fun close() {
        nativeAPI.FreeBuildImageProgressUpdate_StepFinished(this)
    }
}

internal class BuildImageProgressUpdate_BuildFailed(runtime: Runtime) : Struct(runtime), AutoCloseable {
    constructor(pointer: jnr.ffi.Pointer) : this(pointer.runtime) {
        this.useMemory(pointer)
    }

    val message = UTF8StringRef()

    override fun close() {
        nativeAPI.FreeBuildImageProgressUpdate_BuildFailed(this)
    }
}

internal class BuildImageProgressUpdate(runtime: Runtime) : Struct(runtime), AutoCloseable {
    constructor(pointer: jnr.ffi.Pointer) : this(pointer.runtime) {
        this.useMemory(pointer)
    }

    val imageBuildContextUploadProgressPointer = Pointer()
    val imageBuildContextUploadProgress: BuildImageProgressUpdate_ImageBuildContextUploadProgress? by lazy { if (imageBuildContextUploadProgressPointer.intValue() == 0) null else BuildImageProgressUpdate_ImageBuildContextUploadProgress(imageBuildContextUploadProgressPointer.get()) }
    val stepStartingPointer = Pointer()
    val stepStarting: BuildImageProgressUpdate_StepStarting? by lazy { if (stepStartingPointer.intValue() == 0) null else BuildImageProgressUpdate_StepStarting(stepStartingPointer.get()) }
    val stepOutputPointer = Pointer()
    val stepOutput: BuildImageProgressUpdate_StepOutput? by lazy { if (stepOutputPointer.intValue() == 0) null else BuildImageProgressUpdate_StepOutput(stepOutputPointer.get()) }
    val stepPullProgressUpdatePointer = Pointer()
    val stepPullProgressUpdate: BuildImageProgressUpdate_StepPullProgressUpdate? by lazy { if (stepPullProgressUpdatePointer.intValue() == 0) null else BuildImageProgressUpdate_StepPullProgressUpdate(stepPullProgressUpdatePointer.get()) }
    val stepDownloadProgressUpdatePointer = Pointer()
    val stepDownloadProgressUpdate: BuildImageProgressUpdate_StepDownloadProgressUpdate? by lazy { if (stepDownloadProgressUpdatePointer.intValue() == 0) null else BuildImageProgressUpdate_StepDownloadProgressUpdate(stepDownloadProgressUpdatePointer.get()) }
    val stepFinishedPointer = Pointer()
    val stepFinished: BuildImageProgressUpdate_StepFinished? by lazy { if (stepFinishedPointer.intValue() == 0) null else BuildImageProgressUpdate_StepFinished(stepFinishedPointer.get()) }
    val buildFailedPointer = Pointer()
    val buildFailed: BuildImageProgressUpdate_BuildFailed? by lazy { if (buildFailedPointer.intValue() == 0) null else BuildImageProgressUpdate_BuildFailed(buildFailedPointer.get()) }

    override fun close() {
        nativeAPI.FreeBuildImageProgressUpdate(this)
    }
}

internal interface BuildImageProgressCallback {
    @Delegate
    fun invoke(userData: Pointer?, progressPointer: Pointer?): Boolean
}

internal class ContainerReference(runtime: Runtime) : Struct(runtime), AutoCloseable {
    constructor(pointer: jnr.ffi.Pointer) : this(pointer.runtime) {
        this.useMemory(pointer)
    }

    val id = UTF8StringRef()

    override fun close() {
        nativeAPI.FreeContainerReference(this)
    }
}

internal class DeviceMount(runtime: Runtime) : Struct(runtime), AutoCloseable {
    constructor(pointer: jnr.ffi.Pointer) : this(pointer.runtime) {
        this.useMemory(pointer)
    }

    val localPath = UTF8StringRef()
    val containerPath = UTF8StringRef()
    val permissions = UTF8StringRef()

    override fun close() {
        nativeAPI.FreeDeviceMount(this)
    }
}

internal class ExposedPort(runtime: Runtime) : Struct(runtime), AutoCloseable {
    constructor(pointer: jnr.ffi.Pointer) : this(pointer.runtime) {
        this.useMemory(pointer)
    }

    val localPort = int64_t()
    val containerPort = int64_t()
    val protocol = UTF8StringRef()

    override fun close() {
        nativeAPI.FreeExposedPort(this)
    }
}

internal class CreateContainerRequest(runtime: Runtime) : Struct(runtime), AutoCloseable {
    constructor(pointer: jnr.ffi.Pointer) : this(pointer.runtime) {
        this.useMemory(pointer)
    }

    val imageReference = UTF8StringRef()
    val name = UTF8StringRef()
    val commandCount = u_int64_t()
    val commandPointer = Pointer()
    val entrypointCount = u_int64_t()
    val entrypointPointer = Pointer()
    val workingDirectory = UTF8StringRef()
    val hostname = UTF8StringRef()
    val extraHostsCount = u_int64_t()
    val extraHostsPointer = Pointer()
    val environmentVariablesCount = u_int64_t()
    val environmentVariablesPointer = Pointer()
    val bindMountsCount = u_int64_t()
    val bindMountsPointer = Pointer()
    val tmpfsMountsCount = u_int64_t()
    val tmpfsMountsPointer = Pointer()
    val deviceMountsCount = u_int64_t()
    val deviceMountsPointer = Pointer()
    val exposedPortsCount = u_int64_t()
    val exposedPortsPointer = Pointer()
    val user = UTF8StringRef()
    val useInitProcess = Boolean()
    val shmSizeInBytes = int64_t()
    val attachTTY = Boolean()
    val privileged = Boolean()
    val capabilitiesToAddCount = u_int64_t()
    val capabilitiesToAddPointer = Pointer()
    val capabilitiesToDropCount = u_int64_t()
    val capabilitiesToDropPointer = Pointer()
    val networkReference = UTF8StringRef()
    val networkAliasesCount = u_int64_t()
    val networkAliasesPointer = Pointer()
    val logDriver = UTF8StringRef()
    val loggingOptionsCount = u_int64_t()
    val loggingOptionsPointer = Pointer()
    val healthcheckCommandCount = u_int64_t()
    val healthcheckCommandPointer = Pointer()
    val healthcheckInterval = int64_t()
    val healthcheckTimeout = int64_t()
    val healthcheckStartPeriod = int64_t()
    val healthcheckRetries = int64_t()
    val labelsCount = u_int64_t()
    val labelsPointer = Pointer()
    val attachStdin = Boolean()
    val stdinOnce = Boolean()
    val openStdin = Boolean()

    override fun close() {
        nativeAPI.FreeCreateContainerRequest(this)
    }
}

internal class CreateContainerReturn(runtime: Runtime) : Struct(runtime), AutoCloseable {
    constructor(pointer: jnr.ffi.Pointer) : this(pointer.runtime) {
        this.useMemory(pointer)
    }

    val responsePointer = Pointer()
    val response: ContainerReference? by lazy { if (responsePointer.intValue() == 0) null else ContainerReference(responsePointer.get()) }
    val errorPointer = Pointer()
    val error: Error? by lazy { if (errorPointer.intValue() == 0) null else Error(errorPointer.get()) }

    override fun close() {
        nativeAPI.FreeCreateContainerReturn(this)
    }
}

internal class WaitForContainerToExitReturn(runtime: Runtime) : Struct(runtime), AutoCloseable {
    constructor(pointer: jnr.ffi.Pointer) : this(pointer.runtime) {
        this.useMemory(pointer)
    }

    val exitCode = int64_t()
    val errorPointer = Pointer()
    val error: Error? by lazy { if (errorPointer.intValue() == 0) null else Error(errorPointer.get()) }

    override fun close() {
        nativeAPI.FreeWaitForContainerToExitReturn(this)
    }
}

internal interface ReadyCallback {
    @Delegate
    fun invoke(userData: Pointer?): Boolean
}

internal class ContainerHealthcheckConfig(runtime: Runtime) : Struct(runtime), AutoCloseable {
    constructor(pointer: jnr.ffi.Pointer) : this(pointer.runtime) {
        this.useMemory(pointer)
    }

    val testCount = u_int64_t()
    val testPointer = Pointer()
    val interval = int64_t()
    val timeout = int64_t()
    val startPeriod = int64_t()
    val retries = int64_t()

    override fun close() {
        nativeAPI.FreeContainerHealthcheckConfig(this)
    }
}

internal class ContainerConfig(runtime: Runtime) : Struct(runtime), AutoCloseable {
    constructor(pointer: jnr.ffi.Pointer) : this(pointer.runtime) {
        this.useMemory(pointer)
    }

    val labelsCount = u_int64_t()
    val labelsPointer = Pointer()
    val healthcheckPointer = Pointer()
    val healthcheck: ContainerHealthcheckConfig? by lazy { if (healthcheckPointer.intValue() == 0) null else ContainerHealthcheckConfig(healthcheckPointer.get()) }

    override fun close() {
        nativeAPI.FreeContainerConfig(this)
    }
}

internal class ContainerHealthLogEntry(runtime: Runtime) : Struct(runtime), AutoCloseable {
    constructor(pointer: jnr.ffi.Pointer) : this(pointer.runtime) {
        this.useMemory(pointer)
    }

    val start = int64_t()
    val end = int64_t()
    val exitCode = int64_t()
    val output = UTF8StringRef()

    override fun close() {
        nativeAPI.FreeContainerHealthLogEntry(this)
    }
}

internal class ContainerHealthState(runtime: Runtime) : Struct(runtime), AutoCloseable {
    constructor(pointer: jnr.ffi.Pointer) : this(pointer.runtime) {
        this.useMemory(pointer)
    }

    val status = UTF8StringRef()
    val logCount = u_int64_t()
    val logPointer = Pointer()

    override fun close() {
        nativeAPI.FreeContainerHealthState(this)
    }
}

internal class ContainerState(runtime: Runtime) : Struct(runtime), AutoCloseable {
    constructor(pointer: jnr.ffi.Pointer) : this(pointer.runtime) {
        this.useMemory(pointer)
    }

    val healthPointer = Pointer()
    val health: ContainerHealthState? by lazy { if (healthPointer.intValue() == 0) null else ContainerHealthState(healthPointer.get()) }

    override fun close() {
        nativeAPI.FreeContainerState(this)
    }
}

internal class ContainerLogConfig(runtime: Runtime) : Struct(runtime), AutoCloseable {
    constructor(pointer: jnr.ffi.Pointer) : this(pointer.runtime) {
        this.useMemory(pointer)
    }

    val type = UTF8StringRef()
    val configCount = u_int64_t()
    val configPointer = Pointer()

    override fun close() {
        nativeAPI.FreeContainerLogConfig(this)
    }
}

internal class ContainerHostConfig(runtime: Runtime) : Struct(runtime), AutoCloseable {
    constructor(pointer: jnr.ffi.Pointer) : this(pointer.runtime) {
        this.useMemory(pointer)
    }

    val logConfigPointer = Pointer()
    val logConfig: ContainerLogConfig? by lazy { if (logConfigPointer.intValue() == 0) null else ContainerLogConfig(logConfigPointer.get()) }

    override fun close() {
        nativeAPI.FreeContainerHostConfig(this)
    }
}

internal class ContainerInspectionResult(runtime: Runtime) : Struct(runtime), AutoCloseable {
    constructor(pointer: jnr.ffi.Pointer) : this(pointer.runtime) {
        this.useMemory(pointer)
    }

    val id = UTF8StringRef()
    val name = UTF8StringRef()
    val hostConfigPointer = Pointer()
    val hostConfig: ContainerHostConfig? by lazy { if (hostConfigPointer.intValue() == 0) null else ContainerHostConfig(hostConfigPointer.get()) }
    val statePointer = Pointer()
    val state: ContainerState? by lazy { if (statePointer.intValue() == 0) null else ContainerState(statePointer.get()) }
    val configPointer = Pointer()
    val config: ContainerConfig? by lazy { if (configPointer.intValue() == 0) null else ContainerConfig(configPointer.get()) }

    override fun close() {
        nativeAPI.FreeContainerInspectionResult(this)
    }
}

internal class InspectContainerReturn(runtime: Runtime) : Struct(runtime), AutoCloseable {
    constructor(pointer: jnr.ffi.Pointer) : this(pointer.runtime) {
        this.useMemory(pointer)
    }

    val responsePointer = Pointer()
    val response: ContainerInspectionResult? by lazy { if (responsePointer.intValue() == 0) null else ContainerInspectionResult(responsePointer.get()) }
    val errorPointer = Pointer()
    val error: Error? by lazy { if (errorPointer.intValue() == 0) null else Error(errorPointer.get()) }

    override fun close() {
        nativeAPI.FreeInspectContainerReturn(this)
    }
}

internal class UploadDirectory(runtime: Runtime) : Struct(runtime), AutoCloseable {
    constructor(pointer: jnr.ffi.Pointer) : this(pointer.runtime) {
        this.useMemory(pointer)
    }

    val path = UTF8StringRef()
    val owner = int32_t()
    val group = int32_t()
    val mode = int32_t()

    override fun close() {
        nativeAPI.FreeUploadDirectory(this)
    }
}

internal class UploadFile(runtime: Runtime) : Struct(runtime), AutoCloseable {
    constructor(pointer: jnr.ffi.Pointer) : this(pointer.runtime) {
        this.useMemory(pointer)
    }

    val path = UTF8StringRef()
    val owner = int32_t()
    val group = int32_t()
    val mode = int32_t()
    val contents = Pointer()
    val contentsSize = int32_t()

    override fun close() {
        nativeAPI.FreeUploadFile(this)
    }
}

internal class UploadToContainerRequest(runtime: Runtime) : Struct(runtime), AutoCloseable {
    constructor(pointer: jnr.ffi.Pointer) : this(pointer.runtime) {
        this.useMemory(pointer)
    }

    val directoriesCount = u_int64_t()
    val directoriesPointer = Pointer()
    val filesCount = u_int64_t()
    val filesPointer = Pointer()

    override fun close() {
        nativeAPI.FreeUploadToContainerRequest(this)
    }
}

internal class StringToStringListPair(runtime: Runtime) : Struct(runtime), AutoCloseable {
    constructor(pointer: jnr.ffi.Pointer) : this(pointer.runtime) {
        this.useMemory(pointer)
    }

    val key = UTF8StringRef()
    val valuesCount = u_int64_t()
    val valuesPointer = Pointer()

    override fun close() {
        nativeAPI.FreeStringToStringListPair(this)
    }
}

internal class StreamEventsRequest(runtime: Runtime) : Struct(runtime), AutoCloseable {
    constructor(pointer: jnr.ffi.Pointer) : this(pointer.runtime) {
        this.useMemory(pointer)
    }

    val haveSinceFilter = Boolean()
    val sinceSeconds = int64_t()
    val sinceNanoseconds = int64_t()
    val haveUntilFilter = Boolean()
    val untilSeconds = int64_t()
    val untilNanoseconds = int64_t()
    val filtersCount = u_int64_t()
    val filtersPointer = Pointer()

    override fun close() {
        nativeAPI.FreeStreamEventsRequest(this)
    }
}

internal class Actor(runtime: Runtime) : Struct(runtime), AutoCloseable {
    constructor(pointer: jnr.ffi.Pointer) : this(pointer.runtime) {
        this.useMemory(pointer)
    }

    val id = UTF8StringRef()
    val attributesCount = u_int64_t()
    val attributesPointer = Pointer()

    override fun close() {
        nativeAPI.FreeActor(this)
    }
}

internal class Event(runtime: Runtime) : Struct(runtime), AutoCloseable {
    constructor(pointer: jnr.ffi.Pointer) : this(pointer.runtime) {
        this.useMemory(pointer)
    }

    val type = UTF8StringRef()
    val action = UTF8StringRef()
    val actorPointer = Pointer()
    val actor: Actor? by lazy { if (actorPointer.intValue() == 0) null else Actor(actorPointer.get()) }
    val scope = UTF8StringRef()
    val timestamp = int64_t()

    override fun close() {
        nativeAPI.FreeEvent(this)
    }
}

internal interface EventCallback {
    @Delegate
    fun invoke(userData: Pointer?, eventPointer: Pointer?): Boolean
}

internal class CreateExecRequest(runtime: Runtime) : Struct(runtime), AutoCloseable {
    constructor(pointer: jnr.ffi.Pointer) : this(pointer.runtime) {
        this.useMemory(pointer)
    }

    val containerID = UTF8StringRef()
    val commandCount = u_int64_t()
    val commandPointer = Pointer()
    val attachStdout = Boolean()
    val attachStderr = Boolean()
    val attachStdin = Boolean()
    val attachTTY = Boolean()
    val environmentVariablesCount = u_int64_t()
    val environmentVariablesPointer = Pointer()
    val workingDirectory = UTF8StringRef()
    val user = UTF8StringRef()
    val privileged = Boolean()

    override fun close() {
        nativeAPI.FreeCreateExecRequest(this)
    }
}

internal class ContainerExecReference(runtime: Runtime) : Struct(runtime), AutoCloseable {
    constructor(pointer: jnr.ffi.Pointer) : this(pointer.runtime) {
        this.useMemory(pointer)
    }

    val id = UTF8StringRef()

    override fun close() {
        nativeAPI.FreeContainerExecReference(this)
    }
}

internal class CreateExecReturn(runtime: Runtime) : Struct(runtime), AutoCloseable {
    constructor(pointer: jnr.ffi.Pointer) : this(pointer.runtime) {
        this.useMemory(pointer)
    }

    val responsePointer = Pointer()
    val response: ContainerExecReference? by lazy { if (responsePointer.intValue() == 0) null else ContainerExecReference(responsePointer.get()) }
    val errorPointer = Pointer()
    val error: Error? by lazy { if (errorPointer.intValue() == 0) null else Error(errorPointer.get()) }

    override fun close() {
        nativeAPI.FreeCreateExecReturn(this)
    }
}

internal class InspectExecResult(runtime: Runtime) : Struct(runtime), AutoCloseable {
    constructor(pointer: jnr.ffi.Pointer) : this(pointer.runtime) {
        this.useMemory(pointer)
    }

    val exitCode = int64_t()
    val running = Boolean()

    override fun close() {
        nativeAPI.FreeInspectExecResult(this)
    }
}

internal class InspectExecReturn(runtime: Runtime) : Struct(runtime), AutoCloseable {
    constructor(pointer: jnr.ffi.Pointer) : this(pointer.runtime) {
        this.useMemory(pointer)
    }

    val responsePointer = Pointer()
    val response: InspectExecResult? by lazy { if (responsePointer.intValue() == 0) null else InspectExecResult(responsePointer.get()) }
    val errorPointer = Pointer()
    val error: Error? by lazy { if (errorPointer.intValue() == 0) null else Error(errorPointer.get()) }

    override fun close() {
        nativeAPI.FreeInspectExecReturn(this)
    }
}
