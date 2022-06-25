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

import "fmt"

var (
	ErrInvalidDockerClientHandle = InvalidDockerClientHandleError{}
	ErrProgressCallbackFailed    = ProgressCallbackFailedError{}
	ErrReadyCallbackFailed       = ReadyCallbackFailedError{}
	ErrEventCallbackFailed       = EventCallbackFailedError{}
	ErrInvalidOutputStreamHandle = InvalidOutputStreamHandleError{}
	ErrInvalidInputStreamHandle  = InvalidInputStreamHandleError{}
	ErrBuildKitNotSupported      = BuildKitNotSupportedError{}
	ErrInvalidContextHandle      = InvalidContextHandleError{}
)

type InvalidDockerClientHandleError struct{}

func (e InvalidDockerClientHandleError) Error() string {
	return "invalid Docker client handle"
}

type ProgressCallbackFailedError struct{}

func (e ProgressCallbackFailedError) Error() string {
	return "progress callback failed"
}

type ReadyCallbackFailedError struct{}

func (e ReadyCallbackFailedError) Error() string {
	return "ready callback failed"
}

type EventCallbackFailedError struct{}

func (e EventCallbackFailedError) Error() string {
	return "event callback failed"
}

type InvalidOutputStreamHandleError struct{}

func (e InvalidOutputStreamHandleError) Error() string {
	return "invalid output stream handle"
}

type InvalidInputStreamHandleError struct{}

func (e InvalidInputStreamHandleError) Error() string {
	return "invalid input stream handle"
}

type BuildKitNotSupportedError struct{}

func (e BuildKitNotSupportedError) Error() string {
	return "the Docker deamon in use does not support BuildKit"
}

type InvalidBuilderVersionError struct {
	InvalidVersion string
}

func (e InvalidBuilderVersionError) Error() string {
	return fmt.Sprintf("unknown builder version '%s'", e.InvalidVersion)
}

type InvalidContextHandleError struct{}

func (e InvalidContextHandleError) Error() string {
	return "invalid context handle"
}
