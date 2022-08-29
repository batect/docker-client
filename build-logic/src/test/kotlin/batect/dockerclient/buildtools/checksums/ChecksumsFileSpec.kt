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

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import java.nio.file.Paths

class ChecksumsFileSpec : ShouldSpec({
    val testFixturesPath = Paths.get("src", "test", "resources", "checksums-file-test-fixtures").toAbsolutePath()
    val checksumsFile = ChecksumsFile(testFixturesPath.resolve("checksums.txt"))

    context("given a filename present in the checksums file") {
        should("return the checksum for that file") {
            val checksum = checksumsFile.checksumForFile("file-2.txt")

            checksum shouldBe "58d27a5dec87fc697c160b4e46ecbfd6abfee3c1e64a968b4a66ef61703eb73f"
        }
    }

    context("given a filename not present in the checksums file") {
        should("throw an appropriate exception") {
            val exception = shouldThrow<ChecksumVerificationException> {
                checksumsFile.checksumForFile("file-3.txt")
            }

            exception.message shouldBe "There is no checksum for file-3.txt in ${checksumsFile.path}."
        }
    }
})
