import java.util.Properties

plugins {
    alias(libs.plugins.kotlin.jvm)
    application
}

kotlin { jvmToolchain(21) }

val databaseImages = Properties().apply { file("database-images.properties").inputStream().use { load(it) } }
val postgresImage = databaseImages.getProperty("postgres")
val generatedJooq = layout.buildDirectory.dir("generated-src/jooq")

// Bootstrap only the migration code, before main compilation needs generated fields.
// These identical sources are compiled into main too; schemaTools is never packaged.
val schemaTools = sourceSets.create("schemaTools") {
    java.srcDirs("src/schemaTools/java", "src/main/java")
    java.include(
        "org/asyncttwiz/server/tools/*.java",
        "org/asyncttwiz/server/database/DatabaseSettings.java",
        "org/asyncttwiz/server/database/FoundationMigrations.java",
    )
    resources.srcDir("src/main/resources")
}

dependencies {
    testImplementation(project(":scale-fixtures"))
    implementation(project(":core"))
    implementation(project(":protocol"))
    // I-D54: the server runs the Arcanum engine (the authoritative engine state of each game).
    implementation(project(":arcanum-api"))
    implementation(project(":arcanum-engine"))
    // Tests only: the engine's invented cube cards are parsed from their oracle text (I-D54 spike, event model tests).
    testImplementation(project(":arcanum-parser"))
    implementation(libs.flyway.core)
    implementation(libs.flyway.postgresql)
    implementation(libs.postgresql)
    implementation(libs.jooq) { version { strictly(libs.versions.jooq.get()) } }
    implementation(platform(libs.spring.boot.bom))
    implementation("com.zaxxer:HikariCP")
    implementation(libs.spring.boot)
    implementation(libs.spring.boot.tomcat)
    implementation(libs.spring.security.web)
    implementation(libs.spring.security.crypto)
    implementation(libs.icu)
    implementation(libs.bouncycastle)
    implementation(libs.serialization.json)
    implementation(libs.angus.mail)
    add(schemaTools.implementationConfigurationName, libs.flyway.core)
    add(schemaTools.implementationConfigurationName, libs.flyway.postgresql)
    add(schemaTools.implementationConfigurationName, libs.postgresql)
    add(schemaTools.implementationConfigurationName, libs.jooq.codegen)
    add(schemaTools.implementationConfigurationName, libs.testcontainers.postgresql)
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.testcontainers.postgresql)
    testRuntimeOnly(libs.junit.launcher)
}

application { mainClass.set("org.asyncttwiz.server.ServiceCommand") }

// The engine's invented cube cards by name, shared as source (their texts live only there), as :automatum does.
kotlin.sourceSets.named("test") { kotlin.srcDir("../arcanum-engine/src/commonTest/kotlin/arcanum/engine/cards") }

// I-D54: the engine spike's workloads as one runnable jar (EngineBenchmark.kt), to measure the engine on another machine
// (the Pi) without Gradle or a database: the spike's classes, the invented cube cards, only the libraries they use, and
// the seeded games' inputs recorded here (the jar replays them instead of running the slow random player).
val engineBenchmarkGames = tasks.register<JavaExec>("engineBenchmarkGames") {
    description = "Records the engine benchmark's seeded games (their inputs) for engineBenchmarkJar."
    classpath = sourceSets.test.get().runtimeClasspath
    mainClass.set("org.asyncttwiz.server.game.EngineBenchmarkKt")
    maxHeapSize = "1g"
    val games = layout.buildDirectory.file("benchmark/games/engine-benchmark-games.json")
    outputs.file(games)
    args("record", games.get().asFile.absolutePath)
}
tasks.register<Jar>("engineBenchmarkJar") {
    group = "verification"
    description = "Writes build/benchmark/engine-benchmark.jar: java -jar it to print the engine spike's timings."
    archiveFileName.set("engine-benchmark.jar")
    destinationDirectory.set(layout.buildDirectory.dir("benchmark"))
    manifest { attributes("Main-Class" to "org.asyncttwiz.server.game.EngineBenchmarkKt") }
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    from(sourceSets.test.map { it.output.classesDirs }) {
        include("org/asyncttwiz/server/game/EngineOnServerSpikeTest*", "org/asyncttwiz/server/game/EngineBenchmark*")
        include("arcanum/engine/cards/**")
    }
    from(engineBenchmarkGames)
    val prefixes = listOf("arcanum-", "protocol", "core", "kotlin-stdlib", "kotlinx-serialization", "kotlinx-collections")
    val libraries = configurations.testRuntimeClasspath.map { classpath ->
        classpath.filter { file -> prefixes.any { file.name.startsWith(it) } }.map { zipTree(it) }
    }
    from(libraries) { exclude("META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA", "**/module-info.class") }
}

