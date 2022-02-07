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
	"strings"
	"unsafe"

	"github.com/docker/cli/cli/command/image/build"
	"github.com/docker/cli/cli/config/configfile"
	"github.com/docker/docker/api/types"
	"github.com/docker/docker/client"
	"github.com/docker/docker/pkg/archive"
	"github.com/docker/docker/pkg/idtools"
	"github.com/docker/docker/pkg/jsonmessage"
	"github.com/docker/docker/pkg/progress"
	"github.com/pkg/errors"

	"github.com/batect/docker-client/golang-wrapper/src/replacements"
)

var buildStepLineRegex = regexp.MustCompile(`^Step (\d+)/(\d+) : (.*)$`)
var buildStepRunningInContainerLineRegex = regexp.MustCompile(`^ ---> Running in [0-9a-f]{12}\n$`)
var removingIntermediateContainerLineRegex = regexp.MustCompile(`^Removing intermediate container [0-9a-f]{12}\n$`)
var buildStepFinishedLineRegex = regexp.MustCompile(`^ ---> [0-9a-f]{12}\n$`)
var buildSuccessfullyFinishedLineRegex = regexp.MustCompile(`^Successfully built [0-9a-f]{12}\n$`)

func buildImageWithLegacyBuilder(clientHandle DockerClientHandle, request *imageBuildRequest, outputStreamHandle OutputStreamHandle, reportContextUploadProgressEvents C.bool, onProgressUpdate BuildImageProgressCallback, callbackUserData unsafe.Pointer) BuildImageReturn {
	docker := getDockerAPIClient(clientHandle)
	configFile := getClientConfigFile(clientHandle)
	contextDir := request.ContextDirectory
	pathToDockerfile := request.PathToDockerfile

	contextDir, pathToDockerfile, err := build.GetContextFromLocalDir(contextDir, pathToDockerfile)

	if err != nil {
		return newBuildImageReturn(nil, toError(err))
	}

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

	// This is only required while we're using the v1 Kotlin/Native memory model (as Golang invokes the callback from another thread).
	// Once we're using the new memory model, we can just always report context upload progress events.
	if bool(reportContextUploadProgressEvents) {
		contextUploadEventHandler := newContextUploadProgressHandler(onProgressUpdate, callbackUserData)
		buildContext = replacements.NewProgressReader(buildContext, contextUploadEventHandler, 0, "", "Sending build context to Docker daemon")
	}

	opts := createLegacyBuilderImageBuildOptions(docker, configFile, pathToDockerfile, request)
	response, err := docker.ImageBuild(context.Background(), buildContext, opts)

	if err != nil {
		if errors.Is(err, ErrProgressCallbackFailed) {
			return newBuildImageReturn(nil, toError(ErrProgressCallbackFailed))
		}

		return newBuildImageReturn(nil, toError(err))
	}

	parser := newLegacyImageBuildResponseBodyParser(outputStreamHandle, onProgressUpdate, callbackUserData)
	imageID, err := parser.Parse(response)

	if err != nil {
		return newBuildImageReturn(nil, toError(err))
	}

	return newBuildImageReturn(newImageReference(imageID), nil)
}

func createLegacyBuilderImageBuildOptions(docker *client.Client, configFile *configfile.ConfigFile, pathToDockerfile string, request *imageBuildRequest) types.ImageBuildOptions {
	creds, _ := configFile.GetAllCredentials() // The CLI ignores errors, so do we.
	authConfigs := make(map[string]types.AuthConfig, len(creds))

	for k, auth := range creds {
		authConfigs[k] = types.AuthConfig(auth)
	}

	opts := createImageBuildOptions(docker, configFile, pathToDockerfile, request)
	opts.Version = types.BuilderV1
	opts.AuthConfigs = authConfigs

	return opts
}

type legacyImageBuildResponseBodyParser struct {
	imageID                                string
	currentStep                            int64
	currentStepIsPullStep                  bool
	haveSeenMeaningfulOutputForCurrentStep bool
	haveSeenStepFinishedLineForCurrentStep bool
	outputStreamHandle                     OutputStreamHandle
	onProgressUpdate                       BuildImageProgressCallback
	onProgressUpdateUserData               unsafe.Pointer
}

