import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// Developer tools for the Arcanum libraries (not a library, never shipped). Must not depend on any ATW module.
plugins {
    alias(libs.plugins.kotlin.jvm)
}

kotlin {
    jvmToolchain(21)
    compilerOptions.jvmTarget.set(JvmTarget.JVM_21)
}

// A copy of the parser's sources with a regex that counts its matches (RegexProbe), for the rule usage in parserMetrics. It
// sits in front of the real parser on that one task's classpath; nothing else, and nothing shipped, sees it.
val shadowParser = sourceSets.create("shadowParser")
kotlin.sourceSets["shadowParser"].kotlin.srcDir(rootProject.file("arcanum-parser/src/commonMain/kotlin"))

dependencies {
    implementation(project(":arcanum-parser"))
    implementation(project(":arcanum-engine"))
    implementation(libs.gson)
    "shadowParserImplementation"(project(":arcanum-engine")) // for arcanum-api: the sources stand in for the parser
    "shadowParserImplementation"(sourceSets["main"].output)
    testImplementation(libs.kotlin.test.junit5)
    testImplementation(platform(libs.junit.bom))
    testRuntimeOnly(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.launcher)
}

tasks.test { useJUnitPlatform() }

// Local files outside the repository the parser migration tools use; each has a default (see ParserToolPaths).
fun JavaExec.parserToolPaths() {
    mapOf("mtgjson" to "arcanum.mtgjson", "cube" to "arcanum.cube", "parserOutputs" to "arcanum.parserOutputs", "setCache" to "arcanum.setCache")
        .forEach { (property, key) -> providers.gradleProperty(property).orNull?.let { systemProperty(key, it) } }
    systemProperty("arcanum.parserSources", rootProject.file("arcanum-parser/src/commonMain/kotlin").path)
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

// Castability over every supported card (castability spec §6), from the same cached set files: each mana ability pays, each cost reducer
// lowers a cost on a test board; everything listed in build/arcanum/mana-check.tsv. -Psets=LTR,BLB checks only those sets.
tasks.register<JavaExec>("manaCheck") {
    group = "arcanum"
    description = "Fails listing every mana ability of the supported sets that doesn't pay; lists every cost reducer."
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("arcanum.tools.ManaCheckKt")
    maxHeapSize = "2g"
    val cache = providers.gradleProperty("setCache").orElse(System.getProperty("user.home") + "/.cache/arcanum-sets")
    val sets = providers.gradleProperty("sets").orElse("")
    val output = layout.buildDirectory.file("arcanum/mana-check.tsv")
    argumentProviders += CommandLineArgumentProvider { listOf(cache.get(), output.get().asFile.absolutePath, sets.get()) }
}

// The parser migration's gate: --args=snapshot writes a canonical model of every local card outside the repository, and the
// default (--args=compare) compares one with the current parser. Fails on a change in a supported set or a cube card and on a
// card that stops being read; lists what changes on held-out cards.
tasks.register<JavaExec>("parserDiff") {
    group = "arcanum"
    description = "Snapshots every card's parsed model (--args=snapshot) or compares a snapshot with the current parser."
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("arcanum.tools.ParserDiffKt")
    maxHeapSize = "3g"
    parserToolPaths()
}

// What the parser migration is judged by: perturbation, grids, held-out cards read, regex count, rule usage. --args="--set CODE" is
// instead a cold read of one set.
tasks.register<JavaExec>("parserMetrics") {
    group = "arcanum"
    description = "Prints the parser's perturbation, grids, held-out reads, regex count and rule usage; --args=\"--set CODE\" reads one set cold."
    classpath = shadowParser.output + sourceSets["main"].runtimeClasspath
    mainClass.set("arcanum.tools.ParserMetricsKt")
    maxHeapSize = "3g"
    parserToolPaths()
}
