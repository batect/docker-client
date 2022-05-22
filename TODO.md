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
  * Enforce this somehow?
* Go build: use Zig for compilation
  * Set ZIG_LOCAL_CACHE_DIR and ZIG_GLOBAL_CACHE_DIR to the same thing for all builds once https://github.com/ziglang/zig/issues/9711 is resolved
* Run tests with memory leak detector - eg. valgrind
* Linter to catch when memory is not freed (eg. Golang code allocates struct that is used as callback parameter)
* Re-enable Golang linter on CI once it supports Go 1.18: https://github.com/golangci/golangci-lint/issues/2649
* Remove "be able to" prefixes from test descriptions
* Remove `IODispatcher` / `Dispatchers.kt` once https://github.com/Kotlin/kotlinx.coroutines/issues/3205 is resolved
* Merge Golang Gradle plugins, distinction doesn't make sense any more
* Refactor / rework code generation - it's a mess and needs tests
  * Use https://github.com/square/kotlinpoet/ to generate Kotlin code rather than current string concatenation approach?
* Remove use of panics in Golang code

# APIs

* Client configuration
  * Add tests to verify that client configuration is actually applied - see TODO in DockerClientBuilderSpec
  * Throw exceptions early (eg. if files provided don't exist)
  * Support for Docker CLI config contexts
    * Rework configuration API: allow consumer to choose between:
      * use default CLI context (which falls back to environment variables, then hard-coded defaults)
      * use specific CLI context
      * provide all configuration
* Timeouts and cancellation for calls
  * Add tests to verify this is working as expected
  * Go through all methods and check for any use of context.Background()

* Check that Golang code can return an empty list (eg. listing all volumes returns no volumes)

* Remove `with...` prefixes from ImageBuildSpec / ContainerCreationSpec?
* Add `ifFailed` helper method to JVM `RealDockerClient`

* Images
  * Build
    * BuildKit
      * Deal with steps formatted like `[ 1/12] FROM docker.io/...` (notice leading space before '1') when sorting steps
        * Is this sorting still required?
      * Support for SSH passthrough - fail if attempted with legacy builder
      * Support for secrets - fail if attempted with legacy builder
      * Add support for warnings (added in BuildKit 0.10.0)
      * Upgrade to most recent version of BuildKit library (currently blocked due to version hell)
    * Test that we can run a built image
* Containers
  * Create
    * Name
    * Network
      * Network aliases
    * Health check configuration
    * Privileged
    * Capabilities to add
    * Capabilities to drop
    * STDIN attached
    * Log driver
    * Log configuration
    * Labels
    * Scenarios to test:
      * Windows containers
      * Default command
      * Default entrypoint
  * Upload files
  * Attach
    * Stream input to stdin - from console or from buffer
    * Reuse output stream
    * Reuse input stream
    * Forward signals to container (test by sending Ctrl-C to self)
    * Set and update TTY size
    * Handle case where only one stream is provided (eg. only stdout, no stderr)
    * Test with and without TTY enabled
      * With TTY enabled: all output goes to stdout stream
      * Without TTY enabled: output split across streams according to source
    * Handle case where container hasn't been started or has already finished
  * Stream events (for waiting for health check)
  * Inspect (for getting last health check result)
* Exec
  * Create
  * Run
