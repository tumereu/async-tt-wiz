import java.util.Properties

// End-to-end tests (AGENTS.md "End-to-end coverage"): a real server process on a real PostgreSQL (Docker), and two
// instances of the app's real code (each player's controllers, sockets and Compose UI, in one Robolectric JVM) talking
// to it over real sockets. The sources (src/test/kotlin) compile with :androidApp's JVM unit tests, which lets them use
// the app's internal classes; the ordinary test tasks leave them out and only this task runs them.
//   ./scripts/gradle.sh :e2e:test                                   all (the prod release gate)
//   ./scripts/gradle.sh :e2e:test --tests '*CoreLoopTest*'          one class (or '*CoreLoopTest.theCoreLoop')
plugins { base }

evaluationDependsOn(":androidApp")
evaluationDependsOn(":server")

val postgresImage = Properties().apply { rootProject.file("server/database-images.properties").inputStream().use { load(it) } }
    .getProperty("postgres")

tasks.register<Test>("test") {
    group = "verification"
    description = "End-to-end tests: two app instances against a real server and PostgreSQL (slow; the prod release gate)."
    val unit = project(":androidApp").tasks.named<Test>("testDebugUnitTest").get()
    testClassesDirs = unit.testClassesDirs
    classpath = unit.classpath
    javaLauncher.set(unit.javaLauncher)
    jvmArgs(unit.jvmArgs.orEmpty())
    jvmArgumentProviders.addAll(unit.jvmArgumentProviders)
    systemProperties(unit.systemProperties.filterKeys { it != "java.io.tmpdir" })
    workingDir = project(":androidApp").projectDir
    include("org/asyncttwiz/e2e/**")
    // One JVM: it starts one server and database for all the tests.
    maxParallelForks = 1
    val install = project(":server").tasks.named<Sync>("installDist")
    dependsOn(install)
    systemProperty("atw.e2e.server", install.get().destinationDir.resolve("bin/server").absolutePath)
    // The server serves the invented test cards' card catalog (tests/fixtures/card-catalog)
    dependsOn(":catalog:testCardsCatalog")
    systemProperty("atw.e2e.catalogDir", rootProject.file("catalog/build/test-cards-catalog").absolutePath)
    // The server also serves the web client's build (WEB_APP_DIR), which WebSmokeTest loads in Chrome
    dependsOn(":webApp:webDist")
    systemProperty("atw.e2e.webDir", rootProject.file("webApp/build/web").absolutePath)
    // WebSmokeTest drives the installed Google Chrome: Playwright downloads no browser of its own
    environment("PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD", "1")
    systemProperty("atw.e2e.postgresImage", postgresImage)
    systemProperty("atw.e2e.output", layout.buildDirectory.dir("e2e").get().asFile.absolutePath)
    reports.html.outputLocation.set(layout.buildDirectory.dir("reports/tests/test"))
    reports.junitXml.outputLocation.set(layout.buildDirectory.dir("test-results/test"))
    binaryResultsDirectory.set(layout.buildDirectory.dir("test-results/test/binary"))
    // The stack is outside Gradle's view: always run when asked.
    outputs.upToDateWhen { false }
    testLogging {
        events("failed", "skipped")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
        showStandardStreams = false
    }
}
