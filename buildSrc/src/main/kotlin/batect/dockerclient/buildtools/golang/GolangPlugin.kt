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

package batect.dockerclient.buildtools.golang

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.create
import org.gradle.kotlin.dsl.register

class GolangPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        val extension = createExtension(target)

        registerLintTask(target, extension)
    }

    private fun createExtension(target: Project): GolangPluginExtension {
        val extension = target.extensions.create<GolangPluginExtension>("golang")

        extension.sourceDirectory.convention(target.layout.projectDirectory.dir("src"))

        return extension
    }

    private fun registerLintTask(target: Project, extension: GolangPluginExtension) {
        target.tasks.register<GolangLint>("lint") {
            golangCILintVersion.set(extension.golangCILintToolVersion)
            sourceDirectory.set(extension.sourceDirectory)
            upToDateCheckFilePath.set(target.layout.buildDirectory.file("lint/upToDate"))
        }
    }
}
