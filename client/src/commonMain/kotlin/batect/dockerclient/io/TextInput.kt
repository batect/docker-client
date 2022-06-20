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

package batect.dockerclient.io

import batect.dockerclient.AutoCloseable
import okio.Source

public expect sealed interface TextInput {
    public fun prepareStream(): PreparedInputStream
    public fun abortRead()

    public companion object {
        public val StandardInput: TextInput
    }
}

public interface PreparedInputStream : AutoCloseable {
    public val inputStreamHandle: ULong
    public fun run()
}

internal class StandardTextInput(val wellKnownInputStreamHandle: ULong) : TextInput {
    override fun prepareStream(): PreparedInputStream {
        return object : PreparedInputStream {
            override val inputStreamHandle: ULong = wellKnownInputStreamHandle

            override fun run() {
                // Nothing to do.
            }

            override fun close() {
                // Nothing to do.
            }
        }
    }

    override fun abortRead() {
        // Nothing to do.
    }
}

public expect class SourceTextInput(source: Source) : TextInput
