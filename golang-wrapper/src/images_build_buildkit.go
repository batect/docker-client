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
	"C"
	"context"
	"fmt"
	"io"
	"os"
	"path/filepath"
	"strings"
	"unsafe"

	"github.com/batect/docker-client/golang-wrapper/src/replacements/progress"
	"github.com/docker/buildx/build"
	"github.com/docker/buildx/driver"
	"github.com/docker/buildx/store"
	"github.com/docker/buildx/util/imagetools"
	"github.com/docker/cli/cli/config/configfile"
	"github.com/docker/docker/api/types/versions"
	"github.com/docker/docker/client"
	buildkitclient "github.com/moby/buildkit/client"
	"github.com/moby/buildkit/session"
	"github.com/moby/buildkit/session/auth/authprovider"
	"github.com/moby/buildkit/session/secrets/secretsprovider"
	"github.com/moby/buildkit/session/sshforward/sshprovider"
	"github.com/opencontainers/go-digest"

	// Required for side effects.
	_ "github.com/docker/buildx/driver/docker"
	_ "github.com/docker/buildx/driver/docker-container"
	_ "github.com/docker/buildx/driver/remote"
)

const (
	// These values must be kept in sync with the values in BuildKitInstance in Kotlin.
	buildKitInstanceTypeSelectedOrDefaultDockerDriver = 1
	buildKitInstanceTypeDefaultDockerDriver           = 2
	buildKitInstanceTypeNamed                         = 3
)

func buildImageWithBuildKitBuilder(
	ctx context.Context,
	clientHandle DockerClientHandle,
	request *imageBuildRequest,
	outputStreamHandle OutputStreamHandle,
	onProgressUpdate BuildImageProgressCallback,
	callbackUserData unsafe.Pointer,
) BuildImageReturn {
	if supported, err := supportsBuildKit(ctx, clientHandle); err != nil {
		return newBuildImageReturn(nil, toError(err))
	} else if !supported {
		return newBuildImageReturn(nil, toError(ErrBuildKitNotSupported))
	}

	docker := clientHandle.DockerAPIClient()
	configFile := clientHandle.ClientConfigFile()

	sess, err := createSession(configFile, request)

	if err != nil {
		return newBuildImageReturn(nil, toError(err))
	}

	opts := build.Options{
		Inputs: build.Inputs{
			ContextPath:    request.ContextDirectory,
			DockerfilePath: request.PathToDockerfile,
		},
		BuildArgs: buildKitBuildArgs(request.BuildArgs),
		NoCache:   request.NoCache,
		Pull:      request.AlwaysPullBaseImages,
		Tags:      request.ImageTags,
		Target:    request.TargetBuildStage,
		Session:   sess,
		CacheFrom: request.CacheFrom,
		CacheTo:   request.CacheTo,
	}

	imageID, err := runBuild(ctx, docker, configFile, opts, outputStreamHandle.OutputStream(), onProgressUpdate, callbackUserData)

	if err != nil {
		return newBuildImageReturn(nil, toError(err))
	}

	return newBuildImageReturn(newImageReference(imageID), nil)
}

func buildKitBuildArgs(args map[string]*string) map[string]string {
	buildKitFormat := make(map[string]string, len(args))

	for k, v := range args {
		buildKitFormat[k] = *v
	}

	return buildKitFormat
}

// This is based on isSessionSupported from github.com/docker/cli/cli/command/image/build_session.go
func supportsBuildKit(ctx context.Context, clientHandle DockerClientHandle) (bool, error) {
	docker := clientHandle.DockerAPIClient()

	if versions.GreaterThanOrEqualTo(docker.ClientVersion(), "1.39") {
		return true, nil
	}

	serverInfo, err := clientHandle.ServerInfo(ctx)

	if err != nil {
		return false, err
	}

	return serverInfo.HasExperimental && versions.GreaterThanOrEqualTo(docker.ClientVersion(), "1.31"), nil
}

