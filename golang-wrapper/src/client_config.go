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

import "C"
import (
	"fmt"
	"os"
	"path/filepath"

	"github.com/docker/cli/cli/command"
	"github.com/docker/cli/cli/config"
	"github.com/docker/cli/cli/context/docker"
	"github.com/docker/cli/cli/context/store"
	"github.com/docker/cli/cli/flags"
	"github.com/docker/cli/opts"
	"github.com/docker/go-connections/tlsconfig"

	dcontext "github.com/docker/cli/cli/context"
)

//export LoadClientConfigurationFromCLIContext
func LoadClientConfigurationFromCLIContext(contextName *C.char, configDir *C.char) LoadClientConfigurationFromCLIContextReturn {
	baseConfigDir := resolveBaseConfigDir(configDir)
	contextStore := createContextStore(baseConfigDir)
	endpoint, err := loadEndpointForContext(C.GoString(contextName), contextStore)

	if err != nil {
		return newLoadClientConfigurationFromCLIContextReturn(nil, toError(err))
	}

	var tls TLSConfiguration

	if endpoint.TLSData != nil {
		tls = newTLSConfiguration(
			endpoint.TLSData.CA,
			int32(len(endpoint.TLSData.CA)),
			endpoint.TLSData.Cert,
			int32(len(endpoint.TLSData.Cert)),
			endpoint.TLSData.Key,
			int32(len(endpoint.TLSData.Key)),
		)
	}

	cfg := newClientConfiguration(endpoint.Host, tls, endpoint.SkipTLSVerify, baseConfigDir)

	return newLoadClientConfigurationFromCLIContextReturn(cfg, nil)
}

func resolveBaseConfigDir(configDir *C.char) string {
	baseConfigDir := C.GoString(configDir)

	if baseConfigDir == "" {
		baseConfigDir = config.Dir()
	}

	return baseConfigDir
}

// This is based on github.com/docker/cli's Initialize in cli/command/cli.go.
func createContextStore(baseConfigDir string) *command.ContextStoreWithDefault {
	contextStoreDir := filepath.Join(baseConfigDir, "contexts")
	contextStoreConfig := command.DefaultContextStoreConfig()
	baseContextStore := store.New(contextStoreDir, contextStoreConfig)
	contextStore := &command.ContextStoreWithDefault{
		Store: baseContextStore,
		Resolver: func() (*command.DefaultContext, error) {
			return resolveDefaultContext(baseConfigDir)
		},
	}

	return contextStore
}

// This is based on github.com/docker/cli's ResolveDefaultContext in cli/command/defaultcontextstore.go.
func resolveDefaultContext(baseConfigDir string) (*command.DefaultContext, error) {
	contextTLSData := store.ContextTLSData{
		Endpoints: make(map[string]store.EndpointTLSData),
	}

	contextMetadata := store.Metadata{
		Endpoints: make(map[string]interface{}),
		Metadata: command.DockerContext{
			Description: "",
		},
		Name: command.DefaultContextName,
	}

	dockerEP, err := resolveDefaultDockerEndpoint(baseConfigDir)

	if err != nil {
		return nil, err
	}

	contextMetadata.Endpoints[docker.DockerEndpoint] = dockerEP.EndpointMeta

	if dockerEP.TLSData != nil {
		contextTLSData.Endpoints[docker.DockerEndpoint] = *dockerEP.TLSData.ToStoreTLSData()
	}

	return &command.DefaultContext{Meta: contextMetadata, TLS: contextTLSData}, nil
}

// This is based on github.com/docker/cli's resolveDefaultDockerEndpoint in cli/command/cli.go.
func resolveDefaultDockerEndpoint(baseConfigDir string) (docker.Endpoint, error) {
	tlsOptions := getDefaultTLSOptions(baseConfigDir)
	host, err := getDefaultServerHost(tlsOptions != nil)

	if err != nil {
		return docker.Endpoint{}, err
	}

	skipTLSVerify := false

	var tlsData *dcontext.TLSData

	if tlsOptions != nil {
		skipTLSVerify = tlsOptions.InsecureSkipVerify
		tlsData, err = dcontext.TLSDataFromFiles(tlsOptions.CAFile, tlsOptions.CertFile, tlsOptions.KeyFile)

		if err != nil {
			return docker.Endpoint{}, err
		}
	}

	return docker.Endpoint{
		EndpointMeta: docker.EndpointMeta{
			Host:          host,
			SkipTLSVerify: skipTLSVerify,
		},
		TLSData: tlsData,
	}, nil
}

func getDefaultServerHost(defaultToTLS bool) (string, error) {
	host := os.Getenv("DOCKER_HOST")
	parsed, err := opts.ParseHost(defaultToTLS, host)

	if err != nil {
		return "", fmt.Errorf("value '%s' for DOCKER_HOST environment variable is invalid: %w", host, err)
	}

	return parsed, nil
}

// This is based on github.com/docker/cli's SetDefaultOptions in cli/flags/common.go.
func getDefaultTLSOptions(baseConfigDir string) *tlsconfig.Options {
	useTLS := os.Getenv("DOCKER_TLS") != ""
	verifyTLS := os.Getenv("DOCKER_TLS_VERIFY") != ""

	if !useTLS && !verifyTLS {
		return nil
	}

	certPath := os.Getenv("DOCKER_CERT_PATH")

	if certPath == "" {
		certPath = baseConfigDir
	}

	tlsOptions := &tlsconfig.Options{
		CAFile:             filepath.Join(certPath, flags.DefaultCaFile),
		CertFile:           filepath.Join(certPath, flags.DefaultCertFile),
		KeyFile:            filepath.Join(certPath, flags.DefaultKeyFile),
		InsecureSkipVerify: !verifyTLS,
	}

	// The Docker CLI unsets these options if the default paths are being used (ie. these aren't set explicitly
	// with CLI flags) and the file does not exist, so we do the same.
	if _, err := os.Stat(tlsOptions.CertFile); os.IsNotExist(err) {
		tlsOptions.CertFile = ""
	}

	if _, err := os.Stat(tlsOptions.KeyFile); os.IsNotExist(err) {
		tlsOptions.KeyFile = ""
	}

	return tlsOptions
}

func loadEndpointForContext(contextName string, store *command.ContextStoreWithDefault) (*docker.Endpoint, error) {
	metadata, err := store.GetMetadata(contextName)

	if err != nil {
		return nil, err
	}

	endpointMetadata, err := docker.EndpointFromContext(metadata)

	if err != nil {
		return nil, err
	}

	endpoint, err := docker.WithTLSData(store, contextName, endpointMetadata)

	return &endpoint, err
}

// This is based on github.com/docker/cli's resolveContextName in cli/flags/common.go.
//
//export DetermineActiveCLIContext
func DetermineActiveCLIContext(configDir *C.char) DetermineActiveCLIContextReturn {
	if _, present := os.LookupEnv("DOCKER_HOST"); present {
		return newDetermineActiveCLIContextReturn(command.DefaultContextName, nil)
	}

	if ctxName, present := os.LookupEnv("DOCKER_CONTEXT"); present {
		return newDetermineActiveCLIContextReturn(ctxName, nil)
	}

	baseConfigDir := resolveBaseConfigDir(configDir)
	cfg, err := loadConfigFile(baseConfigDir)

	if err != nil {
		return newDetermineActiveCLIContextReturn("", toError(err))
	}

	if cfg.CurrentContext != "" {
		return newDetermineActiveCLIContextReturn(cfg.CurrentContext, nil)
	}

	return newDetermineActiveCLIContextReturn(command.DefaultContextName, nil)
}
