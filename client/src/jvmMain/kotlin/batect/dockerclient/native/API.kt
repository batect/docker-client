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
// This file is autogenerated by the :client:generateJvmMethods Gradle task.
// Do not edit this file, as it will be regenerated automatically next time this project is built.

@file:Suppress("LongParameterList", "MaxLineLength")

package batect.dockerclient.native

import jnr.ffi.Pointer
import jnr.ffi.annotations.In

@Suppress("FunctionName")
internal interface API {
    fun CreateClient(@In cfg: ClientConfiguration): CreateClientReturn?
    fun DisposeClient(@In clientHandle: DockerClientHandle): Error?
    fun SetClientProxySettingsForTest(@In clientHandle: DockerClientHandle)
    fun LoadClientConfigurationFromCLIContext(@In contextName: kotlin.String, @In configDir: kotlin.String): LoadClientConfigurationFromCLIContextReturn?
    fun CreateContainer(@In clientHandle: DockerClientHandle, @In contextHandle: ContextHandle, @In request: CreateContainerRequest): CreateContainerReturn?
    fun StartContainer(@In clientHandle: DockerClientHandle, @In contextHandle: ContextHandle, @In id: kotlin.String): Error?
    fun StopContainer(@In clientHandle: DockerClientHandle, @In contextHandle: ContextHandle, @In id: kotlin.String, @In timeoutSeconds: Long): Error?
    fun RemoveContainer(@In clientHandle: DockerClientHandle, @In contextHandle: ContextHandle, @In id: kotlin.String, @In force: Boolean, @In removeVolumes: Boolean): Error?
    fun AttachToContainerOutput(@In clientHandle: DockerClientHandle, @In contextHandle: ContextHandle, @In id: kotlin.String, @In stdoutStreamHandle: OutputStreamHandle, @In stderrStreamHandle: OutputStreamHandle, @In stdinStreamHandle: InputStreamHandle, @In onReady: ReadyCallback, @In callbackUserData: Pointer?): Error?
    fun WaitForContainerToExit(@In clientHandle: DockerClientHandle, @In contextHandle: ContextHandle, @In id: kotlin.String, @In onReady: ReadyCallback, @In callbackUserData: Pointer?): WaitForContainerToExitReturn?
    fun InspectContainer(@In clientHandle: DockerClientHandle, @In contextHandle: ContextHandle, @In idOrName: kotlin.String): InspectContainerReturn?
    fun UploadToContainer(@In clientHandle: DockerClientHandle, @In contextHandle: ContextHandle, @In containerID: kotlin.String, @In request: UploadToContainerRequest, @In destinationPath: kotlin.String): Error?
    fun CreateContext(): ContextHandle
    fun CancelContext(@In contextHandle: ContextHandle)
    fun DestroyContext(@In contextHandle: ContextHandle): Error?
    fun Ping(@In clientHandle: DockerClientHandle, @In contextHandle: ContextHandle): PingReturn?
    fun GetDaemonVersionInformation(@In clientHandle: DockerClientHandle, @In contextHandle: ContextHandle): GetDaemonVersionInformationReturn?
    fun StreamEvents(@In clientHandle: DockerClientHandle, @In contextHandle: ContextHandle, @In request: StreamEventsRequest, @In onEvent: EventCallback, @In callbackUserData: Pointer?): Error?
    fun CreateExec(@In clientHandle: DockerClientHandle, @In contextHandle: ContextHandle, @In request: CreateExecRequest): CreateExecReturn?
    fun StartExecDetached(@In clientHandle: DockerClientHandle, @In contextHandle: ContextHandle, @In id: kotlin.String): Error?
    fun InspectExec(@In clientHandle: DockerClientHandle, @In contextHandle: ContextHandle, @In id: kotlin.String): InspectExecReturn?
    fun StartAndAttachToExec(@In clientHandle: DockerClientHandle, @In contextHandle: ContextHandle, @In id: kotlin.String, @In attachTTY: Boolean, @In stdoutStreamHandle: OutputStreamHandle, @In stderrStreamHandle: OutputStreamHandle, @In stdinStreamHandle: InputStreamHandle): Error?
    fun DeleteImage(@In clientHandle: DockerClientHandle, @In contextHandle: ContextHandle, @In ref: kotlin.String, @In force: Boolean): Error?
    fun GetImage(@In clientHandle: DockerClientHandle, @In contextHandle: ContextHandle, @In ref: kotlin.String): GetImageReturn?
    fun ValidateImageTag(@In tag: kotlin.String): Error?
    fun BuildImage(@In clientHandle: DockerClientHandle, @In contextHandle: ContextHandle, @In request: BuildImageRequest, @In outputStreamHandle: OutputStreamHandle, @In onProgressUpdate: BuildImageProgressCallback, @In callbackUserData: Pointer?): BuildImageReturn?
    fun PruneImageBuildCache(@In clientHandle: DockerClientHandle, @In contextHandle: ContextHandle): Error?
    fun PullImage(@In clientHandle: DockerClientHandle, @In contextHandle: ContextHandle, @In ref: kotlin.String, @In onProgressUpdate: PullImageProgressCallback, @In callbackUserData: Pointer?): PullImageReturn?
    fun CreateInputPipe(): CreateInputPipeReturn?
    fun CloseInputPipeWriteEnd(@In handle: InputStreamHandle): Error?
    fun DisposeInputPipe(@In handle: InputStreamHandle): Error?
    fun CreateOutputPipe(): CreateOutputPipeReturn?
    fun DisposeOutputPipe(@In handle: OutputStreamHandle): Error?
    fun CreateNetwork(@In clientHandle: DockerClientHandle, @In contextHandle: ContextHandle, @In name: kotlin.String, @In driver: kotlin.String): CreateNetworkReturn?
    fun DeleteNetwork(@In clientHandle: DockerClientHandle, @In contextHandle: ContextHandle, @In id: kotlin.String): Error?
    fun GetNetworkByNameOrID(@In clientHandle: DockerClientHandle, @In contextHandle: ContextHandle, @In searchFor: kotlin.String): GetNetworkByNameOrIDReturn?
    fun GetEnvironmentVariable(@In name: kotlin.String): kotlin.String?
    fun UnsetEnvironmentVariable(@In name: kotlin.String): Error?
    fun SetEnvironmentVariable(@In name: kotlin.String, @In value: kotlin.String): Error?
    fun CreateVolume(@In clientHandle: DockerClientHandle, @In contextHandle: ContextHandle, @In name: kotlin.String): CreateVolumeReturn?
    fun DeleteVolume(@In clientHandle: DockerClientHandle, @In contextHandle: ContextHandle, @In name: kotlin.String): Error?
    fun ListAllVolumes(@In clientHandle: DockerClientHandle, @In contextHandle: ContextHandle): ListAllVolumesReturn?
    fun FreeError(@In value: Error)
    fun AllocError(): Error?
    fun FreeTLSConfiguration(@In value: TLSConfiguration)
    fun AllocTLSConfiguration(): TLSConfiguration?
    fun FreeClientConfiguration(@In value: ClientConfiguration)
    fun AllocClientConfiguration(): ClientConfiguration?
    fun FreeLoadClientConfigurationFromCLIContextReturn(@In value: LoadClientConfigurationFromCLIContextReturn)
    fun AllocLoadClientConfigurationFromCLIContextReturn(): LoadClientConfigurationFromCLIContextReturn?
    fun FreeCreateClientReturn(@In value: CreateClientReturn)
    fun AllocCreateClientReturn(): CreateClientReturn?
    fun FreeCreateOutputPipeReturn(@In value: CreateOutputPipeReturn)
    fun AllocCreateOutputPipeReturn(): CreateOutputPipeReturn?
    fun FreeCreateInputPipeReturn(@In value: CreateInputPipeReturn)
    fun AllocCreateInputPipeReturn(): CreateInputPipeReturn?
    fun FreePingResponse(@In value: PingResponse)
    fun AllocPingResponse(): PingResponse?
    fun FreePingReturn(@In value: PingReturn)
    fun AllocPingReturn(): PingReturn?
    fun FreeDaemonVersionInformation(@In value: DaemonVersionInformation)
    fun AllocDaemonVersionInformation(): DaemonVersionInformation?
    fun FreeGetDaemonVersionInformationReturn(@In value: GetDaemonVersionInformationReturn)
    fun AllocGetDaemonVersionInformationReturn(): GetDaemonVersionInformationReturn?
    fun FreeVolumeReference(@In value: VolumeReference)
    fun AllocVolumeReference(): VolumeReference?
    fun FreeCreateVolumeReturn(@In value: CreateVolumeReturn)
    fun AllocCreateVolumeReturn(): CreateVolumeReturn?
    fun FreeListAllVolumesReturn(@In value: ListAllVolumesReturn)
    fun AllocListAllVolumesReturn(): ListAllVolumesReturn?
    fun FreeNetworkReference(@In value: NetworkReference)
    fun AllocNetworkReference(): NetworkReference?
    fun FreeCreateNetworkReturn(@In value: CreateNetworkReturn)
    fun AllocCreateNetworkReturn(): CreateNetworkReturn?
    fun FreeGetNetworkByNameOrIDReturn(@In value: GetNetworkByNameOrIDReturn)
    fun AllocGetNetworkByNameOrIDReturn(): GetNetworkByNameOrIDReturn?
    fun FreeImageReference(@In value: ImageReference)
    fun AllocImageReference(): ImageReference?
    fun FreePullImageReturn(@In value: PullImageReturn)
    fun AllocPullImageReturn(): PullImageReturn?
    fun FreePullImageProgressDetail(@In value: PullImageProgressDetail)
    fun AllocPullImageProgressDetail(): PullImageProgressDetail?
    fun FreePullImageProgressUpdate(@In value: PullImageProgressUpdate)
    fun AllocPullImageProgressUpdate(): PullImageProgressUpdate?
    fun FreeGetImageReturn(@In value: GetImageReturn)
    fun AllocGetImageReturn(): GetImageReturn?
    fun FreeStringPair(@In value: StringPair)
    fun AllocStringPair(): StringPair?
    fun FreeBuildImageRequest(@In value: BuildImageRequest)
    fun AllocBuildImageRequest(): BuildImageRequest?
    fun FreeBuildImageReturn(@In value: BuildImageReturn)
    fun AllocBuildImageReturn(): BuildImageReturn?
    fun FreeBuildImageProgressUpdate_ImageBuildContextUploadProgress(@In value: BuildImageProgressUpdate_ImageBuildContextUploadProgress)
    fun AllocBuildImageProgressUpdate_ImageBuildContextUploadProgress(): BuildImageProgressUpdate_ImageBuildContextUploadProgress?
    fun FreeBuildImageProgressUpdate_StepStarting(@In value: BuildImageProgressUpdate_StepStarting)
    fun AllocBuildImageProgressUpdate_StepStarting(): BuildImageProgressUpdate_StepStarting?
    fun FreeBuildImageProgressUpdate_StepOutput(@In value: BuildImageProgressUpdate_StepOutput)
    fun AllocBuildImageProgressUpdate_StepOutput(): BuildImageProgressUpdate_StepOutput?
    fun FreeBuildImageProgressUpdate_StepPullProgressUpdate(@In value: BuildImageProgressUpdate_StepPullProgressUpdate)
    fun AllocBuildImageProgressUpdate_StepPullProgressUpdate(): BuildImageProgressUpdate_StepPullProgressUpdate?
    fun FreeBuildImageProgressUpdate_StepDownloadProgressUpdate(@In value: BuildImageProgressUpdate_StepDownloadProgressUpdate)
    fun AllocBuildImageProgressUpdate_StepDownloadProgressUpdate(): BuildImageProgressUpdate_StepDownloadProgressUpdate?
    fun FreeBuildImageProgressUpdate_StepFinished(@In value: BuildImageProgressUpdate_StepFinished)
    fun AllocBuildImageProgressUpdate_StepFinished(): BuildImageProgressUpdate_StepFinished?
    fun FreeBuildImageProgressUpdate_BuildFailed(@In value: BuildImageProgressUpdate_BuildFailed)
    fun AllocBuildImageProgressUpdate_BuildFailed(): BuildImageProgressUpdate_BuildFailed?
    fun FreeBuildImageProgressUpdate(@In value: BuildImageProgressUpdate)
    fun AllocBuildImageProgressUpdate(): BuildImageProgressUpdate?
    fun FreeContainerReference(@In value: ContainerReference)
    fun AllocContainerReference(): ContainerReference?
    fun FreeDeviceMount(@In value: DeviceMount)
    fun AllocDeviceMount(): DeviceMount?
    fun FreeExposedPort(@In value: ExposedPort)
    fun AllocExposedPort(): ExposedPort?
    fun FreeCreateContainerRequest(@In value: CreateContainerRequest)
    fun AllocCreateContainerRequest(): CreateContainerRequest?
    fun FreeCreateContainerReturn(@In value: CreateContainerReturn)
    fun AllocCreateContainerReturn(): CreateContainerReturn?
    fun FreeWaitForContainerToExitReturn(@In value: WaitForContainerToExitReturn)
    fun AllocWaitForContainerToExitReturn(): WaitForContainerToExitReturn?
    fun FreeContainerHealthcheckConfig(@In value: ContainerHealthcheckConfig)
    fun AllocContainerHealthcheckConfig(): ContainerHealthcheckConfig?
    fun FreeContainerConfig(@In value: ContainerConfig)
    fun AllocContainerConfig(): ContainerConfig?
    fun FreeContainerHealthLogEntry(@In value: ContainerHealthLogEntry)
    fun AllocContainerHealthLogEntry(): ContainerHealthLogEntry?
    fun FreeContainerHealthState(@In value: ContainerHealthState)
    fun AllocContainerHealthState(): ContainerHealthState?
    fun FreeContainerState(@In value: ContainerState)
    fun AllocContainerState(): ContainerState?
    fun FreeContainerLogConfig(@In value: ContainerLogConfig)
    fun AllocContainerLogConfig(): ContainerLogConfig?
    fun FreeContainerHostConfig(@In value: ContainerHostConfig)
    fun AllocContainerHostConfig(): ContainerHostConfig?
    fun FreeContainerInspectionResult(@In value: ContainerInspectionResult)
    fun AllocContainerInspectionResult(): ContainerInspectionResult?
    fun FreeInspectContainerReturn(@In value: InspectContainerReturn)
    fun AllocInspectContainerReturn(): InspectContainerReturn?
    fun FreeUploadDirectory(@In value: UploadDirectory)
    fun AllocUploadDirectory(): UploadDirectory?
    fun FreeUploadFile(@In value: UploadFile)
    fun AllocUploadFile(): UploadFile?
    fun FreeUploadToContainerRequest(@In value: UploadToContainerRequest)
    fun AllocUploadToContainerRequest(): UploadToContainerRequest?
    fun FreeStringToStringListPair(@In value: StringToStringListPair)
    fun AllocStringToStringListPair(): StringToStringListPair?
    fun FreeStreamEventsRequest(@In value: StreamEventsRequest)
    fun AllocStreamEventsRequest(): StreamEventsRequest?
    fun FreeActor(@In value: Actor)
    fun AllocActor(): Actor?
    fun FreeEvent(@In value: Event)
    fun AllocEvent(): Event?
    fun FreeCreateExecRequest(@In value: CreateExecRequest)
    fun AllocCreateExecRequest(): CreateExecRequest?
    fun FreeContainerExecReference(@In value: ContainerExecReference)
    fun AllocContainerExecReference(): ContainerExecReference?
    fun FreeCreateExecReturn(@In value: CreateExecReturn)
    fun AllocCreateExecReturn(): CreateExecReturn?
    fun FreeInspectExecResult(@In value: InspectExecResult)
    fun AllocInspectExecResult(): InspectExecResult?
    fun FreeInspectExecReturn(@In value: InspectExecReturn)
    fun AllocInspectExecReturn(): InspectExecReturn?
}
