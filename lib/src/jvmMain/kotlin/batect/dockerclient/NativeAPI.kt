package batect.dockerclient

import jnr.ffi.annotations.In

@Suppress("FunctionName")
internal interface NativeAPI {
    fun Ping(@In handle: DockerClientHandle): PingReturn
    fun CreateClient(): CreateClientReturn
    fun DisposeClient(@In handle: DockerClientHandle)

    fun FreeCreateClientReturn(@In value: CreateClientReturn)
    fun FreePingReturn(@In value: PingReturn)
}
