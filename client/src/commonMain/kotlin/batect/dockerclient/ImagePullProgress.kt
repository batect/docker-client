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

public typealias ImagePullProgressReceiver = (ImagePullProgressUpdate) -> Unit

/**
 * Contains a snapshot of the progress of either an entire image pull request or a sub-operation of an image pull request.
 */
public data class ImagePullProgressUpdate(
    val message: String,
    val detail: ImagePullProgressDetail?,
    val id: String,
)

/**
 * Contains details of an operation performed as part of an image pull request.
 *
 * @see [ImagePullProgressUpdate]
 */
public data class ImagePullProgressDetail(
    /**
     * The current number of bytes completed (downloaded, extracted etc.).
     */
    val current: Long,
    /**
     * The total number of bytes in this operation.
     */
    val total: Long,
)
