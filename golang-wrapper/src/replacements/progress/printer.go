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

package progress

import (
	"context"
	"io"
	"sync"

	"github.com/moby/buildkit/client"
	"github.com/moby/buildkit/util/progress/progressui"
	"github.com/opencontainers/go-digest"
)

// This file is based on github.com/docker/buildx/util/progress/printer.go,
// with changes to sort vertices in a sensible order and run callbacks when
// requested.

type Printer struct {
	status       chan *client.SolveStatus
	done         <-chan struct{}
	err          error
	warnings     []client.VertexWarning
	logMu        sync.Mutex
	logSourceMap map[digest.Digest]interface{}
	notify       func(status *client.SolveStatus)
}

func (p *Printer) Wait() error {
	close(p.status)
	<-p.done

	return p.err
}

func (p *Printer) Write(s *client.SolveStatus) {
	sortVerticesForDisplay(s.Vertexes)
	p.status <- s
	p.notify(s)
}

func (p *Printer) Warnings() []client.VertexWarning {
	return p.warnings
}

func (p *Printer) ValidateLogSource(dgst digest.Digest, v interface{}) bool {
	p.logMu.Lock()
	defer p.logMu.Unlock()
	src, ok := p.logSourceMap[dgst]

	if ok {
		if src == v {
			return true
		}
	} else {
		p.logSourceMap[dgst] = v
		return true
	}

	return false
}

func (p *Printer) ClearLogSource(v interface{}) {
	p.logMu.Lock()
	defer p.logMu.Unlock()

	for d := range p.logSourceMap {
		if p.logSourceMap[d] == v {
			delete(p.logSourceMap, d)
		}
	}
}

func NewPrinter(w io.Writer, notify func(status *client.SolveStatus)) *Printer {
	statusCh := make(chan *client.SolveStatus)
	doneCh := make(chan struct{})

	pw := &Printer{
		status:       statusCh,
		done:         doneCh,
		logSourceMap: map[digest.Digest]interface{}{},
		notify:       notify,
	}

	go func() {
		// We deliberately don't use the build operation's context as the context below - otherwise, error messages might not be printed after the context is cancelled.
		pw.warnings, pw.err = progressui.DisplaySolveStatus(context.Background(), "", nil, w, statusCh)

		close(doneCh)
	}()

	return pw
}
