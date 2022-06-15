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
import okio.Source
import okio.Timeout
import java.io.IOException

internal class WindowsPipeSource(private val fd: Int) : Source {
    private val handle = HANDLE.valueOf(fd.toLong())

    override fun timeout(): Timeout = Timeout.NONE

    override fun read(sink: Buffer, byteCount: Long): Long {
        val bufferPointer = runtime.memoryManager.allocateDirect(byteCount, true)
        val bytesRead = NativeLongByReference(0)
        val succeeded = win32.ReadFile(handle, bufferPointer, bufferPointer.size(), bytesRead, null)

        if (!succeeded) {
            val lastError = posix.errno()

            if (lastError == ERROR_BROKEN_PIPE) {
                return -1
            }

            throw IOException(messageForError(lastError))
        }

        return when (bytesRead.toInt()) {
            0 -> -1
            else -> {
                val buffer = ByteArray(bytesRead.toInt())
                bufferPointer.get(0, buffer, 0, bytesRead.toInt())
                sink.write(buffer, 0, bytesRead.toInt())

                bytesRead.toLong()
            }
        }
    }

    override fun close() {
        // Nothing to do.
    }

    companion object {
        private const val ERROR_BROKEN_PIPE: Int = 0x0000006D
    }
}
