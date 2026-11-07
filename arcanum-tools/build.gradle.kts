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
    implementation(project(":arcanum-engine"))
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

// Checks that every card of each supported set (src/main/resources/arcanum/tools/supported-sets.txt) is fully
// supported, from MTGJSON set files cached outside the repository (~/.cache/arcanum-sets, or -PsetCache=<dir>); fetched
// again when MTGJSON's version changes, so the first run needs a network. -Psets=LTR,BLB checks only those sets.
tasks.register<JavaExec>("setSupport") {
    group = "arcanum"
    description = "Fails listing every card of the supported sets the parser and engine don't fully support."
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("arcanum.tools.SetSupportKt")
    maxHeapSize = "2g"
    val cache = providers.gradleProperty("setCache").orElse(System.getProperty("user.home") + "/.cache/arcanum-sets")
    val sets = providers.gradleProperty("sets").orElse("")
    argumentProviders += CommandLineArgumentProvider { listOf(cache.get(), sets.get()) }
}
