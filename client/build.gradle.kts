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

import batect.dockerclient.buildtools.CheckJarContents
import batect.dockerclient.buildtools.codegen.GenerateGolangTypes
import batect.dockerclient.buildtools.codegen.GenerateKotlinJVMMethods
import batect.dockerclient.buildtools.codegen.GenerateKotlinJVMTypes
import batect.dockerclient.buildtools.golang.crosscompilation.GolangBuild
import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.gradle.api.tasks.testing.logging.TestLogEvent
import org.gradle.internal.os.OperatingSystem
import org.jetbrains.kotlin.gradle.dsl.ExplicitApiMode
import org.jetbrains.kotlin.gradle.plugin.KotlinTarget
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeCompilation
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget
import org.jetbrains.kotlin.gradle.targets.native.tasks.KotlinNativeHostTest
import org.jetbrains.kotlin.konan.target.Family
import org.jetbrains.kotlin.konan.target.KonanTarget

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotest.multiplatform)
    `maven-publish`
    signing

    id("batect.dockerclient.buildtools.formatting")
}

repositories {
    mavenCentral()
}

evaluationDependsOn(":golang-wrapper")

val golangWrapperProject = project(":golang-wrapper")
val jvmLibsDir = buildDir.resolve("resources").resolve("jvm")
val runTargetsForOtherHosts = System.getenv().getOrDefault("RUN_TARGETS_FOR_OTHER_HOSTS", "true") == "true"

kotlin {
    jvm {
        compilations.configureEach {
            kotlinOptions {
                jvmTarget = "1.8"
                freeCompilerArgs += listOf("-Xjvm-default=all")
            }
        }
    }

    linuxX64()
    macosX64()
    macosArm64()
    mingwX64()

    // This is currently not supported by kotest:
    //  linuxArm64()

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(libs.okio)
                implementation(libs.kotlinx.coroutines.core)
                implementation(libs.kotlinx.datetime)
            }
        }

        val nativeMain by creating {
            dependsOn(commonMain)
        }

        val jvmMain by getting {
            dependencies {
                implementation(libs.jnr.ffi)
                implementation(libs.jnr.posix)
            }

            resources.srcDir(jvmLibsDir)
        }

        val linuxMain by creating {
            dependsOn(nativeMain)
        }

        val linuxX64Main by getting {
            dependsOn(linuxMain)
        }

        val macosMain by creating {
            dependsOn(nativeMain)
        }

        val macosX64Main by getting {
            dependsOn(macosMain)
        }

        val macosArm64Main by getting {
            dependsOn(macosMain)
        }

        val mingwMain by creating {
            dependsOn(nativeMain)
        }

        val mingwX64Main by getting {
            dependsOn(mingwMain)
        }

        val commonTest by getting {
            dependencies {
                implementation(libs.kotest.assertions.core)
                implementation(libs.kotest.assertions.json)
                implementation(libs.kotest.framework.api)
                implementation(libs.kotest.framework.engine)
                implementation(libs.ktor.client)
            }
        }

        val jvmTest by getting {
            dependencies {
                implementation(libs.kotest.runner.junit5)
                implementation(libs.ktor.client.cio)
            }
        }

        val nativeTest by creating {
            dependsOn(commonTest)
        }

        val linuxTest by creating {
            dependsOn(nativeTest)

            dependencies {
                implementation(libs.ktor.client.cio)
            }
        }

        val linuxX64Test by getting {
            dependsOn(linuxTest)
        }

        val mingwTest by creating {
            dependsOn(nativeTest)

            dependencies {
                implementation(libs.ktor.client.curl)
            }
        }

        val mingwX64Test by getting {
            dependsOn(mingwTest)
        }

        val macosTest by creating {
            dependsOn(nativeTest)

            dependencies {
                implementation(libs.ktor.client.cio)
            }
        }

        val macosX64Test by getting {
            dependsOn(macosTest)
        }

        val macosArm64Test by getting {
            dependsOn(macosTest)
        }

        all {
            languageSettings {
                progressiveMode = true
                explicitApi = ExplicitApiMode.Strict
            }
        }
    }

    targets.withType<KotlinNativeTarget>() {
        val target = this

        addNativeCommonSourceSetDependencies()

        target.compilations.named<KotlinNativeCompilation>("main") {
            addDockerClientWrapperCinterop()
        }

        setOf(
            "compileKotlin${target.name.capitalize()}",
            "compileTestKotlin${target.name.capitalize()}",
            "${target.name}MainKlibrary",
            "${target.name}TestKlibrary",
        ).forEach { taskName ->
            tasks.named(taskName) {
                onlyIf { target.konanTarget.isSupportedOnThisMachine && (target.konanTarget.isSameOperatingSystemAsHost || runTargetsForOtherHosts) }
            }
        }

        // I'm not sure where the culprit lies, but this task fails on macOS hosts for non-macOS targets.
        // (Linux and Windows can run this just fine for each other's targets.)
        // Given we can't run the linked executable on non-matching hosts, there's little point in doing this anyway.
        tasks.named("linkDebugTest${target.name.capitalize()}") {
            onlyIf { target.konanTarget.isSameOperatingSystemAsHost }
        }
    }
}

