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

package main

import (
	/*
		#include "types.h"
	*/
	"C"
	"archive/tar"
	"bytes"
	"fmt"
	"strconv"
	"time"
	"unsafe"

	"github.com/batect/docker-client/golang-wrapper/src/replacements"
	"github.com/docker/docker/api/types"
	"github.com/docker/docker/api/types/container"
	"github.com/docker/docker/api/types/network"
	"github.com/docker/go-connections/nat"
)

//export CreateContainer
func CreateContainer(clientHandle DockerClientHandle, contextHandle ContextHandle, request *C.CreateContainerRequest) CreateContainerReturn {
	docker := clientHandle.DockerAPIClient()
	ctx := contextHandle.Context()

	config := configForContainer(request)
	hostConfig := hostConfigForContainer(request)
	networkingConfig := network.NetworkingConfig{}
	networkName := C.GoString(request.NetworkReference)

	if networkName != "" {
		hostConfig.NetworkMode = container.NetworkMode(networkName)

		networkingConfig.EndpointsConfig = map[string]*network.EndpointSettings{
			networkName: {
				Aliases: fromStringArray(request.NetworkAliases, request.NetworkAliasesCount),
			},
		}
	}

	containerName := C.GoString(request.Name)

	createdContainer, err := docker.ContainerCreate(ctx, &config, &hostConfig, &networkingConfig, nil, containerName)

	if err != nil {
		return newCreateContainerReturn(nil, toError(err))
	}

	return newCreateContainerReturn(newContainerReference(createdContainer.ID), nil)
}

func configForContainer(request *C.CreateContainerRequest) container.Config {
	config := container.Config{
		Image:        C.GoString(request.ImageReference),
		WorkingDir:   C.GoString(request.WorkingDirectory),
		Hostname:     C.GoString(request.Hostname),
		Env:          fromStringArray(request.EnvironmentVariables, request.EnvironmentVariablesCount),
		ExposedPorts: exposedPortsForContainer(request),
		User:         C.GoString(request.User),
		Tty:          bool(request.AttachTTY),
		AttachStdin:  bool(request.AttachStdin),
		StdinOnce:    bool(request.StdinOnce),
		OpenStdin:    bool(request.OpenStdin),
		Labels:       fromStringPairs(request.Labels, request.LabelsCount),
		Healthcheck: &container.HealthConfig{
			Interval:    time.Duration(int64(request.HealthcheckInterval)) * time.Nanosecond,
			Timeout:     time.Duration(int64(request.HealthcheckTimeout)) * time.Nanosecond,
			StartPeriod: time.Duration(int64(request.HealthcheckStartPeriod)) * time.Nanosecond,
			Retries:     int(request.HealthcheckRetries),
		},
	}

	if request.CommandCount > 0 {
		config.Cmd = fromStringArray(request.Command, request.CommandCount)
	}

	if request.EntrypointCount > 0 {
		config.Entrypoint = fromStringArray(request.Entrypoint, request.EntrypointCount)
	}

	if request.HealthcheckCommandCount > 0 {
		config.Healthcheck.Test = append([]string{"CMD-SHELL"}, fromStringArray(request.HealthcheckCommand, request.HealthcheckCommandCount)...)
	}

	return config
}

func hostConfigForContainer(request *C.CreateContainerRequest) container.HostConfig {
	useInitProcess := bool(request.UseInitProcess)

	hostConfig := container.HostConfig{
		ExtraHosts:   fromStringArray(request.ExtraHosts, request.ExtraHostsCount),
		Binds:        fromStringArray(request.BindMounts, request.BindMountsCount),
		Tmpfs:        fromStringPairs(request.TmpfsMounts, request.TmpfsMountsCount),
		PortBindings: portBindingsForContainer(request),
		Init:         &useInitProcess,
		ShmSize:      int64(request.ShmSizeInBytes),
		Privileged:   bool(request.Privileged),
		CapAdd:       fromStringArray(request.CapabilitiesToAdd, request.CapabilitiesToAddCount),
		CapDrop:      fromStringArray(request.CapabilitiesToDrop, request.CapabilitiesToDropCount),
		Resources: container.Resources{
			Devices: devicesForContainer(request),
		},
		LogConfig: container.LogConfig{
			Type:   C.GoString(request.LogDriver),
			Config: fromStringPairs(request.LoggingOptions, request.LoggingOptionsCount),
		},
	}

	return hostConfig
}

