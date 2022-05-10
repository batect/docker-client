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

#include <stdint.h>
#include <stdbool.h>

#ifndef TYPES_H
#define TYPES_H

#ifdef WINDOWS
#define EXPORTED_FUNCTION extern __declspec(dllexport)
#else
#define EXPORTED_FUNCTION
#endif

typedef uint64_t DockerClientHandle;

typedef uint64_t OutputStreamHandle;

typedef uintptr_t FileDescriptor;

typedef uint64_t ContextHandle;

typedef struct {
    char* Type;
    char* Message;
} Error;

typedef struct {
    char* CAFilePath;
    char* CertFilePath;
    char* KeyFilePath;
    bool InsecureSkipVerify;
} TLSConfiguration;

typedef struct {
    bool UseConfigurationFromEnvironment;
    char* Host;
    TLSConfiguration* TLS;
    char* ConfigDirectoryPath;
} ClientConfiguration;

typedef struct {
    DockerClientHandle Client;
    Error* Error;
} CreateClientReturn;

typedef struct {
    OutputStreamHandle OutputStream;
    FileDescriptor ReadFileDescriptor;
    Error* Error;
} CreateOutputPipeReturn;

typedef struct {
    char* APIVersion;
    char* OSType;
    bool Experimental;
    char* BuilderVersion;
} PingResponse;

typedef struct {
    PingResponse* Response;
    Error* Error;
} PingReturn;

typedef struct {
    char* Version;
    char* APIVersion;
    char* MinAPIVersion;
    char* GitCommit;
    char* OperatingSystem;
    char* Architecture;
    bool Experimental;
} DaemonVersionInformation;

typedef struct {
    DaemonVersionInformation* Response;
    Error* Error;
} GetDaemonVersionInformationReturn;

typedef struct {
    char* Name;
} VolumeReference;

typedef struct {
    VolumeReference* Response;
    Error* Error;
} CreateVolumeReturn;

typedef struct {
    uint64_t VolumesCount;
    VolumeReference** Volumes;
    Error* Error;
} ListAllVolumesReturn;

typedef struct {
    char* ID;
} NetworkReference;

typedef struct {
    NetworkReference* Response;
    Error* Error;
} CreateNetworkReturn;

typedef struct {
    NetworkReference* Response;
    Error* Error;
} GetNetworkByNameOrIDReturn;

typedef struct {
    char* ID;
} ImageReference;

typedef struct {
    ImageReference* Response;
    Error* Error;
} PullImageReturn;

typedef struct {
    int64_t Current;
    int64_t Total;
} PullImageProgressDetail;

typedef struct {
    char* Message;
    PullImageProgressDetail* Detail;
    char* ID;
} PullImageProgressUpdate;

typedef bool (*PullImageProgressCallback) (void*, PullImageProgressUpdate*);

typedef struct {
    ImageReference* Response;
    Error* Error;
} GetImageReturn;

typedef struct {
    char* Key;
    char* Value;
} StringPair;

typedef struct {
    char* ContextDirectory;
    char* PathToDockerfile;
    uint64_t BuildArgsCount;
    StringPair** BuildArgs;
    uint64_t ImageTagsCount;
    char** ImageTags;
    bool AlwaysPullBaseImages;
    bool NoCache;
    char* TargetBuildStage;
    char* BuilderVersion;
} BuildImageRequest;

typedef struct {
    ImageReference* Response;
    Error* Error;
} BuildImageReturn;

typedef struct {
    int64_t StepNumber;
    int64_t BytesUploaded;
} BuildImageProgressUpdate_ImageBuildContextUploadProgress;

typedef struct {
    int64_t StepNumber;
    char* StepName;
} BuildImageProgressUpdate_StepStarting;

typedef struct {
    int64_t StepNumber;
    char* Output;
} BuildImageProgressUpdate_StepOutput;

typedef struct {
    int64_t StepNumber;
    PullImageProgressUpdate* PullProgress;
} BuildImageProgressUpdate_StepPullProgressUpdate;

typedef struct {
    int64_t StepNumber;
    int64_t DownloadedBytes;
    int64_t TotalBytes;
} BuildImageProgressUpdate_StepDownloadProgressUpdate;

