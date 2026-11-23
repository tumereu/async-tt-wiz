import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// The app's engine side without UI: a game played on engine state on
// this device (EngineGame: the device core, its published boards and their computed values) and the platform pieces it
// needs. Android runs it in-process on its engine thread under the shared Compose UI (:app-ui); the browser's engine
// Web Worker (:webApp:engine) builds it without Compose.
plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kmp.library)
}

kotlin {
    jvmToolchain(21)
    // The engine side has no composition: its own reads of what it publishes are plain by design (UnobservedEngineState)
    compilerOptions { optIn.add("org.asyncttwiz.android.UnobservedEngineState") }
    android {
        namespace = "org.asyncttwiz.appengine"
        compileSdk = 37
        minSdk = 29
        compilerOptions.jvmTarget.set(JvmTarget.JVM_17)
        withHostTest {}
    }
    // The common tests run on the JVM (the Android host tests, `test`) and on Kotlin/Wasm in Node (wasmJsNodeTest).
    @OptIn(org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class)
    wasmJs {
        browser { testTask { enabled = false } }
        nodejs()
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
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.coroutines.test)
        }
        getByName("androidHostTest").dependencies {
            implementation(project.dependencies.platform(libs.junit.bom))
            implementation(libs.junit.jupiter)
            runtimeOnly(libs.junit.launcher)
            // The common tests' kotlin.test on JUnit 5
            implementation(libs.kotlin.test.junit5)
        }
    }
}

tasks.withType<Test>().configureEach { useJUnitPlatform() }

// The name AGENTS.md and the scripts use for this module's JVM tests (the Android host tests).
tasks.register("test") {
    group = "verification"
    description = "Runs app-engine's common tests on the JVM (testAndroidHostTest)."
    dependsOn("testAndroidHostTest")
}
