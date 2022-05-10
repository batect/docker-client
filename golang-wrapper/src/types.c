// Copyright 2017-2022 Charles Korn.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     https://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

// AUTOGENERATED
// This file is autogenerated by the :golang-wrapper:generateTypes Gradle task.
// Do not edit this file, as it will be regenerated automatically next time this project is built.

#include <stdlib.h>
#include "types.h"

Error* AllocError() {
    Error* value = malloc(sizeof(Error));
    value->Type = NULL;
    value->Message = NULL;

    return value;
}

void FreeError(Error* value) {
    if (value == NULL) {
        return;
    }

    free(value->Type);
    free(value->Message);
    free(value);
}

TLSConfiguration* AllocTLSConfiguration() {
    TLSConfiguration* value = malloc(sizeof(TLSConfiguration));
    value->CAFilePath = NULL;
    value->CertFilePath = NULL;
    value->KeyFilePath = NULL;

    return value;
}

void FreeTLSConfiguration(TLSConfiguration* value) {
    if (value == NULL) {
        return;
    }

    free(value->CAFilePath);
    free(value->CertFilePath);
    free(value->KeyFilePath);
    free(value);
}

ClientConfiguration* AllocClientConfiguration() {
    ClientConfiguration* value = malloc(sizeof(ClientConfiguration));
    value->Host = NULL;
    value->TLS = NULL;
    value->ConfigDirectoryPath = NULL;

    return value;
}

void FreeClientConfiguration(ClientConfiguration* value) {
    if (value == NULL) {
        return;
    }

    free(value->Host);
    FreeTLSConfiguration(value->TLS);
    free(value->ConfigDirectoryPath);
    free(value);
}

CreateClientReturn* AllocCreateClientReturn() {
    CreateClientReturn* value = malloc(sizeof(CreateClientReturn));
    value->Error = NULL;

    return value;
}

void FreeCreateClientReturn(CreateClientReturn* value) {
    if (value == NULL) {
        return;
    }

    FreeError(value->Error);
    free(value);
}

CreateOutputPipeReturn* AllocCreateOutputPipeReturn() {
    CreateOutputPipeReturn* value = malloc(sizeof(CreateOutputPipeReturn));
    value->Error = NULL;

    return value;
}

void FreeCreateOutputPipeReturn(CreateOutputPipeReturn* value) {
    if (value == NULL) {
        return;
    }

    FreeError(value->Error);
    free(value);
}

PingResponse* AllocPingResponse() {
    PingResponse* value = malloc(sizeof(PingResponse));
    value->APIVersion = NULL;
    value->OSType = NULL;
    value->BuilderVersion = NULL;

    return value;
}

void FreePingResponse(PingResponse* value) {
    if (value == NULL) {
        return;
    }

    free(value->APIVersion);
    free(value->OSType);
    free(value->BuilderVersion);
    free(value);
}

PingReturn* AllocPingReturn() {
    PingReturn* value = malloc(sizeof(PingReturn));
    value->Response = NULL;
    value->Error = NULL;

    return value;
}

void FreePingReturn(PingReturn* value) {
    if (value == NULL) {
        return;
    }

    FreePingResponse(value->Response);
    FreeError(value->Error);
    free(value);
}

DaemonVersionInformation* AllocDaemonVersionInformation() {
    DaemonVersionInformation* value = malloc(sizeof(DaemonVersionInformation));
    value->Version = NULL;
    value->APIVersion = NULL;
    value->MinAPIVersion = NULL;
    value->GitCommit = NULL;
    value->OperatingSystem = NULL;
    value->Architecture = NULL;

    return value;
}

void FreeDaemonVersionInformation(DaemonVersionInformation* value) {
    if (value == NULL) {
        return;
    }

    free(value->Version);
    free(value->APIVersion);
    free(value->MinAPIVersion);
    free(value->GitCommit);
    free(value->OperatingSystem);
    free(value->Architecture);
    free(value);
}

GetDaemonVersionInformationReturn* AllocGetDaemonVersionInformationReturn() {
    GetDaemonVersionInformationReturn* value = malloc(sizeof(GetDaemonVersionInformationReturn));
    value->Response = NULL;
    value->Error = NULL;

    return value;
}

void FreeGetDaemonVersionInformationReturn(GetDaemonVersionInformationReturn* value) {
    if (value == NULL) {
        return;
    }

    FreeDaemonVersionInformation(value->Response);
    FreeError(value->Error);
    free(value);
}

VolumeReference* AllocVolumeReference() {
    VolumeReference* value = malloc(sizeof(VolumeReference));
    value->Name = NULL;

    return value;
}

void FreeVolumeReference(VolumeReference* value) {
    if (value == NULL) {
        return;
    }

    free(value->Name);
    free(value);
}

