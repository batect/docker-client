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

package batect.dockerclient.buildtools.formatting

import com.diffplug.gradle.spotless.SpotlessExtension
import com.diffplug.gradle.spotless.SpotlessPlugin
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.getByType
import java.nio.file.Files

class FormattingConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        applySpotless(target)
        configureSpotless(target)
    }

    private fun applySpotless(target: Project) {
        target.plugins.apply(SpotlessPlugin::class.java)
    }

    private fun configureSpotless(target: Project) {
        val licenseText = Files.readString(target.rootProject.projectDir.resolve("gradle").resolve("license.txt").toPath())!!
        val isKotlinProject = target.plugins.hasPlugin("org.jetbrains.kotlin.multiplatform")
        val kotlinLicenseHeader = "/*\n${licenseText.trimEnd().lines().joinToString("\n") { "    $it".trimEnd() }}\n*/\n\n"
        val ktlintVersion = getKtlintVersion(target)

        target.configure<SpotlessExtension> {
            encoding("UTF-8")

            kotlinGradle {
                it.ktlint(ktlintVersion)
                it.licenseHeader(kotlinLicenseHeader, "plugins|rootProject|import")
            }

            if (isKotlinProject) {
                kotlin {
                    it.target(target.fileTree("src").include("**/*.kt"))

                    it.ktlint(ktlintVersion)
                    it.licenseHeader(kotlinLicenseHeader, "package |@file|// AUTOGENERATED")
                }
            }
        }
    }

    private fun getKtlintVersion(target: Project): String {
        val catalogs = target.extensions.getByType<VersionCatalogsExtension>()
        val libs = catalogs.named("libs")

        return libs.findVersion("ktlint").get().requiredVersion
    }
}
