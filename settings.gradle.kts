// Extract local-repo.zip for offline builds if needed
val localRepoZip = file("gradle/local-repo.zip")
val localRepoDir = file("gradle/local-repo")
if (localRepoZip.exists() && !localRepoDir.exists()) {
    println("Extracting offline dependencies from local-repo.zip...")
    ProcessBuilder("unzip", "-q", "local-repo.zip")
        .directory(file("gradle"))
        .start()
        .waitFor()
}

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
