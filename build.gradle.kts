/*
    Copyright 2017-2021 Charles Korn.

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

plugins {
    alias(libs.plugins.reckon)
    alias(libs.plugins.publish)
    id("batect.dockerclient.buildtools.formatting")
}

repositories {
    mavenCentral()
}

tasks.named<Wrapper>("wrapper") {
    distributionType = Wrapper.DistributionType.ALL
}

tasks.register("generate") {
    dependsOn(":golang-wrapper:generateTypes")
    dependsOn(":client:generateJvm")
}

reckon {
    scopeFromProp()
    snapshotFromProp()
}

nexusPublishing {
    repositories {
        sonatype {
            nexusUrl.set(uri("https://s01.oss.sonatype.org/service/local/"))
            snapshotRepositoryUrl.set(uri("https://s01.oss.sonatype.org/content/repositories/snapshots/"))
        }
    }

    transitionCheckOptions {
        maxRetries.set(100)
    }
}

tasks.named("closeSonatypeStagingRepository") {
    mustRunAfter(":client:publishAllPublicationsToSonatypeRepository")
}

tasks.register("publishSnapshot") {
    dependsOn(":client:publishAllPublicationsToSonatypeRepository")
}

tasks.register("publishRelease") {
    dependsOn(":client:publishAllPublicationsToSonatypeRepository")
    dependsOn("closeAndReleaseSonatypeStagingRepository")
}

allprojects {
    group = "dev.batect.docker"

    afterEvaluate {
        if (!plugins.hasPlugin("batect.dockerclient.buildtools.formatting")) {
            throw RuntimeException("Formatting convention plugin not applied to $this")
        }
    }
}

gradleEnterprise {
    buildScan {
        termsOfServiceUrl = "https://gradle.com/terms-of-service"
        termsOfServiceAgree = "yes"
    }
}
