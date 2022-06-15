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
	/*
		#include "types.h"
	*/
	"C"
	"context"

	"github.com/docker/distribution/reference"
	"github.com/docker/docker/api/types"
	"github.com/docker/docker/client"
)

//export DeleteImage
func DeleteImage(clientHandle DockerClientHandle, contextHandle ContextHandle, ref *C.char, force C.bool) Error {
	docker := clientHandle.DockerAPIClient()
	ctx := contextHandle.Context()

	opts := types.ImageRemoveOptions{
		Force: bool(force),
	}

	_, err := docker.ImageRemove(ctx, C.GoString(ref), opts)

	if err != nil {
		return toError(err)
	}

	return nil
}

//export GetImage
func GetImage(clientHandle DockerClientHandle, contextHandle ContextHandle, ref *C.char) GetImageReturn {
	docker := clientHandle.DockerAPIClient()
	ctx := contextHandle.Context()

	response, err := getImageReference(ctx, docker, C.GoString(ref))

	if err != nil {
		if client.IsErrNotFound(err) {
			return newGetImageReturn(nil, nil)
		}

		return newGetImageReturn(nil, toError(err))
	}

	return newGetImageReturn(response, nil)
}

func getImageReference(ctx context.Context, docker *client.Client, reference string) (ImageReference, error) {
	dockerResponse, _, err := docker.ImageInspectWithRaw(ctx, reference)

	if err != nil {
		return nil, err
	}

	return newImageReference(dockerResponse.ID), nil
}

//export ValidateImageTag
func ValidateImageTag(tag *C.char) Error {
	_, err := reference.ParseNormalizedNamed(C.GoString(tag))

	if err != nil {
		return toError(err)
	}

	return nil
}
