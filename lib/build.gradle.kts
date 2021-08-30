plugins {
    kotlin("multiplatform") version "1.5.30"
}

repositories {
    mavenCentral()
}

kotlin {
    jvm()
    linuxArm64()
    linuxX64()
    macosX64()
    macosArm64()
    mingwX64()
    mingwX86()
}
