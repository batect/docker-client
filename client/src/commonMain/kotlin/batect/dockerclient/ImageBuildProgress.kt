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

public typealias ImageBuildProgressReceiver = (ImageBuildProgressUpdate) -> Unit

public sealed class ImageBuildProgressUpdate

public sealed class ImageBuildStepProgressUpdate() : ImageBuildProgressUpdate() {
    public abstract val stepIndex: Int
}

public data class StepStarting(override val stepIndex: Int, val stepName: String) : ImageBuildStepProgressUpdate()
public data class StepOutput(override val stepIndex: Int, val output: String) : ImageBuildStepProgressUpdate()
public data class StepPullProgressUpdate(override val stepIndex: Int, val pullProgress: ImagePullProgressUpdate) : ImageBuildStepProgressUpdate()
public data class StepFinished(override val stepIndex: Int) : ImageBuildStepProgressUpdate()
public data class BuildFailed(val message: String) : ImageBuildProgressUpdate()
public data class BuildComplete(val image: ImageReference) : ImageBuildProgressUpdate()
