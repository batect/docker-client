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
import kotlin.time.Duration

/**
 * A specification for a container.
 *
 * @see [DockerClient.createContainer]
 */
public data class ContainerCreationSpec(
    val image: ImageReference,
    val name: String? = null,
    val command: List<String> = emptyList(),
    val entrypoint: List<String> = emptyList(),
    val workingDirectory: String? = null,
    val hostname: String? = null,
    val extraHosts: Set<ExtraHost> = emptySet(),
    val environmentVariables: Map<String, String> = emptyMap(),
    val bindMounts: Set<BindMount> = emptySet(),
    val tmpfsMounts: Set<TmpfsMount> = emptySet(),
    val deviceMounts: Set<DeviceMount> = emptySet(),
    val exposedPorts: Set<ExposedPort> = emptySet(),
    val userAndGroup: UserAndGroup? = null,
    val useInitProcess: Boolean = false,
    val shmSizeInBytes: Long? = null,
    val attachTTY: Boolean = false,
    val privileged: Boolean = false,
    val capabilitiesToAdd: Set<Capability> = emptySet(),
    val capabilitiesToDrop: Set<Capability> = emptySet(),
    val network: NetworkReference? = null,
    val networkAliases: Set<String> = emptySet(),
    val logDriver: String? = null,
    val loggingOptions: Map<String, String> = emptyMap(),
    val healthcheckCommand: List<String> = emptyList(),
    val healthcheckInterval: Duration? = null,
    val healthcheckTimeout: Duration? = null,
    val healthcheckStartPeriod: Duration? = null,
    val healthcheckRetries: Int? = null,
    val labels: Map<String, String> = emptyMap(),
    val attachStdin: Boolean = false,
    val stdinOnce: Boolean = false,
    val openStdin: Boolean = false
) {
    internal fun ensureValid() {
        if (networkAliases.isNotEmpty() && network == null) {
            throw ContainerCreationFailedException("Container creation spec is not valid: must provide explicit network if using network aliases.")
        }
    }

    internal val environmentVariablesFormattedForDocker: List<String> = environmentVariables.map { "${it.key}=${it.value}" }
    internal val extraHostsFormattedForDocker: List<String> = extraHosts.map { "${it.hostname}:${it.address}" }
    internal val bindMountsFormattedForDocker: List<String> = bindMounts.map { it.formattedForDocker }
    internal val userAndGroupFormattedForDocker: String? = if (userAndGroup == null) null else "${userAndGroup.uid}:${userAndGroup.gid}"

    /**
     * Builder to create an instance of a [ContainerCreationSpec] for use with [DockerClient.createContainer].
     *
     * @see [DockerClient.createContainer]
     */
    public class Builder(image: ImageReference) {
        private var spec = ContainerCreationSpec(image)

        public fun withName(name: String): Builder {
            spec = spec.copy(name = name)

            return this
        }

        public fun withCommand(vararg command: String): Builder = withCommand(command.toList())

        public fun withCommand(command: List<String>): Builder {
            spec = spec.copy(command = command)

            return this
        }

        public fun withEntrypoint(vararg entrypoint: String): Builder = withEntrypoint(entrypoint.toList())

        public fun withEntrypoint(entrypoint: List<String>): Builder {
            spec = spec.copy(entrypoint = entrypoint)

            return this
        }

        public fun withWorkingDirectory(directory: String): Builder {
            spec = spec.copy(workingDirectory = directory)

            return this
        }

        public fun withHostname(hostname: String): Builder {
            spec = spec.copy(hostname = hostname)

            return this
        }

        public fun withExtraHost(hostname: String, address: String): Builder {
            spec = spec.copy(extraHosts = spec.extraHosts + ExtraHost(hostname, address))

            return this
        }

        public fun withEnvironmentVariable(name: String, value: String): Builder = withEnvironmentVariables(mapOf(name to value))
        public fun withEnvironmentVariables(vararg variables: Pair<String, String>): Builder = withEnvironmentVariables(mapOf(*variables))

        public fun withEnvironmentVariables(variables: Map<String, String>): Builder {
            spec = spec.copy(environmentVariables = spec.environmentVariables + variables)

            return this
        }

        public fun withHostMount(localPath: Path, containerPath: String, options: String? = null): Builder =
            withHostMount(HostMount(localPath, containerPath, options))

        public fun withHostMount(mount: HostMount): Builder {
            spec = spec.copy(bindMounts = spec.bindMounts + mount)

            return this
        }

        public fun withVolumeMount(volume: VolumeReference, containerPath: String, options: String? = null): Builder =
            withVolumeMount(VolumeMount(volume, containerPath, options))

        public fun withVolumeMount(mount: VolumeMount): Builder {
            spec = spec.copy(bindMounts = spec.bindMounts + mount)

            return this
        }

        public fun withTmpfsMount(containerPath: String, options: String = ""): Builder = withTmpfsMount(TmpfsMount(containerPath, options))

        public fun withTmpfsMount(mount: TmpfsMount): Builder {
            spec = spec.copy(tmpfsMounts = spec.tmpfsMounts + mount)

            return this
        }

        public fun withDeviceMount(localPath: Path, containerPath: String, permissions: String = DeviceMount.defaultPermissions): Builder = withDeviceMount(DeviceMount(localPath, containerPath, permissions))

        public fun withDeviceMount(mount: DeviceMount): Builder {
            spec = spec.copy(deviceMounts = spec.deviceMounts + mount)

            return this
        }

        public fun withExposedPort(localPort: Long, containerPort: Long, protocol: String = ExposedPort.defaultProtocol): Builder = withExposedPort(ExposedPort(localPort, containerPort, protocol))

        public fun withExposedPort(port: ExposedPort): Builder {
            spec = spec.copy(exposedPorts = spec.exposedPorts + port)

            return this
        }

        public fun withUserAndGroup(uid: Int, gid: Int): Builder = withUserAndGroup(UserAndGroup(uid, gid))

        public fun withUserAndGroup(userAndGroup: UserAndGroup): Builder {
            spec = spec.copy(userAndGroup = userAndGroup)

            return this
        }

        public fun withInitProcess(): Builder {
            spec = spec.copy(useInitProcess = true)

            return this
        }

        public fun withShmSize(sizeInBytes: Long): Builder {
            spec = spec.copy(shmSizeInBytes = sizeInBytes)

            return this
        }

        public fun withTTY(): Builder {
            spec = spec.copy(attachTTY = true)

            return this
        }

        public fun withPrivileged(): Builder {
            spec = spec.copy(privileged = true)

            return this
        }

        public fun withCapabilityAdded(capability: Capability): Builder = withCapabilitiesAdded(setOf(capability))
        public fun withCapabilitiesAdded(vararg capabilities: Capability): Builder = withCapabilitiesAdded(capabilities.toSet())

        public fun withCapabilitiesAdded(capabilities: Set<Capability>): Builder {
            spec = spec.copy(capabilitiesToAdd = spec.capabilitiesToAdd + capabilities)

            return this
        }

        public fun withCapabilityDropped(capability: Capability): Builder = withCapabilitiesDropped(setOf(capability))
        public fun withCapabilitiesDropped(vararg capabilities: Capability): Builder = withCapabilitiesDropped(capabilities.toSet())

        public fun withCapabilitiesDropped(capabilities: Set<Capability>): Builder {
            spec = spec.copy(capabilitiesToDrop = spec.capabilitiesToDrop + capabilities)

            return this
        }

        public fun withNetwork(network: NetworkReference): Builder {
            spec = spec.copy(network = network)

            return this
        }

        public fun withNetworkAlias(alias: String): Builder = withNetworkAliases(setOf(alias))
        public fun withNetworkAliases(vararg aliases: String): Builder = withNetworkAliases(aliases.toSet())

        public fun withNetworkAliases(aliases: Set<String>): Builder {
            spec = spec.copy(networkAliases = spec.networkAliases + aliases)

            return this
        }

        public fun withLogDriver(driverName: String): Builder {
            spec = spec.copy(logDriver = driverName)

            return this
        }

        public fun withLoggingOption(name: String, value: String): Builder = withLoggingOptions(mapOf(name to value))
        public fun withLoggingOptions(vararg options: Pair<String, String>): Builder = withLoggingOptions(mapOf(*options))

        public fun withLoggingOptions(options: Map<String, String>): Builder {
            spec = spec.copy(loggingOptions = spec.loggingOptions + options)

            return this
        }

        public fun withHealthcheckCommand(vararg command: String): Builder = withHealthcheckCommand(command.toList())

        public fun withHealthcheckCommand(command: List<String>): Builder {
            spec = spec.copy(healthcheckCommand = command)

            return this
        }

        public fun withHealthcheckInterval(interval: Duration): Builder {
            spec = spec.copy(healthcheckInterval = interval)

            return this
        }

        public fun withHealthcheckTimeout(timeout: Duration): Builder {
            spec = spec.copy(healthcheckTimeout = timeout)

            return this
        }

        public fun withHealthcheckStartPeriod(startPeriod: Duration): Builder {
            spec = spec.copy(healthcheckStartPeriod = startPeriod)

            return this
        }

        public fun withHealthcheckRetries(retries: Int): Builder {
            spec = spec.copy(healthcheckRetries = retries)

            return this
        }

        public fun withLabel(key: String, value: String): Builder = withLabels(mapOf(key to value))
        public fun withLabels(vararg labels: Pair<String, String>): Builder = withLabels(mapOf(*labels))

        public fun withLabels(labels: Map<String, String>): Builder {
            spec = spec.copy(labels = spec.labels + labels)

            return this
        }

        public fun withStdinAttached(): Builder {
            spec = spec.copy(attachStdin = true, openStdin = true, stdinOnce = true)

            return this
        }

        public fun build(): ContainerCreationSpec = spec
    }
}

