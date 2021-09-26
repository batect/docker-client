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

	"github.com/docker/cli/cli/command"
	"github.com/docker/cli/cli/trust"
	"github.com/docker/distribution/reference"
	"github.com/docker/docker/api/types"
	registrytypes "github.com/docker/docker/api/types/registry"
	"github.com/docker/docker/client"
	"github.com/docker/docker/pkg/jsonmessage"
	"github.com/docker/docker/registry"
)

//export PullImage
func PullImage(clientHandle DockerClientHandle, ref *C.char) PullImageReturn {
	docker := getClient(clientHandle)

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
		RegistryAuth:  encodedAuth,
		All:           false,
	}

	cleanedReference := reference.FamiliarString(imgRefAndAuth.Reference())
	responseBody, err := docker.ImagePull(context.Background(), cleanedReference, options)

	if err != nil {
		return newPullImageReturn(nil, toError(err))
	}

	defer responseBody.Close()

	err = parsePullResponseBody(responseBody, func(message jsonmessage.JSONMessage) error {
		// TODO: callback with progress information
		// TODO: capture digest and use this for getting the correct image reference below
		return nil
	})

	if err != nil {
		return newPullImageReturn(nil, toError(err))
	}

	reference, err := getImageReference(docker, distributionRef.String())

	if err != nil {
		return newPullImageReturn(nil, toError(fmt.Errorf("could not get image reference after pulling image: %w", err)))
	}

	return newPullImageReturn(reference, nil)
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
	docker := getClient(clientHandle)
	info, err := docker.Info(ctx)

	if err != nil || info.IndexServerAddress == "" {
		return registry.IndexServer
	}

	return info.IndexServerAddress
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
			return errors.New(message.Error.Message)
		}

		if message.ErrorMessage != "" {
			return errors.New(message.ErrorMessage)
		}

		if err := callback(message); err != nil {
			return err
		}
	}
}

//export DeleteImage
func DeleteImage(clientHandle DockerClientHandle, ref *C.char) Error {
	docker := getClient(clientHandle)

	_, err := docker.ImageRemove(context.Background(), C.GoString(ref), types.ImageRemoveOptions{})

	if err != nil {
		return toError(err)
	}

	return nil
}

//export GetImage
func GetImage(clientHandle DockerClientHandle, ref *C.char) GetImageReturn {
	docker := getClient(clientHandle)

	response, err := getImageReference(docker, C.GoString(ref))

	if err != nil {
		if client.IsErrNotFound(err) {
			return newGetImageReturn(nil, nil)
		}

		return newGetImageReturn(nil, toError(err))
	}

	return newGetImageReturn(response, nil)
}

func getImageReference(docker *client.Client, reference string) (ImageReference, error) {
	dockerResponse, _, err := docker.ImageInspectWithRaw(context.Background(), reference)

	if err != nil {
		return nil, err
	}

	return newImageReference(dockerResponse.ID), nil
}
