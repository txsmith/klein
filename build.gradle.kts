plugins {
    kotlin("multiplatform") version "2.0.21" apply false
    kotlin("plugin.serialization") version "2.0.21" apply false
}

// Task to cache dependencies for offline builds
tasks.register("cacheOfflineDependencies") {
    group = "build setup"
    description = "Downloads and caches all dependencies to gradle/local-repo for offline builds"

    doLast {
        val localRepo = file("gradle/local-repo")
        println("Caching dependencies to: ${localRepo.absolutePath}")
        println("Run './gradlew build --offline' after this to verify offline builds work")
    }
}