func newLegacyImageBuildResponseBodyParser(outputStreamHandle OutputStreamHandle, onProgressUpdate BuildImageProgressCallback, callbackUserData unsafe.Pointer) *legacyImageBuildResponseBodyParser {
	return &legacyImageBuildResponseBodyParser{
		outputStreamHandle:       outputStreamHandle,
		onProgressUpdate:         onProgressUpdate,
		onProgressUpdateUserData: callbackUserData,
	}
}

func (p *legacyImageBuildResponseBodyParser) Parse(response types.ImageBuildResponse) (string, error) {
	p.imageID = ""
	p.currentStep = int64(0)
	p.haveSeenMeaningfulOutputForCurrentStep = false
	p.haveSeenStepFinishedLineForCurrentStep = false
	p.currentStepIsPullStep = false

	output := getOutputStream(p.outputStreamHandle)

	if err := parseAndDisplayJSONMessagesStream(response.Body, output, p.onMessageReceived); err != nil {
		return "", err
	}

	if p.imageID != "" {
		if err := p.onStepFinished(p.currentStep); err != nil {
			return "", err
		}
	}

	return p.imageID, nil
}

func (p *legacyImageBuildResponseBodyParser) onMessageReceived(msg jsonmessage.JSONMessage) error {
	if msg.Stream != "" {
		if err := p.onBuildOutput(msg.Stream); err != nil {
			return err
		}
	}

	if msg.Status != "" || msg.Progress != nil {
		if err := p.onProgress(msg); err != nil {
			return err
		}
	}

	if msg.Error != nil {
		if err := p.onBuildFailed(msg.Error.Message); err != nil {
			return err
		}
	}

	if msg.Aux != nil {
		var result types.BuildResult
		if err := json.Unmarshal(*msg.Aux, &result); err == nil {
			p.imageID = result.ID
		}
	}

	return nil
}

func (p *legacyImageBuildResponseBodyParser) onBuildOutput(stream string) error {
	if match := buildStepLineRegex.FindStringSubmatch(stream); match != nil {
		newStep, err := strconv.ParseInt(match[1], 10, 64)
		stepName := match[3]

		if err != nil {
			// This should never happen - the regex should not match values that are non-numeric.
			panic(err)
		}

		if p.currentStep != 0 {
			if err := p.onStepFinished(p.currentStep); err != nil {
				return err
			}
		}

		p.currentStep = newStep
		p.currentStepIsPullStep = strings.HasPrefix(strings.ToUpper(stepName), "FROM ")
		p.haveSeenMeaningfulOutputForCurrentStep = false
		p.haveSeenStepFinishedLineForCurrentStep = false

		return p.onStepStarting(newStep, stepName)
	}

	if !p.haveSeenMeaningfulOutputForCurrentStep && stream == "\n" {
		return nil
	}

	if !p.haveSeenMeaningfulOutputForCurrentStep && buildStepRunningInContainerLineRegex.MatchString(stream) {
		return nil
	}

	if removingIntermediateContainerLineRegex.MatchString(stream) {
		return nil
	}

	// FIXME: this could potentially match output from the build process rather than the synthetic output generated by the
	// daemon with the layer's ID.
	if buildStepFinishedLineRegex.MatchString(stream) {
		p.haveSeenStepFinishedLineForCurrentStep = true
		return nil
	}

	if p.haveSeenStepFinishedLineForCurrentStep && buildSuccessfullyFinishedLineRegex.MatchString(stream) {
		return nil
	}

	p.haveSeenMeaningfulOutputForCurrentStep = true

	return p.onStepOutput(stream, p.currentStep)
}

func (p *legacyImageBuildResponseBodyParser) onProgress(msg jsonmessage.JSONMessage) error {
	if p.currentStepIsPullStep {
		var progressDetail PullImageProgressDetail = nil

		if msg.Progress != nil {
			progressDetail = newPullImageProgressDetail(msg.Progress.Current, msg.Progress.Total)
		}

		progressUpdate := newPullImageProgressUpdate(msg.Status, progressDetail, msg.ID)

		return p.onImagePullProgress(progressUpdate, p.currentStep)
	}

	return p.onDownloadProgress(p.currentStep, msg.Progress.Current, msg.Progress.Total)
}

