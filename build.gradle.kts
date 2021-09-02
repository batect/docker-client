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

import java.nio.file.Files

plugins {
    id("com.diffplug.spotless")
}

repositories {
    mavenCentral()
}

tasks.named<Wrapper>("wrapper") {
    distributionType = Wrapper.DistributionType.ALL
}

val licenseText = Files.readString(project.projectDir.resolve("gradle").resolve("license.txt").toPath())!!

allprojects {
    afterEvaluate {
        if (extensions.findByName("spotless") == null) {
            throw RuntimeException("Project ${this.displayName} does not have the Spotless plugin applied.")
        }

        val isKotlinProject = plugins.hasPlugin("org.jetbrains.kotlin.multiplatform")
        val kotlinLicenseHeader = "/*\n${licenseText.lines().joinToString("\n") { "    $it".trimEnd() }}\n*/\n\n"

        spotless {
            encoding("UTF-8")

            kotlinGradle {
                ktlint()
                licenseHeader(kotlinLicenseHeader, "plugins|rootProject|import")
            }

            if (isKotlinProject) {
                kotlin {
                    target(fileTree("src").include("**/*.kt"))

                    ktlint()
                    licenseHeader(kotlinLicenseHeader)
                }
            }
        }
    }
}
