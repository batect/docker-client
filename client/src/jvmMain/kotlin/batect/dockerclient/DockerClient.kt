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
import batect.dockerclient.native.PullImageProgressCallback
import batect.dockerclient.native.PullImageProgressUpdate
import batect.dockerclient.native.nativeAPI
import jnr.ffi.Pointer

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
        nativeAPI.DeleteVolume(clientHandle, volume.name).use { error ->
            if (error != null) {
                throw VolumeDeletionFailedException(error)
            }
        }
    }

    public actual fun createNetwork(name: String, driver: String): NetworkReference {
        nativeAPI.CreateNetwork(clientHandle, name, driver)!!.use { ret ->
            if (ret.error != null) {
                throw NetworkCreationFailedException(ret.error!!)
            }

            return NetworkReference(ret.response!!)
        }
    }

    public actual fun deleteNetwork(network: NetworkReference) {
        nativeAPI.DeleteNetwork(clientHandle, network.id).use { error ->
            if (error != null) {
                throw NetworkDeletionFailedException(error)
            }
        }
    }

    public actual fun getNetworkByNameOrID(searchFor: String): NetworkReference? {
        nativeAPI.GetNetworkByNameOrID(clientHandle, searchFor)!!.use { ret ->
            if (ret.error != null) {
                throw NetworkRetrievalFailedException(ret.error!!)
            }

            if (ret.response == null) {
                return null
            }

            return NetworkReference(ret.response!!)
        }
    }

    public actual fun pullImage(name: String, onProgressUpdate: ImagePullProgressReceiver): ImageReference {
        var exceptionThrownInCallback: Throwable? = null

        val callback = object : PullImageProgressCallback {
            override fun invoke(userData: Pointer?, progressPointer: Pointer?): Boolean {
                try {
                    val progress = PullImageProgressUpdate(progressPointer!!)
                    onProgressUpdate(ImagePullProgressUpdate(progress))

                    return true
                } catch (t: Throwable) {
                    exceptionThrownInCallback = t

                    return false
                }
            }
        }

        nativeAPI.PullImage(clientHandle, name, callback, null)!!.use { ret ->
            if (ret.error != null) {
                if (ret.error!!.type.get() == "main.ProgressCallbackFailedError") {
                    throw ImagePullFailedException("Image pull progress receiver threw an exception: $exceptionThrownInCallback", exceptionThrownInCallback)
                }

                throw ImagePullFailedException(ret.error!!)
            }

            return ImageReference(ret.response!!)
        }
    }

    public actual fun deleteImage(image: ImageReference) {
        nativeAPI.DeleteImage(clientHandle, image.id).use { error ->
            if (error != null) {
                throw ImageDeletionFailedException(error)
            }
        }
    }

    public actual fun getImage(name: String): ImageReference? {
        nativeAPI.GetImage(clientHandle, name)!!.use { ret ->
            if (ret.error != null) {
                throw ImageRetrievalFailedException(ret.error!!)
            }

            if (ret.response == null) {
                return null
            }

            return ImageReference(ret.response!!)
        }
    }

    actual override fun close() {
        nativeAPI.DisposeClient(clientHandle)
    }

    private fun VolumeReference(native: batect.dockerclient.native.VolumeReference): VolumeReference = VolumeReference(native.name.get())
    private fun NetworkReference(native: batect.dockerclient.native.NetworkReference): NetworkReference = NetworkReference(native.id.get())
    private fun ImageReference(native: batect.dockerclient.native.ImageReference): ImageReference = ImageReference(native.id.get())

    private fun ImagePullProgressUpdate(native: batect.dockerclient.native.PullImageProgressUpdate): ImagePullProgressUpdate =
        ImagePullProgressUpdate(native.message.get(), ImagePullProgressDetail(native.detail), native.id.get())

    private fun ImagePullProgressDetail(native: batect.dockerclient.native.PullImageProgressDetail?): ImagePullProgressDetail? =
        when (native) {
            null -> null
            else -> ImagePullProgressDetail(native.current.get(), native.total.get())
        }
}
