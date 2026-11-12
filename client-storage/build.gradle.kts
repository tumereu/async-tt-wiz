import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// The app's local storage (docs/specs/web-client.md §1 items 1 and 4): commonMain holds the storage models and pure
// logic both clients share, and the browser's storage logic over an asynchronous store (IndexedDB, adapted in app-ui's
// wasm side); androidMain the Android implementation (SQLDelight on the device's SQLite, the card records index, artwork
// downloads), unchanged.
plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kmp.library)
    alias(libs.plugins.sqldelight)
}

kotlin {
    jvmToolchain(21)
    android {
        namespace = "org.asyncttwiz.storage"
        compileSdk = 36
        minSdk = 29
        compilerOptions.jvmTarget.set(JvmTarget.JVM_17)
        withHostTest {}
    }
    // Kotlin/Wasm for the web client: the common tests (the browser storage logic over an in-memory store) also run on
    // Node (wasmJsNodeTest); the Android implementation's tests are JVM tests on the Android host (androidHostTest).
    @OptIn(org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class)
    wasmJs {
        browser { testTask { enabled = false } }
        nodejs()
    }
    sourceSets {
        commonMain.dependencies {
            implementation(project(":core"))
            implementation(project(":protocol"))
            // The card catalog's format and files, and the card records it is indexed into (docs/specs/web-client.md section 3).
            api(project(":catalog"))
            // Arcanum card definitions parsed on the device (docs/specs/arcanum-atw-integration.md I-D2).
            implementation(project(":arcanum-parser"))
            implementation(libs.serialization.json)
            implementation(libs.sqldelight.runtime)
            implementation(libs.coroutines.core)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.coroutines.test)
        }
        androidMain.dependencies {
            implementation(libs.sqldelight.android.driver)
            implementation(libs.gson)
            implementation(libs.okhttp)
        }
        // The MTGJSON import (:catalog's src/mtgjson, JVM only in the catalog module) indexes the tests' invented MTGJSON
        // fixtures; the app itself reads only the server's card catalog (docs/specs/web-client.md section 3.5).
        getByName("androidHostTest") {
            kotlin.srcDir(rootProject.file("catalog/src/mtgjson/kotlin"))
            dependencies {
                implementation(libs.sqldelight.sqlite.driver)
                runtimeOnly(libs.sqlite.jdbc)
                implementation(project.dependencies.platform(libs.junit.bom))
                implementation(libs.junit.jupiter)
                runtimeOnly(libs.junit.launcher)
                // The common tests' kotlin.test on JUnit 5
                implementation(libs.kotlin.test.junit5)
            }
        }
    }
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    systemProperty("storage.fixture", file("src/commonMain/sqldelight/databases/1.db").absolutePath)
    systemProperty("catalog.fixtures", rootProject.file("tests/fixtures/catalogs").absolutePath)
    // CatalogInstallAnswersTest compares with src/androidHostTest/resources/catalog-answers; -PcatalogAnswers=record rewrites them.
    systemProperty("catalog.answers", file("src/androidHostTest/resources/catalog-answers").absolutePath)
    val recordAnswers = providers.gradleProperty("catalogAnswers").orNull == "record"
    systemProperty("catalog.answers.record", recordAnswers.toString())
    inputs.property("catalogAnswersMode", recordAnswers)
    // The test cards' card catalog (tests/fixtures/card-catalog), as the server serves it
    dependsOn(":catalog:testCardsCatalog")
    systemProperty("catalog.testCards", rootProject.file("catalog/build/test-cards-catalog").absolutePath)
    val realCatalog = providers.systemProperty("atw.realCatalog")
        .orElse(providers.environmentVariable("atw.realCatalog"))
    if (realCatalog.isPresent) {
        systemProperty("atw.realCatalog", realCatalog.get())
        maxHeapSize = "256m"
        testLogging.showStandardStreams = true
    } else {
        // Test classes run in parallel JVMs (also capped by org.gradle.workers.max); -PatwTestForks=<n> overrides.
        maxParallelForks = providers.gradleProperty("atwTestForks").map(String::toInt)
            .getOrElse((Runtime.getRuntime().availableProcessors() / 3).coerceIn(1, 4))
    }
}

// The name AGENTS.md and the scripts use for this module's tests (the Android host tests).
tasks.register("test") {
    group = "verification"
    description = "Runs client-storage's tests (testAndroidHostTest)."
    dependsOn("testAndroidHostTest")
}

sqldelight {
    databases {
        create("ClientDatabase") {
            packageName.set("org.asyncttwiz.storage.internaldb")
            // API29 ships SQLite 3.22: do not generate newer UPSERT/RETURNING SQL.
            dialect("app.cash.sqldelight:sqlite-3-18-dialect:${libs.versions.sqldelight.get()}")
            schemaOutputDirectory.set(file("src/commonMain/sqldelight/databases"))
            verifyMigrations.set(true)
        }
    }
}
