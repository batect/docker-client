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

package batect.dockerclient

import batect.dockerclient.io.TextOutput
import batect.dockerclient.native.BuildImage
import batect.dockerclient.native.BuildImageProgressUpdate
import batect.dockerclient.native.BuildImageProgressUpdate_BuildFailed
import batect.dockerclient.native.BuildImageProgressUpdate_ImageBuildContextUploadProgress
import batect.dockerclient.native.BuildImageProgressUpdate_StepFinished
import batect.dockerclient.native.BuildImageProgressUpdate_StepOutput
import batect.dockerclient.native.BuildImageProgressUpdate_StepPullProgressUpdate
import batect.dockerclient.native.BuildImageProgressUpdate_StepStarting
import batect.dockerclient.native.BuildImageRequest
import batect.dockerclient.native.ClientConfiguration
import batect.dockerclient.native.CreateClient
import batect.dockerclient.native.CreateNetwork
import batect.dockerclient.native.CreateVolume
import batect.dockerclient.native.DeleteImage
import batect.dockerclient.native.DeleteNetwork
import batect.dockerclient.native.DeleteVolume
import batect.dockerclient.native.DisposeClient
import batect.dockerclient.native.DockerClientHandle
import batect.dockerclient.native.FreeBuildImageReturn
import batect.dockerclient.native.FreeCreateClientReturn
import batect.dockerclient.native.FreeCreateNetworkReturn
import batect.dockerclient.native.FreeCreateVolumeReturn
import batect.dockerclient.native.FreeError
import batect.dockerclient.native.FreeGetDaemonVersionInformationReturn
import batect.dockerclient.native.FreeGetImageReturn
import batect.dockerclient.native.FreeGetNetworkByNameOrIDReturn
import batect.dockerclient.native.FreeListAllVolumesReturn
import batect.dockerclient.native.FreePingReturn
import batect.dockerclient.native.FreePullImageReturn
import batect.dockerclient.native.GetDaemonVersionInformation
import batect.dockerclient.native.GetImage
import batect.dockerclient.native.GetNetworkByNameOrID
import batect.dockerclient.native.ListAllVolumes
import batect.dockerclient.native.Ping
import batect.dockerclient.native.PullImage
import batect.dockerclient.native.PullImageProgressDetail
import batect.dockerclient.native.PullImageProgressUpdate
import batect.dockerclient.native.StringPair
import batect.dockerclient.native.TLSConfiguration
import kotlinx.cinterop.CFunction
import kotlinx.cinterop.COpaquePointer
import kotlinx.cinterop.CPointed
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.CPointerVar
import kotlinx.cinterop.MemScope
import kotlinx.cinterop.StableRef
import kotlinx.cinterop.alloc
import kotlinx.cinterop.allocArrayOf
import kotlinx.cinterop.asStableRef
import kotlinx.cinterop.cstr
import kotlinx.cinterop.get
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.pointed
import kotlinx.cinterop.ptr
import kotlinx.cinterop.staticCFunction
import kotlinx.cinterop.toKString

