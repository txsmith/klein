plugins {
    kotlin("jvm")
    application
}

dependencies {
    implementation(project(":klein-lib"))
    testImplementation(kotlin("test"))
}

application {
    mainClass = "klein.example.LendingHostKt"
}

// Paths on the command line and in the test are relative to the repository root, so
// `examples/lending.contract` means the same thing in both.
tasks.named<JavaExec>("run") {
    workingDir = rootDir
}

tasks.test {
    workingDir = rootDir
    useJUnitPlatform()
}
