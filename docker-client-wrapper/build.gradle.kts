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

val srcDir = projectDir.resolve("src")
val libsDir = buildDir.resolve("libs")
val libraryName = "dockerclientwrapper"

val buildSharedLib = tasks.register<Exec>("buildSharedLib") {
    val outputDir = libsDir.resolve("darwin").resolve("amd64").resolve("shared")
    val outputLibraryFile = outputDir.resolve("lib$libraryName.dylib")
    val outputHeaderFile = outputDir.resolve("lib$libraryName.h")

    inputs.dir(srcDir)
    outputs.file(outputLibraryFile)
    outputs.file(outputHeaderFile)

    workingDir("src")
    commandLine("go", "build", "-buildmode=c-shared", "-o", outputLibraryFile)
    environment("CGO_ENABLED", "1")
}

val buildArchiveLib = tasks.register<Exec>("buildArchiveLib") {
    val outputDir = libsDir.resolve("darwin").resolve("amd64").resolve("archive")
    val outputLibraryFile = outputDir.resolve("lib$libraryName.a")
    val outputHeaderFile = outputDir.resolve("lib$libraryName.h")

    inputs.dir(srcDir)
    outputs.file(outputLibraryFile)
    outputs.file(outputHeaderFile)

    workingDir("src")
    commandLine("go", "build", "-buildmode=c-shared", "-o", outputLibraryFile)
    environment("CGO_ENABLED", "1")
}

tasks.named("assemble") {
    dependsOn(buildSharedLib)
    dependsOn(buildArchiveLib)
}
