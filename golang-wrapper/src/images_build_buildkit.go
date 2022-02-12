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

import "C"
import (
	"context"
	"encoding/json"
	"fmt"
	"io"
	"net"
	"path/filepath"
	"unsafe"

	"github.com/batect/docker-client/golang-wrapper/src/buildkit"
	"github.com/docker/cli/cli/config/configfile"
	"github.com/docker/docker/api/types"
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
	github_com_opencontainers_go_digest "github.com/opencontainers/go-digest"
	fsutiltypes "github.com/tonistiigi/fsutil/types"
	"golang.org/x/sync/errgroup"
)

func buildImageWithBuildKitBuilder(
	clientHandle DockerClientHandle,
	request *imageBuildRequest,
	outputStreamHandle OutputStreamHandle,
	onProgressUpdate BuildImageProgressCallback,
	callbackUserData unsafe.Pointer,
) BuildImageReturn {
	// TODO: check daemon supports BuildKit and fail early if it doesn't

	docker := getDockerAPIClient(clientHandle)
	configFile := getClientConfigFile(clientHandle)
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

	authProvider.(loggingAttachable).SetLogger(func(s *buildkitclient.SolveStatus) {
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
	imageID, err := parser.Parse(response, tracer)

	if err != nil {
		return "", err
	}

	return imageID, nil
}

func waitForSuccessOrCancelBuild(ctx context.Context, docker *client.Client, opts types.ImageBuildOptions, done chan struct{}) error {
	select {
	case <-ctx.Done():
		// We deliberately don't use 'ctx' as the context below - otherwise, the already cancelled context might interfere with the API call to cancel the build.
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
	tracer.Start()
	defer tracer.Stop()

	p.imageID = ""
	output := getOutputStream(p.outputStreamHandle)

	writeAux := func(msg jsonmessage.JSONMessage) {
		switch msg.ID {
		case "moby.image.id":
			var result types.BuildResult

			if err := json.Unmarshal(*msg.Aux, &result); err != nil {
				// TODO: handle error
			}

			p.imageID = result.ID
		case "moby.buildkit.trace":
			tracer.LogJSONMessage(msg)
		}
	}

	if err := jsonmessage.DisplayJSONMessagesStream(response.Body, output, output.FD(), false, writeAux); err != nil {
		return "", err
	}

	return p.imageID, nil
}

type buildKitBuildTracer struct {
	displayCh          chan *buildkitclient.SolveStatus
	eg                 *errgroup.Group
	outputStreamHandle OutputStreamHandle
	progressCallback   *imageBuildProgressCallback
	startedVertices    map[github_com_opencontainers_go_digest.Digest]interface{}
}

func newBuildKitBuildTracer(outputStreamHandle OutputStreamHandle, eg *errgroup.Group, onProgressUpdate BuildImageProgressCallback, onProgressUpdateUserData unsafe.Pointer) *buildKitBuildTracer {
	return &buildKitBuildTracer{
		displayCh:          make(chan *buildkitclient.SolveStatus),
		eg:                 eg,
		outputStreamHandle: outputStreamHandle,
		progressCallback:   newImageBuildProgressCallback(onProgressUpdate, onProgressUpdateUserData),
		startedVertices:    map[github_com_opencontainers_go_digest.Digest]interface{}{},
	}
}

func (t *buildKitBuildTracer) Start() {
	t.eg.Go(func() error {
		return t.run()
	})
}

func (t *buildKitBuildTracer) run() error {
	output := getOutputStream(t.outputStreamHandle)

	// We deliberately don't use the build operation's context as the context below - otherwise, error messages might not be printed after the context is cancelled.
	return progressui.DisplaySolveStatus(context.Background(), "", nil, output, t.displayCh)
}

func (t *buildKitBuildTracer) Stop() {
	close(t.displayCh)
}

func (t *buildKitBuildTracer) LogStatus(s *buildkitclient.SolveStatus) {
	t.displayCh <- s
}

func (t *buildKitBuildTracer) LogJSONMessage(msg jsonmessage.JSONMessage) {
	var dt []byte

	if err := json.Unmarshal(*msg.Aux, &dt); err != nil {
		return // The Docker CLI ignores all errors here, so we do the same.
	}

	var resp controlapi.StatusResponse

	if err := (&resp).Unmarshal(dt); err != nil {
		return // The Docker CLI ignores all errors here, so we do the same.
	}

	t.print(resp)
	t.sendProgressUpdateNotifications(resp)
}

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

func (t *buildKitBuildTracer) sendProgressUpdateNotifications(resp controlapi.StatusResponse) {
	for _, v := range resp.Vertexes {
		if !t.haveSeenVertex(v) {
			t.startedVertices[v.Digest] = nil
			t.progressCallback.onStepStarting(int64(len(t.startedVertices)), v.Name) // TODO: handle error
		}
	}
}

func (t *buildKitBuildTracer) haveSeenVertex(v *controlapi.Vertex) bool {
	_, haveSeen := t.startedVertices[v.Digest]

	return haveSeen
}