CreateVolumeReturn* AllocCreateVolumeReturn() {
    CreateVolumeReturn* value = malloc(sizeof(CreateVolumeReturn));
    value->Response = NULL;
    value->Error = NULL;

    return value;
}

void FreeCreateVolumeReturn(CreateVolumeReturn* value) {
    if (value == NULL) {
        return;
    }

    FreeVolumeReference(value->Response);
    FreeError(value->Error);
    free(value);
}

ListAllVolumesReturn* AllocListAllVolumesReturn() {
    ListAllVolumesReturn* value = malloc(sizeof(ListAllVolumesReturn));
    value->Volumes = NULL;
    value->Error = NULL;
    value->VolumesCount = 0;

    return value;
}

void FreeListAllVolumesReturn(ListAllVolumesReturn* value) {
    if (value == NULL) {
        return;
    }

    for (uint64_t i = 0; i < value->VolumesCount; i++) {
        FreeVolumeReference(value->Volumes[i]);
    }

    free(value->Volumes);
    FreeError(value->Error);
    free(value);
}

NetworkReference* AllocNetworkReference() {
    NetworkReference* value = malloc(sizeof(NetworkReference));
    value->ID = NULL;

    return value;
}

void FreeNetworkReference(NetworkReference* value) {
    if (value == NULL) {
        return;
    }

    free(value->ID);
    free(value);
}

CreateNetworkReturn* AllocCreateNetworkReturn() {
    CreateNetworkReturn* value = malloc(sizeof(CreateNetworkReturn));
    value->Response = NULL;
    value->Error = NULL;

    return value;
}

void FreeCreateNetworkReturn(CreateNetworkReturn* value) {
    if (value == NULL) {
        return;
    }

    FreeNetworkReference(value->Response);
    FreeError(value->Error);
    free(value);
}

GetNetworkByNameOrIDReturn* AllocGetNetworkByNameOrIDReturn() {
    GetNetworkByNameOrIDReturn* value = malloc(sizeof(GetNetworkByNameOrIDReturn));
    value->Response = NULL;
    value->Error = NULL;

    return value;
}

void FreeGetNetworkByNameOrIDReturn(GetNetworkByNameOrIDReturn* value) {
    if (value == NULL) {
        return;
    }

    FreeNetworkReference(value->Response);
    FreeError(value->Error);
    free(value);
}

ImageReference* AllocImageReference() {
    ImageReference* value = malloc(sizeof(ImageReference));
    value->ID = NULL;

    return value;
}

void FreeImageReference(ImageReference* value) {
    if (value == NULL) {
        return;
    }

    free(value->ID);
    free(value);
}

PullImageReturn* AllocPullImageReturn() {
    PullImageReturn* value = malloc(sizeof(PullImageReturn));
    value->Response = NULL;
    value->Error = NULL;

    return value;
}

void FreePullImageReturn(PullImageReturn* value) {
    if (value == NULL) {
        return;
    }

    FreeImageReference(value->Response);
    FreeError(value->Error);
    free(value);
}

PullImageProgressDetail* AllocPullImageProgressDetail() {
    PullImageProgressDetail* value = malloc(sizeof(PullImageProgressDetail));

    return value;
}

void FreePullImageProgressDetail(PullImageProgressDetail* value) {
    if (value == NULL) {
        return;
    }

    free(value);
}

PullImageProgressUpdate* AllocPullImageProgressUpdate() {
    PullImageProgressUpdate* value = malloc(sizeof(PullImageProgressUpdate));
    value->Message = NULL;
    value->Detail = NULL;
    value->ID = NULL;

    return value;
}

void FreePullImageProgressUpdate(PullImageProgressUpdate* value) {
    if (value == NULL) {
        return;
    }

    free(value->Message);
    FreePullImageProgressDetail(value->Detail);
    free(value->ID);
    free(value);
}

bool InvokePullImageProgressCallback(PullImageProgressCallback method, void* userData, PullImageProgressUpdate* progress) {
    return method(userData, progress);
}

GetImageReturn* AllocGetImageReturn() {
    GetImageReturn* value = malloc(sizeof(GetImageReturn));
    value->Response = NULL;
    value->Error = NULL;

    return value;
}

void FreeGetImageReturn(GetImageReturn* value) {
    if (value == NULL) {
        return;
    }

    FreeImageReference(value->Response);
    FreeError(value->Error);
    free(value);
}

StringPair* AllocStringPair() {
    StringPair* value = malloc(sizeof(StringPair));
    value->Key = NULL;
    value->Value = NULL;

    return value;
}

void FreeStringPair(StringPair* value) {
    if (value == NULL) {
        return;
    }

    free(value->Key);
    free(value->Value);
    free(value);
}