func (p *legacyImageBuildResponseBodyParser) onBuildFailed(msg string) error {
	update := newBuildImageProgressUpdate(nil, nil, nil, nil, nil, nil, newBuildImageProgressUpdate_BuildFailed(msg))

	defer C.FreeBuildImageProgressUpdate(update)

	if !invokeBuildImageProgressCallback(p.onProgressUpdate, p.onProgressUpdateUserData, update) {
		return ErrProgressCallbackFailed
	}

	return nil
}

func (p *legacyImageBuildResponseBodyParser) onStepOutput(output string, currentStep int64) error {
	update := newBuildImageProgressUpdate(nil, nil, newBuildImageProgressUpdate_StepOutput(currentStep, output), nil, nil, nil, nil)

	defer C.FreeBuildImageProgressUpdate(update)

	if !invokeBuildImageProgressCallback(p.onProgressUpdate, p.onProgressUpdateUserData, update) {
		return ErrProgressCallbackFailed
	}

	return nil
}

func (p *legacyImageBuildResponseBodyParser) onStepFinished(currentStep int64) error {
	update := newBuildImageProgressUpdate(nil, nil, nil, nil, nil, newBuildImageProgressUpdate_StepFinished(currentStep), nil)

	defer C.FreeBuildImageProgressUpdate(update)

	if !invokeBuildImageProgressCallback(p.onProgressUpdate, p.onProgressUpdateUserData, update) {
		return ErrProgressCallbackFailed
	}

	return nil
}

func (p *legacyImageBuildResponseBodyParser) onStepStarting(newStep int64, stepName string) error {
	update := newBuildImageProgressUpdate(nil, newBuildImageProgressUpdate_StepStarting(newStep, stepName), nil, nil, nil, nil, nil)

	defer C.FreeBuildImageProgressUpdate(update)

	if !invokeBuildImageProgressCallback(p.onProgressUpdate, p.onProgressUpdateUserData, update) {
		return ErrProgressCallbackFailed
	}

	return nil
}

func (p *legacyImageBuildResponseBodyParser) onImagePullProgress(progressUpdate PullImageProgressUpdate, currentStep int64) error {
	update := newBuildImageProgressUpdate(nil, nil, nil, newBuildImageProgressUpdate_StepPullProgressUpdate(currentStep, progressUpdate), nil, nil, nil)

	defer C.FreeBuildImageProgressUpdate(update)

	if !invokeBuildImageProgressCallback(p.onProgressUpdate, p.onProgressUpdateUserData, update) {
		return ErrProgressCallbackFailed
	}

	return nil
}

func (p *legacyImageBuildResponseBodyParser) onDownloadProgress(currentStep int64, downloadedBytes int64, totalBytes int64) error {
	update := newBuildImageProgressUpdate(nil, nil, nil, nil, newBuildImageProgressUpdate_StepDownloadProgressUpdate(currentStep, downloadedBytes, totalBytes), nil, nil)

	defer C.FreeBuildImageProgressUpdate(update)

	if !invokeBuildImageProgressCallback(p.onProgressUpdate, p.onProgressUpdateUserData, update) {
		return ErrProgressCallbackFailed
	}

	return nil
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

type contextUploadProgressHandler struct {
	onProgressUpdate         BuildImageProgressCallback
	onProgressUpdateUserData unsafe.Pointer
}

func newContextUploadProgressHandler(onProgressUpdate BuildImageProgressCallback, callbackUserData unsafe.Pointer) *contextUploadProgressHandler {
	return &contextUploadProgressHandler{
		onProgressUpdate:         onProgressUpdate,
		onProgressUpdateUserData: callbackUserData,
	}
}

func (h *contextUploadProgressHandler) WriteProgress(progress progress.Progress) error {
	update := newBuildImageProgressUpdate(newBuildImageProgressUpdate_ImageBuildContextUploadProgress(progress.Current), nil, nil, nil, nil, nil, nil)

	defer C.FreeBuildImageProgressUpdate(update)

	if !invokeBuildImageProgressCallback(h.onProgressUpdate, h.onProgressUpdateUserData, update) {
		return ErrProgressCallbackFailed
	}

	return nil
}
