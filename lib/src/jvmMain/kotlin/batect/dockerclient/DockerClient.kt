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

import batect.dockerclient.native.DockerClientHandle
import batect.dockerclient.native.nativeAPI

public actual class DockerClient : AutoCloseable {
    private val clientHandle: DockerClientHandle = createClient()

    private fun createClient(): DockerClientHandle {
        nativeAPI.CreateClient()!!.use { ret ->
            if (ret.error != null) {
                throw DockerClientException(ret.error!!)
            }

            return ret.client.get()
        }
    }

    public actual fun ping(): PingResponse {
        nativeAPI.Ping(clientHandle)!!.use { ret ->
            if (ret.error != null) {
                throw PingFailedException(ret.error!!)
            }

            val response = ret.response!!

            return PingResponse(
                response.apiVersion.get(),
                response.osType.get(),
                response.experimental.get(),
                BuilderVersion.fromAPI(response.builderVersion.get())
            )
        }
    }

    public actual fun getDaemonVersionInformation(): DaemonVersionInformation {
        nativeAPI.GetDaemonVersionInformation(clientHandle)!!.use { ret ->
            if (ret.error != null) {
                throw GetDaemonVersionInformationFailedException(ret.error!!)
            }

            val response = ret.response!!

            return DaemonVersionInformation(
                response.version.get(),
                response.apiVersion.get(),
                response.minAPIVersion.get(),
                response.gitCommit.get(),
                response.operatingSystem.get(),
                response.architecture.get(),
                response.experimental.get()
            )
        }
    }

    public actual fun listAllVolumes(): Set<VolumeReference> {
        nativeAPI.ListAllVolumes(clientHandle)!!.use { ret ->
            if (ret.error != null) {
                throw ListAllVolumesFailedException(ret.error!!)
            }

            return ret.volumes.map { VolumeReference(it) }.toSet()
        }
    }

    public actual fun createVolume(name: String): VolumeReference {
        nativeAPI.CreateVolume(clientHandle, name)!!.use { ret ->
            if (ret.error != null) {
                throw VolumeCreationFailedException(ret.error!!)
            }

            return VolumeReference(ret.response!!)
        }
    }

    public actual fun deleteVolume(volume: VolumeReference) {
        nativeAPI.DeleteVolume(clientHandle, volume.name).use { ret ->
            if (ret != null) {
                throw VolumeDeletionFailedException(ret)
            }
        }
    }

    actual override fun close() {
        nativeAPI.DisposeClient(clientHandle)
    }

    private fun VolumeReference(native: batect.dockerclient.native.VolumeReference): VolumeReference = VolumeReference(native.name.get())
}