//export StartContainer
func StartContainer(clientHandle DockerClientHandle, contextHandle ContextHandle, id *C.char) Error {
	docker := clientHandle.DockerAPIClient()
	ctx := contextHandle.Context()
	opts := types.ContainerStartOptions{}

	if err := docker.ContainerStart(ctx, C.GoString(id), opts); err != nil {
		return toError(err)
	}

	return nil
}

//export StopContainer
func StopContainer(clientHandle DockerClientHandle, contextHandle ContextHandle, id *C.char, timeoutSeconds C.int64_t) Error {
	docker := clientHandle.DockerAPIClient()
	ctx := contextHandle.Context()
	timeout := int(timeoutSeconds)
	opts := container.StopOptions{
		Timeout: &timeout,
	}

	if err := docker.ContainerStop(ctx, C.GoString(id), opts); err != nil {
		return toError(err)
	}

	return nil
}

//export RemoveContainer
func RemoveContainer(clientHandle DockerClientHandle, contextHandle ContextHandle, id *C.char, force C.bool, removeVolumes C.bool) Error {
	docker := clientHandle.DockerAPIClient()
	ctx := contextHandle.Context()

	opts := types.ContainerRemoveOptions{
		Force:         bool(force),
		RemoveVolumes: bool(removeVolumes),
	}

	if err := docker.ContainerRemove(ctx, C.GoString(id), opts); err != nil {
		return toError(err)
	}

	return nil
}

//export AttachToContainerOutput
//nolint:funlen
func AttachToContainerOutput(
	clientHandle DockerClientHandle,
	contextHandle ContextHandle,
	id *C.char,
	stdoutStreamHandle OutputStreamHandle,
	stderrStreamHandle OutputStreamHandle,
	stdinStreamHandle InputStreamHandle,
	onReady ReadyCallback,
	callbackUserData unsafe.Pointer,
) Error {
	defer stdoutStreamHandle.Close()
	defer stderrStreamHandle.Close()
	// We don't need to close stdinStreamHandle - the Kotlin code should do that when there is no more input to stream.

	docker := clientHandle.DockerAPIClient()
	ctx := contextHandle.Context()
	containerID := C.GoString(id)

	config, err := docker.ContainerInspect(ctx, containerID)

	if err != nil {
		return toError(err)
	}

	opts := types.ContainerAttachOptions{
		Logs:   true,
		Stream: true,
	}

	streamer := replacements.HijackedIOStreamer{
		Tty: config.Config.Tty,
	}

	if stdoutStreamHandle != 0 {
		streamer.OutputStream = stdoutStreamHandle.OutputStream()
		opts.Stdout = true
	}

	if stderrStreamHandle != 0 {
		streamer.ErrorStream = stderrStreamHandle.OutputStream()
		opts.Stderr = true
	}

	if stdinStreamHandle != 0 {
		streamer.InputStream = stdinStreamHandle.InputStream()
		opts.Stdin = true
	}

	resp, err := docker.ContainerAttach(ctx, containerID, opts)

	if err != nil {
		return toError(err)
	}

	defer resp.Close()

	streamer.Resp = resp

	if !invokeReadyCallback(onReady, callbackUserData) {
		return toError(ErrReadyCallbackFailed)
	}

	if config.Config.Tty && stdoutStreamHandle.OutputStream().IsTerminal() {
		replacements.StartMonitoringTTYSizeForContainer(ctx, docker, stdoutStreamHandle.OutputStream(), containerID)
	}

	if err := streamer.Stream(ctx); err != nil {
		return toError(err)
	}

	return nil
}

