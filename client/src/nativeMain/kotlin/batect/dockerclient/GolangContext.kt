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

import batect.dockerclient.native.CancelContext
import batect.dockerclient.native.ContextHandle
import batect.dockerclient.native.CreateContext
import batect.dockerclient.native.DestroyContext
import kotlinx.cinterop.pointed

@OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)
internal actual class GolangContext : AutoCloseable {
    val handle: ContextHandle = CreateContext()

    actual fun cancel() {
        CancelContext(handle)
    }

    override fun close() {
        DestroyContext(handle).ifFailed { error -> throw DockerClientException(error.pointed) }
    }
}
