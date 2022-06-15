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

package batect.dockerclient.io.posix

import jnr.posix.POSIXFactory
import java.io.IOException

internal val posix = POSIXFactory.getNativePOSIX()

// This is based on okio's implementation.
internal fun errnoToIOException(errno: Int): IOException {
    val message = posix.strerror(errno) ?: "errno: $errno"

    return IOException(message)
}
