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
val createKleinSymlink = tasks.register<Exec>("createKleinSymlink") {
    group = "build"
    description = "Create ./klein symlink to the native CLI binary"

    // Determine the platform-specific link task
    val arch = System.getProperty("os.arch")
    val linkTaskName =
        when {
            org.gradle.internal.os.OperatingSystem
                .current()
                .isMacOsX && (arch == "aarch64" || arch == "arm64") -> "linkDebugExecutableMacosArm64"
            org.gradle.internal.os.OperatingSystem
                .current()
                .isLinux -> "linkDebugExecutableLinuxX64"
            else -> null
        }

    if (linkTaskName != null) {
        dependsOn(linkTaskName)

        val targetPath =
            when (linkTaskName) {
                "linkDebugExecutableMacosArm64" -> "klein-lib/build/bin/macosArm64/debugExecutable/klein-lib.kexe"
                "linkDebugExecutableLinuxX64" -> "klein-lib/build/bin/linuxX64/debugExecutable/klein-lib.kexe"
                else -> null
            }

        if (targetPath != null) {
            val projectRoot = project.rootProject.projectDir

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
    }
}

// Make the symlink task run automatically after the appropriate link task
afterEvaluate {
    val arch = System.getProperty("os.arch")
    val linkTaskName =
        when {
            org.gradle.internal.os.OperatingSystem
                .current()
                .isMacOsX && (arch == "aarch64" || arch == "arm64") -> "linkDebugExecutableMacosArm64"
            org.gradle.internal.os.OperatingSystem
                .current()
                .isLinux -> "linkDebugExecutableLinuxX64"
            else -> null
        }

    if (linkTaskName != null) {
        tasks.named(linkTaskName) {
            finalizedBy(createKleinSymlink)
        }
    }
}
