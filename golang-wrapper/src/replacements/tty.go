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
	"time"

	"github.com/docker/cli/cli/streams"
	"github.com/docker/docker/api/types"
	"github.com/docker/docker/client"
)

// These methods are based on the corresponding functions in github.com/docker/cli/cli/command/container/attach.go and
// github.com/docker/cli/cli/command/container/tty.go.

// Based on "resizeTty" from github.com/docker/cli/cli/command/container/attach.go.
func StartMonitoringTTYSizeForContainer(ctx context.Context, docker *client.Client, stdoutStream *streams.Out, containerID string) {
	height, width := stdoutStream.GetTtySize()
	// To handle the case where a user repeatedly attaches/detaches without resizing their
	// terminal, the only way to get the shell prompt to display for attaches 2+ is to artificially
	// resize it, then go back to normal. Without this, every attach after the first will
	// require the user to manually resize or hit enter.
	_ = resizeTtyTo(ctx, docker, containerID, height+1, width+1, false)

	// After the above resizing occurs, the call to monitorTtySize below will handle resetting back
	// to the actual size.
	monitorTtySize(ctx, docker, stdoutStream, containerID, false)
}

func StartMonitoringTTYSizeForExec(ctx context.Context, docker *client.Client, stdoutStream *streams.Out, execID string) {
	monitorTtySize(ctx, docker, stdoutStream, execID, true)
}

func initTtySize(ctx context.Context, docker *client.Client, stdoutStream *streams.Out, id string, isExec bool) {
	for retry := 0; retry < 6; retry++ {
		if err := resizeTty(ctx, docker, stdoutStream, id, isExec); err == nil {
			return
		}

		time.Sleep(10 * time.Millisecond)
	}
}

func resizeTty(ctx context.Context, docker *client.Client, stdoutStream *streams.Out, id string, isExec bool) error {
	height, width := stdoutStream.GetTtySize()

	return resizeTtyTo(ctx, docker, id, height, width, isExec)
}

func resizeTtyTo(ctx context.Context, client client.ContainerAPIClient, id string, height, width uint, isExec bool) error {
	if height == 0 && width == 0 {
		return nil
	}

	options := types.ResizeOptions{
		Height: height,
		Width:  width,
	}

	if isExec {
		return client.ContainerExecResize(ctx, id, options)
	}

	return client.ContainerResize(ctx, id, options)
}
