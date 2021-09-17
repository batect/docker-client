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
)

//export Ping
func Ping(clientHandle DockerClientHandle) PingReturn {
	docker := getClient(clientHandle)

	dockerResponse, err := docker.Ping(context.Background())

	if err != nil {
		return newPingReturn(nil, toError(err))
	}

	response := newPingResponse(
		dockerResponse.APIVersion,
		dockerResponse.OSType,
		dockerResponse.Experimental,
		string(dockerResponse.BuilderVersion),
	)

	return newPingReturn(response, nil)
}

//export GetDaemonVersionInformation
func GetDaemonVersionInformation(clientHandle DockerClientHandle) GetDaemonVersionInformationReturn {
	docker := getClient(clientHandle)

	dockerResponse, err := docker.ServerVersion(context.Background())

	if err != nil {
		return newGetDaemonVersionInformationReturn(nil, toError(err))
	}

	response := newDaemonVersionInformation(
		dockerResponse.Version,
		dockerResponse.APIVersion,
		dockerResponse.MinAPIVersion,
		dockerResponse.GitCommit,
		dockerResponse.Os,
		dockerResponse.Arch,
		dockerResponse.Experimental,
	)

	return newGetDaemonVersionInformationReturn(response, nil)
}