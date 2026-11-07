import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// Web spike (throwaway): the first piece of the Android UI moved into a Compose Multiplatform module that builds for
// Android (used by :androidApp) and for the browser (Kotlin/Wasm). JetBrains Compose artifacts resolve to the androidx
// ones on Android (1.11.1 -> androidx.compose 1.11.x, the app's own versions).
plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kmp.library)
    alias(libs.plugins.kotlin.compose)
}

val composeMp = "1.11.1"

kotlin {
    jvmToolchain(21)
    android {
        namespace = "org.asyncttwiz.sharedui"
        compileSdk = 36
        minSdk = 29
        compilerOptions.jvmTarget.set(JvmTarget.JVM_17)
    }
    @OptIn(org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class)
    wasmJs { browser() }
    sourceSets {
        commonMain.dependencies {
            api(project(":protocol"))
            api(project(":automatum"))
            api("org.jetbrains.compose.runtime:runtime:$composeMp")
            api("org.jetbrains.compose.foundation:foundation:$composeMp")
            api("org.jetbrains.compose.ui:ui:$composeMp")
        }
    }
}
