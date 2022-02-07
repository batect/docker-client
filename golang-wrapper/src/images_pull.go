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
	"errors"
	"fmt"
	"io"
	"strings"
	"unsafe"

	"github.com/docker/cli/cli/command"
	"github.com/docker/cli/cli/trust"
	"github.com/docker/distribution/reference"
	"github.com/docker/docker/api/types"
	registrytypes "github.com/docker/docker/api/types/registry"
	"github.com/docker/docker/client"
	"github.com/docker/docker/pkg/jsonmessage"
	"github.com/docker/docker/registry"
	"github.com/opencontainers/go-digest"
)

//export PullImage
func PullImage(clientHandle DockerClientHandle, ref *C.char, onProgressUpdate PullImageProgressCallback, callbackUserData unsafe.Pointer) PullImageReturn {
	docker := getDockerAPIClient(clientHandle)

	distributionRef, err := reference.ParseNormalizedNamed(C.GoString(ref))

	if err != nil {
		return newPullImageReturn(nil, toError(err))
	}

	imgRefAndAuth, err := trust.GetImageReferencesAndAuth(
		context.Background(),
		nil,
		getAuthResolver(clientHandle),
		distributionRef.String(),
	)

	if err != nil {
		return newPullImageReturn(nil, toError(err))
	}

	encodedAuth, err := command.EncodeAuthToBase64(*imgRefAndAuth.AuthConfig())

	if err != nil {
		return newPullImageReturn(nil, toError(err))
	}

	options := types.ImagePullOptions{
		RegistryAuth: encodedAuth,
		All:          false,
	}

	cleanedReference := reference.FamiliarString(imgRefAndAuth.Reference())
	responseBody, err := docker.ImagePull(context.Background(), cleanedReference, options)

	if err != nil {
		return newPullImageReturn(nil, toError(err))
	}

	defer responseBody.Close()

	return processPullResponse(docker, responseBody, distributionRef, onProgressUpdate, callbackUserData)
}

func getAuthResolver(clientHandle DockerClientHandle) func(ctx context.Context, index *registrytypes.IndexInfo) types.AuthConfig {
	return func(ctx context.Context, index *registrytypes.IndexInfo) types.AuthConfig {
		configKey := index.Name

		if index.Official {
			configKey = electAuthServerForOfficialIndex(ctx, clientHandle)
		}

		// The CLI ignores errors, so we do the same.
		auth, _ := getClientConfigFile(clientHandle).GetAuthConfig(configKey)

		return types.AuthConfig(auth)
	}
}

func electAuthServerForOfficialIndex(ctx context.Context, clientHandle DockerClientHandle) string {
	docker := getDockerAPIClient(clientHandle)
	info, err := docker.Info(ctx)

	if err != nil || info.IndexServerAddress == "" {
		return registry.IndexServer
	}

	return info.IndexServerAddress
}

func processPullResponse(docker *client.Client, responseBody io.ReadCloser, originalReference reference.Named, onProgressUpdate PullImageProgressCallback, callbackUserData unsafe.Pointer) PullImageReturn {
	pulledDigest := ""

	err := parsePullResponseBody(responseBody, func(message jsonmessage.JSONMessage) error {
		if strings.HasPrefix(message.Status, "Digest: ") {
			pulledDigest = strings.TrimPrefix(message.Status, "Digest: ")
		}

		var progressDetail PullImageProgressDetail = nil
		defer C.FreePullImageProgressDetail(progressDetail)

		if message.Progress != nil {
			progressDetail = newPullImageProgressDetail(message.Progress.Current, message.Progress.Total)
		}

		progressUpdate := newPullImageProgressUpdate(message.Status, progressDetail, message.ID)
		defer C.FreePullImageProgressUpdate(progressUpdate)

		if !invokePullImageProgressCallback(onProgressUpdate, callbackUserData, progressUpdate) {
			return ErrProgressCallbackFailed
		}

		return nil
	})

	if err != nil {
		return newPullImageReturn(nil, toError(err))
	}

	lookupReference := originalReference

	if pulledDigest != "" {
		lookupReference, err = reference.WithDigest(originalReference, digest.Digest(pulledDigest))

		if err != nil {
			return newPullImageReturn(nil, toError(err))
		}
	}

	ref, err := getImageReference(docker, lookupReference.String())

	if err != nil {
		return newPullImageReturn(nil, toError(fmt.Errorf("could not get image reference after pulling image: %w", err)))
	}

	return newPullImageReturn(ref, nil)
}

func parsePullResponseBody(body io.ReadCloser, callback func(message jsonmessage.JSONMessage) error) error {
	decoder := json.NewDecoder(body)

	for {
		var message jsonmessage.JSONMessage

		if err := decoder.Decode(&message); err != nil {
			if errors.Is(err, io.EOF) {
				return nil
			}

			return err
		}

		if message.Error != nil {
			return errors.New(message.Error.Message) // nolint:goerr113
		}

		if message.ErrorMessage != "" {
			return errors.New(message.ErrorMessage) // nolint:goerr113
		}

		if err := callback(message); err != nil {
			return err
		}
	}
}
