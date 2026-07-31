plugins {
    kotlin("multiplatform")
    kotlin("plugin.allopen")
    id("org.jetbrains.kotlinx.benchmark")
}

// JMH requires benchmark classes to be open.
allOpen {
    annotation("org.openjdk.jmh.annotations.State")
}

kotlin {
    jvm()

    sourceSets {
        commonMain {
            dependencies {
                implementation(project(":klein-lib"))
                implementation("org.jetbrains.kotlinx:kotlinx-benchmark-runtime:0.4.13")
            }
        }
    }
}

benchmark {
    targets {
        register("jvm")
    }

    configurations {
        // Full statistical run: `./gradlew :klein-bench:benchmark`
        named("main") {
            warmups = 5
            iterations = 5
            iterationTime = 1
            iterationTimeUnit = "s"
        }
        // Quick sanity pass, not for real comparisons: `./gradlew :klein-bench:smokeBenchmark`
        register("smoke") {
            warmups = 1
            iterations = 1
            iterationTime = 200
            iterationTimeUnit = "ms"
        }
    }
}
