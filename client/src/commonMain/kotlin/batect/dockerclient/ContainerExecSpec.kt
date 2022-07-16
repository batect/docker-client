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

public data class ContainerExecSpec(
    val container: ContainerReference,
    val command: List<String> = emptyList(),
    val attachStdout: Boolean = false,
    val attachStderr: Boolean = false,
    val attachStdin: Boolean = false,
    val environmentVariables: Map<String, String> = emptyMap()
) {
    internal val environmentVariablesFormattedForDocker: List<String> = environmentVariables.map { "${it.key}=${it.value}" }

    public class Builder(container: ContainerReference) {
        private var spec = ContainerExecSpec(container)

        public fun withCommand(vararg command: String): Builder = withCommand(command.toList())

        public fun withCommand(command: List<String>): Builder {
            spec = spec.copy(command = command)

            return this
        }

        public fun withStdoutAttached(): Builder {
            spec = spec.copy(attachStdout = true)

            return this
        }

        public fun withStderrAttached(): Builder {
            spec = spec.copy(attachStderr = true)

            return this
        }

        public fun withStdinAttached(): Builder {
            spec = spec.copy(attachStdin = true)

            return this
        }

        public fun withEnvironmentVariable(name: String, value: String): Builder = withEnvironmentVariables(mapOf(name to value))
        public fun withEnvironmentVariables(vararg variables: Pair<String, String>): Builder = withEnvironmentVariables(mapOf(*variables))

        public fun withEnvironmentVariables(variables: Map<String, String>): Builder {
            spec = spec.copy(environmentVariables = spec.environmentVariables + variables)

            return this
        }

        public fun build(): ContainerExecSpec = spec
    }
}
