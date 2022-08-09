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

package batect.dockerclient

/**
 * Base class for files and directories to be uploaded to a container.
 *
 * @see [DockerClient.uploadToContainer]
 */
public sealed interface UploadItem

/**
 * A directory to be uploaded to a container.
 *
 * @see [DockerClient.uploadToContainer]
 */
public data class UploadDirectory(
    val path: String,
    val owner: Int,
    val group: Int,
    val mode: Int
) : UploadItem

/**
 * A file to be uploaded to a container.
 *
 * @see [DockerClient.uploadToContainer]
 */
public data class UploadFile(
    val path: String,
    val owner: Int,
    val group: Int,
    val mode: Int,
    val contents: ByteArray // FIXME: loading this as a byte array isn't suitable for large files, as we'll load the entire file into memory
) : UploadItem {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false

        other as UploadFile

        if (path != other.path) return false
        if (owner != other.owner) return false
        if (group != other.group) return false
        if (!contents.contentEquals(other.contents)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = path.hashCode()
        result = 31 * result + owner
        result = 31 * result + group
        result = 31 * result + contents.contentHashCode()
        return result
    }
}
