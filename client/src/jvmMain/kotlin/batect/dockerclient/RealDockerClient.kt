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

package batect.dockerclient

import batect.dockerclient.io.TextOutput
import batect.dockerclient.native.BuildImageProgressCallback
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
import batect.dockerclient.native.CreateContainerRequest
import batect.dockerclient.native.DockerClientHandle
import batect.dockerclient.native.PullImageProgressCallback
import batect.dockerclient.native.PullImageProgressUpdate
import batect.dockerclient.native.StringPair
import batect.dockerclient.native.TLSConfiguration
import batect.dockerclient.native.bindMounts
import batect.dockerclient.native.buildArgs
import batect.dockerclient.native.command
import batect.dockerclient.native.entrypoint
import batect.dockerclient.native.environmentVariables
import batect.dockerclient.native.extraHosts
import batect.dockerclient.native.imageTags
import batect.dockerclient.native.nativeAPI
import batect.dockerclient.native.volumes
import jnr.ffi.Pointer
import jnr.ffi.Runtime
import jnr.ffi.Struct
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlin.time.Duration

internal actual class RealDockerClient actual constructor(configuration: DockerClientConfiguration) : DockerClient, AutoCloseable {
    // This property is internally visible so that tests can get this value to establish scenarios
    // by communicating with the Golang wrapper.
    internal val clientHandle: DockerClientHandle = createClient(configuration)

    private fun createClient(configuration: DockerClientConfiguration): DockerClientHandle {
        nativeAPI.CreateClient(ClientConfiguration(configuration))!!.use { ret ->
            if (ret.error != null) {
                throw DockerClientException(ret.error!!)
            }

            return ret.client.get()
        }
    }

    public override fun ping(): PingResponse {
        nativeAPI.Ping(clientHandle)!!.use { ret ->
            if (ret.error != null) {
                throw PingFailedException(ret.error!!)
            }

            val response = ret.response!!

            return PingResponse(
                response.apiVersion.get(),
                response.osType.get(),
                response.experimental.get(),
                BuilderVersion.fromAPI(response.builderVersion.get())
            )
        }
    }

    public override fun getDaemonVersionInformation(): DaemonVersionInformation {
        nativeAPI.GetDaemonVersionInformation(clientHandle)!!.use { ret ->
            if (ret.error != null) {
                throw GetDaemonVersionInformationFailedException(ret.error!!)
            }

            val response = ret.response!!

            return DaemonVersionInformation(
                response.version.get(),
                response.apiVersion.get(),
                response.minAPIVersion.get(),
                response.gitCommit.get(),
                response.operatingSystem.get(),
                response.architecture.get(),
                response.experimental.get()
            )
        }
    }

    public override fun listAllVolumes(): Set<VolumeReference> {
        nativeAPI.ListAllVolumes(clientHandle)!!.use { ret ->
            if (ret.error != null) {
                throw ListAllVolumesFailedException(ret.error!!)
            }

            return ret.volumes.map { VolumeReference(it) }.toSet()
        }
    }

    public override fun createVolume(name: String): VolumeReference {
        nativeAPI.CreateVolume(clientHandle, name)!!.use { ret ->
            if (ret.error != null) {
                throw VolumeCreationFailedException(ret.error!!)
            }

            return VolumeReference(ret.response!!)
        }
    }

    public override fun deleteVolume(volume: VolumeReference) {
        nativeAPI.DeleteVolume(clientHandle, volume.name).use { error ->
            if (error != null) {
                throw VolumeDeletionFailedException(error)
            }
        }
    }

    public override fun createNetwork(name: String, driver: String): NetworkReference {
        nativeAPI.CreateNetwork(clientHandle, name, driver)!!.use { ret ->
            if (ret.error != null) {
                throw NetworkCreationFailedException(ret.error!!)
            }

            return NetworkReference(ret.response!!)
        }
    }

    public override fun deleteNetwork(network: NetworkReference) {
        nativeAPI.DeleteNetwork(clientHandle, network.id).use { error ->
            if (error != null) {
                throw NetworkDeletionFailedException(error)
            }
        }
    }

    public override fun getNetworkByNameOrID(searchFor: String): NetworkReference? {
        nativeAPI.GetNetworkByNameOrID(clientHandle, searchFor)!!.use { ret ->
            if (ret.error != null) {
                throw NetworkRetrievalFailedException(ret.error!!)
            }

            if (ret.response == null) {
                return null
            }

            return NetworkReference(ret.response!!)
        }
    }

    public override fun pullImage(name: String, onProgressUpdate: ImagePullProgressReceiver): ImageReference {
        var exceptionThrownInCallback: Throwable? = null

        val callback = object : PullImageProgressCallback {
            override fun invoke(userData: Pointer?, progressPointer: Pointer?): Boolean {
                try {
                    val progress = PullImageProgressUpdate(progressPointer!!)
                    onProgressUpdate(ImagePullProgressUpdate(progress))

                    return true
                } catch (t: Throwable) {
                    exceptionThrownInCallback = t

                    return false
                }
            }
        }

        nativeAPI.PullImage(clientHandle, name, callback, null)!!.use { ret ->
            if (ret.error != null) {
                if (ret.error!!.type.get() == "main.ProgressCallbackFailedError") {
                    throw ImagePullFailedException("Image pull progress receiver threw an exception: $exceptionThrownInCallback", exceptionThrownInCallback)
                }

                throw ImagePullFailedException(ret.error!!)
            }

            return ImageReference(ret.response!!)
        }
    }

    public override fun deleteImage(image: ImageReference, force: Boolean) {
        nativeAPI.DeleteImage(clientHandle, image.id, force).use { error ->
            if (error != null) {
                throw ImageDeletionFailedException(error)
            }
        }
    }

    public override fun getImage(name: String): ImageReference? {
        nativeAPI.GetImage(clientHandle, name)!!.use { ret ->
            if (ret.error != null) {
                throw ImageRetrievalFailedException(ret.error!!)
            }

            if (ret.response == null) {
                return null
            }

            return ImageReference(ret.response!!)
        }
    }

    override fun buildImage(spec: ImageBuildSpec, output: TextOutput, onProgressUpdate: ImageBuildProgressReceiver): ImageReference {
        var exceptionThrownInCallback: Throwable? = null

        val callback = object : BuildImageProgressCallback {
            override fun invoke(userData: Pointer?, progressPointer: Pointer?): Boolean {
                try {
                    val progress = BuildImageProgressUpdate(progressPointer!!)
                    onProgressUpdate(ImageBuildProgressUpdate(progress))

                    return true
                } catch (t: Throwable) {
                    exceptionThrownInCallback = t

                    return false
                }
            }
        }

        output.prepareStream().use { stream ->
            return runBlocking(Dispatchers.IO) {
                launch { stream.run() }

                nativeAPI.BuildImage(clientHandle, BuildImageRequest(spec), stream.outputStreamHandle.toLong(), callback, null)!!.use { ret ->
                    if (ret.error != null) {
                        if (ret.error!!.type.get() == "main.ProgressCallbackFailedError") {
                            throw ImageBuildFailedException(
                                "Image build progress receiver threw an exception: $exceptionThrownInCallback",
                                exceptionThrownInCallback
                            )
                        }

                        throw ImageBuildFailedException(ret.error!!)
                    }

                    val imageReference = ImageReference(ret.response!!)
                    onProgressUpdate(BuildComplete(imageReference))

                    imageReference
                }
            }
        }
    }

    override fun pruneImageBuildCache() {
        nativeAPI.PruneImageBuildCache(clientHandle).use { error ->
            if (error != null) {
                throw ImageBuildCachePruneFailedException(error)
            }
        }
    }

    override fun createContainer(spec: ContainerCreationSpec): ContainerReference {
        nativeAPI.CreateContainer(clientHandle, CreateContainerRequest(spec))!!.use { ret ->
            if (ret.error != null) {
                throw ContainerCreationFailedException(ret.error!!)
            }

            return ContainerReference(ret.response!!.id.get())
        }
    }

    override fun startContainer(container: ContainerReference) {
        nativeAPI.StartContainer(clientHandle, container.id).use { error ->
            if (error != null) {
                throw ContainerStartFailedException(error)
            }
        }
    }

    override fun stopContainer(container: ContainerReference, timeout: Duration) {
        nativeAPI.StopContainer(clientHandle, container.id, timeout.inWholeSeconds).use { error ->
            if (error != null) {
                throw ContainerStopFailedException(error)
            }
        }
    }

    override fun removeContainer(container: ContainerReference, force: Boolean, removeVolumes: Boolean) {
        nativeAPI.RemoveContainer(clientHandle, container.id, force, removeVolumes).use { error ->
            if (error != null) {
                throw ContainerRemovalFailedException(error)
            }
        }
    }

    override suspend fun attachToContainerOutput(
        container: ContainerReference,
        stdout: TextOutput,
        stderr: TextOutput,
        attachedNotification: ReadyNotification?
    ) {
        val callback = ReadyCallback(attachedNotification)

        stdout.prepareStream().use { stdoutStream ->
            stderr.prepareStream().use { stderrStream ->
                withContext(IODispatcher) {
                    launch { stdoutStream.run() }
                    launch { stderrStream.run() }

                    launchWithGolangContext { context ->
                        nativeAPI.AttachToContainerOutput(
                            clientHandle,
                            context.handle,
                            container.id,
                            stdoutStream.outputStreamHandle.toLong(),
                            stderrStream.outputStreamHandle.toLong(),
                            callback,
                            null
                        ).use { error ->
                            if (error != null) {
                                if (error.type.get() == "main.ReadyCallbackFailedError") {
                                    throw callback.exceptionThrownInCallback!!
                                }

                                throw AttachToContainerFailedException(error)
                            }
                        }
                    }
                }
            }
        }
    }

    override suspend fun waitForContainerToExit(container: ContainerReference, waitingNotification: ReadyNotification?): Long {
        return withContext(IODispatcher) {
            launchWithGolangContext { context ->
                val callback = ReadyCallback(waitingNotification)

                nativeAPI.WaitForContainerToExit(clientHandle, context.handle, container.id, callback, null)!!.use { ret ->
                    if (ret.error != null) {
                        if (ret.error!!.type.get() == "main.ReadyCallbackFailedError") {
                            throw callback.exceptionThrownInCallback!!
                        }

                        throw ContainerWaitFailedException(ret.error!!)
                    }

                    ret.exitCode.longValue()
                }
            }
        }
    }

    override fun close() {
        nativeAPI.DisposeClient(clientHandle).use { error ->
            if (error != null) {
                throw DockerClientException(error)
            }
        }
    }

    private fun VolumeReference(native: batect.dockerclient.native.VolumeReference): VolumeReference = VolumeReference(native.name.get())
    private fun NetworkReference(native: batect.dockerclient.native.NetworkReference): NetworkReference = NetworkReference(native.id.get())
    private fun ImageReference(native: batect.dockerclient.native.ImageReference): ImageReference = ImageReference(native.id.get())

    private fun ImagePullProgressUpdate(native: batect.dockerclient.native.PullImageProgressUpdate): ImagePullProgressUpdate =
        ImagePullProgressUpdate(native.message.get(), ImagePullProgressDetail(native.detail), native.id.get())

    private fun ImagePullProgressDetail(native: batect.dockerclient.native.PullImageProgressDetail?): ImagePullProgressDetail? =
        when (native) {
            null -> null
            else -> ImagePullProgressDetail(native.current.get(), native.total.get())
        }

    private fun ImageBuildProgressUpdate(native: BuildImageProgressUpdate): ImageBuildProgressUpdate = when {
        native.imageBuildContextUploadProgress != null -> contextUploadProgress(native.imageBuildContextUploadProgress!!)
        native.stepStarting != null -> StepStarting(native.stepStarting!!)
        native.stepOutput != null -> StepOutput(native.stepOutput!!)
        native.stepPullProgressUpdate != null -> StepPullProgressUpdate(native.stepPullProgressUpdate!!)
        native.stepDownloadProgressUpdate != null -> StepDownloadProgressUpdate(native.stepDownloadProgressUpdate!!)
        native.stepFinished != null -> StepFinished(native.stepFinished!!)
        native.buildFailed != null -> BuildFailed(native.buildFailed!!)
        else -> throw RuntimeException("${BuildImageProgressUpdate::class.qualifiedName} did not contain an update")
    }

    private fun contextUploadProgress(native: BuildImageProgressUpdate_ImageBuildContextUploadProgress): ImageBuildProgressUpdate =
        when (native.stepNumber.get()) {
            0L -> ImageBuildContextUploadProgress(native.bytesUploaded.get())
            else -> StepContextUploadProgress(native.stepNumber.get(), native.bytesUploaded.get())
        }

    private fun StepStarting(native: BuildImageProgressUpdate_StepStarting): StepStarting =
        StepStarting(native.stepNumber.get(), native.stepName.get())

    private fun StepOutput(native: BuildImageProgressUpdate_StepOutput): StepOutput =
        StepOutput(native.stepNumber.get(), native.output.get())

    private fun StepPullProgressUpdate(native: BuildImageProgressUpdate_StepPullProgressUpdate): StepPullProgressUpdate =
        StepPullProgressUpdate(native.stepNumber.get(), ImagePullProgressUpdate(native.pullProgress!!))

    private fun StepDownloadProgressUpdate(native: BuildImageProgressUpdate_StepDownloadProgressUpdate): StepDownloadProgressUpdate =
        StepDownloadProgressUpdate(native.stepNumber.get(), native.downloadedBytes.get(), native.totalBytes.get())

    private fun StepFinished(native: BuildImageProgressUpdate_StepFinished): StepFinished =
        StepFinished(native.stepNumber.get())

    private fun BuildFailed(native: BuildImageProgressUpdate_BuildFailed): BuildFailed =
        BuildFailed(native.message.get())

    private fun ClientConfiguration(jvm: DockerClientConfiguration): ClientConfiguration {
        val config = ClientConfiguration(Runtime.getRuntime(nativeAPI))
        config.useConfigurationFromEnvironment.set(jvm.useConfigurationFromEnvironment)
        config.host.set(jvm.host)
        config.configDirectoryPath.set(jvm.configDirectoryPath)

        if (jvm.tls != null) {
            config.tlsPointer.set(Struct.getMemory(TLSConfiguration(jvm.tls)))
        } else {
            config.tlsPointer.set(0)
        }

        return config
    }

    private fun TLSConfiguration(jvm: DockerClientTLSConfiguration): TLSConfiguration {
        val tls = TLSConfiguration(Runtime.getRuntime(nativeAPI))
        tls.caFilePath.set(jvm.caFilePath)
        tls.certFilePath.set(jvm.certFilePath)
        tls.keyFilePath.set(jvm.keyFilePath)
        tls.insecureSkipVerify.set(jvm.insecureSkipVerify)

        return tls
    }

    private fun BuildImageRequest(jvm: ImageBuildSpec): BuildImageRequest {
        val request = BuildImageRequest(Runtime.getRuntime(nativeAPI))
        request.contextDirectory.set(jvm.contextDirectory.toString())
        request.pathToDockerfile.set(jvm.pathToDockerfile.toString())
        request.buildArgs = jvm.buildArgs.map { StringPair(it.key, it.value) }
        request.imageTags = jvm.imageTags
        request.alwaysPullBaseImages.set(jvm.alwaysPullBaseImages)
        request.noCache.set(jvm.noCache)
        request.targetBuildStage.set(jvm.targetBuildStage)
        request.builderVersion.set(jvm.builderApiVersion)

        return request
    }

    private fun CreateContainerRequest(jvm: ContainerCreationSpec): CreateContainerRequest {
        val request = CreateContainerRequest(Runtime.getRuntime(nativeAPI))
        request.imageReference.set(jvm.image.id)
        request.command = jvm.command
        request.entrypoint = jvm.entrypoint
        request.workingDirectory.set(jvm.workingDirectory)
        request.hostname.set(jvm.hostname)
        request.extraHosts = jvm.extraHostsFormattedForDocker
        request.environmentVariables = jvm.environmentVariablesFormattedForDocker
        request.bindMounts = jvm.bindMountsFormattedForDocker

        return request
    }

    private class ReadyCallback(private val notification: ReadyNotification?) : batect.dockerclient.native.ReadyCallback {
        var exceptionThrownInCallback: Throwable? = null

        override fun invoke(userData: Pointer?): Boolean {
            try {
                notification?.markAsReady()
                return true
            } catch (t: Throwable) {
                exceptionThrownInCallback = t
                return false
            }
        }
    }
}
