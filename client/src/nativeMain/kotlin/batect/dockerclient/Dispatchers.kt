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

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Runnable
import kotlinx.coroutines.newFixedThreadPoolContext
import kotlin.coroutines.CoroutineContext

// FIXME: This is only required until https://github.com/Kotlin/kotlinx.coroutines/issues/3205 is resolved.
// We use HackIODispatcher (instead of Dispatchers.Default above) because the implementation of Dispatchers.Default on
// Windows and Linux uses a fixed thread pool size of 4 threads, which is not enough for some of our tests - and would
// definitely not be enough for any non-trivial application that consumes this library.
internal actual val IODispatcher: CoroutineDispatcher = HackIODispatcher

private object HackIODispatcher : CoroutineDispatcher() {
    // FIXME: The use of this fixed thread pool size means that dispatching will block once the maximum number of threads have been reached.
    private val ctx = newFixedThreadPoolContext(20, "batect.dockerclient.HackIODispatcher")

    override fun dispatch(context: CoroutineContext, block: Runnable) {
        ctx.dispatch(context, block)
    }
}
