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

import "C"
import (
	"context"
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"net"
	"path/filepath"
	"sort"
	"strconv"
	"strings"
	"unsafe"

	"github.com/batect/docker-client/golang-wrapper/src/buildkit"
	"github.com/docker/cli/cli/config/configfile"
	"github.com/docker/docker/api/types"
	"github.com/docker/docker/api/types/versions"
	"github.com/docker/docker/client"
	"github.com/docker/docker/pkg/jsonmessage"
	"github.com/docker/docker/pkg/stringid"
	controlapi "github.com/moby/buildkit/api/services/control"
	buildkitclient "github.com/moby/buildkit/client"
	"github.com/moby/buildkit/session"
	"github.com/moby/buildkit/session/auth/authprovider"
	"github.com/moby/buildkit/session/filesync"
	"github.com/moby/buildkit/util/progress/progressui"
	"github.com/moby/buildkit/util/progress/progresswriter"
	digest "github.com/opencontainers/go-digest"
	fsutiltypes "github.com/tonistiigi/fsutil/types"
	"golang.org/x/sync/errgroup"
)

var errDockerAuthProviderDoesNotSupportLogging = errors.New("DockerAuthProvider does not support logging")

func buildImageWithBuildKitBuilder(
	clientHandle DockerClientHandle,
	request *imageBuildRequest,
	outputStreamHandle OutputStreamHandle,
	onProgressUpdate BuildImageProgressCallback,
	callbackUserData unsafe.Pointer,
) BuildImageReturn {
	if supported, err := supportsBuildKit(clientHandle); err != nil {
		return newBuildImageReturn(nil, toError(err))
	} else if !supported {
		return newBuildImageReturn(nil, toError(ErrBuildKitNotSupported))
	}

	docker := clientHandle.DockerAPIClient()
	configFile := clientHandle.ClientConfigFile()
	eg, ctx := errgroup.WithContext(context.TODO())
	tracer := newBuildKitBuildTracer(outputStreamHandle, eg, onProgressUpdate, callbackUserData)
	sess, err := createSession(request, tracer)

	if err != nil {
		return newBuildImageReturn(nil, toError(err))
	}

	eg.Go(func() error {
		return runSession(ctx, sess, docker)
	})

	imageID := ""

	eg.Go(func() error {
		defer sess.Close()

		opts := createBuildKitImageBuildOptions(docker, sess, configFile, request)

		var err error
		imageID, err = runBuild(ctx, eg, docker, opts, tracer, outputStreamHandle)

		return err
	})

	if err := eg.Wait(); err != nil {
		return newBuildImageReturn(nil, toError(err))
	}

	return newBuildImageReturn(newImageReference(imageID), nil)
}

// This is based on isSessionSupported from github.com/docker/cli/cli/command/image/build_session.go
func supportsBuildKit(clientHandle DockerClientHandle) (bool, error) {
	docker := clientHandle.DockerAPIClient()

	if versions.GreaterThanOrEqualTo(docker.ClientVersion(), "1.39") {
		return true, nil
	}

	serverInfo, err := clientHandle.ServerInfo()

	if err != nil {
		return false, err
	}

	return serverInfo.HasExperimental && versions.GreaterThanOrEqualTo(docker.ClientVersion(), "1.31"), nil
}

type loggingAttachable interface {
	SetLogger(progresswriter.Logger)
}

// This function is based on trySession() from github.com/docker/cli/command/image/build_session.go
func createSession(request *imageBuildRequest, tracer *buildKitBuildTracer) (*session.Session, error) {
	sharedKey := buildkit.GetBuildSharedKey(request.ContextDirectory)

	sess, err := session.NewSession(context.Background(), filepath.Base(request.ContextDirectory), sharedKey)

	if err != nil {
		return nil, fmt.Errorf("failed to create session: %w", err)
	}

	dockerfileDir := filepath.Dir(request.PathToDockerfile)

	sess.Allow(filesync.NewFSSyncProvider([]filesync.SyncedDir{
		{
			Name: "context",
			Dir:  request.ContextDirectory,
			Map:  resetUIDAndGID,
		},
		{
			Name: "dockerfile",
			Dir:  dockerfileDir,
		},
	}))

	// At the time of writing, this Writer is just used for warning messages when loading the local config file, so we can safely ignore these messages.
	authProvider := authprovider.NewDockerAuthProvider(io.Discard)
	authProviderLogger, authProviderSupportsLogging := authProvider.(loggingAttachable)

	if !authProviderSupportsLogging {
		return nil, errDockerAuthProviderDoesNotSupportLogging
	}

	authProviderLogger.SetLogger(func(s *buildkitclient.SolveStatus) {
		tracer.LogStatus(s)
	})

	sess.Allow(authProvider)

	return sess, nil
}

