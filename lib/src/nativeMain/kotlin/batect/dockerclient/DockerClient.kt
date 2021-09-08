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
import batect.dockerclient.native.CreateVolume
import batect.dockerclient.native.DeleteVolume
import batect.dockerclient.native.DisposeClient
import batect.dockerclient.native.DockerClientHandle
import batect.dockerclient.native.FreeCreateClientReturn
import batect.dockerclient.native.FreeCreateVolumeReturn
import batect.dockerclient.native.FreeError
import batect.dockerclient.native.FreeGetDaemonVersionInformationReturn
import batect.dockerclient.native.FreeListAllVolumesReturn
import batect.dockerclient.native.FreePingReturn
import batect.dockerclient.native.GetDaemonVersionInformation
import batect.dockerclient.native.ListAllVolumes
import batect.dockerclient.native.Ping
import kotlinx.cinterop.CPointed
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.CPointerVar
import kotlinx.cinterop.cstr
import kotlinx.cinterop.get
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
                throw PingFailedException(ret.pointed.Error!!.pointed)
            }

            val response = ret.pointed.Response!!.pointed

            return PingResponse(
                response.APIVersion!!.toKString(),
                response.OSType!!.toKString(),
                response.Experimental,
                BuilderVersion.fromAPI(response.BuilderVersion!!.toKString())
            )
        } finally {
            FreePingReturn(ret)
        }
    }

    public actual fun getDaemonVersionInformation(): DaemonVersionInformation {
        val ret = GetDaemonVersionInformation(clientHandle)!!

        try {
            if (ret.pointed.Error != null) {
                throw GetDaemonVersionInformationFailedException(ret.pointed.Error!!.pointed)
            }

            val response = ret.pointed.Response!!.pointed

            return DaemonVersionInformation(
                response.Version!!.toKString(),
                response.APIVersion!!.toKString(),
                response.MinAPIVersion!!.toKString(),
                response.GitCommit!!.toKString(),
                response.OperatingSystem!!.toKString(),
                response.Architecture!!.toKString(),
                response.Experimental
            )
        } finally {
            FreeGetDaemonVersionInformationReturn(ret)
        }
    }

    public actual fun listAllVolumes(): Set<VolumeReference> {
        val ret = ListAllVolumes(clientHandle)!!

        try {
            if (ret.pointed.Error != null) {
                throw ListAllVolumesFailedException(ret.pointed.Error!!.pointed)
            }

            return fromArray(ret.pointed.Volumes!!, ret.pointed.VolumesCount) { VolumeReference(it) }.toSet()
        } finally {
            FreeListAllVolumesReturn(ret)
        }
    }

    public actual fun createVolume(name: String): VolumeReference {
        val ret = CreateVolume(clientHandle, name.cstr)!!

        try {
            if (ret.pointed.Error != null) {
                throw VolumeCreationFailedException(ret.pointed.Error!!.pointed)
            }

            return VolumeReference(ret.pointed.Response!!.pointed)
        } finally {
            FreeCreateVolumeReturn(ret)
        }
    }

    public actual fun deleteVolume(volume: VolumeReference) {
        val error = DeleteVolume(clientHandle, volume.name.cstr)

        try {
            if (error != null) {
                throw VolumeDeletionFailedException(error.pointed)
            }
        } finally {
            FreeError(error)
        }
    }

    actual override fun close() {
        DisposeClient(clientHandle)
    }

    private fun VolumeReference(native: batect.dockerclient.native.VolumeReference): VolumeReference =
        VolumeReference(native.Name!!.toKString())

    private inline fun <reified NativeType : CPointed, KotlinType> fromArray(
        source: CPointer<CPointerVar<NativeType>>,
        count: ULong,
        creator: (NativeType) -> KotlinType
    ): List<KotlinType> {
        return (0.toULong().until(count))
            .map { i -> creator(source[i.toLong()]!!.pointed) }
    }
}
