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

import batect.dockerclient.buildtools.Architecture
import batect.dockerclient.buildtools.BinaryType
import batect.dockerclient.buildtools.GolangBuild
import batect.dockerclient.buildtools.OperatingSystem

plugins {
    id("com.diffplug.spotless")
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
    Target(OperatingSystem.Windows, Architecture.X64),
    Target(OperatingSystem.Windows, Architecture.X86),
)

val srcDir = projectDir.resolve("src")
val libsDir = buildDir.resolve("libs")

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
        libraryName.set(baseName)
    }

    buildSharedLibs.configure { dependsOn(buildSharedLib) }

    val buildArchiveLib = tasks.register<GolangBuild>("buildArchiveLib${target.operatingSystem.name.capitalize()}${target.architecture.name.capitalize()}") {
        targetArchitecture.set(target.architecture)
        targetOperatingSystem.set(target.operatingSystem)
        targetBinaryType.set(BinaryType.Archive)
        libraryName.set(baseName)
    }

    buildArchiveLibs.configure { dependsOn(buildArchiveLib) }
}

tasks.named("assemble") {
    dependsOn(buildSharedLibs)
    dependsOn(buildArchiveLibs)
}
