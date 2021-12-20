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
import batect.dockerclient.native.BuildImageProgressUpdate_StepDownloadProgressUpdate
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
import batect.dockerclient.native.Error
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
    // This property is internally visible so that tests can get this value to establish scenarios
    // by communicating with the Golang wrapper.
    internal val clientHandle: DockerClientHandle = createClient(configuration)

    private fun createClient(configuration: DockerClientConfiguration): DockerClientHandle {
        memScoped {
            CreateClient(allocClientConfiguration(configuration).ptr)!!.use { ret ->
                if (ret.pointed.Error != null) {
                    throw DockerClientException(ret.pointed.Error!!.pointed)
                }

                return ret.pointed.Client
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
        Ping(clientHandle)!!.use { ret ->
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
        }
    }

    public override fun getDaemonVersionInformation(): DaemonVersionInformation {
        GetDaemonVersionInformation(clientHandle)!!.use { ret ->
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
        }
    }

    public override fun listAllVolumes(): Set<VolumeReference> {
        ListAllVolumes(clientHandle)!!.use { ret ->
            if (ret.pointed.Error != null) {
                throw ListAllVolumesFailedException(ret.pointed.Error!!.pointed)
            }

            return fromArray(ret.pointed.Volumes!!, ret.pointed.VolumesCount) { VolumeReference(it) }.toSet()
        }
    }

    public override fun createVolume(name: String): VolumeReference {
        CreateVolume(clientHandle, name.cstr)!!.use { ret ->
            if (ret.pointed.Error != null) {
                throw VolumeCreationFailedException(ret.pointed.Error!!.pointed)
            }

            return VolumeReference(ret.pointed.Response!!.pointed)
        }
    }

    public override fun deleteVolume(volume: VolumeReference) {
        DeleteVolume(clientHandle, volume.name.cstr).ifFailed { error ->
            throw VolumeDeletionFailedException(error.pointed)
        }
    }

    public override fun createNetwork(name: String, driver: String): NetworkReference {
        CreateNetwork(clientHandle, name.cstr, driver.cstr)!!.use { ret ->
            if (ret.pointed.Error != null) {
                throw NetworkCreationFailedException(ret.pointed.Error!!.pointed)
            }

            return NetworkReference(ret.pointed.Response!!.pointed)
        }
    }

    public override fun deleteNetwork(network: NetworkReference) {
        DeleteNetwork(clientHandle, network.id.cstr).ifFailed { error ->
            throw NetworkDeletionFailedException(error.pointed)
        }
    }

    public override fun getNetworkByNameOrID(searchFor: String): NetworkReference? {
        GetNetworkByNameOrID(clientHandle, searchFor.cstr)!!.use { ret ->
            if (ret.pointed.Error != null) {
                throw NetworkRetrievalFailedException(ret.pointed.Error!!.pointed)
            }

            if (ret.pointed.Response == null) {
                return null
            }

            return NetworkReference(ret.pointed.Response!!.pointed)
        }
    }

    public override fun pullImage(name: String, onProgressUpdate: ImagePullProgressReceiver): ImageReference {
        val callbackState = CallbackState<PullImageProgressUpdate> { progress ->
            onProgressUpdate.invoke(ImagePullProgressUpdate(progress!!.pointed))
        }

        return callbackState.use { callback, callbackUserData ->
            PullImage(clientHandle, name.cstr, callback, callbackUserData)!!.use { ret ->
                if (ret.pointed.Error != null) {
                    val errorType = ret.pointed.Error!!.pointed.Type!!.toKString()

                    if (errorType == "main.ProgressCallbackFailedError") {
                        throw ImagePullFailedException("Image pull progress receiver threw an exception: ${callbackState.exceptionThrown}", callbackState.exceptionThrown, errorType)
                    }

                    throw ImagePullFailedException(ret.pointed.Error!!.pointed)
                }

                ImageReference(ret.pointed.Response!!.pointed)
            }
        }
    }

    public override fun deleteImage(image: ImageReference, force: Boolean) {
        DeleteImage(clientHandle, image.id.cstr, force).ifFailed { error ->
            throw ImageDeletionFailedException(error.pointed)
        }
    }

    public override fun getImage(name: String): ImageReference? {
        GetImage(clientHandle, name.cstr)!!.use { ret ->
            if (ret.pointed.Error != null) {
                throw ImageRetrievalFailedException(ret.pointed.Error!!.pointed)
            }

            if (ret.pointed.Response == null) {
                return null
            }

            return ImageReference(ret.pointed.Response!!.pointed)
        }
    }

    public override fun buildImage(spec: ImageBuildSpec, output: TextOutput, onProgressUpdate: ImageBuildProgressReceiver): ImageReference {
        memScoped {
            output.prepareStream().use { stream ->
                val callbackState = CallbackState<BuildImageProgressUpdate> { progress ->
                    onProgressUpdate.invoke(ImageBuildProgressUpdate(progress!!.pointed))
                }

                return callbackState.use { callback, callbackUserData ->
                    BuildImage(
                        clientHandle,
                        allocBuildImageRequest(spec).ptr,
                        stream.outputStreamHandle,
                        false, // FIXME: Only required while we're using the old Kotlin/Native memory model that does not support sharing values between threads.
                        callback,
                        callbackUserData
                    )!!.use { ret ->
                        stream.run() // FIXME: This really should be done in parallel with the BuildImage call above, but Kotlin/Native does not yet support multithreaded coroutines

                        if (ret.pointed.Error != null) {
                            val errorType = ret.pointed.Error!!.pointed.Type!!.toKString()

                            if (errorType == "main.ProgressCallbackFailedError") {
                                throw ImageBuildFailedException("Image build progress receiver threw an exception: ${callbackState.exceptionThrown}", callbackState.exceptionThrown, errorType)
                            }

                            throw ImageBuildFailedException(ret.pointed.Error!!.pointed)
                        }

                        val imageReference = ImageReference(ret.pointed.Response!!.pointed)
                        onProgressUpdate(BuildComplete(imageReference))

                        imageReference
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
        request.TargetBuildStage = spec.targetBuildStage.cstr.ptr

        return request
    }

    override fun close() {
        DisposeClient(clientHandle).ifFailed { error ->
            throw DockerClientException(error.pointed)
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
    native.StepDownloadProgressUpdate != null -> StepDownloadProgressUpdate(native.StepDownloadProgressUpdate!!.pointed)
    native.StepFinished != null -> StepFinished(native.StepFinished!!.pointed)
    native.BuildFailed != null -> BuildFailed(native.BuildFailed!!.pointed)
    else -> throw RuntimeException("${BuildImageProgressUpdate::class.qualifiedName} did not contain an update")
}

private fun ImageBuildContextUploadProgress(native: BuildImageProgressUpdate_ImageBuildContextUploadProgress): ImageBuildContextUploadProgress =
    ImageBuildContextUploadProgress(native.BytesUploaded)

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

private fun StepDownloadProgressUpdate(native: BuildImageProgressUpdate_StepDownloadProgressUpdate): StepDownloadProgressUpdate =
    StepDownloadProgressUpdate(
        native.StepNumber,
        native.DownloadedBytes,
        native.TotalBytes
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