internal actual class RealDockerClient actual constructor(configuration: DockerClientConfiguration) : DockerClient, AutoCloseable {
    private val clientHandle: DockerClientHandle = createClient(configuration)

    private fun createClient(configuration: DockerClientConfiguration): DockerClientHandle {
        memScoped {
            val ret = CreateClient(allocClientConfiguration(configuration).ptr)!!

            try {
                if (ret.pointed.Error != null) {
                    throw DockerClientException(ret.pointed.Error!!.pointed)
                }

                return ret.pointed.Client
            } finally {
                FreeCreateClientReturn(ret)
            }
        }
    }

    private fun MemScope.allocClientConfiguration(configuration: DockerClientConfiguration): ClientConfiguration {
        val nativeConfig = alloc<ClientConfiguration>()
        nativeConfig.UseConfigurationFromEnvironment = configuration.useConfigurationFromEnvironment
        nativeConfig.Host = configuration.host?.cstr?.ptr
        nativeConfig.ConfigDirectoryPath = configuration.configDirectoryPath?.cstr?.ptr

        if (configuration.tls != null) {
            val tls = alloc<TLSConfiguration>()
            tls.CAFilePath = configuration.tls.caFilePath.cstr.ptr
            tls.CertFilePath = configuration.tls.certFilePath.cstr.ptr
            tls.KeyFilePath = configuration.tls.keyFilePath.cstr.ptr
            tls.InsecureSkipVerify = configuration.tls.insecureSkipVerify

            nativeConfig.TLS = tls.ptr
        } else {
            nativeConfig.TLS = null
        }

        return nativeConfig
    }

    public override fun ping(): PingResponse {
        val ret = Ping(clientHandle)!!

        try {
            if (ret.pointed.Error != null) {
                throw PingFailedException(ret.pointed.Error!!.pointed)
            }

            val response = ret.pointed.Response!!.pointed

            return PingResponse(
                response.APIVersion!!.toKString(),
                response.OSType!!.toKString(),
                response.Experimental,
                BuilderVersion.fromAPI(response.BuilderVersion!!.toKString())
            )
        } finally {
            FreePingReturn(ret)
        }
    }

    public override fun getDaemonVersionInformation(): DaemonVersionInformation {
        val ret = GetDaemonVersionInformation(clientHandle)!!

        try {
            if (ret.pointed.Error != null) {
                throw GetDaemonVersionInformationFailedException(ret.pointed.Error!!.pointed)
            }

            val response = ret.pointed.Response!!.pointed

            return DaemonVersionInformation(
                response.Version!!.toKString(),
                response.APIVersion!!.toKString(),
                response.MinAPIVersion!!.toKString(),
                response.GitCommit!!.toKString(),
                response.OperatingSystem!!.toKString(),
                response.Architecture!!.toKString(),
                response.Experimental
            )
        } finally {
            FreeGetDaemonVersionInformationReturn(ret)
        }
    }

    public override fun listAllVolumes(): Set<VolumeReference> {
        val ret = ListAllVolumes(clientHandle)!!

        try {
            if (ret.pointed.Error != null) {
                throw ListAllVolumesFailedException(ret.pointed.Error!!.pointed)
            }

            return fromArray(ret.pointed.Volumes!!, ret.pointed.VolumesCount) { VolumeReference(it) }.toSet()
        } finally {
            FreeListAllVolumesReturn(ret)
        }
    }

    public override fun createVolume(name: String): VolumeReference {
        val ret = CreateVolume(clientHandle, name.cstr)!!

        try {
            if (ret.pointed.Error != null) {
                throw VolumeCreationFailedException(ret.pointed.Error!!.pointed)
            }

            return VolumeReference(ret.pointed.Response!!.pointed)
        } finally {
            FreeCreateVolumeReturn(ret)
        }
    }

    public override fun deleteVolume(volume: VolumeReference) {
        val error = DeleteVolume(clientHandle, volume.name.cstr)

        try {
            if (error != null) {
                throw VolumeDeletionFailedException(error.pointed)
            }
        } finally {
            FreeError(error)
        }
    }

    public override fun createNetwork(name: String, driver: String): NetworkReference {
        val ret = CreateNetwork(clientHandle, name.cstr, driver.cstr)!!

        try {
            if (ret.pointed.Error != null) {
                throw NetworkCreationFailedException(ret.pointed.Error!!.pointed)
            }

            return NetworkReference(ret.pointed.Response!!.pointed)
        } finally {
            FreeCreateNetworkReturn(ret)
        }
    }

    public override fun deleteNetwork(network: NetworkReference) {
        val error = DeleteNetwork(clientHandle, network.id.cstr)

        try {
            if (error != null) {
                throw NetworkDeletionFailedException(error.pointed)
            }
        } finally {
            FreeError(error)
        }
    }

    public override fun getNetworkByNameOrID(searchFor: String): NetworkReference? {
        val ret = GetNetworkByNameOrID(clientHandle, searchFor.cstr)!!

        try {
            if (ret.pointed.Error != null) {
                throw NetworkRetrievalFailedException(ret.pointed.Error!!.pointed)
            }

            if (ret.pointed.Response == null) {
                return null
            }

            return NetworkReference(ret.pointed.Response!!.pointed)
        } finally {
            FreeGetNetworkByNameOrIDReturn(ret)
        }
    }

    public override fun pullImage(name: String, onProgressUpdate: ImagePullProgressReceiver): ImageReference {
        val callbackState = CallbackState<PullImageProgressUpdate> { progress ->
            onProgressUpdate.invoke(ImagePullProgressUpdate(progress!!.pointed))
        }

        return callbackState.use { callback, callbackUserData ->
            val ret = PullImage(clientHandle, name.cstr, callback, callbackUserData)!!

            try {
                if (ret.pointed.Error != null) {
                    val errorType = ret.pointed.Error!!.pointed.Type!!.toKString()

                    if (errorType == "main.ProgressCallbackFailedError") {
                        throw ImagePullFailedException("Image pull progress receiver threw an exception: ${callbackState.exceptionThrown}", callbackState.exceptionThrown, errorType)
                    }

                    throw ImagePullFailedException(ret.pointed.Error!!.pointed)
                }

                ImageReference(ret.pointed.Response!!.pointed)
            } finally {
                FreePullImageReturn(ret)
            }
        }
    }

    public override fun deleteImage(image: ImageReference, force: Boolean) {
        val error = DeleteImage(clientHandle, image.id.cstr, force)

        try {
            if (error != null) {
                throw ImageDeletionFailedException(error.pointed)
            }
        } finally {
            FreeError(error)
        }
    }

    public override fun getImage(name: String): ImageReference? {
        val ret = GetImage(clientHandle, name.cstr)!!

        try {
            if (ret.pointed.Error != null) {
                throw ImageRetrievalFailedException(ret.pointed.Error!!.pointed)
            }

            if (ret.pointed.Response == null) {
                return null
            }

            return ImageReference(ret.pointed.Response!!.pointed)
        } finally {
            FreeGetImageReturn(ret)
        }
    }

    public override fun buildImage(spec: ImageBuildSpec, output: TextOutput, onProgressUpdate: ImageBuildProgressReceiver): ImageReference {
        memScoped {
            output.prepareStream().use { stream ->
                val callbackState = CallbackState<BuildImageProgressUpdate> { progress ->
                    onProgressUpdate.invoke(ImageBuildProgressUpdate(progress!!.pointed))
                }

                return callbackState.use { callback, callbackUserData ->
                    val ret = BuildImage(clientHandle, allocBuildImageRequest(spec).ptr, stream.outputStreamHandle, callback, callbackUserData)!!

                    try {
                        stream.run() // FIXME: This really should be done in parallel with the BuildImage call above, but Kotlin/Native does not yet support multithreaded coroutines

                        if (ret.pointed.Error != null) {
                            throw ImageBuildFailedException(ret.pointed.Error!!.pointed)
                        }

                        val imageReference = ImageReference(ret.pointed.Response!!.pointed)
                        onProgressUpdate(BuildComplete(imageReference))

                        imageReference
                    } finally {
                        FreeBuildImageReturn(ret)
                    }
                }
            }
        }
    }

    private fun MemScope.allocBuildImageRequest(spec: ImageBuildSpec): BuildImageRequest {
        val request = alloc<BuildImageRequest>()
        request.ContextDirectory = spec.contextDirectory.toString().cstr.ptr
        request.PathToDockerfile = spec.pathToDockerfile.toString().cstr.ptr

        request.BuildArgs = allocArrayOf(
            spec.buildArgs.map {
                val pair = alloc<StringPair>()
                pair.Key = it.key.cstr.ptr
                pair.Value = it.value.cstr.ptr
                pair.ptr
            }
        )
        request.BuildArgsCount = spec.buildArgs.size.toULong()

        request.ImageTags = allocArrayOf(spec.imageTags.map { it.cstr.ptr })
        request.ImageTagsCount = spec.imageTags.size.toULong()
        request.AlwaysPullBaseImages = spec.alwaysPullBaseImages
        request.NoCache = spec.noCache

        return request
    }

    override fun close() {
        val error = DisposeClient(clientHandle)

        try {
            if (error != null) {
                throw DockerClientException(error.pointed)
            }
        } finally {
            FreeError(error)
        }
    }
}