func createSession(configFile *configfile.ConfigFile, request *imageBuildRequest) ([]session.Attachable, error) {
	authProvider := createAuthProvider(configFile)
	secretsProvider, err := createSecretsProvider(request)

	if err != nil {
		return nil, err
	}

	sshProvider, err := createSSHProvider(request)

	if err != nil {
		return nil, err
	}

	return []session.Attachable{
		secretsProvider,
		authProvider,
		sshProvider,
	}, nil
}

func createAuthProvider(configFile *configfile.ConfigFile) session.Attachable {
	return authprovider.NewDockerAuthProvider(configFile)
}

func createSecretsProvider(request *imageBuildRequest) (session.Attachable, error) {
	store, err := secretsprovider.NewStore(request.Secrets)

	if err != nil {
		return nil, err
	}

	return secretsprovider.NewSecretProvider(store), nil
}

func createSSHProvider(request *imageBuildRequest) (session.Attachable, error) {
	return sshprovider.NewSSHAgentProvider(request.SSHAgents)
}

func runBuild(
	ctx context.Context,
	docker *client.Client,
	configFile *configfile.ConfigFile,
	opts build.Options,
	output io.Writer,
	onProgressUpdate BuildImageProgressCallback,
	onProgressUpdateUserData unsafe.Pointer,
) (string, error) {
	drivers, err := createDriver(ctx, docker, configFile, opts.Inputs.ContextPath)

	if err != nil {
		return "", err
	}

	callback := newBuildKitProgressCallback(onProgressUpdate, onProgressUpdateUserData)

	// We deliberately don't use the build operation's context as the context for the printer - otherwise, error messages might not be printed after the build's context is cancelled.
	//nolint:contextcheck
	printer := progress.NewPrinter(output, callback.onStatusUpdate)

	targetName := "default"
	o := map[string]build.Options{
		targetName: opts,
	}

	configDir := buildxConfigDir(configFile)
	api := buildxDockerAPI{docker}
	resp, err := build.BuildWithResultHandler(ctx, drivers, o, &api, configDir, printer, func(driverIndex int, resultContext *build.ResultContext) {}, false)
	printerErr := printer.Wait()

	if callback.callbackError != nil {
		return "", callback.callbackError
	}

	if printerErr != nil {
		return "", printerErr
	}

	if err != nil {
		return "", err
	}

	return resp[targetName].ExporterResponse["containerimage.digest"], nil
}

// If an instance name has been specified, and it is the name of the in-use Docker CLI context, use default driver for the current Docker CLI context (second half of getDefaultDrivers)
// If an instance name has been specified, and it is the name of an inactive Docker CLI context, fail
// If an instance name has been specified, and it is not the name of a Docker CLI context, use that instance (getInstanceByName)
// If no instance name has been specified:
//   - check for a selected instance and use that if it is present (first half of getDefaultDrivers)
//   - otherwise use the default driver for the current Docker CLI context (second half of getDefaultDrivers)
func createDriver(ctx context.Context, docker *client.Client, configFile *configfile.ConfigFile, request *imageBuildRequest, contextPathHash string) ([]build.DriverInfo, error) {
	switch request.BuildKitInstanceType {
	case buildKitInstanceTypeSelectedOrDefaultDockerDriver:
		if selectedInstance, err := getSelectedInstance(configFile); err != nil {
			return nil, err
		} else if selectedInstance != nil {
			panic("TODO: get selected instance")
		}

		return createDefaultDriver(ctx, docker, configFile, contextPathHash)
	case buildKitInstanceTypeDefaultDockerDriver:
		return createDefaultDriver(ctx, docker, configFile, contextPathHash)
	case buildKitInstanceTypeNamed:
		return createDriverByName(...)
	default:
		return nil, fmt.Errorf("invalid BuildKit instance type %d", request.BuildKitInstanceType)
	}
}

func getSelectedInstance(configFile *configfile.ConfigFile) (*store.NodeGroup, error) {
	configDir := buildxConfigDir(configFile)
	store, err := store.New(configDir)

	if err != nil {
		return nil, err
	}

	txn, release, err := store.Txn()

	if err != nil {
		return nil, err
	}

	defer release()

	
}

