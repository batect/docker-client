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

public expect open class DockerClientException(
    message: String,
    cause: Throwable? = null,
    golangErrorType: String? = null
) : RuntimeException {
    public val golangErrorType: String?
}

public expect class PingFailedException(
    message: String,
    cause: Throwable? = null,
    golangErrorType: String? = null
) : DockerClientException

public expect class GetDaemonVersionInformationFailedException(
    message: String,
    cause: Throwable? = null,
    golangErrorType: String? = null
) : DockerClientException

public expect class VolumeCreationFailedException(
    message: String,
    cause: Throwable? = null,
    golangErrorType: String? = null
) : DockerClientException

public expect class VolumeDeletionFailedException(
    message: String,
    cause: Throwable? = null,
    golangErrorType: String? = null
) : DockerClientException

public expect class ListAllVolumesFailedException(
    message: String,
    cause: Throwable? = null,
    golangErrorType: String? = null
) : DockerClientException
