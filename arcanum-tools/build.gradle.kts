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


// Local files outside the repository the tools use; each has a default (see ParserToolPaths). The MTGJSON AllPrintings
// file is the host's pinned one (catalog/pin.properties): a tool checks it against the pin and stops without it.
fun JavaExec.parserToolPaths() {
    mapOf("mtgjson" to "arcanum.mtgjson", "cube" to "arcanum.cube", "parserOutputs" to "arcanum.parserOutputs", "setPlans" to "arcanum.setPlans")
        .forEach { (property, key) -> providers.gradleProperty(property).orNull?.let { systemProperty(key, it) } }
    systemProperty("arcanum.parserSources", rootProject.file("arcanum-parser/src/commonMain/kotlin").path)
    systemProperty("arcanum.pin", rootProject.file("catalog/pin.properties").path)
}

// Counts normalised oracle-text patterns in the pinned, never committed MTGJSON file (AllPrintings):
// ./scripts/gradle.sh :arcanum-tools:patternFrequencies
tasks.register<JavaExec>("patternFrequencies") {
    group = "arcanum"
    description = "Writes build/arcanum/pattern-frequencies.tsv from the pinned MTGJSON file."
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("arcanum.tools.PatternFrequenciesKt")
    maxHeapSize = "2g"
    parserToolPaths()
    val output = layout.buildDirectory.file("arcanum/pattern-frequencies.tsv")
    argumentProviders += CommandLineArgumentProvider { listOf(output.get().asFile.absolutePath) }
}

// Checks that every card of each supported set (src/main/resources/arcanum/tools/supported-sets.txt) is fully
// supported, from the pinned MTGJSON file (catalog/pin.properties). -Psets=LTR,BLB checks only those sets.
tasks.register<JavaExec>("setSupport") {
    group = "arcanum"
    description = "Fails listing every card of the supported sets the parser and engine don't fully support."
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("arcanum.tools.SetSupportKt")
    maxHeapSize = "2g"
    parserToolPaths()
    val sets = providers.gradleProperty("sets").orElse("")
    argumentProviders += CommandLineArgumentProvider { listOf(sets.get()) }
}

// Castability over every supported card (castability spec section 6), from the same pinned file: each mana ability pays, each cost
// reducer lowers a cost on a test board; everything listed in build/arcanum/mana-check.tsv. -Psets=LTR,BLB checks only those sets.
tasks.register<JavaExec>("manaCheck") {
    group = "arcanum"
    description = "Fails listing every mana ability of the supported sets that doesn't pay; lists every cost reducer."
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("arcanum.tools.ManaCheckKt")
    maxHeapSize = "2g"
    parserToolPaths()
    val sets = providers.gradleProperty("sets").orElse("")
    val output = layout.buildDirectory.file("arcanum/mana-check.tsv")
    argumentProviders += CommandLineArgumentProvider { listOf(output.get().asFile.absolutePath, sets.get()) }
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

// The ability shapes the tests cover (set-plans/covered-shapes.tsv): the parser's and the engine's tests once more, with their recorder on.
tasks.register<JavaExec>("coveredShapes") {
    group = "arcanum"
    description = "Runs the parser and engine tests with their recorder on and writes which ability shapes they cover."
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("arcanum.tools.CoveredShapesKt")
    parserToolPaths()
    dependsOn(":arcanum-parser:recordCoveredAbilities", ":arcanum-engine:recordCoveredAbilities")
    args(listOf("arcanum-parser", "arcanum-engine").map { rootProject.file("$it/build/arcanum/covered-abilities.tsv").path })
}

// Which clause of an oracle text doesn't read and the parser rules nearest it: --args='"<text>"'.
tasks.register<JavaExec>("parserExplain") {
    group = "arcanum"
    description = "Explains which clauses of an oracle text read and the nearest parser rules: --args='\"<text>\"'."
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("arcanum.tools.ParserExplainKt")
    parserToolPaths()
}

// A set's plan for chunk workers (set-plans/CODE/): --args=CODE, from the pinned MTGJSON file.
tasks.register<JavaExec>("setPlan") {
    group = "arcanum"
    description = "Writes a set's plan: verdicts, chunks, digest, difficult cards and audit packets (--args=CODE)."
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("arcanum.tools.SetPlanKt")
    maxHeapSize = "2g"
    parserToolPaths()
}