func createDefaultDriver(ctx context.Context, docker *client.Client, configFile *configfile.ConfigFile, contextPathHash string) ([]build.DriverInfo, error) {
	imageOpt := imagetools.Opt{Auth: configFile}

	d, err := driver.GetDriver(ctx, "buildx_buildkit_default", nil, "", docker, imageOpt.Auth, nil, nil, nil, nil, nil, contextPathHash)

	if err != nil {
		return nil, err
	}

	return []build.DriverInfo{
		{
			Name:        "default",
			Driver:      d,
			ImageOpt:    imageOpt,
			ProxyConfig: getProxyConfig(docker, configFile),
		},
	}, nil
}

// This is based on GetProxyConfig from github.com/docker/buildx/store/storeutil/storeutil.go
func getProxyConfig(docker *client.Client, configFile *configfile.ConfigFile) map[string]string {
	host := docker.DaemonHost()
	proxy, ok := configFile.Proxies[host]

	if !ok {
		proxy = configFile.Proxies["default"]
	}

	m := map[string]string{}

	if v := proxy.HTTPProxy; v != "" {
		m["HTTP_PROXY"] = v
	}

	if v := proxy.HTTPSProxy; v != "" {
		m["HTTPS_PROXY"] = v
	}

	if v := proxy.NoProxy; v != "" {
		m["NO_PROXY"] = v
	}

	if v := proxy.FTPProxy; v != "" {
		m["FTP_PROXY"] = v
	}

	return m
}

// This is based on ConfigDir from github.com/docker/buildx/util/confutil/config.go
func buildxConfigDir(configFile *configfile.ConfigFile) string {
	if buildxConfig := os.Getenv("BUILDX_CONFIG"); buildxConfig != "" {
		return buildxConfig
	}

	return filepath.Join(filepath.Dir(configFile.Filename), "buildx")
}

type buildxDockerAPI struct {
	docker *client.Client
}

func (a *buildxDockerAPI) DockerAPI(_ string) (client.APIClient, error) {
	return a.docker, nil
}

type buildKitProgressCallback struct {
	progressCallback           *imageBuildProgressCallback
	vertexDigestsToStepNumbers map[digest.Digest]int64
	completedVertices          map[digest.Digest]struct{}
	callbackError              error
}

func newBuildKitProgressCallback(
	onProgressUpdate BuildImageProgressCallback,
	onProgressUpdateUserData unsafe.Pointer,
) *buildKitProgressCallback {
	return &buildKitProgressCallback{
		progressCallback:           newImageBuildProgressCallback(onProgressUpdate, onProgressUpdateUserData),
		vertexDigestsToStepNumbers: map[digest.Digest]int64{},
		completedVertices:          map[digest.Digest]struct{}{},
		callbackError:              nil,
	}
}

func (t *buildKitProgressCallback) onStatusUpdate(status *buildkitclient.SolveStatus) {
	if t.callbackError != nil {
		return
	}

	t.callbackError = t.sendProgressUpdateNotifications(status)
}

func (t *buildKitProgressCallback) sendProgressUpdateNotifications(status *buildkitclient.SolveStatus) error {
	processedStatuses := map[*buildkitclient.VertexStatus]struct{}{}
	processedLogs := map[*buildkitclient.VertexLog]struct{}{}

	for _, v := range status.Vertexes {
		if err := t.sendVertexNotifications(v, status, processedStatuses, processedLogs); err != nil {
			return err
		}
	}

	for _, s := range status.Statuses {
		if _, alreadyProcessed := processedStatuses[s]; !alreadyProcessed {
			if err := t.sendStatusNotification(s); err != nil {
				return err
			}
		}
	}

	for _, l := range status.Logs {
		if _, alreadyProcessed := processedLogs[l]; !alreadyProcessed {
			if err := t.sendLogNotification(l); err != nil {
				return err
			}
		}
	}

	return nil
}

