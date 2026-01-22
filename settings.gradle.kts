pluginManagement {
    repositories {
        // Check local repository first for offline builds
        maven {
            url = uri("${rootDir}/gradle/local-repo")
            name = "localRepo"
        }
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositories {
        // Check local repository first for offline builds
        maven {
            url = uri("${rootDir}/gradle/local-repo")
            name = "localRepo"
        }
        mavenCentral()
    }
}

rootProject.name = "klein"

include(":klein-lib")
