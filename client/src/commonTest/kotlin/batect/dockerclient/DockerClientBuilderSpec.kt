/*
    Copyright 2017-2022 Charles Korn.

    Licensed under the Apache License, Version 2.0 (the "License");
    you may not use this file except in compliance with the License.
    You may obtain a copy of the License at

        https://www.apache.org/licenses/LICENSE-2.0

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
import okio.FileSystem
import okio.Path.Companion.toPath

class DockerClientBuilderSpec : ShouldSpec({
    val rootTestCertificatesDirectory = FileSystem.SYSTEM.canonicalize("./src/commonTest/resources/dummy-client-certificates".toPath())

    val operatingSystemFileNotFoundMessage = when (testEnvironmentOperatingSystem) {
        OperatingSystem.Windows -> "The system cannot find the file specified."
        OperatingSystem.Linux, OperatingSystem.MacOS -> "no such file or directory"
    }

    var configurationProvidedToClient: DockerClientConfiguration? = null

    val factory: DockerClientFactory = { config ->
        configurationProvidedToClient = config
        RealDockerClient(config)
    }

    beforeEach { configurationProvidedToClient = null }

    should("be able to create a client with the default configuration, implicitly taking values from the environment") {
        DockerClient.Builder(factory).build()

        configurationProvidedToClient shouldNotBe null
        configurationProvidedToClient!!.useConfigurationFromEnvironment shouldBe true
        configurationProvidedToClient!!.host shouldBe null
        configurationProvidedToClient!!.tls shouldBe null
        configurationProvidedToClient!!.configDirectoryPath shouldBe null
    }

    should("be able to create a client with the default configuration, explicitly taking values from the environment") {
        DockerClient.Builder(factory)
            .useDefaultConfigurationFromEnvironment()
            .build()

        configurationProvidedToClient shouldNotBe null
        configurationProvidedToClient!!.useConfigurationFromEnvironment shouldBe true
        configurationProvidedToClient!!.host shouldBe null
        configurationProvidedToClient!!.tls shouldBe null
        configurationProvidedToClient!!.configDirectoryPath shouldBe null
    }

    should("be able to create a client with the default configuration, without taking values from the environment") {
        DockerClient.Builder(factory)
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
            DockerClient.Builder(factory)
                .withHost("$protocol://thisdoesnotexist.batect.dev")
                .build()
        }

        exception.message shouldBe "protocol not available"
    }

    should("throw an exception if the provided config directory does not exist") {
        val exception = shouldThrow<DockerClientException> {
            DockerClient.Builder(factory)
                .withConfigDirectory("thisdirectorydoesnotexist".toPath())
                .build()
        }

        exception.message shouldBe "configuration directory 'thisdirectorydoesnotexist' does not exist or is not a directory"
    }

    should("configure the client with the given configuration directory") {
        DockerClient.Builder(factory)
            .withConfigDirectory(".".toPath())
            .build()

        configurationProvidedToClient!!.configDirectoryPath shouldBe "."
    }

    should("configure the client with the given host name") {
        val host = when (testEnvironmentOperatingSystem) {
            OperatingSystem.Windows -> "npipe:////./pipe/docker_engine"
            OperatingSystem.MacOS, OperatingSystem.Linux -> "unix:///var/run/docker.sock"
        }

        DockerClient.Builder(factory)
            .withHost(host)
            .build()

        configurationProvidedToClient!!.host shouldBe host
    }

    should("configure the client with the given TLS certificate and key files") {
        DockerClient.Builder(factory)
            .withTLSConfiguration(
                rootTestCertificatesDirectory / "ca.pem",
                rootTestCertificatesDirectory / "cert.pem",
                rootTestCertificatesDirectory / "key.pem",
                TLSVerification.Enabled
            )
            .build()

        configurationProvidedToClient!!.tls shouldBe DockerClientTLSConfiguration(
            (rootTestCertificatesDirectory / "ca.pem").toString(),
            (rootTestCertificatesDirectory / "cert.pem").toString(),
            (rootTestCertificatesDirectory / "key.pem").toString(),
            false
        )
    }

    should("configure default to verifying the daemon's identity if no explicit value is provided") {
        DockerClient.Builder(factory)
            .withTLSConfiguration(
                rootTestCertificatesDirectory / "ca.pem",
                rootTestCertificatesDirectory / "cert.pem",
                rootTestCertificatesDirectory / "key.pem"
            )
            .build()

        configurationProvidedToClient!!.tls shouldBe DockerClientTLSConfiguration(
            (rootTestCertificatesDirectory / "ca.pem").toString(),
            (rootTestCertificatesDirectory / "cert.pem").toString(),
            (rootTestCertificatesDirectory / "key.pem").toString(),
            false
        )
    }

    should("configure the client with the given TLS certificate and key files with verification disabled") {
        DockerClient.Builder(factory)
            .withTLSConfiguration(
                rootTestCertificatesDirectory / "ca.pem",
                rootTestCertificatesDirectory / "cert.pem",
                rootTestCertificatesDirectory / "key.pem",
                TLSVerification.InsecureDisabled
            )
            .build()

        configurationProvidedToClient!!.tls shouldBe DockerClientTLSConfiguration(
            (rootTestCertificatesDirectory / "ca.pem").toString(),
            (rootTestCertificatesDirectory / "cert.pem").toString(),
            (rootTestCertificatesDirectory / "key.pem").toString(),
            true
        )
    }

    should("throw an exception if the provided CA certificate file does not exist") {
        val exception = shouldThrow<DockerClientException> {
            DockerClient.Builder(factory)
                .withTLSConfiguration(
                    rootTestCertificatesDirectory / "ca-does-not-exist.pem",
                    rootTestCertificatesDirectory / "cert.pem",
                    rootTestCertificatesDirectory / "key.pem"
                )
                .build()
        }

        exception.message shouldStartWith "failed to create TLS config: could not read CA certificate"
        exception.message shouldEndWith "${rootTestCertificatesDirectory.resolve("ca-does-not-exist.pem")}: $operatingSystemFileNotFoundMessage"
    }

    should("throw an exception if the provided client certificate file does not exist") {
        val exception = shouldThrow<DockerClientException> {
            DockerClient.Builder(factory)
                .withTLSConfiguration(
                    rootTestCertificatesDirectory / "ca.pem",
                    rootTestCertificatesDirectory / "cert-does-not-exist.pem",
                    rootTestCertificatesDirectory / "key.pem"
                )
                .build()
        }

        exception.message shouldStartWith "failed to create TLS config: Could not load X509 key pair"
        exception.message shouldEndWith "${rootTestCertificatesDirectory.resolve("cert-does-not-exist.pem")}: $operatingSystemFileNotFoundMessage"
    }

    should("throw an exception if the provided client key file does not exist") {
        val exception = shouldThrow<DockerClientException> {
            DockerClient.Builder(factory)
                .withTLSConfiguration(
                    rootTestCertificatesDirectory / "ca.pem",
                    rootTestCertificatesDirectory / "cert.pem",
                    rootTestCertificatesDirectory / "key-does-not-exist.pem"
                )
                .build()
        }

        exception.message shouldStartWith "failed to create TLS config: Could not load X509 key pair"
        exception.message shouldEndWith "${rootTestCertificatesDirectory.resolve("key-does-not-exist.pem")}: $operatingSystemFileNotFoundMessage"
    }
})
