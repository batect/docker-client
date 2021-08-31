package main

import (
	/*
	#include "types.h"
	*/
	"C"
	"context"
	"runtime/cgo"

	"github.com/docker/docker/client"
)

//export CreateClient
func CreateClient() CreateClientReturn {
	c, err := client.NewClientWithOpts()

	if err != nil {
		return newCreateClientReturn(0, err)
	}

	return newCreateClientReturn(DockerClient(cgo.NewHandle(c)) , nil)
}

//export DisposeClient
func DisposeClient(clientHandle DockerClient) {
	h := cgo.Handle(clientHandle)
	h.Delete()
}

//export Ping
func Ping(clientHandle DockerClient) PingReturn {
	docker := cgo.Handle(clientHandle).Value().(*client.Client)

	dockerResponse, err := docker.Ping(context.Background())

	if err != nil {
		return newPingReturn(nil, err)
	}

	response := newPingResponse(
		dockerResponse.APIVersion,
		dockerResponse.OSType,
		dockerResponse.Experimental,
		string(dockerResponse.BuilderVersion),
	)

	return newPingReturn(response, nil)
}
