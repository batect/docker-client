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

@file:Suppress("ktlint:standard:filename")

package batect.dockerclient.io

import okio.Buffer
import okio.Sink
import okio.Source

// Why don't we use Okio's Buffer.readAll() here?
// readAll() is optimised for reading large amounts of data and won't write any data to the destination sink
// until either the source is exhausted or the buffer's internal byte buffer is filled.
// This isn't quite what we want in our case - we want to propagate output through to the sink as soon as it's
// received, so that it can be displayed in real time, rather than waiting for a buffer to fill.
internal fun Source.streamTo(sink: Sink) {
    val buffer = Buffer()

    while (true) {
        val bytesWritten = this.read(buffer, 8192)

        if (bytesWritten == -1L) {
            return
        }

        sink.write(buffer, bytesWritten)
    }
}
