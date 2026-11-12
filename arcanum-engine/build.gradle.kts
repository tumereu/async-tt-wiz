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
            listOf("arcanum.longGames", "arcanum.longGames.from", "arcanum.longGames.players", "arcanum.longGames.viewer", "arcanum.longGames.malformed",
                "arcanum.review3.games", "arcanum.review3.perf", "arcanum.massTriggers.perf", "arcanum.massTriggers.runs").forEach { key ->
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
    // Kotlin/Wasm for the web client (docs/specs/web-client.md): common tests also run on Node (wasmJsNodeTest).
    @OptIn(org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class)
    wasmJs {
        browser { testTask { enabled = false } }
        nodejs {
            // The four long random-game stress classes take ~8 of the engine's ~11 minutes on Node, so wasmJsNodeTest
            // skips them unless -Parcanum.wasmLongGames=true; the JVM always runs them. Whether to include them is
            // asked at each release (AGENTS.md).
            if (providers.gradleProperty("arcanum.wasmLongGames").orNull != "true") {
                testTask {
                    listOf("NightReview4LongGamesTest", "Review3HostTest", "Review2LongGamesTest", "Review3CubeDecksTest")
                        .forEach { filter.excludeTestsMatching("arcanum.engine.review.$it") }
                }
            }
        }
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

// The module's tests once more, noting which abilities they cover (:arcanum-tools:coveredShapes reads the file); jvmTest records nothing.
// A failing test just covers nothing, so it doesn't stop the run.
tasks.register<Test>("recordCoveredAbilities") {
    val jvmTest = tasks.named<Test>("jvmTest").get()
    testClassesDirs = jvmTest.testClassesDirs
    classpath = jvmTest.classpath
    useJUnitPlatform()
    ignoreFailures = true
    val output = layout.buildDirectory.file("arcanum/covered-abilities.tsv").get().asFile
    systemProperty("arcanum.coveredAbilities", output.path)
    outputs.upToDateWhen { false }
    doFirst { output.parentFile.mkdirs(); output.delete() }
}
