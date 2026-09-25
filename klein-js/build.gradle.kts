plugins {
    alias(libs.plugins.kotlin.multiplatform)
}

kotlin {
    js {
        browser {
            testTask {
                enabled = false
            }
        }
        nodejs()
        outputModuleName = "klein"
        useEsModules()
        compilerOptions {
            target = "es2015"
            optIn.add("kotlin.js.ExperimentalJsExport")
            optIn.add("kotlin.js.ExperimentalJsCollectionsApi")
        }
        generateTypeScriptDefinitions()
        binaries.library()
        compilations["main"].packageJson {
            customField("types", "index.d.mts")
            customField("exports", mapOf("." to mapOf("types" to "./index.d.mts", "default" to "./klein.mjs")))
            customField("sideEffects", false)
        }
    }

    sourceSets {
        jsMain {
            dependencies {
                implementation(project(":klein-lib"))
            }
        }

        jsTest {
            dependencies {
                implementation(devNpm("@types/node", "24.13.6"))
            }
        }
    }
}

val bindingTest = tasks.register<Exec>("bindingTest") {
    group = "verification"
    description = "Run the JavaScript tests against the built binding"
    dependsOn("jsNodeProductionLibraryDistribution")
    inputs.dir("test")
    inputs.dir(layout.buildDirectory.dir("dist/js/productionLibrary"))
    commandLine("node", "--test", "test/")
}

val bindingTypes = tasks.register<Exec>("bindingTypes") {
    group = "verification"
    description = "Type-check the handwritten TypeScript declarations against the generated ones"
    dependsOn("jsNodeProductionLibraryDistribution", ":kotlinNpmInstall")
    inputs.dir("test")
    inputs.dir(layout.buildDirectory.dir("dist/js/productionLibrary"))
    commandLine(
        "node",
        rootProject.layout.buildDirectory.file("js/node_modules/typescript/bin/tsc").get().asFile.path,
        "--noEmit",
        "--strict",
        "--target",
        "es2022",
        "--module",
        "nodenext",
        "--moduleResolution",
        "nodenext",
        "--allowImportingTsExtensions",
        "--typeRoots",
        rootProject.layout.buildDirectory.dir("js/node_modules/@types").get().asFile.path,
        "--types",
        "node",
        "test/types.check.mts",
        "test/fixtures.mts",
        "test/host-errors.test.mts",
    )
}

tasks.named("check") {
    dependsOn(bindingTest, bindingTypes)
}
