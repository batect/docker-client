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

package batect.dockerclient.buildtools

import jnr.ffi.Platform

enum class Architecture(
    val golangName: String,
    val jnrName: String, // Name as per jnr.ffi.Platform.OS constants
    val zigName: String,
) {
    X86("386", "I386", "i386"),
    X64("amd64", "X86_64", "x86_64"),
    Arm64("arm64", "AARCH64", "aarch64"),
    ;

    companion object {
        val current: Architecture = when (val arch = Platform.getNativePlatform().cpu) {
            Platform.CPU.I386 -> X86
            Platform.CPU.X86_64 -> X64
            Platform.CPU.AARCH64 -> Arm64
            else -> throw IllegalArgumentException("Unknown architecture $arch.")
        }
    }
}
