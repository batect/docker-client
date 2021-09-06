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
	"fmt"
	"sync"

	"github.com/docker/docker/client"
)

//nolint:gochecknoglobals
var (
	clients = map[uint64]*client.Client{}
	clientsLock = sync.RWMutex{}
	nextClientIndex uint64 = 0
)

//export CreateClient
func CreateClient() CreateClientReturn {
	c, err := client.NewClientWithOpts()

	if err != nil {
		return newCreateClientReturn(0, toError(err))
	}

	clientsLock.Lock()
	defer clientsLock.Unlock()

	clientIndex := nextClientIndex
	clients[clientIndex] = c
	nextClientIndex++

	return newCreateClientReturn(DockerClient(clientIndex) , nil)
}

//export DisposeClient
func DisposeClient(clientHandle DockerClient) {
	clientsLock.Lock()
	defer clientsLock.Unlock()

	delete(clients, uint64(clientHandle))
}

//export Ping
func Ping(clientHandle DockerClient) PingReturn {
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

func getClient(clientHandle DockerClient) *client.Client {
	clientsLock.RLock()
	defer clientsLock.RUnlock()

	return clients[uint64(clientHandle)]
}

func toError(err error) Error {
	if err == nil {
		return nil
	}

	return newError(
		fmt.Sprintf("%T", err),
		err.Error(),
	)
}
