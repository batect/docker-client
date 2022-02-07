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

package main

import (
	/*
		#include "types.h"
	*/
	"C"
	"fmt"
	"unsafe"

	"github.com/docker/cli/cli/config/configfile"
	"github.com/docker/docker/api/types"
	"github.com/docker/docker/client"
)

//export BuildImage
func BuildImage(clientHandle DockerClientHandle, request *C.BuildImageRequest, outputStreamHandle OutputStreamHandle, reportContextUploadProgressEvents C.bool, onProgressUpdate BuildImageProgressCallback, callbackUserData unsafe.Pointer) BuildImageReturn {
	defer closeOutputStream(outputStreamHandle)

	builderVersion := C.GoString(request.BuilderVersion)

	switch builderVersion {
	case string(types.BuilderV1):
		return buildImageWithLegacyBuilder(clientHandle, fromCBuildImageRequest(request), outputStreamHandle, reportContextUploadProgressEvents, onProgressUpdate, callbackUserData)

	case string(types.BuilderBuildKit):
		return buildImageWithBuildKitBuilder(clientHandle, fromCBuildImageRequest(request), outputStreamHandle, onProgressUpdate, callbackUserData)

	default:
		return newBuildImageReturn(nil, toError(fmt.Errorf("unknown builder version '%v'", builderVersion)))
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
}

func fromCBuildImageRequest(request *C.BuildImageRequest) *imageBuildRequest {
	return &imageBuildRequest{
		ContextDirectory:     C.GoString(request.ContextDirectory),
		PathToDockerfile:     C.GoString(request.PathToDockerfile),
		BuildArgs:            fromStringPairs(request.BuildArgs, request.BuildArgsCount),
		ImageTags:            fromStringArray(request.ImageTags, request.ImageTagsCount),
		AlwaysPullBaseImages: bool(request.AlwaysPullBaseImages),
		NoCache:              bool(request.NoCache),
		TargetBuildStage:     C.GoString(request.TargetBuildStage),
	}
}

func fromStringPairs(pairs **C.StringPair, count C.uint64_t) map[string]*string {
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
