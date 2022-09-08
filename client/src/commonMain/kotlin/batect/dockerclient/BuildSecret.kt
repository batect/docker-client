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

import okio.Path

/**
 * A secret used for a BuildKit image build.
 *
 * @see [EnvironmentBuildSecret]
 * @see [FileBuildSecret]
 * @see [ImageBuildSpec.Builder.withFileSecret]
 * @see [ImageBuildSpec.Builder.withEnvironmentSecret]
 * @see [ImageBuildSpec.Builder.withSecret]
 * @see [ImageBuildSpec.Builder.withSecrets]
 */
public sealed interface BuildSecret

/**
 * A secret used for a BuildKit image build whose value is taken from an environment variable.
 *
 * Note that all secrets are exposed to the image build as files, even if their value is sourced
 * from an environment variable. (See https://docs.docker.com/engine/reference/commandline/buildx_build/#secret
 * for examples.)
 *
 * @see [ImageBuildSpec.Builder.withEnvironmentSecret]
 * @see [ImageBuildSpec.Builder.withSecret]
 * @see [ImageBuildSpec.Builder.withSecrets]
 */
public data class EnvironmentBuildSecret(val sourceEnvironmentVariableName: String) : BuildSecret

/**
 * A secret used for a BuildKit image build whose value is taken from a local file.
 *
 * @see [ImageBuildSpec.Builder.withFileSecret]
 * @see [ImageBuildSpec.Builder.withSecret]
 * @see [ImageBuildSpec.Builder.withSecrets]
 */
public data class FileBuildSecret(val source: Path) : BuildSecret
