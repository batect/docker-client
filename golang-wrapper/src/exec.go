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

	"github.com/batect/docker-client/golang-wrapper/src/replacements"
	"github.com/docker/docker/api/types"
)

//export CreateExec
func CreateExec(clientHandle DockerClientHandle, contextHandle ContextHandle, request *C.CreateExecRequest) CreateExecReturn {
	docker := clientHandle.DockerAPIClient()
	ctx := contextHandle.Context()

	containerID := C.GoString(request.ContainerID)

	config := types.ExecConfig{
		AttachStdout: bool(request.AttachStdout),
		AttachStderr: bool(request.AttachStderr),
		AttachStdin:  bool(request.AttachStdin),
		WorkingDir:   C.GoString(request.WorkingDirectory),
	}

	if request.CommandCount > 0 {
		config.Cmd = fromStringArray(request.Command, request.CommandCount)
	}

	if request.EnvironmentVariablesCount > 0 {
		config.Env = fromStringArray(request.EnvironmentVariables, request.EnvironmentVariablesCount)
	}

	resp, err := docker.ContainerExecCreate(ctx, containerID, config)

	if err != nil {
		return newCreateExecReturn(nil, toError(err))
	}

	return newCreateExecReturn(newContainerExecReference(resp.ID), nil)
}

//export StartExecDetached
func StartExecDetached(clientHandle DockerClientHandle, contextHandle ContextHandle, id *C.char, attachTTY C.bool) Error {
	docker := clientHandle.DockerAPIClient()
	ctx := contextHandle.Context()

	config := types.ExecStartCheck{
		Tty:    bool(attachTTY),
		Detach: true,
	}

	if err := docker.ContainerExecStart(ctx, C.GoString(id), config); err != nil {
		return toError(err)
	}

	return nil
}

//export InspectExec
func InspectExec(clientHandle DockerClientHandle, contextHandle ContextHandle, id *C.char) InspectExecReturn {
	docker := clientHandle.DockerAPIClient()
	ctx := contextHandle.Context()

	resp, err := docker.ContainerExecInspect(ctx, C.GoString(id))

	if err != nil {
		return newInspectExecReturn(nil, toError(err))
	}

	result := newInspectExecResult(int64(resp.ExitCode), resp.Running)

	return newInspectExecReturn(result, nil)
}

//export StartAndAttachToExec
func StartAndAttachToExec(clientHandle DockerClientHandle, contextHandle ContextHandle, id *C.char, attachTTY C.bool, stdoutStreamHandle OutputStreamHandle, stderrStreamHandle OutputStreamHandle, stdinStreamHandle InputStreamHandle) Error {
	defer stdoutStreamHandle.Close()
	defer stderrStreamHandle.Close()
	// We don't need to close stdinStreamHandle - the Kotlin code should do that when there is no more input to stream.

	docker := clientHandle.DockerAPIClient()
	ctx := contextHandle.Context()

	config := types.ExecStartCheck{
		Tty:    bool(attachTTY),
		Detach: false,
	}

	resp, err := docker.ContainerExecAttach(ctx, C.GoString(id), config)

	if err != nil {
		return toError(err)
	}

	defer resp.Close()

	streamer := replacements.HijackedIOStreamer{
		Tty:  bool(attachTTY),
		Resp: resp,
	}

	if stdoutStreamHandle != 0 {
		streamer.OutputStream = stdoutStreamHandle.OutputStream()
	}

	if stderrStreamHandle != 0 {
		streamer.ErrorStream = stderrStreamHandle.OutputStream()
	}

	if stdinStreamHandle != 0 {
		streamer.InputStream = stdinStreamHandle.InputStream()
	}


	if err := streamer.Stream(ctx); err != nil {
		return toError(err)
	}

	return nil
}
