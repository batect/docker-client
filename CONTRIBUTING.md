# Contribution guide

## Environment set up

At a minimum, to build and test the library, you'll need the following installed on your machine:

* a JVM
* Docker (Linux), Docker Desktop (Windows or macOS) or Colima (macOS)

Other tools required to build and test the library (including Golang and Zig) will be downloaded automatically by Gradle.

## Common tasks

Run all linting and tests with `./gradlew check`.

Run a specific set of tests:
* JVM: `./gradlew jvmTest`
* Native macOS (ARM): `./gradlew macosArm64Test`
* Native macOS (Intel): `./gradlew macosX64Test`
* Native Windows: `./gradle mingwX64Test`
* Native Linux: `./gradlew linuxX64Test`

Filter tests by setting the [`kotest_filter_specs` environment variable](https://kotest.io/docs/framework/conditional/conditional-tests-with-gradle.html#kotest-specific-test-filtering).
For example: `kotest_filter_specs='*DockerClientContainerManagementSpec*' ./gradlew macosArm64Test`
