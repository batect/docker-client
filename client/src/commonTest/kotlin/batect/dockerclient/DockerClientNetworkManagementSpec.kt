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
import io.kotest.matchers.shouldBe
import kotlin.random.Random
import kotlin.random.nextULong

class DockerClientNetworkManagementSpec : ShouldSpec({
    val client = closeAfterTest(DockerClient.create())
    val networkDriver = when (testEnvironmentContainerOperatingSystem) {
        ContainerOperatingSystem.Linux -> NetworkDrivers.bridge
        ContainerOperatingSystem.Windows -> NetworkDrivers.nat
    }

    should("be able to create, get and delete networks").onlyIfDockerDaemonPresent {
        val networkName = "docker-client-test-${Random.nextULong()}"
        val networkFromCreate = client.createNetwork(networkName, networkDriver)

        val networkFromGet = client.getNetworkByNameOrID(networkName)
        networkFromCreate shouldBe networkFromGet

        client.deleteNetwork(networkFromCreate)

        val networkAfterDelete = client.getNetworkByNameOrID(networkName)
        networkAfterDelete shouldBe null
    }

    should("fail when creating a network with the same name as an existing network").onlyIfDockerDaemonPresent {
        val networkName = "docker-client-test-${Random.nextULong()}"
        val network = client.createNetwork(networkName, networkDriver)

        try {
            val exception = shouldThrow<NetworkCreationFailedException> {
                client.createNetwork(networkName, networkDriver)
            }

            exception.message shouldBe "Error response from daemon: network with name $networkName already exists"
        } finally {
            client.deleteNetwork(network)
        }
    }

    should("return null when getting a network that does not exist").onlyIfDockerDaemonPresent {
        val network = client.getNetworkByNameOrID("this-network-does-not-exist")

        network shouldBe null
    }

    should("fail when deleting a network that does not exist").onlyIfDockerDaemonPresent {
        val exception = shouldThrow<NetworkDeletionFailedException> {
            client.deleteNetwork(NetworkReference("this-network-does-not-exist"))
        }

        exception.message shouldBe "No such network: this-network-does-not-exist"
    }
})