private fun VolumeReference(native: batect.dockerclient.native.VolumeReference): VolumeReference =
    VolumeReference(native.Name!!.toKString())

private fun NetworkReference(native: batect.dockerclient.native.NetworkReference): NetworkReference =
    NetworkReference(native.ID!!.toKString())

private fun ImageReference(native: batect.dockerclient.native.ImageReference): ImageReference =
    ImageReference(native.ID!!.toKString())

private fun ImagePullProgressUpdate(native: PullImageProgressUpdate): ImagePullProgressUpdate =
    ImagePullProgressUpdate(
        native.Message!!.toKString(),
        if (native.Detail == null) null else ImagePullProgressDetail(native.Detail!!.pointed),
        native.ID!!.toKString()
    )

private fun ImagePullProgressDetail(native: PullImageProgressDetail): ImagePullProgressDetail =
    ImagePullProgressDetail(native.Current, native.Total)

private fun ImageBuildProgressUpdate(native: BuildImageProgressUpdate): ImageBuildProgressUpdate = when {
    native.ImageBuildContextUploadProgress != null -> ImageBuildContextUploadProgress(native.ImageBuildContextUploadProgress!!.pointed)
    native.StepStarting != null -> StepStarting(native.StepStarting!!.pointed)
    native.StepOutput != null -> StepOutput(native.StepOutput!!.pointed)
    native.StepPullProgressUpdate != null -> StepPullProgressUpdate(native.StepPullProgressUpdate!!.pointed)
    native.StepFinished != null -> StepFinished(native.StepFinished!!.pointed)
    native.BuildFailed != null -> BuildFailed(native.BuildFailed!!.pointed)
    else -> throw RuntimeException("${BuildImageProgressUpdate::class.qualifiedName} did not contain an update")
}

