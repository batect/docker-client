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

@file:Suppress("ktlint:standard:filename")

package batect.dockerclient

import batect.dockerclient.native.ifFailed
import batect.dockerclient.native.nativeAPI

actual fun getEnvironmentVariable(name: String): String? = System.getenv(name)

actual fun getEnvironmentVariableAccordingToGolang(name: String): String? {
    return nativeAPI.GetEnvironmentVariable(name)
}

actual fun unsetEnvironmentVariableForGolang(name: String) {
    nativeAPI.UnsetEnvironmentVariable(name).ifFailed { err ->
        val message = err.message.get()
        throw EnvironmentVariableException("Unsetting environment variable $name failed: $message")
    }
}

actual fun setEnvironmentVariableForGolang(name: String, value: String) {
    nativeAPI.SetEnvironmentVariable(name, value).ifFailed { err ->
        val message = err.message.get()
        throw EnvironmentVariableException("Setting environment variable $name failed: $message")
    }
}
