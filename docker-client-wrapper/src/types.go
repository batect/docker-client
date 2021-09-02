package main

/*
	#include "types.h"
*/
import "C"
import "fmt"

type DockerClient C.DockerClient

type CreateClientReturn *C.CreateClientReturn

func newCreateClientReturn(client DockerClient, err error) CreateClientReturn {
	value := C.AllocCreateClientReturn()
	value.Error = newError(err)
	value.Client = C.uint64_t(client)

	return value
}

type PingResponse *C.PingResponse

func newPingResponse(apiVersion string, osType string, experimental bool, builderVersion string) PingResponse {
	value := C.AllocPingResponse()
	value.APIVersion = C.CString(apiVersion)
	value.OSType = C.CString(osType)
	value.Experimental = C.bool(experimental)
	value.BuilderVersion = C.CString(builderVersion)

	return value
}

type PingReturn *C.PingReturn

func newPingReturn(response PingResponse, err error) PingReturn {
	value := C.AllocPingReturn()
	value.Error = newError(err)
	value.Response = response

	return value
}

type Error *C.Error

func newError(err error) Error {
	if err == nil {
		return nil
	}

	value := C.AllocError()

	value.Message = C.CString(err.Error())
	value.Type = C.CString(fmt.Sprintf("%T", err))

	return value
}