BuildImageRequest* AllocBuildImageRequest() {
    BuildImageRequest* value = malloc(sizeof(BuildImageRequest));
    value->ContextDirectory = NULL;
    value->PathToDockerfile = NULL;
    value->BuildArgs = NULL;
    value->ImageTags = NULL;
    value->TargetBuildStage = NULL;
    value->BuilderVersion = NULL;
    value->BuildArgsCount = 0;
    value->ImageTagsCount = 0;

    return value;
}

void FreeBuildImageRequest(BuildImageRequest* value) {
    if (value == NULL) {
        return;
    }

    free(value->ContextDirectory);
    free(value->PathToDockerfile);
    for (uint64_t i = 0; i < value->BuildArgsCount; i++) {
        FreeStringPair(value->BuildArgs[i]);
    }

    free(value->BuildArgs);
    for (uint64_t i = 0; i < value->ImageTagsCount; i++) {
        free(value->ImageTags[i]);
    }

    free(value->ImageTags);
    free(value->TargetBuildStage);
    free(value->BuilderVersion);
    free(value);
}

BuildImageReturn* AllocBuildImageReturn() {
    BuildImageReturn* value = malloc(sizeof(BuildImageReturn));
    value->Response = NULL;
    value->Error = NULL;

    return value;
}

void FreeBuildImageReturn(BuildImageReturn* value) {
    if (value == NULL) {
        return;
    }

    FreeImageReference(value->Response);
    FreeError(value->Error);
    free(value);
}

BuildImageProgressUpdate_ImageBuildContextUploadProgress* AllocBuildImageProgressUpdate_ImageBuildContextUploadProgress() {
    BuildImageProgressUpdate_ImageBuildContextUploadProgress* value = malloc(sizeof(BuildImageProgressUpdate_ImageBuildContextUploadProgress));

    return value;
}

void FreeBuildImageProgressUpdate_ImageBuildContextUploadProgress(BuildImageProgressUpdate_ImageBuildContextUploadProgress* value) {
    if (value == NULL) {
        return;
    }

    free(value);
}

BuildImageProgressUpdate_StepStarting* AllocBuildImageProgressUpdate_StepStarting() {
    BuildImageProgressUpdate_StepStarting* value = malloc(sizeof(BuildImageProgressUpdate_StepStarting));
    value->StepName = NULL;

    return value;
}

void FreeBuildImageProgressUpdate_StepStarting(BuildImageProgressUpdate_StepStarting* value) {
    if (value == NULL) {
        return;
    }

    free(value->StepName);
    free(value);
}

BuildImageProgressUpdate_StepOutput* AllocBuildImageProgressUpdate_StepOutput() {
    BuildImageProgressUpdate_StepOutput* value = malloc(sizeof(BuildImageProgressUpdate_StepOutput));
    value->Output = NULL;

    return value;
}

void FreeBuildImageProgressUpdate_StepOutput(BuildImageProgressUpdate_StepOutput* value) {
    if (value == NULL) {
        return;
    }

    free(value->Output);
    free(value);
}

BuildImageProgressUpdate_StepPullProgressUpdate* AllocBuildImageProgressUpdate_StepPullProgressUpdate() {
    BuildImageProgressUpdate_StepPullProgressUpdate* value = malloc(sizeof(BuildImageProgressUpdate_StepPullProgressUpdate));
    value->PullProgress = NULL;

    return value;
}

void FreeBuildImageProgressUpdate_StepPullProgressUpdate(BuildImageProgressUpdate_StepPullProgressUpdate* value) {
    if (value == NULL) {
        return;
    }

    FreePullImageProgressUpdate(value->PullProgress);
    free(value);
}

BuildImageProgressUpdate_StepDownloadProgressUpdate* AllocBuildImageProgressUpdate_StepDownloadProgressUpdate() {
    BuildImageProgressUpdate_StepDownloadProgressUpdate* value = malloc(sizeof(BuildImageProgressUpdate_StepDownloadProgressUpdate));

    return value;
}

void FreeBuildImageProgressUpdate_StepDownloadProgressUpdate(BuildImageProgressUpdate_StepDownloadProgressUpdate* value) {
    if (value == NULL) {
        return;
    }

    free(value);
}

BuildImageProgressUpdate_StepFinished* AllocBuildImageProgressUpdate_StepFinished() {
    BuildImageProgressUpdate_StepFinished* value = malloc(sizeof(BuildImageProgressUpdate_StepFinished));

    return value;
}

void FreeBuildImageProgressUpdate_StepFinished(BuildImageProgressUpdate_StepFinished* value) {
    if (value == NULL) {
        return;
    }

    free(value);
}

BuildImageProgressUpdate_BuildFailed* AllocBuildImageProgressUpdate_BuildFailed() {
    BuildImageProgressUpdate_BuildFailed* value = malloc(sizeof(BuildImageProgressUpdate_BuildFailed));
    value->Message = NULL;

    return value;
}