//export WaitForContainerToExit
func WaitForContainerToExit(
	clientHandle DockerClientHandle,
	contextHandle ContextHandle,
	id *C.char,
	onReady ReadyCallback,
	callbackUserData unsafe.Pointer,
) WaitForContainerToExitReturn {
	docker := clientHandle.DockerAPIClient()
	ctx := contextHandle.Context()

	responseC, errC := docker.ContainerWait(ctx, C.GoString(id), container.WaitConditionNextExit)

	if !invokeReadyCallback(onReady, callbackUserData) {
		return newWaitForContainerToExitReturn(-1, toError(ErrReadyCallbackFailed))
	}

	select {
	case result := <-responseC:
		if result.Error != nil {
			return newWaitForContainerToExitReturn(-1, newError("WaitForContainerToExitFailed", result.Error.Message))
		}

		return newWaitForContainerToExitReturn(result.StatusCode, nil)

	case err := <-errC:
		return newWaitForContainerToExitReturn(-1, toError(err))
	}
}

//export InspectContainer
func InspectContainer(clientHandle DockerClientHandle, contextHandle ContextHandle, idOrName *C.char) InspectContainerReturn {
	docker := clientHandle.DockerAPIClient()
	ctx := contextHandle.Context()

	resp, err := docker.ContainerInspect(ctx, C.GoString(idOrName))

	if err != nil {
		return newInspectContainerReturn(nil, toError(err))
	}

	logConfig := newContainerLogConfig(resp.HostConfig.LogConfig.Type, toStringPairs(resp.HostConfig.LogConfig.Config))
	hostConfig := newContainerHostConfig(logConfig)

	var health ContainerHealthState

	if resp.State.Health == nil {
		health = newContainerHealthState("", []ContainerHealthLogEntry{})
	} else {
		health = newContainerHealthState(resp.State.Health.Status, toContainerHealthLogEntries(resp.State.Health.Log))
	}

	state := newContainerState(health)

	var healthcheckConfig ContainerHealthcheckConfig

	if resp.Config.Healthcheck != nil {
		healthcheckConfig = newContainerHealthcheckConfig(
			resp.Config.Healthcheck.Test,
			resp.Config.Healthcheck.Interval.Nanoseconds(),
			resp.Config.Healthcheck.Timeout.Nanoseconds(),
			resp.Config.Healthcheck.StartPeriod.Nanoseconds(),
			int64(resp.Config.Healthcheck.Retries),
		)
	}

	labels := toStringPairs(resp.Config.Labels)
	config := newContainerConfig(labels, healthcheckConfig)
	result := newContainerInspectionResult(resp.ID, resp.Name, hostConfig, state, config)

	return newInspectContainerReturn(result, nil)
}

