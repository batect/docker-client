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

@file:Suppress("ktlint:filename")

package batect.dockerclient.buildtools.checksums

import okio.HashingSource
import okio.blackholeSink
import okio.buffer
import okio.source
import java.nio.file.Files
import java.nio.file.Path

fun verifyChecksum(file: Path, expectedSHA256Hash: String) {
    val actualHash = computeSHA256(file)

    if (actualHash != expectedSHA256Hash) {
        throw ChecksumMismatchException("$file is expected to have SHA256 hash $expectedSHA256Hash, but has hash $actualHash.")
    }
}

private fun computeSHA256(file: Path): String {
    Files.newInputStream(file).use { inputStream ->
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