fun KotlinNativeCompilation.addDockerClientWrapperCinterop() {
    val generationTask = golangWrapperProject.tasks.named<GenerateGolangTypes>("generateTypes")
    val sourceTask = golangWrapperProject.tasks.named<GolangBuild>("buildArchiveLib${konanTarget.golangOSName.capitalize()}${konanTarget.architecture.name.toLowerCase().capitalize()}")

    cinterops.register("dockerClientWrapper") {
        extraOpts("-libraryPath", sourceTask.get().outputDirectory.get())

        includeDirs(generationTask.map { it.headerFile.get().asFile.parentFile })

        headers(sourceTask.map { it.outputHeaderFile })
        headers(generationTask.map { it.headerFile })
    }

    tasks.named("cinteropDockerClientWrapper${target.name.capitalize()}") {
        dependsOn(sourceTask)

        inputs.file(sourceTask.map { it.outputLibraryFile })

        onlyIf { konanTarget.isSupportedOnThisMachine }
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

val KonanTarget.golangOSName: String
    get() = when (family) {
        Family.OSX -> "darwin"
        Family.LINUX -> "linux"
        Family.MINGW -> "windows"
        else -> throw UnsupportedOperationException("Unknown target family: $family")
    }

val KonanTarget.isSupportedOnThisMachine: Boolean
    get() = when (this.family) {
        Family.OSX -> OperatingSystem.current().isMacOsX
        Family.LINUX -> true
        Family.MINGW -> true
        else -> throw UnsupportedOperationException("Unknown target family: $family")
    }

val KonanTarget.isSameOperatingSystemAsHost: Boolean
    get() = when (this.family) {
        Family.OSX -> OperatingSystem.current().isMacOsX
        Family.LINUX -> OperatingSystem.current().isLinux
        Family.MINGW -> OperatingSystem.current().isWindows
        else -> throw UnsupportedOperationException("Unknown target family: $family")
    }

kotlin.targets.configureEach {
    compilations.configureEach {
        kotlinOptions {
            freeCompilerArgs += listOf("-opt-in=kotlin.RequiresOptIn")
        }
    }
}

// Remove this once the new memory model is the default.
kotlin.targets.withType(KotlinNativeTarget::class.java) {
    binaries.all {
        binaryOptions["memoryModel"] = "experimental"
    }
}

val kotestProperties = setOf(
    "kotest.filter.specs",
    "kotest.filter.tests"
)

val testEnvironmentVariables = setOf(
    "DISABLE_DOCKER_DAEMON_TESTS",
    "DOCKER_CONTAINER_OPERATING_SYSTEM",
    "DOCKER_CONNECTION_OVER_TCP",
) + kotestProperties + kotestProperties.map { it.replace('.', '_') }

tasks.named<Test>("jvmTest") {
    useJUnitPlatform()

    kotestProperties.forEach { name ->
        val value = System.getProperty(name)

        if (value != null) {
            inputs.property(name, value)
            systemProperty(name, value)
        }
    }

    testEnvironmentVariables.forEach { name ->
        val value = System.getenv(name)

        if (value != null) {
            inputs.property(name, value)
            environment(name, value)
        }
    }

    environment("GODEBUG", "cgocheck=2")
    systemProperty("kotest.assertions.collection.print.size", "-1")
}

tasks.withType<KotlinNativeHostTest>().configureEach {
    testEnvironmentVariables.forEach { name ->
        val value = System.getenv(name)

        if (value != null) {
            inputs.property(name, value)
            environment(name, value)
        }
    }

    environment("GODEBUG", "cgocheck=2")
    environment("kotest_assertions_collection_print_size", "-1")
}

tasks.withType<AbstractTestTask>().configureEach {
    testLogging {
        showExceptions = true
        showStandardStreams = true
        events = setOf(TestLogEvent.FAILED, TestLogEvent.SKIPPED, TestLogEvent.STANDARD_ERROR, TestLogEvent.STANDARD_OUT)
        exceptionFormat = TestExceptionFormat.FULL
    }
}

val buildSharedLibTasks = golangWrapperProject.tasks.names.filter { it.startsWith("buildSharedLib") && it != "buildSharedLibs" }

val copyJvmLibs = tasks.register<Copy>("copyJvmLibs") {
    buildSharedLibTasks.forEach { taskName ->
        val task = golangWrapperProject.tasks.getByName<GolangBuild>(taskName)

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

tasks.register<CheckJarContents>("checkJarContents") {
    jarFile.set(tasks.getByName<Jar>("jvmJar").archiveFile)
    expectedFiles.add("batect/dockerclient/libs/darwin/aarch64/libdockerclientwrapper.dylib")
    expectedFiles.add("batect/dockerclient/libs/darwin/x86_64/libdockerclientwrapper.dylib")
    expectedFiles.add("batect/dockerclient/libs/linux/aarch64/libdockerclientwrapper.so")
    expectedFiles.add("batect/dockerclient/libs/linux/x86_64/libdockerclientwrapper.so")
    expectedFiles.add("batect/dockerclient/libs/windows/x86_64/dockerclientwrapper.dll")
}

val generateJvmTypes = tasks.register<GenerateKotlinJVMTypes>("generateJvmTypes") {
    kotlinFile.set(kotlin.sourceSets.getByName("jvmMain").kotlin.sourceDirectories.singleFile.resolve("batect/dockerclient/native/Types.kt"))
}

val generateJvmMethods = tasks.register<GenerateKotlinJVMMethods>("generateJvmMethods") {
    buildSharedLibTasks.forEach { taskName ->
        val task = golangWrapperProject.tasks.getByName<GolangBuild>(taskName)

        sourceHeaderFiles.from(task.outputHeaderFile)
    }

    kotlinFile.set(kotlin.sourceSets.getByName("jvmMain").kotlin.sourceDirectories.singleFile.resolve("batect/dockerclient/native/API.kt"))
}

val generateJvm = tasks.register("generateJvm") {
    dependsOn(generateJvmTypes)
    dependsOn(generateJvmMethods)
}

val dependOnGeneratedCode = setOf(
    "compileKotlinJvm",
    "jvmSourcesJar",
    "sourcesJar",
    "spotlessKotlin"
)

dependOnGeneratedCode.forEach { task -> tasks.named(task) { dependsOn(generateJvm) } }

// Generate dummy Javadoc JAR to make Sonatype happy: see https://github.com/Kotlin/dokka/issues/1753#issuecomment-784173735
val javadocJar by tasks.register<Jar>("javadocJar") {
    archiveClassifier.set("javadoc")
}

publishing {
    publications {
        named<MavenPublication>("jvm") {
            artifact(javadocJar)
        }

        withType<MavenPublication>().configureEach {
            pom {
                name.set("docker-client")
                description.set("A Docker client for Kotlin/JVM and Kotlin/Native")
                url.set("https://github.com/batect/docker-client")

                licenses {
                    license {
                        name.set("The Apache License, Version 2.0")
                        url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
                    }
                }

                developers {
                    developer {
                        id.set("charleskorn")
                        name.set("Charles Korn")
                        email.set("me@charleskorn.com")
                    }
                }

                scm {
                    connection.set("scm:git:git://github.com/batect/docker-client.git")
                    developerConnection.set("scm:git:ssh://github.com:batect/docker-client.git")
                    url.set("https://github.com/batect/docker-client")
                }
            }
        }
    }
}

signing {
    val signingKey: String? by project
    val signingPassword: String? by project

    useInMemoryPgpKeys(signingKey, signingPassword)

    sign(publishing.publications)
}
