import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// The shared Compose UI (docs/specs/web-client.md §1 and §11 step 2): the app's screens move here from :androidApp, into
// commonMain, so they build for Android and for the browser (Kotlin/Wasm). Compose Multiplatform's Android artifacts
// only point at androidx Compose, and :androidApp's compose-bom keeps those at the versions the app has always used.
plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kmp.library)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.compose.multiplatform)
}

kotlin {
    jvmToolchain(21)
    android {
        namespace = "org.asyncttwiz.appui"
        compileSdk = 36
        minSdk = 29
        compilerOptions.jvmTarget.set(JvmTarget.JVM_17)
    }
    // Kotlin/Wasm for the web client. Browser only: Compose's Skia runtime (Skiko) doesn't load in Node, so this module's
    // wasm tests will need a browser (decided with the web tests, docs/specs/web-client.md §1 item 6).
    @OptIn(org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class)
    wasmJs {
        browser { testTask { enabled = false } }
    }
    sourceSets {
        commonMain.dependencies {
            implementation(project(":core"))
            implementation(project(":protocol"))
            implementation(project(":automatum"))
            implementation(libs.cmp.runtime)
            implementation(libs.cmp.foundation)
            implementation(libs.cmp.ui)
            implementation(libs.cmp.material3)
            // Platform seams (docs/specs/web-client.md §1 item 2): the HTTP client and Coil's image type.
            implementation(libs.ktor.client.core)
            implementation(libs.coil.core)
            // The app's UI (step 2c-2). Each resolves on Android to what the app already runs with: JetBrains' icons and
            // lifecycle point at androidx's (compose-bom's), Coil Compose and the rest are multiplatform themselves.
            implementation(project(":client-storage"))
            implementation(libs.cmp.material.icons)
            implementation(libs.cmp.lifecycle.viewmodel)
            implementation(libs.cmp.lifecycle.runtime.compose)
            implementation(libs.coil.compose)
            implementation(libs.serialization.json)
            implementation(libs.collections.immutable)
        }
        // Compiled against the androidx Compose the app runs with (inline functions are copied in at compile time).
        androidMain.dependencies {
            implementation(project.dependencies.platform(libs.compose.bom))
            // The Android sides of the seams: the activity's back handling and Ktor's Android engine, as the app has them.
            implementation(libs.activity.compose)
            implementation(libs.ktor.client.android)
            // The app's Android-only pieces (the socket on OkHttp), and the androidx Compose the app compiles with.
            implementation(libs.compose.ui)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            implementation(libs.compose.material.icons)
            implementation(libs.okhttp)
        }
        // The browser's sides of the storage, card records and pictures (web client step 3a): IndexedDB, fetch, the Cache
        // API and DecompressionStream, through the browser API declarations Compose already brings (same version).
        wasmJsMain.dependencies {
            implementation(libs.kotlinx.browser)
            implementation(libs.coroutines.core)
            // The web shell's Back (step 3): Esc and the close watcher go through Compose's own Back handling.
            implementation(libs.cmp.ui.backhandler)
            implementation(libs.navigationevent)
        }
    }
}
