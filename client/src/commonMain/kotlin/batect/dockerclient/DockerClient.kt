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

public expect class DockerClient() : AutoCloseable {
    public fun ping(): PingResponse
    public fun getDaemonVersionInformation(): DaemonVersionInformation

    public fun listAllVolumes(): Set<VolumeReference>
    public fun createVolume(name: String): VolumeReference
    public fun deleteVolume(volume: VolumeReference)

    public fun createNetwork(name: String, driver: String): NetworkReference
    public fun deleteNetwork(network: NetworkReference)
    public fun getNetworkByNameOrID(searchFor: String): NetworkReference?

    public fun pullImage(name: String, onProgressUpdate: ImagePullProgressReceiver = {}): ImageReference
    public fun deleteImage(image: ImageReference)
    public fun getImage(name: String): ImageReference?

    override fun close()
}
