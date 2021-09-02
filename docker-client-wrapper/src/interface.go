package main

import (
	/*
		#include "types.h"
	*/
	"C"
	"context"
	"sync"

	"github.com/docker/docker/client"
)

var (
	clients = map[uint64]*client.Client{}
	clientsLock = sync.RWMutex{}
	nextClientIndex uint64 = 0
)

//export CreateClient
func CreateClient() CreateClientReturn {
	c, err := client.NewClientWithOpts()

	if err != nil {
		return newCreateClientReturn(0, err)
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

func getClient(clientHandle DockerClient) *client.Client {
	clientsLock.RLock()
	defer clientsLock.RUnlock()

	return clients[uint64(clientHandle)]
}
