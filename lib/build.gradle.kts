import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.gradle.api.tasks.testing.logging.TestLogEvent

plugins {
    kotlin("multiplatform") version "1.5.30"
    id("io.kotest.multiplatform") version "5.0.0.5"
}

repositories {
    mavenCentral()
    maven("https://oss.sonatype.org/content/repositories/snapshots")
}

val kotestVersion = "5.0.0.419-SNAPSHOT"

kotlin {
    jvm()
//    linuxArm64()
    linuxX64()
    macosX64()
//    macosArm64()
    mingwX64()

    sourceSets {
        val commonTest by getting {
            dependencies {
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

        all {
            languageSettings {
                progressiveMode = true
            }
        }
    }
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
