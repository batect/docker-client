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

package batect.dockerclient

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.shouldBe
import kotlin.random.Random
import kotlin.random.nextULong

class DockerClientVolumeManagementSpec : ShouldSpec({
    val client = closeAfterTest(DockerClient.Builder().build())

    should("be able to create, list and delete volumes").onlyIfDockerDaemonPresent {
        val volumeName = "docker-client-test-volume-${Random.nextULong()}"

        val volumeReference = client.createVolume(volumeName)

        val volumesAfterCreation = client.listAllVolumes()
        volumesAfterCreation shouldContain volumeReference

        client.deleteVolume(volumeReference)

        val volumesAfterDeletion = client.listAllVolumes()
        volumesAfterDeletion shouldNotContain volumeReference
    }

    should("fail when deleting a volume that does not exist").onlyIfDockerDaemonPresent {
        val exception = shouldThrow<VolumeDeletionFailedException> {
            client.deleteVolume(VolumeReference("this-volume-does-not-exist"))
        }

        exception.message shouldBe "No such volume: this-volume-does-not-exist"
    }
})
