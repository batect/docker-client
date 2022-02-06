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
import io.kotest.matchers.shouldBe
import okio.Path
import okio.Path.Companion.toPath

class ImageBuildSpecSpec : ShouldSpec({
    val rootTestImagesDirectory: Path = systemFileSystem.canonicalize("./src/commonTest/resources/images".toPath())

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
})