/**
 * An additional hostname / address pair added to a container's `/etc/hosts` file.
 *
 * @see [ContainerCreationSpec.Builder.withExtraHost]
 */
public data class ExtraHost(val hostname: String, val address: String)

/**
 * Common properties for all forms of mounts into containers: [HostMount], [VolumeMount], [TmpfsMount] and [DeviceMount].
 */
public sealed interface ContainerMount {
    public val containerPath: String
}

/**
 * Base class for bind mounts into containers.
 *
 * @see [HostMount]
 * @see [VolumeMount]
 */
public sealed class BindMount(private val source: String) : ContainerMount {
    public abstract val options: String?

    internal val formattedForDocker: String
        get() = if (options == null) "$source:$containerPath" else "$source:$containerPath:$options"
}

/**
 * A [BindMount] representing a local path mounted into a container.
 *
 * @see [ContainerCreationSpec.Builder.withHostMount]
 */
public data class HostMount(val localPath: Path, override val containerPath: String, override val options: String? = null) : BindMount(localPath.toString())

/**
 * A [BindMount] representing a volume mounted into a container.
 *
 * @see [ContainerCreationSpec.Builder.withVolumeMount]
 */
public data class VolumeMount(val volume: VolumeReference, override val containerPath: String, override val options: String? = null) : BindMount(volume.name)

/**
 * A tmpfs filesystem mounted into a container.
 *
 * @see [ContainerCreationSpec.Builder.withTmpfsMount]
 */
public data class TmpfsMount(override val containerPath: String, val options: String) : ContainerMount

/**
 * A local device mounted into a container.
 *
 * @see [ContainerCreationSpec.Builder.withDeviceMount]
 */
public data class DeviceMount(val localPath: Path, override val containerPath: String, val permissions: String = defaultPermissions) : ContainerMount {
    public companion object {
        public const val defaultPermissions: String = "rwm"
    }
}

/**
 * A port exposed from a container to the host.
 *
 * @see [ContainerCreationSpec.Builder.withExposedPort]
 */
public data class ExposedPort(val localPort: Long, val containerPort: Long, val protocol: String = defaultProtocol) {
    public companion object {
        public const val defaultProtocol: String = "tcp"
    }
}

/**
 * A Unix user and group used to run the container or exec instance.
 *
 * @see [ContainerCreationSpec.Builder.withUserAndGroup]
 * @see [ContainerExecSpec.Builder.withUserAndGroup]
 */
public data class UserAndGroup(val uid: Int, val gid: Int)
