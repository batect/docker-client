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

package batect.dockerclient.samples.exec

import batect.dockerclient.ContainerExecSpec
import batect.dockerclient.ContainerReference
import batect.dockerclient.DockerClient
import batect.dockerclient.io.TextInput
import batect.dockerclient.io.TextOutput
import kotlinx.coroutines.runBlocking

fun main(args: Array<String>) = runBlocking {
    if (args.size != 1) {
        println("Please provide exactly one argument, the container ID.")
        exitProcess(1)
    }

    val client = DockerClient.Builder().build()
    val containerID = args[0]
    val container = ContainerReference(containerID)

    val spec = ContainerExecSpec.Builder(container)
        .withCommand("sh")
        .withStdoutAttached()
        .withStderrAttached()
        .withStdinAttached()
        .withTTYAttached()
        .build()

    println("Creating exec instance...")
    val exec = client.createExec(spec)

    println("Running exec instance...")
    client.startAndAttachToExec(exec, true, TextOutput.StandardOutput, TextOutput.StandardError, TextInput.StandardInput)

    println("Exec instance exited with code ${client.inspectExec(exec).exitCode}.")
}

internal expect fun exitProcess(exitCode: Int): Nothing
