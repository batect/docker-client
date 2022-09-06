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

package batect.dockerclient.native

import batect.dockerclient.DockerClient
import jnr.ffi.LibraryLoader
import jnr.ffi.LibraryOption
import jnr.ffi.Platform
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

internal val nativeAPI: API by lazy {
    try {
        val systemTempDirectory = Files.createTempDirectory("batect-docker-client")
        extractAndLoadNativeLibrary(systemTempDirectory)
    } catch (e: UnsatisfiedLinkError) {
        // Try an alternative directory - see https://github.com/batect/batect/issues/1340
        val userHome = Paths.get(System.getProperty("user.home"), ".batect", "docker")
        Files.createDirectories(userHome)

        val userTempDirectory = Files.createTempDirectory(userHome, "batect-docker-client")
        extractAndLoadNativeLibrary(userTempDirectory)
    }
}

private fun extractAndLoadNativeLibrary(outputDirectory: Path): API {
    outputDirectory.toFile().deleteOnExit()
    extractNativeLibrary(outputDirectory)

    return loadNativeLibrary(outputDirectory)
}

private fun extractNativeLibrary(outputDirectory: Path) {
    val classLoader = DockerClient::class.java.classLoader
    val platform = "${Platform.getNativePlatform().os.name.lowercase()}/${Platform.getNativePlatform().cpu.name.lowercase()}"
    val libraryFileName = Platform.getNativePlatform().mapLibraryName("dockerclientwrapper")
    val resourcePath = "batect/dockerclient/libs/$platform/$libraryFileName"
    val stream = classLoader.getResourceAsStream(resourcePath) ?: throw UnsupportedOperationException("Platform '$platform' is not supported.")

    stream.use {
        val outputFile = outputDirectory.resolve(libraryFileName)
        Files.copy(it, outputFile)
        outputFile.toFile().deleteOnExit()
    }
}

private fun loadNativeLibrary(outputDirectory: Path): API {
    return LibraryLoader
        .create(API::class.java)
        .option(LibraryOption.LoadNow, true)
        .option(LibraryOption.IgnoreError, true)
        .option(LibraryOption.PreferCustomPaths, true)
        .search(outputDirectory.toString())
        .library("dockerclientwrapper")
        .failImmediately()
        .load()
}
