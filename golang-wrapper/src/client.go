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
	"sync"

	"github.com/docker/cli/cli/config"
	"github.com/docker/cli/cli/config/configfile"
	"github.com/docker/cli/cli/config/credentials"
	"github.com/docker/docker/client"
)

//nolint:gochecknoglobals
var (
	clients               = map[uint64]*client.Client{}
	configFilesForClients = map[uint64]*configfile.ConfigFile{}
	clientsLock           = sync.RWMutex{}
	nextClientIndex uint64 = 0
)

//export CreateClient
func CreateClient() CreateClientReturn {
	c, err := client.NewClientWithOpts()

	if err != nil {
		return newCreateClientReturn(0, toError(err))
	}

	configFile, err := loadConfigFile()

	if err != nil {
		return newCreateClientReturn(0, toError(err))
	}

	clientsLock.Lock()
	defer clientsLock.Unlock()

	clientIndex := nextClientIndex
	clients[clientIndex] = c
	configFilesForClients[clientIndex] = configFile
	nextClientIndex++

	return newCreateClientReturn(DockerClientHandle(clientIndex) , nil)
}

//export DisposeClient
func DisposeClient(clientHandle DockerClientHandle) Error {
	clientsLock.Lock()
	defer clientsLock.Unlock()

	idx := uint64(clientHandle)

	if _, ok := clients[idx]; !ok {
		return toError(ErrInvalidDockerClientHandle)
	}

	delete(clients, idx)
	delete(configFilesForClients, idx)

	return nil
}

func getClient(clientHandle DockerClientHandle) *client.Client {
	clientsLock.RLock()
	defer clientsLock.RUnlock()

	return clients[uint64(clientHandle)]
}

func getClientConfigFile(clientHandle DockerClientHandle) *configfile.ConfigFile {
	clientsLock.RLock()
	defer clientsLock.RUnlock()

	return configFilesForClients[uint64(clientHandle)]
}

func loadConfigFile() (*configfile.ConfigFile, error) {
	// TODO: allow overriding default config file path
	configFile, err := config.Load(config.Dir())

	if err != nil {
		return nil, err
	}

	if !configFile.ContainsAuth() {
		configFile.CredentialsStore = credentials.DetectDefaultStore(configFile.CredentialsStore)
	}

	return configFile, nil
}
