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

import batect.dockerclient.buildtools.GolangBuild
import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.gradle.api.tasks.testing.logging.TestLogEvent
import org.jetbrains.kotlin.gradle.dsl.ExplicitApiMode
import org.jetbrains.kotlin.gradle.plugin.KotlinPlatformType
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeCompilation
import org.jetbrains.kotlin.konan.target.Family
import org.jetbrains.kotlin.konan.target.KonanTarget

plugins {
    kotlin("multiplatform") version "1.5.30"
    id("io.kotest.multiplatform") version "5.0.0.5"
    id("com.diffplug.spotless")
}

repositories {
    mavenCentral()
    maven("https://oss.sonatype.org/content/repositories/snapshots")
}

val kotestVersion = "5.0.0.419-SNAPSHOT"

val dockerClientWrapperProject = project(":docker-client-wrapper")
val jvmLibsDir = buildDir.resolve("resources").resolve("jvm")

val buildIsRunningOnLinux = org.gradle.internal.os.OperatingSystem.current().isLinux

kotlin {
    jvm()
    linuxX64()
    macosX64()
    mingwX64()

    // These are currently not supported by kotest:
    //  linuxArm64()
    //  macosArm64()
    //  mingwX86()

    sourceSets {
        val commonMain by getting {
            dependencies {
            }
        }

        val nativeMain by creating {
            dependsOn(commonMain)
        }

        val linuxX64Main by getting {
            dependsOn(nativeMain)
        }

        val macosX64Main by getting {
            dependsOn(nativeMain)
        }

        val mingwX64Main by getting {
            dependsOn(nativeMain)
        }

        val jvmMain by getting {
            dependencies {
                implementation("com.github.jnr:jnr-ffi:2.2.5")
            }

            resources.srcDir(jvmLibsDir)
        }

        val commonTest by getting {
            dependencies {
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.5.1")
                implementation("io.kotest:kotest-assertions-core:$kotestVersion")
                implementation("io.kotest:kotest-framework-api:$kotestVersion")
                implementation("io.kotest:kotest-framework-engine:$kotestVersion")
            }
        }

        val jvmTest by getting {
            dependencies {
                implementation("io.kotest:kotest-runner-junit5:$kotestVersion")
            }
        }

        val nativeTest by creating {
            dependsOn(commonTest)
        }

        val linuxX64Test by getting {
            dependsOn(nativeTest)
        }

        val macosX64Test by getting {
            dependsOn(nativeTest)
        }

        val mingwX64Test by getting {
            dependsOn(nativeTest)
        }

        all {
            languageSettings {
                progressiveMode = true
                explicitApi = ExplicitApiMode.Strict
            }
        }
    }

    targets.all {
        val target = this

        if (target.platformType == KotlinPlatformType.native) {
            target.compilations.getByName<KotlinNativeCompilation>("main") {
                val libraryPath = dockerClientWrapperProject.buildDir
                    .resolve("libs")
                    .resolve(konanTarget.golangOSName)
                    .resolve(konanTarget.architecture.name.toLowerCase())
                    .resolve("archive")

                cinterops.register("dockerClientWrapper") {
                    includeDirs(dockerClientWrapperProject.projectDir.resolve("src"), libraryPath)
                    extraOpts("-libraryPath", libraryPath)
                }

                tasks.named("cinteropDockerClientWrapper${target.name.capitalize()}") {
                    dependsOn(dockerClientWrapperProject.tasks.named("buildArchiveLib${konanTarget.golangOSName.capitalize()}${konanTarget.architecture.name.capitalize()}"))

                    if (konanTarget.family == Family.LINUX) {
                        onlyIf { buildIsRunningOnLinux }
                    }
                }
            }
        }
    }
}

setOf(
    "compileKotlinLinuxX64",
    "compileTestKotlinLinuxX64",
    "linuxX64MainKlibrary",
    "linuxX64TestKlibrary",
    "linkDebugTestLinuxX64",
).forEach { taskName ->
    tasks.named(taskName) {
        onlyIf { buildIsRunningOnLinux }
    }
}

val KonanTarget.golangOSName: String
    get() = when (family) {
        Family.OSX -> "darwin"
        Family.LINUX -> "linux"
        Family.MINGW -> "windows"
        else -> throw UnsupportedOperationException("Unknown target family: $family")
    }

tasks.named<Test>("jvmTest") {
    useJUnitPlatform()

    testLogging {
        showExceptions = true
        showStandardStreams = true
        events = setOf(TestLogEvent.FAILED, TestLogEvent.SKIPPED)
        exceptionFormat = TestExceptionFormat.FULL
    }
}

val copyJvmLibs = tasks.register<Copy>("copyJvmLibs") {
    val prefix = "buildSharedLib"
    val taskNames = dockerClientWrapperProject.tasks.names.filter { it.startsWith(prefix) && it != "buildSharedLibs" }

    taskNames.forEach { taskName ->
        val task = dockerClientWrapperProject.tasks.getByName<GolangBuild>(taskName)

        from(task.outputLibraryFile) {
            into("batect/dockerclient/libs/${task.targetOperatingSystem.get().name}/${task.targetArchitecture.get().jnrName}".toLowerCase())
        }
    }

    into(jvmLibsDir)

    duplicatesStrategy = DuplicatesStrategy.FAIL
}

tasks.named("jvmProcessResources") {
    dependsOn(copyJvmLibs)
}
