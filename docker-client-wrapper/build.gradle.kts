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

plugins {
    id("com.diffplug.spotless")
}

val libraryName = "dockerclientwrapper"

enum class OperatingSystem {
    Darwin,
    Linux,
    Windows
}

enum class Architecture {
    Amd64,
    Arm64
}

data class Target(val operatingSystem: OperatingSystem, val architecture: Architecture) {
    private val baseName = when (operatingSystem) {
        OperatingSystem.Windows -> libraryName
        OperatingSystem.Linux, OperatingSystem.Darwin -> "lib$libraryName"
    }

    val headerFileName = "$baseName.h"

    val sharedLibraryName = when (operatingSystem) {
        OperatingSystem.Windows -> "$baseName.dll"
        OperatingSystem.Linux -> "$baseName.so"
        OperatingSystem.Darwin -> "$baseName.dylib"
    }

    val archiveLibraryName = when (operatingSystem) {
        OperatingSystem.Windows -> "$baseName.lib"
        OperatingSystem.Linux, OperatingSystem.Darwin -> "$baseName.a"
    }
}

val targets = setOf(
    Target(OperatingSystem.Darwin, Architecture.Amd64),
    Target(OperatingSystem.Linux, Architecture.Amd64),
    Target(OperatingSystem.Windows, Architecture.Amd64),
)

val srcDir = projectDir.resolve("src")
val libsDir = buildDir.resolve("libs")

val buildSharedLibs = tasks.register("buildSharedLibs")
val buildArchiveLibs = tasks.register("buildArchiveLibs")

targets.forEach { target ->
    val targetDir = libsDir.resolve(target.operatingSystem.name.toLowerCase()).resolve(target.architecture.name.toLowerCase())

    val targetEnvironment = mapOf(
        "CGO_ENABLED" to "1",
        "GOOS" to target.operatingSystem.name.toLowerCase(),
        "GOARCH" to target.architecture.name.toLowerCase()
    )

    val buildSharedLib = tasks.register<Exec>("buildSharedLib${target.operatingSystem.name}${target.architecture.name}") {
        val outputDir = targetDir.resolve("shared")
        val outputLibraryFile = outputDir.resolve(target.sharedLibraryName)
        val outputHeaderFile = outputDir.resolve(target.headerFileName)

        inputs.dir(srcDir)
        outputs.file(outputLibraryFile)
        outputs.file(outputHeaderFile)

        workingDir("src")
        commandLine("go", "build", "-buildmode=c-shared", "-o", outputLibraryFile)
        environment(targetEnvironment)
    }

    buildSharedLibs.configure { dependsOn(buildSharedLib) }

    val buildArchiveLib = tasks.register<Exec>("buildArchiveLib${target.operatingSystem.name.capitalize()}${target.architecture.name.capitalize()}") {
        val outputDir = targetDir.resolve("archive")
        val outputLibraryFile = outputDir.resolve(target.archiveLibraryName)
        val outputHeaderFile = outputDir.resolve(target.headerFileName)

        inputs.dir(srcDir)
        outputs.file(outputLibraryFile)
        outputs.file(outputHeaderFile)

        workingDir("src")
        commandLine("go", "build", "-buildmode=c-archive", "-o", outputLibraryFile)
        environment(targetEnvironment)
    }

    buildArchiveLibs.configure { dependsOn(buildArchiveLib) }
}

tasks.named("assemble") {
    dependsOn(buildSharedLibs)
    dependsOn(buildArchiveLibs)
}
