// Copyright 2017-2021 Charles Korn.
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
	"encoding/json"
	"fmt"
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

func buildImageWithLegacyBuilder(clientHandle DockerClientHandle, request *imageBuildRequest, outputStreamHandle OutputStreamHandle, onProgressUpdate BuildImageProgressCallback, callbackUserData unsafe.Pointer) BuildImageReturn {
	docker := clientHandle.DockerAPIClient()
	configFile := clientHandle.ClientConfigFile()
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

	progressCallback := newImageBuildProgressCallback(onProgressUpdate, callbackUserData)
	contextUploadEventHandler := newContextUploadProgressHandler(progressCallback)
	buildContext = replacements.NewProgressReader(buildContext, contextUploadEventHandler, 0, "", "Sending build context to Docker daemon")

	opts := createLegacyBuilderImageBuildOptions(docker, configFile, pathToDockerfile, request)
	response, err := docker.ImageBuild(context.Background(), buildContext, opts)

	if err != nil {
		if errors.Is(err, ErrProgressCallbackFailed) {
			return newBuildImageReturn(nil, toError(ErrProgressCallbackFailed))
		}

		return newBuildImageReturn(nil, toError(err))
	}

	parser := newLegacyImageBuildResponseBodyParser(outputStreamHandle, progressCallback)
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
	progressCallback                       *imageBuildProgressCallback
}

func newLegacyImageBuildResponseBodyParser(outputStreamHandle OutputStreamHandle, progressCallback *imageBuildProgressCallback) *legacyImageBuildResponseBodyParser {
	return &legacyImageBuildResponseBodyParser{
		outputStreamHandle: outputStreamHandle,
		progressCallback:   progressCallback,
	}
}

func (p *legacyImageBuildResponseBodyParser) Parse(response types.ImageBuildResponse) (string, error) {
	p.imageID = ""
	p.currentStep = int64(0)
	p.haveSeenMeaningfulOutputForCurrentStep = false
	p.haveSeenStepFinishedLineForCurrentStep = false
	p.currentStepIsPullStep = false

	output := p.outputStreamHandle.OutputStream()

	if err := parseAndDisplayJSONMessagesStream(response.Body, output, p.onMessageReceived); err != nil {
		return "", err
	}

	if p.imageID != "" {
		if err := p.progressCallback.onStepFinished(p.currentStep); err != nil {
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
		if err := p.progressCallback.onBuildFailed(msg.Error.Message); err != nil {
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
			if err := p.progressCallback.onStepFinished(p.currentStep); err != nil {
				return err
			}
		}

		p.currentStep = newStep
		p.currentStepIsPullStep = strings.HasPrefix(strings.ToUpper(stepName), "FROM ")
		p.haveSeenMeaningfulOutputForCurrentStep = false
		p.haveSeenStepFinishedLineForCurrentStep = false

		return p.progressCallback.onStepStarting(newStep, stepName)
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

	return p.progressCallback.onStepOutput(p.currentStep, stream)
}

func (p *legacyImageBuildResponseBodyParser) onProgress(msg jsonmessage.JSONMessage) error {
	if p.currentStepIsPullStep {
		var progressDetail PullImageProgressDetail = nil

		if msg.Progress != nil {
			progressDetail = newPullImageProgressDetail(msg.Progress.Current, msg.Progress.Total)
		}

		progressUpdate := newPullImageProgressUpdate(msg.Status, progressDetail, msg.ID)

		return p.progressCallback.onImagePullProgress(p.currentStep, progressUpdate)
	}

	return p.progressCallback.onDownloadProgress(p.currentStep, msg.Progress.Current, msg.Progress.Total)
}

type contextUploadProgressHandler struct {
	progressCallback *imageBuildProgressCallback
}

func newContextUploadProgressHandler(progressCallback *imageBuildProgressCallback) *contextUploadProgressHandler {
	return &contextUploadProgressHandler{
		progressCallback: progressCallback,
	}
}

func (h *contextUploadProgressHandler) WriteProgress(progress progress.Progress) error {
	return h.progressCallback.onContextUploadProgress(0, progress.Current)
}
