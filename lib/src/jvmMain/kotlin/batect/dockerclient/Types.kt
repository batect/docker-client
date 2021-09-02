package batect.dockerclient

import jnr.ffi.Runtime
import jnr.ffi.Struct

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
