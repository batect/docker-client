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
* Go build: use Zig for compilation?
  * Share Golang and Zig build cache between tasks in same CI build run?
  * Set ZIG_LOCAL_CACHE_DIR to the same thing for all builds?
  * Cache value of macOS SDK root (currently run xcrun every time the value is used)
* Run tests with memory leak detector - eg. valgrind
* Linter to catch when memory is not freed (eg. Golang code allocates struct that is used as callback parameter)
* Fix broken publication of library
  * Disabled due to Okio not supporting hierarchical project structure - https://github.com/square/okio/pull/980 needs bug fixes in Kotlin 1.6.20
  * See compatibility table at https://kotlinlang.org/docs/migrating-multiplatform-project-to-14.html#migrate-to-the-hierarchical-project-structure
  * Will likely fail until https://github.com/ziglang/zig/issues/9711 is fixed
    * Also re-enable `:golang-wrapper:buildSharedLibWindowsX64` on linting job once this is fixed
* Sample app
  * Add snippets to readme
* Use https://github.com/square/kotlinpoet/ to generate Kotlin code rather than current string concatenation approach?
* Reduce impact of buildSrc build time on CI builds - currently takes 5 minutes before task even starts
  * Use some kind of build cache? eg. Gradle Enterprise or https://github.com/gradle/gradle-build-action
* Remove "be able to" prefixes from test descriptions

# APIs

* Client configuration
  * Add tests to verify that client configuration is actually applied - see TODO in DockerClientBuilderSpec
  * Throw exceptions early (eg. if files provided don't exist)
  * Support for Docker config contexts
* Timeouts for calls?
* Cancellation for calls
  * Make all methods coroutines? https://kotlin.github.io/kotlinx.coroutines/kotlinx-coroutines-core/kotlinx.coroutines/suspend-cancellable-coroutine.html
* Pass structs to methods by value rather than by reference once jnr-ffi supports it

* Check that Golang code can return an empty list (eg. listing all volumes returns no volumes)
* Autogenerate struct accessors for arrays in structs (eg. BuildImageRequest.BuildArgs)

* Remove `with...` prefixes from ImageBuildSpec / ContainerCreationSpec?
* Add `ifFailed` helper method to JVM `RealDockerClient`

* Images
  * Build
    * Kotlin/Native: Stream output while build is running, not just when build returns (see TODO in native buildImage())
      * Waiting for answer to question about Dispatchers.IO - does not exist on Kotlin/Native
    * BuildKit
      * Fix issue running tests on JVM: blocked by https://github.com/jnr/jnr-ffi/pull/299, re-enable JVM tests on CI once this is resolved
      * Deal with steps formatted like `[ 1/12] FROM docker.io/...` (notice leading space before '1') when sorting steps
        * Is this sorting still required?
      * Support for SSH passthrough
      * Support for secrets
      * Add support for warnings (added in BuildKit 0.10.0)
    * Test that we can run a built image
* Containers
  * Create
    * Name
    * Network
    * Host name
      * If greater than 63 characters, throw exception
    * Network aliases
    * Extra hosts
    * Environment variables
    * Mounts
      * Local directory
      * Volume
      * Docker socket
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
  * Run - helper method that does start / attach / wait / stop / remove
  * Stream events (for waiting for health check)
  * Inspect (for getting last health check result)
* Exec
  * Create
  * Run
