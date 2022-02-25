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

	"github.com/docker/docker/api/types/filters"
	"github.com/docker/docker/api/types/volume"
)

//export CreateVolume
func CreateVolume(clientHandle DockerClientHandle, name *C.char) CreateVolumeReturn {
	docker := clientHandle.DockerAPIClient()

	request := volume.VolumeCreateBody{
		Name: C.GoString(name),
	}

	dockerResponse, err := docker.VolumeCreate(context.Background(), request)

	if err != nil {
		return newCreateVolumeReturn(nil, toError(err))
	}

	response := newVolumeReference(dockerResponse.Name)

	return newCreateVolumeReturn(response, nil)
}

//export DeleteVolume
func DeleteVolume(clientHandle DockerClientHandle, name *C.char) Error {
	docker := clientHandle.DockerAPIClient()

	err := docker.VolumeRemove(context.Background(), C.GoString(name), false)

	return toError(err)
}

//export ListAllVolumes
func ListAllVolumes(clientHandle DockerClientHandle) ListAllVolumesReturn {
	docker := clientHandle.DockerAPIClient()

	dockerResponse, err := docker.VolumeList(context.Background(), filters.NewArgs())

	if err != nil {
		return newListAllVolumesReturn(nil, toError(err))
	}

	volumes := make([]VolumeReference, 0, len(dockerResponse.Volumes))

	for _, v := range dockerResponse.Volumes {
		volumes = append(volumes, newVolumeReference(v.Name))
	}

	return newListAllVolumesReturn(volumes, nil)
}
