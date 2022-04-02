/*
    Copyright 2017-2021 Charles Korn.

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

package batect.dockerclient.buildtools.golang

import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property

abstract class GolangPluginExtension {
    abstract val sourceDirectory: DirectoryProperty
    abstract val golangVersion: Property<String>
    abstract val golangCILintVersion: Property<String>
    abstract val golangRoot: DirectoryProperty
    abstract val golangCompilerExecutablePath: RegularFileProperty
    abstract val linterExecutablePath: RegularFileProperty
}
