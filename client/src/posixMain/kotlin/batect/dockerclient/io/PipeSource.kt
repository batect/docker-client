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

import batect.dockerclient.native.FileDescriptor
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import okio.Buffer
import okio.Source
import okio.Timeout
import platform.posix.errno

internal actual class PipeSource actual constructor(private val fd: FileDescriptor) : Source {
    actual override fun timeout(): Timeout = Timeout.NONE

    actual override fun read(sink: Buffer, byteCount: Long): Long {
        val bytesToRead = if (byteCount > Int.MAX_VALUE) Int.MAX_VALUE else byteCount.toInt()
        val buffer = ByteArray(bytesToRead)

        val bytesRead = buffer.usePinned { pinned ->
            platform.posix.read(fd.safeToInt(), pinned.addressOf(0), bytesToRead.toULong())
        }

        return when (bytesRead) {
            0L -> -1
            -1L -> throw errnoToIOException(errno)
            else -> {
                sink.write(buffer, 0, bytesRead.safeToInt())

                bytesRead
            }
        }
    }

    actual override fun close() {
        // Nothing to do.
    }
}

private fun Long.safeToInt() = if (this > Int.MAX_VALUE.toLong()) throw IllegalArgumentException("Value out of range") else this.toInt()
