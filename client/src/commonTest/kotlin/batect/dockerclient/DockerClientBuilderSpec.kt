/*
    Copyright 2017-2021 Charles Korn.

    Licensed under the Apache License, Version 2.0 (the "License");
    you may not use this file except in compliance with the License.
    You may obtain a copy of the License at

        http://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and
    limitations under the License.
*/

package batect.dockerclient

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldEndWith
import io.kotest.matchers.string.shouldStartWith
import okio.ExperimentalFileSystem
import okio.FileSystem
import okio.Path.Companion.toPath
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalFileSystem::class)
@ExperimentalTime
class DockerClientBuilderSpec : ShouldSpec({
    val rootTestCertificatesDirectory = FileSystem.SYSTEM.canonicalize("./src/commonTest/resources/dummyClientCertificates".toPath())

    var configurationProvidedToClient: DockerClientConfiguration? = null

    val factory: DockerClientFactory = { config ->
        configurationProvidedToClient = config
        RealDockerClient(config)
    }

    beforeEach { configurationProvidedToClient = null }

    should("be able to create a client with the default configuration, implicitly taking values from the environment") {
        DockerClientBuilder(factory).build()

        configurationProvidedToClient shouldNotBe null
        configurationProvidedToClient!!.useConfigurationFromEnvironment shouldBe true
        configurationProvidedToClient!!.host shouldBe null
        configurationProvidedToClient!!.tls shouldBe null
        configurationProvidedToClient!!.configDirectoryPath shouldBe null
    }

    should("be able to create a client with the default configuration, explicitly taking values from the environment") {
        DockerClientBuilder(factory)
            .useDefaultConfigurationFromEnvironment()
            .build()

        configurationProvidedToClient shouldNotBe null
        configurationProvidedToClient!!.useConfigurationFromEnvironment shouldBe true
        configurationProvidedToClient!!.host shouldBe null
        configurationProvidedToClient!!.tls shouldBe null
        configurationProvidedToClient!!.configDirectoryPath shouldBe null
    }

    should("be able to create a client with the default configuration, without taking values from the environment") {
        DockerClientBuilder(factory)
            .doNotUseDefaultConfigurationFromEnvironment()
            .build()

        configurationProvidedToClient!!.useConfigurationFromEnvironment shouldBe false
    }

    should("throw an exception if the protocol is not supported on this operating system") {
        val protocol = when (testEnvironmentOperatingSystem) {
            OperatingSystem.Windows -> "unix"
            OperatingSystem.MacOS, OperatingSystem.Linux -> "npipe"
        }

        val exception = shouldThrow<DockerClientException> {
            DockerClientBuilder(factory)
                .withHost("$protocol://thisdoesnotexist.batect.dev")
                .build()
        }

        exception.message shouldBe "protocol not available"
    }

    should("throw an exception if the provided config directory does not exist") {
        val exception = shouldThrow<DockerClientException> {
            DockerClientBuilder(factory)
                .withConfigDirectory("thisdirectorydoesnotexist")
                .build()
        }

        exception.message shouldBe "configuration directory 'thisdirectorydoesnotexist' does not exist or is not a directory"
    }

    should("configure the client with the given configuration directory") {
        DockerClientBuilder(factory)
            .withConfigDirectory(".")
            .build()

        configurationProvidedToClient!!.configDirectoryPath shouldBe "."
    }

    should("configure the client with the given host name") {
        val host = when (testEnvironmentOperatingSystem) {
            OperatingSystem.Windows -> "npipe:////./pipe/docker_engine"
            OperatingSystem.MacOS, OperatingSystem.Linux -> "unix:///var/run/docker.sock"
        }

        DockerClientBuilder(factory)
            .withHost(host)
            .build()

        configurationProvidedToClient!!.host shouldBe host
    }

    should("configure the client with the given TLS certificate and key files") {
        DockerClientBuilder(factory)
            .withTLSConfiguration(
                "$rootTestCertificatesDirectory/ca.pem",
                "$rootTestCertificatesDirectory/cert.pem",
                "$rootTestCertificatesDirectory/key.pem",
                TLSVerification.Enabled
            )
            .build()

        configurationProvidedToClient!!.tls shouldBe DockerClientTLSConfiguration(
            "$rootTestCertificatesDirectory/ca.pem",
            "$rootTestCertificatesDirectory/cert.pem",
            "$rootTestCertificatesDirectory/key.pem",
            false
        )
    }

    should("configure default to verifying the daemon's identity if no explicit value is provided") {
        DockerClientBuilder(factory)
            .withTLSConfiguration(
                "$rootTestCertificatesDirectory/ca.pem",
                "$rootTestCertificatesDirectory/cert.pem",
                "$rootTestCertificatesDirectory/key.pem"
            )
            .build()

        configurationProvidedToClient!!.tls shouldBe DockerClientTLSConfiguration(
            "$rootTestCertificatesDirectory/ca.pem",
            "$rootTestCertificatesDirectory/cert.pem",
            "$rootTestCertificatesDirectory/key.pem",
            false
        )
    }

    should("configure the client with the given TLS certificate and key files with verification disabled") {
        DockerClientBuilder(factory)
            .withTLSConfiguration(
                "$rootTestCertificatesDirectory/ca.pem",
                "$rootTestCertificatesDirectory/cert.pem",
                "$rootTestCertificatesDirectory/key.pem",
                TLSVerification.InsecureDisabled
            )
            .build()

        configurationProvidedToClient!!.tls shouldBe DockerClientTLSConfiguration(
            "$rootTestCertificatesDirectory/ca.pem",
            "$rootTestCertificatesDirectory/cert.pem",
            "$rootTestCertificatesDirectory/key.pem",
            true
        )
    }

    should("throw an exception if the provided CA certificate file does not exist") {
        val exception = shouldThrow<DockerClientException> {
            DockerClientBuilder(factory)
                .withTLSConfiguration(
                    "$rootTestCertificatesDirectory/ca-does-not-exist.pem",
                    "$rootTestCertificatesDirectory/cert.pem",
                    "$rootTestCertificatesDirectory/key.pem"
                )
                .build()
        }

        exception.message shouldStartWith "failed to create TLS config: could not read CA certificate"
        exception.message shouldEndWith "$rootTestCertificatesDirectory/ca-does-not-exist.pem: no such file or directory"
    }

    should("throw an exception if the provided client certificate file does not exist") {
        val exception = shouldThrow<DockerClientException> {
            DockerClientBuilder(factory)
                .withTLSConfiguration(
                    "$rootTestCertificatesDirectory/ca.pem",
                    "$rootTestCertificatesDirectory/cert-does-not-exist.pem",
                    "$rootTestCertificatesDirectory/key.pem"
                )
                .build()
        }

        exception.message shouldStartWith "failed to create TLS config: Could not load X509 key pair"
        exception.message shouldEndWith "$rootTestCertificatesDirectory/cert-does-not-exist.pem: no such file or directory"
    }

    should("throw an exception if the provided client key file does not exist") {
        val exception = shouldThrow<DockerClientException> {
            DockerClientBuilder(factory)
                .withTLSConfiguration(
                    "$rootTestCertificatesDirectory/ca.pem",
                    "$rootTestCertificatesDirectory/cert.pem",
                    "$rootTestCertificatesDirectory/key-does-not-exist.pem"
                )
                .build()
        }

        exception.message shouldStartWith "failed to create TLS config: Could not load X509 key pair"
        exception.message shouldEndWith "$rootTestCertificatesDirectory/key-does-not-exist.pem: no such file or directory"
    }

    // TODO: these tests verify that the right thing is passed into the native library, but don't verify that the have the intended effect
    // Need to cover:
    // - TLS
    // - host name
    // - configuration directory
})
