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

package batect.dockerclient.native

import jnr.ffi.Pointer
import jnr.ffi.Runtime
import jnr.ffi.Struct
import jnr.ffi.provider.MemoryManager
import jnr.ffi.provider.jffi.ArrayMemoryIO
import kotlin.properties.ReadOnlyProperty
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty
import kotlin.reflect.KProperty1

internal val ListAllVolumesReturn.volumes by ReadOnlyList(
    ListAllVolumesReturn::volumesCount,
    ListAllVolumesReturn::volumesPointer,
    ::VolumeReference
)

internal var BuildImageRequest.buildArgs by WriteOnlyList<BuildImageRequest, StringPair>(
    BuildImageRequest::buildArgsCount,
    BuildImageRequest::buildArgsPointer
)

internal var BuildImageRequest.imageTags by WriteOnlyList<BuildImageRequest, String>(
    BuildImageRequest::imageTagsCount,
    BuildImageRequest::imageTagsPointer,
    ::stringToPointer
)

internal var CreateContainerRequest.command by WriteOnlyList<CreateContainerRequest, String>(
    CreateContainerRequest::commandCount,
    CreateContainerRequest::commandPointer,
    ::stringToPointer
)

internal var CreateContainerRequest.entrypoint by WriteOnlyList<CreateContainerRequest, String>(
    CreateContainerRequest::entrypointCount,
    CreateContainerRequest::entrypointPointer,
    ::stringToPointer
)

internal var CreateContainerRequest.extraHosts by WriteOnlyList<CreateContainerRequest, String>(
    CreateContainerRequest::extraHostsCount,
    CreateContainerRequest::extraHostsPointer,
    ::stringToPointer
)

internal var CreateContainerRequest.environmentVariables by WriteOnlyList<CreateContainerRequest, String>(
    CreateContainerRequest::environmentVariablesCount,
    CreateContainerRequest::environmentVariablesPointer,
    ::stringToPointer
)

internal var CreateContainerRequest.bindMounts by WriteOnlyList<CreateContainerRequest, String>(
    CreateContainerRequest::bindMountsCount,
    CreateContainerRequest::bindMountsPointer,
    ::stringToPointer
)

internal var CreateContainerRequest.tmpfsMounts by WriteOnlyList<CreateContainerRequest, StringPair>(
    CreateContainerRequest::tmpfsMountsCount,
    CreateContainerRequest::tmpfsMountsPointer
)

internal var CreateContainerRequest.deviceMounts by WriteOnlyList<CreateContainerRequest, batect.dockerclient.DeviceMount>(
    CreateContainerRequest::deviceMountsCount,
    CreateContainerRequest::deviceMountsPointer,
    ::deviceMountToNative
)

internal var CreateContainerRequest.exposedPorts by WriteOnlyList<CreateContainerRequest, batect.dockerclient.ExposedPort>(
    CreateContainerRequest::exposedPortsCount,
    CreateContainerRequest::exposedPortsPointer,
    ::exposedPortToNative
)

internal var CreateContainerRequest.capabilitiesToAdd by WriteOnlyList<CreateContainerRequest, String>(
    CreateContainerRequest::capabilitiesToAddCount,
    CreateContainerRequest::capabilitiesToAddPointer,
    ::stringToPointer
)

internal var CreateContainerRequest.capabilitiesToDrop by WriteOnlyList<CreateContainerRequest, String>(
    CreateContainerRequest::capabilitiesToDropCount,
    CreateContainerRequest::capabilitiesToDropPointer,
    ::stringToPointer
)

internal var CreateContainerRequest.networkAliases by WriteOnlyList<CreateContainerRequest, String>(
    CreateContainerRequest::networkAliasesCount,
    CreateContainerRequest::networkAliasesPointer,
    ::stringToPointer
)

internal fun StringPair(key: String, value: String): StringPair {
    val pair = StringPair(Runtime.getRuntime(nativeAPI))
    pair.key.set(key)
    pair.value.set(value)

    return pair
}

