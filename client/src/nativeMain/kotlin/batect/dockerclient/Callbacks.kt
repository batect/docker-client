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

import kotlinx.cinterop.CFunction
import kotlinx.cinterop.COpaquePointer
import kotlinx.cinterop.CPointed
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.StableRef
import kotlinx.cinterop.asStableRef
import kotlinx.cinterop.staticCFunction

// What's this for?
// Kotlin/Native does not handle exceptions that propagate out of Kotlin/Native well.
// For example, if a C function invokes a Kotlin function, and that Kotlin function throws an exception,
// the process crashes.
// While we can make sure our own code invoked by C functions don't throw exceptions, we can't make the
// same guarantee for functions provided by users of this library, such as progress reporting callback
// functions.
// This is a helper class that helps us capture exceptions and report them later on.
@OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)
internal class CallbackState<ParameterType : CPointed>(private val callbackFunction: (CPointer<ParameterType>?) -> Unit) {
    var exceptionThrown: Throwable? = null

    fun <R> use(user: (CPointer<CFunction<(COpaquePointer?, CPointer<ParameterType>?) -> Boolean>>, COpaquePointer) -> R): R = StableRef.create(this).use { userDataRef ->
        val callback = staticCFunction { userData: COpaquePointer?, param: CPointer<ParameterType>? ->
            val callbackState = userData!!.asStableRef<CallbackState<ParameterType>>().get()

            try {
                callbackState.callbackFunction(param)
                true
            } catch (t: Throwable) {
                callbackState.exceptionThrown = t
                false
            }
        }

        user(callback, userDataRef.asCPointer())
    }
}

@OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)
internal class ReadyNotificationCallbackState(private val readyNotification: ReadyNotification?) {
    var exceptionThrown: Throwable? = null

    fun <R> use(user: (CPointer<CFunction<(COpaquePointer?) -> Boolean>>, COpaquePointer) -> R): R = StableRef.create(this).use { userDataRef ->
        val callback = staticCFunction { userData: COpaquePointer? ->
            val callbackState = userData!!.asStableRef<ReadyNotificationCallbackState>().get()

            try {
                callbackState.readyNotification?.markAsReady()
                true
            } catch (t: Throwable) {
                callbackState.exceptionThrown = t
                false
            }
        }

        user(callback, userDataRef.asCPointer())
    }
}
