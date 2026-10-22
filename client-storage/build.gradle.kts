import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.sqldelight)
}

android {
    namespace = "org.asyncttwiz.storage"
    compileSdk = 36
    defaultConfig { minSdk = 29 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    testOptions.unitTests.all {
        it.useJUnitPlatform()
        it.systemProperty("storage.fixture", file("src/main/sqldelight/databases/1.db").absolutePath)
        it.systemProperty("catalog.fixtures", rootProject.file("tests/fixtures/catalogs").absolutePath)
    }
}

kotlin { compilerOptions.jvmTarget.set(JvmTarget.JVM_17) }

sqldelight {
    databases {
        create("ClientDatabase") {
            packageName.set("org.asyncttwiz.storage.internaldb")
            // API29 ships SQLite 3.22: do not generate newer UPSERT/RETURNING SQL.
            dialect("app.cash.sqldelight:sqlite-3-18-dialect:${libs.versions.sqldelight.get()}")
            schemaOutputDirectory.set(file("src/main/sqldelight/databases"))
            verifyMigrations.set(true)
        }
    }
}

dependencies {
    implementation(project(":core"))
    implementation(libs.sqldelight.runtime)
    implementation(libs.sqldelight.android.driver)
    implementation(libs.gson)
    testImplementation(libs.sqldelight.sqlite.driver)
    testRuntimeOnly(libs.sqlite.jdbc)
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.launcher)
}
