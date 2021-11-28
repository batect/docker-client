/*
    Copyright 2017-2021 Charles Korn.

    Licensed under the Apache License, Version 2.0 (the "License");
    you may not use this file except in compliance with the License.
    You may obtain a copy of the License at

        http://www.apache.org/licenses/LICENSE-2.0

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
import batect.dockerclient.native.FreeError
import kotlinx.cinterop.ByteVarOf
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.get
import kotlinx.cinterop.pointed
import kotlinx.cinterop.usePinned
import okio.Buffer
import okio.IOException
import okio.Sink
import okio.Source
import okio.Timeout
import okio.buffer
import platform.posix.errno
import platform.posix.strerror

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
                val fd = ret.pointed.ReadFileDescriptor.safeToInt()
                source = PipeSource(fd)
            } finally {
                FreeCreateOutputPipeReturn(ret)
            }
        }

        override fun run() {
            source.buffer().readAll(sink)
        }

        override fun close() {
            val error = DisposeOutputPipe(outputStreamHandle)

            try {
                if (error != null) {
                    throw DockerClientException(error.pointed)
                }
            } finally {
                FreeError(error)
            }
        }
    }

    private class PipeSource(private val fd: Int) : Source {
        override fun timeout(): Timeout = Timeout.NONE

        override fun read(sink: Buffer, byteCount: Long): Long {
            val bytesToRead = if (byteCount > Int.MAX_VALUE) Int.MAX_VALUE else byteCount.toInt()
            val buffer = ByteArray(bytesToRead)

            val bytesRead = buffer.usePinned { pinned ->
                platform.posix.read(fd, pinned.addressOf(0), bytesToRead.toULong())
            }

            return when (bytesRead) {
                0L -> -1
                -1L -> throw errnoToIOException(errno)
                else -> {
                    sink.write(buffer, 0, bytesRead.safeToInt())

                    bytesRead
                }
            }
        }

        override fun close() {
            // Nothing to do.
        }
    }
}

private fun ULong.safeToInt() = if (this > Int.MAX_VALUE.toULong()) throw IllegalArgumentException("Value out of range") else this.toInt()
private fun Long.safeToInt() = if (this > Int.MAX_VALUE.toLong()) throw IllegalArgumentException("Value out of range") else this.toInt()

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
