plugins {
    id("com.diffplug.spotless")
}

repositories {
    mavenCentral()
}

tasks.named<Wrapper>("wrapper") {
    distributionType = Wrapper.DistributionType.ALL
}

allprojects {
    afterEvaluate {
        if (extensions.findByName("spotless") == null) {
            throw RuntimeException("Project ${this.displayName} does not have the Spotless plugin applied.")
        }

        val isKotlinProject = plugins.hasPlugin("org.jetbrains.kotlin.multiplatform")

        spotless {
            kotlinGradle {
                ktlint()
            }

            if (isKotlinProject) {
                kotlin {
                    target(fileTree("src").include("**/*.kt"))
                    ktlint()
                }
            }
        }
    }
}
