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
	"errors"
	"fmt"
	"os"
)

type pipe struct {
	readEnd  *os.File
	writeEnd *os.File
}

func (p pipe) Close() error {
	if err := p.writeEnd.Close(); err != nil && !errors.Is(err, os.ErrClosed) {
		return fmt.Errorf("could not close write end of pipe: %w", err)
	}

	if err := p.readEnd.Close(); err != nil && !errors.Is(err, os.ErrClosed) {
		return fmt.Errorf("could not close read end of pipe: %w", err)
	}

	return nil
}
