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

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.comparables.shouldBeGreaterThan
import io.kotest.matchers.comparables.shouldBeLessThan
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.ExperimentalTime
import kotlin.time.measureTime

@ExperimentalTime
class ReadyNotificationSpec : ShouldSpec({
    // FIXME: this test is a bit of a hack, but I can't find a better way to test this
    should("wait to be marked as ready before releasing a waiting caller") {
        val notification = ReadyNotification()

        coroutineScope {
            launch {
                delay(100.milliseconds)
                notification.markAsReady()
            }

            val waitDuration = measureTime {
                withTimeout(1.seconds) {
                    notification.waitForReady()
                }
            }

            waitDuration shouldBeGreaterThan 50.milliseconds
            waitDuration shouldBeLessThan 150.milliseconds
        }
    }

    should("throw an exception if marked as ready multiple times") {
        val notification = ReadyNotification()
        notification.markAsReady()

        val exception = shouldThrow<UnsupportedOperationException> {
            notification.markAsReady()
        }

        exception.message shouldBe "ReadyNotification has already been marked as ready."
    }

    should("throw an exception if waited upon multiple times") {
        val notification = ReadyNotification()

        val exception = shouldThrow<UnsupportedOperationException> {
            withTimeout(1.seconds) {
                coroutineScope {
                    launch { notification.waitForReady() }
                    launch { notification.waitForReady() }
                }
            }
        }

        exception.message shouldBe "Cannot wait on a ReadyNotification multiple times."
    }
})
