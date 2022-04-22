/*
    Copyright 2017-2022 Charles Korn.

    Licensed under the Apache License, Version 2.0 (the "License");
    you may not use this file except in compliance with the License.
    You may obtain a copy of the License at

        https://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and
    limitations under the License.
*/

package batect.dockerclient

import okio.FileSystem

// This is required because Okio doesn't declare FileSystem.SYSTEM for all platforms, even though it does declare it
// for all of the platforms we target.
// The library will compile fine without it, but the compileCommonMainKotlinMetadata task will fail when publishing the
// library otherwise.
internal expect val systemFileSystem: FileSystem
