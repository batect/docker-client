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

package main

/*
    #cgo windows CFLAGS: -DWINDOWS=1
    #include "types.h"
*/
import "C"
import "unsafe"

type DockerClientHandle C.DockerClientHandle
type OutputStreamHandle C.OutputStreamHandle
type FileDescriptor C.FileDescriptor
type ContextHandle C.ContextHandle
type Error *C.Error
type TLSConfiguration *C.TLSConfiguration
type ClientConfiguration *C.ClientConfiguration
type CreateClientReturn *C.CreateClientReturn
type CreateOutputPipeReturn *C.CreateOutputPipeReturn
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
type PullImageProgressDetail *C.PullImageProgressDetail
type PullImageProgressUpdate *C.PullImageProgressUpdate
type PullImageProgressCallback C.PullImageProgressCallback
type GetImageReturn *C.GetImageReturn
type StringPair *C.StringPair
type BuildImageRequest *C.BuildImageRequest
type BuildImageReturn *C.BuildImageReturn
type BuildImageProgressUpdate_ImageBuildContextUploadProgress *C.BuildImageProgressUpdate_ImageBuildContextUploadProgress
type BuildImageProgressUpdate_StepStarting *C.BuildImageProgressUpdate_StepStarting
type BuildImageProgressUpdate_StepOutput *C.BuildImageProgressUpdate_StepOutput
type BuildImageProgressUpdate_StepPullProgressUpdate *C.BuildImageProgressUpdate_StepPullProgressUpdate
type BuildImageProgressUpdate_StepDownloadProgressUpdate *C.BuildImageProgressUpdate_StepDownloadProgressUpdate
type BuildImageProgressUpdate_StepFinished *C.BuildImageProgressUpdate_StepFinished
type BuildImageProgressUpdate_BuildFailed *C.BuildImageProgressUpdate_BuildFailed
type BuildImageProgressUpdate *C.BuildImageProgressUpdate
type BuildImageProgressCallback C.BuildImageProgressCallback
type ContainerReference *C.ContainerReference
type DeviceMount *C.DeviceMount
type ExposedPort *C.ExposedPort
type CreateContainerRequest *C.CreateContainerRequest
type CreateContainerReturn *C.CreateContainerReturn
type WaitForContainerToExitReturn *C.WaitForContainerToExitReturn
type ReadyCallback C.ReadyCallback
type ContainerHealthcheckConfig *C.ContainerHealthcheckConfig
type ContainerConfig *C.ContainerConfig
type ContainerHealthLogEntry *C.ContainerHealthLogEntry
type ContainerHealthState *C.ContainerHealthState
type ContainerState *C.ContainerState
type ContainerLogConfig *C.ContainerLogConfig
type ContainerHostConfig *C.ContainerHostConfig
type ContainerInspectionResult *C.ContainerInspectionResult
type InspectContainerReturn *C.InspectContainerReturn

func newError(
    Type string,
    Message string,
) Error {
    value := C.AllocError()
    value.Type = C.CString(Type)
    value.Message = C.CString(Message)

    return value
}

func newTLSConfiguration(
    CAFilePath string,
    CertFilePath string,
    KeyFilePath string,
    InsecureSkipVerify bool,
) TLSConfiguration {
    value := C.AllocTLSConfiguration()
    value.CAFilePath = C.CString(CAFilePath)
    value.CertFilePath = C.CString(CertFilePath)
    value.KeyFilePath = C.CString(KeyFilePath)
    value.InsecureSkipVerify = C.bool(InsecureSkipVerify)

    return value
}