private class ReadOnlyList<T : Struct, E>(
    private val countProperty: KProperty1<T, Struct.u_int64_t>,
    private val pointerProperty: KProperty1<T, Struct.Pointer>,
    private val readFromPointer: (Pointer) -> E
) : ReadOnlyProperty<T, List<E>> {
    override fun getValue(thisRef: T, property: KProperty<*>): List<E> {
        if (pointerProperty.get(thisRef).intValue() == 0) {
            throw IllegalArgumentException("Array pointer is null")
        }

        val count = countProperty.get(thisRef).get()

        if (count == 0L) {
            return emptyList()
        }

        val pointer = pointerProperty.get(thisRef).get()

        // This assumes that all array elements are pointers. If this is not true, the getPointer call below will calculate the wrong offset.
        val elementSize = thisRef.runtime.addressSize()

        return (0 until count).map { i -> readFromPointer(pointer.getPointer(elementSize * i)) }
    }
}

private fun <T : Struct, E : Struct> WriteOnlyList(
    countProperty: KProperty1<T, Struct.u_int64_t>,
    pointerProperty: KProperty1<T, Struct.Pointer>
) = WriteOnlyList(
    countProperty,
    pointerProperty
) { e: E, _ -> Struct.getMemory(e) }

private fun stringToPointer(value: String, memoryManager: MemoryManager): Pointer {
    val bytes = value.toByteArray(Charsets.UTF_8)
    val pointer = memoryManager.allocateDirect(bytes.size + 1)
    pointer.put(0, bytes, 0, bytes.size)
    pointer.putByte(bytes.size.toLong(), 0)
    return pointer
}

private fun deviceMountToNative(value: batect.dockerclient.DeviceMount, @Suppress("UNUSED_PARAMETER") memoryManager: MemoryManager): Pointer {
    val runtime = Runtime.getRuntime(nativeAPI)
    val mount = DeviceMount(runtime)

    mount.localPath.set(value.localPath.toString())
    mount.containerPath.set(value.containerPath)
    mount.permissions.set(value.permissions)

    return Struct.getMemory(mount)
}

private fun exposedPortToNative(value: batect.dockerclient.ExposedPort, @Suppress("UNUSED_PARAMETER") memoryManager: MemoryManager): Pointer {
    val runtime = Runtime.getRuntime(nativeAPI)
    val port = ExposedPort(runtime)

    port.localPort.set(value.localPort)
    port.containerPort.set(value.containerPort)
    port.protocol.set(value.protocol)

    return Struct.getMemory(port)
}

private class WriteOnlyList<T : Struct, E>(
    private val countProperty: KProperty1<T, Struct.u_int64_t>,
    private val pointerProperty: KProperty1<T, Struct.Pointer>,
    private val getPointer: (E, MemoryManager) -> Pointer
) : ReadWriteProperty<T, Collection<E>> {
    override fun getValue(thisRef: T, property: KProperty<*>): Collection<E> {
        throw UnsupportedOperationException()
    }

    override fun setValue(thisRef: T, property: KProperty<*>, value: Collection<E>) {
        countProperty.get(thisRef).set(value.size)

        // This assumes that all array elements are pointers. If this is not true, the getPointer call below will calculate the wrong offset.
        val elementSize = thisRef.runtime.addressSize()
        val memoryManager = thisRef.runtime.memoryManager
        val pointer = memoryManager.allocate(value.size * elementSize)

        value.forEachIndexed { i, e ->
            val memory = getPointer(e, memoryManager).ensureDirectlyAllocated(memoryManager)
            pointer.putPointer(i * elementSize.toLong(), memory)
        }

        pointerProperty.get(thisRef).set(pointer)
    }
}

// What is this for?
// JNR defaults to allocating new values in JVM arrays before transferring them into native memory later.
// These values have no native memory address, so we can't use that non-existent address as a pointer.
// This function ensures that the memory referred to by a JNR pointer has been transferred into native memory
// so we can then write its address into an array of pointers.
private fun Pointer.ensureDirectlyAllocated(memoryManager: MemoryManager): Pointer {
    if (this.isDirect) {
        return this
    }

    val originalBytes = (this as ArrayMemoryIO).array()
    val newPointer = memoryManager.allocateDirect(originalBytes.size)
    newPointer.put(0, originalBytes, 0, originalBytes.size)

    return newPointer
}
