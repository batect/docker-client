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

import "C"
import "os"

//export GetEnvironmentVariable
func GetEnvironmentVariable(name *C.char) *C.char {
	value, found := os.LookupEnv(C.GoString(name))

	if !found {
		return nil
	}

	return C.CString(value)
}

//export UnsetEnvironmentVariable
func UnsetEnvironmentVariable(name *C.char) Error {
	err := os.Unsetenv(C.GoString(name))

	return toError(err)
}

//export SetEnvironmentVariable
func SetEnvironmentVariable(name *C.char, value *C.char) Error {
	err := os.Setenv(C.GoString(name), C.GoString(value))

	return toError(err)
}
