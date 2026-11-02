import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// ATW's side of Arcanum Automatum (docs/specs/arcanum-atw-integration.md §2–§4): the mapping between ATW's shared game
// state and actions and the Arcanum rules engine. Depends on both; the Arcanum libraries never depend on it.
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
            // -Pautomatum.bundle=<file> replays a report's Automatum bundle (ReproFromFileTest, I-D43)
            providers.gradleProperty("automatum.bundle").orNull?.let { systemProperty("automatum.bundle", it) }
        }
    }
    android {
        namespace = "org.asyncttwiz.automatum"
        compileSdk = 36
        minSdk = 29
        compilerOptions.jvmTarget.set(JvmTarget.JVM_17)
    }
    sourceSets {
        commonMain.dependencies {
            api(project(":protocol"))
            api(project(":arcanum-api"))
            api(project(":arcanum-engine"))
            implementation(libs.serialization.json)
        }
        commonTest {
            // The engine's invented cube cards by name, shared as source (their texts live only there).
            kotlin.srcDir("../arcanum-engine/src/commonTest/kotlin/arcanum/engine/cards")
            dependencies {
                implementation(libs.kotlin.test)
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
