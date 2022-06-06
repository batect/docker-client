# Project setup

* Demo / sample project
  * Add snippets to readme
* License checking
  * Gradle dependencies
  * Golang dependencies
* Test more environments:
  * Podman?
  * Colima?
* Bump minimum Docker client version to something more recent?
* Migrate integration tests from main Batect codebase
* Remove "work in progress" tag from repo description
* Contributor guide
* KDoc comments for public API
  * Enforce this with Detekt (https://detekt.dev/docs/rules/comments#undocumentedpublicclass, https://detekt.dev/docs/rules/comments#undocumentedpublicfunction, https://detekt.dev/docs/rules/comments#undocumentedpublicproperty)
* Go build: use Zig for compilation
  * Set ZIG_LOCAL_CACHE_DIR and ZIG_GLOBAL_CACHE_DIR to the same thing for all builds once https://github.com/ziglang/zig/issues/9711 is resolved
* Run tests with memory leak detector - eg. valgrind
* Linter to catch when memory is not freed (eg. Golang code allocates struct that is used as callback parameter)
* Switch Golang linter back to using Go 1.18 syntax in `.golangci.yml` once it supports Go 1.18: https://github.com/golangci/golangci-lint/issues/2649
* Remove "be able to" prefixes from test descriptions
* Remove `IODispatcher` / `Dispatchers.kt` once https://github.com/Kotlin/kotlinx.coroutines/issues/3205 is resolved
* Refactor / rework code generation - it's a mess and needs tests
  * Use https://github.com/square/kotlinpoet/ to generate Kotlin code rather than current string concatenation approach?
* Remove use of panics in Golang code
* Restore AssertionMode to Error once https://github.com/kotest/kotest/issues/3022 is resolved

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

* Remove `with...` prefixes from ImageBuildSpec / ContainerCreationSpec?
* Add `ifFailed` helper method to JVM `RealDockerClient`

* Images
  * Build
    * BuildKit
      * Support for SSH passthrough - fail if attempted with legacy builder
      * Support for secrets - fail if attempted with legacy builder
      * Add support for warnings (added in BuildKit 0.10.0)
      * Upgrade to most recent version of BuildKit library (currently blocked due to version hell)
* Containers
  * Create
    * STDIN attached
  * Upload files
  * Attach
    * Stream input to stdin - from console or from buffer
      * Test on macOS / Linux
      * Test on Windows
    * Reuse output stream
    * Stream input to stdin - from console or from buffer
    * Reuse input stream
    * Forward signals to container (test by sending Ctrl-C to self)
    * Set and update TTY size
    * Handle case where container hasn't been started or has already finished
  * Stream events (for waiting for health check)
* Exec
  * Create
  * Run
