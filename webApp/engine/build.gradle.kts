// The engine's Web Worker (docs/specs/web-client.md §1 item 3): the engine side of the engine boundary
// (:automatum's org.asyncttwiz.automatum.boundary) in its own Kotlin/Wasm module, so the worker loads the engine alone,
// without the UI. Its production build (engine.js and its wasm) goes into webApp's served build beside the app's
// (webApp's webDist); the page starts it as a Web Worker.
plugins {
    alias(libs.plugins.kotlin.multiplatform)
}

kotlin {
    jvmToolchain(21)
    @OptIn(org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class)
    wasmJs {
        outputModuleName.set("atw-engine")
        browser {
            commonWebpackConfig { outputFileName = "engine.js" }
            testTask { enabled = false }
        }
        binaries.executable()
    }
    sourceSets {
        wasmJsMain.dependencies {
            implementation(project(":automatum"))
            // The device itself (web client step 4c part 3): EngineGame without the UI.
            implementation(project(":app-engine"))
        }
    }
}
