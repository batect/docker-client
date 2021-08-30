rootProject.name = "docker-client"

pluginManagement {
    plugins {
        id("com.diffplug.spotless") version "5.14.3" apply false
    }
}

include("lib")
