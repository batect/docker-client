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
	"errors"
	"fmt"
	"os"
	"sync"

	"github.com/docker/cli/cli/streams"
)

//nolint:gochecknoglobals
var (
	outputStreams = map[uint64]*streams.Out{
		1: streams.NewOut(os.Stdout),
		2: streams.NewOut(os.Stderr),
	}

	outputPipes                  = map[uint64]*pipe{}
	outputStreamsLock            = sync.RWMutex{}
	nextOutputStreamIndex uint64 = 3
)

type pipe struct {
	readEnd  *os.File
	writeEnd *os.File
}

func (handle OutputStreamHandle) OutputStream() *streams.Out {
	outputStreamsLock.RLock()
	defer outputStreamsLock.RUnlock()

	return outputStreams[uint64(handle)]
}

func (handle OutputStreamHandle) Close() {
	outputStreamsLock.RLock()
	defer outputStreamsLock.RUnlock()

	idx := uint64(handle)
	pipe, ok := outputPipes[idx]

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

	streamIndex := nextOutputStreamIndex
	outputStreams[streamIndex] = streams.NewOut(writeEnd)
	outputPipes[streamIndex] = &pipe{readEnd: readEnd, writeEnd: writeEnd}
	nextOutputStreamIndex++

	return newCreateOutputPipeReturn(OutputStreamHandle(streamIndex), FileDescriptor(readEnd.Fd()), nil)
}

//export DisposeOutputPipe
func DisposeOutputPipe(handle OutputStreamHandle) Error {
	outputStreamsLock.Lock()
	defer outputStreamsLock.Unlock()

	idx := uint64(handle)
	pipe, ok := outputPipes[idx]

	if !ok {
		return toError(ErrInvalidOutputStreamHandle)
	}

	if err := pipe.writeEnd.Close(); err != nil && !errors.Is(err, os.ErrClosed) {
		return toError(fmt.Errorf("could not close write end of pipe: %w", err))
	}

	if err := pipe.readEnd.Close(); err != nil && !errors.Is(err, os.ErrClosed) {
		return toError(fmt.Errorf("could not close read end of pipe: %w", err))
	}

	delete(outputPipes, idx)
	delete(outputStreams, idx)

	return nil
}
