import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kmp.library)
}

kotlin {
    jvmToolchain(21)
    jvm {
        compilerOptions.jvmTarget.set(JvmTarget.JVM_21)
        testRuns["test"].executionTask.configure { useJUnitPlatform() }
    }
    android {
        namespace = "org.asyncttwiz.core"
        compileSdk = 36
        minSdk = 29
        compilerOptions.jvmTarget.set(JvmTarget.JVM_17)
    }
    sourceSets {
        commonTest.dependencies { implementation(libs.kotlin.test) }
        jvmTest.dependencies {
            implementation(libs.kotlin.test.junit5)
            implementation(project.dependencies.platform(libs.junit.bom))
            runtimeOnly(libs.junit.jupiter)
            runtimeOnly(libs.junit.launcher)
        }
    }
}
