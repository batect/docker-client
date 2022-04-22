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

	"github.com/docker/docker/api/types"
	"github.com/docker/docker/client"
)

//export CreateNetwork
func CreateNetwork(clientHandle DockerClientHandle, name *C.char, driver *C.char) CreateNetworkReturn {
	docker := clientHandle.DockerAPIClient()

	opts := types.NetworkCreate{
		CheckDuplicate: true,
		Driver:         C.GoString(driver),
	}

	dockerResponse, err := docker.NetworkCreate(context.Background(), C.GoString(name), opts)

	if err != nil {
		return newCreateNetworkReturn(nil, toError(err))
	}

	response := newNetworkReference(dockerResponse.ID)

	return newCreateNetworkReturn(response, nil)
}

//export DeleteNetwork
func DeleteNetwork(clientHandle DockerClientHandle, id *C.char) Error {
	docker := clientHandle.DockerAPIClient()

	err := docker.NetworkRemove(context.Background(), C.GoString(id))

	return toError(err)
}

//export GetNetworkByNameOrID
func GetNetworkByNameOrID(clientHandle DockerClientHandle, searchFor *C.char) GetNetworkByNameOrIDReturn {
	docker := clientHandle.DockerAPIClient()

	dockerResponse, err := docker.NetworkInspect(context.Background(), C.GoString(searchFor), types.NetworkInspectOptions{})

	if err != nil {
		if client.IsErrNotFound(err) {
			return newGetNetworkByNameOrIDReturn(nil, nil)
		}

		return newGetNetworkByNameOrIDReturn(nil, toError(err))
	}

	response := newNetworkReference(dockerResponse.ID)

	return newGetNetworkByNameOrIDReturn(response, nil)
}
