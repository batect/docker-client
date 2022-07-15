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

public sealed interface UploadItem

public data class UploadDirectory(
    val path: String,
    val owner: Int,
    val group: Int
) : UploadItem

public data class UploadFile(
    val path: String,
    val owner: Int,
    val group: Int,
    val contents: ByteArray // FIXME: loading this as a byte array isn't suitable for large files, as we'll load the entire file into memory
) : UploadItem
