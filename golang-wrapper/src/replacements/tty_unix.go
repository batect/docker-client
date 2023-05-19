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

//go:build !windows

package replacements

import (
	"context"
	"os"
	gosignal "os/signal"

	"github.com/docker/cli/cli/streams"
	"github.com/docker/docker/client"
	"github.com/moby/sys/signal"
)

func monitorTtySize(ctx context.Context, docker *client.Client, stdoutStream *streams.Out, id string, isExec bool) {
	sigchan := make(chan os.Signal, 1)
	gosignal.Notify(sigchan, signal.SIGWINCH)
	initTtySize(ctx, docker, stdoutStream, id, isExec)

	go func() {
		for {
			select {
			case <-sigchan:
				_ = resizeTty(ctx, docker, stdoutStream, id, isExec)
			case <-ctx.Done():
				gosignal.Stop(sigchan)
				return
			}
		}
	}()
}