func resetUIDAndGID(path string, stat *fsutiltypes.Stat) bool {
	stat.Uid = 0
	stat.Gid = 0

	return true
}

func runSession(ctx context.Context, sess *session.Session, docker *client.Client) error {
	dialSession := func(ctx context.Context, proto string, meta map[string][]string) (net.Conn, error) {
		return docker.DialHijack(ctx, "/session", proto, meta)
	}

	return sess.Run(ctx, dialSession)
}

func createBuildKitImageBuildOptions(docker *client.Client, sess *session.Session, configFile *configfile.ConfigFile, request *imageBuildRequest) types.ImageBuildOptions {
	dockerfileName := filepath.Base(request.PathToDockerfile)

	opts := createImageBuildOptions(docker, configFile, dockerfileName, request)
	opts.Version = types.BuilderBuildKit
	opts.Dockerfile = dockerfileName
	opts.RemoteContext = "client-session"
	opts.SessionID = sess.ID()
	opts.BuildID = stringid.GenerateRandomID()

	return opts
}

func runBuild(ctx context.Context, eg *errgroup.Group, docker *client.Client, opts types.ImageBuildOptions, tracer *buildKitBuildTracer, outputStreamHandle OutputStreamHandle) (string, error) {
	response, err := docker.ImageBuild(ctx, nil, opts)

	if err != nil {
		return "", err
	}

	defer response.Body.Close()

	done := make(chan struct{})
	defer close(done)

	eg.Go(func() error {
		return waitForSuccessOrCancelBuild(ctx, docker, opts, done)
	})

	parser := newBuildKitImageBuildResponseBodyParser(outputStreamHandle)
	imageID, err := parser.Parse(response, tracer) //nolint:contextcheck

	if err != nil {
		return "", err
	}

	return imageID, nil
}

func waitForSuccessOrCancelBuild(ctx context.Context, docker *client.Client, opts types.ImageBuildOptions, done chan struct{}) error {
	select {
	case <-ctx.Done():
		//nolint:contextcheck // We deliberately don't use 'ctx' as the context below - otherwise, the already cancelled context might interfere with the API call to cancel the build.
		return docker.BuildCancel(context.Background(), opts.BuildID)
	case <-done:
	}

	return nil
}

type buildKitImageBuildResponseBodyParser struct {
	imageID            string
	outputStreamHandle OutputStreamHandle
}

func newBuildKitImageBuildResponseBodyParser(outputStreamHandle OutputStreamHandle) *buildKitImageBuildResponseBodyParser {
	return &buildKitImageBuildResponseBodyParser{
		outputStreamHandle: outputStreamHandle,
	}
}

func (p *buildKitImageBuildResponseBodyParser) Parse(response types.ImageBuildResponse, tracer *buildKitBuildTracer) (string, error) {
	tracer.Start() //nolint:contextcheck
	defer tracer.Stop()

	p.imageID = ""

	processMessage := func(msg jsonmessage.JSONMessage) error {
		switch msg.ID {
		case "moby.image.id":
			var result types.BuildResult

			if err := json.Unmarshal(*msg.Aux, &result); err != nil {
				return err
			}

			p.imageID = result.ID
		case "moby.buildkit.trace":
			if err := tracer.LogJSONMessage(msg); err != nil {
				return err
			}
		}

		if msg.Error != nil {
			if err := tracer.progressCallback.onBuildFailed(msg.Error.Message); err != nil {
				return err
			}
		}

		return nil
	}

	// We use io.Discard below because all output is handled by BuildKit's trace messages.
	if err := parseAndDisplayJSONMessagesStream(response.Body, io.Discard, processMessage); err != nil {
		return "", err
	}

	return p.imageID, nil
}

type buildKitBuildTracer struct {
	displayCh                  chan *buildkitclient.SolveStatus
	eg                         *errgroup.Group
	outputStreamHandle         OutputStreamHandle
	progressCallback           *imageBuildProgressCallback
	vertexDigestsToStepNumbers map[digest.Digest]int64
	completedVertices          map[digest.Digest]interface{}
}

func newBuildKitBuildTracer(outputStreamHandle OutputStreamHandle, eg *errgroup.Group, onProgressUpdate BuildImageProgressCallback, onProgressUpdateUserData unsafe.Pointer) *buildKitBuildTracer {
	return &buildKitBuildTracer{
		displayCh:                  make(chan *buildkitclient.SolveStatus),
		eg:                         eg,
		outputStreamHandle:         outputStreamHandle,
		progressCallback:           newImageBuildProgressCallback(onProgressUpdate, onProgressUpdateUserData),
		vertexDigestsToStepNumbers: map[digest.Digest]int64{},
		completedVertices:          map[digest.Digest]interface{}{},
	}
}

