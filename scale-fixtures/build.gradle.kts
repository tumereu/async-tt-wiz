import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kmp.library)
}

kotlin {
    jvmToolchain(21)
    jvm { compilerOptions.jvmTarget.set(JvmTarget.JVM_21) }
    android {
        namespace = "org.asyncttwiz.scalefixtures"
        compileSdk = 36
        minSdk = 29
        compilerOptions.jvmTarget.set(JvmTarget.JVM_17)
    }
    sourceSets.commonMain.dependencies { api(project(":protocol")) }
}
