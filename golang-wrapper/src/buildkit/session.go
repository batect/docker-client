// Copyright 2017-2021 Charles Korn.
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

package buildkit

import (
	"crypto/rand"
	"crypto/sha256"
	"encoding/hex"
	"fmt"
	"io/ioutil"
	"os"
	"path/filepath"

	"github.com/docker/cli/cli/config"
)

// These functions are taken directly from github.com/docker/cli/command/image/build_session.go.

func GetBuildSharedKey(dir string) string {
	// build session is hash of build dir with node based randomness
	s := sha256.Sum256([]byte(fmt.Sprintf("%s:%s", tryNodeIdentifier(), dir)))
	return hex.EncodeToString(s[:])
}

//nolint // This function is taken directly from the Docker CLI and for ease of maintenance we want to keep it identical.
func tryNodeIdentifier() string {
	out := config.Dir() // return config dir as default on permission error
	if err := os.MkdirAll(config.Dir(), 0700); err == nil {
		sessionFile := filepath.Join(config.Dir(), ".buildNodeID")
		if _, err := os.Lstat(sessionFile); err != nil {
			if os.IsNotExist(err) { // create a new file with stored randomness
				b := make([]byte, 32)
				if _, err := rand.Read(b); err != nil {
					return out
				}
				if err := ioutil.WriteFile(sessionFile, []byte(hex.EncodeToString(b)), 0600); err != nil {
					return out
				}
			}
		}

		dt, err := ioutil.ReadFile(sessionFile)
		if err == nil {
			return string(dt)
		}
	}
	return out
}
