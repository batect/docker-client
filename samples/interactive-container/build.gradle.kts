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

import batect.dockerclient.buildtools.capitalize
import batect.dockerclient.buildtools.kotlin.isSameOperatingSystemAsHost
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    application

    id("batect.dockerclient.buildtools.formatting")
    id("batect.dockerclient.buildtools.licensecheck")
}

repositories {
    mavenCentral()
}

val rootPackage = "batect.dockerclient.samples.interactivecontainer"

kotlin {
    jvm {
        compilations.configureEach {
            kotlinOptions {
                jvmTarget = "1.8"
            }
        }

        withJava()
    }

    linuxX64()
    macosX64()
    macosArm64()
    mingwX64()

    targets.withType<KotlinNativeTarget> {
        val target = this

        binaries {
            executable {
                entryPoint = "$rootPackage.main"
            }
        }

        // I'm not sure where the culprit lies, but these tasks fails on macOS hosts for non-macOS targets.
        // (Linux and Windows can run this just fine for each other's targets.)
        // Given we can't run the linked executable on non-matching hosts, there's little point in doing this anyway.
        // The same issue applies to the client library itself as well.
        setOf(
            "linkDebugExecutable${target.name.capitalize()}",
            "linkReleaseExecutable${target.name.capitalize()}",
        ).forEach { name ->
            tasks.named(name) {
                onlyIf { target.konanTarget.isSameOperatingSystemAsHost }
            }
        }
    }

    sourceSets {
        commonMain {
            dependencies {
                implementation(project(":client"))
                implementation(libs.kotlinx.coroutines.core)
            }
        }
    }
}

application {
    mainClass.set("$rootPackage.ApplicationKt")
}

java {
    targetCompatibility = JavaVersion.VERSION_1_8
}
