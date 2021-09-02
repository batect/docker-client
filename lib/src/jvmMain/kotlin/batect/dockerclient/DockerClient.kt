/*
    Copyright 2017-2021 Charles Korn.

    Licensed under the Apache License, Version 2.0 (the "License");
    you may not use this file except in compliance with the License.
    You may obtain a copy of the License at

        http://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and
    limitations under the License.
*/

package batect.dockerclient

import jnr.ffi.LibraryLoader
import jnr.ffi.LibraryOption
import jnr.ffi.Runtime
import jnr.ffi.Struct
import jnr.ffi.annotations.In
import java.nio.file.Paths

public actual class DockerClient : AutoCloseable {
    private val clientHandle: DockerClientHandle = createClient()

    private fun createClient(): DockerClientHandle {
        nativeAPI.CreateClient().use { ret ->
            if (ret.error != null) {
                throw DockerClientException(ret.error!!.message.get())
            }

            return ret.client.get()
        }
    }

    public actual fun ping(): PingResponse {
        nativeAPI.Ping(clientHandle).use { ret ->
            if (ret.error != null) {
                throw DockerClientException(ret.error!!.message.get())
            }

            val response = ret.response!!

            return PingResponse(
                response.apiVersion.get(),
                response.osType.get(),
                response.experimental.get(),
                response.builderVersion.get()
            )
        }
    }

    actual override fun close() {
        nativeAPI.DisposeClient(clientHandle)
    }
}

internal val nativeAPI: NativeAPI by lazy {
    println(Paths.get("").toAbsolutePath())

    LibraryLoader
        .create(NativeAPI::class.java)
        .option(LibraryOption.LoadNow, true)
        .option(LibraryOption.IgnoreError, true)
        .search("../docker-client-wrapper/build/libs/darwin/x64/shared")
        .library("dockerclientwrapper")
        .failImmediately()
        .load()
}

@Suppress("FunctionName")
internal interface NativeAPI {
    fun Ping(@In handle: DockerClientHandle): PingReturn
    fun CreateClient(): CreateClientReturn
    fun DisposeClient(@In handle: DockerClientHandle)

    fun FreeCreateClientReturn(@In value: CreateClientReturn)
    fun FreePingReturn(@In value: PingReturn)
}

internal typealias DockerClientHandle = Long

internal class PingReturn(runtime: Runtime) : Struct(runtime), AutoCloseable {
    val responsePointer = Pointer()
    val response: NativePingResponse? by lazy { if (responsePointer.intValue() == 0) null else { NativePingResponse(responsePointer) } }

    val errorPointer = Pointer()
    val error: Error? by lazy { if (errorPointer.intValue() == 0) null else { Error(errorPointer) } }

    override fun close() {
        nativeAPI.FreePingReturn(this)
    }
}

internal class NativePingResponse(runtime: Runtime) : Struct(runtime) {
    constructor(pointer: Pointer) : this (pointer.memory.runtime) {
        this.useMemory(pointer.get())
    }

    val apiVersion = UTF8StringRef()
    val osType = UTF8StringRef()
    val experimental = Boolean()
    val builderVersion = UTF8StringRef()
}

internal class Error(runtime: Runtime) : Struct(runtime) {
    constructor(pointer: Pointer) : this (pointer.memory.runtime) {
        this.useMemory(pointer.get())
    }

    val type = UTF8StringRef()
    val message = UTF8StringRef()
}

internal class CreateClientReturn(runtime: Runtime) : Struct(runtime), AutoCloseable {
    val client = u_int64_t()
    val errorPointer = Pointer()
    val error: Error? by lazy { if (errorPointer.intValue() == 0) null else { Error(errorPointer) } }

    override fun close() {
        nativeAPI.FreeCreateClientReturn(this)
    }
}
