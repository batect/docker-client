# docker-client

[![CI](https://github.com/batect/docker-client/actions/workflows/ci.yml/badge.svg)](https://github.com/batect/docker-client/actions/workflows/ci.yml)
[![License](https://img.shields.io/github/license/batect/batect.svg)](https://opensource.org/licenses/Apache-2.0)
[![Maven Central](https://img.shields.io/maven-central/v/dev.batect.docker/client.svg?label=maven%20central)](https://search.maven.org/search?q=g:%22dev.batect.docker%22%20AND%20a:%22client%22)

A Docker client library for Kotlin/JVM and Kotlin/Native.

## Status

:construction: This project is an ongoing work in progress. It is used by [Batect](https://batect.dev) as of v0.80, and should
be stable enough for production use.

However, it only has the APIs required by Batect. Others are likely missing.
If you require an API not provided here, please [open a new issue](https://github.com/batect/docker-client/issues).

## How to use

### Referencing in Gradle

Using the Kotlin Gradle DSL:

```kotlin
dependencies {
  implementation("dev.batect.docker:client:<version number here>") // Get the latest version number from https://github.com/batect/docker-client/releases/latest
}
```

Check the [releases page](https://github.com/batect/docker-client/releases/latest) for the latest release information,
and the [Maven Central page](https://search.maven.org/artifact/dev.batect.docker/client) for examples of how
to reference the library in other build systems.

### Usage samples

Full sample projects demonstrating how to use this library are available in the [`samples`](./samples) directory.

#### Create a client

```kotlin
val client = DockerClient.create()
```

#### Pull an image

```kotlin
val image = client.pullImage("ubuntu:22.04")
```

#### Run a container

```kotlin
val containerSpec = ContainerCreationSpec.Builder(image)
    .withTTY()
    .withStdinAttached()
    .build()

val container = client.createContainer(containerSpec)

try {
    val exitCode = client.run(container, TextOutput.StandardOutput, TextOutput.StandardError, TextInput.StandardInput)

    println("Container exited with code $exitCode.")
} finally {
    client.removeContainer(container, force = true)
}
```

### Documentation

Dokka documentation for the latest version of the library is available at https://batect.github.io/docker-client/.

## Requirements

### Operating system and architecture

This library supports the following:

| Operating system | Architecture    | Kotlin/JVM         | Kotlin/Native      |
| ---------------- | --------------- | ------------------ | ------------------ |
| macOS            | x64 (Intel)     | :white_check_mark: | :white_check_mark: |
| macOS            | ARM64 (Silicon) | :white_check_mark: | :white_check_mark: |
| Linux            | x64             | :white_check_mark: | :white_check_mark: |
| Linux            | ARM64           | :white_check_mark: | :x:                |
| Windows          | x64             | :white_check_mark: | :white_check_mark: |

Support for ARM64 Linux with Kotlin/Native will be added once Okio supports it (see https://github.com/square/okio/issues/1242).

### Docker daemon

This library supports Docker 19.03.10 or later. However, using the most recent version of Docker is highly recommended.

This library _may_ work with earlier versions of Docker, but this is untested and unsupported.

## How is this different to other Docker client libraries?

(or: why have you created _another_ client library?)

There are two major differences compared to other client libraries:

* This library supports both Kotlin/JVM and Kotlin/Native, whereas most other existing libraries only support the JVM.

* This library embeds the official Golang Docker client libraries, rather than invoking the `docker` executable or
  calling the Docker API itself.

  This makes it much easier to add support for new Docker features and easier to provide features that require a lot of client logic
  (eg. BuildKit) without sacrificing performance.
