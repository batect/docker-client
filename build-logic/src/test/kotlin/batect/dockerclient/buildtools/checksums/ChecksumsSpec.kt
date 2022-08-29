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

package batect.dockerclient.buildtools.checksums

import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.string.shouldMatch
import java.nio.file.Paths

class ChecksumsSpec : ShouldSpec({
    val fixturesRoot = Paths.get("src", "test", "resources", "checksum-test-fixtures").toAbsolutePath()

    context("verifying the checksum of a file") {
        context("given a file that matches the given hash") {
            should("not throw an exception") {
                shouldNotThrowAny {
                    verifyChecksum(fixturesRoot.resolve("valid.txt"), "f427b9be3bcbad6d06551760ccb02ac02861cd0e3a0f8015e46d284ca78031d8")
                }
            }
        }

        context("given a file that does not match the given hash") {
            should("throw an exception") {
                val file = fixturesRoot.resolve("invalid.txt")

                val exception = shouldThrow<ChecksumMismatchException> {
                    verifyChecksum(file, "f427b9be3bcbad6d06551760ccb02ac02861cd0e3a0f8015e46d284ca78031d8")
                }

                // Why use a regex here? Because we want this test to pass on both Windows and non-Windows machines and we don't
                // want to hard-code the absolute path to the file here.
                exception.message shouldMatch """(.*)[/\\]invalid\.txt is expected to have SHA256 hash f427b9be3bcbad6d06551760ccb02ac02861cd0e3a0f8015e46d284ca78031d8, but has hash c8f829da748dd3e094455afebd80fc92a99482ff9f92b0dd563d3157f25cba50\.$""".toRegex()
            }
        }
    }
})
