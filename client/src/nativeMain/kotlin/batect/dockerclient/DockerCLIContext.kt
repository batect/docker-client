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

package batect.dockerclient

import batect.dockerclient.native.DetermineActiveCLIContext
import batect.dockerclient.native.DetermineSelectedCLIContext
import kotlinx.cinterop.cstr
import kotlinx.cinterop.pointed
import kotlinx.cinterop.toKString
import okio.Path

@OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)
internal actual fun determineActiveCLIContext(dockerConfigurationDirectory: Path?): String {
    DetermineActiveCLIContext(dockerConfigurationDirectory?.toString()?.cstr)!!.use { ret ->
        if (ret.pointed.Error != null) {
            throw DockerClientException(ret.pointed.Error!!.pointed)
        }

        return ret.pointed.ContextName!!.toKString()
    }
}

@OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)
internal actual fun determineSelectedCLIContext(dockerConfigurationDirectory: Path?): String {
    DetermineSelectedCLIContext(dockerConfigurationDirectory?.toString()?.cstr)!!.use { ret ->
        if (ret.pointed.Error != null) {
            throw DockerClientException(ret.pointed.Error!!.pointed)
        }

        return ret.pointed.ContextName!!.toKString()
    }
}
