// Copyright 2017-2021 Charles Korn.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
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

typedef struct {
    char* Type;
    char* Message;
} Error;

typedef struct {
    DockerClientHandle Client;
    Error* Error;
} CreateClientReturn;

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

typedef void (*PullImageProgressCallback) (void*, PullImageProgressUpdate*);

typedef struct {
    ImageReference* Response;
    Error* Error;
} GetImageReturn;

EXPORTED_FUNCTION Error* AllocError();
EXPORTED_FUNCTION void FreeError(Error* value);
EXPORTED_FUNCTION CreateClientReturn* AllocCreateClientReturn();
EXPORTED_FUNCTION void FreeCreateClientReturn(CreateClientReturn* value);
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
EXPORTED_FUNCTION void InvokePullImageProgressCallback(PullImageProgressCallback method, void* userData, PullImageProgressUpdate* progress);
EXPORTED_FUNCTION GetImageReturn* AllocGetImageReturn();
EXPORTED_FUNCTION void FreeGetImageReturn(GetImageReturn* value);
EXPORTED_FUNCTION VolumeReference** CreateVolumeReferenceArray(uint64_t size);
EXPORTED_FUNCTION void SetVolumeReferenceArrayElement(VolumeReference** array, uint64_t index, VolumeReference* value);
#endif
