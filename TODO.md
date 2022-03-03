# Project setup

* Demo / sample project
* GitHub Actions pipeline
  * Build all publications in platform jobs and then publish in one job, rather than OS-specific publish jobs?
* License checking
  * Gradle dependencies
  * Golang dependencies
* Integration testing setup?
* Readme
  * Add examples
* Go build: ensure Golang version is as expected (or download and cache binary)
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
  * https://awsteele.com/blog/2021/10/17/cgo-for-arm64-lambda-functions.html
  * https://dev.to/kristoff/zig-makes-go-cross-compilation-just-work-29ho
  * https://github.com/marketplace/actions/setup-zig
  * If implemented: need to add a check that the version of Zig in use matches the expected version (similar to check for Golang version).
  * Works with Zig 0.9.0, but requires https://github.com/golang/go/issues/43078 to remove need for wrapper scripts
  * Remove setup-go action in CI
  * Cache downloaded tools on CI?
  * Cache Golang packages on CI
  * Share Golang and Zig build cache between tasks in same CI build run?
* Run tests with memory leak detector - eg. valgrind
* Linter to catch when memory is not freed (eg. Golang code allocates struct that is used as callback parameter)
* Clean task for Golang wrapper project
* Fix broken publication of library
  * Disabled due to Okio not supporting hierarchical project structure - https://github.com/square/okio/pull/980 needs bug fixes in Kotlin 1.6.20
  * See compatibility table at https://kotlinlang.org/docs/migrating-multiplatform-project-to-14.html#migrate-to-the-hierarchical-project-structure
* Sample app
  * Add snippets to readme
* Remove use of Kotest snapshot version once new release is published with support for Kotlin/Native's new memory model
* Run Golang builds in parallel - use the worker API to enable this: https://docs.gradle.org/current/userguide/custom_tasks.html#worker_api
  * May also want to apply this to the Golang linting task and checksum verification tasks as well
* Configure golangci-lint version in GolangPluginExtension without 'v' prefix

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

* Images
  * Build
    * Kotlin/Native: Stream output while build is running, not just when build returns (see TODO in native buildImage())
      * Waiting for answer to question about Dispatchers.IO - does not exist on Kotlin/Native
    * BuildKit
      * Fix issue running tests on JVM: blocked by https://github.com/jnr/jnr-ffi/pull/299, re-enable JVM tests on CI once this is resolved
      * Support for SSH passthrough
      * Support for secrets
    * Test that we can run a built image
* Containers
  * Create
  * Start
  * Stop
  * Remove
  * Upload files
  * Run
  * Stream events (for waiting for health check)
  * Inspect (for getting last health check result)
* Exec
  * Create
  * Run
