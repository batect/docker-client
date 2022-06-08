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
import okio.Sink
import okio.Timeout
import platform.posix.errno

internal actual class PipeSink actual constructor(private val fd: FileDescriptor) : Sink {
    actual override fun timeout(): Timeout = Timeout.NONE

    actual override fun write(source: Buffer, byteCount: Long) {
        source.readByteArray(byteCount).usePinned { buffer ->
            var nextIndex = 0

            while (nextIndex < byteCount) {
                val bytesRemaining = byteCount.toInt() - nextIndex
                val bytesWritten = platform.posix.write(fd.safeToInt(), buffer.addressOf(nextIndex), bytesRemaining.toULong())

                if (bytesWritten == -1L) {
                    throw errnoToIOException(errno)
                }

                nextIndex += bytesWritten.toInt()
            }
        }
    }

    actual override fun flush() {
        // Nothing to do - pipes don't need to be flushed, see https://stackoverflow.com/a/43188944/1668119.
    }

    actual override fun close() {
        if (platform.posix.close(fd.safeToInt()) == -1) {
            throw errnoToIOException(errno)
        }
    }
}
