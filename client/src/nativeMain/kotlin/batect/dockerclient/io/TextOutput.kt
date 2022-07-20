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

import batect.dockerclient.DockerClientException
import batect.dockerclient.native.CreateOutputPipe
import batect.dockerclient.native.DisposeOutputPipe
import batect.dockerclient.native.FreeCreateOutputPipeReturn
import batect.dockerclient.use
import kotlinx.cinterop.ByteVarOf
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.get
import kotlinx.cinterop.pointed
import okio.Buffer
import okio.IOException
import okio.Sink
import platform.posix.strerror

public actual sealed interface TextOutput {
    public actual fun prepareStream(): PreparedOutputStream

    public actual companion object {
        public actual val StandardOutput: TextOutput = StandardTextOutput(1u)
        public actual val StandardError: TextOutput = StandardTextOutput(2u)
    }
}

public actual class SinkTextOutput actual constructor(public val sink: Sink) : TextOutput {
    override fun prepareStream(): PreparedOutputStream {
        return Pipe(sink)
    }

    private class Pipe(private val sink: Sink) : PreparedOutputStream {
        public override val outputStreamHandle: ULong
        private val source: PipeSource

        init {
            val ret = CreateOutputPipe()!!

            try {
                if (ret.pointed.Error != null) {
                    throw DockerClientException(ret.pointed.Error!!.pointed)
                }

                outputStreamHandle = ret.pointed.OutputStream
                val fd = ret.pointed.ReadFileDescriptor
                source = PipeSource(fd)
            } finally {
                FreeCreateOutputPipeReturn(ret)
            }
        }

        override fun run() {
            source.streamTo(sink)
        }

        override fun close() {
            DisposeOutputPipe(outputStreamHandle).use { error ->
                if (error != null) {
                    throw DockerClientException(error.pointed)
                }
            }
        }
    }
}

internal fun ULong.safeToInt(): Int {
    require(this <= Int.MAX_VALUE.toULong()) {
        "Value out of range"
    }

    return this.toInt()
}

// These two functions are based on okio's implementations.
internal fun errnoToIOException(errno: Int): IOException {
    val message = strerror(errno)

    val messageString = if (message != null) {
        Buffer().writeNullTerminated(message).readUtf8()
    } else {
        "errno: $errno"
    }

    return IOException(messageString)
}

internal fun Buffer.writeNullTerminated(bytes: CPointer<ByteVarOf<Byte>>): Buffer = apply {
    var pos = 0
    while (true) {
        val byte = bytes[pos++].toInt()
        if (byte == 0) {
            break
        } else {
            writeByte(byte)
        }
    }
}
