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

import batect.dockerclient.io.SinkTextOutput
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.inspectors.forAtLeastOne
import io.kotest.inspectors.forNone
import io.kotest.matchers.collections.shouldBeIn
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldContainAnyOf
import io.kotest.matchers.collections.shouldEndWith
import io.kotest.matchers.collections.shouldStartWith
import io.kotest.matchers.longs.shouldBeLessThan
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldMatch
import io.kotest.matchers.string.shouldNotContain
import io.kotest.matchers.types.shouldBeTypeOf
import okio.Buffer
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath
import kotlin.random.Random
import kotlin.time.ExperimentalTime

@ExperimentalTime
class DockerClientImageBuildSpec : ShouldSpec({
    val rootTestImagesDirectory: Path = FileSystem.SYSTEM.canonicalize("./src/commonTest/resources/images".toPath())
    val client = closeAfterTest(DockerClient.Builder().build())

    should("be able to build a basic Linux container image").onlyIfDockerDaemonSupportsLinuxContainers {
        val spec = ImageBuildSpec.Builder(rootTestImagesDirectory.resolve("basic-image"))
            .withNoBuildCache()
            .build()

        val output = Buffer()
        val progressUpdatesReceived = mutableListOf<ImageBuildProgressUpdate>()

        val image = client.buildImage(spec, SinkTextOutput(output)) { update ->
            progressUpdatesReceived.add(update)
        }

        val outputText = output.readUtf8().trim()

        outputText shouldMatch """
            Step 1/2 : FROM alpine:\d+\.\d+.\d+(
            \d+\.\d+.\d+: Pulling from library/alpine
            ([a-f0-9]{12}: (Pulling fs layer|Verifying Checksum|Download complete|Extracting|Pull complete)
            )*Digest: sha256:[0-9a-f]{64}
            Status: Downloaded newer image for alpine:\d+\.\d+\.\d+)?
             ---> [0-9a-f]{12}
            Step 2/2 : RUN echo "Hello world!"
             ---> Running in [0-9a-f]{12}
            Hello world!
            Removing intermediate container [0-9a-f]{12}
             ---> [0-9a-f]{12}
            Successfully built [0-9a-f]{12}
        """.trimIndent().toRegex()

        if (multithreadingSupportedOnThisPlatform) {
            progressUpdatesReceived shouldStartWith listOf(
                ImageBuildContextUploadProgress(2048),
                StepStarting(1, "FROM alpine:3.14.2"),
            )
        } else {
            progressUpdatesReceived shouldStartWith StepStarting(1, "FROM alpine:3.14.2")
        }

        progressUpdatesReceived shouldEndWith listOf(
            StepFinished(1),
            StepStarting(2, "RUN echo \"Hello world!\""),
            StepOutput(2, "Hello world!\n"),
            StepFinished(2),
            BuildComplete(image)
        )
    }

    should("be able to build a Linux container image using files in the build context").onlyIfDockerDaemonSupportsLinuxContainers {
        val spec = ImageBuildSpec.Builder(rootTestImagesDirectory.resolve("image-with-context"))
            .withBuildArg("CACHE_BUSTER", Random.nextInt().toString())
            .build()

        val output = Buffer()
        client.buildImage(spec, SinkTextOutput(output))

        val outputText = output.readUtf8()

        outputText shouldContain """
            [
              {"type":"directory","name":"/files","contents":[
                {"type":"file","name":"Dockerfile","user":"root","group":"root","size":179},
                {"type":"file","name":"root-file.txt","user":"root","group":"root","size":25},
                {"type":"directory","name":"subdir","user":"root","group":"root","size":4096,"contents":[
                  {"type":"file","name":"my-file.txt","user":"root","group":"root","size":28}
                ]}
              ]}
            ]
        """.trimIndent()
    }

    should("be able to build a Linux container image with .dockerignore respected").onlyIfDockerDaemonSupportsLinuxContainers {
        val spec = ImageBuildSpec.Builder(rootTestImagesDirectory.resolve("image-with-dockerignore"))
            .withBuildArg("CACHE_BUSTER", Random.nextInt().toString())
            .build()

        val output = Buffer()
        client.buildImage(spec, SinkTextOutput(output))

        val outputText = output.readUtf8()

        outputText shouldContain """
            [
              {"type":"directory","name":"/files","contents":[
                {"type":"file","name":"Dockerfile","user":"root","group":"root","size":179},
                {"type":"file","name":"root-file.txt","user":"root","group":"root","size":25},
                {"type":"directory","name":"subdir","user":"root","group":"root","size":4096,"contents":[
                  {"type":"file","name":"my-file.txt","user":"root","group":"root","size":28}
                ]}
              ]}
            ]
        """.trimIndent()
    }

    should("be able to build a Linux container image with a non-default Dockerfile name").onlyIfDockerDaemonSupportsLinuxContainers {
        val spec = ImageBuildSpec.Builder(rootTestImagesDirectory.resolve("non-default-dockerfile"))
            .withDockerfile("subdirectory/my-dockerfile".toPath())
            .build()

        val output = Buffer()
        client.buildImage(spec, SinkTextOutput(output))

        val outputText = output.readUtf8()
        outputText shouldContain "RUN echo This is the non-default Dockerfile"
    }

    should("be able to build a Linux container image and pass build args to the build process").onlyIfDockerDaemonSupportsLinuxContainers {
        val spec = ImageBuildSpec.Builder(rootTestImagesDirectory.resolve("build-args"))
            .withNoBuildCache()
            .withBuildArg("FIRST_ARG", "first value")
            .withBuildArgs("SECOND_ARG" to "second value")
            .withBuildArgs(mapOf("THIRD_ARG" to "third value"))
            .build()

        val output = Buffer()
        client.buildImage(spec, SinkTextOutput(output))

        val outputText = output.readUtf8()
        outputText.lines() shouldContain "First arg: first value"
        outputText.lines() shouldContain "Second arg: second value"
        outputText.lines() shouldContain "Third arg: third value"
    }

    should("be able to build a Linux container image and force the base image to be re-pulled").onlyIfDockerDaemonSupportsLinuxContainers {
        val contextDirectory = rootTestImagesDirectory.resolve("force-repull-base-image")
        val dockerfile = contextDirectory.resolve("Dockerfile")
        client.removeBaseImagesIfPresent(dockerfile)

        val spec = ImageBuildSpec.Builder(contextDirectory)
            .withBaseImageAlwaysPulled()
            .build()

        val output = Buffer()
        val progressUpdatesReceived = mutableListOf<ImageBuildProgressUpdate>()

        val image = client.buildImage(spec, SinkTextOutput(output)) { update ->
            progressUpdatesReceived.add(update)
        }

        val outputText = output.readUtf8().trim()

        outputText shouldContain """
            Step 1/1 : FROM gcr.io/distroless/static@sha256:aadea1b1f16af043a34491eec481d0132479382096ea34f608087b4bef3634be
        """.trimIndent()

        outputText shouldContain """
            b49b96595fd4: Pull complete
            Digest: sha256:aadea1b1f16af043a34491eec481d0132479382096ea34f608087b4bef3634be
            Status: Downloaded newer image for gcr.io/distroless/static@sha256:aadea1b1f16af043a34491eec481d0132479382096ea34f608087b4bef3634be
        """.trimIndent()

        val imageReference = "gcr.io/distroless/static@sha256:aadea1b1f16af043a34491eec481d0132479382096ea34f608087b4bef3634be"
        val layerId = "b49b96595fd4"
        val layerSize = 657696

        progressUpdatesReceived.forAtLeastOne {
            it.shouldBeTypeOf<StepPullProgressUpdate>()
            it.stepNumber shouldBe 1
            it.pullProgress.message shouldBe "Pulling from distroless/static"
            it.pullProgress.detail shouldBe null
            it.pullProgress.id shouldBeIn setOf(imageReference, imageReference.substringAfter('@')) // Older versions of Docker only return the digest here
        }

        progressUpdatesReceived shouldContain StepPullProgressUpdate(1, ImagePullProgressUpdate("Pulling fs layer", ImagePullProgressDetail(0, 0), layerId))

        progressUpdatesReceived.forAtLeastOne {
            it.shouldBeTypeOf<StepPullProgressUpdate>()
            it.stepNumber shouldBe 1
            it.pullProgress.message shouldBe "Downloading"
            it.pullProgress.detail shouldNotBe null
            it.pullProgress.detail!!.total shouldBe layerSize
            it.pullProgress.id shouldBe layerId
        }

        progressUpdatesReceived.forAtLeastOne {
            it.shouldBeTypeOf<StepPullProgressUpdate>()
            it.stepNumber shouldBe 1
            it.pullProgress.message shouldBe "Extracting"
            it.pullProgress.detail shouldNotBe null
            it.pullProgress.detail!!.total shouldBe layerSize
            it.pullProgress.id shouldBe layerId
        }

        progressUpdatesReceived shouldEndWith listOf(
            StepPullProgressUpdate(1, ImagePullProgressUpdate("Pull complete", ImagePullProgressDetail(0, 0), layerId)),
            StepPullProgressUpdate(1, ImagePullProgressUpdate("Digest: sha256:aadea1b1f16af043a34491eec481d0132479382096ea34f608087b4bef3634be", null, "")),
            StepPullProgressUpdate(1, ImagePullProgressUpdate("Status: Downloaded newer image for $imageReference", null, "")),
            StepFinished(1),
            BuildComplete(image)
        )
    }

    should("be able to build a Linux container image and tag it").onlyIfDockerDaemonSupportsLinuxContainers {
        val imageTag1 = "batect-docker-client/image-build-test:1"
        val imageTag2 = "batect-docker-client/image-build-test:2"

        client.deleteImageIfPresent(imageTag1)
        client.deleteImageIfPresent(imageTag2)

        val spec = ImageBuildSpec.Builder(rootTestImagesDirectory.resolve("basic-image"))
            .withImageTag(imageTag1)
            .withImageTags(setOf(imageTag2))
            .build()

        val output = Buffer()
        client.buildImage(spec, SinkTextOutput(output))

        client.getImage(imageTag1) shouldNotBe null
        client.getImage(imageTag2) shouldNotBe null
    }

    should("be able to reuse a SinkTextOutput instance").onlyIfDockerDaemonSupportsLinuxContainers {
        val spec = ImageBuildSpec.Builder(rootTestImagesDirectory.resolve("basic-image"))
            .withNoBuildCache()
            .build()

        val output = Buffer()
        val sink = SinkTextOutput(output)
        client.buildImage(spec, sink)
        client.buildImage(spec, sink)

        val outputText = output.readUtf8().trim()

        outputText shouldMatch """
            Step 1/2 : FROM alpine:\d+\.\d+.\d+
             ---> [0-9a-f]{12}
            Step 2/2 : RUN echo "Hello world!"
             ---> Running in [0-9a-f]{12}
            Hello world!
            Removing intermediate container [0-9a-f]{12}
             ---> [0-9a-f]{12}
            Successfully built [0-9a-f]{12}
            Step 1/2 : FROM alpine:\d+\.\d+.\d+
             ---> [0-9a-f]{12}
            Step 2/2 : RUN echo "Hello world!"
             ---> Running in [0-9a-f]{12}
            Hello world!
            Removing intermediate container [0-9a-f]{12}
             ---> [0-9a-f]{12}
            Successfully built [0-9a-f]{12}
        """.trimIndent().toRegex()
    }

    should("be able to build a Linux container image that uses a base image that requires authentication").onlyIfDockerDaemonSupportsLinuxContainers {
        val spec = ImageBuildSpec.Builder(rootTestImagesDirectory.resolve("authenticated-base-image"))
            .withBaseImageAlwaysPulled()
            .build()

        val output = Buffer()
        val progressUpdatesReceived = mutableListOf<ImageBuildProgressUpdate>()

        val image = client.buildImage(spec, SinkTextOutput(output)) { update ->
            progressUpdatesReceived.add(update)
        }

        progressUpdatesReceived shouldEndWith BuildComplete(image)
    }

    should("be able to build a Linux container image where the Dockerfile path is specified with an absolute path").onlyIfDockerDaemonSupportsLinuxContainers {
        val contextDirectory = rootTestImagesDirectory.resolve("basic-image")
        val spec = ImageBuildSpec.Builder(contextDirectory)
            .withDockerfile(contextDirectory.resolve("Dockerfile"))
            .build()

        val output = Buffer()
        val progressUpdatesReceived = mutableListOf<ImageBuildProgressUpdate>()

        val image = client.buildImage(spec, SinkTextOutput(output)) { update ->
            progressUpdatesReceived.add(update)
        }

        progressUpdatesReceived shouldEndWith BuildComplete(image)
    }

    should("be able to build a multi-stage Linux container image").onlyIfDockerDaemonSupportsLinuxContainers {
        val spec = ImageBuildSpec.Builder(rootTestImagesDirectory.resolve("multistage"))
            .build()

        val output = Buffer()
        val progressUpdatesReceived = mutableListOf<ImageBuildProgressUpdate>()

        val image = client.buildImage(spec, SinkTextOutput(output)) { update ->
            progressUpdatesReceived.add(update)
        }

        val outputText = output.readUtf8().trim()
        outputText shouldContain """^Step 1/4 : FROM alpine:3.14.2 AS other$""".toRegex(RegexOption.MULTILINE)
        outputText shouldContain """^Step 2/4 : RUN touch /file-from-other$""".toRegex(RegexOption.MULTILINE)
        outputText shouldContain """^Step 3/4 : FROM alpine:3.14.2$""".toRegex(RegexOption.MULTILINE)
        outputText shouldContain """^Step 4/4 : COPY --from=other /file-from-other /received/file-from-other$""".toRegex(RegexOption.MULTILINE)
        outputText shouldContain """^Successfully built [0-9a-f]{12}$""".toRegex(RegexOption.MULTILINE)

        progressUpdatesReceived shouldContain StepStarting(1, "FROM alpine:3.14.2 AS other")
        progressUpdatesReceived shouldContain StepStarting(2, "RUN touch /file-from-other")
        progressUpdatesReceived shouldContain StepStarting(3, "FROM alpine:3.14.2")
        progressUpdatesReceived shouldContain StepStarting(4, "COPY --from=other /file-from-other /received/file-from-other")
        progressUpdatesReceived shouldEndWith BuildComplete(image)
    }

    should("be able to build a specific stage of a multi-stage Linux container image").onlyIfDockerDaemonSupportsLinuxContainers {
        val spec = ImageBuildSpec.Builder(rootTestImagesDirectory.resolve("multistage-with-failing-default-stage"))
            .withTargetBuildStage("other")
            .build()

        val output = Buffer()
        val progressUpdatesReceived = mutableListOf<ImageBuildProgressUpdate>()

        val image = client.buildImage(spec, SinkTextOutput(output)) { update ->
            progressUpdatesReceived.add(update)
        }

        val outputText = output.readUtf8().trim()
        outputText shouldContain """^Step 1/2 : FROM alpine:3.14.2 AS other$""".toRegex(RegexOption.MULTILINE)
        outputText shouldContain """^Step 2/2 : RUN touch /file-from-other$""".toRegex(RegexOption.MULTILINE)
        outputText shouldContain """^Successfully built [0-9a-f]{12}$""".toRegex(RegexOption.MULTILINE)

        progressUpdatesReceived shouldContain StepStarting(1, "FROM alpine:3.14.2 AS other")
        progressUpdatesReceived shouldContain StepStarting(2, "RUN touch /file-from-other")

        progressUpdatesReceived.forNone {
            it.shouldBeTypeOf<StepStarting>()
            it.stepName shouldBe "FROM alpine:3.14.2"
        }

        progressUpdatesReceived shouldEndWith BuildComplete(image)
    }

    should("be able to build a Linux container image with a failing RUN step").onlyIfDockerDaemonSupportsLinuxContainers {
        val spec = ImageBuildSpec.Builder(rootTestImagesDirectory.resolve("failing-command"))
            .build()

        val output = Buffer()
        val progressUpdatesReceived = mutableListOf<ImageBuildProgressUpdate>()

        val exception = shouldThrow<ImageBuildFailedException> {
            client.buildImage(spec, SinkTextOutput(output)) { update ->
                progressUpdatesReceived.add(update)
            }
        }

        exception.message shouldBe "The command '/bin/sh -c echo \"This command has failed!\" && exit 1' returned a non-zero code: 1"

        val outputText = output.readUtf8().trim()
        outputText shouldContain """^Step 1/2 : FROM alpine:3.14.2$""".toRegex(RegexOption.MULTILINE)

        outputText shouldContain """
            ^Step 2/2 : RUN echo "This command has failed!" && exit 1
             ---> Running in [0-9a-f]{12}
            This command has failed!$
        """.trimIndent().toRegex(RegexOption.MULTILINE)

        outputText shouldNotContain """^Successfully built [0-9a-f]{12}$""".toRegex(RegexOption.MULTILINE)

        progressUpdatesReceived shouldContain StepStarting(1, "FROM alpine:3.14.2")
        progressUpdatesReceived shouldContain StepStarting(2, "RUN echo \"This command has failed!\" && exit 1")
        progressUpdatesReceived shouldContain BuildFailed("The command '/bin/sh -c echo \"This command has failed!\" && exit 1' returned a non-zero code: 1")

        progressUpdatesReceived.forNone {
            it.shouldBeTypeOf<BuildComplete>()
        }
    }

    should("be able to build a Linux container image with a non-existent image").onlyIfDockerDaemonSupportsLinuxContainers {
        val spec = ImageBuildSpec.Builder(rootTestImagesDirectory.resolve("failing-base-image"))
            .build()

        val output = Buffer()
        val progressUpdatesReceived = mutableListOf<ImageBuildProgressUpdate>()

        val exception = shouldThrow<ImageBuildFailedException> {
            client.buildImage(spec, SinkTextOutput(output)) { update ->
                progressUpdatesReceived.add(update)
            }
        }

        exception.message shouldBeIn setOf(
            "manifest for batect/this-image-does-not-exist:1.0 not found: manifest unknown: manifest unknown",
            "pull access denied for batect/this-image-does-not-exist, repository does not exist or may require 'docker login': denied: requested access to the resource is denied"
        )

        val outputText = output.readUtf8().trim()
        outputText shouldContain """^Step 1/2 : FROM batect/this-image-does-not-exist:1.0$""".toRegex(RegexOption.MULTILINE)
        outputText shouldNotContain """^Successfully built [0-9a-f]{12}$""".toRegex(RegexOption.MULTILINE)

        progressUpdatesReceived shouldContain StepStarting(1, "FROM batect/this-image-does-not-exist:1.0")

        progressUpdatesReceived.shouldContainAnyOf(
            BuildFailed("manifest for batect/this-image-does-not-exist:1.0 not found: manifest unknown: manifest unknown"),
            BuildFailed("pull access denied for batect/this-image-does-not-exist, repository does not exist or may require 'docker login': denied: requested access to the resource is denied"),
        )

        progressUpdatesReceived.forNone {
            it.shouldBeTypeOf<BuildComplete>()
        }
    }

    should("be able to build a Linux container image that downloads a file and report download progress").onlyIfDockerDaemonSupportsLinuxContainers {
        val spec = ImageBuildSpec.Builder(rootTestImagesDirectory.resolve("file-download"))
            .withNoBuildCache()
            .build()

        val output = Buffer()
        val progressUpdatesReceived = mutableListOf<ImageBuildProgressUpdate>()

        val image = client.buildImage(spec, SinkTextOutput(output)) { update ->
            progressUpdatesReceived.add(update)
        }

        val outputText = output.readUtf8().trim()

        outputText shouldMatch """
            Step 1/2 : FROM alpine:3.14.2
             ---> [0-9a-f]{12}
            Step 2/2 : ADD "https://httpbin.org/drip\?duration=1&numbytes=2048&code=200&delay=0" /file.txt\n*
             ---> [0-9a-f]{12}
            Successfully built [0-9a-f]{12}
        """.trimIndent().toRegex()

        progressUpdatesReceived shouldContain StepStarting(1, "FROM alpine:3.14.2")
        progressUpdatesReceived shouldContain StepStarting(2, "ADD \"https://httpbin.org/drip?duration=1&numbytes=2048&code=200&delay=0\" /file.txt")

        progressUpdatesReceived.forAtLeastOne {
            it.shouldBeTypeOf<StepDownloadProgressUpdate>()
            it.stepNumber shouldBe 2
            it.bytesDownloaded shouldBeLessThan 2048
            it.totalBytes shouldBe 2048
        }

        progressUpdatesReceived shouldContain StepDownloadProgressUpdate(2, 2048, 2048)
        progressUpdatesReceived shouldEndWith BuildComplete(image)
    }

    should("throw an exception when the provided context directory does not exist") {
        val exception = shouldThrow<InvalidImageBuildSpecException> {
            ImageBuildSpec.Builder("this-does-not-exist".toPath())
        }

        exception.message shouldBe "Context directory 'this-does-not-exist' does not exist."
    }

    should("throw an exception when the provided Dockerfile does not exist") {
        val contextDirectory = rootTestImagesDirectory.resolve("basic-image")
        val dockerfilePath = contextDirectory.resolve("my-other-dockerfile")

        val exception = shouldThrow<InvalidImageBuildSpecException> {
            ImageBuildSpec.Builder(contextDirectory)
                .withDockerfile(dockerfilePath)
                .build()
        }

        exception.message shouldBe "Dockerfile '$dockerfilePath' does not exist."
    }

    should("throw an exception when the default Dockerfile does not exist in the provided context directory") {
        val contextDirectory = rootTestImagesDirectory.resolve("non-default-dockerfile")
        val dockerfilePath = contextDirectory.resolve("Dockerfile")

        val exception = shouldThrow<InvalidImageBuildSpecException> {
            ImageBuildSpec.Builder(contextDirectory).build()
        }

        exception.message shouldBe "Dockerfile '$dockerfilePath' does not exist."
    }

    should("throw an exception when the Dockerfile is not in the context directory") {
        val contextDirectory = rootTestImagesDirectory.resolve("basic-image")
        val dockerfilePath = rootTestImagesDirectory.resolve("build-args").resolve("Dockerfile")

        val exception = shouldThrow<InvalidImageBuildSpecException> {
            ImageBuildSpec.Builder(contextDirectory)
                .withDockerfile(dockerfilePath)
                .build()
        }

        exception.message shouldBe "Dockerfile '$dockerfilePath' is not a child of the context directory ($contextDirectory)."
    }

    should("throw an exception when attempting to build an image with an invalid image tag") {
        val exception = shouldThrow<InvalidImageBuildSpecException> {
            ImageBuildSpec.Builder(rootTestImagesDirectory.resolve("basic-image"))
                .withImageTag("_nonsense")
        }

        exception.message shouldBe "Image tag '_nonsense' is not a valid Docker image tag: invalid reference format"
    }

    should("throw an exception when attempting to build an image with an invalid image tag in a list of image tags provided as a collection") {
        val exception = shouldThrow<InvalidImageBuildSpecException> {
            ImageBuildSpec.Builder(rootTestImagesDirectory.resolve("basic-image"))
                .withImageTags(setOf("valid", "_nonsense", "also-valid"))
        }

        exception.message shouldBe "Image tag '_nonsense' is not a valid Docker image tag: invalid reference format"
    }

    should("throw an exception when attempting to build an image with an invalid image tag in a list of image tag arguments") {
        val exception = shouldThrow<InvalidImageBuildSpecException> {
            ImageBuildSpec.Builder(rootTestImagesDirectory.resolve("basic-image"))
                .withImageTags("valid", "_nonsense", "also-valid")
        }

        exception.message shouldBe "Image tag '_nonsense' is not a valid Docker image tag: invalid reference format"
    }

    should("gracefully handle a progress callback that throws an exception while building an image").onlyIfDockerDaemonSupportsLinuxContainers {
        val exceptionThrownByCallbackHandler = RuntimeException("This is an exception from the callback handler")

        val exceptionThrownByPullMethod = shouldThrow<ImageBuildFailedException> {
            val spec = ImageBuildSpec.Builder(rootTestImagesDirectory.resolve("basic-image"))
                .build()

            val output = Buffer()

            client.buildImage(spec, SinkTextOutput(output)) { update ->
                if (update !is ImageBuildContextUploadProgress) {
                    throw exceptionThrownByCallbackHandler
                }
            }
        }

        exceptionThrownByPullMethod.message shouldBe "Image build progress receiver threw an exception: $exceptionThrownByCallbackHandler"
        exceptionThrownByPullMethod.cause shouldBe exceptionThrownByCallbackHandler
    }

    should("gracefully handle a progress callback that throws an exception while uploading build context").onlyIfDockerDaemonSupportsLinuxContainersAnd(
        multithreadingSupportedOnThisPlatform // FIXME: context upload progress reporting is not supported on Kotlin/Native yet.
    ) {
        val exceptionThrownByCallbackHandler = RuntimeException("This is an exception from the callback handler")

        val exceptionThrownByPullMethod = shouldThrow<ImageBuildFailedException> {
            val spec = ImageBuildSpec.Builder(rootTestImagesDirectory.resolve("basic-image"))
                .build()

            val output = Buffer()

            client.buildImage(spec, SinkTextOutput(output)) { update ->
                if (update is ImageBuildContextUploadProgress) {
                    throw exceptionThrownByCallbackHandler
                }
            }
        }

        exceptionThrownByPullMethod.message shouldBe "Image build progress receiver threw an exception: $exceptionThrownByCallbackHandler"
        exceptionThrownByPullMethod.cause shouldBe exceptionThrownByCallbackHandler
    }

    should("propagate configured proxy settings to the build").onlyIfDockerDaemonSupportsLinuxContainers {
        setClientProxySettingsForTest(client)

        val spec = ImageBuildSpec.Builder(rootTestImagesDirectory.resolve("proxy-reporter"))
            .withNoBuildCache()
            .build()

        val output = Buffer()

        client.buildImage(spec, SinkTextOutput(output))

        val outputText = output.readUtf8().trim()

        outputText shouldContain "Value of FTP_PROXY is https://ftp-proxy"
        outputText shouldContain "Value of ftp_proxy is https://ftp-proxy"
        outputText shouldContain "Value of HTTPS_PROXY is https://https-proxy"
        outputText shouldContain "Value of https_proxy is https://https-proxy"
        outputText shouldContain "Value of HTTP_PROXY is https://http-proxy"
        outputText shouldContain "Value of http_proxy is https://http-proxy"
        outputText shouldContain "Value of NO_PROXY is https://no-proxy"
        outputText shouldContain "Value of no_proxy is https://no-proxy"
    }
})

private fun DockerClient.removeBaseImagesIfPresent(dockerfile: Path) {
    val dockerfileContent = readFileContents(dockerfile)
    val fromRegex = """^FROM ([a-zA-Z0-9./_-]+(:[a-zA-Z0-9./_-]+))?$""".toRegex(RegexOption.MULTILINE)

    fromRegex.findAll(dockerfileContent).forEach { match ->
        deleteImageIfPresent(match.groupValues[1])
    }
}

private fun readFileContents(path: Path): String =
    FileSystem.SYSTEM.read(path) {
        return readUtf8()
    }

internal expect fun setClientProxySettingsForTest(client: DockerClient)