func newClientConfiguration(
    UseConfigurationFromEnvironment bool,
    Host string,
    TLS TLSConfiguration,
    ConfigDirectoryPath string,
) ClientConfiguration {
    value := C.AllocClientConfiguration()
    value.UseConfigurationFromEnvironment = C.bool(UseConfigurationFromEnvironment)
    value.Host = C.CString(Host)
    value.TLS = TLS
    value.ConfigDirectoryPath = C.CString(ConfigDirectoryPath)

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

func newCreateOutputPipeReturn(
    OutputStream OutputStreamHandle,
    ReadFileDescriptor FileDescriptor,
    Error Error,
) CreateOutputPipeReturn {
    value := C.AllocCreateOutputPipeReturn()
    value.OutputStream = C.uint64_t(OutputStream)
    value.ReadFileDescriptor = C.uintptr_t(ReadFileDescriptor)
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

func newPullImageProgressDetail(
    Current int64,
    Total int64,
) PullImageProgressDetail {
    value := C.AllocPullImageProgressDetail()
    value.Current = C.int64_t(Current)
    value.Total = C.int64_t(Total)

    return value
}

func newPullImageProgressUpdate(
    Message string,
    Detail PullImageProgressDetail,
    ID string,
) PullImageProgressUpdate {
    value := C.AllocPullImageProgressUpdate()
    value.Message = C.CString(Message)
    value.Detail = Detail
    value.ID = C.CString(ID)

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

func newStringPair(
    Key string,
    Value string,
) StringPair {
    value := C.AllocStringPair()
    value.Key = C.CString(Key)
    value.Value = C.CString(Value)

    return value
}

func newBuildImageRequest(
    ContextDirectory string,
    PathToDockerfile string,
    BuildArgs []StringPair,
    ImageTags []string,
    AlwaysPullBaseImages bool,
    NoCache bool,
    TargetBuildStage string,
    BuilderVersion string,
) BuildImageRequest {
    value := C.AllocBuildImageRequest()
    value.ContextDirectory = C.CString(ContextDirectory)
    value.PathToDockerfile = C.CString(PathToDockerfile)

    value.BuildArgsCount = C.uint64_t(len(BuildArgs))
    value.BuildArgs = C.CreateStringPairArray(value.BuildArgsCount)

    for i, v := range BuildArgs {
        C.SetStringPairArrayElement(value.BuildArgs, C.uint64_t(i), v)
    }


    value.ImageTagsCount = C.uint64_t(len(ImageTags))
    value.ImageTags = C.CreatestringArray(value.ImageTagsCount)

    for i, v := range ImageTags {
        C.SetstringArrayElement(value.ImageTags, C.uint64_t(i), C.CString(v))
    }

    value.AlwaysPullBaseImages = C.bool(AlwaysPullBaseImages)
    value.NoCache = C.bool(NoCache)
    value.TargetBuildStage = C.CString(TargetBuildStage)
    value.BuilderVersion = C.CString(BuilderVersion)

    return value
}

func newBuildImageReturn(
    Response ImageReference,
    Error Error,
) BuildImageReturn {
    value := C.AllocBuildImageReturn()
    value.Response = Response
    value.Error = Error

    return value
}

func newBuildImageProgressUpdate_ImageBuildContextUploadProgress(
    StepNumber int64,
    BytesUploaded int64,
) BuildImageProgressUpdate_ImageBuildContextUploadProgress {
    value := C.AllocBuildImageProgressUpdate_ImageBuildContextUploadProgress()
    value.StepNumber = C.int64_t(StepNumber)
    value.BytesUploaded = C.int64_t(BytesUploaded)

    return value
}

func newBuildImageProgressUpdate_StepStarting(
    StepNumber int64,
    StepName string,
) BuildImageProgressUpdate_StepStarting {
    value := C.AllocBuildImageProgressUpdate_StepStarting()
    value.StepNumber = C.int64_t(StepNumber)
    value.StepName = C.CString(StepName)

    return value
}

func newBuildImageProgressUpdate_StepOutput(
    StepNumber int64,
    Output string,
) BuildImageProgressUpdate_StepOutput {
    value := C.AllocBuildImageProgressUpdate_StepOutput()
    value.StepNumber = C.int64_t(StepNumber)
    value.Output = C.CString(Output)

    return value
}

func newBuildImageProgressUpdate_StepPullProgressUpdate(
    StepNumber int64,
    PullProgress PullImageProgressUpdate,
) BuildImageProgressUpdate_StepPullProgressUpdate {
    value := C.AllocBuildImageProgressUpdate_StepPullProgressUpdate()
    value.StepNumber = C.int64_t(StepNumber)
    value.PullProgress = PullProgress

    return value
}

func newBuildImageProgressUpdate_StepDownloadProgressUpdate(
    StepNumber int64,
    DownloadedBytes int64,
    TotalBytes int64,
) BuildImageProgressUpdate_StepDownloadProgressUpdate {
    value := C.AllocBuildImageProgressUpdate_StepDownloadProgressUpdate()
    value.StepNumber = C.int64_t(StepNumber)
    value.DownloadedBytes = C.int64_t(DownloadedBytes)
    value.TotalBytes = C.int64_t(TotalBytes)

    return value
}

func newBuildImageProgressUpdate_StepFinished(
    StepNumber int64,
) BuildImageProgressUpdate_StepFinished {
    value := C.AllocBuildImageProgressUpdate_StepFinished()
    value.StepNumber = C.int64_t(StepNumber)

    return value
}

func newBuildImageProgressUpdate_BuildFailed(
    Message string,
) BuildImageProgressUpdate_BuildFailed {
    value := C.AllocBuildImageProgressUpdate_BuildFailed()
    value.Message = C.CString(Message)

    return value
}

func newBuildImageProgressUpdate(
    ImageBuildContextUploadProgress BuildImageProgressUpdate_ImageBuildContextUploadProgress,
    StepStarting BuildImageProgressUpdate_StepStarting,
    StepOutput BuildImageProgressUpdate_StepOutput,
    StepPullProgressUpdate BuildImageProgressUpdate_StepPullProgressUpdate,
    StepDownloadProgressUpdate BuildImageProgressUpdate_StepDownloadProgressUpdate,
    StepFinished BuildImageProgressUpdate_StepFinished,
    BuildFailed BuildImageProgressUpdate_BuildFailed,
) BuildImageProgressUpdate {
    value := C.AllocBuildImageProgressUpdate()
    value.ImageBuildContextUploadProgress = ImageBuildContextUploadProgress
    value.StepStarting = StepStarting
    value.StepOutput = StepOutput
    value.StepPullProgressUpdate = StepPullProgressUpdate
    value.StepDownloadProgressUpdate = StepDownloadProgressUpdate
    value.StepFinished = StepFinished
    value.BuildFailed = BuildFailed

    return value
}

func newContainerReference(
    ID string,
) ContainerReference {
    value := C.AllocContainerReference()
    value.ID = C.CString(ID)

    return value
}

func newDeviceMount(
    LocalPath string,
    ContainerPath string,
    Permissions string,
) DeviceMount {
    value := C.AllocDeviceMount()
    value.LocalPath = C.CString(LocalPath)
    value.ContainerPath = C.CString(ContainerPath)
    value.Permissions = C.CString(Permissions)

    return value
}

func newExposedPort(
    LocalPort int64,
    ContainerPort int64,
    Protocol string,
) ExposedPort {
    value := C.AllocExposedPort()
    value.LocalPort = C.int64_t(LocalPort)
    value.ContainerPort = C.int64_t(ContainerPort)
    value.Protocol = C.CString(Protocol)

    return value
}

func newCreateContainerRequest(
    ImageReference string,
    Name string,
    Command []string,
    Entrypoint []string,
    WorkingDirectory string,
    Hostname string,
    ExtraHosts []string,
    EnvironmentVariables []string,
    BindMounts []string,
    TmpfsMounts []StringPair,
    DeviceMounts []DeviceMount,
    ExposedPorts []ExposedPort,
    User string,
    UseInitProcess bool,
    ShmSizeInBytes int64,
    AttachTTY bool,
    Privileged bool,
    CapabilitiesToAdd []string,
    CapabilitiesToDrop []string,
    NetworkReference string,
    NetworkAliases []string,
    LogDriver string,
    LoggingOptions []StringPair,
    HealthcheckCommand []string,
    HealthcheckInterval int64,
    HealthcheckTimeout int64,
    HealthcheckStartPeriod int64,
    HealthcheckRetries int64,
    Labels []StringPair,
) CreateContainerRequest {
    value := C.AllocCreateContainerRequest()
    value.ImageReference = C.CString(ImageReference)
    value.Name = C.CString(Name)

    value.CommandCount = C.uint64_t(len(Command))
    value.Command = C.CreatestringArray(value.CommandCount)

    for i, v := range Command {
        C.SetstringArrayElement(value.Command, C.uint64_t(i), C.CString(v))
    }


    value.EntrypointCount = C.uint64_t(len(Entrypoint))
    value.Entrypoint = C.CreatestringArray(value.EntrypointCount)

    for i, v := range Entrypoint {
        C.SetstringArrayElement(value.Entrypoint, C.uint64_t(i), C.CString(v))
    }

    value.WorkingDirectory = C.CString(WorkingDirectory)
    value.Hostname = C.CString(Hostname)

    value.ExtraHostsCount = C.uint64_t(len(ExtraHosts))
    value.ExtraHosts = C.CreatestringArray(value.ExtraHostsCount)

    for i, v := range ExtraHosts {
        C.SetstringArrayElement(value.ExtraHosts, C.uint64_t(i), C.CString(v))
    }


    value.EnvironmentVariablesCount = C.uint64_t(len(EnvironmentVariables))
    value.EnvironmentVariables = C.CreatestringArray(value.EnvironmentVariablesCount)

    for i, v := range EnvironmentVariables {
        C.SetstringArrayElement(value.EnvironmentVariables, C.uint64_t(i), C.CString(v))
    }


    value.BindMountsCount = C.uint64_t(len(BindMounts))
    value.BindMounts = C.CreatestringArray(value.BindMountsCount)

    for i, v := range BindMounts {
        C.SetstringArrayElement(value.BindMounts, C.uint64_t(i), C.CString(v))
    }


    value.TmpfsMountsCount = C.uint64_t(len(TmpfsMounts))
    value.TmpfsMounts = C.CreateStringPairArray(value.TmpfsMountsCount)

    for i, v := range TmpfsMounts {
        C.SetStringPairArrayElement(value.TmpfsMounts, C.uint64_t(i), v)
    }


    value.DeviceMountsCount = C.uint64_t(len(DeviceMounts))
    value.DeviceMounts = C.CreateDeviceMountArray(value.DeviceMountsCount)

    for i, v := range DeviceMounts {
        C.SetDeviceMountArrayElement(value.DeviceMounts, C.uint64_t(i), v)
    }


    value.ExposedPortsCount = C.uint64_t(len(ExposedPorts))
    value.ExposedPorts = C.CreateExposedPortArray(value.ExposedPortsCount)

    for i, v := range ExposedPorts {
        C.SetExposedPortArrayElement(value.ExposedPorts, C.uint64_t(i), v)
    }

    value.User = C.CString(User)
    value.UseInitProcess = C.bool(UseInitProcess)
    value.ShmSizeInBytes = C.int64_t(ShmSizeInBytes)
    value.AttachTTY = C.bool(AttachTTY)
    value.Privileged = C.bool(Privileged)

    value.CapabilitiesToAddCount = C.uint64_t(len(CapabilitiesToAdd))
    value.CapabilitiesToAdd = C.CreatestringArray(value.CapabilitiesToAddCount)

    for i, v := range CapabilitiesToAdd {
        C.SetstringArrayElement(value.CapabilitiesToAdd, C.uint64_t(i), C.CString(v))
    }


    value.CapabilitiesToDropCount = C.uint64_t(len(CapabilitiesToDrop))
    value.CapabilitiesToDrop = C.CreatestringArray(value.CapabilitiesToDropCount)

    for i, v := range CapabilitiesToDrop {
        C.SetstringArrayElement(value.CapabilitiesToDrop, C.uint64_t(i), C.CString(v))
    }

    value.NetworkReference = C.CString(NetworkReference)

    value.NetworkAliasesCount = C.uint64_t(len(NetworkAliases))
    value.NetworkAliases = C.CreatestringArray(value.NetworkAliasesCount)

    for i, v := range NetworkAliases {
        C.SetstringArrayElement(value.NetworkAliases, C.uint64_t(i), C.CString(v))
    }

    value.LogDriver = C.CString(LogDriver)

    value.LoggingOptionsCount = C.uint64_t(len(LoggingOptions))
    value.LoggingOptions = C.CreateStringPairArray(value.LoggingOptionsCount)

    for i, v := range LoggingOptions {
        C.SetStringPairArrayElement(value.LoggingOptions, C.uint64_t(i), v)
    }


    value.HealthcheckCommandCount = C.uint64_t(len(HealthcheckCommand))
    value.HealthcheckCommand = C.CreatestringArray(value.HealthcheckCommandCount)

    for i, v := range HealthcheckCommand {
        C.SetstringArrayElement(value.HealthcheckCommand, C.uint64_t(i), C.CString(v))
    }

    value.HealthcheckInterval = C.int64_t(HealthcheckInterval)
    value.HealthcheckTimeout = C.int64_t(HealthcheckTimeout)
    value.HealthcheckStartPeriod = C.int64_t(HealthcheckStartPeriod)
    value.HealthcheckRetries = C.int64_t(HealthcheckRetries)

    value.LabelsCount = C.uint64_t(len(Labels))
    value.Labels = C.CreateStringPairArray(value.LabelsCount)

    for i, v := range Labels {
        C.SetStringPairArrayElement(value.Labels, C.uint64_t(i), v)
    }


    return value
}

func newCreateContainerReturn(
    Response ContainerReference,
    Error Error,
) CreateContainerReturn {
    value := C.AllocCreateContainerReturn()
    value.Response = Response
    value.Error = Error

    return value
}

func newWaitForContainerToExitReturn(
    ExitCode int64,
    Error Error,
) WaitForContainerToExitReturn {
    value := C.AllocWaitForContainerToExitReturn()
    value.ExitCode = C.int64_t(ExitCode)
    value.Error = Error

    return value
}

func newContainerHealthcheckConfig(
    Test []string,
    Interval int64,
    Timeout int64,
    StartPeriod int64,
    Retries int64,
) ContainerHealthcheckConfig {
    value := C.AllocContainerHealthcheckConfig()

    value.TestCount = C.uint64_t(len(Test))
    value.Test = C.CreatestringArray(value.TestCount)

    for i, v := range Test {
        C.SetstringArrayElement(value.Test, C.uint64_t(i), C.CString(v))
    }

    value.Interval = C.int64_t(Interval)
    value.Timeout = C.int64_t(Timeout)
    value.StartPeriod = C.int64_t(StartPeriod)
    value.Retries = C.int64_t(Retries)

    return value
}

func newContainerConfig(
    Labels []StringPair,
    Healthcheck ContainerHealthcheckConfig,
) ContainerConfig {
    value := C.AllocContainerConfig()

    value.LabelsCount = C.uint64_t(len(Labels))
    value.Labels = C.CreateStringPairArray(value.LabelsCount)

    for i, v := range Labels {
        C.SetStringPairArrayElement(value.Labels, C.uint64_t(i), v)
    }

    value.Healthcheck = Healthcheck

    return value
}

func newContainerHealthLogEntry(
    Start int64,
    End int64,
    ExitCode int64,
    Output string,
) ContainerHealthLogEntry {
    value := C.AllocContainerHealthLogEntry()
    value.Start = C.int64_t(Start)
    value.End = C.int64_t(End)
    value.ExitCode = C.int64_t(ExitCode)
    value.Output = C.CString(Output)

    return value
}

func newContainerHealthState(
    Status string,
    Log []ContainerHealthLogEntry,
) ContainerHealthState {
    value := C.AllocContainerHealthState()
    value.Status = C.CString(Status)

    value.LogCount = C.uint64_t(len(Log))
    value.Log = C.CreateContainerHealthLogEntryArray(value.LogCount)

    for i, v := range Log {
        C.SetContainerHealthLogEntryArrayElement(value.Log, C.uint64_t(i), v)
    }


    return value
}

func newContainerState(
    Health ContainerHealthState,
) ContainerState {
    value := C.AllocContainerState()
    value.Health = Health

    return value
}

func newContainerLogConfig(
    Type string,
    Config []StringPair,
) ContainerLogConfig {
    value := C.AllocContainerLogConfig()
    value.Type = C.CString(Type)

    value.ConfigCount = C.uint64_t(len(Config))
    value.Config = C.CreateStringPairArray(value.ConfigCount)

    for i, v := range Config {
        C.SetStringPairArrayElement(value.Config, C.uint64_t(i), v)
    }


    return value
}

func newContainerHostConfig(
    LogConfig ContainerLogConfig,
) ContainerHostConfig {
    value := C.AllocContainerHostConfig()
    value.LogConfig = LogConfig

    return value
}

func newContainerInspectionResult(
    ID string,
    Name string,
    HostConfig ContainerHostConfig,
    State ContainerState,
    Config ContainerConfig,
) ContainerInspectionResult {
    value := C.AllocContainerInspectionResult()
    value.ID = C.CString(ID)
    value.Name = C.CString(Name)
    value.HostConfig = HostConfig
    value.State = State
    value.Config = Config

    return value
}

func newInspectContainerReturn(
    Response ContainerInspectionResult,
    Error Error,
) InspectContainerReturn {
    value := C.AllocInspectContainerReturn()
    value.Response = Response
    value.Error = Error

    return value
}

func invokePullImageProgressCallback(method PullImageProgressCallback, userData unsafe.Pointer, progress PullImageProgressUpdate) bool {
    return bool(C.InvokePullImageProgressCallback(method, userData, progress))
}

func invokeBuildImageProgressCallback(method BuildImageProgressCallback, userData unsafe.Pointer, progress BuildImageProgressUpdate) bool {
    return bool(C.InvokeBuildImageProgressCallback(method, userData, progress))
}

func invokeReadyCallback(method ReadyCallback, userData unsafe.Pointer, ) bool {
    return bool(C.InvokeReadyCallback(method, userData, ))
}

