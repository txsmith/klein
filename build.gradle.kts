plugins {
    kotlin("multiplatform") version "2.0.21" apply false
    kotlin("plugin.serialization") version "2.0.21" apply false
}

allprojects {
    repositories {
        mavenCentral()
    }
}
