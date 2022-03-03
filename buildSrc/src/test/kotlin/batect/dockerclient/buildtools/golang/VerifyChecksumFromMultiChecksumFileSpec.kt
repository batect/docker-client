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

import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import org.gradle.kotlin.dsl.create
import org.gradle.testfixtures.ProjectBuilder
import java.nio.file.Paths

class VerifyChecksumFromMultiChecksumFileSpec : ShouldSpec({
    val testFixturesPath = Paths.get("src", "test", "resources", "verify-checksum-from-multi-checksum-file-test-fixtures").toAbsolutePath()

    fun createTask(targetFileName: String): VerifyChecksumFromMultiChecksumFile {
        val project = ProjectBuilder.builder()
            .withProjectDir(testFixturesPath.toFile())
            .build()

        return project.tasks.create<VerifyChecksumFromMultiChecksumFile>("testVerifyChecksum") {
            checksumFile.set(project.file("checksums.txt"))
            fileToVerify.set(project.file(targetFileName))
        }
    }

    should("pass when file and checksum match") {
        val task = createTask("file-1.txt")

        shouldNotThrowAny { task.run() }
    }

    should("fail when file does not match checksum") {
        val task = createTask("file-2.txt")

        val exception = shouldThrow<ChecksumVerificationFailedException> { task.run() }
        exception.message shouldBe "file-2.txt is expected to have checksum 58d27a5dec87fc697c160b4e46ecbfd6abfee3c1e64a968b4a66ef61703eb73f, but has checksum 11b5da548eba9ff3e5934f8ceafe9916d9055d99488794599872586173249909."
    }

    should("fail when checksum file does not have a corresponding checksum") {
        val task = createTask("file-3.txt")

        val exception = shouldThrow<ChecksumVerificationFailedException> { task.run() }
        exception.message shouldBe "There is no checksum for file-3.txt in ${testFixturesPath.resolve("checksums.txt")}."
    }
})
