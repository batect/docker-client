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

import batect.dockerclient.DockerClientException
import batect.dockerclient.ifFailed
import batect.dockerclient.native.CloseInputPipeWriteEnd
import batect.dockerclient.native.FileDescriptor
import batect.dockerclient.native.InputStreamHandle
import kotlinx.cinterop.UIntVar
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.pointed
import kotlinx.cinterop.ptr
import kotlinx.cinterop.toCPointer
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import okio.Buffer
import okio.IOException
import okio.Sink
import okio.Timeout
import platform.windows.GetLastError

@OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)
internal actual class PipeSink actual constructor(private val fd: FileDescriptor, private val handle: InputStreamHandle) : Sink {
    actual override fun timeout(): Timeout = Timeout.NONE

    actual override fun write(source: Buffer, byteCount: Long) = memScoped {
        source.readByteArray(byteCount).usePinned { buffer ->
            var nextIndex = 0
            val bytesWritten = alloc<UIntVar>()

            while (nextIndex < byteCount) {
                bytesWritten.value = 0u

                val bytesRemaining = byteCount.toInt() - nextIndex
                val result = platform.windows.WriteFile(fd.toLong().toCPointer(), buffer.addressOf(nextIndex), bytesRemaining.toUInt(), bytesWritten.ptr, null)

                if (result == 0) {
                    val lastError = GetLastError()

                    throw IOException(lastErrorString(lastError))
                }

                nextIndex += bytesWritten.value.toInt()
            }
        }
    }

    actual override fun flush() {
        // Nothing to do - pipes don't need to be flushed, see https://stackoverflow.com/a/43188944/1668119.
    }

    actual override fun close() {
        CloseInputPipeWriteEnd(handle).ifFailed { err ->
            throw DockerClientException(err.pointed)
        }
    }
}
