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

import kotlinx.atomicfu.atomic
import kotlinx.coroutines.sync.Semaphore

/**
 * Used to signal when an operation has finished initialising.
 *
 * Important: only one caller can wait on a [ReadyNotification].
 *
 * Important: a single instance should only be used once, for one operation.
 *
 */
public class ReadyNotification {
    private val semaphore = Semaphore(1, 1)
    private val haveAlreadyMarkedAsReady = atomic<Boolean>(false)
    private val haveAlreadyWaited = atomic<Boolean>(false)

    /**
     * Signal that the operation has finished initialising.
     */
    public fun markAsReady() {
        if (!haveAlreadyMarkedAsReady.compareAndSet(false, true)) {
            throw UnsupportedOperationException("ReadyNotification has already been marked as ready.")
        }

        semaphore.release()
    }

    /**
     * Block until the operation finishes initialising.
     */
    public suspend fun waitForReady() {
        if (!haveAlreadyWaited.compareAndSet(false, true)) {
            throw UnsupportedOperationException("Cannot wait on a ReadyNotification multiple times.")
        }

        semaphore.acquire()
    }
}
