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

package batect.dockerclient.buildtools.zig

import batect.dockerclient.buildtools.ChecksumVerificationFailedException
import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import org.gradle.kotlin.dsl.create
import org.gradle.testfixtures.ProjectBuilder
import java.nio.file.Paths

class VerifyZigChecksumSpec : ShouldSpec({
    val testFixturesPath = Paths.get("src", "test", "resources", "verify-zig-checksum-test-fixtures").toAbsolutePath()

    fun createTask(targetFileName: String, version: String, platform: String): VerifyZigChecksum {
        val project = ProjectBuilder.builder()
            .withProjectDir(testFixturesPath.toFile())
            .build()

        return project.tasks.create<VerifyZigChecksum>("testVerifyChecksum") {
            checksumFile.set(project.file("checksums.json"))
            fileToVerify.set(project.file(targetFileName))
            zigVersion.set(version)
            zigPlatformName.set(platform)
        }
    }

    context("given a version present in the checksum file") {
        val version = "0.9.1"

        context("given a platform present in the checksum file") {
            val platform = "x86_64-linux"

            should("pass when file and checksum match") {
                val task = createTask("valid.txt", version, platform)

                shouldNotThrowAny { task.run() }
            }

            should("fail when file does not match checksum") {
                val task = createTask("invalid.txt", version, platform)

                val exception = shouldThrow<ChecksumVerificationFailedException> { task.run() }
                exception.message shouldBe "${testFixturesPath.resolve("invalid.txt")} is expected to have checksum f427b9be3bcbad6d06551760ccb02ac02861cd0e3a0f8015e46d284ca78031d8, but has checksum c8f829da748dd3e094455afebd80fc92a99482ff9f92b0dd563d3157f25cba50."
            }
        }

        context("given a platform not present in the checksum file") {
            val platform = "x86_64-darwin"

            should("fail with an appropriate error message") {
                val task = createTask("valid.txt", version, platform)

                val exception = shouldThrow<ChecksumVerificationFailedException> { task.run() }
                exception.message shouldBe "Platform 'x86_64-darwin' not found for version '0.9.1' in ${testFixturesPath.resolve("checksums.json")}."
            }
        }

        context("given a platform value that does not correspond to a platform in the checksum file") {
            val platform = "notes"

            should("fail with an appropriate error message") {
                val task = createTask("valid.txt", version, platform)

                val exception = shouldThrow<ChecksumVerificationFailedException> { task.run() }
                exception.message shouldBe "'notes' is not a valid platform for version '0.9.1' in ${testFixturesPath.resolve("checksums.json")}."
            }
        }
    }

    context("given a version not present in the checksum file") {
        val version = "0.9.9"
        val platform = "x86_64-linux"

        should("fail with an appropriate error message") {
            val task = createTask("valid.txt", version, platform)

            val exception = shouldThrow<ChecksumVerificationFailedException> { task.run() }
            exception.message shouldBe "Version '0.9.9' not found in ${testFixturesPath.resolve("checksums.json")}."
        }
    }
})
