import org.jetbrains.kotlin.gradle.dsl.JvmTarget

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
    buildFeatures { compose = true }
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
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.compose.ui.test)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.junit)
}
