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
	"fmt"
	"net"
	"net/http"
	"os"
	"sync"
	"time"

	"github.com/docker/cli/cli/config"
	"github.com/docker/cli/cli/config/configfile"
	"github.com/docker/cli/cli/config/credentials"
	"github.com/docker/docker/client"

	"github.com/docker/go-connections/tlsconfig"
)

//nolint:gochecknoglobals
var (
	clients               = map[uint64]*client.Client{}
	configFilesForClients = map[uint64]*configfile.ConfigFile{}
	clientsLock           = sync.RWMutex{}
	nextClientIndex uint64 = 0
)

//export CreateClient
func CreateClient(cfg *C.ClientConfiguration) CreateClientReturn {
	var opts []client.Opt

	if cfg.UseConfigurationFromEnvironment {
		opts = append(opts, client.FromEnv)
	}

	if cfg.Host != nil {
		opts = append(opts, client.WithHost(C.GoString(cfg.Host)))
	}

	if cfg.TLS != nil {
		opts = append(opts, withTLSClientConfig(
			C.GoString(cfg.TLS.CAFilePath),
			C.GoString(cfg.TLS.CertFilePath),
			C.GoString(cfg.TLS.KeyFilePath),
			bool(cfg.TLS.InsecureSkipVerify),
		))
	}

	c, err := client.NewClientWithOpts(opts...)

	if err != nil {
		return newCreateClientReturn(0, toError(err))
	}

	configDir := config.Dir()

	if cfg.ConfigDirectoryPath != nil {
		configDir = C.GoString(cfg.ConfigDirectoryPath)

		if !directoryExists(configDir) {
			// nolint:goerr113
			return newCreateClientReturn(0, toError(fmt.Errorf("configuration directory '%s' does not exist or is not a directory", configDir)))
		}
	}

	configFile, err := loadConfigFile(configDir)

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

// The Docker client library does not expose a version of WithTLSClientConfig that allows us to set
// InsecureSkipVerify. So this is a mish-mash of that function and cli/context/docker.withHTTPConfig()
func withTLSClientConfig(caCertPath, certPath, keyPath string, insecureSkipVerify bool) client.Opt {
	return func(c *client.Client) error {
		opts := tlsconfig.Options{
			CAFile:             caCertPath,
			CertFile:           certPath,
			KeyFile:            keyPath,
			ExclusiveRootPools: true,
			InsecureSkipVerify: insecureSkipVerify,
		}

		tlsConfig, err := tlsconfig.Client(opts)

		if err != nil {
			return fmt.Errorf("failed to create TLS config: %w", err)
		}

		httpClient := &http.Client{
			Transport: &http.Transport{
				TLSClientConfig: tlsConfig,
				DialContext: (&net.Dialer{
					KeepAlive: 30 * time.Second,
					Timeout:   30 * time.Second,
				}).DialContext,
			},
			CheckRedirect: client.CheckRedirect,
		}

		return client.WithHTTPClient(httpClient)(c)
	}
}

func directoryExists(path string) bool {
	s, err := os.Stat(path)

	if err == nil {
		return s.IsDir()
	}

	return false
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

func loadConfigFile(configPath string) (*configfile.ConfigFile, error) {
	configFile, err := config.Load(configPath)

	if err != nil {
		return nil, err
	}

	if !configFile.ContainsAuth() {
		configFile.CredentialsStore = credentials.DetectDefaultStore(configFile.CredentialsStore)
	}

	return configFile, nil
}
