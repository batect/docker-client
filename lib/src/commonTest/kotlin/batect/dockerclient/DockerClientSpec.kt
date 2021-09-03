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

import io.kotest.assertions.asClue
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.collections.shouldBeIn
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldMatch
import kotlin.time.ExperimentalTime

@ExperimentalTime
class DockerClientSpec : ShouldSpec({
    val client = closeAfterTest(DockerClient())

    should("be able to ping the daemon").onlyIfDockerDaemonPresent {
        val response = client.ping()

        response.asClue {
            it.apiVersion shouldMatch """^\d\.\d+$""".toRegex()
            it.osType shouldBeIn setOf("linux", "windows")
            it.experimental shouldBe false
            it.builderVersion shouldBeIn setOf("1", "2", "")
        }
    }
})
