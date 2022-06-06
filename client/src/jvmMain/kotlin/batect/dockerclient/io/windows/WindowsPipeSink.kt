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

package batect.dockerclient.io.windows

import jnr.ffi.byref.NativeLongByReference
import jnr.posix.HANDLE
import okio.Buffer
import okio.Sink
import okio.Timeout
import java.io.IOException
import java.nio.ByteBuffer

internal class WindowsPipeSink(private val fd: Int) : Sink {
    private val handle = HANDLE.valueOf(fd.toLong())

    override fun timeout(): Timeout = Timeout.NONE

    override fun write(source: Buffer, byteCount: Long) {
        val buffer = ByteBuffer.wrap(source.readByteArray(byteCount))
        var nextIndex = 0

        while (nextIndex < byteCount) {
            val bytesWritten = NativeLongByReference(0)
            val bytesRemaining = byteCount.toInt() - nextIndex
            val succeeded = win32.WriteFile(handle, buffer.slice(nextIndex, bytesRemaining), bytesRemaining.toLong(), bytesWritten, null)

            if (!succeeded) {
                val lastError = posix.errno()

                throw IOException(messageForError(lastError))
            }

            nextIndex += bytesWritten.toInt()
        }
    }

    override fun flush() {
        // TODO: do we need to flush here?
    }

    override fun close() {
        flush()

        val succeeded = win32.CloseHandle(handle)

        if (!succeeded) {
            val lastError = posix.errno()

            throw IOException(messageForError(lastError))
        }
    }
}
