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

// This file is based on github.com/docker/docker/pkg/progress/progressreader.go,
// but correctly propagates errors from progress.Output.WriteProgress() in updateProgress below.

package replacements

import (
	"io"
	"time"

	"github.com/docker/docker/pkg/progress"
	"golang.org/x/time/rate"
)

// Reader is a Reader with progress bar.
type Reader struct {
	in          io.ReadCloser   // Stream to read from
	out         progress.Output // Where to send progress bar to
	size        int64
	current     int64
	lastUpdate  int64
	id          string
	action      string
	rateLimiter *rate.Limiter
}

// NewProgressReader creates a new ProgressReader.
func NewProgressReader(in io.ReadCloser, out progress.Output, size int64, id, action string) *Reader {
	return &Reader{
		in:          in,
		out:         out,
		size:        size,
		id:          id,
		action:      action,
		rateLimiter: rate.NewLimiter(rate.Every(100*time.Millisecond), 1),
	}
}

func (p *Reader) Read(buf []byte) (n int, err error) {
	read, err := p.in.Read(buf)
	p.current += int64(read)
	updateEvery := int64(1024 * 512) // 512kB

	if p.size > 0 {
		// Update progress for every 1% read if 1% < 512kB
		if increment := int64(0.01 * float64(p.size)); increment < updateEvery {
			updateEvery = increment
		}
	}

	if p.current-p.lastUpdate > updateEvery || err != nil {
		if err := p.updateProgress(err != nil && read == 0); err != nil {
			return read, err
		}

		p.lastUpdate = p.current
	}

	return read, err
}

// Close closes the progress reader and its underlying reader.
func (p *Reader) Close() error {
	if p.current < p.size {
		// print a full progress bar when closing prematurely
		p.current = p.size

		if err := p.updateProgress(false); err != nil {
			p.in.Close()
			return err
		}
	}

	return p.in.Close()
}

func (p *Reader) updateProgress(last bool) error {
	if last || p.current == p.size || p.rateLimiter.Allow() {
		return p.out.WriteProgress(progress.Progress{ID: p.id, Action: p.action, Current: p.current, Total: p.size, LastUpdate: last})
	}

	return nil
}
