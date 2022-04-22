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

package batect.dockerclient.buildtools

import okio.HashingSource
import okio.blackholeSink
import okio.buffer
import okio.source
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.workers.WorkAction
import org.gradle.workers.WorkParameters
import java.io.File
import java.nio.file.Files

abstract class VerifyChecksum : WorkAction<VerifyChecksumParameters> {
    override fun execute() {
        val expectedChecksum = parameters.expectedChecksum.get()
        val fileToVerify = parameters.fileToVerify.get().asFile
        val actualChecksum = computeActualChecksum(fileToVerify)

        if (actualChecksum != expectedChecksum) {
            throw ChecksumVerificationFailedException("$fileToVerify is expected to have checksum $expectedChecksum, but has checksum $actualChecksum.")
        }
    }

    private fun computeActualChecksum(fileToVerify: File): String {
        Files.newInputStream(fileToVerify.toPath()).use { inputStream ->
            inputStream.source().use { source ->
                HashingSource.sha256(source).use { hashed ->
                    hashed.buffer().use { buffered ->
                        buffered.readAll(blackholeSink())

                        return hashed.hash.hex()
                    }
                }
            }
        }
    }
}

interface VerifyChecksumParameters : WorkParameters {
    val expectedChecksum: Property<String>
    val fileToVerify: RegularFileProperty
}
