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

// AUTOGENERATED
// This file is autogenerated by the :lib:generateJvmTypes Gradle task.
// Do not edit this file, as it will be regenerated automatically next time this project is built.

package batect.dockerclient.native

import jnr.ffi.Runtime
import jnr.ffi.Struct

internal typealias DockerClientHandle = Long

internal class Error(runtime: Runtime) : Struct(runtime), AutoCloseable {
    constructor(pointer: jnr.ffi.Pointer) : this(pointer.runtime) {
        this.useMemory(pointer)
    }

    val type = UTF8StringRef()
    val message = UTF8StringRef()

    override fun close() {
        nativeAPI.FreeError(this)
    }
}

internal class CreateClientReturn(runtime: Runtime) : Struct(runtime), AutoCloseable {
    constructor(pointer: jnr.ffi.Pointer) : this(pointer.runtime) {
        this.useMemory(pointer)
    }

    val client = u_int64_t()
    private val errorPointer = Pointer()
    val error: Error? by lazy { if (errorPointer.intValue() == 0) null else Error(errorPointer.get()) }

    override fun close() {
        nativeAPI.FreeCreateClientReturn(this)
    }
}

internal class PingResponse(runtime: Runtime) : Struct(runtime), AutoCloseable {
    constructor(pointer: jnr.ffi.Pointer) : this(pointer.runtime) {
        this.useMemory(pointer)
    }

    val apiVersion = UTF8StringRef()
    val osType = UTF8StringRef()
    val experimental = Boolean()
    val builderVersion = UTF8StringRef()

    override fun close() {
        nativeAPI.FreePingResponse(this)
    }
}

internal class PingReturn(runtime: Runtime) : Struct(runtime), AutoCloseable {
    constructor(pointer: jnr.ffi.Pointer) : this(pointer.runtime) {
        this.useMemory(pointer)
    }

    private val responsePointer = Pointer()
    val response: PingResponse? by lazy { if (responsePointer.intValue() == 0) null else PingResponse(responsePointer.get()) }
    private val errorPointer = Pointer()
    val error: Error? by lazy { if (errorPointer.intValue() == 0) null else Error(errorPointer.get()) }

    override fun close() {
        nativeAPI.FreePingReturn(this)
    }
}

internal class DaemonVersionInformation(runtime: Runtime) : Struct(runtime), AutoCloseable {
    constructor(pointer: jnr.ffi.Pointer) : this(pointer.runtime) {
        this.useMemory(pointer)
    }

    val version = UTF8StringRef()
    val apiVersion = UTF8StringRef()
    val minAPIVersion = UTF8StringRef()
    val gitCommit = UTF8StringRef()
    val operatingSystem = UTF8StringRef()
    val architecture = UTF8StringRef()
    val experimental = Boolean()

    override fun close() {
        nativeAPI.FreeDaemonVersionInformation(this)
    }
}

internal class GetDaemonVersionInformationReturn(runtime: Runtime) : Struct(runtime), AutoCloseable {
    constructor(pointer: jnr.ffi.Pointer) : this(pointer.runtime) {
        this.useMemory(pointer)
    }

    private val responsePointer = Pointer()
    val response: DaemonVersionInformation? by lazy { if (responsePointer.intValue() == 0) null else DaemonVersionInformation(responsePointer.get()) }
    private val errorPointer = Pointer()
    val error: Error? by lazy { if (errorPointer.intValue() == 0) null else Error(errorPointer.get()) }

    override fun close() {
        nativeAPI.FreeGetDaemonVersionInformationReturn(this)
    }
}

internal class VolumeReference(runtime: Runtime) : Struct(runtime), AutoCloseable {
    constructor(pointer: jnr.ffi.Pointer) : this(pointer.runtime) {
        this.useMemory(pointer)
    }

    val name = UTF8StringRef()

    override fun close() {
        nativeAPI.FreeVolumeReference(this)
    }
}

internal class CreateVolumeReturn(runtime: Runtime) : Struct(runtime), AutoCloseable {
    constructor(pointer: jnr.ffi.Pointer) : this(pointer.runtime) {
        this.useMemory(pointer)
    }

    private val responsePointer = Pointer()
    val response: VolumeReference? by lazy { if (responsePointer.intValue() == 0) null else VolumeReference(responsePointer.get()) }
    private val errorPointer = Pointer()
    val error: Error? by lazy { if (errorPointer.intValue() == 0) null else Error(errorPointer.get()) }

    override fun close() {
        nativeAPI.FreeCreateVolumeReturn(this)
    }
}

internal class ListAllVolumesReturn(runtime: Runtime) : Struct(runtime), AutoCloseable {
    constructor(pointer: jnr.ffi.Pointer) : this(pointer.runtime) {
        this.useMemory(pointer)
    }

    private val volumesCount = u_int64_t()
    private val volumesPointer = Pointer()
    val volumes: List<VolumeReference> by lazy {
        if (volumesPointer.intValue() == 0) {
            throw IllegalArgumentException("volumes is null")
        } else {
            val count = volumesCount.get()
            val pointer = volumesPointer.get()
            val elementSize = runtime.addressSize()

            (0..(count - 1)).map { i -> VolumeReference(pointer.getPointer(elementSize * i)) }
        }
    }
    private val errorPointer = Pointer()
    val error: Error? by lazy { if (errorPointer.intValue() == 0) null else Error(errorPointer.get()) }

    override fun close() {
        nativeAPI.FreeListAllVolumesReturn(this)
    }
}
