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

public expect class NetworkCreationFailedException(
    message: String,
    cause: Throwable? = null,
    golangErrorType: String? = null
) : DockerClientException

public expect class NetworkDeletionFailedException(
    message: String,
    cause: Throwable? = null,
    golangErrorType: String? = null
) : DockerClientException

public expect class NetworkRetrievalFailedException(
    message: String,
    cause: Throwable? = null,
    golangErrorType: String? = null
) : DockerClientException

public expect class ImagePullFailedException(
    message: String,
    cause: Throwable? = null,
    golangErrorType: String? = null
) : DockerClientException

public expect class ImageDeletionFailedException(
    message: String,
    cause: Throwable? = null,
    golangErrorType: String? = null
) : DockerClientException

public expect class ImageRetrievalFailedException(
    message: String,
    cause: Throwable? = null,
    golangErrorType: String? = null
) : DockerClientException

public expect class ImageBuildFailedException(
    message: String,
    cause: Throwable? = null,
    golangErrorType: String? = null
) : DockerClientException

public expect class ImageBuildCachePruneFailedException(
    message: String,
    cause: Throwable? = null,
    golangErrorType: String? = null
) : DockerClientException

public class InvalidImageBuildSpecException(
    message: String,
    cause: Throwable? = null
) : DockerClientException(message, cause)

public expect class ContainerCreationFailedException(
    message: String,
    cause: Throwable? = null,
    golangErrorType: String? = null
) : DockerClientException

public expect class ContainerStartFailedException(
    message: String,
    cause: Throwable? = null,
    golangErrorType: String? = null
) : DockerClientException

public expect class ContainerStopFailedException(
    message: String,
    cause: Throwable? = null,
    golangErrorType: String? = null
) : DockerClientException

public expect class ContainerRemovalFailedException(
    message: String,
    cause: Throwable? = null,
    golangErrorType: String? = null
) : DockerClientException

public expect class ContainerWaitFailedException(
    message: String,
    cause: Throwable? = null,
    golangErrorType: String? = null
) : DockerClientException

public expect class AttachToContainerFailedException(
    message: String,
    cause: Throwable? = null,
    golangErrorType: String? = null
) : DockerClientException

public expect class ContainerInspectionFailedException(
    message: String,
    cause: Throwable? = null,
    golangErrorType: String? = null
) : DockerClientException
