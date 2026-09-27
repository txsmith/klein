import org.jetbrains.kotlin.gradle.targets.js.nodejs.NodeJsEnvSpec

plugins {
    alias(libs.plugins.kotlin.multiplatform)
}

kotlin {
    js {
        nodejs {
            testTask {
                enabled = false
            }
        }
        outputModuleName = "klein-kotlin"
        useEsModules()
        compilerOptions {
            target = "es2015"
            optIn.add("kotlin.js.ExperimentalJsExport")
            optIn.add("kotlin.js.ExperimentalJsCollectionsApi")
        }
        generateTypeScriptDefinitions()
        binaries.library()
    }

    sourceSets {
        jsMain {
            dependencies {
                implementation(project(":klein-lib"))
                implementation(devNpm("typescript", "6.0.3"))
                implementation(devNpm("@types/node", "24.13.6"))
            }
        }
    }
}

val nodeJs = the<NodeJsEnvSpec>()
val nodeJsSetup = with(nodeJs) { project.nodeJsSetupTaskProvider }
val nodeModules = rootProject.layout.buildDirectory.dir("js/node_modules")
val packageDir = layout.buildDirectory.dir("package")

val syncKotlin = tasks.register<Sync>("syncKotlin") {
    description = "Copy the compiled Kotlin module into the package"
    dependsOn("jsNodeProductionLibraryDistribution")
    from(layout.buildDirectory.dir("dist/js/productionLibrary")) {
        include("*.mjs", "*.d.mts")
    }
    into(packageDir.map { it.dir("kotlin") })
}

val compileTypeScript = tasks.register<Exec>("compileTypeScript") {
    group = "build"
    description = "Compile the TypeScript API into the package"
    dependsOn(syncKotlin, ":kotlinNpmInstall", nodeJsSetup)
    inputs.dir("src/ts")
    inputs.file("tsconfig.json")
    inputs.dir(packageDir.map { it.dir("kotlin") })
    outputs.files(fileTree(packageDir) { exclude("kotlin/**") })
    commandLine(nodeJs.executable.get(), nodeModules.get().file("typescript/bin/tsc").asFile.path, "-p", "tsconfig.json")
    doLast {
        copy {
            from("package.json")
            into(packageDir)
        }
    }
}

tasks.named("assemble") {
    dependsOn(compileTypeScript)
}

val typeScriptTest = tasks.register<Exec>("typeScriptTest") {
    group = "verification"
    description = "Run the Node tests against the package"
    dependsOn(compileTypeScript, nodeJsSetup)
    inputs.dir("test")
    inputs.dir(packageDir)
    commandLine(nodeJs.executable.get(), "--test", "test/*.test.ts")
}

val typeScriptTestTypes = tasks.register<Exec>("typeScriptTestTypes") {
    group = "verification"
    description = "Type-check the Node tests against the package's declarations"
    dependsOn(compileTypeScript, nodeJsSetup)
    inputs.dir("test")
    inputs.dir(packageDir)
    commandLine(nodeJs.executable.get(), nodeModules.get().file("typescript/bin/tsc").asFile.path, "-p", "test/tsconfig.json")
}

tasks.named("check") {
    dependsOn(typeScriptTest, typeScriptTestTypes)
}
