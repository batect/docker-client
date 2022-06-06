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

import jnr.constants.platform.windows.LastError
import jnr.ffi.LibraryLoader
import jnr.ffi.LibraryOption
import jnr.ffi.Pointer
import jnr.ffi.Runtime
import jnr.ffi.annotations.Direct
import jnr.ffi.annotations.In
import jnr.ffi.annotations.Out
import jnr.ffi.annotations.SaveError
import jnr.ffi.byref.NativeLongByReference
import jnr.ffi.mapper.TypeMapper
import jnr.posix.HANDLE
import jnr.posix.POSIXFactory
import jnr.posix.WindowsLibC
import okio.Buffer
import okio.Source
import okio.Timeout
import java.io.IOException

internal class WindowsPipeSource(private val fd: Int) : Source {
    override fun timeout(): Timeout = Timeout.NONE

    override fun read(sink: Buffer, byteCount: Long): Long {
        val bufferPointer = runtime.memoryManager.allocateDirect(byteCount, true)
        val bytesRead = NativeLongByReference(0)
        val succeeded = win32.ReadFile(HANDLE.valueOf(fd.toLong()), bufferPointer, bufferPointer.size(), bytesRead, null)

        if (!succeeded) {
            val lastError = posix.errno()

            if (lastError == ERROR_BROKEN_PIPE) {
                return -1
            }

            throw IOException(messageForError(lastError))
        }

        return when (bytesRead.toInt()) {
            0 -> -1
            -1 -> throw errnoToIOException(posix.errno())
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

    private fun messageForError(errno: Int): String {
        val error = LastError.values().singleOrNull { it.intValue() == errno }

        if (error != null) {
            return "${error.name}: $error"
        }

        return "0x${errno.toString(16)}: <unknown Win32 error>"
    }

    companion object {
        private const val ERROR_BROKEN_PIPE: Int = 0x0000006D
    }
}

internal interface Win32 : WindowsLibC {
    @SaveError
    fun ReadFile(@In hFile: HANDLE, @Direct lpBuffer: Pointer, @In nNumberOfBytesToRead: Long, @Out lpNumberOfBytesRead: NativeLongByReference?, lpOverlapped: Pointer?): Boolean
}

private val posix = POSIXFactory.getNativePOSIX()

private val win32 = LibraryLoader.create(Win32::class.java)
    .option(LibraryOption.LoadNow, true)
    .option(LibraryOption.IgnoreError, true)
    .option(LibraryOption.TypeMapper, createTypeMapper())
    .library("kernel32")
    .failImmediately()
    .load()

private val runtime = Runtime.getRuntime(win32)

// HACK: This is a hack to workaround the fact that POSIXTypeMapper isn't public, but we
// need it to translate a number of different Win32 types to their JVM equivalents.
private fun createTypeMapper(): TypeMapper {
    val constructor = Class.forName("jnr.posix.POSIXTypeMapper").getDeclaredConstructor()
    constructor.isAccessible = true

    return constructor.newInstance() as TypeMapper
}
