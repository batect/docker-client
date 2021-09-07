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

import jnr.ffi.annotations.In

@Suppress("FunctionName")
internal interface API {
    fun Ping(@In handle: DockerClientHandle): PingReturn
    fun CreateClient(): CreateClientReturn
    fun DisposeClient(@In handle: DockerClientHandle)

    fun FreeCreateClientReturn(@In value: CreateClientReturn)
    fun FreePingReturn(@In value: PingReturn)
    fun FreeError(@In value: Error)
    fun FreePingResponse(@In value: PingResponse)
}
