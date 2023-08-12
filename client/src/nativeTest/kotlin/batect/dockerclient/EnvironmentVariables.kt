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

import batect.dockerclient.native.GetEnvironmentVariable
import batect.dockerclient.native.SetEnvironmentVariable
import batect.dockerclient.native.UnsetEnvironmentVariable
import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.cstr
import kotlinx.cinterop.pointed
import kotlinx.cinterop.toKString
import platform.posix.free
import platform.posix.getenv

actual fun getEnvironmentVariable(name: String): String? = getenv(name)?.toKString()

actual fun getEnvironmentVariableAccordingToGolang(name: String): String? {
    GetEnvironmentVariable(name.cstr).use { value ->
        return value?.toKString()
    }
}

actual fun unsetEnvironmentVariableForGolang(name: String) {
    UnsetEnvironmentVariable(name.cstr).ifFailed { err ->
        val message = err.pointed.Message!!.toKString()
        throw EnvironmentVariableException("Unsetting environment variable $name failed: $message")
    }
}

actual fun setEnvironmentVariableForGolang(name: String, value: String) {
    SetEnvironmentVariable(name.cstr, value.cstr).ifFailed { err ->
        val message = err.pointed.Message!!.toKString()
        throw EnvironmentVariableException("Setting environment variable $name failed: $message")
    }
}

internal inline fun <R> CPointer<ByteVar>?.use(user: (CPointer<ByteVar>?) -> R): R = use(::free, user)
