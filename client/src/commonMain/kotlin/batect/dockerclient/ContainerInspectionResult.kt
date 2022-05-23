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

import kotlinx.datetime.Instant
import kotlin.time.Duration

public data class ContainerInspectionResult(
    val reference: ContainerReference,
    val name: String,
    val hostConfig: ContainerHostConfig,
    val state: ContainerState,
    val config: ContainerConfig
)

public data class ContainerHostConfig(
    val logConfig: ContainerLogConfig
)

public data class ContainerLogConfig(
    val type: String,
    val config: Map<String, String>
)

public data class ContainerState(
    val health: ContainerHealthState?
)

public data class ContainerHealthState(
    val status: String,
    val log: List<ContainerHealthLogEntry>
)

public data class ContainerHealthLogEntry(
    val start: Instant,
    val end: Instant,
    val exitCode: Long,
    val output: String
)

public data class ContainerConfig(
    val labels: Map<String, String>,
    val healthcheck: ContainerHealthcheckConfig?
)

public data class ContainerHealthcheckConfig(
    val test: List<String>,
    val interval: Duration?,
    val timeout: Duration?,
    val startPeriod: Duration?,
    val retries: Int?
)
