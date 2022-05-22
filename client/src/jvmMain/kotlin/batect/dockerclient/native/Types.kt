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

package batect.dockerclient.native

import jnr.ffi.Pointer
import jnr.ffi.Runtime
import jnr.ffi.Struct
import jnr.ffi.annotations.Delegate

internal typealias DockerClientHandle = Long

internal typealias OutputStreamHandle = Long

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

    val caFilePath = UTF8StringRef()
    val certFilePath = UTF8StringRef()
    val keyFilePath = UTF8StringRef()
    val insecureSkipVerify = Boolean()

    override fun close() {
        nativeAPI.FreeTLSConfiguration(this)
    }
}

internal class ClientConfiguration(runtime: Runtime) : Struct(runtime), AutoCloseable {
    constructor(pointer: jnr.ffi.Pointer) : this(pointer.runtime) {
        this.useMemory(pointer)
    }

    val useConfigurationFromEnvironment = Boolean()
    val host = UTF8StringRef()
    val tlsPointer = Pointer()
    val tls: TLSConfiguration? by lazy { if (tlsPointer.intValue() == 0) null else TLSConfiguration(tlsPointer.get()) }
    val configDirectoryPath = UTF8StringRef()

    override fun close() {
        nativeAPI.FreeClientConfiguration(this)
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
