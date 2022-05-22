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

public data class ContainerCreationSpec(
    val image: ImageReference,
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
    val useInitProcess: Boolean = false
) {
    public class Builder(image: ImageReference) {
        private var spec = ContainerCreationSpec(image)

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

        public fun build(): ContainerCreationSpec = spec
    }

    internal val environmentVariablesFormattedForDocker: List<String> = environmentVariables.map { "${it.key}=${it.value}" }
    internal val extraHostsFormattedForDocker: List<String> = extraHosts.map { "${it.hostname}:${it.address}" }
    internal val bindMountsFormattedForDocker: List<String> = bindMounts.map { it.formattedForDocker }
    internal val userAndGroupFormattedForDocker: String? = if (userAndGroup == null) null else "${userAndGroup.uid}:${userAndGroup.gid}"
}

public data class ExtraHost(val hostname: String, val address: String)

public sealed class BindMount(private val source: String) {
    public abstract val containerPath: String
    public abstract val options: String?

    internal val formattedForDocker: String
        get() = if (options == null) "$source:$containerPath" else "$source:$containerPath:$options"
}

public data class HostMount(val localPath: Path, override val containerPath: String, override val options: String? = null) : BindMount(localPath.toString())
public data class VolumeMount(val volume: VolumeReference, override val containerPath: String, override val options: String? = null) : BindMount(volume.name)

public data class TmpfsMount(val containerPath: String, val options: String)

public data class DeviceMount(val localPath: Path, val containerPath: String, val permissions: String = defaultPermissions) {
    public companion object {
        public const val defaultPermissions: String = "rwm"
    }
}

public data class ExposedPort(val localPort: Long, val containerPort: Long, val protocol: String = defaultProtocol) {
    public companion object {
        public const val defaultProtocol: String = "tcp"
    }
}

public data class UserAndGroup(val uid: Int, val gid: Int)
