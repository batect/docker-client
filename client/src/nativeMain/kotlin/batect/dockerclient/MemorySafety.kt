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

import batect.dockerclient.native.BuildImageReturn
import batect.dockerclient.native.CreateClientReturn
import batect.dockerclient.native.CreateContainerReturn
import batect.dockerclient.native.CreateNetworkReturn
import batect.dockerclient.native.CreateVolumeReturn
import batect.dockerclient.native.Error
import batect.dockerclient.native.FreeBuildImageReturn
import batect.dockerclient.native.FreeCreateClientReturn
import batect.dockerclient.native.FreeCreateContainerReturn
import batect.dockerclient.native.FreeCreateNetworkReturn
import batect.dockerclient.native.FreeCreateVolumeReturn
import batect.dockerclient.native.FreeError
import batect.dockerclient.native.FreeGetDaemonVersionInformationReturn
import batect.dockerclient.native.FreeGetImageReturn
import batect.dockerclient.native.FreeGetNetworkByNameOrIDReturn
import batect.dockerclient.native.FreeInspectContainerReturn
import batect.dockerclient.native.FreeListAllVolumesReturn
import batect.dockerclient.native.FreePingReturn
import batect.dockerclient.native.FreePullImageReturn
import batect.dockerclient.native.FreeWaitForContainerToExitReturn
import batect.dockerclient.native.GetDaemonVersionInformationReturn
import batect.dockerclient.native.GetImageReturn
import batect.dockerclient.native.GetNetworkByNameOrIDReturn
import batect.dockerclient.native.InspectContainerReturn
import batect.dockerclient.native.ListAllVolumesReturn
import batect.dockerclient.native.PingReturn
import batect.dockerclient.native.PullImageReturn
import batect.dockerclient.native.WaitForContainerToExitReturn
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.StableRef

internal inline fun <T : Any?, R> T.use(dispose: (T) -> Unit, user: (T) -> R): R {
    try {
        return user(this)
    } finally {
        dispose(this)
    }
}

internal inline fun <T : Any, R> StableRef<T>.use(user: (StableRef<T>) -> R): R = use(StableRef<T>::dispose, user)
internal inline fun <R> CPointer<CreateClientReturn>.use(user: (CPointer<CreateClientReturn>) -> R): R = use(::FreeCreateClientReturn, user)
internal inline fun <R> CPointer<PingReturn>.use(user: (CPointer<PingReturn>) -> R): R = use(::FreePingReturn, user)
internal inline fun <R> CPointer<GetDaemonVersionInformationReturn>.use(user: (CPointer<GetDaemonVersionInformationReturn>) -> R): R = use(::FreeGetDaemonVersionInformationReturn, user)
internal inline fun <R> CPointer<ListAllVolumesReturn>.use(user: (CPointer<ListAllVolumesReturn>) -> R): R = use(::FreeListAllVolumesReturn, user)
internal inline fun <R> CPointer<CreateVolumeReturn>.use(user: (CPointer<CreateVolumeReturn>) -> R): R = use(::FreeCreateVolumeReturn, user)
internal inline fun <R> CPointer<CreateNetworkReturn>.use(user: (CPointer<CreateNetworkReturn>) -> R): R = use(::FreeCreateNetworkReturn, user)
internal inline fun <R> CPointer<GetNetworkByNameOrIDReturn>.use(user: (CPointer<GetNetworkByNameOrIDReturn>) -> R): R = use(::FreeGetNetworkByNameOrIDReturn, user)
internal inline fun <R> CPointer<PullImageReturn>.use(user: (CPointer<PullImageReturn>) -> R): R = use(::FreePullImageReturn, user)
internal inline fun <R> CPointer<GetImageReturn>.use(user: (CPointer<GetImageReturn>) -> R): R = use(::FreeGetImageReturn, user)
internal inline fun <R> CPointer<BuildImageReturn>.use(user: (CPointer<BuildImageReturn>) -> R): R = use(::FreeBuildImageReturn, user)
internal inline fun <R> CPointer<CreateContainerReturn>.use(user: (CPointer<CreateContainerReturn>) -> R): R = use(::FreeCreateContainerReturn, user)
internal inline fun <R> CPointer<WaitForContainerToExitReturn>.use(user: (CPointer<WaitForContainerToExitReturn>) -> R): R = use(::FreeWaitForContainerToExitReturn, user)
internal inline fun <R> CPointer<InspectContainerReturn>.use(user: (CPointer<InspectContainerReturn>) -> R): R = use(::FreeInspectContainerReturn, user)
internal inline fun <R> CPointer<Error>?.use(user: (CPointer<Error>?) -> R): R = use(::FreeError, user)

internal inline fun CPointer<Error>?.ifFailed(onError: (CPointer<Error>) -> Unit) = use { error ->
    if (error != null) {
        onError(error)
    }
}
