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

import io.kotest.core.spec.style.scopes.RootTestWithConfigBuilder
import io.kotest.core.test.TestContext
import okio.FileSystem

// FIXME: this is a temporary workaround until Okio supports HMPP (https://github.com/square/okio/pull/980)
// This isolates the IDE's complaints to this file, rather than having red squiggles in every file that uses the file system.
internal val systemFileSystem: FileSystem
    get() = FileSystem.SYSTEM

internal fun RootTestWithConfigBuilder.onlyIfDockerDaemonPresent(test: suspend TestContext.() -> Unit) =
    this.config(enabledIf = { dockerDaemonPresent }, test = test)

private val dockerDaemonPresent: Boolean
    get() = getEnvironmentVariable("DISABLE_DOCKER_DAEMON_TESTS") != "1"

internal fun RootTestWithConfigBuilder.onlyIfDockerDaemonSupportsLinuxContainers(test: suspend TestContext.() -> Unit) =
    this.onlyIfDockerDaemonSupportsLinuxContainersAnd(true, test)

internal fun RootTestWithConfigBuilder.onlyIfDockerDaemonSupportsLinuxContainersAnd(condition: Boolean, test: suspend TestContext.() -> Unit) =
    this.config(enabledIf = { dockerDaemonPresent && testEnvironmentContainerOperatingSystem == ContainerOperatingSystem.Linux && condition }, test = test)

internal fun RootTestWithConfigBuilder.onlyIfDockerDaemonSupportsWindowsContainers(test: suspend TestContext.() -> Unit) =
    this.config(enabledIf = { dockerDaemonPresent && testEnvironmentContainerOperatingSystem == ContainerOperatingSystem.Windows }, test = test)

internal val testEnvironmentContainerOperatingSystem: ContainerOperatingSystem
    get() = when (val value = getEnvironmentVariable("DOCKER_CONTAINER_OPERATING_SYSTEM")) {
        "windows" -> ContainerOperatingSystem.Windows
        null, "", "linux" -> ContainerOperatingSystem.Linux
        else -> throw IllegalArgumentException("Unknown value for 'DOCKER_CONTAINER_OPERATING_SYSTEM' environment variable: $value")
    }

expect val testEnvironmentOperatingSystem: OperatingSystem

expect fun getEnvironmentVariable(name: String): String?

enum class ContainerOperatingSystem {
    Linux,
    Windows
}

enum class OperatingSystem {
    Linux,
    Windows,
    MacOS
}

// FIXME: remove this once the new Kotlin/Native memory model can be used
internal expect val multithreadingSupportedOnThisPlatform: Boolean
