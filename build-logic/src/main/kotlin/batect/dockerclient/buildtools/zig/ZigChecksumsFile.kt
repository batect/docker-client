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
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.nio.file.Path
import kotlin.io.path.readText

data class ZigChecksumsFile(val path: Path) {
    private val checksumFileContent by lazy { Json.parseToJsonElement(path.readText(Charsets.UTF_8)) as JsonObject }

    fun archiveChecksumForVersionAndPlatform(version: String, platformName: String): String {
        val versionDetails = checksumFileContent.getOrElse(version) {
            throw ChecksumVerificationException("Version '$version' not found in $path.")
        } as JsonObject

        val platformDetails = versionDetails.getOrElse(platformName) {
            throw ChecksumVerificationException("Platform '$platformName' not found for version '$version' in $path.")
        }

        if (platformDetails !is JsonObject) {
            throw ChecksumVerificationException("'$platformName' is not a valid platform for version '$version' in $path.")
        }

        val checksum = platformDetails.getOrElse("shasum") {
            throw ChecksumVerificationException("Platform '$platformName' for version '$version' does not have a checksum in $path.")
        } as JsonPrimitive

        return checksum.content
    }
}
