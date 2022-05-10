# Project setup

* Demo / sample project
* License checking
  * Gradle dependencies
  * Golang dependencies
* Readme
  * Add examples
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
  * Share Golang and Zig build cache between tasks in same CI build run?
  * Set ZIG_LOCAL_CACHE_DIR and ZIG_GLOBAL_CACHE_DIR to the same thing for all builds once https://github.com/ziglang/zig/issues/9711 is resolved
* Run tests with memory leak detector - eg. valgrind
* Linter to catch when memory is not freed (eg. Golang code allocates struct that is used as callback parameter)
* Sample app
  * Add snippets to readme
* Use https://github.com/square/kotlinpoet/ to generate Kotlin code rather than current string concatenation approach?
* Re-enable Golang linter on CI once it supports Go 1.18: https://github.com/golangci/golangci-lint/issues/2649
* Remove "be able to" prefixes from test descriptions
* Run Mac CI tests with Colima?
* Remove `IODispatcher` / `Dispatchers.kt` once https://github.com/Kotlin/kotlinx.coroutines/issues/3205 is resolved
* Merge Golang Gradle plugins, distinction doesn't make sense any more
* Refactor / rework code generation - it's a mess and needs tests
* Remove use of panics in Golang code

# APIs

* Client configuration
  * Add tests to verify that client configuration is actually applied - see TODO in DockerClientBuilderSpec
  * Throw exceptions early (eg. if files provided don't exist)
  * Support for Docker CLI config contexts
* Timeouts for calls?
  * Add tests to verify this is working as expected
* Cancellation for calls
  * Make all methods coroutines? https://kotlin.github.io/kotlinx.coroutines/kotlinx-coroutines-core/kotlinx.coroutines/suspend-cancellable-coroutine.html
  * Go through all methods and check for any use of context.Background()
* Pass structs to methods by value rather than by reference once jnr-ffi supports it

* Check that Golang code can return an empty list (eg. listing all volumes returns no volumes)
* Autogenerate struct accessors for arrays in structs (eg. BuildImageRequest.BuildArgs)

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
    * Test that we can run a built image
* Containers
  * Create
    * Name
    * Network
    * Network aliases
    * Extra hosts
    * Environment variables
    * Mounts
      * Local directory
      * Volume
      * Docker socket
      * tmpfs
    * Entrypoint
    * Working directory
    * Device mounts
    * Port mappings
      * Listed in `EXPOSE` instruction
      * Not listed in `EXPOSE`
    * Health check configuration
    * User and group
    * Privileged
    * Capabilities to add
    * Capabilities to drop
    * Init process enabled
    * TTY enabled
    * STDIN attached
    * Log driver
    * Log configuration
    * SHM size
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
