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

import batect.dockerclient.buildtools.Architecture
import batect.dockerclient.buildtools.BinaryType
import batect.dockerclient.buildtools.OperatingSystem
import batect.dockerclient.buildtools.codegen.GenerateGolangTypes
import batect.dockerclient.buildtools.golang.crosscompilation.GolangBuild
import java.nio.file.Files

plugins {
    id("batect.dockerclient.buildtools.formatting")
    id("batect.dockerclient.buildtools.golang.crosscompilation")
    id("batect.dockerclient.buildtools.licensecheck")
}

repositories {
    mavenCentral()
}

val baseName = "dockerclientwrapper"

data class Target(
    val operatingSystem: OperatingSystem,
    val architecture: Architecture
)

val targets = setOf(
    Target(OperatingSystem.Darwin, Architecture.X64),
    Target(OperatingSystem.Darwin, Architecture.Arm64),
    Target(OperatingSystem.Linux, Architecture.X64),
    Target(OperatingSystem.Linux, Architecture.Arm64),
    Target(OperatingSystem.Windows, Architecture.X64)
)

val srcDir = projectDir.resolve("src")

val generateTypes = tasks.register<GenerateGolangTypes>("generateTypes")

val buildSharedLibs = tasks.register("buildSharedLibs") {
    group = "build"
}

val buildArchiveLibs = tasks.register("buildArchiveLibs") {
    group = "build"
}

targets.forEach { target ->
    val buildSharedLib = tasks.register<GolangBuild>("buildSharedLib${target.operatingSystem.name}${target.architecture.name}") {
        targetArchitecture.set(target.architecture)
        targetOperatingSystem.set(target.operatingSystem)
        targetBinaryType.set(BinaryType.Shared)
    }

    buildSharedLibs.configure { dependsOn(buildSharedLib) }

    val buildArchiveLib = tasks.register<GolangBuild>("buildArchiveLib${target.operatingSystem.name.capitalize()}${target.architecture.name.capitalize()}") {
        targetArchitecture.set(target.architecture)
        targetOperatingSystem.set(target.operatingSystem)
        targetBinaryType.set(BinaryType.Archive)
    }

    buildArchiveLibs.configure { dependsOn(buildArchiveLib) }
}

tasks.withType<GolangBuild>().configureEach {
    libraryName.set(baseName)

    dependsOn(generateTypes)
}

val assemble = tasks.named("assemble") {
    dependsOn(buildSharedLibs)
    dependsOn(buildArchiveLibs)
}

val licenseText = Files.readString(rootProject.projectDir.resolve("gradle").resolve("license.txt").toPath())!!

spotless {
    format("golang") {
        target("**/*.go")

        val golangLicenseHeader = "${licenseText.trimEnd().lines().joinToString("\n") { "// $it".trimEnd() }}\n\n"

        licenseHeader(golangLicenseHeader, "^(package|// AUTOGENERATED|//go:build)")
    }

    format("c") {
        target("**/*.c", "**/*.h")

        val cLicenseHeader = "${licenseText.trimEnd().lines().joinToString("\n") { "// $it".trimEnd() }}\n\n"

        licenseHeader(cLicenseHeader, "^(#include|// AUTOGENERATED)")
    }
}

golang {
    golangVersion.set("1.19")
    golangCILintVersion.set("1.49.0")
    zigVersion.set("0.9.1")
}

val lint = tasks.named("lint") {
    dependsOn(generateTypes)

    mustRunAfter(buildSharedLibs)
    mustRunAfter(buildArchiveLibs)
    mustRunAfter(tasks.withType<GolangBuild>())
}

tasks.named("check") {
    dependsOn(assemble)
    dependsOn(lint)
}

tasks.named("spotlessC") {
    dependsOn(generateTypes)

    mustRunAfter(lint)
}

tasks.named("spotlessGolang") {
    dependsOn(generateTypes)

    mustRunAfter(lint)
}

tasks.named("spotlessKotlinGradle") {
    mustRunAfter(lint)
}

val cleanGolangCache = tasks.named("cleanGolangCache")

tasks.named("clean") {
    dependsOn(cleanGolangCache)
}
