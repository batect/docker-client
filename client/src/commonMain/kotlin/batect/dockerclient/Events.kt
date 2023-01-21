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

/**
 * An event that has occurred.
 *
 * @see [DockerClient.streamEvents]
 */
public data class Event(
    val type: String,
    val action: String,
    val actor: Actor,
    val scope: String,
    val timestamp: Instant,
)

/**
 * Contains details of the actor related to a given [Event].
 *
 * @see [Event]
 * @see [DockerClient.streamEvents]
 */
public data class Actor(
    val id: String,
    val attributes: Map<String, String>,
)

/**
 * Returned from an [EventHandler] to indicate whether the handler would like to continue receiving further events.
 *
 * @see [DockerClient.streamEvents]
 */
public enum class EventHandlerAction {
    ContinueStreaming,
    Stop,
}

public typealias EventHandler = (Event) -> EventHandlerAction
