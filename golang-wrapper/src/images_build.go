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
	"encoding/json"
	"errors"
	"io"
	"unsafe"

	"github.com/docker/cli/cli/config/configfile"
	"github.com/docker/docker/api/types"
	"github.com/docker/docker/api/types/filters"
	"github.com/docker/docker/client"
	"github.com/docker/docker/pkg/jsonmessage"
	"github.com/moby/buildkit/session/secrets/secretsprovider"
	"github.com/moby/buildkit/session/sshforward/sshprovider"
)

//export BuildImage
func BuildImage(
	clientHandle DockerClientHandle,
	contextHandle ContextHandle,
	request *C.BuildImageRequest,
	outputStreamHandle OutputStreamHandle,
	onProgressUpdate BuildImageProgressCallback,
	callbackUserData unsafe.Pointer,
) BuildImageReturn {
	defer outputStreamHandle.Close()

	ctx := contextHandle.Context()
	builderVersion := C.GoString(request.BuilderVersion)

	if builderVersion == "" {
		defaultVersion, err := clientHandle.DefaultBuilderVersion(ctx)

		if err != nil {
			return newBuildImageReturn(nil, toError(err))
		}

		builderVersion = string(defaultVersion)
	}

	switch builderVersion {
	case string(types.BuilderV1):
		return buildImageWithLegacyBuilder(ctx, clientHandle, fromCBuildImageRequest(request), outputStreamHandle, onProgressUpdate, callbackUserData)

	case string(types.BuilderBuildKit):
		return buildImageWithBuildKitBuilder(ctx, clientHandle, fromCBuildImageRequest(request), outputStreamHandle, onProgressUpdate, callbackUserData)

	default:
		return newBuildImageReturn(nil, toError(InvalidBuilderVersionError{builderVersion}))
	}
}

type imageBuildRequest struct {
	ContextDirectory     string
	PathToDockerfile     string
	BuildArgs            map[string]*string
	ImageTags            []string
	AlwaysPullBaseImages bool
	NoCache              bool
	TargetBuildStage     string
	Secrets              []secretsprovider.Source
	SSHAgents            []sshprovider.AgentConfig
}

func fromCBuildImageRequest(request *C.BuildImageRequest) *imageBuildRequest {
	secrets := append(
		secretsFromFileSecrets(request.FileSecrets, request.FileSecretsCount),
		secretsFromEnvironmentSecrets(request.EnvironmentSecrets, request.EnvironmentSecretsCount)...,
	)

	return &imageBuildRequest{
		ContextDirectory:     C.GoString(request.ContextDirectory),
		PathToDockerfile:     C.GoString(request.PathToDockerfile),
		BuildArgs:            buildArgsFromStringPairs(request.BuildArgs, request.BuildArgsCount),
		ImageTags:            fromStringArray(request.ImageTags, request.ImageTagsCount),
		AlwaysPullBaseImages: bool(request.AlwaysPullBaseImages),
		NoCache:              bool(request.NoCache),
		TargetBuildStage:     C.GoString(request.TargetBuildStage),
		Secrets:              secrets,
		SSHAgents:            sshAgentsFromRequest(request.SSHAgents, request.SSHAgentsCount),
	}
}

func buildArgsFromStringPairs(pairs **C.StringPair, count C.uint64_t) map[string]*string {
	m := make(map[string]*string, count)

	for i := 0; i < int(count); i++ {
		pair := C.GetStringPairArrayElement(pairs, C.uint64_t(i))
		key := C.GoString(pair.Key)
		value := C.GoString(pair.Value)

		m[key] = &value
	}

	return m
}

func fromStringArray(array **C.char, count C.uint64_t) []string {
	l := make([]string, 0, count)

	for i := 0; i < int(count); i++ {
		value := C.GoString(C.GetstringArrayElement(array, C.uint64_t(i)))
		l = append(l, value)
	}

	return l
}

func secretsFromFileSecrets(secrets **C.FileBuildSecret, count C.uint64_t) []secretsprovider.Source {
	l := make([]secretsprovider.Source, 0, count)

	for i := 0; i < int(count); i++ {
		s := C.GetFileBuildSecretArrayElement(secrets, C.uint64_t(i))
		id := C.GoString(s.ID)
		path := C.GoString(s.Path)

		l = append(l, secretsprovider.Source{ID: id, FilePath: path})
	}

	return l
}

func secretsFromEnvironmentSecrets(secrets **C.EnvironmentBuildSecret, count C.uint64_t) []secretsprovider.Source {
	l := make([]secretsprovider.Source, 0, count)

	for i := 0; i < int(count); i++ {
		s := C.GetEnvironmentBuildSecretArrayElement(secrets, C.uint64_t(i))
		id := C.GoString(s.ID)
		environmentVariableName := C.GoString(s.SourceEnvironmentVariableName)

		l = append(l, secretsprovider.Source{ID: id, Env: environmentVariableName})
	}

	return l
}

func sshAgentsFromRequest(agents **C.SSHAgent, count C.uint64_t) []sshprovider.AgentConfig {
	l := make([]sshprovider.AgentConfig, 0, count)

	for i := 0; i < int(count); i++ {
		agent := C.GetSSHAgentArrayElement(agents, C.uint64_t(i))
		id := C.GoString(agent.ID)
		paths := fromStringArray(agent.Paths, agent.PathsCount)

		l = append(l, sshprovider.AgentConfig{ID: id, Paths: paths})
	}

	return l
}