func (t *buildKitBuildTracer) Start() {
	t.eg.Go(func() error {
		//nolint:contextcheck // We deliberately don't use the build operation's context as the context below - otherwise, error messages might not be printed after the context is cancelled.
		return t.run()
	})
}

func (t *buildKitBuildTracer) run() error {
	output := t.outputStreamHandle.OutputStream()

	// We deliberately don't use the build operation's context as the context below - otherwise, error messages might not be printed after the context is cancelled.
	return progressui.DisplaySolveStatus(context.Background(), "", nil, output, t.displayCh)
}

func (t *buildKitBuildTracer) Stop() {
	close(t.displayCh)
}

func (t *buildKitBuildTracer) LogStatus(s *buildkitclient.SolveStatus) {
	t.displayCh <- s
}

func (t *buildKitBuildTracer) LogJSONMessage(msg jsonmessage.JSONMessage) error {
	var dt []byte

	if err := json.Unmarshal(*msg.Aux, &dt); err != nil {
		return err
	}

	var resp controlapi.StatusResponse

	if err := (&resp).Unmarshal(dt); err != nil {
		return err
	}

	sortVerticesForDisplay(resp.Vertexes)
	t.print(resp)

	return t.sendProgressUpdateNotifications(resp)
}

//nolint:forbidigo // False positive.
func (t *buildKitBuildTracer) print(resp controlapi.StatusResponse) {
	s := buildkitclient.SolveStatus{}

	for _, v := range resp.Vertexes {
		s.Vertexes = append(s.Vertexes, &buildkitclient.Vertex{
			Digest:    v.Digest,
			Inputs:    v.Inputs,
			Name:      v.Name,
			Started:   v.Started,
			Completed: v.Completed,
			Error:     v.Error,
			Cached:    v.Cached,
		})
	}

	for _, v := range resp.Statuses {
		s.Statuses = append(s.Statuses, &buildkitclient.VertexStatus{
			ID:        v.ID,
			Vertex:    v.Vertex,
			Name:      v.Name,
			Total:     v.Total,
			Current:   v.Current,
			Timestamp: v.Timestamp,
			Started:   v.Started,
			Completed: v.Completed,
		})
	}

	for _, v := range resp.Logs {
		s.Logs = append(s.Logs, &buildkitclient.VertexLog{
			Vertex:    v.Vertex,
			Stream:    int(v.Stream),
			Data:      v.Msg,
			Timestamp: v.Timestamp,
		})
	}

	t.displayCh <- &s
}

func sortVerticesForDisplay(vertices []*controlapi.Vertex) {
	sort.Stable(&vertexSortingInterface{vertices})
}

type vertexSortingInterface struct {
	vertices []*controlapi.Vertex
}

func (v *vertexSortingInterface) Len() int {
	return len(v.vertices)
}

const (
	preserveOrder    = false // Provided we use this with sort.Stable, this will retain the existing order.
	firstSortsFirst  = true
	secondSortsFirst = false
)

func (v *vertexSortingInterface) Less(i, j int) bool {
	first := v.vertices[i]
	second := v.vertices[j]

	if vertexReliesOnDigest(first, second.Digest) {
		return secondSortsFirst
	}

	if vertexReliesOnDigest(second, first.Digest) {
		return firstSortsFirst
	}

	firstHasStepNumbering := strings.HasPrefix(first.Name, "[")
	secondHasStepNumbering := strings.HasPrefix(second.Name, "[")

	switch {
	case !firstHasStepNumbering && !secondHasStepNumbering:
		return preserveOrder
	case firstHasStepNumbering && !secondHasStepNumbering:
		return firstSortsFirst
	case !firstHasStepNumbering && secondHasStepNumbering:
		return secondSortsFirst
	}

	firstIsInternalStep := strings.HasPrefix(first.Name, "[internal] ")
	secondIsInternalStep := strings.HasPrefix(second.Name, "[internal] ")

	switch {
	case firstIsInternalStep && secondIsInternalStep:
		return v.sortInternalSteps(first.Name, second.Name)
	case firstIsInternalStep && !secondIsInternalStep:
		return firstSortsFirst
	case !firstIsInternalStep && secondIsInternalStep:
		return secondSortsFirst
	}

	firstStageName, firstStep := v.extractStageNameAndStepNumber(first.Name)
	secondStageName, secondStep := v.extractStageNameAndStepNumber(second.Name)

	if firstStageName != secondStageName {
		return preserveOrder
	}

	return firstStep < secondStep
}

func (v *vertexSortingInterface) sortInternalSteps(firstName string, secondName string) bool {
	loadDockerfile := "[internal] load build definition "

	if strings.HasPrefix(firstName, loadDockerfile) {
		return firstSortsFirst
	}

	if strings.HasPrefix(secondName, loadDockerfile) {
		return secondSortsFirst
	}

	return preserveOrder
}

