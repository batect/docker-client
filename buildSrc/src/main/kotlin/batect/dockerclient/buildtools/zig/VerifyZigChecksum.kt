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
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import okio.ByteString
import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import java.io.File
import java.nio.file.Files

abstract class VerifyZigChecksum : DefaultTask() {
    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val checksumFile: RegularFileProperty

    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val fileToVerify: RegularFileProperty

    @get:Input
    abstract val zigVersion: Property<String>

    @get:Input
    abstract val zigPlatformName: Property<String>

    @TaskAction
    fun run() {
        val expectedChecksum = findExpectedChecksum(checksumFile.get().asFile)
        val actualChecksum = computeActualChecksum()

        if (actualChecksum != expectedChecksum) {
            throw ChecksumVerificationFailedException("${fileToVerify.get().asFile} is expected to have checksum $expectedChecksum, but has checksum $actualChecksum.")
        }
    }

    private fun findExpectedChecksum(checksumFile: File): String {
        val checksumFileContent = Json.parseToJsonElement(checksumFile.readText(Charsets.UTF_8)) as JsonObject

        val versionDetails = checksumFileContent.getOrElse(zigVersion.get()) {
            throw ChecksumVerificationFailedException("Version '${zigVersion.get()}' not found in $checksumFile.")
        } as JsonObject

        val platformDetails = versionDetails.getOrElse(zigPlatformName.get()) {
            throw ChecksumVerificationFailedException("Platform '${zigPlatformName.get()}' not found for version '${zigVersion.get()}' in $checksumFile.")
        }

        if (platformDetails !is JsonObject) {
            throw ChecksumVerificationFailedException("'${zigPlatformName.get()}' is not a valid platform for version '${zigVersion.get()}' in $checksumFile.")
        }

        val checksum = platformDetails.getOrElse("shasum") {
            throw ChecksumVerificationFailedException("Platform '${zigPlatformName.get()}' for version '${zigVersion.get()}' does not have a checksum in $checksumFile.")
        } as JsonPrimitive

        return checksum.content
    }

    private fun computeActualChecksum(): String {
        val fileToVerify = fileToVerify.get().asFile
        val actualBytes = ByteString.of(*Files.readAllBytes(fileToVerify.toPath()))

        return actualBytes.sha256().hex()
    }
}
