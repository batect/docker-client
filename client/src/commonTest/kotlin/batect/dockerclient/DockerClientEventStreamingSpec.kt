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

import io.kotest.assertions.asClue
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.comparables.shouldBeGreaterThan
import io.kotest.matchers.comparables.shouldBeLessThan
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlin.random.Random
import kotlin.time.Duration.Companion.seconds

class DockerClientEventStreamingSpec : ShouldSpec({
    val client = closeAfterTest(DockerClient.Builder().build())
    val clockSkewFudgeFactor = 2.seconds

    should("retrieve events within the requested window that occurred before streaming events started").onlyIfDockerDaemonPresent {
        val startTime = Clock.System.now() - clockSkewFudgeFactor
        val volumeName = "test-volume-${Random.nextInt()}"
        val volume = client.createVolume(volumeName)
        client.deleteVolume(volume)
        val endTime = Clock.System.now() + clockSkewFudgeFactor
        val filters = mapOf("volume" to setOf(volumeName))

        val events = mutableListOf<Event>()

        withTimeout(5.seconds) {
            client.streamEvents(startTime, endTime, filters) { event ->
                events.add(event)
                EventHandlerAction.ContinueStreaming
            }
        }

        events shouldHaveSize 2

        events[0].asClue {
            it.type shouldBe "volume"
            it.action shouldBe "create"
            it.scope shouldBe "local"
            it.timestamp shouldBeGreaterThan startTime
            it.timestamp shouldBeLessThan endTime
            it.actor shouldBe Actor(volumeName, mapOf("driver" to "local"))
        }

        events[1].asClue {
            it.type shouldBe "volume"
            it.action shouldBe "destroy"
            it.scope shouldBe "local"
            it.timestamp shouldBeGreaterThan startTime
            it.timestamp shouldBeLessThan endTime
            it.actor shouldBe Actor(volumeName, mapOf("driver" to "local"))
        }
    }

    should("retrieve events that occur while streaming events").onlyIfDockerDaemonPresent {
        val startTime = Clock.System.now() - clockSkewFudgeFactor
        val volumeName = "test-volume-${Random.nextInt()}"
        val volume = client.createVolume(volumeName)
        val filters = mapOf("volume" to setOf(volumeName))
        val endTime = Clock.System.now() + 4.seconds

        val events = mutableListOf<Event>()

        withTimeout(5.seconds) {
            client.streamEvents(startTime, endTime, filters) { event ->
                events.add(event)

                if (events.size <= 1) {
                    runBlocking {
                        client.deleteVolume(volume)
                    }

                    EventHandlerAction.ContinueStreaming
                } else {
                    EventHandlerAction.Stop
                }
            }
        }

        events shouldHaveSize 2

        events[0].asClue {
            it.type shouldBe "volume"
            it.action shouldBe "create"
            it.scope shouldBe "local"
            it.timestamp shouldBeGreaterThan startTime
            it.timestamp shouldBeLessThan endTime
            it.actor shouldBe Actor(volumeName, mapOf("driver" to "local"))
        }

        events[1].asClue {
            it.type shouldBe "volume"
            it.action shouldBe "destroy"
            it.scope shouldBe "local"
            it.timestamp shouldBeGreaterThan startTime
            it.timestamp shouldBeLessThan endTime
            it.actor shouldBe Actor(volumeName, mapOf("driver" to "local"))
        }
    }

    should("stop streaming events when the event processor requests it, even if more events are available").onlyIfDockerDaemonPresent {
        val startTime = Clock.System.now() - clockSkewFudgeFactor
        val volumeName = "test-volume-${Random.nextInt()}"
        val volume = client.createVolume(volumeName)
        client.deleteVolume(volume)
        val endTime = Clock.System.now() + clockSkewFudgeFactor
        val filters = mapOf("volume" to setOf(volumeName))

        val events = mutableListOf<Event>()

        withTimeout(5.seconds) {
            client.streamEvents(startTime, endTime, filters) { event ->
                events.add(event)

                if (events.size >= 1) {
                    EventHandlerAction.Stop
                } else {
                    EventHandlerAction.ContinueStreaming
                }
            }
        }

        events shouldHaveSize 1

        events[0].asClue {
            it.type shouldBe "volume"
            it.action shouldBe "create"
            it.scope shouldBe "local"
            it.timestamp shouldBeGreaterThan startTime
            it.timestamp shouldBeLessThan endTime
            it.actor shouldBe Actor(volumeName, mapOf("driver" to "local"))
        }
    }

    should("filter events").onlyIfDockerDaemonPresent {
        val startTime = Clock.System.now() - clockSkewFudgeFactor
        val volumeName = "test-volume-${Random.nextInt()}"
        val volume = client.createVolume(volumeName)
        client.deleteVolume(volume)
        val endTime = Clock.System.now() + clockSkewFudgeFactor

        val filters = mapOf(
            "volume" to setOf(volumeName),
            "event" to setOf("destroy")
        )

        val events = mutableListOf<Event>()

        withTimeout(5.seconds) {
            client.streamEvents(startTime, endTime, filters) { event ->
                events.add(event)
                EventHandlerAction.ContinueStreaming
            }
        }

        events shouldHaveSize 1

        events[0].asClue {
            it.type shouldBe "volume"
            it.action shouldBe "destroy"
            it.scope shouldBe "local"
            it.timestamp shouldBeGreaterThan startTime
            it.timestamp shouldBeLessThan endTime
            it.actor shouldBe Actor(volumeName, mapOf("driver" to "local"))
        }
    }

    should("be able to get all events ever recorded, including those generated while streaming events").onlyIfDockerDaemonPresent {
        val startTime = Clock.System.now() - clockSkewFudgeFactor
        val volumeName = "test-volume-${Random.nextInt()}"
        val volume = client.createVolume(volumeName)
        val filters = mapOf("volume" to setOf(volumeName))
        val events = mutableListOf<Event>()

        withTimeout(5.seconds) {
            client.streamEvents(Instant.fromEpochMilliseconds(0), null, filters) { event ->
                events.add(event)

                if (events.size <= 1) {
                    runBlocking {
                        client.deleteVolume(volume)
                    }

                    EventHandlerAction.ContinueStreaming
                } else {
                    EventHandlerAction.Stop
                }
            }
        }

        val endTime = Clock.System.now() + clockSkewFudgeFactor

        events shouldHaveSize 2

        events[0].asClue {
            it.type shouldBe "volume"
            it.action shouldBe "create"
            it.scope shouldBe "local"
            it.timestamp shouldBeGreaterThan startTime
            it.timestamp shouldBeLessThan endTime
            it.actor shouldBe Actor(volumeName, mapOf("driver" to "local"))
        }

        events[1].asClue {
            it.type shouldBe "volume"
            it.action shouldBe "destroy"
            it.scope shouldBe "local"
            it.timestamp shouldBeGreaterThan startTime
            it.timestamp shouldBeLessThan endTime
            it.actor shouldBe Actor(volumeName, mapOf("driver" to "local"))
        }
    }

    should("gracefully handle a event processor that throws an exception").onlyIfDockerDaemonPresent {
        val startTime = Clock.System.now() - clockSkewFudgeFactor
        val volumeName = "test-volume-${Random.nextInt()}"
        val volume = client.createVolume(volumeName)
        client.deleteVolume(volume)
        val endTime = Clock.System.now() + clockSkewFudgeFactor

        val exceptionThrownByProcessor = RuntimeException("This is an exception from the callback handler")

        val exceptionThrownByStreamMethod = shouldThrow<StreamingEventsFailedException> {
            client.streamEvents(startTime, endTime, emptyMap()) {
                throw exceptionThrownByProcessor
            }
        }

        exceptionThrownByStreamMethod.message shouldBe "Event receiver threw an exception: $exceptionThrownByProcessor"
        exceptionThrownByStreamMethod.cause shouldBe exceptionThrownByProcessor
    }
})
