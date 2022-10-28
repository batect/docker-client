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

package batect.dockerclient.buildtools.licensecheck

import app.cash.licensee.LicenseeExtension
import app.cash.licensee.LicenseePlugin
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.MinimalExternalModuleDependency
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.api.provider.Provider
import org.gradle.kotlin.dsl.apply
import org.gradle.kotlin.dsl.getByType

class LicenseCheckPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        target.plugins.withId("org.jetbrains.kotlin.multiplatform") {
            target.apply<LicenseePlugin>()

            val extension = target.extensions.getByType<LicenseeExtension>()

            extension.allow("Apache-2.0")
            extension.allow("MIT")
            extension.allow("EPL-2.0")
            extension.allowUrl("https://asm.ow2.io/license.html") // BSD
        }
    }
}

private fun Project.lib(name: String): Provider<MinimalExternalModuleDependency> {
    val catalogs = this.extensions.getByType<VersionCatalogsExtension>()
    val libs = catalogs.named("libs")

    return libs.findLibrary(name).orElseGet { throw RuntimeException("No entry for '$name' found in version catalog.") }
}
