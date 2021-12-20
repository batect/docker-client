/*
    Copyright 2017-2021 Charles Korn.

    Licensed under the Apache License, Version 2.0 (the "License");
    you may not use this file except in compliance with the License.
    You may obtain a copy of the License at

        http://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and
    limitations under the License.
*/

// AUTOGENERATED
// This file is autogenerated by the :client:generateJvmMethods Gradle task.
// Do not edit this file, as it will be regenerated automatically next time this project is built.

package batect.dockerclient.native

import jnr.ffi.Pointer
import jnr.ffi.annotations.In

@Suppress("FunctionName")
internal interface API {
    fun CreateClient(@In cfg: ClientConfiguration): CreateClientReturn?
    fun DisposeClient(@In clientHandle: Long): Error?
    fun SetClientProxySettingsForTest(@In clientHandle: Long)
    fun Ping(@In clientHandle: Long): PingReturn?
    fun GetDaemonVersionInformation(@In clientHandle: Long): GetDaemonVersionInformationReturn?
    fun DeleteImage(@In clientHandle: Long, @In ref: kotlin.String, @In force: Boolean): Error?
    fun GetImage(@In clientHandle: Long, @In ref: kotlin.String): GetImageReturn?
    fun ValidateImageTag(@In tag: kotlin.String): Error?
    fun BuildImage(@In clientHandle: Long, @In request: BuildImageRequest, @In outputStreamHandle: Long, @In reportContextUploadProgressEvents: Boolean, @In onProgressUpdate: BuildImageProgressCallback, @In callbackUserData: Pointer?): BuildImageReturn?
    fun PullImage(@In clientHandle: Long, @In ref: kotlin.String, @In onProgressUpdate: PullImageProgressCallback, @In callbackUserData: Pointer?): PullImageReturn?
    fun CreateOutputPipe(): CreateOutputPipeReturn?
    fun DisposeOutputPipe(@In handle: Long): Error?
    fun CreateNetwork(@In clientHandle: Long, @In name: kotlin.String, @In driver: kotlin.String): CreateNetworkReturn?
    fun DeleteNetwork(@In clientHandle: Long, @In id: kotlin.String): Error?
    fun GetNetworkByNameOrID(@In clientHandle: Long, @In searchFor: kotlin.String): GetNetworkByNameOrIDReturn?
    fun CreateVolume(@In clientHandle: Long, @In name: kotlin.String): CreateVolumeReturn?
    fun DeleteVolume(@In clientHandle: Long, @In name: kotlin.String): Error?
    fun ListAllVolumes(@In clientHandle: Long): ListAllVolumesReturn?
    fun FreeError(@In value: Error)
    fun AllocError(): Error?
    fun FreeTLSConfiguration(@In value: TLSConfiguration)
    fun AllocTLSConfiguration(): TLSConfiguration?
    fun FreeClientConfiguration(@In value: ClientConfiguration)
    fun AllocClientConfiguration(): ClientConfiguration?
    fun FreeCreateClientReturn(@In value: CreateClientReturn)
    fun AllocCreateClientReturn(): CreateClientReturn?
    fun FreeCreateOutputPipeReturn(@In value: CreateOutputPipeReturn)
    fun AllocCreateOutputPipeReturn(): CreateOutputPipeReturn?
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
}
