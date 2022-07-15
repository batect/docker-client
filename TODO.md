# Project setup

* Demo / sample project
  * Add snippets to readme
  * Pulling an image
    * Make output from sample apps nicer / clearer
  * Building an image
* License checking for Golang dependencies: use https://github.com/google/go-licenses perhaps?
* Test more environments:
  * Podman?
  * Colima?
* Bump minimum Docker client version to something more recent?
* Migrate integration tests from main Batect codebase
* Remove "work in progress" tag from repo description
* Contributor guide
* KDoc comments for public API
  * Enforce this with Detekt (https://detekt.dev/docs/rules/comments#undocumentedpublicclass, https://detekt.dev/docs/rules/comments#undocumentedpublicfunction, https://detekt.dev/docs/rules/comments#undocumentedpublicproperty)
* Run tests with memory leak detector - eg. valgrind
* Linter to catch when memory is not freed (eg. Golang code allocates struct that is used as callback parameter)
* Remove "be able to" prefixes from test descriptions
* Refactor / rework code generation - it's a mess and needs tests
  * Use https://github.com/square/kotlinpoet/ to generate Kotlin code rather than current string concatenation approach?
* Fix issue linking sample apps and tests for non-Mac Kotlin/Native targets from Mac hosts (eg. running `./gradlew samples:interactive-container:linkReleaseExecutableLinuxX64` on a Mac host)
  * Currently ignored through `isSameOperatingSystemAsHost` checks in `build.gradle.kts`
* Use Gradle's dependency infrastructure to download Golang and Zig compilers?
  * Or something like https://docs.gradle.org/current/userguide/build_services.html perhaps?
  * Or look at toolchains concept?
* Use Gradle's project dependencies to refer to the Golang wrapper in the client project, rather than the current hack referencing Gradle tasks directly
  * https://docs.gradle.org/current/userguide/declaring_dependencies_between_subprojects.html#sec:depending_on_output_of_another_project

# Issues blocked by upstream dependencies

* Go build: use Zig for compilation
  * Set ZIG_LOCAL_CACHE_DIR and ZIG_GLOBAL_CACHE_DIR to the same thing for all builds once https://github.com/ziglang/zig/issues/9711 is resolved
* Switch Golang linter back to using Go 1.18 syntax in `.golangci.yml` once it supports Go 1.18: https://github.com/golangci/golangci-lint/issues/2649
* Remove `IODispatcher` / `Dispatchers.kt` once https://github.com/Kotlin/kotlinx.coroutines/issues/3205 is resolved
* Restore AssertionMode to Error once https://github.com/kotest/kotest/issues/3022 is resolved
* Move `nonMingwTest` tests back into `commonTest` once https://youtrack.jetbrains.com/issue/KTOR-4307 is resolved or there's an alternative Ktor engine available for Kotlin/Native on Windows.
* Replace use of `shouldBe` in upload tests in `DockerClientContainerManagementSpec` with `shouldMatchJson` once https://github.com/kotest/kotest/pull/3021 is available

# APIs

* Client configuration
  * Add tests to verify that client configuration is actually applied - see TODO in DockerClientBuilderSpec
  * Throw exceptions early (eg. if files provided don't exist)
  * Support for Docker CLI config contexts
    * Rework configuration API:
      * add method to get CLI context by name
      * allow consumer to either: pass returned context settings, modify and pass context settings, provide their own settings or use defaults

* Check that Golang code can return an empty list (eg. listing all volumes returns no volumes)

* Allow calling `ReadyNotification.waitForReady()` from multiple places (will currently only release one caller, not all)
* Remove `with...` prefixes from ImageBuildSpec, ContainerCreationSpec and ContainerExecSpec?

* Images
  * Build
    * BuildKit
      * Support for SSH passthrough - fail if attempted with legacy builder
      * Support for secrets - fail if attempted with legacy builder
      * Add support for warnings (added in BuildKit 0.10.0)
      * Upgrade to most recent version of BuildKit library (currently blocked due to version hell)
* Exec
  * Create
    * Test scenario: container hasn't been started yet
    * Test scenario: container has stopped
    * Test scenario: invalid command
    * Test scenario: no command (is this even valid?)
  * Features:
    * UID / GID
    * Privileged
    * TTY
      * Monitor for terminal size changes
    * Attach stdin + stream input
    * Attach stdout / stderr + stream output
    * Environment variables
      * Test scenario: not set on container
      * Test scenario: override value set on container
    * Working directory
    * Command
