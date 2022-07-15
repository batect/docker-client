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
import batect.dockerclient.native.CreateInputPipe
import batect.dockerclient.native.DisposeInputPipe
import batect.dockerclient.native.FreeCreateInputPipeReturn
import batect.dockerclient.use
import kotlinx.cinterop.pointed
import okio.Sink
import okio.Source

public actual sealed interface TextInput {
    public actual fun prepareStream(): PreparedInputStream
    public actual fun abortRead()

    public actual companion object {
        public actual val StandardInput: TextInput = StandardTextInput(1u)
    }
}

public actual class SourceTextInput actual constructor(private val source: Source) : TextInput {
    override fun prepareStream(): PreparedInputStream {
        return Pipe(source)
    }

    override fun abortRead() {
        source.close()
    }

    private class Pipe(private val source: Source) : PreparedInputStream {
        public override val inputStreamHandle: ULong
        private val sink: Sink

        init {
            val ret = CreateInputPipe()!!

            try {
                if (ret.pointed.Error != null) {
                    throw DockerClientException(ret.pointed.Error!!.pointed)
                }

                inputStreamHandle = ret.pointed.InputStream
                val fd = ret.pointed.WriteFileDescriptor
                sink = PipeSink(fd, inputStreamHandle)
            } finally {
                FreeCreateInputPipeReturn(ret)
            }
        }

        override fun run() {
            source.streamTo(sink)
            sink.close()
        }

        override fun close() {
            DisposeInputPipe(inputStreamHandle).use { error ->
                if (error != null) {
                    throw DockerClientException(error.pointed)
                }
            }
        }
    }
}
