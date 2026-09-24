plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.allopen)
    alias(libs.plugins.kotlinx.benchmark)
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
                implementation(libs.kotlinx.benchmark.runtime)
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
