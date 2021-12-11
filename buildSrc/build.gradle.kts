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
    kotlin("jvm") version "1.6.0"
    kotlin("plugin.serialization") version "1.6.0"
    id("com.diffplug.spotless") version "6.0.4"
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(platform("org.jetbrains.kotlin:kotlin-bom"))
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-core:1.3.1")
    implementation(gradleApi())
    implementation(gradleKotlinDsl())
    implementation("com.github.jnr:jnr-posix:3.1.14")
    implementation("com.charleskorn.kaml:kaml:0.37.0")
}

java {
    targetCompatibility = JavaVersion.VERSION_1_8
}

val licenseText = Files.readString(project.projectDir.resolve("..").resolve("gradle").resolve("license.txt").toPath())!!
val kotlinLicenseHeader = "/*\n${licenseText.lines().joinToString("\n") { "    $it".trimEnd() }}*/\n\n"

spotless {
    encoding("UTF-8")

    kotlinGradle {
        ktlint()
        licenseHeader(kotlinLicenseHeader, "plugins|rootProject|import")
    }

    kotlin {
        target(fileTree("src").include("**/*.kt"))

        ktlint()
        licenseHeader(kotlinLicenseHeader)
    }
}
