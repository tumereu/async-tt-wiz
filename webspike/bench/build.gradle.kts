import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// Web spike (throwaway): the engine benchmark on the JVM and in Kotlin/Wasm (Node, a page, a Web Worker).
plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    jvmToolchain(21)
    jvm {
        compilerOptions.jvmTarget.set(JvmTarget.JVM_21)
        mainRun { mainClass.set("webspike.bench.MainKt") }
    }
    @OptIn(org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class)
    wasmJs {
        outputModuleName.set("webspike-bench")
        browser()
        nodejs()
        binaries.executable()
    }
    sourceSets {
        commonMain.dependencies {
            implementation(project(":automatum"))
            implementation(project(":protocol"))
            implementation(project(":arcanum-parser"))
            implementation(libs.serialization.json)
        }
    }
}
