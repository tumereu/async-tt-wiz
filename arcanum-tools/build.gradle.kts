import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// Developer tools for the Arcanum libraries (not a library, never shipped). Must not depend on any ATW module.
plugins {
    alias(libs.plugins.kotlin.jvm)
}

kotlin {
    jvmToolchain(21)
    compilerOptions.jvmTarget.set(JvmTarget.JVM_21)
}

dependencies {
    implementation(project(":arcanum-parser"))
    implementation(libs.gson)
}

// Counts normalised oracle-text patterns in a local, never committed MTGJSON file (AllPrintings or AtomicCards,
// .json or .json.gz): ./scripts/gradle.sh :arcanum-tools:patternFrequencies -Pmtgjson=/path/AllPrintings.json.gz
tasks.register<JavaExec>("patternFrequencies") {
    group = "arcanum"
    description = "Writes build/arcanum/pattern-frequencies.tsv from a local MTGJSON file (-Pmtgjson=<path>)."
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("arcanum.tools.PatternFrequenciesKt")
    maxHeapSize = "2g"
    val input = providers.gradleProperty("mtgjson")
    val output = layout.buildDirectory.file("arcanum/pattern-frequencies.tsv")
    doFirst { check(input.isPresent) { "Give the local MTGJSON file: -Pmtgjson=/path/AllPrintings.json.gz" } }
    argumentProviders += CommandLineArgumentProvider { listOf(input.getOrElse(""), output.get().asFile.absolutePath) }
}
