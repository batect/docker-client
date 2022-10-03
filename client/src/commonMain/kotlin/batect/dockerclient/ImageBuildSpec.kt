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
    val builder: ImageBuilder? = null,
    val secrets: Map<String, BuildSecret> = emptyMap(),
    val sshAgents: Set<SSHAgent> = emptySet(),
    val cacheFrom: Set<ImageBuildCache> = emptySet(),
    val cacheTo: Set<ImageBuildCache> = emptySet()
) {
    init {
        if (secrets.isNotEmpty() && builder?.version != BuilderVersion.BuildKit) {
            throw UnsupportedImageBuildFeatureException("Secrets are only supported when building an image with BuildKit.")
        }

        if (sshAgents.isNotEmpty() && builder?.version != BuilderVersion.BuildKit) {
            throw UnsupportedImageBuildFeatureException("SSH agents are only supported when building an image with BuildKit.")
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

        public fun withLegacyBuilder(): Builder = withBuilder(ImageBuilder.Legacy)
        public fun withBuildKitBuilder(instance: BuildKitInstance = BuildKitInstance.SelectedOrDefaultDockerDriver): Builder = withBuilder(ImageBuilder.BuildKit(instance))

        public fun withBuilder(builder: ImageBuilder): Builder {
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

        public fun withDefaultSSHAgent(): Builder = withSSHAgent(SSHAgent.default)
        public fun withSSHAgent(id: String, paths: Set<Path> = emptySet()): Builder = withSSHAgent(SSHAgent(id, paths))

        public fun withSSHAgent(agent: SSHAgent): Builder {
            spec = spec.copy(sshAgents = spec.sshAgents + agent)

            return this
        }

        public fun withCacheFrom(type: String, vararg attributes: Pair<String, String>): Builder = withCacheFrom(type, mapOf(*attributes))
        public fun withCacheFrom(type: String, attributes: Map<String, String> = emptyMap()): Builder = withCacheFrom(ImageBuildCache(type, attributes))

        public fun withCacheFrom(cache: ImageBuildCache): Builder {
            spec = spec.copy(cacheFrom = spec.cacheFrom + cache)

            return this
        }

        public fun withCacheTo(type: String, vararg attributes: Pair<String, String>): Builder = withCacheTo(type, mapOf(*attributes))
        public fun withCacheTo(type: String, attributes: Map<String, String> = emptyMap()): Builder = withCacheTo(ImageBuildCache(type, attributes))

        public fun withCacheTo(cache: ImageBuildCache): Builder {
            spec = spec.copy(cacheTo = spec.cacheTo + cache)

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

    internal val builderApiVersion: String? = when (builder?.version) {
        BuilderVersion.Legacy -> "1"
        BuilderVersion.BuildKit -> "2"
        null -> null
    }
}

/**
 * Represents a SSH agent exposed to a BuildKit image build.
 *
 * @property id ID of agent, used to refer to agent in `--mount` flag in Dockerfile
 * @property paths paths to either a SSH agent socket on the host machine or a set of keys to expose to the build from the host machine.
 *   If empty, the default SSH agent socket from the `SSH_AUTH_SOCK` environment variable is used.
 */
public data class SSHAgent(val id: String, val paths: Set<Path>) {
    public companion object {
        public const val defaultID: String = "default"
        public val default: SSHAgent = SSHAgent(defaultID, emptySet())
    }
}

/**
 * A Docker image builder.
 */
public sealed class ImageBuilder(public val version: BuilderVersion) {
    /**
     * The legacy image builder.
     */
    public object Legacy : ImageBuilder(BuilderVersion.Legacy)

    /**
     * A BuildKit image builder.
     */
    public data class BuildKit(val instance: BuildKitInstance = BuildKitInstance.SelectedOrDefaultDockerDriver) : ImageBuilder(BuilderVersion.BuildKit)
}

/**
 * A BuildKit node.
 */
public sealed class BuildKitInstance(internal val golangConstantValue: Int) {
    public object SelectedOrDefaultDockerDriver : BuildKitInstance(1)
    public object DefaultDockerDriver : BuildKitInstance(2)
    public data class Named(val name: String) : BuildKitInstance(3)
}

/**
 * Represents a cache used during a BuildKit image build.
 *
 * https://github.com/moby/buildkit#cache lists the types of caches supported and their supported attributes.
 *
 * @property type the type of cache to use
 * @property attributes attributes to pass to cache implementation (eg. remote cache URL)
 *
 */
public data class ImageBuildCache(val type: String, val attributes: Map<String, String> = emptyMap())

internal expect fun validateImageTag(tag: String)
