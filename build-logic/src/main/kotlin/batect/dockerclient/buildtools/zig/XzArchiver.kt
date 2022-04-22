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

import org.apache.commons.compress.compressors.xz.XZCompressorInputStream
import org.gradle.api.internal.file.archive.compression.CompressedReadableResource
import org.gradle.api.internal.file.archive.compression.URIBuilder
import org.gradle.api.resources.internal.ReadableResourceInternal
import org.gradle.internal.IoActions
import org.gradle.internal.resource.ResourceExceptions
import java.io.BufferedInputStream
import java.io.File
import java.io.InputStream
import java.net.URI

// This is based on AbstractArchiver and GzipArchiver from Gradle.
class XzArchiver(val resource: ReadableResourceInternal) : CompressedReadableResource {
    override fun read(): InputStream {
        val input = BufferedInputStream(resource.read())

        try {
            return XZCompressorInputStream(input)
        } catch (e: Exception) {
            IoActions.closeQuietly(input)
            throw ResourceExceptions.readFailed(resource.displayName, e)
        }
    }

    override fun getDisplayName(): String = resource.displayName
    override fun getURI(): URI = URIBuilder(resource.uri).schemePrefix("xz:").build()
    override fun getBaseName(): String = resource.baseName
    override fun getBackingFile(): File = resource.backingFile
}