func createImageBuildOptions(docker *client.Client, configFile *configfile.ConfigFile, pathToDockerfile string, request *imageBuildRequest) types.ImageBuildOptions {
	opts := types.ImageBuildOptions{
		Dockerfile:  pathToDockerfile,
		BuildArgs:   configFile.ParseProxyConfig(docker.DaemonHost(), request.BuildArgs),
		Tags:        request.ImageTags,
		PullParent:  request.AlwaysPullBaseImages,
		NoCache:     request.NoCache,
		Target:      request.TargetBuildStage,
		Remove:      true,
		ForceRemove: true,
	}

	return opts
}

// This function is based on jsonmessage.DisplayJSONMessagesStream, but allows us to process every message, not just those with
// an aux value.
func parseAndDisplayJSONMessagesStream(in io.Reader, out io.Writer, processor func(message jsonmessage.JSONMessage) error) error {
	decoder := json.NewDecoder(in)

	for {
		var msg jsonmessage.JSONMessage

		if err := decoder.Decode(&msg); err != nil {
			if errors.Is(err, io.EOF) {
				return nil
			}

			return err
		}

		if err := processor(msg); err != nil {
			return err
		}

		if msg.Aux == nil {
			if err := msg.Display(out, false); err != nil {
				return err
			}
		}
	}
}

type imageBuildProgressCallback struct {
	onProgressUpdate         BuildImageProgressCallback
	onProgressUpdateUserData unsafe.Pointer
}

func newImageBuildProgressCallback(onProgressUpdate BuildImageProgressCallback, callbackUserData unsafe.Pointer) *imageBuildProgressCallback {
	return &imageBuildProgressCallback{
		onProgressUpdate:         onProgressUpdate,
		onProgressUpdateUserData: callbackUserData,
	}
}

func (p *imageBuildProgressCallback) onBuildFailed(msg string) error {
	update := newBuildImageProgressUpdate(nil, nil, nil, nil, nil, nil, newBuildImageProgressUpdate_BuildFailed(msg))

	defer C.FreeBuildImageProgressUpdate(update)

	if !invokeBuildImageProgressCallback(p.onProgressUpdate, p.onProgressUpdateUserData, update) {
		return ErrProgressCallbackFailed
	}

	return nil
}

func (p *imageBuildProgressCallback) onStepOutput(currentStep int64, output string) error {
	update := newBuildImageProgressUpdate(nil, nil, newBuildImageProgressUpdate_StepOutput(currentStep, output), nil, nil, nil, nil)

	defer C.FreeBuildImageProgressUpdate(update)

	if !invokeBuildImageProgressCallback(p.onProgressUpdate, p.onProgressUpdateUserData, update) {
		return ErrProgressCallbackFailed
	}

	return nil
}

func (p *imageBuildProgressCallback) onStepFinished(currentStep int64) error {
	update := newBuildImageProgressUpdate(nil, nil, nil, nil, nil, newBuildImageProgressUpdate_StepFinished(currentStep), nil)

	defer C.FreeBuildImageProgressUpdate(update)

	if !invokeBuildImageProgressCallback(p.onProgressUpdate, p.onProgressUpdateUserData, update) {
		return ErrProgressCallbackFailed
	}

	return nil
}

func (p *imageBuildProgressCallback) onStepStarting(newStep int64, stepName string) error {
	update := newBuildImageProgressUpdate(nil, newBuildImageProgressUpdate_StepStarting(newStep, stepName), nil, nil, nil, nil, nil)

	defer C.FreeBuildImageProgressUpdate(update)

	if !invokeBuildImageProgressCallback(p.onProgressUpdate, p.onProgressUpdateUserData, update) {
		return ErrProgressCallbackFailed
	}

	return nil
}

func (p *imageBuildProgressCallback) onImagePullProgress(currentStep int64, progressUpdate PullImageProgressUpdate) error {
	update := newBuildImageProgressUpdate(nil, nil, nil, newBuildImageProgressUpdate_StepPullProgressUpdate(currentStep, progressUpdate), nil, nil, nil)

	defer C.FreeBuildImageProgressUpdate(update)

	if !invokeBuildImageProgressCallback(p.onProgressUpdate, p.onProgressUpdateUserData, update) {
		return ErrProgressCallbackFailed
	}

	return nil
}

func (p *imageBuildProgressCallback) onDownloadProgress(currentStep int64, downloadedBytes int64, totalBytes int64) error {
	update := newBuildImageProgressUpdate(nil, nil, nil, nil, newBuildImageProgressUpdate_StepDownloadProgressUpdate(currentStep, downloadedBytes, totalBytes), nil, nil)

	defer C.FreeBuildImageProgressUpdate(update)

	if !invokeBuildImageProgressCallback(p.onProgressUpdate, p.onProgressUpdateUserData, update) {
		return ErrProgressCallbackFailed
	}

	return nil
}

func (p *imageBuildProgressCallback) onContextUploadProgress(currentStep int64, currentBytes int64) error {
	update := newBuildImageProgressUpdate(newBuildImageProgressUpdate_ImageBuildContextUploadProgress(currentStep, currentBytes), nil, nil, nil, nil, nil, nil)

	defer C.FreeBuildImageProgressUpdate(update)

	if !invokeBuildImageProgressCallback(p.onProgressUpdate, p.onProgressUpdateUserData, update) {
		return ErrProgressCallbackFailed
	}

	return nil
}

//export PruneImageBuildCache
func PruneImageBuildCache(clientHandle DockerClientHandle, contextHandle ContextHandle) Error {
	docker := clientHandle.DockerAPIClient()
	ctx := contextHandle.Context()

	opts := types.BuildCachePruneOptions{
		All:         false,
		KeepStorage: 0,
		Filters:     filters.Args{},
	}

	_, err := docker.BuildCachePrune(ctx, opts)

	return toError(err)
}
