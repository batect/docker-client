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

package main

/*
    #cgo windows CFLAGS: -DWINDOWS=1
    #include "types.h"
*/
import "C"

type DockerClientHandle C.DockerClientHandle
type Error *C.Error
type CreateClientReturn *C.CreateClientReturn
type PingResponse *C.PingResponse
type PingReturn *C.PingReturn
type DaemonVersionInformation *C.DaemonVersionInformation
type GetDaemonVersionInformationReturn *C.GetDaemonVersionInformationReturn
type VolumeReference *C.VolumeReference
type CreateVolumeReturn *C.CreateVolumeReturn
type ListAllVolumesReturn *C.ListAllVolumesReturn
type NetworkReference *C.NetworkReference
type CreateNetworkReturn *C.CreateNetworkReturn
type GetNetworkByNameOrIDReturn *C.GetNetworkByNameOrIDReturn
type ImageReference *C.ImageReference
type PullImageReturn *C.PullImageReturn
type GetImageReturn *C.GetImageReturn

func newError(
    Type string,
    Message string,
) Error {
    value := C.AllocError()
    value.Type = C.CString(Type)
    value.Message = C.CString(Message)

    return value
}

func newCreateClientReturn(
    Client DockerClientHandle,
    Error Error,
) CreateClientReturn {
    value := C.AllocCreateClientReturn()
    value.Client = C.uint64_t(Client)
    value.Error = Error

    return value
}

func newPingResponse(
    APIVersion string,
    OSType string,
    Experimental bool,
    BuilderVersion string,
) PingResponse {
    value := C.AllocPingResponse()
    value.APIVersion = C.CString(APIVersion)
    value.OSType = C.CString(OSType)
    value.Experimental = C.bool(Experimental)
    value.BuilderVersion = C.CString(BuilderVersion)

    return value
}

func newPingReturn(
    Response PingResponse,
    Error Error,
) PingReturn {
    value := C.AllocPingReturn()
    value.Response = Response
    value.Error = Error

    return value
}

func newDaemonVersionInformation(
    Version string,
    APIVersion string,
    MinAPIVersion string,
    GitCommit string,
    OperatingSystem string,
    Architecture string,
    Experimental bool,
) DaemonVersionInformation {
    value := C.AllocDaemonVersionInformation()
    value.Version = C.CString(Version)
    value.APIVersion = C.CString(APIVersion)
    value.MinAPIVersion = C.CString(MinAPIVersion)
    value.GitCommit = C.CString(GitCommit)
    value.OperatingSystem = C.CString(OperatingSystem)
    value.Architecture = C.CString(Architecture)
    value.Experimental = C.bool(Experimental)

    return value
}

func newGetDaemonVersionInformationReturn(
    Response DaemonVersionInformation,
    Error Error,
) GetDaemonVersionInformationReturn {
    value := C.AllocGetDaemonVersionInformationReturn()
    value.Response = Response
    value.Error = Error

    return value
}

func newVolumeReference(
    Name string,
) VolumeReference {
    value := C.AllocVolumeReference()
    value.Name = C.CString(Name)

    return value
}

func newCreateVolumeReturn(
    Response VolumeReference,
    Error Error,
) CreateVolumeReturn {
    value := C.AllocCreateVolumeReturn()
    value.Response = Response
    value.Error = Error

    return value
}

func newListAllVolumesReturn(
    Volumes []VolumeReference,
    Error Error,
) ListAllVolumesReturn {
    value := C.AllocListAllVolumesReturn()

    value.VolumesCount = C.uint64_t(len(Volumes))
    value.Volumes = C.CreateVolumeReferenceArray(value.VolumesCount)

    for i, v := range Volumes {
        C.SetVolumeReferenceArrayElement(value.Volumes, C.uint64_t(i), v)
    }

    value.Error = Error

    return value
}

func newNetworkReference(
    ID string,
) NetworkReference {
    value := C.AllocNetworkReference()
    value.ID = C.CString(ID)

    return value
}

func newCreateNetworkReturn(
    Response NetworkReference,
    Error Error,
) CreateNetworkReturn {
    value := C.AllocCreateNetworkReturn()
    value.Response = Response
    value.Error = Error

    return value
}

func newGetNetworkByNameOrIDReturn(
    Response NetworkReference,
    Error Error,
) GetNetworkByNameOrIDReturn {
    value := C.AllocGetNetworkByNameOrIDReturn()
    value.Response = Response
    value.Error = Error

    return value
}

func newImageReference(
    ID string,
) ImageReference {
    value := C.AllocImageReference()
    value.ID = C.CString(ID)

    return value
}

func newPullImageReturn(
    Response ImageReference,
    Error Error,
) PullImageReturn {
    value := C.AllocPullImageReturn()
    value.Response = Response
    value.Error = Error

    return value
}

func newGetImageReturn(
    Response ImageReference,
    Error Error,
) GetImageReturn {
    value := C.AllocGetImageReturn()
    value.Response = Response
    value.Error = Error

    return value
}

