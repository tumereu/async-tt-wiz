import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// Generic MTG rules library (docs/specs/arcanum-automatum.md). Must not depend on any ATW module.
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
            // -Parcanum.regenerate=true rewrites the generated cube card list (CubeCardsInSyncTest).
            systemProperty("arcanum.regenerate", providers.gradleProperty("arcanum.regenerate").getOrElse("false"))
            // -Parcanum.longGames=N plays N seeded random whole games (Review2LongGamesJvmTest); 0 skips them.
            listOf("arcanum.longGames", "arcanum.longGames.from", "arcanum.longGames.players").forEach { key ->
                providers.gradleProperty(key).orNull?.let { systemProperty(key, it) }
            }
        }
    }
    android {
        namespace = "arcanum.engine"
        compileSdk = 36
        minSdk = 29
        compilerOptions.jvmTarget.set(JvmTarget.JVM_17)
    }
    sourceSets {
        commonMain.dependencies { api(project(":arcanum-api")) }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            // Tests only: the invented cube cards are parsed from their oracle text (cards/CubeCardTexts.kt).
            implementation(project(":arcanum-parser"))
        }
        jvmTest.dependencies {
            implementation(libs.kotlin.test.junit5)
            implementation(libs.kotlin.reflect) // lists sealed types for completeness tests
            implementation(project.dependencies.platform(libs.junit.bom))
            runtimeOnly(libs.junit.jupiter)
            runtimeOnly(libs.junit.launcher)
        }
    }
}
