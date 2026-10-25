import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kmp.library)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    jvmToolchain(21)
    jvm {
        compilerOptions.jvmTarget.set(JvmTarget.JVM_21)
        testRuns["test"].executionTask.configure { useJUnitPlatform() }
    }
    android {
        namespace = "org.asyncttwiz.protocol"
        compileSdk = 36
        minSdk = 29
        compilerOptions.jvmTarget.set(JvmTarget.JVM_17)
    }
    sourceSets {
        commonMain.dependencies {
            api(project(":core"))
            implementation(libs.serialization.json)
            implementation("org.jetbrains.kotlinx:kotlinx-collections-immutable:0.4.0")
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(project(":scale-fixtures"))
        }
        jvmTest.dependencies {
            implementation(libs.kotlin.test.junit5)
            implementation(project.dependencies.platform(libs.junit.bom))
            runtimeOnly(libs.junit.jupiter)
            runtimeOnly(libs.junit.launcher)
        }
    }
}