val generateJooq = tasks.register<JavaExec>("generateJooq") {
    group = "code generation"
    description = "Migrate an owned disposable PostgreSQL17 and generate real server-only jOOQ bindings"
    classpath = schemaTools.runtimeClasspath
    mainClass.set("org.asyncttwiz.server.tools.GenerateJooq")
    args(postgresImage, generatedJooq.get().asFile.absolutePath)
    inputs.files(fileTree("src/main/resources/db/migration"), fileTree("src/schemaTools/java"), fileTree("src/main/java"))
    inputs.property("postgresImage", postgresImage)
    outputs.dir(generatedJooq)
}

sourceSets.main { java.srcDir(generatedJooq) }
tasks.named("compileKotlin") { dependsOn(generateJooq) }
tasks.named("compileJava") { dependsOn(generateJooq) }

tasks.register<JavaExec>("verifyJooqGeneration") {
    group = "verification"
    description = "Regenerate twice from independent migrated databases and compare every generated byte"
    classpath = schemaTools.runtimeClasspath
    mainClass.set("org.asyncttwiz.server.tools.GenerateJooq")
    args(postgresImage, layout.buildDirectory.dir("jooq-reproducibility").get().asFile.absolutePath, "verify")
}

tasks.register<JavaExec>("migrate") {
    group = "database"
    description = "Explicit forward-only migration using DB_URL, DB_USER and DB_PASSWORD_FILE"
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("org.asyncttwiz.server.database.MigrationCommand")
    args("migrate")
}

tasks.test {
    exclude("**/ComposeIsolationTest.class")
    // Each fork is its own JVM with its own owned PostgreSQL container (FreshDatabase), so
    // classes in different forks never share a database. Gradle caps this at max-workers.
    maxParallelForks = 4
}

// Image-dependent host qualification is explicit, not a hidden prerequisite for
// ordinary server integration tests or building the image it needs to exercise.
tasks.register<Test>("composeIsolationTest") {
    group = "verification"
    description = "Qualify owned Compose reset alongside an independent live Testcontainers database"
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    include("**/ComposeIsolationTest.class")
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    systemProperty("asyncttwiz.postgresImage", postgresImage)
    systemProperty("asyncttwiz.root", rootDir.absolutePath)
    // One lock file for every fork of every test task: container lifecycles never overlap.
    systemProperty("asyncttwiz.containerLock", layout.buildDirectory.file("tmp/owned-postgres-lifecycle.lock").get().asFile.absolutePath)
    // Launch the real installed executable, not a second test-only migration CLI.
    dependsOn(tasks.installDist)
    systemProperty("asyncttwiz.migrationExecutable", layout.buildDirectory.file("install/server/bin/server").get().asFile.absolutePath)
    // ProductionUpgradeTest compares against the committed fixture in src/test/resources/upgrade; -PupgradeFixture=record rewrites it.
    val recordUpgradeFixture = providers.gradleProperty("upgradeFixture").orNull == "record"
    systemProperty("asyncttwiz.recordUpgradeFixture", recordUpgradeFixture.toString())
    inputs.property("upgradeFixtureMode", recordUpgradeFixture)
    testLogging { events("passed", "skipped", "failed"); showStandardStreams = true }
    // -Patw.engineSpike=true runs the I-D54 engine cost spike (EngineOnServerSpikeTest: prints timings, asserts none).
    providers.gradleProperty("atw.engineSpike").orNull?.let { systemProperty("atw.engineSpike", it) }
}
