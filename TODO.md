# Project setup

* Demo / sample project
  * Pulling an image
    * Make output from sample apps nicer / clearer
  * Building an image
    * Add to readme
* License checking for Golang dependencies: use https://github.com/google/go-licenses perhaps?
* Test more environments:
  * Podman?
  * Colima?
* Remove "work in progress" tag from repo description
* Contributor guide
* KDoc comments for public API
  * Enforce this with Detekt (https://detekt.dev/docs/rules/comments#undocumentedpublicfunction, https://detekt.dev/docs/rules/comments#undocumentedpublicproperty)
* Run tests with memory leak detector - eg. valgrind
* Linter to catch when memory is not freed (eg. Golang code allocates struct that is used as callback parameter)
* Remove "be able to" prefixes from test descriptions?
* Refactor / rework code generation - it's a mess and needs tests
  * Use https://github.com/square/kotlinpoet/ to generate Kotlin code rather than current string concatenation approach?
* Fix issue linking sample apps and tests for non-Mac Kotlin/Native targets from Mac hosts (eg. running `./gradlew samples:interactive-container:linkReleaseExecutableLinuxX64` on a Mac host)
  * Currently ignored through `isSameOperatingSystemAsHost` checks in `build.gradle.kts`
* Use Gradle's project dependencies to refer to the Golang wrapper in the client project, rather than the current hack referencing Gradle tasks directly
  * https://docs.gradle.org/current/userguide/declaring_dependencies_between_subprojects.html#sec:depending_on_output_of_another_project

# Issues blocked by upstream dependencies

* Go build: use Zig for compilation
  * Set ZIG_LOCAL_CACHE_DIR and ZIG_GLOBAL_CACHE_DIR to the same thing for all builds once https://github.com/ziglang/zig/issues/9711 is resolved
* Switch Golang linter back to using Go 1.18 syntax in `.golangci.yml` once it supports Go 1.18: https://github.com/golangci/golangci-lint/issues/2649
* Remove `IODispatcher` / `Dispatchers.kt` once https://github.com/Kotlin/kotlinx.coroutines/issues/3205 is resolved
* Restore AssertionMode to Error once https://github.com/kotest/kotest/issues/3022 is resolved
* Move `nonMingwTest` tests back into `commonTest` once https://youtrack.jetbrains.com/issue/KTOR-4307 is resolved or there's an alternative Ktor engine available for Kotlin/Native on Windows.
* Remove unnecessary extra constructor for DockerClientException in `nativeMain` source set once https://youtrack.jetbrains.com/issue/KT-52193/Native-Unable-to-call-primary-constructor-with-default-values-in-an-actual-class-without-passing-the-values-in-nativeMain-source is fixed
* Re-enable generating HTML and XML test reports in `build.gradle.kts`, `gradle.properties` and `ci.yml` once https://github.com/gradle/gradle/issues/21547 is fixed

# APIs

* Images
  * Build
    * BuildKit
      * Support for SSH passthrough - fail if attempted with legacy builder
      * Support for cache-from and cache-to - fail if attempted with legacy builder
      * Add support for warnings (added in BuildKit 0.10.0)
      * Upgrade to most recent version of BuildKit library (currently blocked due to version hell)
