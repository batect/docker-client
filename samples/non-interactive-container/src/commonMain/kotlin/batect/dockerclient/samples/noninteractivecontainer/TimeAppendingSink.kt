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

package batect.dockerclient.samples.noninteractivecontainer

import kotlinx.datetime.Clock
import okio.Buffer
import okio.Sink
import okio.Timeout

class TimeAppendingSink : Sink {
    private val buffer = StringBuilder()

    override fun timeout(): Timeout = Timeout.NONE
    override fun flush() {}

    override fun write(source: Buffer, byteCount: Long) {
        buffer.append(source.readUtf8(byteCount))
        writeFullLinesIfAvailable()
    }

    override fun close() {
        writeFullLinesIfAvailable()

        if (buffer.isNotEmpty()) {
            writeLine(buffer.toString())
        }
    }

    private fun writeFullLinesIfAvailable() {
        while (true) {
            val endOfLine = buffer.indexOf('\n')

            if (endOfLine == -1) {
                return
            }

            val line = buffer.substring(0, endOfLine)
            writeLine(line)

            buffer.deleteRange(0, endOfLine + 1)
        }
    }

    private fun writeLine(line: CharSequence) {
        print("${Clock.System.now()}: $line\n")
    }
}
