// Web spike (throwaway): a Compose Multiplatform (Kotlin/Wasm) battlefield of ~150 cards with real card images.
plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.compose)
    // Unpacks the Skiko wasm runtime (skiko.mjs/.wasm) next to the app; resources, if used, need it too.
    id("org.jetbrains.compose") version "1.11.1"
}

val composeMp = "1.11.1"

kotlin {
    @OptIn(org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class)
    wasmJs {
        outputModuleName.set("table")
        browser { commonWebpackConfig { outputFileName = "table.js" } }
        binaries.executable()
    }
    sourceSets {
        wasmJsMain.dependencies {
            implementation("org.jetbrains.compose.runtime:runtime:$composeMp")
            implementation("org.jetbrains.compose.foundation:foundation:$composeMp")
            implementation("org.jetbrains.compose.ui:ui:$composeMp")
        }
    }
}
