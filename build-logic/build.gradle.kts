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
}

dependencies {
    implementation(platform("org.jetbrains.kotlin:kotlin-bom"))
    implementation(libs.kotlin.native.utils)
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
    implementation(libs.licensee)

    testImplementation(gradleTestKit())
    testImplementation(libs.kotest.assertions.core)
    testImplementation(libs.kotest.framework.api)
    testImplementation(libs.kotest.framework.engine)
    testImplementation(libs.kotest.runner.junit5)
}

tasks.withType<Test> {
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

        create("docker-client-golang-crosscompilation") {
            id = "batect.dockerclient.buildtools.golang.crosscompilation"
            implementationClass = "batect.dockerclient.buildtools.golang.crosscompilation.GolangCrossCompilationPlugin"
        }

        create("docker-client-licensecheck") {
            id = "batect.dockerclient.buildtools.licensecheck"
            implementationClass = "batect.dockerclient.buildtools.licensecheck.LicenseCheckPlugin"
        }
    }
}
