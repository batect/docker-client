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

public data class ContainerCreationSpec(
    val image: ImageReference,
    val command: List<String> = emptyList(),
    val hostname: String? = null
) {
    public class Builder(image: ImageReference) {
        private var spec = ContainerCreationSpec(image)

        public fun withCommand(vararg command: String): Builder = withCommand(command.toList())

        public fun withCommand(command: List<String>): Builder {
            spec = spec.copy(command = command)

            return this
        }

        public fun withHostname(hostname: String): Builder {
            spec = spec.copy(hostname = hostname)

            return this
        }

        public fun build(): ContainerCreationSpec = spec
    }
}
