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
	"io"
	"time"
	"unsafe"

	"github.com/docker/docker/api/types"
	"github.com/docker/docker/api/types/events"
	filtertypes "github.com/docker/docker/api/types/filters"
)

//export StreamEvents
func StreamEvents(
	clientHandle DockerClientHandle,
	contextHandle ContextHandle,
	request StreamEventsRequest,
	onEvent EventCallback,
	callbackUserData unsafe.Pointer,
) Error {
	docker := clientHandle.DockerAPIClient()
	ctx := contextHandle.Context()

	opts := types.EventsOptions{
		Since:   time.Unix(int64(request.SinceSeconds), int64(request.SinceNanoseconds)).Format(time.RFC3339Nano),
		Until:   time.Unix(int64(request.UntilSeconds), int64(request.UntilNanoseconds)).Format(time.RFC3339Nano),
		Filters: toFilterArgs(request.Filters, request.FiltersCount),
	}

	eventsChan, errorsChan := docker.Events(ctx, opts)

	for {
		select {
		case event := <-eventsChan:
			if err := notifyEvent(event, onEvent, callbackUserData); err != nil {
				return toError(err)
			}
		case err := <-errorsChan:
			if err == io.EOF {
				return nil
			}

			return toError(err)
		}
	}
}

func notifyEvent(e events.Message, onEvent EventCallback, callbackUserData unsafe.Pointer) error {
	actor := newActor(e.Actor.ID, toStringPairs(e.Actor.Attributes))
	event := newEvent(e.Type, e.Action, actor, e.Scope, e.TimeNano)
	defer C.FreeEvent(event)

	if !invokeEventCallback(onEvent, callbackUserData, event) {
		return ErrEventCallbackFailed
	}

	return nil
}

func toFilterArgs(filters **C.StringToStringListPair, keyCount C.uint64_t) filtertypes.Args {
	args := filtertypes.NewArgs()

	for i := 0; i < int(keyCount); i++ {
		pair := C.GetStringToStringListPairArrayElement(filters, C.uint64_t(i))
		key := C.GoString(pair.Key)
		values := pair.Values

		for j := 0; j < int(pair.ValuesCount); j++ {
			value := C.GetstringArrayElement(values, C.uint64_t(j))

			args.Add(key, C.GoString(value))
		}
	}

	return args
}
