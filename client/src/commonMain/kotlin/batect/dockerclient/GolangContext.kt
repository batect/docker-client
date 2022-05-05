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

import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

internal expect class GolangContext constructor() : AutoCloseable {
    fun cancel()
}

internal suspend fun <R> launchWithGolangContext(operation: (GolangContext) -> R): R {
    return GolangContext().use { context ->
        withContext(IODispatcher) {
            suspendCancellableCoroutine { continuation ->
                continuation.invokeOnCancellation { context.cancel() }

                try {
                    val result = operation(context)
                    continuation.resume(result)
                } catch (e: Throwable) {
                    // Without this check, if the coroutine was cancelled, we'll report back the exception from the operation, rather than the
                    // CancellationException or TimeoutCancellationException.
                    if (continuation.isCancelled) {
                        continuation.cancel(e)
                    } else {
                        throw e
                    }
                }
            }
        }
    }
}