func (t *buildKitProgressCallback) sendVertexNotifications(
	v *buildkitclient.Vertex,
	status *buildkitclient.SolveStatus,
	processedStatuses map[*buildkitclient.VertexStatus]struct{},
	processedLogs map[*buildkitclient.VertexLog]struct{},
) error {
	if !t.haveSeenVertex(v) && v.Started != nil {
		t.allocateStepNumber(v)

		if err := t.sendVertexStartedNotification(v); err != nil {
			return err
		}
	}

	if t.haveAlreadySeenVertexCompleted(v) && v.Completed == nil {
		// Step is restarting: sometimes BuildKit marks a vertex as complete, then starts it again.
		delete(t.completedVertices, v.Digest)

		if err := t.sendVertexStartedNotification(v); err != nil {
			return err
		}
	}

	for _, s := range status.Statuses {
		if s.Vertex == v.Digest {
			processedStatuses[s] = struct{}{}

			if err := t.sendStatusNotification(s); err != nil {
				return err
			}
		}
	}

	for _, l := range status.Logs {
		if l.Vertex == v.Digest {
			processedLogs[l] = struct{}{}

			if err := t.sendLogNotification(l); err != nil {
				return err
			}
		}
	}

	if v.Error != "" {
		if err := t.progressCallback.onBuildFailed(v.Error); err != nil {
			return err
		}
	}

	if v.Completed != nil && !t.haveAlreadySeenVertexCompleted(v) {
		if err := t.sendVertexCompleteNotification(v); err != nil {
			return err
		}
	}

	return nil
}

func (t *buildKitProgressCallback) sendVertexStartedNotification(v *buildkitclient.Vertex) error {
	stepNumber := t.getStepNumberForDigest(v.Digest)

	if err := t.progressCallback.onStepStarting(stepNumber, v.Name); err != nil {
		return err
	}

	return nil
}

func (t *buildKitProgressCallback) getStepNumberForDigest(d digest.Digest) int64 {
	stepNumber, ok := t.vertexDigestsToStepNumbers[d]

	if !ok {
		panic(fmt.Sprintf("no step number found for digest '%s'", d))
	}

	return stepNumber
}

func (t *buildKitProgressCallback) sendVertexCompleteNotification(v *buildkitclient.Vertex) error {
	stepNumber := t.getStepNumberForDigest(v.Digest)
	t.completedVertices[v.Digest] = struct{}{}

	if err := t.progressCallback.onStepFinished(stepNumber); err != nil {
		return err
	}

	return nil
}

func (t *buildKitProgressCallback) sendLogNotification(l *buildkitclient.VertexLog) error {
	stepNumber := t.getStepNumberForDigest(l.Vertex)

	if err := t.progressCallback.onStepOutput(stepNumber, string(l.Data)); err != nil {
		return err
	}

	return nil
}

func (t *buildKitProgressCallback) sendStatusNotification(s *buildkitclient.VertexStatus) error {
	stepNumber := t.getStepNumberForDigest(s.Vertex)

	if s.Name == "transferring" {
		if err := t.progressCallback.onContextUploadProgress(stepNumber, s.Current); err != nil {
			return err
		}
	} else if s.Name != "" {
		cleanID := strings.TrimPrefix(s.ID, "extracting ")

		progressDetail := newPullImageProgressDetail(s.Current, s.Total)
		progressUpdate := newPullImageProgressUpdate(s.Name, progressDetail, cleanID)

		if err := t.progressCallback.onImagePullProgress(stepNumber, progressUpdate); err != nil {
			return err
		}
	}

	return nil
}

func (t *buildKitProgressCallback) haveSeenVertex(v *buildkitclient.Vertex) bool {
	_, haveSeen := t.vertexDigestsToStepNumbers[v.Digest]

	return haveSeen
}

func (t *buildKitProgressCallback) allocateStepNumber(v *buildkitclient.Vertex) int64 {
	stepNumber := int64(len(t.vertexDigestsToStepNumbers)) + 1
	t.vertexDigestsToStepNumbers[v.Digest] = stepNumber

	return stepNumber
}

func (t *buildKitProgressCallback) haveAlreadySeenVertexCompleted(v *buildkitclient.Vertex) bool {
	_, present := t.completedVertices[v.Digest]

	return present
}
