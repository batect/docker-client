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
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldEndWith
import io.kotest.matchers.collections.shouldStartWith
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldMatch
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
            Step 1/2 : FROM alpine:\d+\.\d+.\d+
             ---> [0-9a-f]{12}
            Step 2/2 : RUN echo "Hello world!"
             ---> Running in [0-9a-f]{12}
            Hello world!
             ---> [0-9a-f]{12}
            Successfully built [0-9a-f]{12}
        """.trimIndent().toRegex()

        progressUpdatesReceived shouldStartWith StepStarting(0, "FROM alpine:3.14.2")

        progressUpdatesReceived shouldEndWith listOf(
            StepFinished(0),
            StepStarting(1, "RUN echo \"Hello world!\""),
            StepOutput(1, "Hello world!\n"),
            StepFinished(1),
            BuildComplete(image)
        )

        // TODO: assert on progress events
        // - context upload
        // TODO: verify returned image reference can be used to run image
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
        println(outputText)

        outputText.lines() shouldContain "First arg: first value"
        outputText.lines() shouldContain "Second arg: second value"
        outputText.lines() shouldContain "Third arg: third value"
    }

//    should("be able to build a Linux container image and force the base image to be re-pulled").onlyIfDockerDaemonSupportsLinuxContainers {
//        val spec = ImageBuildSpec.Builder(rootTestImagesDirectory.resolve("basic-image"))
//            .withBaseImageAlwaysPulled()
//            .build()
//
//        val output = Buffer()
//
//        client.buildImage(spec, output)
//
//        // TODO: assert on progress events
//        // TODO: assert pull progress is included in output
//    }

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
             ---> [0-9a-f]{12}
            Successfully built [0-9a-f]{12}
            Step 1/2 : FROM alpine:\d+\.\d+.\d+
             ---> [0-9a-f]{12}
            Step 2/2 : RUN echo "Hello world!"
             ---> Running in [0-9a-f]{12}
            Hello world!
             ---> [0-9a-f]{12}
            Successfully built [0-9a-f]{12}
        """.trimIndent().toRegex()
    }

    // TODO: absolute path to Dockerfile
    // TODO: .dockerignore
    // TODO: base image that requires authentication
    // TODO: proxy environment variables - CLI does some magic for this
    // TODO: multi-stage Dockerfile with default target stage
    // TODO: multi-stage Dockerfile with specified target stage
    // TODO: image build that fails due to command that exits with non-zero exit code
    // TODO: image build that fails due to non-existent base image
    // TODO: image build that downloads a file - report progress information
    // TODO: handle invalid values
    // - context directory does not exist
    // - Dockerfile does not exist
    // - Dockerfile not in context directory
    // - image tag not valid identifier
    // - invalid build arg name
})
