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

import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import java.nio.file.Paths

class VerifyZigChecksumSpec : ShouldSpec({
    val testFixturesPath = Paths.get("src", "test", "resources", "verify-zig-checksum-test-fixtures").toAbsolutePath().toFile()

    context("given a version present in the checksum file") {
        context("given a platform present in the checksum file") {
            should("pass when file and checksum match") {
                val runner = GradleRunner.create()
                    .withProjectDir(testFixturesPath)
                    .withArguments("verifyMatchingFile")
                    .withPluginClasspath()

                shouldNotThrowAny { runner.build() }
            }

            should("fail when file does not match checksum") {
                val result = GradleRunner.create()
                    .withProjectDir(testFixturesPath)
                    .withArguments("verifyNonMatchingFile")
                    .withPluginClasspath()
                    .buildAndFail()

                result.task(":verifyNonMatchingFile")!!.outcome shouldBe TaskOutcome.FAILED
                result.output shouldContain "${testFixturesPath.resolve("invalid.txt")} is expected to have checksum f427b9be3bcbad6d06551760ccb02ac02861cd0e3a0f8015e46d284ca78031d8, but has checksum c8f829da748dd3e094455afebd80fc92a99482ff9f92b0dd563d3157f25cba50."
            }
        }

        context("given a platform not present in the checksum file") {
            should("fail with an appropriate error message") {
                val result = GradleRunner.create()
                    .withProjectDir(testFixturesPath)
                    .withArguments("verifyFileWithNonExistentPlatform")
                    .withPluginClasspath()
                    .buildAndFail()

                result.task(":verifyFileWithNonExistentPlatform")!!.outcome shouldBe TaskOutcome.FAILED
                result.output shouldContain "Platform 'x86_64-darwin' not found for version '0.9.1' in ${testFixturesPath.resolve("checksums.json")}."
            }
        }

        context("given a platform value that does not correspond to a platform in the checksum file") {
            should("fail with an appropriate error message") {
                val result = GradleRunner.create()
                    .withProjectDir(testFixturesPath)
                    .withArguments("verifyFileWithNonPlatform")
                    .withPluginClasspath()
                    .buildAndFail()

                result.task(":verifyFileWithNonPlatform")!!.outcome shouldBe TaskOutcome.FAILED
                result.output shouldContain "'notes' is not a valid platform for version '0.9.1' in ${testFixturesPath.resolve("checksums.json")}."
            }
        }
    }

    context("given a version not present in the checksum file") {
        should("fail with an appropriate error message") {
            val result = GradleRunner.create()
                .withProjectDir(testFixturesPath)
                .withArguments("verifyFileWithNonExistentVersion")
                .withPluginClasspath()
                .buildAndFail()

            result.task(":verifyFileWithNonExistentVersion")!!.outcome shouldBe TaskOutcome.FAILED
            result.output shouldContain "Version '0.9.9' not found in ${testFixturesPath.resolve("checksums.json")}."
        }
    }
})
