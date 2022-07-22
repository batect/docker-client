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
 * Base class for all exceptions thrown by [DockerClient].
 */
public expect open class DockerClientException(
    message: String,
    cause: Throwable? = null,
    golangErrorType: String? = null
) : RuntimeException {
    public val golangErrorType: String?
}

/**
 * Thrown when pinging the Docker daemon fails.
 */
public expect class PingFailedException(
    message: String,
    cause: Throwable? = null,
    golangErrorType: String? = null
) : DockerClientException

/**
 * Thrown when retrieving information about the Docker daemon fails.
 */
public expect class GetDaemonVersionInformationFailedException(
    message: String,
    cause: Throwable? = null,
    golangErrorType: String? = null
) : DockerClientException

/**
 * Thrown when creating a volume fails.
 */
public expect class VolumeCreationFailedException(
    message: String,
    cause: Throwable? = null,
    golangErrorType: String? = null
) : DockerClientException

/**
 * Thrown when deleting a volume fails.
 */
public expect class VolumeDeletionFailedException(
    message: String,
    cause: Throwable? = null,
    golangErrorType: String? = null
) : DockerClientException

/**
 * Thrown when listing all volumes fails.
 */
public expect class ListAllVolumesFailedException(
    message: String,
    cause: Throwable? = null,
    golangErrorType: String? = null
) : DockerClientException

/**
 * Thrown when creating a network fails.
 */
public expect class NetworkCreationFailedException(
    message: String,
    cause: Throwable? = null,
    golangErrorType: String? = null
) : DockerClientException

/**
 * Thrown when deleting a network fails.
 */
public expect class NetworkDeletionFailedException(
    message: String,
    cause: Throwable? = null,
    golangErrorType: String? = null
) : DockerClientException

/**
 * Thrown when retrieving a network fails.
 */
public expect class NetworkRetrievalFailedException(
    message: String,
    cause: Throwable? = null,
    golangErrorType: String? = null
) : DockerClientException

/**
 * Thrown when pulling an image fails.
 */
public expect class ImagePullFailedException(
    message: String,
    cause: Throwable? = null,
    golangErrorType: String? = null
) : DockerClientException

/**
 * Thrown when deleting an image fails.
 */
public expect class ImageDeletionFailedException(
    message: String,
    cause: Throwable? = null,
    golangErrorType: String? = null
) : DockerClientException

/**
 * Thrown when retrieving an image fails.
 */
public expect class ImageRetrievalFailedException(
    message: String,
    cause: Throwable? = null,
    golangErrorType: String? = null
) : DockerClientException

/**
 * Thrown when building an image fails.
 */
public expect class ImageBuildFailedException(
    message: String,
    cause: Throwable? = null,
    golangErrorType: String? = null
) : DockerClientException

/**
 * Thrown when pruning the Docker daemon's image build cache fails.
 */
public expect class ImageBuildCachePruneFailedException(
    message: String,
    cause: Throwable? = null,
    golangErrorType: String? = null
) : DockerClientException

/**
 * Thrown when an [ImageBuildSpec] is not valid.
 */
public class InvalidImageBuildSpecException(
    message: String,
    cause: Throwable? = null
) : DockerClientException(message, cause)

/**
 * Thrown when creating a container fails.
 */
public expect class ContainerCreationFailedException(
    message: String,
    cause: Throwable? = null,
    golangErrorType: String? = null
) : DockerClientException

/**
 * Thrown when starting a container fails.
 */
public expect class ContainerStartFailedException(
    message: String,
    cause: Throwable? = null,
    golangErrorType: String? = null
) : DockerClientException

/**
 * Thrown when stopping a container fails.
 */
public expect class ContainerStopFailedException(
    message: String,
    cause: Throwable? = null,
    golangErrorType: String? = null
) : DockerClientException

/**
 * Thrown when removing a container fails.
 */
public expect class ContainerRemovalFailedException(
    message: String,
    cause: Throwable? = null,
    golangErrorType: String? = null
) : DockerClientException

/**
 * Thrown when waiting for a container to exit fails.
 */
public expect class ContainerWaitFailedException(
    message: String,
    cause: Throwable? = null,
    golangErrorType: String? = null
) : DockerClientException

/**
 * Thrown when streaming I/O to and/or from a container fails.
 */
public expect class AttachToContainerFailedException(
    message: String,
    cause: Throwable? = null,
    golangErrorType: String? = null
) : DockerClientException

/**
 * Thrown when inspecting a container fails.
 */
public expect class ContainerInspectionFailedException(
    message: String,
    cause: Throwable? = null,
    golangErrorType: String? = null
) : DockerClientException

/**
 * Thrown when uploading files or directories to a container fails.
 */
public expect class ContainerUploadFailedException(
    message: String,
    cause: Throwable? = null,
    golangErrorType: String? = null
) : DockerClientException

/**
 * Thrown when streaming events from the Docker daemon fails.
 */
public expect class StreamingEventsFailedException(
    message: String,
    cause: Throwable? = null,
    golangErrorType: String? = null
) : DockerClientException

/**
 * Throw when an exec instance cannot be created.
 */
public expect class ContainerExecCreationFailedException(
    message: String,
    cause: Throwable? = null,
    golangErrorType: String? = null
) : DockerClientException

/**
 * Thrown when an exec instance cannot be inspected.
 */
public expect class ContainerExecInspectionFailedException(
    message: String,
    cause: Throwable? = null,
    golangErrorType: String? = null
) : DockerClientException

/**
 * Thrown when an exec instance cannot be started.
 */
public expect class StartingContainerExecFailedException(
    message: String,
    cause: Throwable? = null,
    golangErrorType: String? = null
) : DockerClientException
