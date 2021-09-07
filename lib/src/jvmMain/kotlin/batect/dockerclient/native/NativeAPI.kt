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

package batect.dockerclient.native

import batect.dockerclient.DockerClient
import jnr.ffi.LibraryLoader
import jnr.ffi.LibraryOption
import jnr.ffi.Platform
import jnr.ffi.annotations.In
import java.nio.file.Files
import java.nio.file.Path

@Suppress("FunctionName")
internal interface NativeAPI {
    fun Ping(@In handle: DockerClientHandle): PingReturn
    fun CreateClient(): CreateClientReturn
    fun DisposeClient(@In handle: DockerClientHandle)

    fun FreeCreateClientReturn(@In value: CreateClientReturn)
    fun FreePingReturn(@In value: PingReturn)
    fun FreeError(@In value: Error)
    fun FreePingResponse(@In value: PingResponse)
}

internal val nativeAPI: NativeAPI by lazy {
    val libraryDirectory = extractNativeLibrary()

    LibraryLoader
        .create(NativeAPI::class.java)
        .option(LibraryOption.LoadNow, true)
        .option(LibraryOption.IgnoreError, true)
        .option(LibraryOption.PreferCustomPaths, true)
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

        return outputDirectory
    }
}