void FreeBuildImageProgressUpdate_BuildFailed(BuildImageProgressUpdate_BuildFailed* value) {
    if (value == NULL) {
        return;
    }

    free(value->Message);
    free(value);
}

BuildImageProgressUpdate* AllocBuildImageProgressUpdate() {
    BuildImageProgressUpdate* value = malloc(sizeof(BuildImageProgressUpdate));
    value->ImageBuildContextUploadProgress = NULL;
    value->StepStarting = NULL;
    value->StepOutput = NULL;
    value->StepPullProgressUpdate = NULL;
    value->StepDownloadProgressUpdate = NULL;
    value->StepFinished = NULL;
    value->BuildFailed = NULL;

    return value;
}

void FreeBuildImageProgressUpdate(BuildImageProgressUpdate* value) {
    if (value == NULL) {
        return;
    }

    FreeBuildImageProgressUpdate_ImageBuildContextUploadProgress(value->ImageBuildContextUploadProgress);
    FreeBuildImageProgressUpdate_StepStarting(value->StepStarting);
    FreeBuildImageProgressUpdate_StepOutput(value->StepOutput);
    FreeBuildImageProgressUpdate_StepPullProgressUpdate(value->StepPullProgressUpdate);
    FreeBuildImageProgressUpdate_StepDownloadProgressUpdate(value->StepDownloadProgressUpdate);
    FreeBuildImageProgressUpdate_StepFinished(value->StepFinished);
    FreeBuildImageProgressUpdate_BuildFailed(value->BuildFailed);
    free(value);
}

bool InvokeBuildImageProgressCallback(BuildImageProgressCallback method, void* userData, BuildImageProgressUpdate* progress) {
    return method(userData, progress);
}

ContainerReference* AllocContainerReference() {
    ContainerReference* value = malloc(sizeof(ContainerReference));
    value->ID = NULL;

    return value;
}

void FreeContainerReference(ContainerReference* value) {
    if (value == NULL) {
        return;
    }

    free(value->ID);
    free(value);
}

CreateContainerRequest* AllocCreateContainerRequest() {
    CreateContainerRequest* value = malloc(sizeof(CreateContainerRequest));
    value->ImageReference = NULL;
    value->Command = NULL;
    value->Hostname = NULL;
    value->ExtraHosts = NULL;
    value->CommandCount = 0;
    value->ExtraHostsCount = 0;

    return value;
}

void FreeCreateContainerRequest(CreateContainerRequest* value) {
    if (value == NULL) {
        return;
    }

    free(value->ImageReference);
    for (uint64_t i = 0; i < value->CommandCount; i++) {
        free(value->Command[i]);
    }

    free(value->Command);
    free(value->Hostname);
    for (uint64_t i = 0; i < value->ExtraHostsCount; i++) {
        free(value->ExtraHosts[i]);
    }

    free(value->ExtraHosts);
    free(value);
}

CreateContainerReturn* AllocCreateContainerReturn() {
    CreateContainerReturn* value = malloc(sizeof(CreateContainerReturn));
    value->Response = NULL;
    value->Error = NULL;

    return value;
}

void FreeCreateContainerReturn(CreateContainerReturn* value) {
    if (value == NULL) {
        return;
    }

    FreeContainerReference(value->Response);
    FreeError(value->Error);
    free(value);
}

WaitForContainerToExitReturn* AllocWaitForContainerToExitReturn() {
    WaitForContainerToExitReturn* value = malloc(sizeof(WaitForContainerToExitReturn));
    value->Error = NULL;

    return value;
}

void FreeWaitForContainerToExitReturn(WaitForContainerToExitReturn* value) {
    if (value == NULL) {
        return;
    }

    FreeError(value->Error);
    free(value);
}

bool InvokeReadyCallback(ReadyCallback method, void* userData) {
    return method(userData);
}

VolumeReference** CreateVolumeReferenceArray(uint64_t size) {
    return malloc(size * sizeof(VolumeReference*));
}

void SetVolumeReferenceArrayElement(VolumeReference** array, uint64_t index, VolumeReference* value) {
    array[index] = value;
}

VolumeReference* GetVolumeReferenceArrayElement(VolumeReference** array, uint64_t index) {
    return array[index];
}

StringPair** CreateStringPairArray(uint64_t size) {
    return malloc(size * sizeof(StringPair*));
}

void SetStringPairArrayElement(StringPair** array, uint64_t index, StringPair* value) {
    array[index] = value;
}

StringPair* GetStringPairArrayElement(StringPair** array, uint64_t index) {
    return array[index];
}

char** CreatestringArray(uint64_t size) {
    return malloc(size * sizeof(char*));
}

void SetstringArrayElement(char** array, uint64_t index, char* value) {
    array[index] = value;
}

char* GetstringArrayElement(char** array, uint64_t index) {
    return array[index];
}
