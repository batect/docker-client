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

package batect.dockerclient.buildtools.golang

import batect.dockerclient.buildtools.ChecksumVerificationFailedException
import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import org.gradle.kotlin.dsl.create
import org.gradle.testfixtures.ProjectBuilder
import java.nio.file.Paths

class VerifyChecksumFromSingleChecksumFileSpec : ShouldSpec({
    val testFixturesPath = Paths.get("src", "test", "resources", "verify-checksum-from-single-checksum-file-test-fixtures").toAbsolutePath()

    fun createTask(targetFileName: String): VerifyChecksumFromSingleChecksumFile {
        val project = ProjectBuilder.builder()
            .withProjectDir(testFixturesPath.toFile())
            .build()

        return project.tasks.create<VerifyChecksumFromSingleChecksumFile>("testVerifyChecksum") {
            checksumFile.set(project.file("checksum.txt"))
            fileToVerify.set(project.file(targetFileName))
        }
    }

    should("pass when file and checksum match") {
        val task = createTask("matching.txt")

        shouldNotThrowAny { task.run() }
    }

    should("fail when file does not match checksum") {
        val task = createTask("not-matching.txt")

        val exception = shouldThrow<ChecksumVerificationFailedException> { task.run() }
        exception.message shouldBe "not-matching.txt is expected to have checksum 2e1b7cbd2af81da37243e5529b304e4e3dab6fc272e154f9128fdfba8ebffd8d, but has checksum d109110bade85113b836991fe0d2565c000d8b61257ea29f71177e8a01eab786."
    }
})
