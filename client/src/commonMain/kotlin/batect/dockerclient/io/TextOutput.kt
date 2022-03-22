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

package batect.dockerclient.io

import batect.dockerclient.AutoCloseable
import okio.Sink

public expect sealed interface TextOutput {
    public fun prepareStream(): PreparedOutputStream

    public companion object {
        public val StandardOutput: TextOutput
        public val StandardError: TextOutput
    }
}

public interface PreparedOutputStream : AutoCloseable {
    public val outputStreamHandle: ULong
    public fun run()
}

internal class StandardTextOutput(val wellKnownOutputStreamHandle: ULong) : TextOutput {
    override fun prepareStream(): PreparedOutputStream {
        return object : PreparedOutputStream {
            override val outputStreamHandle: ULong = wellKnownOutputStreamHandle

            override fun run() {
                // Nothing to do.
            }

            override fun close() {
                // Nothing to do.
            }
        }
    }
}

public expect class SinkTextOutput(sink: Sink) : TextOutput
