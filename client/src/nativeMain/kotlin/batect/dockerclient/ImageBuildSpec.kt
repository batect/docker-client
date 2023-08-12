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

import batect.dockerclient.native.ValidateImageTag
import kotlinx.cinterop.cstr
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.pointed
import kotlinx.cinterop.toKString

internal actual fun validateImageTag(tag: String) {
    memScoped {
        ValidateImageTag(tag.cstr.ptr).ifFailed { error ->
            throw InvalidImageBuildSpecException("Image tag '$tag' is not a valid Docker image tag: ${error.pointed.Message!!.toKString()}")
        }
    }
}
