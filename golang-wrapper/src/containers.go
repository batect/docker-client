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
	"context"
	"time"
	"unsafe"

	"github.com/batect/docker-client/golang-wrapper/src/replacements"
	"github.com/docker/docker/api/types"
	"github.com/docker/docker/api/types/container"
	"github.com/docker/docker/api/types/network"
)

//export CreateContainer
func CreateContainer(clientHandle DockerClientHandle, spec *C.CreateContainerRequest) CreateContainerReturn {
	docker := clientHandle.DockerAPIClient()

	config := container.Config{
		Image: C.GoString(spec.ImageReference),
		Cmd:   fromStringArray(spec.Command, spec.CommandCount),
	}

	hostConfig := container.HostConfig{}
	networkingConfig := network.NetworkingConfig{}
	containerName := "" // TODO

	createdContainer, err := docker.ContainerCreate(context.Background(), &config, &hostConfig, &networkingConfig, nil, containerName)

	if err != nil {
		return newCreateContainerReturn(nil, toError(err))
	}

	return newCreateContainerReturn(newContainerReference(createdContainer.ID), nil)
}

//export StartContainer
func StartContainer(clientHandle DockerClientHandle, id *C.char) Error {
	docker := clientHandle.DockerAPIClient()
	opts := types.ContainerStartOptions{}

	if err := docker.ContainerStart(context.Background(), C.GoString(id), opts); err != nil {
		return toError(err)
	}

	return nil
}

//export StopContainer
func StopContainer(clientHandle DockerClientHandle, id *C.char, timeoutSeconds C.int64_t) Error {
	docker := clientHandle.DockerAPIClient()
	timeout := time.Second * time.Duration(timeoutSeconds)

	if err := docker.ContainerStop(context.Background(), C.GoString(id), &timeout); err != nil {
		return toError(err)
	}

	return nil
}

//export RemoveContainer
func RemoveContainer(clientHandle DockerClientHandle, id *C.char, force C.bool, removeVolumes C.bool) Error {
	docker := clientHandle.DockerAPIClient()
	opts := types.ContainerRemoveOptions{
		Force:         bool(force),
		RemoveVolumes: bool(removeVolumes),
	}

	if err := docker.ContainerRemove(context.Background(), C.GoString(id), opts); err != nil {
		return toError(err)
	}

	return nil
}

//export AttachToContainerOutput
func AttachToContainerOutput(clientHandle DockerClientHandle, contextHandle ContextHandle, id *C.char, stdoutStreamHandle OutputStreamHandle, stderrStreamHandle OutputStreamHandle, onReady ReadyCallback, callbackUserData unsafe.Pointer) Error {
	defer stdoutStreamHandle.Close()
	defer stderrStreamHandle.Close()

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
		Stdout: true,
		Stderr: true,
	}

	resp, err := docker.ContainerAttach(ctx, containerID, opts)

	if err != nil {
		return toError(err)
	}

	defer resp.Close()

	if !invokeReadyCallback(onReady, callbackUserData) {
		return toError(ErrReadyCallbackFailed)
	}

	streamer := replacements.HijackedIOStreamer{
		InputStream:  nil,
		OutputStream: stdoutStreamHandle.OutputStream(),
		ErrorStream:  stderrStreamHandle.OutputStream(),
		Resp:         resp,
		Tty:          config.Config.Tty,
	}

	if err := streamer.Stream(ctx); err != nil {
		return toError(err)
	}

	return nil
}

//export WaitForContainerToExit
func WaitForContainerToExit(clientHandle DockerClientHandle, contextHandle ContextHandle, id *C.char, onReady ReadyCallback, callbackUserData unsafe.Pointer) WaitForContainerToExitReturn {
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
