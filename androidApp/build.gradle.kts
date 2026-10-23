import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.net.URI
import groovy.json.JsonSlurper

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

val releaseMetadata = JsonSlurper().parseText(providers.fileContents(
    rootProject.layout.projectDirectory.file("release/version.json")).asText.get()) as Map<*, *>
require(releaseMetadata["manifestVersion"] == 1 && releaseMetadata["applicationId"] == "org.asyncttwiz.android")
val versionAllocations = releaseMetadata["allocations"] as List<*>
val allocatedVersions = versionAllocations.map { it as Map<*, *> }
val allocatedCodes = allocatedVersions.map { it["versionCode"] as Int }
require(allocatedCodes.isNotEmpty() && allocatedCodes.all { it in 1..2100000000 } &&
    allocatedCodes.zipWithNext().all { (previous, next) -> next > previous }) { "Version allocations must increase; never reuse an installed versionCode" }
val allocatedVersion = allocatedVersions.last()
val allocatedVersionName = allocatedVersion["versionName"] as String
require(Regex("[0-9]+\\.[0-9]+\\.[0-9]+(?:[-+][A-Za-z0-9.-]+)?").matches(allocatedVersionName))

// Only external file references/alias are configuration inputs, never secret values in Git or command lines.
fun externalSigningInputs(kind: String, environmentKind: String): List<String>? {
    val fields = listOf("StoreFile" to "STORE_FILE", "StorePasswordFile" to "STORE_PASSWORD_FILE",
        "KeyAlias" to "KEY_ALIAS", "KeyPasswordFile" to "KEY_PASSWORD_FILE")
    val inputs = fields.map { (property, environment) ->
        providers.gradleProperty("atw$kind$property")
            .orElse(providers.environmentVariable("ATW_${environmentKind}_$environment")).orElse("").get()
    }
    require(inputs.all(String::isEmpty) || inputs.none(String::isEmpty)) { "Supply all four $kind signing references, or none" }
    return inputs.takeIf { it.none(String::isEmpty) }
}
fun externalSigningFile(reference: String): java.io.File = rootProject.file(reference).canonicalFile.also {
    require(it.isFile && !it.toPath().startsWith(rootProject.projectDir.canonicalFile.toPath())) {
        "Signing material must be an existing operator-owned file outside the checkout"
    }
}
fun externalSigningPassword(reference: String): String = providers.fileContents(
    layout.file(providers.provider { externalSigningFile(reference) })).asText.get()
    .removeSuffix("\n").removeSuffix("\r").also {
        require(it.isNotEmpty() && '\n' !in it && '\r' !in it) { "Signing password files must contain one nonempty line" }
    }
val releaseSigningInputs = externalSigningInputs("Release", "RELEASE")
val testSigningInputs = externalSigningInputs("Test", "TEST")

val fcmProperties = listOf("atwFcmApplicationId", "atwFcmProject", "atwFcmApiKey", "atwFcmSender")
val fcmConfiguredCount = fcmProperties.count { providers.gradleProperty(it).orElse("").get().isNotEmpty() }
require(fcmConfiguredCount == 0 || fcmConfiguredCount == fcmProperties.size) { "Supply all four public FCM identifiers, or none" }
val fcmEnabled = fcmConfiguredCount == fcmProperties.size

abstract class NonFcmSources : Sync() {
    @get:OutputDirectory abstract val outputDirectory: DirectoryProperty
}

