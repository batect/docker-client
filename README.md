# docker-client

[![CI](https://github.com/batect/docker-client/actions/workflows/ci.yml/badge.svg)](https://github.com/batect/docker-client/actions/workflows/ci.yml)
[![License](https://img.shields.io/github/license/batect/batect.svg)](https://opensource.org/licenses/Apache-2.0)

A Docker client library for Kotlin/JVM and Kotlin/Native.

## Status

:construction: This project is still experimental, with limited support for the Docker API.
The initial focus is on providing the APIs used by [Batect](https://batect.dev).

If you require an API not provided here, please [open a new issue](https://github.com/batect/docker-client/issues).

## Requirements

Supported environments for both JVM and Native:
* macOS: x64 and ARM64 (aka Apple Silicon)
* Linux: x64 and ARM64
* Windows: x64

## How is this different to other Docker client libraries?

(or: why have you created _another_ client library?)

There are two major differences compared to other client libraries:

* This library supports both Kotlin/JVM and Kotlin/Native, whereas most other existing libraries only support the JVM.

* This library embeds the official Golang Docker client libraries, rather than invoking the `docker` executable or
  calling the Docker API itself.

  This makes it much easier to add support for new Docker features, or use features that require a lot of client logic
  (eg. BuildKit) without sacrificing performance.
