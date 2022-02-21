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
import io.kotest.matchers.collections.shouldContainInOrder
import io.kotest.matchers.collections.shouldEndWith
import io.kotest.matchers.longs.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldMatch
import io.kotest.matchers.types.shouldBeTypeOf
import okio.Buffer
import okio.Path
import okio.Path.Companion.toPath
import kotlin.random.Random

class DockerClientBuildKitImageBuildSpec : ShouldSpec({
    val rootTestImagesDirectory: Path = systemFileSystem.canonicalize("./src/commonTest/resources/images".toPath())
    val client = closeAfterTest(DockerClient.Builder().build())

    should("be able to build a basic Linux container image").onlyIfDockerDaemonSupportsLinuxContainers {
        val spec = ImageBuildSpec.Builder(rootTestImagesDirectory.resolve("basic-image"))
            .withBuildKitBuilder()
            .withNoBuildCache()
            .build()

        val output = Buffer()
        val progressUpdatesReceived = mutableListOf<ImageBuildProgressUpdate>()

        val image = client.buildImage(spec, SinkTextOutput(output)) { update ->
            progressUpdatesReceived.add(update)
        }

        val outputText = output.readUtf8().trim()

        outputText shouldContain """
            |#1 \[internal] load build definition from Dockerfile
            |#1 transferring dockerfile: \d+B (\d+\.\d+s )?done
            |#1 DONE \d+\.\d+s
            |
        """.trimMargin().toRegex()

        outputText shouldContain """
            |#2 \[internal] load .dockerignore
            |#2 transferring context: 2B (\d+\.\d+s )?done
            |#2 DONE \d+\.\d+s
            |
        """.trimMargin().toRegex()

        outputText shouldContain """
            |#3 \[internal] load metadata for docker.io/library/alpine:3.14.2
            |#3 DONE \d+\.\d+s
            |
        """.trimMargin().toRegex()

        outputText shouldContain """
            |#(\d) \[1/2] FROM docker.io/library/alpine:3.14.2(@sha256:e1c082e3d3c45cccac829840a25941e679c25d438cc8412c2fa221cf1a824e6a)?
            |(#\1 resolve docker.io/library/alpine:3.14.2@sha256:e1c082e3d3c45cccac829840a25941e679c25d438cc8412c2fa221cf1a824e6a (\d+\.\d+s )?done
            |(#\1 .*
            |)*)?#\1 (DONE \d+\.\d+s|CACHED)
            |
        """.trimMargin().toRegex()

        outputText shouldContain """
            |#(\d) \[2/2] RUN echo "Hello world!"
            |#\1 \d+\.\d+ Hello world!
            |#\1 DONE \d+\.\d+s
            |
        """.trimMargin().toRegex()

        outputText shouldContain """
            |#(\d) exporting to image
            |(#\1 exporting layers
            |)?#\1 exporting layers (\d+\.\d+s )?done
            |(#\1 writing image sha256:[0-9a-f]{64}
            |)?#\1 writing image sha256:[0-9a-f]{64} done
            |#\1 DONE \d+\.\d+s
        """.trimMargin().toRegex()

        progressUpdatesReceived shouldContainInOrder listOf(
            StepStarting(1, "[internal] load build definition from Dockerfile"),
            StepContextUploadProgress(1, 0),
            StepFinished(1),
        )

        progressUpdatesReceived.forAtLeastOne {
            it.shouldBeTypeOf<StepContextUploadProgress>()
            it.stepNumber shouldBe 1
            it.bytesUploaded shouldBeGreaterThan 0
        }

        progressUpdatesReceived shouldContainInOrder listOf(
            StepStarting(2, "[internal] load .dockerignore"),
            StepContextUploadProgress(2, 0),
            StepContextUploadProgress(2, 2),
            StepFinished(2),
        )

        progressUpdatesReceived shouldContainInOrder listOf(
            StepStarting(3, "[internal] load metadata for docker.io/library/alpine:3.14.2"),
            StepFinished(3),
        )

        progressUpdatesReceived shouldContainAnyOf setOf(
            StepStarting(4, "[1/2] FROM docker.io/library/alpine:3.14.2"),
            StepStarting(4, "[1/2] FROM docker.io/library/alpine:3.14.2@sha256:e1c082e3d3c45cccac829840a25941e679c25d438cc8412c2fa221cf1a824e6a"),
        )

        progressUpdatesReceived shouldEndWith listOf(
            StepFinished(4),
            StepStarting(5, "[2/2] RUN echo \"Hello world!\""),
            StepOutput(5, "Hello world!\n"),
            StepFinished(5),
            StepStarting(6, "exporting to image"),
            StepFinished(6),
            BuildComplete(image)
        )
    }

    should("be able to build a Linux container image using files in the build context").onlyIfDockerDaemonSupportsLinuxContainers {
        val spec = ImageBuildSpec.Builder(rootTestImagesDirectory.resolve("image-with-context"))
            .withBuildKitBuilder()
            .withBuildArg("CACHE_BUSTER", Random.nextInt().toString())
            .build()

        val output = Buffer()
        client.buildImage(spec, SinkTextOutput(output))

        val outputText = output.readUtf8()
        val stepNumber = outputText.findStepNumberForStep("RUN tree -J --noreport -u -g -s --sort=name /files")

        val directoryListing = outputText.lines()
            .filter { it.startsWith("#$stepNumber ") && !it.startsWith("#$stepNumber [") && !it.startsWith("#$stepNumber DONE ") }
            .joinToString("\n") { it.removePrefix("#$stepNumber ").substringAfter(' ') }

        directoryListing shouldContain """
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
            .withBuildKitBuilder()
            .withBuildArg("CACHE_BUSTER", Random.nextInt().toString())
            .build()

        val output = Buffer()
        client.buildImage(spec, SinkTextOutput(output))

        val outputText = output.readUtf8()
        val stepNumber = outputText.findStepNumberForStep("RUN tree -J --noreport -u -g -s --sort=name /files")

        val directoryListing = outputText.lines()
            .filter { it.startsWith("#$stepNumber ") && !it.startsWith("#$stepNumber [") && !it.startsWith("#$stepNumber DONE ") }
            .joinToString("\n") { it.removePrefix("#$stepNumber ").substringAfter(' ') }

        directoryListing shouldContain """
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
            .withBuildKitBuilder()
            .withDockerfile("subdirectory/my-dockerfile".toPath())
            .build()

        val output = Buffer()
        client.buildImage(spec, SinkTextOutput(output))

        val outputText = output.readUtf8()
        outputText shouldContain "RUN echo This is the non-default Dockerfile"
    }

    should("be able to build a Linux container image and pass build args to the build process").onlyIfDockerDaemonSupportsLinuxContainers {
        val spec = ImageBuildSpec.Builder(rootTestImagesDirectory.resolve("build-args"))
            .withBuildKitBuilder()
            .withNoBuildCache()
            .withBuildArg("FIRST_ARG", "first value")
            .withBuildArgs("SECOND_ARG" to "second value")
            .withBuildArgs(mapOf("THIRD_ARG" to "third value"))
            .build()

        val output = Buffer()
        client.buildImage(spec, SinkTextOutput(output))

        val outputText = output.readUtf8()
        outputText shouldContain """^#\d+ \d+\.\d+ First arg: first value$""".toRegex(RegexOption.MULTILINE)
        outputText shouldContain """^#\d+ \d+\.\d+ Second arg: second value$""".toRegex(RegexOption.MULTILINE)
        outputText shouldContain """^#\d+ \d+\.\d+ Third arg: third value$""".toRegex(RegexOption.MULTILINE)
    }

    context("using a base image not present on the machine") {
        val imageTag = "batect-docker-client-buildkit-image-build-pull-progress"
        val contextDirectory = rootTestImagesDirectory.resolve("buildkit-pull-progress")
        val dockerfile = contextDirectory.resolve("Dockerfile")

        beforeEach {
            client.deleteImageIfPresent(imageTag)
            client.removeBaseImagesIfPresent(dockerfile)

            // BuildKit still caches too aggressively with the 'no build cache' and 'always pull' options - this appears to be the only way to ensure that the image is really pulled as part of this test.
            client.pruneImageBuildCache()
        }

        should("be able to build a Linux container image and report image pull progress").onlyIfDockerDaemonSupportsLinuxContainers {
            val spec = ImageBuildSpec.Builder(contextDirectory)
                .withBuildKitBuilder()
                .withBaseImageAlwaysPulled()
                .withNoBuildCache()
                .withImageTag(imageTag)
                .build()

            val output = Buffer()
            val progressUpdatesReceived = mutableListOf<ImageBuildProgressUpdate>()

            val image = client.buildImage(spec, SinkTextOutput(output)) { update ->
                progressUpdatesReceived.add(update)
            }

            val outputText = output.readUtf8().trim()
            val imageReference = "ghcr.io/batect/docker-client:buildkit-image-build-pull-progress@sha256:8a6789a0ff3df495ee00c05ecd89825305b37003b6b2fc1178afc23d7d186e23"

            outputText shouldContain """
                #(\d+) \[1/1] FROM $imageReference
                #\1 resolve $imageReference (\d+\.\d+s )?done.*
            """.trimIndent().toRegex()

            val stepNumber = outputText.findStepNumberForStep("FROM $imageReference")

            outputText shouldContain """#$stepNumber sha256:8a6789a0ff3df495ee00c05ecd89825305b37003b6b2fc1178afc23d7d186e23 523B / 523B (\d+\.\d+s )?done""".toRegex()
            outputText shouldContain """#$stepNumber sha256:704eee611c18ef00a526edb37f0e25d467e86b07c427fdb4af9964300bcd2a7e 459B / 459B (\d+\.\d+s )?done""".toRegex()
            outputText shouldContain """#$stepNumber sha256:0c85f017ab24df430248de7f3859b83fb16b763e93e419bc105d7caa810e4ea1 136B / 136B (\d+\.\d+s )?done""".toRegex()
            outputText shouldContain """#$stepNumber extracting sha256:0c85f017ab24df430248de7f3859b83fb16b763e93e419bc105d7caa810e4ea1 (\d+\.\d+s )?done""".toRegex()
            outputText shouldContain """#$stepNumber DONE \d+\.\d+s""".trimIndent().toRegex()

            val layerId = "sha256:0c85f017ab24df430248de7f3859b83fb16b763e93e419bc105d7caa810e4ea1"
            val layerSize = 136

            progressUpdatesReceived.forAtLeastOne {
                it.shouldBeTypeOf<StepPullProgressUpdate>()
                it.stepNumber shouldBe stepNumber
                it.pullProgress.message shouldBe "downloading"
                it.pullProgress.detail shouldNotBe null
                it.pullProgress.detail!!.total shouldBe layerSize
                it.pullProgress.id shouldBe layerId
            }

            progressUpdatesReceived.forAtLeastOne {
                it.shouldBeTypeOf<StepPullProgressUpdate>()
                it.stepNumber shouldBe stepNumber
                it.pullProgress.message shouldBe "extract"
                it.pullProgress.detail shouldNotBe null
                it.pullProgress.detail!!.total shouldBe 0
                it.pullProgress.id shouldBe layerId
            }

            progressUpdatesReceived.forAtLeastOne {
                it.shouldBeTypeOf<StepPullProgressUpdate>()
                it.stepNumber shouldBe stepNumber
                it.pullProgress.message shouldBe "done"
                it.pullProgress.detail shouldNotBe null
                it.pullProgress.detail!!.total shouldBe layerSize
                it.pullProgress.id shouldBe layerId
            }

            progressUpdatesReceived shouldEndWith listOf(
                StepFinished(stepNumber),
                StepStarting(stepNumber + 1, "exporting to image"),
                StepFinished(stepNumber + 1),
                BuildComplete(image)
            )
        }
    }

    should("be able to build a Linux container image and tag it").onlyIfDockerDaemonSupportsLinuxContainers {
        val imageTag1 = "batect-docker-client/image-build-test:1"
        val imageTag2 = "batect-docker-client/image-build-test:2"

        client.deleteImageIfPresent(imageTag1)
        client.deleteImageIfPresent(imageTag2)

        val spec = ImageBuildSpec.Builder(rootTestImagesDirectory.resolve("basic-image"))
            .withBuildKitBuilder()
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
            .withBuildKitBuilder()
            .withNoBuildCache()
            .build()

        val output = Buffer()
        val sink = SinkTextOutput(output)
        client.buildImage(spec, sink)
        client.buildImage(spec, sink)

        val outputText = output.readUtf8().trim()
        val singleBuildOutputPattern = """
            #1 \[internal] load build definition from Dockerfile
            #1 transferring dockerfile: 36B (\d+\.\d+s )?done
            #1 DONE \d+\.\d+s

            #2 \[internal] load .dockerignore
            #2 transferring context: 2B (\d+\.\d+s )?done
            #2 DONE \d+\.\d+s

            #3 \[internal] load metadata for docker.io/library/alpine:3.14.2
            #3 DONE \d+\.\d+s

            #4 \[1/2] FROM docker.io/library/alpine:3.14.2(@sha256:e1c082e3d3c45cccac829840a25941e679c25d438cc8412c2fa221cf1a824e6a)?
            #4 (DONE \d+\.\d+s|CACHED)

            #5 \[2/2] RUN echo "Hello world!"
            #5 \d+\.\d+ Hello world!
            #5 DONE \d+\.\d+s

            #6 exporting to image
            (#6 exporting layers
            )?#6 exporting layers (\d+\.\d+s )?done
            (#6 writing image sha256:[0-9a-f]{64}
            )?#6 writing image sha256:[0-9a-f]{64} done
            #6 DONE \d+\.\d+s
        """.trimIndent()

        outputText shouldMatch """($singleBuildOutputPattern\s*){2}""".toRegex()
    }

    should("be able to build a Linux container image that uses a base image that requires authentication").onlyIfDockerDaemonSupportsLinuxContainers {
        val spec = ImageBuildSpec.Builder(rootTestImagesDirectory.resolve("authenticated-base-image"))
            .withBuildKitBuilder()
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
            .withBuildKitBuilder()
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
            .withBuildKitBuilder()
            .build()

        val output = Buffer()
        val progressUpdatesReceived = mutableListOf<ImageBuildProgressUpdate>()

        val image = client.buildImage(spec, SinkTextOutput(output)) { update ->
            progressUpdatesReceived.add(update)
        }

        val outputText = output.readUtf8().trim()
        val outputTextLines = outputText.lines()

        outputTextLines shouldContain "#1 [internal] load build definition from Dockerfile"
        outputTextLines shouldContain "#2 [internal] load .dockerignore"
        outputTextLines shouldContain "#3 [internal] load metadata for docker.io/library/alpine:3.14.2"

        outputTextLines shouldContainAnyOf setOf(
            "#4 [other 1/2] FROM docker.io/library/alpine:3.14.2",
            "#4 [other 1/2] FROM docker.io/library/alpine:3.14.2@sha256:e1c082e3d3c45cccac829840a25941e679c25d438cc8412c2fa221cf1a824e6a",
            "#4 [stage-1 1/2] FROM docker.io/library/alpine:3.14.2",
            "#4 [stage-1 1/2] FROM docker.io/library/alpine:3.14.2@sha256:e1c082e3d3c45cccac829840a25941e679c25d438cc8412c2fa221cf1a824e6a"
        )

        outputTextLines shouldContain "#5 [other 2/2] RUN touch /file-from-other"
        outputTextLines shouldContain "#6 [stage-1 2/2] COPY --from=other /file-from-other /received/file-from-other"
        outputTextLines shouldContain "#7 exporting to image"
        outputText shouldContain "^#7 writing image sha256:[0-9a-f]{64} done$".toRegex(RegexOption.MULTILINE)

        progressUpdatesReceived shouldContain StepStarting(1, "[internal] load build definition from Dockerfile")
        progressUpdatesReceived shouldContain StepStarting(2, "[internal] load .dockerignore")
        progressUpdatesReceived shouldContain StepStarting(3, "[internal] load metadata for docker.io/library/alpine:3.14.2")

        progressUpdatesReceived shouldContainAnyOf setOf(
            StepStarting(4, "[other 1/2] FROM docker.io/library/alpine:3.14.2"),
            StepStarting(4, "[other 1/2] FROM docker.io/library/alpine:3.14.2@sha256:e1c082e3d3c45cccac829840a25941e679c25d438cc8412c2fa221cf1a824e6a"),
            StepStarting(4, "[stage-1 1/2] FROM docker.io/library/alpine:3.14.2"),
            StepStarting(4, "[stage-1 1/2] FROM docker.io/library/alpine:3.14.2@sha256:e1c082e3d3c45cccac829840a25941e679c25d438cc8412c2fa221cf1a824e6a")
        )

        progressUpdatesReceived shouldContain StepStarting(5, "[other 2/2] RUN touch /file-from-other")
        progressUpdatesReceived shouldContain StepStarting(6, "[stage-1 2/2] COPY --from=other /file-from-other /received/file-from-other")
        progressUpdatesReceived shouldContain StepStarting(7, "exporting to image")
        progressUpdatesReceived shouldEndWith BuildComplete(image)
    }

    should("be able to build a specific stage of a multi-stage Linux container image").onlyIfDockerDaemonSupportsLinuxContainers {
        val spec = ImageBuildSpec.Builder(rootTestImagesDirectory.resolve("multistage-with-failing-default-stage"))
            .withBuildKitBuilder()
            .withTargetBuildStage("other")
            .build()

        val output = Buffer()
        val progressUpdatesReceived = mutableListOf<ImageBuildProgressUpdate>()

        val image = client.buildImage(spec, SinkTextOutput(output)) { update ->
            progressUpdatesReceived.add(update)
        }

        val outputTextLines = output.readUtf8().trim().lines()
        outputTextLines shouldContainAnyOf setOf("#4 [other 1/2] FROM docker.io/library/alpine:3.14.2", "#4 [other 1/2] FROM docker.io/library/alpine:3.14.2@sha256:e1c082e3d3c45cccac829840a25941e679c25d438cc8412c2fa221cf1a824e6a")
        outputTextLines shouldContain "#5 [other 2/2] RUN touch /file-from-other"

        progressUpdatesReceived shouldContainAnyOf setOf(StepStarting(4, "[other 1/2] FROM docker.io/library/alpine:3.14.2"), StepStarting(4, "[other 1/2] FROM docker.io/library/alpine:3.14.2@sha256:e1c082e3d3c45cccac829840a25941e679c25d438cc8412c2fa221cf1a824e6a"))
        progressUpdatesReceived shouldContain StepStarting(5, "[other 2/2] RUN touch /file-from-other")

        progressUpdatesReceived.forNone {
            it.shouldBeTypeOf<StepStarting>()
            it.stepName shouldContain "This stage should never be used!"
        }

        progressUpdatesReceived shouldEndWith BuildComplete(image)
    }

    should("be able to build a Linux container image with a failing RUN step").onlyIfDockerDaemonSupportsLinuxContainers {
        val spec = ImageBuildSpec.Builder(rootTestImagesDirectory.resolve("failing-command"))
            .withBuildKitBuilder()
            .build()

        val output = Buffer()
        val progressUpdatesReceived = mutableListOf<ImageBuildProgressUpdate>()

        val exception = shouldThrow<ImageBuildFailedException> {
            client.buildImage(spec, SinkTextOutput(output)) { update ->
                progressUpdatesReceived.add(update)
            }
        }

        exception.message shouldBe "executor failed running [/bin/sh -c echo \"This command has failed!\" && exit 1]: exit code: 1"

        val outputText = output.readUtf8().trim()

        outputText shouldContain """
            ^#5 \[2/2] RUN echo "This command has failed!" && exit 1
            #5 \d+\.\d+ This command has failed!
            #5 ERROR: executor failed running \[/bin/sh -c echo "This command has failed!" && exit 1]: exit code: 1
            ------
             > \[2/2] RUN echo "This command has failed!" && exit 1:
            #5 \d+\.\d+ This command has failed!
            ------$
        """.trimIndent().toRegex(RegexOption.MULTILINE)

        progressUpdatesReceived shouldContainAnyOf setOf(StepStarting(4, "[1/2] FROM docker.io/library/alpine:3.14.2"), StepStarting(4, "[1/2] FROM docker.io/library/alpine:3.14.2@sha256:e1c082e3d3c45cccac829840a25941e679c25d438cc8412c2fa221cf1a824e6a"))
        progressUpdatesReceived shouldContain StepStarting(5, "[2/2] RUN echo \"This command has failed!\" && exit 1")
        progressUpdatesReceived shouldContain BuildFailed("executor failed running [/bin/sh -c echo \"This command has failed!\" && exit 1]: exit code: 1")

        progressUpdatesReceived.forNone {
            it.shouldBeTypeOf<BuildComplete>()
        }
    }

    should("be able to build a Linux container image with a non-existent image").onlyIfDockerDaemonSupportsLinuxContainers {
        val spec = ImageBuildSpec.Builder(rootTestImagesDirectory.resolve("failing-base-image"))
            .withBuildKitBuilder()
            .build()

        val output = Buffer()
        val progressUpdatesReceived = mutableListOf<ImageBuildProgressUpdate>()

        val exception = shouldThrow<ImageBuildFailedException> {
            client.buildImage(spec, SinkTextOutput(output)) { update ->
                progressUpdatesReceived.add(update)
            }
        }

        exception.message shouldBeIn setOf(
            "failed to solve with frontend dockerfile.v0: failed to create LLB definition: docker.io/batect/this-image-does-not-exist:1.0: not found",
            "failed to solve with frontend dockerfile.v0: failed to create LLB definition: pull access denied, repository does not exist or may require authorization: server message: insufficient_scope: authorization failed"
        )

        val outputText = output.readUtf8().trim()
        outputText.lines() shouldContain "#3 [internal] load metadata for docker.io/batect/this-image-does-not-exist:1.0"

        progressUpdatesReceived shouldContain StepStarting(3, "[internal] load metadata for docker.io/batect/this-image-does-not-exist:1.0")
        progressUpdatesReceived shouldContainAnyOf setOf(
            BuildFailed("failed to solve with frontend dockerfile.v0: failed to create LLB definition: docker.io/batect/this-image-does-not-exist:1.0: not found"),
            BuildFailed("failed to solve with frontend dockerfile.v0: failed to create LLB definition: pull access denied, repository does not exist or may require authorization: server message: insufficient_scope: authorization failed")
        )

        progressUpdatesReceived.forNone {
            it.shouldBeTypeOf<BuildComplete>()
        }
    }

    // Note that BuildKit does not support reporting progress information for file downloads.
    should("be able to build a Linux container image that downloads a file").onlyIfDockerDaemonSupportsLinuxContainers {
        val spec = ImageBuildSpec.Builder(rootTestImagesDirectory.resolve("file-download"))
            .withBuildKitBuilder()
            .withNoBuildCache()
            .build()

        val output = Buffer()
        val progressUpdatesReceived = mutableListOf<ImageBuildProgressUpdate>()

        val image = client.buildImage(spec, SinkTextOutput(output)) { update ->
            progressUpdatesReceived.add(update)
        }

        val outputText = output.readUtf8().trim().lines()

        outputText shouldContainAnyOf setOf(
            "#4 [1/2] FROM docker.io/library/alpine:3.14.2",
            "#4 [1/2] FROM docker.io/library/alpine:3.14.2@sha256:e1c082e3d3c45cccac829840a25941e679c25d438cc8412c2fa221cf1a824e6a",
            "#5 [1/2] FROM docker.io/library/alpine:3.14.2",
            "#5 [1/2] FROM docker.io/library/alpine:3.14.2@sha256:e1c082e3d3c45cccac829840a25941e679c25d438cc8412c2fa221cf1a824e6a",
        )

        outputText shouldContainAnyOf setOf("#4 https://httpbin.org/drip?duration=1&numbytes=2048&code=200&delay=0", "#5 https://httpbin.org/drip?duration=1&numbytes=2048&code=200&delay=0")
        outputText shouldContain "#6 [2/2] ADD https://httpbin.org/drip?duration=1&numbytes=2048&code=200&delay=0 /file.txt"

        progressUpdatesReceived shouldContainAnyOf setOf(
            StepStarting(4, "[1/2] FROM docker.io/library/alpine:3.14.2"),
            StepStarting(4, "[1/2] FROM docker.io/library/alpine:3.14.2@sha256:e1c082e3d3c45cccac829840a25941e679c25d438cc8412c2fa221cf1a824e6a"),
            StepStarting(5, "[1/2] FROM docker.io/library/alpine:3.14.2"),
            StepStarting(5, "[1/2] FROM docker.io/library/alpine:3.14.2@sha256:e1c082e3d3c45cccac829840a25941e679c25d438cc8412c2fa221cf1a824e6a"),
        )

        progressUpdatesReceived shouldContainAnyOf setOf(StepStarting(4, "https://httpbin.org/drip?duration=1&numbytes=2048&code=200&delay=0"), StepStarting(5, "https://httpbin.org/drip?duration=1&numbytes=2048&code=200&delay=0"))
        progressUpdatesReceived shouldEndWith BuildComplete(image)
    }

    should("gracefully handle a progress callback that throws an exception while building an image").onlyIfDockerDaemonSupportsLinuxContainers {
        val exceptionThrownByCallbackHandler = RuntimeException("This is an exception from the callback handler")

        val exceptionThrownByBuildMethod = shouldThrow<ImageBuildFailedException> {
            val spec = ImageBuildSpec.Builder(rootTestImagesDirectory.resolve("basic-image"))
                .withBuildKitBuilder()
                .build()

            val output = Buffer()

            client.buildImage(spec, SinkTextOutput(output)) { update ->
                if (update !is ImageBuildContextUploadProgress) {
                    throw exceptionThrownByCallbackHandler
                }
            }
        }

        exceptionThrownByBuildMethod.message shouldBe "Image build progress receiver threw an exception: $exceptionThrownByCallbackHandler"
        exceptionThrownByBuildMethod.cause shouldBe exceptionThrownByCallbackHandler
    }

    should("propagate configured proxy settings to the build").onlyIfDockerDaemonSupportsLinuxContainers {
        setClientProxySettingsForTest(client)

        val spec = ImageBuildSpec.Builder(rootTestImagesDirectory.resolve("proxy-reporter"))
            .withBuildKitBuilder()
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

private fun String.findStepNumberForStep(step: String): Long {
    val regex = """^#\d+ \[.*] ${Regex.escape(step)}$""".toRegex()

    val match = this.lines()
        .firstOrNull() { it.matches(regex) }

    if (match == null) {
        throw RuntimeException("Could not find step '$step' in output: $this")
    }

    return match.substringBefore(' ').substringAfter('#')
        .toLong()
}

private fun readFileContents(path: Path): String =
    systemFileSystem.read(path) {
        return readUtf8()
    }
