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

package batect.dockerclient

import batect.dockerclient.native.Error

public actual open class DockerClientException actual constructor(
    message: String,
    cause: Throwable?,
    public actual val golangErrorType: String?
) : RuntimeException(message, cause) {
    internal constructor(error: Error) : this(error.message.get(), golangErrorType = error.type.get())
}

public actual class PingFailedException actual constructor(
    message: String,
    cause: Throwable?,
    golangErrorType: String?
) : DockerClientException(message, cause, golangErrorType) {
    internal constructor(error: Error) : this(error.cleanErrorMessage, golangErrorType = error.type.get())
}

public actual class GetDaemonVersionInformationFailedException actual constructor(
    message: String,
    cause: Throwable?,
    golangErrorType: String?
) : DockerClientException(message, cause, golangErrorType) {
    internal constructor(error: Error) : this(error.cleanErrorMessage, golangErrorType = error.type.get())
}

public actual class VolumeCreationFailedException actual constructor(
    message: String,
    cause: Throwable?,
    golangErrorType: String?
) : DockerClientException(message, cause, golangErrorType) {
    internal constructor(error: Error) : this(error.cleanErrorMessage, golangErrorType = error.type.get())
}

public actual class VolumeDeletionFailedException actual constructor(
    message: String,
    cause: Throwable?,
    golangErrorType: String?
) : DockerClientException(message, cause, golangErrorType) {
    internal constructor(error: Error) : this(error.cleanErrorMessage, golangErrorType = error.type.get())
}

public actual class ListAllVolumesFailedException actual constructor(
    message: String,
    cause: Throwable?,
    golangErrorType: String?
) : DockerClientException(message, cause, golangErrorType) {
    internal constructor(error: Error) : this(error.cleanErrorMessage, golangErrorType = error.type.get())
}

private val Error.cleanErrorMessage: String
    get() = this.message.get().removePrefix("Error: ")
