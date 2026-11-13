import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// The app's engine side without UI (docs/specs/web-client.md §1 item 3, §11 step 4c): a game played on engine state on
// this device (EngineGame: the device core, its published boards and their computed values) and the platform pieces it
// needs. Android runs it in-process on its engine thread under the shared Compose UI (:app-ui); the browser's engine
// Web Worker (:webApp:engine) builds it without Compose.
plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kmp.library)
}

kotlin {
    jvmToolchain(21)
    android {
        namespace = "org.asyncttwiz.appengine"
        compileSdk = 36
        minSdk = 29
        compilerOptions.jvmTarget.set(JvmTarget.JVM_17)
    }
    @OptIn(org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class)
    wasmJs {
        browser { testTask { enabled = false } }
    }
    sourceSets {
        commonMain.dependencies {
            api(project(":core"))
            api(project(":protocol"))
            api(project(":automatum"))
            api(project(":client-storage"))
            implementation(libs.serialization.json)
            implementation(libs.coroutines.core)
        }
    }
}
