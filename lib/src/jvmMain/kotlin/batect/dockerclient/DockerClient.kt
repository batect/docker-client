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

import jnr.ffi.LibraryLoader
import jnr.ffi.LibraryOption
import jnr.ffi.Platform
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

public actual class DockerClient : AutoCloseable {
    private val clientHandle: DockerClientHandle = createClient()

    private fun createClient(): DockerClientHandle {
        nativeAPI.CreateClient().use { ret ->
            if (ret.error != null) {
                throw DockerClientException(ret.error!!.message.get())
            }

            return ret.client.get()
        }
    }

    public actual fun ping(): PingResponse {
        nativeAPI.Ping(clientHandle).use { ret ->
            if (ret.error != null) {
                throw DockerClientException(ret.error!!.message.get())
            }

            val response = ret.response!!

            return PingResponse(
                response.apiVersion.get(),
                response.osType.get(),
                response.experimental.get(),
                response.builderVersion.get()
            )
        }
    }

    actual override fun close() {
        nativeAPI.DisposeClient(clientHandle)
    }
}

internal val nativeAPI: NativeAPI by lazy {
    val libraryDirectory = extractNativeLibrary()

    LibraryLoader
        .create(NativeAPI::class.java)
        .option(LibraryOption.LoadNow, true)
        .option(LibraryOption.IgnoreError, true)
        .search(libraryDirectory.toString())
        .library("dockerclientwrapper")
        .failImmediately()
        .load()
}

private fun extractNativeLibrary(): Path {
    val classLoader = DockerClient::class.java.classLoader
    val platform = "${Platform.getNativePlatform().os.name.lowercase()}/${Platform.getNativePlatform().cpu.name.lowercase()}"
    val libraryFileName = Platform.getNativePlatform().mapLibraryName("dockerclientwrapper")
    val resourcePath = "batect/dockerclient/libs/$platform/$libraryFileName"
    val stream = classLoader.getResourceAsStream(resourcePath) ?: throw UnsupportedOperationException("Platform '$platform' is not supported.")

    stream.use {
        val outputDirectory = Files.createTempDirectory("batect-docker-client")
        outputDirectory.toFile().deleteOnExit()

        val outputFile = outputDirectory.resolve(libraryFileName)
        Files.copy(it, outputFile)
        outputFile.toFile().deleteOnExit()

        println(outputFile)

        return outputDirectory
    }
}
