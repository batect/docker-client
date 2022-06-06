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

package batect.dockerclient.io

import jnr.posix.POSIXFactory
import okio.Buffer
import okio.Source
import okio.Timeout
import java.io.IOException

internal class POSIXPipeSource(private val fd: Int) : Source {
    override fun timeout(): Timeout = Timeout.NONE

    override fun read(sink: Buffer, byteCount: Long): Long {
        val bytesToRead = if (byteCount > Int.MAX_VALUE) Int.MAX_VALUE else byteCount.toInt()
        val buffer = ByteArray(bytesToRead)

        return when (val bytesRead = posix.read(fd, buffer, bytesToRead)) {
            0 -> -1
            -1 -> throw errnoToIOException(posix.errno())
            else -> {
                sink.write(buffer, 0, bytesRead)

                bytesRead.toLong()
            }
        }
    }

    override fun close() {
        // Nothing to do.
    }
}

private val posix = POSIXFactory.getNativePOSIX()

// This is based on okio's implementation.
internal fun errnoToIOException(errno: Int): IOException {
    val message = posix.strerror(errno) ?: "errno: $errno"

    return IOException(message)
}
