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

import batect.dockerclient.native.ClientConfiguration
import batect.dockerclient.native.DockerClientHandle
import batect.dockerclient.native.PullImageProgressCallback
import batect.dockerclient.native.PullImageProgressUpdate
import batect.dockerclient.native.TLSConfiguration
import batect.dockerclient.native.nativeAPI
import jnr.ffi.Pointer
import jnr.ffi.Runtime
import jnr.ffi.Struct

internal actual class RealDockerClient actual constructor(configuration: DockerClientConfiguration) : DockerClient, AutoCloseable {
    private val clientHandle: DockerClientHandle = createClient(configuration)

    private fun createClient(configuration: DockerClientConfiguration): DockerClientHandle {
        nativeAPI.CreateClient(ClientConfiguration(configuration))!!.use { ret ->
            if (ret.error != null) {
                throw DockerClientException(ret.error!!)
            }

            return ret.client.get()
        }
    }

    public override fun ping(): PingResponse {
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

    public override fun getDaemonVersionInformation(): DaemonVersionInformation {
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

    public override fun listAllVolumes(): Set<VolumeReference> {
        nativeAPI.ListAllVolumes(clientHandle)!!.use { ret ->
            if (ret.error != null) {
                throw ListAllVolumesFailedException(ret.error!!)
            }

            return ret.volumes.map { VolumeReference(it) }.toSet()
        }
    }

    public override fun createVolume(name: String): VolumeReference {
        nativeAPI.CreateVolume(clientHandle, name)!!.use { ret ->
            if (ret.error != null) {
                throw VolumeCreationFailedException(ret.error!!)
            }

            return VolumeReference(ret.response!!)
        }
    }

    public override fun deleteVolume(volume: VolumeReference) {
        nativeAPI.DeleteVolume(clientHandle, volume.name).use { error ->
            if (error != null) {
                throw VolumeDeletionFailedException(error)
            }
        }
    }

    public override fun createNetwork(name: String, driver: String): NetworkReference {
        nativeAPI.CreateNetwork(clientHandle, name, driver)!!.use { ret ->
            if (ret.error != null) {
                throw NetworkCreationFailedException(ret.error!!)
            }

            return NetworkReference(ret.response!!)
        }
    }

    public override fun deleteNetwork(network: NetworkReference) {
        nativeAPI.DeleteNetwork(clientHandle, network.id).use { error ->
            if (error != null) {
                throw NetworkDeletionFailedException(error)
            }
        }
    }

    public override fun getNetworkByNameOrID(searchFor: String): NetworkReference? {
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

    public override fun pullImage(name: String, onProgressUpdate: ImagePullProgressReceiver): ImageReference {
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

    public override fun deleteImage(image: ImageReference) {
        nativeAPI.DeleteImage(clientHandle, image.id).use { error ->
            if (error != null) {
                throw ImageDeletionFailedException(error)
            }
        }
    }

    public override fun getImage(name: String): ImageReference? {
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

    override fun close() {
        nativeAPI.DisposeClient(clientHandle).use { error ->
            if (error != null) {
                throw DockerClientException(error)
            }
        }
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

    private fun ClientConfiguration(jvm: DockerClientConfiguration): ClientConfiguration {
        val config = ClientConfiguration(Runtime.getRuntime(nativeAPI))
        config.useConfigurationFromEnvironment.set(jvm.useConfigurationFromEnvironment)
        config.host.set(jvm.host)
        config.configDirectoryPath.set(jvm.configDirectoryPath)

        if (jvm.tls != null) {
            config.tlsPointer.set(Struct.getMemory(TLSConfiguration(jvm.tls)))
        } else {
            config.tlsPointer.set(0)
        }

        return config
    }

    private fun TLSConfiguration(jvm: DockerClientTLSConfiguration): TLSConfiguration {
        val tls = TLSConfiguration(Runtime.getRuntime(nativeAPI))
        tls.caFilePath.set(jvm.caFilePath)
        tls.certFilePath.set(jvm.certFilePath)
        tls.keyFilePath.set(jvm.keyFilePath)
        tls.insecureSkipVerify.set(jvm.insecureSkipVerify)

        return tls
    }
}