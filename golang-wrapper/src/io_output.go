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
	"github.com/moby/term"
)

//nolint:gochecknoglobals
var (
	outputStreams = systemOutputStreams()

	outputPipes                               = map[OutputStreamHandle]*pipe{}
	outputStreamsLock                         = sync.RWMutex{}
	nextOutputStreamHandle OutputStreamHandle = 3
)

func systemOutputStreams() map[OutputStreamHandle]*streams.Out {
	_, stdout, stderr := term.StdStreams()

	return map[OutputStreamHandle]*streams.Out{
		1: streams.NewOut(stdout),
		2: streams.NewOut(stderr),
	}
}

func (handle OutputStreamHandle) OutputStream() *streams.Out {
	outputStreamsLock.RLock()
	defer outputStreamsLock.RUnlock()

	return outputStreams[handle]
}

func (handle OutputStreamHandle) Close() {
	outputStreamsLock.RLock()
	defer outputStreamsLock.RUnlock()

	pipe, ok := outputPipes[handle]

	if !ok {
		return
	}

	pipe.writeEnd.Close()
}

//export CreateOutputPipe
func CreateOutputPipe() CreateOutputPipeReturn {
	readEnd, writeEnd, err := os.Pipe()

	if err != nil {
		return newCreateOutputPipeReturn(0, 0, toError(err))
	}

	outputStreamsLock.Lock()
	defer outputStreamsLock.Unlock()

	streamHandle := nextOutputStreamHandle

	// This should never happen, unless nextOutputStreamHandle wraps after reaching the maximum value of a uint64
	// (roughly enough to create a new client every nanosecond for 213,500 days, or just over 580 years)
	if _, exists := outputStreams[streamHandle]; exists {
		panic(fmt.Sprintf("would have replaced existing output stream at index %v", streamHandle))
	}

	if _, exists := outputPipes[streamHandle]; exists {
		panic(fmt.Sprintf("would have replaced existing output pipe at index %v", streamHandle))
	}

	outputStreams[streamHandle] = streams.NewOut(writeEnd)
	outputPipes[streamHandle] = &pipe{readEnd: readEnd, writeEnd: writeEnd}
	nextOutputStreamHandle++

	return newCreateOutputPipeReturn(streamHandle, FileDescriptor(readEnd.Fd()), nil)
}

//export DisposeOutputPipe
func DisposeOutputPipe(handle OutputStreamHandle) Error {
	outputStreamsLock.Lock()
	defer outputStreamsLock.Unlock()

	pipe, ok := outputPipes[handle]

	if !ok {
		return toError(ErrInvalidOutputStreamHandle)
	}

	if err := pipe.Close(); err != nil {
		return toError(err)
	}

	delete(outputPipes, handle)
	delete(outputStreams, handle)

	return nil
}
