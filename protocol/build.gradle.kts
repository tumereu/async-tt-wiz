import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kmp.library)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    jvmToolchain(21)
    jvm {
        compilerOptions.jvmTarget.set(JvmTarget.JVM_21)
        testRuns["test"].executionTask.configure {
            useJUnitPlatform()
            // Test classes run in parallel JVMs (also capped by org.gradle.workers.max); -PatwTestForks=<n> overrides.
            maxParallelForks = providers.gradleProperty("atwTestForks").map(String::toInt)
                .getOrElse((Runtime.getRuntime().availableProcessors() / 3).coerceIn(1, 4))
            // -Patw.recordEventFormat=true rewrites the stored engine event sample (EventFormatJvmTest, I-D54).
            providers.gradleProperty("atw.recordEventFormat").orNull?.let { systemProperty("atw.recordEventFormat", it) }
        }
    }
    android {
        namespace = "org.asyncttwiz.protocol"
        compileSdk = 36
        minSdk = 29
        compilerOptions.jvmTarget.set(JvmTarget.JVM_17)
    }
    sourceSets {
        commonMain.dependencies {
            api(project(":core"))
            // I-D54: a game's state is the Arcanum engine's state plus ATW's table state (package game).
            api(project(":arcanum-engine"))
            // I-D54 switch-over item 1: the server and the device read game cards with one parser (game/GameCards)
            api(project(":arcanum-parser"))
            implementation(libs.serialization.json)
            implementation("org.jetbrains.kotlinx:kotlinx-collections-immutable:0.4.0")
        }
        commonTest {
            // The engine's invented cube cards by name, shared as source (their texts live only there), as :automatum does.
            kotlin.srcDir("../arcanum-engine/src/commonTest/kotlin/arcanum/engine/cards")
            dependencies {
                implementation(libs.kotlin.test)
                implementation(project(":scale-fixtures"))
                // Tests only: the shared cube cards are parsed from their oracle text.
                implementation(project(":arcanum-parser"))
            }
        }
        jvmTest.dependencies {
            implementation(libs.kotlin.test.junit5)
            implementation(project.dependencies.platform(libs.junit.bom))
            runtimeOnly(libs.junit.jupiter)
            runtimeOnly(libs.junit.launcher)
        }
    }
}
