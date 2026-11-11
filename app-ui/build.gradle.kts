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
        }
        // Compiled against the androidx Compose the app runs with (inline functions are copied in at compile time).
        androidMain.dependencies {
            implementation(project.dependencies.platform(libs.compose.bom))
            // The Android sides of the seams: the activity's back handling and Ktor's Android engine, as the app has them.
            implementation(libs.activity.compose)
            implementation(libs.ktor.client.android)
        }
    }
}
