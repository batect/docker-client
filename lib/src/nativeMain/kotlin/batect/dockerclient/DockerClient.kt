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

import batect.dockerclient.native.CreateClient
import batect.dockerclient.native.DisposeClient
import batect.dockerclient.native.DockerClientHandle
import batect.dockerclient.native.FreeCreateClientReturn
import batect.dockerclient.native.FreePingReturn
import batect.dockerclient.native.Ping
import kotlinx.cinterop.pointed
import kotlinx.cinterop.toKString

public actual class DockerClient : AutoCloseable {
    private val clientHandle: DockerClientHandle = createClient()

    private fun createClient(): DockerClientHandle {
        val ret = CreateClient()!!

        try {
            if (ret.pointed.Error != null) {
                throw DockerClientException(ret.pointed.Error!!.pointed)
            }

            return ret.pointed.Client
        } finally {
            FreeCreateClientReturn(ret)
        }
    }

    public actual fun ping(): PingResponse {
        val ret = Ping(clientHandle)!!

        try {
            if (ret.pointed.Error != null) {
                throw DockerClientException(ret.pointed.Error!!.pointed)
            }

            val response = ret.pointed.Response!!.pointed

            return PingResponse(
                response.APIVersion!!.toKString(),
                response.OSType!!.toKString(),
                response.Experimental,
                response.BuilderVersion!!.toKString()
            )
        } finally {
            FreePingReturn(ret)
        }
    }

    actual override fun close() {
        DisposeClient(clientHandle)
    }
}
