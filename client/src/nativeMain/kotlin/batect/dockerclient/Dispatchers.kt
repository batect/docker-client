/*
    Copyright 2017-2021 Charles Korn.

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
import kotlinx.coroutines.Dispatchers

// This is only required until https://github.com/Kotlin/kotlinx.coroutines/issues/3205 is resolved.
internal actual val IODispatcher: CoroutineDispatcher = Dispatchers.Default