private fun ImageBuildContextUploadProgress(native: BuildImageProgressUpdate_ImageBuildContextUploadProgress): ImageBuildContextUploadProgress =
    ImageBuildContextUploadProgress(
        native.BytesUploaded,
        native.TotalBytes
    )

private fun StepStarting(native: BuildImageProgressUpdate_StepStarting): StepStarting =
    StepStarting(
        native.StepNumber,
        native.StepName!!.toKString()
    )

private fun StepOutput(native: BuildImageProgressUpdate_StepOutput): StepOutput =
    StepOutput(
        native.StepNumber,
        native.Output!!.toKString()
    )

private fun StepPullProgressUpdate(native: BuildImageProgressUpdate_StepPullProgressUpdate): StepPullProgressUpdate =
    StepPullProgressUpdate(
        native.StepNumber,
        ImagePullProgressUpdate(native.PullProgress!!.pointed)
    )

private fun StepFinished(native: BuildImageProgressUpdate_StepFinished): StepFinished =
    StepFinished(native.StepNumber)

private fun BuildFailed(native: BuildImageProgressUpdate_BuildFailed): BuildFailed =
    BuildFailed(native.Message!!.toKString())

private inline fun <reified NativeType : CPointed, KotlinType> fromArray(
    source: CPointer<CPointerVar<NativeType>>,
    count: ULong,
    creator: (NativeType) -> KotlinType
): List<KotlinType> {
    return (0.toULong().until(count))
        .map { i -> creator(source[i.toLong()]!!.pointed) }
}

private inline fun <T : Any, R> StableRef<T>.use(user: (StableRef<T>) -> R): R {
    try {
        return user(this)
    } finally {
        this.dispose()
    }
}

// What's this for?
// Kotlin/Native does not handle exceptions that propagate out of Kotlin/Native well.
// For example, if a C function invokes a Kotlin function, and that Kotlin function throws an exception,
// the process crashes.
// While we can make sure our own code invoked by C functions don't throw exceptions, we can't make the
// same guarantee for functions provided by users of this library, such as progress reporting callback
// functions.
// This is a helper class that helps us capture exceptions and report them later on.
private class CallbackState<ParameterType : CPointed>(private val callbackFunction: (CPointer<ParameterType>?) -> Unit) {
    var exceptionThrown: Throwable? = null

    fun <R> use(user: (CPointer<CFunction<(COpaquePointer?, CPointer<ParameterType>?) -> Boolean>>, COpaquePointer) -> R): R = StableRef.create(this).use { userDataRef ->
        val callback = staticCFunction { userData: COpaquePointer?, param: CPointer<ParameterType>? ->
            val callbackState = userData!!.asStableRef<CallbackState<ParameterType>>().get()

            try {
                callbackState.callbackFunction(param)
                true
            } catch (t: Throwable) {
                callbackState.exceptionThrown = t
                false
            }
        }

        user(callback, userDataRef.asCPointer())
    }
}