//export UploadToContainer
func UploadToContainer(clientHandle DockerClientHandle, contextHandle ContextHandle, containerID *C.char, request *C.UploadToContainerRequest, destinationPath *C.char) Error {
	docker := clientHandle.DockerAPIClient()
	ctx := contextHandle.Context()
	buf := bytes.Buffer{}
	writer := tar.NewWriter(&buf)

	for i := 0; i < int(request.DirectoriesCount); i++ {
		dir := C.GetUploadDirectoryArrayElement(request.Directories, C.uint64_t(i))

		h := &tar.Header{
			Typeflag: tar.TypeDir,
			Name:     C.GoString(dir.Path) + "/",
			Uid:      int(dir.Owner),
			Gid:      int(dir.Group),
			Mode:     int64(dir.Mode),
		}

		if err := writer.WriteHeader(h); err != nil {
			return toError(err)
		}
	}

	for i := 0; i < int(request.FilesCount); i++ {
		file := C.GetUploadFileArrayElement(request.Files, C.uint64_t(i))

		h := &tar.Header{
			Typeflag: tar.TypeReg,
			Name:     C.GoString(file.Path),
			Size:     int64(file.ContentsSize),
			Uid:      int(file.Owner),
			Gid:      int(file.Group),
			Mode:     int64(file.Mode),
		}

		if err := writer.WriteHeader(h); err != nil {
			return toError(err)
		}

		contents := C.GoBytes(file.Contents, file.ContentsSize)

		if _, err := writer.Write(contents); err != nil {
			return toError(err)
		}
	}

	if err := writer.Close(); err != nil {
		return toError(err)
	}

	opts := types.CopyToContainerOptions{
		AllowOverwriteDirWithFile: false,
	}

	if err := docker.CopyToContainer(ctx, C.GoString(containerID), C.GoString(destinationPath), &buf, opts); err != nil {
		return toError(err)
	}

	return nil
}

func fromStringPairs(pairs **C.StringPair, count C.uint64_t) map[string]string {
	m := make(map[string]string, count)

	for i := 0; i < int(count); i++ {
		pair := C.GetStringPairArrayElement(pairs, C.uint64_t(i))
		key := C.GoString(pair.Key)
		value := C.GoString(pair.Value)

		m[key] = value
	}

	return m
}

func toStringPairs(m map[string]string) []StringPair {
	l := make([]StringPair, 0, len(m))

	for k, v := range m {
		pair := newStringPair(k, v)
		l = append(l, pair)
	}

	return l
}

func devicesForContainer(request *C.CreateContainerRequest) []container.DeviceMapping {
	count := request.DeviceMountsCount
	devices := make([]container.DeviceMapping, 0, count)

	for i := 0; i < int(count); i++ {
		requested := C.GetDeviceMountArrayElement(request.DeviceMounts, C.uint64_t(i))

		device := container.DeviceMapping{
			PathOnHost:        C.GoString(requested.LocalPath),
			PathInContainer:   C.GoString(requested.ContainerPath),
			CgroupPermissions: C.GoString(requested.Permissions),
		}

		devices = append(devices, device)
	}

	return devices
}

func portBindingsForContainer(request *C.CreateContainerRequest) nat.PortMap {
	portMap := nat.PortMap{}
	count := request.ExposedPortsCount

	for i := 0; i < int(count); i++ {
		requested := C.GetExposedPortArrayElement(request.ExposedPorts, C.uint64_t(i))
		port := nat.Port(fmt.Sprintf("%v/%v", requested.ContainerPort, C.GoString(requested.Protocol)))

		if _, ok := portMap[port]; !ok {
			portMap[port] = []nat.PortBinding{}
		}

		portMap[port] = append(portMap[port], nat.PortBinding{
			HostIP:   "",
			HostPort: strconv.FormatInt(int64(requested.LocalPort), 10),
		})
	}

	return portMap
}

func exposedPortsForContainer(request *C.CreateContainerRequest) nat.PortSet {
	portSet := nat.PortSet{}
	count := request.ExposedPortsCount

	for i := 0; i < int(count); i++ {
		requested := C.GetExposedPortArrayElement(request.ExposedPorts, C.uint64_t(i))
		port := fmt.Sprintf("%v/%v", requested.ContainerPort, C.GoString(requested.Protocol))

		portSet[nat.Port(port)] = struct{}{}
	}

	return portSet
}

func toContainerHealthLogEntries(results []*types.HealthcheckResult) []ContainerHealthLogEntry {
	l := make([]ContainerHealthLogEntry, 0, len(results))

	for _, result := range results {
		healthLogEntry := newContainerHealthLogEntry(
			result.Start.UnixMilli(),
			result.End.UnixMilli(),
			int64(result.ExitCode),
			result.Output,
		)

		l = append(l, healthLogEntry)
	}

	return l
}
