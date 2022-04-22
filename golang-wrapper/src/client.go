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
	"fmt"
	"net"
	"net/http"
	"os"
	"strconv"
	"sync"
	"time"

	"github.com/docker/cli/cli/command"
	"github.com/docker/cli/cli/config"
	"github.com/docker/cli/cli/config/configfile"
	"github.com/docker/cli/cli/config/credentials"
	"github.com/docker/docker/api/types"
	"github.com/docker/docker/client"
	"github.com/pkg/errors"

	"github.com/docker/go-connections/tlsconfig"
)

//nolint:gochecknoglobals
var (
	activeClients                        = map[DockerClientHandle]*activeClient{}
	activeClientsLock                    = sync.RWMutex{}
	nextClientHandle  DockerClientHandle = 0
)

type activeClient struct {
	dockerAPIClient *client.Client
	configFile      *configfile.ConfigFile
	serverInfo      *command.ServerInfo
	serverInfoLock  *sync.RWMutex
}

//export CreateClient
func CreateClient(cfg *C.ClientConfiguration) CreateClientReturn {
	opts := []client.Opt{
		client.WithAPIVersionNegotiation(),
	}

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

	activeClientsLock.Lock()
	defer activeClientsLock.Unlock()

	clientIndex := nextClientHandle

	// This should never happen, unless nextClientHandle wraps after reaching the maximum value of a uint64
	// (roughly enough to create a new client every nanosecond for 213,500 days, or just over 580 years)
	if _, exists := activeClients[clientIndex]; exists {
		panic(fmt.Sprintf("would have replaced existing client at index %v", clientIndex))
	}

	activeClients[clientIndex] = &activeClient{
		dockerAPIClient: c,
		configFile:      configFile,
		serverInfo:      nil,
		serverInfoLock:  &sync.RWMutex{},
	}

	nextClientHandle++

	return newCreateClientReturn(clientIndex, nil)
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
	activeClientsLock.Lock()
	defer activeClientsLock.Unlock()

	if _, ok := activeClients[clientHandle]; !ok {
		return toError(ErrInvalidDockerClientHandle)
	}

	delete(activeClients, clientHandle)

	return nil
}

func getClient(h DockerClientHandle) *activeClient {
	activeClientsLock.RLock()
	defer activeClientsLock.RUnlock()

	return activeClients[h]
}

func (h DockerClientHandle) DockerAPIClient() *client.Client {
	return getClient(h).dockerAPIClient
}

func (h DockerClientHandle) ClientConfigFile() *configfile.ConfigFile {
	return getClient(h).configFile
}

func (h DockerClientHandle) ServerInfo() (*command.ServerInfo, error) {
	c := getClient(h)

	if info := c.getCachedServerInfo(); info != nil {
		return info, nil
	}

	return c.updateCachedServerInfo()
}

// This is based on BuildKitEnabled() from github.com/docker/cli/cli/command/cli.go.
func (h DockerClientHandle) DefaultBuilderVersion() (types.BuilderVersion, error) {
	if buildkitEnv := os.Getenv("DOCKER_BUILDKIT"); buildkitEnv != "" {
		buildkitEnabled, err := strconv.ParseBool(buildkitEnv)

		if err != nil {
			return "", errors.Wrap(err, "DOCKER_BUILDKIT environment variable expects boolean value")
		}

		if buildkitEnabled {
			return types.BuilderBuildKit, nil
		} else {
			return types.BuilderV1, nil
		}
	}

	info, err := h.ServerInfo()

	if err != nil {
		return "", err
	}

	if info.BuildkitVersion == types.BuilderBuildKit {
		return types.BuilderBuildKit, nil
	}

	return types.BuilderV1, nil
}

func (c *activeClient) getCachedServerInfo() *command.ServerInfo {
	c.serverInfoLock.RLock()
	defer c.serverInfoLock.RUnlock()

	return c.serverInfo
}

func (c *activeClient) updateCachedServerInfo() (*command.ServerInfo, error) {
	c.serverInfoLock.Lock()
	defer c.serverInfoLock.Unlock()

	ping, err := c.dockerAPIClient.Ping(context.Background())

	if err != nil {
		return nil, err
	}

	c.serverInfo = &command.ServerInfo{
		HasExperimental: ping.Experimental,
		OSType:          ping.OSType,
		BuildkitVersion: ping.BuilderVersion,
	}

	return c.serverInfo, nil
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

//export SetClientProxySettingsForTest
func SetClientProxySettingsForTest(clientHandle DockerClientHandle) {
	docker := clientHandle.DockerAPIClient()
	configFile := clientHandle.ClientConfigFile()
	host := docker.DaemonHost()

	if configFile.Proxies == nil {
		configFile.Proxies = map[string]configfile.ProxyConfig{}
	}

	configFile.Proxies[host] = configfile.ProxyConfig{
		HTTPProxy:  "https://http-proxy",
		HTTPSProxy: "https://https-proxy",
		NoProxy:    "https://no-proxy",
		FTPProxy:   "https://ftp-proxy",
	}
}
