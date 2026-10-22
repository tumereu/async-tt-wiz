import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.net.URI

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "org.asyncttwiz.android"
    compileSdk = 36
    defaultConfig {
        applicationId = "org.asyncttwiz.android"
        minSdk = 29
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures { compose = true; buildConfig = true }
    // No guessed production host. Release endpoints must use normal platform TLS.
    val origin = providers.gradleProperty("atwApiOrigin").orElse("").get()
    require(origin.isEmpty() || runCatching {
        val uri = URI(origin)
        uri.host != null && uri.rawUserInfo == null && uri.rawQuery == null && uri.rawFragment == null &&
            uri.rawPath.isNullOrEmpty() && (uri.scheme == "https" ||
                (uri.scheme == "http" && uri.host == "127.0.0.1"))
    }.getOrDefault(false)) { "atwApiOrigin must be an HTTPS origin (or isolated debug IPv4 loopback)" }
    defaultConfig { buildConfigField("String", "ACCOUNT_ORIGIN", "\"$origin\"") }
    // Explicit isolated source configuration exists only in debug; release always uses provider HTTPS.
    val catalogOrigin = providers.gradleProperty("atwCatalogTestOrigin").orElse("").get()
    require(catalogOrigin.isEmpty() || runCatching {
        val uri = URI(catalogOrigin)
        uri.scheme == "http" && uri.host == "127.0.0.1" && uri.port in 1..65535 &&
            uri.rawUserInfo == null && uri.rawPath.isNullOrEmpty() && uri.rawQuery == null && uri.rawFragment == null
    }.getOrDefault(false)) { "atwCatalogTestOrigin must be an explicit isolated IPv4 loopback origin" }
    val catalogPath = providers.gradleProperty("atwCatalogPath").orElse("/api/v5/").get()
    require(Regex("/(?:[A-Za-z0-9_-]+/)+").matches(catalogPath))
    defaultConfig {
        buildConfigField("String", "CATALOG_TEST_ORIGIN", "\"\"")
        buildConfigField("String", "CATALOG_BASE", "\"https://mtgjson.com/api/v5/\"")
    }
    buildTypes.getByName("debug") {
        buildConfigField("String", "CATALOG_TEST_ORIGIN", "\"$catalogOrigin\"")
        buildConfigField("String", "CATALOG_BASE", "\"${if (catalogOrigin.isEmpty()) "https://mtgjson.com/api/v5/" else catalogOrigin + catalogPath}\"")
    }
    val resetOrigin = providers.gradleProperty("atwResetOrigin").orElse("").get()
    val resetUri = resetOrigin.takeIf(String::isNotEmpty)?.let(::URI)
    require(resetUri == null || (resetUri.scheme == "https" && resetUri.host != null &&
        resetUri.rawUserInfo == null && resetUri.rawPath.isNullOrEmpty() && resetUri.rawQuery == null && resetUri.rawFragment == null &&
        (resetUri.port == -1 || resetUri.port in 1..65535))) { "atwResetOrigin must be an HTTPS origin" }
    defaultConfig {
        buildConfigField("String", "RESET_ORIGIN", "\"$resetOrigin\"")
        manifestPlaceholders["resetHost"] = resetUri?.host ?: "unconfigured.invalid"
    }
    buildTypes.getByName("release") {
        // The release manifest also forbids cleartext, even if misconfigured.
        buildConfigField("String", "ACCOUNT_ORIGIN", "\"${origin.takeIf { it.startsWith("https://") }.orEmpty()}\"")
    }
    // One retained fixture shared with host migration verification; never packaged in the app.
    sourceSets.getByName("androidTest").assets.srcDir("../client-storage/src/main/sqldelight/databases")
}

kotlin { compilerOptions.jvmTarget.set(JvmTarget.JVM_17) }

dependencies {
    implementation(project(":core"))
    implementation(project(":protocol"))
    implementation(project(":client-storage"))
    implementation(platform(libs.compose.bom))
    implementation(libs.activity.compose)
    implementation(libs.compose.ui)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation(libs.ktor.client.android)
    implementation(libs.serialization.json)
    testImplementation(libs.kotlin.test)
    testImplementation("junit:junit:4.13.2")
    testImplementation(libs.ktor.client.mock)
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.compose.ui.test)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.junit)
}
