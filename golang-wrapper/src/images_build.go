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
	"io"
	"regexp"
	"strconv"
	"unsafe"

	"github.com/docker/cli/cli/command/image/build"
	"github.com/docker/docker/api/types"
	"github.com/docker/docker/pkg/archive"
	"github.com/docker/docker/pkg/idtools"
	"github.com/docker/docker/pkg/jsonmessage"
	"github.com/pkg/errors"
)

var buildStepLineRegex = regexp.MustCompile(`^Step (\d+)/(\d+) : (.*)$`)

//export BuildImage
func BuildImage(clientHandle DockerClientHandle, request *C.BuildImageRequest, outputStreamHandle OutputStreamHandle, onProgressUpdate BuildImageProgressCallback, callbackUserData unsafe.Pointer) BuildImageReturn {
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

	imageID, err := processImageBuildResponseBody(response, outputStreamHandle, onProgressUpdate, callbackUserData)

	if err != nil {
		return newBuildImageReturn(nil, toError(err))
	}

	return newBuildImageReturn(newImageReference(imageID), nil)
}

func processImageBuildResponseBody(response types.ImageBuildResponse, outputStreamHandle OutputStreamHandle, onProgressUpdate BuildImageProgressCallback, callbackUserData unsafe.Pointer) (string, error) {
	imageID := ""
	currentStep := int64(0)
	haveSeenMeaningfulOutputForCurrentStep := false

	process := func(msg jsonmessage.JSONMessage) error {
		if msg.Stream != "" {
			if match := buildStepLineRegex.FindStringSubmatch(msg.Stream); match != nil {
				newStep, err := strconv.ParseInt(match[1], 10, 64)
				stepName := match[3]

				if err != nil {
					// This should never happen - the regex should not match values that are non-numeric.
					panic(err)
				}

				if currentStep != 0 {
					onStepFinished(currentStep, onProgressUpdate, callbackUserData)
				}

				currentStep = newStep
				haveSeenMeaningfulOutputForCurrentStep = false

				onStepStarting(newStep, stepName, onProgressUpdate, callbackUserData)
			} else if haveSeenMeaningfulOutputForCurrentStep || msg.Stream != "\n" {
				haveSeenMeaningfulOutputForCurrentStep = true

				onStepOutput(msg.Stream, currentStep, onProgressUpdate, callbackUserData)
			}
		}

		if msg.Error != nil {
			onBuildFailed(msg.Error.Message, onProgressUpdate, callbackUserData)
		}

		if msg.Aux != nil {
			var result types.BuildResult
			if err := json.Unmarshal(*msg.Aux, &result); err == nil {
				imageID = result.ID
			}
		}

		return nil
	}

	output := getOutputStream(outputStreamHandle)
	err := parseJSONMessagesStream(response.Body, output, process)

	if err != nil {
		return "", err
	}

	return imageID, nil
}

func onBuildFailed(msg string, onProgressUpdate BuildImageProgressCallback, callbackUserData unsafe.Pointer) {
	update := newBuildImageProgressUpdate(nil, nil, nil, nil, nil, newBuildImageProgressUpdate_BuildFailed(msg))

	defer C.FreeBuildImageProgressUpdate(update)

	invokeBuildImageProgressCallback(onProgressUpdate, callbackUserData, update)
}

func onStepOutput(output string, currentStep int64, onProgressUpdate BuildImageProgressCallback, callbackUserData unsafe.Pointer) {
	update := newBuildImageProgressUpdate(nil, nil, newBuildImageProgressUpdate_StepOutput(currentStep, output), nil, nil, nil)

	defer C.FreeBuildImageProgressUpdate(update)

	invokeBuildImageProgressCallback(onProgressUpdate, callbackUserData, update)
}

func onStepFinished(currentStep int64, onProgressUpdate BuildImageProgressCallback, callbackUserData unsafe.Pointer) {
	update := newBuildImageProgressUpdate(nil, nil, nil, nil, newBuildImageProgressUpdate_StepFinished(currentStep), nil)

	defer C.FreeBuildImageProgressUpdate(update)

	invokeBuildImageProgressCallback(onProgressUpdate, callbackUserData, update)
}

func onStepStarting(newStep int64, stepName string, onProgressUpdate BuildImageProgressCallback, callbackUserData unsafe.Pointer) {
	update := newBuildImageProgressUpdate(nil, newBuildImageProgressUpdate_StepStarting(newStep, stepName), nil, nil, nil, nil)

	defer C.FreeBuildImageProgressUpdate(update)

	invokeBuildImageProgressCallback(onProgressUpdate, callbackUserData, update)
}

// This function is based on jsonmessage.DisplayJSONMessagesStream, but allows us to process every message, not just those with
// an aux value.
func parseJSONMessagesStream(in io.Reader, out io.Writer, processor func(message jsonmessage.JSONMessage) error) error {
	decoder := json.NewDecoder(in)

	for {
		var msg jsonmessage.JSONMessage

		if err := decoder.Decode(&msg); err != nil {
			if errors.Is(err, io.EOF) {
				return nil
			}

			return err
		}

		if msg.Aux == nil {
			if err := msg.Display(out, false); err != nil {
				return err
			}
		}

		if err := processor(msg); err != nil {
			return err
		}
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