func vertexReliesOnDigest(v *controlapi.Vertex, digest digest.Digest) bool {
	for _, d := range v.Inputs {
		if d == digest {
			return true
		}
	}

	return false
}

func (v *vertexSortingInterface) extractStageNameAndStepNumber(vertexName string) (string, int) {
	identifier := vertexName[1:strings.Index(vertexName, "] ")]
	delimiter := strings.LastIndex(identifier, " ")
	stageName := ""
	stepCounter := identifier

	if delimiter != -1 {
		stageName = identifier[0:delimiter]
		stepCounter = identifier[delimiter+1:]
	}

	step, err := strconv.Atoi(stepCounter[0:strings.Index(stepCounter, "/")])

	if err != nil {
		panic(err)
	}

	return stageName, step
}

func (v *vertexSortingInterface) Swap(i, j int) {
	v.vertices[i], v.vertices[j] = v.vertices[j], v.vertices[i]
}

func (t *buildKitBuildTracer) sendProgressUpdateNotifications(resp controlapi.StatusResponse) error {
	processedStatuses := map[*controlapi.VertexStatus]interface{}{}
	processedLogs := map[*controlapi.VertexLog]interface{}{}

	for _, v := range resp.Vertexes {
		if err := t.sendVertexNotifications(v, resp, processedStatuses, processedLogs); err != nil {
			return err
		}
	}

	for _, s := range resp.Statuses {
		if _, alreadyProcessed := processedStatuses[s]; !alreadyProcessed {
			if err := t.sendStatusNotification(s); err != nil {
				return err
			}
		}
	}

	for _, l := range resp.Logs {
		if _, alreadyProcessed := processedLogs[l]; !alreadyProcessed {
			if err := t.sendLogNotification(l); err != nil {
				return err
			}
		}
	}

	return nil
}

func (t *buildKitBuildTracer) sendVertexNotifications(v *controlapi.Vertex, resp controlapi.StatusResponse, processedStatuses map[*controlapi.VertexStatus]interface{}, processedLogs map[*controlapi.VertexLog]interface{}) error {
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

	for _, s := range resp.Statuses {
		if s.Vertex == v.Digest {
			processedStatuses[s] = nil

			if err := t.sendStatusNotification(s); err != nil {
				return err
			}
		}
	}

	for _, l := range resp.Logs {
		if l.Vertex == v.Digest {
			processedLogs[l] = nil

			if err := t.sendLogNotification(l); err != nil {
				return err
			}
		}
	}

	if v.Completed != nil && !t.haveAlreadySeenVertexCompleted(v) {
		if err := t.sendVertexCompleteNotification(v); err != nil {
			return err
		}
	}

	return nil
}

func (t *buildKitBuildTracer) sendVertexStartedNotification(v *controlapi.Vertex) error {
	stepNumber := t.getStepNumberForDigest(v.Digest)

	if err := t.progressCallback.onStepStarting(stepNumber, v.Name); err != nil {
		return err
	}

	return nil
}

func (t *buildKitBuildTracer) getStepNumberForDigest(d digest.Digest) int64 {
	stepNumber, ok := t.vertexDigestsToStepNumbers[d]

	if !ok {
		panic(fmt.Sprintf("no step number found for digest '%s'", d))
	}

	return stepNumber
}

func (t *buildKitBuildTracer) sendVertexCompleteNotification(v *controlapi.Vertex) error {
	stepNumber := t.getStepNumberForDigest(v.Digest)
	t.completedVertices[v.Digest] = nil

	if err := t.progressCallback.onStepFinished(stepNumber); err != nil {
		return err
	}

	return nil
}

func (t *buildKitBuildTracer) sendLogNotification(l *controlapi.VertexLog) error {
	stepNumber := t.getStepNumberForDigest(l.Vertex)

	if err := t.progressCallback.onStepOutput(stepNumber, string(l.Msg)); err != nil {
		return err
	}

	return nil
}

func (t *buildKitBuildTracer) sendStatusNotification(s *controlapi.VertexStatus) error {
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

func (t *buildKitBuildTracer) haveSeenVertex(v *controlapi.Vertex) bool {
	_, haveSeen := t.vertexDigestsToStepNumbers[v.Digest]

	return haveSeen
}

func (t *buildKitBuildTracer) allocateStepNumber(v *controlapi.Vertex) int64 {
	stepNumber := int64(len(t.vertexDigestsToStepNumbers)) + 1
	t.vertexDigestsToStepNumbers[v.Digest] = stepNumber

	return stepNumber
}

func (t *buildKitBuildTracer) haveAlreadySeenVertexCompleted(v *controlapi.Vertex) bool {
	_, present := t.completedVertices[v.Digest]

	return present
}