android {
    namespace = "org.asyncttwiz.android"
    compileSdk = 36
    val defaultDebugStore = signingConfigs.getByName("debug").storeFile?.canonicalFile
    val externalReleaseSigning = releaseSigningInputs?.let { inputs ->
        val store = externalSigningFile(inputs[0])
        require(store != defaultDebugStore && (testSigningInputs == null || store != externalSigningFile(testSigningInputs[0]))) {
            "Release signing must not use the debug/test keystore"
        }
        signingConfigs.create("externalRelease") {
            storeFile = store
            storePassword = externalSigningPassword(inputs[1])
            keyAlias = inputs[2]
            keyPassword = externalSigningPassword(inputs[3])
        }
    }
    val externalTestSigning = testSigningInputs?.let { inputs ->
        signingConfigs.create("externalTest") {
            storeFile = externalSigningFile(inputs[0])
            storePassword = externalSigningPassword(inputs[1])
            keyAlias = inputs[2]
            keyPassword = externalSigningPassword(inputs[3])
        }
    }
    // Distinct configuration names keep both dependency graphs strictly locked in
    // the same checked-in lockfile. Normal builds never resolve the optional SDK.
    if (fcmEnabled) {
        flavorDimensions += "delivery"
        productFlavors.create("fcm") { dimension = "delivery" }
    }
    defaultConfig {
        applicationId = "org.asyncttwiz.android"
        manifestPlaceholders["fcmEnabled"] = fcmEnabled.toString()
        minSdk = 29
        targetSdk = 36
        versionCode = allocatedCodes.last()
        versionName = allocatedVersionName
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures { compose = true; buildConfig = true }
    testOptions.unitTests.isIncludeAndroidResources = true
    // No guessed production host. Release endpoints must use normal platform TLS.
    val origin = providers.gradleProperty("atwApiOrigin").orElse("").get()
    require(origin.isEmpty() || runCatching {
        val uri = URI(origin)
        uri.host != null && uri.rawUserInfo == null && uri.rawQuery == null && uri.rawFragment == null &&
            uri.rawPath.isNullOrEmpty() && (uri.scheme == "https" ||
                (uri.scheme == "http" && uri.host == "127.0.0.1"))
    }.getOrDefault(false)) { "atwApiOrigin must be an HTTPS origin (or isolated debug IPv4 loopback)" }
    defaultConfig { buildConfigField("String", "ACCOUNT_ORIGIN", "\"$origin\"") }
    // Public Firebase application identifiers only, supplied explicitly. No google-services plugin/file.
    defaultConfig {
        mapOf("FCM_APPLICATION_ID" to "atwFcmApplicationId", "FCM_PROJECT" to "atwFcmProject",
            "FCM_API_KEY" to "atwFcmApiKey", "FCM_SENDER" to "atwFcmSender").forEach { (field, property) ->
            val value = providers.gradleProperty(property).orElse("").get()
            require(value.all { it.isLetterOrDigit() || it in ":_-" })
            buildConfigField("String", field, "\"$value\"")
        }
    }
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
        buildConfigField("String", "ART_TEST_ORIGIN", "\"\"")
        buildConfigField("String", "ART_BASE", "\"https://api.scryfall.com/cards/\"")
        val contact = providers.gradleProperty("atwArtContact").orElse("").get()
        require(contact.length <= 200 && contact.all { it.code in 32..126 && it != '"' && it != '\\' })
        buildConfigField("String", "ART_CONTACT", "\"$contact\"")
    }
    buildTypes.getByName("debug") {
        // Without an override, preserve AGP's existing persistent local debug signer.
        if (externalTestSigning != null) signingConfig = externalTestSigning
        buildConfigField("String", "CATALOG_TEST_ORIGIN", "\"$catalogOrigin\"")
        buildConfigField("String", "CATALOG_BASE", "\"${if (catalogOrigin.isEmpty()) "https://mtgjson.com/api/v5/" else catalogOrigin + catalogPath}\"")
        buildConfigField("String", "ART_TEST_ORIGIN", "\"$catalogOrigin\"")
        buildConfigField("String", "ART_BASE", "\"${if (catalogOrigin.isEmpty()) "https://api.scryfall.com/cards/" else catalogOrigin + catalogPath + "scryfall/cards/"}\"")
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
        // Missing external inputs deliberately produces an unsigned APK, never a debug-signed release.
        signingConfig = externalReleaseSigning
        // The release manifest also forbids cleartext, even if misconfigured.
        buildConfigField("String", "ACCOUNT_ORIGIN", "\"${origin.takeIf { it.startsWith("https://") }.orEmpty()}\"")
    }
    // One retained fixture shared with host migration verification; never packaged in the app.
    sourceSets.getByName("androidTest").assets.srcDir("../client-storage/src/main/sqldelight/databases")
}

kotlin {
    compilerOptions.jvmTarget.set(JvmTarget.JVM_17)
}

// AGP's built-in Kotlin directory DSL has no file exclusions. Keep the optional SDK
// adapter out of the normal APK entirely, rather than shipping missing SDK classes.
if (!fcmEnabled) {
    android.sourceSets.getByName("main").kotlin.directories.clear()
    val nonFcmSources = tasks.register<NonFcmSources>("nonFcmSources") {
        from("src/main/kotlin") { exclude("**/ActivityPushService.kt") }
        outputDirectory.set(layout.buildDirectory.dir("generated/nonFcm"))
        into(outputDirectory)
    }
    androidComponents.onVariants { variant ->
        variant.sources.kotlin!!.addGeneratedSourceDirectory(nonFcmSources) { it.outputDirectory }
    }
}

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
    implementation(libs.okhttp)
    implementation(libs.work.runtime)
    if (fcmEnabled) implementation(libs.firebase.messaging)
    implementation(libs.serialization.json)
    implementation(libs.coil.compose)
    testImplementation(libs.kotlin.test)
    testImplementation(libs.compose.ui.test)
    testImplementation(libs.robolectric)
    testImplementation(libs.roborazzi)
    debugImplementation(libs.compose.ui.test.manifest)
    testImplementation("junit:junit:4.13.2")
    testImplementation(libs.ktor.client.mock)
    testImplementation(libs.sqldelight.sqlite.driver)
    testRuntimeOnly(libs.sqlite.jdbc)
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.compose.ui.test)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.junit)
}

tasks.withType<Test>().configureEach {
    systemProperty("roborazzi.test.record", "true")
    systemProperty("robolectric.dependency.repo.url", "https://repo.maven.apache.org/maven2")
}
