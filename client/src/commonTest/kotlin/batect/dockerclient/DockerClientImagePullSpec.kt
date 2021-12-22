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

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.inspectors.forAtLeastOne
import io.kotest.matchers.collections.shouldBeIn
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldEndWith
import io.kotest.matchers.collections.shouldStartWith
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

class DockerClientImagePullSpec : ShouldSpec({
    val client = closeAfterTest(DockerClient.Builder().build())

    val defaultLinuxTestImage = "gcr.io/distroless/static@sha256:aadea1b1f16af043a34491eec481d0132479382096ea34f608087b4bef3634be"
    val defaultWindowsTestImage = "mcr.microsoft.com/windows/nanoserver@sha256:4f06e1d8263b934d2e88dc1c6ff402f5b499c4d19ad6d0e2a5b9ee945f782928" // This is nanoserver:1809

    val testImages = when (testEnvironmentContainerOperatingSystem) {
        ContainerOperatingSystem.Linux -> mapOf(
            "with a digest and no tag" to defaultLinuxTestImage,
            "with a digest and tag" to "gcr.io/distroless/static:063a079c1a87bad3369cb9daf05e371e925c0c91@sha256:aadea1b1f16af043a34491eec481d0132479382096ea34f608087b4bef3634be",
            "with a tag and no digest" to "gcr.io/distroless/static:063a079c1a87bad3369cb9daf05e371e925c0c91",
            "with neither a digest nor a tag" to "gcr.io/distroless/static",

            // To recreate this image:
            //   docker pull gcr.io/distroless/static@sha256:aadea1b1f16af043a34491eec481d0132479382096ea34f608087b4bef3634be
            //   docker tag gcr.io/distroless/static@sha256:aadea1b1f16af043a34491eec481d0132479382096ea34f608087b4bef3634be ghcr.io/batect/docker-client:sample-authenticated-image
            //   docker push ghcr.io/batect/docker-client:sample-authenticated-image
            //
            // If you need to configure credentials locally: https://docs.github.com/en/packages/working-with-a-github-packages-registry/working-with-the-container-registry#authenticating-to-the-container-registry
            "that requires authentication to pull" to "ghcr.io/batect/docker-client:sample-authenticated-image"
        )
        ContainerOperatingSystem.Windows -> mapOf(
            "with a tag" to defaultWindowsTestImage
        )
    }

    val imageThatDoesNotExist = "batect/this-image-does-not-exist:abc123"

    beforeEach {
        testImages.values.forEach { image -> client.deleteImageIfPresent(image) }
    }

    testImages.forEach { (description, image) ->
        should("be able to pull, get and delete an image $description").onlyIfDockerDaemonPresent {
            val imageReferenceFromPull = client.pullImage(image)
            val imageReferenceFromGet = client.getImage(image)
            imageReferenceFromPull shouldBe imageReferenceFromGet

            client.deleteImage(imageReferenceFromPull)
            val imageReferenceAfterDelete = client.getImage(image)
            imageReferenceAfterDelete shouldBe null
        }
    }

    should("report progress information while pulling a Linux image").onlyIfDockerDaemonSupportsLinuxContainers {
        val image = defaultLinuxTestImage
        val progressUpdatesReceived = mutableListOf<ImagePullProgressUpdate>()

        client.pullImage(image) { update ->
            progressUpdatesReceived.add(update)
        }

        val layerId = "b49b96595fd4"
        val layerSize = 657696

        progressUpdatesReceived.forAtLeastOne {
            it.message shouldBe "Pulling from distroless/static"
            it.detail shouldBe null
            it.id shouldBeIn setOf(image, image.substringAfter('@')) // Older versions of Docker only return the digest here
        }

        progressUpdatesReceived shouldContain ImagePullProgressUpdate("Pulling fs layer", ImagePullProgressDetail(0, 0), layerId)

        progressUpdatesReceived.forAtLeastOne {
            it.message shouldBe "Downloading"
            it.detail shouldNotBe null
            it.detail!!.total shouldBe layerSize
            it.id shouldBe layerId
        }

        progressUpdatesReceived.forAtLeastOne {
            it.message shouldBe "Extracting"
            it.detail shouldNotBe null
            it.detail!!.total shouldBe layerSize
            it.id shouldBe layerId
        }

        progressUpdatesReceived shouldEndWith listOf(
            ImagePullProgressUpdate("Pull complete", ImagePullProgressDetail(0, 0), layerId),
            ImagePullProgressUpdate("Digest: sha256:aadea1b1f16af043a34491eec481d0132479382096ea34f608087b4bef3634be", null, ""),
            ImagePullProgressUpdate("Status: Downloaded newer image for $image", null, "")
        )
    }

    should("gracefully handle a progress callback that throws an exception while pulling an image").onlyIfDockerDaemonSupportsLinuxContainers {
        val exceptionThrownByCallbackHandler = RuntimeException("This is an exception from the callback handler")

        val exceptionThrownByPullMethod = shouldThrow<ImagePullFailedException> {
            client.pullImage(defaultLinuxTestImage) {
                throw exceptionThrownByCallbackHandler
            }
        }

        exceptionThrownByPullMethod.message shouldBe "Image pull progress receiver threw an exception: $exceptionThrownByCallbackHandler"
        exceptionThrownByPullMethod.cause shouldBe exceptionThrownByCallbackHandler
    }

    should("report progress information while pulling a Windows image").onlyIfDockerDaemonSupportsWindowsContainers {
        val image = defaultWindowsTestImage
        val progressUpdatesReceived = mutableListOf<ImagePullProgressUpdate>()

        client.pullImage(image) { update ->
            progressUpdatesReceived.add(update)
        }

        val layerId = "934e212983f2"
        val layerSize = 102661372

        progressUpdatesReceived shouldStartWith listOf(
            ImagePullProgressUpdate("Pulling from windows/nanoserver", null, image),
            ImagePullProgressUpdate("Pulling fs layer", ImagePullProgressDetail(0, 0), layerId),
        )

        progressUpdatesReceived.forAtLeastOne {
            it.message shouldBe "Downloading"
            it.detail shouldNotBe null
            it.detail!!.total shouldBe layerSize
            it.id shouldBe layerId
        }

        progressUpdatesReceived shouldContain ImagePullProgressUpdate("Download complete", ImagePullProgressDetail(0, 0), layerId)

        progressUpdatesReceived.forAtLeastOne {
            it.message shouldBe "Extracting"
            it.detail shouldNotBe null
            it.detail!!.total shouldBe layerSize
            it.id shouldBe layerId
        }

        progressUpdatesReceived shouldEndWith listOf(
            ImagePullProgressUpdate("Pull complete", ImagePullProgressDetail(0, 0), layerId),
            ImagePullProgressUpdate("Digest: sha256:4f06e1d8263b934d2e88dc1c6ff402f5b499c4d19ad6d0e2a5b9ee945f782928", null, ""),
            ImagePullProgressUpdate("Status: Downloaded newer image for $image", null, "")
        )
    }

    should("fail when pulling a non-existent image").onlyIfDockerDaemonPresent {
        val exception = shouldThrow<ImagePullFailedException> {
            client.pullImage(imageThatDoesNotExist)
        }

        // Docker returns a different error message depending on whether or not the user is logged in to the source registry
        exception.message shouldBeIn setOf(
            // User is logged in
            "Error response from daemon: manifest for batect/this-image-does-not-exist:abc123 not found: manifest unknown: manifest unknown",

            // User is not logged in
            "Error response from daemon: pull access denied for batect/this-image-does-not-exist, repository does not exist or may require 'docker login': denied: requested access to the resource is denied"
        )
    }

    should("fail when pulling an image for another platform").onlyIfDockerDaemonPresent {
        val imageForOtherPlatform = when (testEnvironmentContainerOperatingSystem) {
            ContainerOperatingSystem.Linux -> "mcr.microsoft.com/windows/nanoserver:ltsc2022"
            ContainerOperatingSystem.Windows -> "gcr.io/distroless/static:063a079c1a87bad3369cb9daf05e371e925c0c91@sha256:aadea1b1f16af043a34491eec481d0132479382096ea34f608087b4bef3634be"
        }

        val exception = shouldThrow<ImagePullFailedException> {
            client.pullImage(imageForOtherPlatform)
        }

        val expectedMessages = when (testEnvironmentContainerOperatingSystem) {
            ContainerOperatingSystem.Linux -> setOf(
                "no matching manifest for linux/amd64 in the manifest list entries",
                "no matching manifest for linux/arm64/v8 in the manifest list entries",
                "image operating system \"windows\" cannot be used on this platform"
            )
            ContainerOperatingSystem.Windows -> setOf(
                "no matching manifest for windows/amd64 in the manifest list entries",
                "image operating system \"linux\" cannot be used on this platform"
            )
        }

        exception.message shouldBeIn expectedMessages
    }

    should("return null when getting a non-existent image").onlyIfDockerDaemonPresent {
        val imageReference = client.getImage(imageThatDoesNotExist)
        imageReference shouldBe null
    }

    should("fail when deleting a non-existent image").onlyIfDockerDaemonPresent {
        val exception = shouldThrow<ImageDeletionFailedException> {
            client.deleteImage(ImageReference("this-image-does-not-exist"))
        }

        exception.message shouldBe "No such image: this-image-does-not-exist"
    }
})

internal fun DockerClient.deleteImageIfPresent(name: String) {
    val image = getImage(name)

    if (image != null) {
        deleteImage(image, force = true)
    }
}