typedef struct {
    int64_t StepNumber;
} BuildImageProgressUpdate_StepFinished;

typedef struct {
    char* Message;
} BuildImageProgressUpdate_BuildFailed;

typedef struct {
    BuildImageProgressUpdate_ImageBuildContextUploadProgress* ImageBuildContextUploadProgress;
    BuildImageProgressUpdate_StepStarting* StepStarting;
    BuildImageProgressUpdate_StepOutput* StepOutput;
    BuildImageProgressUpdate_StepPullProgressUpdate* StepPullProgressUpdate;
    BuildImageProgressUpdate_StepDownloadProgressUpdate* StepDownloadProgressUpdate;
    BuildImageProgressUpdate_StepFinished* StepFinished;
    BuildImageProgressUpdate_BuildFailed* BuildFailed;
} BuildImageProgressUpdate;

typedef bool (*BuildImageProgressCallback) (void*, BuildImageProgressUpdate*);

typedef struct {
    char* ID;
} ContainerReference;

typedef struct {
    char* LocalPath;
    char* ContainerPath;
    char* Permissions;
} DeviceMount;

typedef struct {
    int64_t LocalPort;
    int64_t ContainerPort;
    char* Protocol;
} ExposedPort;

typedef struct {
    char* ImageReference;
    uint64_t CommandCount;
    char** Command;
    uint64_t EntrypointCount;
    char** Entrypoint;
    char* WorkingDirectory;
    char* Hostname;
    uint64_t ExtraHostsCount;
    char** ExtraHosts;
    uint64_t EnvironmentVariablesCount;
    char** EnvironmentVariables;
    uint64_t BindMountsCount;
    char** BindMounts;
    uint64_t TmpfsMountsCount;
    StringPair** TmpfsMounts;
    uint64_t DeviceMountsCount;
    DeviceMount** DeviceMounts;
    uint64_t ExposedPortsCount;
    ExposedPort** ExposedPorts;
    char* User;
} CreateContainerRequest;

typedef struct {
    ContainerReference* Response;
    Error* Error;
} CreateContainerReturn;

typedef struct {
    int64_t ExitCode;
    Error* Error;
} WaitForContainerToExitReturn;

typedef bool (*ReadyCallback) (void*);

