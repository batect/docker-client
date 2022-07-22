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

public typealias ImageBuildProgressReceiver = (ImageBuildProgressUpdate) -> Unit

/**
 * A progress event for an image build operation.
 */
public sealed class ImageBuildProgressUpdate

/**
 * A progress event for a particular step in an image build.
 */
public sealed class ImageBuildStepProgressUpdate : ImageBuildProgressUpdate() {
    public abstract val stepNumber: Long
}

/**
 * A progress event that reports the progress of uploading build context to the daemon.
 */
public data class ImageBuildContextUploadProgress(val bytesUploaded: Long) : ImageBuildProgressUpdate()

/**
 * A progress event that indicates a step is starting.
 */
public data class StepStarting(override val stepNumber: Long, val stepName: String) : ImageBuildStepProgressUpdate()

/**
 * A progress event that contains output from a step.
 */
public data class StepOutput(override val stepNumber: Long, val output: String) : ImageBuildStepProgressUpdate()

/**
 * A progress event that reports the progress of an image pull.
 */
public data class StepPullProgressUpdate(override val stepNumber: Long, val pullProgress: ImagePullProgressUpdate) : ImageBuildStepProgressUpdate()

/**
 * A progress event that reports the progress of uploading build context to the daemon for a particular step.
 */
public data class StepContextUploadProgress(override val stepNumber: Long, val bytesUploaded: Long) : ImageBuildStepProgressUpdate()

/**
 * A progress event that contains image pull progress information.
 */
public data class StepDownloadProgressUpdate(override val stepNumber: Long, val bytesDownloaded: Long, val totalBytes: Long) : ImageBuildStepProgressUpdate()

/**
 * A progress event that indicates a step has finished.
 */
public data class StepFinished(override val stepNumber: Long) : ImageBuildStepProgressUpdate()

/**
 * A progress event that indicates the image build has failed.
 */
public data class BuildFailed(val message: String) : ImageBuildProgressUpdate()

/**
 * A progress event that indicates the image build is complete.
 */
public data class BuildComplete(val image: ImageReference) : ImageBuildProgressUpdate()
