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
import okio.Path.Companion.toPath

class DockerClientConfigurationBuilderSpec : ShouldSpec({
    val rootTestCertificatesDirectory = systemFileSystem.canonicalize("./src/commonTest/resources/cli-configuration/tls".toPath())

    should("configure the client with the given host name") {
        val host = "tcp://some-host"

        val config = DockerClientConfiguration.Builder(host)
            .build()

        config.host shouldBe host
    }

    should("default to performing daemon identity verification") {
        val config = DockerClientConfiguration.Builder("tcp://some-host")
            .build()

        config.daemonIdentityVerification shouldBe TLSVerification.Enabled
    }

    should("disable daemon identity verification if requested") {
        val config = DockerClientConfiguration.Builder("tcp://some-host")
            .withDaemonIdentityVerificationDisabled()
            .build()

        config.daemonIdentityVerification shouldBe TLSVerification.InsecureDisabled
    }

    should("throw an exception if the protocol is not supported on this operating system") {
        val protocol = when (testEnvironmentOperatingSystem) {
            OperatingSystem.Windows -> "unix"
            OperatingSystem.MacOS, OperatingSystem.Linux -> "npipe"
        }

        val exception = shouldThrow<DockerClientException> {
            val config = DockerClientConfiguration.Builder("$protocol://thisdoesnotexist.batect.dev")
                .build()

            DockerClient.create(config)
        }

        exception.message shouldBe "protocol not available"
    }

    should("configure the client with the given configuration directory") {
        val config = DockerClientConfiguration.Builder("tcp://host")
            .withConfigurationDirectory(".".toPath())
            .build()

        config.configurationDirectory shouldBe "."
    }

    should("throw an exception if the provided config directory does not exist") {
        val exception = shouldThrow<DockerClientException> {
            DockerClientConfiguration.Builder("tcp://host")
                .withConfigurationDirectory("thisdirectorydoesnotexist".toPath())
                .build()
        }

        exception.message shouldBe "configuration directory 'thisdirectorydoesnotexist' does not exist or is not a directory"
    }

    should("configure the client with the given TLS certificate and key files") {
        val config = DockerClientConfiguration.Builder("tcp://host")
            .withTLSConfiguration(
                rootTestCertificatesDirectory / "ca.pem",
                rootTestCertificatesDirectory / "cert.pem",
                rootTestCertificatesDirectory / "key.pem"
            )
            .build()

        config.tls shouldBe DockerClientTLSConfiguration(
            systemFileSystem.readAllBytes(rootTestCertificatesDirectory / "ca.pem"),
            systemFileSystem.readAllBytes(rootTestCertificatesDirectory / "cert.pem"),
            systemFileSystem.readAllBytes(rootTestCertificatesDirectory / "key.pem")
        )
    }

    should("throw an exception if the provided CA certificate file does not exist") {
        val caFilePath = rootTestCertificatesDirectory / "ca-does-not-exist.pem"

        val exception = shouldThrow<DockerClientException> {
            DockerClientConfiguration.Builder("tcp://host")
                .withTLSConfiguration(
                    caFilePath,
                    rootTestCertificatesDirectory / "cert.pem",
                    rootTestCertificatesDirectory / "key.pem"
                )
                .build()
        }

        exception.message shouldBe "CA certificate file '$caFilePath' does not exist."
    }

    should("throw an exception if the provided client certificate file does not exist") {
        val certFilePath = rootTestCertificatesDirectory / "cert-does-not-exist.pem"

        val exception = shouldThrow<DockerClientException> {
            DockerClientConfiguration.Builder("tcp://host")
                .withTLSConfiguration(
                    rootTestCertificatesDirectory / "ca.pem",
                    certFilePath,
                    rootTestCertificatesDirectory / "key.pem"
                )
                .build()
        }

        exception.message shouldBe "Client certificate file '$certFilePath' does not exist."
    }

    should("throw an exception if the provided client key file does not exist") {
        val keyFilePath = rootTestCertificatesDirectory / "key-does-not-exist.pem"

        val exception = shouldThrow<DockerClientException> {
            DockerClientConfiguration.Builder("tcp://host")
                .withTLSConfiguration(
                    rootTestCertificatesDirectory / "ca.pem",
                    rootTestCertificatesDirectory / "cert.pem",
                    keyFilePath
                )
                .build()
        }

        exception.message shouldBe "Client key file '$keyFilePath' does not exist."
    }
})
