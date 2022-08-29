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

package batect.dockerclient.buildtools.zig

import batect.dockerclient.buildtools.checksums.ChecksumVerificationException
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import java.nio.file.Paths

class ZigChecksumsFileSpec : ShouldSpec({
    context("retrieving checksums from a Zig checksums file") {
        val testFixturesPath = Paths.get("src", "test", "resources", "zig-checksums-file-test-fixtures").toAbsolutePath()
        val checksumsFile = ZigChecksumsFile(testFixturesPath.resolve("checksums.json"))

        context("given a version present in the checksum file") {
            context("given a platform present in the checksum file") {
                should("return the checksum for that version's archive on that platform") {
                    val checksum = checksumsFile.archiveChecksumForVersionAndPlatform("0.9.1", "x86_64-linux")

                    checksum shouldBe "f427b9be3bcbad6d06551760ccb02ac02861cd0e3a0f8015e46d284ca78031d8"
                }
            }

            context("given a platform not present in the checksum file") {
                should("throw an appropriate exception") {
                    val exception = shouldThrow<ChecksumVerificationException> {
                        checksumsFile.archiveChecksumForVersionAndPlatform("0.9.1", "x86_64-darwin")
                    }

                    exception.message shouldBe "Platform 'x86_64-darwin' not found for version '0.9.1' in ${checksumsFile.path}."
                }
            }

            context("given a platform value that does not correspond to a platform in the checksum file") {
                should("throw an appropriate exception") {
                    val exception = shouldThrow<ChecksumVerificationException> {
                        checksumsFile.archiveChecksumForVersionAndPlatform("0.9.1", "notes")
                    }

                    exception.message shouldBe "'notes' is not a valid platform for version '0.9.1' in ${checksumsFile.path}."
                }
            }
        }

        context("given a version not present in the checksum file") {
            should("throw an appropriate exception") {
                val exception = shouldThrow<ChecksumVerificationException> {
                    checksumsFile.archiveChecksumForVersionAndPlatform("0.9.9", "x86_64-linux")
                }

                exception.message shouldBe "Version '0.9.9' not found in ${checksumsFile.path}."
            }
        }
    }
})
