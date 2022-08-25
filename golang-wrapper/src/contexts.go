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
	"C"
	"context"
	"fmt"
	"sync"
)

//nolint:gochecknoglobals
var (
	activeContexts                   = map[ContextHandle]*activeContext{}
	activeContextsLock               = sync.RWMutex{}
	nextContextHandle  ContextHandle = 0 //nolint:revive
)

type activeContext struct {
	ctx    context.Context
	cancel context.CancelFunc
}

//export CreateContext
func CreateContext() ContextHandle {
	activeContextsLock.Lock()
	defer activeContextsLock.Unlock()

	contextHandle := nextContextHandle

	// This should never happen, unless nextContextHandle wraps after reaching the maximum value of a uint64
	// (roughly enough to create a new client every nanosecond for 213,500 days, or just over 580 years)
	if _, exists := activeContexts[contextHandle]; exists {
		panic(fmt.Sprintf("would have replaced existing context at index %v", contextHandle))
	}

	ctx, cancel := context.WithCancel(context.Background())

	activeContexts[contextHandle] = &activeContext{
		ctx:    ctx,
		cancel: cancel,
	}

	nextContextHandle++

	return contextHandle
}

//export CancelContext
func CancelContext(contextHandle ContextHandle) {
	getContext(contextHandle).cancel()
}

//export DestroyContext
func DestroyContext(contextHandle ContextHandle) Error {
	activeContextsLock.Lock()
	defer activeContextsLock.Unlock()

	ctx, ok := activeContexts[contextHandle]

	if !ok {
		return toError(ErrInvalidContextHandle)
	}

	ctx.cancel()
	delete(activeContexts, contextHandle)

	return nil
}

func getContext(h ContextHandle) *activeContext {
	activeContextsLock.RLock()
	defer activeContextsLock.RUnlock()

	return activeContexts[h]
}

func (h ContextHandle) Context() context.Context {
	return getContext(h).ctx
}
