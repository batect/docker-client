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

package batect.dockerclient.native

import jnr.ffi.Pointer
import jnr.ffi.Struct
import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KProperty
import kotlin.reflect.KProperty1

internal val ListAllVolumesReturn.volumes by readOnlyList(
    ListAllVolumesReturn::volumesCount,
    ListAllVolumesReturn::volumesPointer,
    ::VolumeReference
)

private fun <T : Struct, E> readOnlyList(
    countProperty: KProperty1<T, Struct.u_int64_t>,
    pointerProperty: KProperty1<T, Struct.Pointer>,
    readFromPointer: (Pointer) -> E
): ReadOnlyProperty<T, List<E>> {
    return object : ReadOnlyProperty<T, List<E>> {
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
}
