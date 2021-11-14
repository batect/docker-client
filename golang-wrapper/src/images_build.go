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
	"context"
	"encoding/json"
	"fmt"

	"github.com/docker/cli/cli/command/image/build"
	"github.com/docker/docker/api/types"
	"github.com/docker/docker/pkg/archive"
	"github.com/docker/docker/pkg/idtools"
	"github.com/docker/docker/pkg/jsonmessage"
	"github.com/pkg/errors"
)

//export BuildImage
func BuildImage(clientHandle DockerClientHandle, request *C.BuildImageRequest, outputStreamHandle OutputStreamHandle) BuildImageReturn {
	defer closeOutputStream(outputStreamHandle)

	docker := getClient(clientHandle)

	contextDir := C.GoString(request.ContextDirectory)
	pathToDockerfile := C.GoString(request.PathToDockerfile)

	excludes, err := build.ReadDockerignore(contextDir)

	if err != nil {
		return newBuildImageReturn(nil, toError(fmt.Errorf("could not read dockerignore file: %w", err)))
	}

	if err := build.ValidateContextDirectory(contextDir, excludes); err != nil {
		return newBuildImageReturn(nil, toError(errors.Errorf("error validating build context: %s", err)))
	}

	pathToDockerfile = archive.CanonicalTarNameForPath(pathToDockerfile)
	excludes = build.TrimBuildFilesFromExcludes(excludes, pathToDockerfile, false)
	buildContext, err := archive.TarWithOptions(contextDir, &archive.TarOptions{
		ExcludePatterns: excludes,
		ChownOpts:       &idtools.Identity{UID: 0, GID: 0},
	})

	if err != nil {
		return newBuildImageReturn(nil, toError(err))
	}

	opts := types.ImageBuildOptions{
		Version:    types.BuilderV1,
		Dockerfile: pathToDockerfile,
		BuildArgs:  fromStringPairs(request.BuildArgs, request.BuildArgsCount),
		Tags:       fromStringArray(request.ImageTags, request.ImageTagsCount),
		PullParent: bool(request.AlwaysPullBaseImages),
		NoCache:    bool(request.NoCache),
	}

	response, err := docker.ImageBuild(context.Background(), buildContext, opts)

	if err != nil {
		return newBuildImageReturn(nil, toError(err))
	}

	imageID := ""
	aux := func(msg jsonmessage.JSONMessage) {
		var result types.BuildResult
		if err := json.Unmarshal(*msg.Aux, &result); err == nil {
			imageID = result.ID
		}
	}

	output := getOutputStream(outputStreamHandle)

	err = jsonmessage.DisplayJSONMessagesStream(response.Body, output, output.FD(), false, aux)

	if err != nil {
		return newBuildImageReturn(nil, toError(err))
	}

	return newBuildImageReturn(newImageReference(imageID), nil)
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
	l := make([]string, count)

	for i := 0; i < int(count); i++ {
		value := C.GoString(C.GetstringArrayElement(array, C.uint64_t(i)))
		l = append(l, value)
	}

	return l
}
