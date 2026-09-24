plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
}

tasks.withType<Test> {
    testLogging {
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
        showExceptions = true
        showCauses = true
        showStackTraces = true
    }
    addTestListener(
        object : TestListener {
            override fun beforeSuite(suite: TestDescriptor) {}

            override fun beforeTest(testDescriptor: TestDescriptor) {}

            override fun afterTest(
                testDescriptor: TestDescriptor,
                result: TestResult,
            ) {}

            override fun afterSuite(
                suite: TestDescriptor,
                result: TestResult,
            ) {
                if (suite.parent == null) {
                    println(
                        "\nResults: ${result.resultType} (${result.testCount} tests, ${result.successfulTestCount} passed, ${result.failedTestCount} failed, ${result.skippedTestCount} skipped)",
                    )
                }
            }
        },
    )
}

kotlin {
    jvm()

    js {
        browser()
        nodejs()
    }

    macosArm64 {
        binaries {
            executable {
                entryPoint = "klein.main"
            }
        }
    }

    linuxX64 {
        binaries {
            executable {
                entryPoint = "klein.main"
            }
        }
    }

    sourceSets {
        commonMain {
            dependencies {
                implementation(libs.kotlinx.serialization.json)
            }
        }

        commonTest {
            dependencies {
                implementation(kotlin("test"))
            }
        }
    }
}

// Create symlink to CLI binary after building
val hostOs = org.gradle.internal.os.OperatingSystem.current()
val hostArch = System.getProperty("os.arch")
val hostNativeTarget =
    when {
        hostOs.isMacOsX && (hostArch == "aarch64" || hostArch == "arm64") -> "macosArm64"
        hostOs.isLinux -> "linuxX64"
        else -> null
    }

if (hostNativeTarget != null) {
    val linkTaskName = "linkDebugExecutable${hostNativeTarget.replaceFirstChar { it.uppercase() }}"
    val targetPath = "klein-lib/build/bin/$hostNativeTarget/debugExecutable/klein-lib.kexe"
    val projectRoot = rootProject.projectDir

    val createKleinSymlink = tasks.register<Exec>("createKleinSymlink") {
        group = "build"
        description = "Create ./klein symlink to the native CLI binary"
        dependsOn(linkTaskName)
        commandLine(
            "sh",
            "-c",
            """
            cd ${projectRoot.absolutePath} &&
            rm -f klein &&
            ln -s $targetPath klein
            """.trimIndent(),
        )
        doLast {
            println("Created symlink: ./klein -> $targetPath")
        }
    }

    afterEvaluate {
        tasks.named(linkTaskName) {
            finalizedBy(createKleinSymlink)
        }
    }
}
