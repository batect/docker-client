/*
    Copyright 2017-2021 Charles Korn.

    Licensed under the Apache License, Version 2.0 (the "License");
    you may not use this file except in compliance with the License.
    You may obtain a copy of the License at

        http://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and
    limitations under the License.
*/

package batect.dockerclient.buildtools

import jnr.ffi.Platform

enum class OperatingSystem {
    Darwin,
    Linux,
    Windows;

    companion object {
        val current: OperatingSystem = when (val os = Platform.getNativePlatform().os) {
            Platform.OS.DARWIN -> Darwin
            Platform.OS.LINUX -> Linux
            Platform.OS.WINDOWS -> Windows
            else -> throw IllegalArgumentException("Unknown operating system $os.")
        }
    }
}
