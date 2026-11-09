import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// Generic MTG rules library (docs/specs/arcanum-automatum.md). Must not depend on any ATW module.
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
        namespace = "arcanum.parser"
        compileSdk = 36
        minSdk = 29
        compilerOptions.jvmTarget.set(JvmTarget.JVM_17)
    }
    sourceSets {
        commonMain.dependencies { api(project(":arcanum-api")) }
        commonTest.dependencies { implementation(libs.kotlin.test) }
        jvmTest.dependencies {
            implementation(libs.kotlin.test.junit5)
            implementation(libs.kotlin.reflect) // lists the sealed Keyword types for the completeness test
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
