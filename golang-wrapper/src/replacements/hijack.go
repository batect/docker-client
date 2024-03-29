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

package replacements

import (
	"context"
	"fmt"
	"io"
	"sync"

	"github.com/docker/cli/cli/streams"
	"github.com/docker/docker/api/types"
	"github.com/docker/docker/pkg/stdcopy"
)

// This file is based on github.com/docker/cli/command/container/hijack.go,
// which does not expose hijackedIOStreamer.

type HijackedIOStreamer struct {
	InputStream  *streams.In
	OutputStream *streams.Out
	ErrorStream  *streams.Out

	Resp types.HijackedResponse

	Tty bool
}

// FIXME: if the output stream ends first, this method will leak the input streaming goroutine
// until one last read() operation returns.
// In the context of this Kotlin library, the stdin stream will be closed by the Kotlin side as soon
// as the container exits if the stdin stream is an Okio-backed input stream, so the goroutine will exit once that happens.
// If the stdin stream is /dev/stdin, then the goroutine will exit after consuming one more read operation,
// which may not happen for quite some time. There doesn't seem to be an easy way to avoid this.
func (h *HijackedIOStreamer) Stream(ctx context.Context) error {
	restoreInput, err := h.setupInput()
	if err != nil {
		return fmt.Errorf("unable to setup input stream: %w", err)
	}

	defer restoreInput()

	outputDone := h.beginOutputStream(restoreInput)
	inputDone := h.beginInputStream(restoreInput)

	select {
	case err := <-outputDone:
		return err
	case inputErr := <-inputDone:
		if h.OutputStream != nil || h.ErrorStream != nil {
			select {
			case outputErr := <-outputDone:
				if outputErr != nil {
					return outputErr
				}
			case <-ctx.Done():
				return ctx.Err()
			}
		}

		return inputErr
	case <-ctx.Done():
		return ctx.Err()
	}
}

func (h *HijackedIOStreamer) setupInput() (restore func(), err error) {
	if h.InputStream == nil || !h.Tty {
		return func() {}, nil
	}

	if err := h.setRawTerminal(); err != nil {
		return nil, fmt.Errorf("unable to set IO streams as raw terminal: %w", err)
	}

	var restoreOnce sync.Once
	restore = func() {
		restoreOnce.Do(func() {
			h.restoreTerminal()
		})
	}

	return restore, nil
}

func (h *HijackedIOStreamer) beginOutputStream(restoreInput func()) <-chan error {
	if h.OutputStream == nil && h.ErrorStream == nil {
		return nil
	}

	outputDone := make(chan error)

	go func() {
		var err error

		if h.OutputStream != nil && h.Tty {
			_, err = io.Copy(h.OutputStream, h.Resp.Reader)

			restoreInput()
		} else {
			_, err = stdcopy.StdCopy(h.OutputStream, h.ErrorStream, h.Resp.Reader)
		}

		outputDone <- err
	}()

	return outputDone
}

func (h *HijackedIOStreamer) beginInputStream(restoreInput func()) <-chan error {
	inputDone := make(chan error, 2)

	go func() {
		if h.InputStream != nil {
			_, err := io.Copy(h.Resp.Conn, h.InputStream)

			restoreInput()

			if err != nil {
				inputDone <- err
			}
		}

		if err := h.Resp.CloseWrite(); err != nil {
			inputDone <- err
		}

		close(inputDone)
	}()

	return inputDone
}

func (h *HijackedIOStreamer) setRawTerminal() error {
	if err := h.InputStream.SetRawTerminal(); err != nil {
		return err
	}

	return h.OutputStream.SetRawTerminal()
}

func (h *HijackedIOStreamer) restoreTerminal() {
	h.InputStream.RestoreTerminal()
	h.OutputStream.RestoreTerminal()
}