EXPORTED_FUNCTION Error* AllocError();
EXPORTED_FUNCTION void FreeError(Error* value);
EXPORTED_FUNCTION TLSConfiguration* AllocTLSConfiguration();
EXPORTED_FUNCTION void FreeTLSConfiguration(TLSConfiguration* value);
EXPORTED_FUNCTION ClientConfiguration* AllocClientConfiguration();
EXPORTED_FUNCTION void FreeClientConfiguration(ClientConfiguration* value);
EXPORTED_FUNCTION CreateClientReturn* AllocCreateClientReturn();
EXPORTED_FUNCTION void FreeCreateClientReturn(CreateClientReturn* value);
EXPORTED_FUNCTION CreateOutputPipeReturn* AllocCreateOutputPipeReturn();
EXPORTED_FUNCTION void FreeCreateOutputPipeReturn(CreateOutputPipeReturn* value);
EXPORTED_FUNCTION PingResponse* AllocPingResponse();
EXPORTED_FUNCTION void FreePingResponse(PingResponse* value);
EXPORTED_FUNCTION PingReturn* AllocPingReturn();
EXPORTED_FUNCTION void FreePingReturn(PingReturn* value);
EXPORTED_FUNCTION DaemonVersionInformation* AllocDaemonVersionInformation();
EXPORTED_FUNCTION void FreeDaemonVersionInformation(DaemonVersionInformation* value);
EXPORTED_FUNCTION GetDaemonVersionInformationReturn* AllocGetDaemonVersionInformationReturn();
EXPORTED_FUNCTION void FreeGetDaemonVersionInformationReturn(GetDaemonVersionInformationReturn* value);
EXPORTED_FUNCTION VolumeReference* AllocVolumeReference();
EXPORTED_FUNCTION void FreeVolumeReference(VolumeReference* value);
EXPORTED_FUNCTION CreateVolumeReturn* AllocCreateVolumeReturn();
EXPORTED_FUNCTION void FreeCreateVolumeReturn(CreateVolumeReturn* value);
EXPORTED_FUNCTION ListAllVolumesReturn* AllocListAllVolumesReturn();
EXPORTED_FUNCTION void FreeListAllVolumesReturn(ListAllVolumesReturn* value);
EXPORTED_FUNCTION NetworkReference* AllocNetworkReference();
EXPORTED_FUNCTION void FreeNetworkReference(NetworkReference* value);
EXPORTED_FUNCTION CreateNetworkReturn* AllocCreateNetworkReturn();
EXPORTED_FUNCTION void FreeCreateNetworkReturn(CreateNetworkReturn* value);
EXPORTED_FUNCTION GetNetworkByNameOrIDReturn* AllocGetNetworkByNameOrIDReturn();
EXPORTED_FUNCTION void FreeGetNetworkByNameOrIDReturn(GetNetworkByNameOrIDReturn* value);
EXPORTED_FUNCTION ImageReference* AllocImageReference();
EXPORTED_FUNCTION void FreeImageReference(ImageReference* value);
EXPORTED_FUNCTION PullImageReturn* AllocPullImageReturn();
EXPORTED_FUNCTION void FreePullImageReturn(PullImageReturn* value);
EXPORTED_FUNCTION PullImageProgressDetail* AllocPullImageProgressDetail();
EXPORTED_FUNCTION void FreePullImageProgressDetail(PullImageProgressDetail* value);
EXPORTED_FUNCTION PullImageProgressUpdate* AllocPullImageProgressUpdate();
EXPORTED_FUNCTION void FreePullImageProgressUpdate(PullImageProgressUpdate* value);
EXPORTED_FUNCTION bool InvokePullImageProgressCallback(PullImageProgressCallback method, void* userData, PullImageProgressUpdate* progress);
EXPORTED_FUNCTION GetImageReturn* AllocGetImageReturn();
EXPORTED_FUNCTION void FreeGetImageReturn(GetImageReturn* value);
EXPORTED_FUNCTION StringPair* AllocStringPair();
EXPORTED_FUNCTION void FreeStringPair(StringPair* value);
EXPORTED_FUNCTION BuildImageRequest* AllocBuildImageRequest();
EXPORTED_FUNCTION void FreeBuildImageRequest(BuildImageRequest* value);
EXPORTED_FUNCTION BuildImageReturn* AllocBuildImageReturn();
EXPORTED_FUNCTION void FreeBuildImageReturn(BuildImageReturn* value);
EXPORTED_FUNCTION BuildImageProgressUpdate_ImageBuildContextUploadProgress* AllocBuildImageProgressUpdate_ImageBuildContextUploadProgress();
EXPORTED_FUNCTION void FreeBuildImageProgressUpdate_ImageBuildContextUploadProgress(BuildImageProgressUpdate_ImageBuildContextUploadProgress* value);
EXPORTED_FUNCTION BuildImageProgressUpdate_StepStarting* AllocBuildImageProgressUpdate_StepStarting();
EXPORTED_FUNCTION void FreeBuildImageProgressUpdate_StepStarting(BuildImageProgressUpdate_StepStarting* value);
EXPORTED_FUNCTION BuildImageProgressUpdate_StepOutput* AllocBuildImageProgressUpdate_StepOutput();
EXPORTED_FUNCTION void FreeBuildImageProgressUpdate_StepOutput(BuildImageProgressUpdate_StepOutput* value);
EXPORTED_FUNCTION BuildImageProgressUpdate_StepPullProgressUpdate* AllocBuildImageProgressUpdate_StepPullProgressUpdate();
EXPORTED_FUNCTION void FreeBuildImageProgressUpdate_StepPullProgressUpdate(BuildImageProgressUpdate_StepPullProgressUpdate* value);
EXPORTED_FUNCTION BuildImageProgressUpdate_StepDownloadProgressUpdate* AllocBuildImageProgressUpdate_StepDownloadProgressUpdate();
EXPORTED_FUNCTION void FreeBuildImageProgressUpdate_StepDownloadProgressUpdate(BuildImageProgressUpdate_StepDownloadProgressUpdate* value);
EXPORTED_FUNCTION BuildImageProgressUpdate_StepFinished* AllocBuildImageProgressUpdate_StepFinished();
EXPORTED_FUNCTION void FreeBuildImageProgressUpdate_StepFinished(BuildImageProgressUpdate_StepFinished* value);
EXPORTED_FUNCTION BuildImageProgressUpdate_BuildFailed* AllocBuildImageProgressUpdate_BuildFailed();
EXPORTED_FUNCTION void FreeBuildImageProgressUpdate_BuildFailed(BuildImageProgressUpdate_BuildFailed* value);
EXPORTED_FUNCTION BuildImageProgressUpdate* AllocBuildImageProgressUpdate();
EXPORTED_FUNCTION void FreeBuildImageProgressUpdate(BuildImageProgressUpdate* value);
EXPORTED_FUNCTION bool InvokeBuildImageProgressCallback(BuildImageProgressCallback method, void* userData, BuildImageProgressUpdate* progress);
EXPORTED_FUNCTION ContainerReference* AllocContainerReference();
EXPORTED_FUNCTION void FreeContainerReference(ContainerReference* value);
EXPORTED_FUNCTION DeviceMount* AllocDeviceMount();
EXPORTED_FUNCTION void FreeDeviceMount(DeviceMount* value);
EXPORTED_FUNCTION ExposedPort* AllocExposedPort();
EXPORTED_FUNCTION void FreeExposedPort(ExposedPort* value);
EXPORTED_FUNCTION CreateContainerRequest* AllocCreateContainerRequest();
EXPORTED_FUNCTION void FreeCreateContainerRequest(CreateContainerRequest* value);
EXPORTED_FUNCTION CreateContainerReturn* AllocCreateContainerReturn();
EXPORTED_FUNCTION void FreeCreateContainerReturn(CreateContainerReturn* value);
EXPORTED_FUNCTION WaitForContainerToExitReturn* AllocWaitForContainerToExitReturn();
EXPORTED_FUNCTION void FreeWaitForContainerToExitReturn(WaitForContainerToExitReturn* value);
EXPORTED_FUNCTION bool InvokeReadyCallback(ReadyCallback method, void* userData);
EXPORTED_FUNCTION VolumeReference** CreateVolumeReferenceArray(uint64_t size);
EXPORTED_FUNCTION void SetVolumeReferenceArrayElement(VolumeReference** array, uint64_t index, VolumeReference* value);
EXPORTED_FUNCTION VolumeReference* GetVolumeReferenceArrayElement(VolumeReference** array, uint64_t index);
EXPORTED_FUNCTION StringPair** CreateStringPairArray(uint64_t size);
EXPORTED_FUNCTION void SetStringPairArrayElement(StringPair** array, uint64_t index, StringPair* value);
EXPORTED_FUNCTION StringPair* GetStringPairArrayElement(StringPair** array, uint64_t index);
EXPORTED_FUNCTION char** CreatestringArray(uint64_t size);
EXPORTED_FUNCTION void SetstringArrayElement(char** array, uint64_t index, char* value);
EXPORTED_FUNCTION char* GetstringArrayElement(char** array, uint64_t index);
EXPORTED_FUNCTION DeviceMount** CreateDeviceMountArray(uint64_t size);
EXPORTED_FUNCTION void SetDeviceMountArrayElement(DeviceMount** array, uint64_t index, DeviceMount* value);
EXPORTED_FUNCTION DeviceMount* GetDeviceMountArrayElement(DeviceMount** array, uint64_t index);
EXPORTED_FUNCTION ExposedPort** CreateExposedPortArray(uint64_t size);
EXPORTED_FUNCTION void SetExposedPortArrayElement(ExposedPort** array, uint64_t index, ExposedPort* value);
EXPORTED_FUNCTION ExposedPort* GetExposedPortArrayElement(ExposedPort** array, uint64_t index);
#endif
