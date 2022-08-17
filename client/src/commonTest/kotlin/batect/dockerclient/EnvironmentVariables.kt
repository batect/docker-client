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

expect fun getEnvironmentVariable(name: String): String?
expect fun getEnvironmentVariableAccordingToGolang(name: String): String?
expect fun unsetEnvironmentVariableForGolang(name: String)
expect fun setEnvironmentVariableForGolang(name: String, value: String)

internal inline fun <R> withoutEnvironmentVariable(name: String, block: () -> R): R {
    val originalValue = getEnvironmentVariableAccordingToGolang(name)
    unsetEnvironmentVariableForGolang(name)

    try {
        return block()
    } finally {
        if (originalValue != null) {
            setEnvironmentVariableForGolang(name, originalValue)
        }
    }
}

internal inline fun <R> withEnvironmentVariable(name: String, value: String, block: () -> R): R {
    val originalValue = getEnvironmentVariableAccordingToGolang(name)
    setEnvironmentVariableForGolang(name, value)

    try {
        return block()
    } finally {
        if (originalValue != null) {
            setEnvironmentVariableForGolang(name, originalValue)
        } else {
            unsetEnvironmentVariableForGolang(name)
        }
    }
}
