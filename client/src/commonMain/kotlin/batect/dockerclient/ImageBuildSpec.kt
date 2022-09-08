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
 * A specification for an image build operation.
 *
 * @see [DockerClient.buildImage]
 */
public data class ImageBuildSpec(
    val contextDirectory: Path,
    val pathToDockerfile: Path,
    val buildArgs: Map<String, String> = emptyMap(),
    val imageTags: Set<String> = emptySet(),
    val alwaysPullBaseImages: Boolean = false,
    val noCache: Boolean = false,
    val targetBuildStage: String = "",
    val builder: BuilderVersion? = null,
    val secrets: Map<String, BuildSecret> = emptyMap()
) {
    init {
        if (secrets.isNotEmpty() && builder != BuilderVersion.BuildKit) {
            throw UnsupportedImageBuildFeatureException("Secrets are only supported when building an image with BuildKit.")
        }
    }

    /**
     * Builder to create an instance of an [ImageBuildSpec] for use with [DockerClient.buildImage].
     *
     * @see [DockerClient.buildImage]
     */
    public class Builder(contextDirectory: Path) {
        private var spec = ImageBuildSpec(contextDirectory, contextDirectory.resolve("Dockerfile"))

        init {
            if (!systemFileSystem.exists(spec.contextDirectory)) {
                throw InvalidImageBuildSpecException("Context directory '${spec.contextDirectory}' does not exist.")
            }
        }

        public fun withDockerfile(path: Path): Builder {
            val resolvedPath = if (path.isAbsolute) path else spec.contextDirectory.resolve(path)

            spec = spec.copy(pathToDockerfile = resolvedPath)

            return this
        }

        public fun withNoBuildCache(): Builder {
            spec = spec.copy(noCache = true)

            return this
        }

        public fun withBuildArg(name: String, value: String): Builder = withBuildArgs(mapOf(name to value))
        public fun withBuildArgs(vararg args: Pair<String, String>): Builder = withBuildArgs(mapOf(*args))

        public fun withBuildArgs(args: Map<String, String>): Builder {
            spec = spec.copy(buildArgs = spec.buildArgs + args)

            return this
        }

        public fun withBaseImageAlwaysPulled(): Builder {
            spec = spec.copy(alwaysPullBaseImages = true)

            return this
        }

        public fun withImageTag(tag: String): Builder = withImageTags(setOf(tag))

        public fun withImageTags(vararg tags: String): Builder {
            tags.forEach { validateImageTag(it) }

            spec = spec.copy(imageTags = spec.imageTags + tags)

            return this
        }

        public fun withImageTags(tags: Collection<String>): Builder {
            tags.forEach { validateImageTag(it) }

            spec = spec.copy(imageTags = spec.imageTags + tags)

            return this
        }

        public fun withTargetBuildStage(targetBuildStage: String): Builder {
            spec = spec.copy(targetBuildStage = targetBuildStage)

            return this
        }

        public fun withLegacyBuilder(): Builder = withBuilder(BuilderVersion.Legacy)
        public fun withBuildKitBuilder(): Builder = withBuilder(BuilderVersion.BuildKit)

        public fun withBuilder(builder: BuilderVersion): Builder {
            spec = spec.copy(builder = builder)

            return this
        }

        public fun withDaemonDefaultBuilder(): Builder {
            spec = spec.copy(builder = null)

            return this
        }

        public fun withFileSecret(id: String, source: Path): Builder = withSecret(id, FileBuildSecret(source))
        public fun withEnvironmentSecret(id: String, sourceEnvironmentVariableName: String): Builder = withSecret(id, EnvironmentBuildSecret(sourceEnvironmentVariableName))
        public fun withSecret(id: String, value: BuildSecret): Builder = withSecrets(id to value)
        public fun withSecrets(vararg secrets: Pair<String, BuildSecret>): Builder = withSecrets(mapOf(*secrets))

        public fun withSecrets(secrets: Map<String, BuildSecret>): Builder {
            spec = spec.copy(secrets = spec.secrets + secrets)

            return this
        }

        public fun build(): ImageBuildSpec {
            if (!systemFileSystem.exists(spec.pathToDockerfile)) {
                throw InvalidImageBuildSpecException("Dockerfile '${spec.pathToDockerfile}' does not exist.")
            }

            if (spec.pathToDockerfile.relativeTo(spec.contextDirectory).segments.first() == "..") {
                throw InvalidImageBuildSpecException("Dockerfile '${spec.pathToDockerfile}' is not a child of the context directory (${spec.contextDirectory}).")
            }

            return spec
        }
    }

    internal val builderApiVersion: String? = when (builder) {
        BuilderVersion.Legacy -> "1"
        BuilderVersion.BuildKit -> "2"
        null -> null
    }
}

internal expect fun validateImageTag(tag: String)
