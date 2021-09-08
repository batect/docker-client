# docker-client

[![CI](https://github.com/batect/docker-client/actions/workflows/ci.yml/badge.svg)](https://github.com/batect/docker-client/actions/workflows/ci.yml)
[![License](https://img.shields.io/github/license/batect/batect.svg)](https://opensource.org/licenses/Apache-2.0)

A Docker client library for Kotlin/JVM and Kotlin/Native.

## Status

:construction: This project is still experimental, with limited support for the Docker API.
The initial focus is on providing the APIs used by [Batect](https://batect.dev).

If you require an API not provided here, please [open a new issue](https://github.com/batect/docker-client/issues).

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

Support for ARM64 Linux with Kotlin/Native will be added once kotlinx.coroutines supports it (see https://github.com/Kotlin/kotlinx.coroutines/issues/855).

### Docker daemon

This library supports Docker 18.03.1 or later. Using a more recent version of Docker is highly recommended.

## How is this different to other Docker client libraries?

(or: why have you created _another_ client library?)

There are two major differences compared to other client libraries:

* This library supports both Kotlin/JVM and Kotlin/Native, whereas most other existing libraries only support the JVM.

* This library embeds the official Golang Docker client libraries, rather than invoking the `docker` executable or
  calling the Docker API itself.

  This makes it much easier to add support for new Docker features, or use features that require a lot of client logic
  (eg. BuildKit) without sacrificing performance.
