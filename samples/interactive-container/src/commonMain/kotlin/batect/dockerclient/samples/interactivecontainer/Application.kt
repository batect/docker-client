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

@file:Suppress("ktlint:filename")

package batect.dockerclient.samples.interactivecontainer

import batect.dockerclient.ContainerCreationSpec
import batect.dockerclient.DockerClient
import batect.dockerclient.ImageReference
import batect.dockerclient.io.TextInput
import batect.dockerclient.io.TextOutput
import batect.dockerclient.run
import kotlinx.coroutines.runBlocking

fun main() = runBlocking {
    val client = DockerClient.Builder().build()
    val image = pullImage(client)
    runContainer(client, image)
}

private suspend fun pullImage(client: DockerClient): ImageReference {
    println("Pulling image...")

    return client.pullImage("ubuntu:22.04") {
        println(it)
    }
}

private suspend fun runContainer(client: DockerClient, image: ImageReference) {
    println("Creating container...")

    val spec = ContainerCreationSpec.Builder(image)
        .withTTY()
        .withStdinAttached()
        .build()

    val container = client.createContainer(spec)

    try {
        println("Running container...")
        val exitCode = client.run(container, TextOutput.StandardOutput, TextOutput.StandardError, TextInput.StandardInput)

        println("Container exited with code $exitCode.")
    } finally {
        client.removeContainer(container, force = true)
    }
}
