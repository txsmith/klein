plugins {
    kotlin("multiplatform")
    kotlin("plugin.serialization")
}

tasks.withType<Test> {
    testLogging {
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
        showExceptions = true
        showCauses = true
        showStackTraces = true
        afterSuite(
            KotlinClosure2<TestDescriptor, TestResult, Unit>({ desc, result ->
                if (desc.parent == null) {
                    println(
                        "\nResults: ${result.resultType} (${result.testCount} tests, ${result.successfulTestCount} passed, ${result.failedTestCount} failed, ${result.skippedTestCount} skipped)",
                    )
                }
            }),
        )
    }
}

kotlin {
    jvm()

    js(IR) {
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

    macosX64 {
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
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
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
val createKleinSymlink by tasks.registering(Exec::class) {
    group = "build"
    description = "Create ./klein symlink to the native CLI binary"

    // Determine the platform-specific link task
    val linkTaskName =
        when {
            org.gradle.internal.os.OperatingSystem
                .current()
                .isMacOsX -> {
                val arch = System.getProperty("os.arch")
                if (arch == "aarch64" || arch == "arm64") {
                    "linkDebugExecutableMacosArm64"
                } else {
                    "linkDebugExecutableMacosX64"
                }
            }
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
                "linkDebugExecutableMacosX64" -> "klein-lib/build/bin/macosX64/debugExecutable/klein-lib.kexe"
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
                .isMacOsX -> {
                if (arch == "aarch64" || arch == "arm64") {
                    "linkDebugExecutableMacosArm64"
                } else {
                    "linkDebugExecutableMacosX64"
                }
            }
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
