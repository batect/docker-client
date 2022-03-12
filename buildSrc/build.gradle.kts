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

import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.gradle.api.tasks.testing.logging.TestLogEvent
import java.nio.file.Files

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.plugin.serialization)
    alias(libs.plugins.spotless)

    `java-gradle-plugin`
}

repositories {
    mavenCentral()

    // Only required for snapshot Kotest versions - remove this once we're using a stable version again.
    maven("https://oss.sonatype.org/content/repositories/snapshots")
}

dependencies {
    implementation(platform("org.jetbrains.kotlin:kotlin-bom"))
    implementation(libs.kotlinx.serialization.core)
    implementation(libs.kotlinx.serialization.json)
    implementation(gradleApi())
    implementation(gradleKotlinDsl())
    implementation(libs.jnr.posix)
    implementation(libs.kaml)
    implementation(libs.spotless)
    implementation(libs.gradle.download.plugin)
    implementation(libs.okio)
    implementation(libs.commons.compress)
    implementation(libs.xz)

    testImplementation(gradleTestKit())
    testImplementation(libs.kotest.assertions.core)
    testImplementation(libs.kotest.framework.api)
    testImplementation(libs.kotest.framework.engine)
    testImplementation(libs.kotest.runner.junit5)
}

java {
    targetCompatibility = JavaVersion.VERSION_1_8
}

tasks.withType<Test>() {
    useJUnitPlatform()
}

tasks.withType<AbstractTestTask>().configureEach {
    testLogging {
        showExceptions = true
        showStandardStreams = true
        events = setOf(TestLogEvent.FAILED, TestLogEvent.SKIPPED, TestLogEvent.STANDARD_ERROR, TestLogEvent.STANDARD_OUT)
        exceptionFormat = TestExceptionFormat.FULL
    }
}

val licenseText = Files.readString(project.projectDir.resolve("..").resolve("gradle").resolve("license.txt").toPath())!!
val kotlinLicenseHeader = "/*\n${licenseText.lines().joinToString("\n") { "    $it".trimEnd() }}*/\n\n"

spotless {
    encoding("UTF-8")

    kotlinGradle {
        ktlint(libs.versions.ktlint.get())
        licenseHeader(kotlinLicenseHeader, "plugins|rootProject|import|dependencyResolutionManagement")
    }

    kotlin {
        target(fileTree("src").include("**/*.kt"))

        ktlint(libs.versions.ktlint.get())
        licenseHeader(kotlinLicenseHeader)
    }
}

tasks.named("spotlessKotlinCheck") {
    mustRunAfter("test")
}

gradlePlugin {
    plugins {
        create("docker-client-formatting-convention") {
            id = "batect.dockerclient.buildtools.formatting"
            implementationClass = "batect.dockerclient.buildtools.formatting.FormattingConventionPlugin"
        }

        create("docker-client-golang") {
            id = "batect.dockerclient.buildtools.golang"
            implementationClass = "batect.dockerclient.buildtools.golang.GolangPlugin"
        }

        create("docker-client-zig") {
            id = "batect.dockerclient.buildtools.zig"
            implementationClass = "batect.dockerclient.buildtools.zig.ZigPlugin"
        }

        create("docker-client-golang-crosscompilation") {
            id = "batect.dockerclient.buildtools.golang.crosscompilation"
            implementationClass = "batect.dockerclient.buildtools.golang.crosscompilation.GolangCrossCompilationPlugin"
        }
    }
}
