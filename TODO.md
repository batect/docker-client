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
* Go build: ensure Golang version is as expected
* golangci-lint task: don't use Docker, just download and cache binary for current OS (allows us to reuse local Golang build cache)
  * Or at least connect it to same cache volumes used by Golang build if using Docker for Golang builds
* Use Gradle version catalogs to manage versions
* Test more environments:
  * Podman
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
  * With Zig `master` as of 2021-10-25, things work but require removing debug information from Golang binaries (`-ldflags` option to `go build` in `GolangBuild`). May be worth waiting for the next release of Zig after 0.8.1 (latest as at 2021-10-25).
  * If implemented: need to add a check that the version of Zig in use matches the expected version (similar to check for Golang version).
* Run tests with memory leak detector
* Linter to catch when memory is not freed (eg. Golang code allocates struct that is used as callback parameter)
* Remove use of `allprojects` in root `build.gradle.kts` - use a custom plugin applied to all projects instead
  * Then should be able to remove use of `afterEvaluate` in `golang-wrapper/build.gradle.kts`

# APIs

* Client configuration
  * Add tests to verify that client configuration is actually applied - see TODO in DockerClientBuilderSpec
  * Throw exceptions early (eg. if files provided don't exist)
* Timeouts for calls?
* Cancellation for calls
  * Make all methods coroutines? https://kotlin.github.io/kotlinx.coroutines/kotlinx-coroutines-core/kotlinx.coroutines/suspend-cancellable-coroutine.html
* Pass structs to methods by value rather than by reference once jnr-ffi supports it

* Check that Golang code can return an empty list (eg. listing all volumes returns no volumes)
* Autogenerate struct accessors for arrays in structs (eg. BuildImageRequest.BuildArgs)

* Images
  * Build
    * Stream output while build is running (see TODO in native buildImage())
    * Legacy builder
    * BuildKit
    * Scenarios to cover:
      * Dockerfile in non-standard location / with non-standard name
      * Base image requires authentication
      * Multi-stage build
      * Multi-stage build with particular target stage
      * Dockerfile outside context directory
    * Features to cover:
      * Progress reporting
        * Image pull
        * Context upload (legacy builder only)
      * Build output
      * Build args
      * Force pull base image
      * Image tags
      * Windows image build
    * Test that we can run a built image
* Containers
  * Create
  * Stop
  * Remove
  * Upload files
  * Run
  * Stream events (for waiting for health check)
  * Inspect (for getting last health check result)
* Exec
  * Run
