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
import batect.dockerclient.buildtools.codegen.GenerateGolangTypes
import batect.dockerclient.buildtools.codegen.GenerateKotlinJVMMethods
import batect.dockerclient.buildtools.codegen.GenerateKotlinJVMTypes
import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.gradle.api.tasks.testing.logging.TestLogEvent
import org.jetbrains.kotlin.gradle.dsl.ExplicitApiMode
import org.jetbrains.kotlin.gradle.plugin.KotlinPlatformType
import org.jetbrains.kotlin.gradle.plugin.KotlinTarget
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeCompilation
import org.jetbrains.kotlin.gradle.targets.native.tasks.KotlinNativeHostTest
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

val dockerClientWrapperProject = project(":docker-client-wrapper")
val jvmLibsDir = buildDir.resolve("resources").resolve("jvm")

val buildIsRunningOnLinux = org.gradle.internal.os.OperatingSystem.current().isLinux

kotlin {
    jvm()
    linuxX64()
    macosX64()
    macosArm64()
    mingwX64()

    // This is currently not supported by kotest:
    //  linuxArm64()

    sourceSets {
        val commonMain by getting

        val nativeMain by creating {
            dependsOn(commonMain)
        }

        val jvmMain by getting {
            dependencies {
                implementation("com.github.jnr:jnr-ffi:2.2.6")
            }

            resources.srcDir(jvmLibsDir)
        }

        val commonTest by getting {
            dependencies {
                implementation("io.kotest:kotest-assertions-core:5.0.0.460-SNAPSHOT")
                implementation("io.kotest:kotest-framework-api:5.0.0.460-SNAPSHOT")
                implementation("io.kotest:kotest-framework-engine:5.0.0.460-SNAPSHOT")
            }
        }

        val jvmTest by getting {
            dependencies {
                implementation("io.kotest:kotest-runner-junit5:5.0.0.460-SNAPSHOT")
            }
        }

        val nativeTest by creating {
            dependsOn(commonTest)
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
            addNativeCommonSourceSetDependencies()

            target.compilations.getByName<KotlinNativeCompilation>("main") {
                addDockerClientWrapperCinterop()
            }
        }
    }
}

fun KotlinNativeCompilation.addDockerClientWrapperCinterop() {
    val generationTask = dockerClientWrapperProject.tasks.named<GenerateGolangTypes>("generateTypes")
    val sourceTask = dockerClientWrapperProject.tasks.named<GolangBuild>("buildArchiveLib${konanTarget.golangOSName.capitalize()}${konanTarget.architecture.name.toLowerCase().capitalize()}")

    cinterops.register("dockerClientWrapper") {
        extraOpts("-libraryPath", sourceTask.get().outputDirectory.get())

        includeDirs(generationTask.map { it.headerFile.get().asFile.parentFile })

        headers(sourceTask.map { it.outputHeaderFile })
        headers(generationTask.map { it.headerFile })
    }

    tasks.named("cinteropDockerClientWrapper${target.name.capitalize()}") {
        dependsOn(sourceTask)

        inputs.file(sourceTask.map { it.outputLibraryFile })

        if (konanTarget.family == Family.LINUX) {
            onlyIf { buildIsRunningOnLinux }
        }
    }
}

fun KotlinTarget.addNativeCommonSourceSetDependencies() {
    val nativeMain = kotlin.sourceSets.getByName("nativeMain")
    val nativeTest = kotlin.sourceSets.getByName("nativeTest")

    kotlin.sourceSets.getByName("${this.name}Main") {
        dependsOn(nativeMain)
    }

    kotlin.sourceSets.getByName("${this.name}Test") {
        dependsOn(nativeTest)
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

val testEnvironmentVariables = setOf(
    "DISABLE_DOCKER_DAEMON_TESTS",
    "DOCKER_CONTAINER_OPERATING_SYSTEM"
)

tasks.named<Test>("jvmTest") {
    useJUnitPlatform()

    testEnvironmentVariables.forEach { name ->
        val value = System.getenv(name) ?: ""
        inputs.property(name, value)
        environment(name, value)
    }
}

tasks.withType<KotlinNativeHostTest>().configureEach {
    testEnvironmentVariables.forEach { name ->
        val value = System.getenv(name) ?: ""
        inputs.property(name, value)
        environment(name, value)
    }
}

tasks.withType<AbstractTestTask>().configureEach {
    testLogging {
        showExceptions = true
        showStandardStreams = true
        events = setOf(TestLogEvent.FAILED, TestLogEvent.SKIPPED)
        exceptionFormat = TestExceptionFormat.FULL
    }
}

val buildSharedLibTasks = dockerClientWrapperProject.tasks.names.filter { it.startsWith("buildSharedLib") && it != "buildSharedLibs" }

val copyJvmLibs = tasks.register<Copy>("copyJvmLibs") {
    buildSharedLibTasks.forEach { taskName ->
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

val generateJvmTypes = tasks.register<GenerateKotlinJVMTypes>("generateJvmTypes") {
    kotlinFile.set(kotlin.sourceSets.getByName("jvmMain").kotlin.sourceDirectories.singleFile.resolve("batect/dockerclient/native/Types.kt"))
}

val generateJvmMethods = tasks.register<GenerateKotlinJVMMethods>("generateJvmMethods") {
    buildSharedLibTasks.forEach { taskName ->
        val task = dockerClientWrapperProject.tasks.getByName<GolangBuild>(taskName)

        sourceHeaderFiles.from(task.outputHeaderFile)
    }

    kotlinFile.set(kotlin.sourceSets.getByName("jvmMain").kotlin.sourceDirectories.singleFile.resolve("batect/dockerclient/native/API.kt"))
}

val generateJvm = tasks.register("generateJvm") {
    dependsOn(generateJvmTypes)
    dependsOn(generateJvmMethods)
}

tasks.named("compileKotlinJvm") {
    dependsOn(generateJvm)
}

afterEvaluate {
    tasks.named("spotlessKotlin") {
        dependsOn(generateJvm)
    }
}
