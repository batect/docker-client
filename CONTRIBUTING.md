# Contribution guide

## Environment set up

At a minimum, to build and test the library, you'll need the following installed on your machine:

* Golang (check [the CI pipeline](.github/workflows/ci.yml) for the current version in use)
* a JVM
* Docker

## Windows-specific notes

You'll need to install MinGW so that Golang's cgo has a compiler it can use. The most recent version at the time of writing (11.2.0) does
not appear to work (the unit test binary fails to run with errors like `Access denied` and `This app can't run on your PC`). Using the same
version that the GitHub Actions runners use (8.1.0) resolved this issue for me. Install it with `choco install mingw --version=8.1.0`.
