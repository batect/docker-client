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

package batect.dockerclient.io.posix

import batect.dockerclient.DockerClientException
import batect.dockerclient.native.InputStreamHandle
import batect.dockerclient.native.nativeAPI
import okio.Buffer
import okio.Sink
import okio.Timeout
import java.nio.ByteBuffer

internal class POSIXPipeSink(private val fd: Int, private val handle: InputStreamHandle) : Sink {
    override fun timeout(): Timeout = Timeout.NONE

    override fun write(source: Buffer, byteCount: Long) {
        val buffer = ByteBuffer.wrap(source.readByteArray(byteCount))
        var nextIndex = 0

        while (nextIndex < byteCount) {
            val bytesRemaining = byteCount.toInt() - nextIndex
            val bytesWritten = posix.write(fd, buffer.slice(nextIndex, bytesRemaining), bytesRemaining)

            if (bytesWritten == -1) {
                throw errnoToIOException(posix.errno())
            }

            nextIndex += bytesWritten
        }
    }

    override fun flush() {
        // Nothing to do - pipes don't need to be flushed, see https://stackoverflow.com/a/43188944/1668119.
    }

    override fun close() {
        val error = nativeAPI.CloseInputPipeWriteEnd(handle)

        if (error != null) {
            throw DockerClientException(error)
        }
    }
}
