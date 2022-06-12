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
	"fmt"
	"os"
	"sync"

	"github.com/docker/cli/cli/streams"
)

//nolint:gochecknoglobals
var (
	inputStreams = map[InputStreamHandle]*streams.In{
		1: streams.NewIn(os.Stdin),
	}

	inputPipes                              = map[InputStreamHandle]*pipe{}
	inputStreamsLock                        = sync.RWMutex{}
	nextInputStreamHandle InputStreamHandle = 2
)

func (handle InputStreamHandle) InputStream() *streams.In {
	inputStreamsLock.RLock()
	defer inputStreamsLock.RUnlock()

	return inputStreams[handle]
}

func (handle InputStreamHandle) Close() error {
	inputStreamsLock.RLock()
	defer inputStreamsLock.RUnlock()

	pipe, ok := inputPipes[handle]

	if !ok {
		return ErrInvalidInputStreamHandle
	}

	return pipe.writeEnd.Close()
}

//export CreateInputPipe
func CreateInputPipe() CreateInputPipeReturn {
	readEnd, writeEnd, err := os.Pipe()

	if err != nil {
		return newCreateInputPipeReturn(0, 0, toError(err))
	}

	inputStreamsLock.Lock()
	defer inputStreamsLock.Unlock()

	streamHandle := nextInputStreamHandle

	// This should never happen, unless nextInputStreamHandle wraps after reaching the maximum value of a uint64
	// (roughly enough to create a new client every nanosecond for 213,500 days, or just over 580 years)
	if _, exists := inputStreams[streamHandle]; exists {
		panic(fmt.Sprintf("would have replaced existing input stream at index %v", streamHandle))
	}

	if _, exists := inputPipes[streamHandle]; exists {
		panic(fmt.Sprintf("would have replaced existing input pipe at index %v", streamHandle))
	}

	inputStreams[streamHandle] = streams.NewIn(readEnd)
	inputPipes[streamHandle] = &pipe{readEnd: readEnd, writeEnd: writeEnd}
	nextInputStreamHandle++

	return newCreateInputPipeReturn(streamHandle, FileDescriptor(writeEnd.Fd()), nil)
}

//export CloseInputPipeWriteEnd
func CloseInputPipeWriteEnd(handle InputStreamHandle) Error {
	return toError(handle.Close())
}

//export DisposeInputPipe
func DisposeInputPipe(handle InputStreamHandle) Error {
	inputStreamsLock.Lock()
	defer inputStreamsLock.Unlock()

	pipe, ok := inputPipes[handle]

	if !ok {
		return toError(ErrInvalidInputStreamHandle)
	}

	if err := pipe.Close(); err != nil {
		return toError(err)
	}

	delete(inputPipes, handle)
	delete(inputStreams, handle)

	return nil
}
