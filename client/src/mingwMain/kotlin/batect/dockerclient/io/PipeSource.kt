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
import kotlinx.cinterop.ByteVarOf
import kotlinx.cinterop.UIntVar
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.toCPointer
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import okio.Buffer
import okio.IOException
import okio.Source
import okio.Timeout
import platform.windows.DWORD
import platform.windows.ERROR_BROKEN_PIPE
import platform.windows.FORMAT_MESSAGE_FROM_SYSTEM
import platform.windows.FORMAT_MESSAGE_IGNORE_INSERTS
import platform.windows.FormatMessageA
import platform.windows.GetLastError
import platform.windows.LANG_NEUTRAL
import platform.windows.SUBLANG_DEFAULT

@OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)
internal actual class PipeSource actual constructor(private val fd: FileDescriptor) : Source {
    actual override fun timeout(): Timeout = Timeout.NONE

    actual override fun read(sink: Buffer, byteCount: Long): Long = memScoped {
        val bytesToRead = if (byteCount > Int.MAX_VALUE) Int.MAX_VALUE else byteCount.toInt()
        val buffer = ByteArray(bytesToRead)
        val bytesRead = alloc<UIntVar>()

        val result = buffer.usePinned { pinnedBuffer ->
            platform.windows.ReadFile(fd.toLong().toCPointer(), pinnedBuffer.addressOf(0), bytesToRead.toUInt(), bytesRead.ptr, null)
        }

        if (result == 0) {
            val lastError = GetLastError()

            if (lastError.toInt() == ERROR_BROKEN_PIPE) {
                return -1
            }

            throw IOException(lastErrorString(lastError))
        }

        return when (bytesRead.value) {
            0u -> -1
            else -> {
                sink.write(buffer, 0, bytesRead.value.toInt())

                bytesRead.value.toLong()
            }
        }
    }

    actual override fun close() {
        // Nothing to do.
    }
}

// From Okio (https://github.com/square/okio/blob/master/okio/src/mingwX64Main/kotlin/okio/-Windows.kt)
@OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)
internal fun lastErrorString(lastError: DWORD): String {
    memScoped {
        val messageMaxSize = 2048
        val message = allocArray<ByteVarOf<Byte>>(messageMaxSize)
        FormatMessageA(
            dwFlags = (FORMAT_MESSAGE_FROM_SYSTEM or FORMAT_MESSAGE_IGNORE_INSERTS).toUInt(),
            lpSource = null,
            dwMessageId = lastError,
            dwLanguageId = (SUBLANG_DEFAULT * 1024 + LANG_NEUTRAL).toUInt(), // MAKELANGID macro.
            lpBuffer = message,
            nSize = messageMaxSize.toUInt(),
            Arguments = null,
        )
        return Buffer().writeNullTerminated(message).readUtf8().trim()
    }
}
