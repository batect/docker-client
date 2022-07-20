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

@file:Suppress("MatchingDeclarationName")

package batect.dockerclient.io.windows

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
import java.nio.ByteBuffer

@Suppress("FunctionName", "FunctionNaming")
internal interface Win32 : WindowsLibC {
    @SaveError
    fun ReadFile(@In hFile: HANDLE, @Direct lpBuffer: Pointer, @In nNumberOfBytesToRead: Long, @Out lpNumberOfBytesRead: NativeLongByReference?, lpOverlapped: Pointer?): Boolean

    @SaveError
    fun WriteFile(@In hFile: HANDLE, @Direct lpBuffer: ByteBuffer, @In nNumberOfBytesToWrite: Long, @Out lpNumberOfBytesWritten: NativeLongByReference?, lpOverlapped: Pointer?): Boolean
}

internal val posix = POSIXFactory.getNativePOSIX()

internal val win32 = LibraryLoader.create(Win32::class.java)
    .option(LibraryOption.LoadNow, true)
    .option(LibraryOption.IgnoreError, true)
    .option(LibraryOption.TypeMapper, createTypeMapper())
    .library("kernel32")
    .failImmediately()
    .load()

internal val runtime = Runtime.getRuntime(win32)

// HACK: This is a hack to workaround the fact that POSIXTypeMapper isn't public, but we
// need it to translate a number of different Win32 types to their JVM equivalents.
private fun createTypeMapper(): TypeMapper {
    val constructor = Class.forName("jnr.posix.POSIXTypeMapper").getDeclaredConstructor()
    constructor.isAccessible = true

    return constructor.newInstance() as TypeMapper
}

internal fun messageForError(errno: Int): String {
    val error = LastError.values().singleOrNull { it.intValue() == errno }

    if (error != null) {
        return "${error.name}: $error"
    }

    return "0x${errno.toString(16)}: <unknown Win32 error>"
}
