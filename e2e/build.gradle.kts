plugins { alias(libs.plugins.kotlin.jvm) }

kotlin { jvmToolchain(21) }

dependencies {
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.launcher)
    testImplementation(libs.appium.client)
    testImplementation(enforcedPlatform(libs.selenium.bom))
    testImplementation(libs.gson)
}

tasks.test { useJUnitPlatform { excludeTags("device") } }

// Explicit opt-in: an ordinary host test never starts or adopts a device.
tasks.register<Test>("deviceTest") {
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    useJUnitPlatform { includeTags("device") }
    val run = providers.environmentVariable("ATW_DEVICE_RUN")
    inputs.property("ownedRun", run.orElse("missing"))
    // A retained phase observes live UI/server state, not reproducible Gradle inputs.
    // Reusing the run directory must never return an old JUnit pass without execution.
    outputs.upToDateWhen { false }
    outputs.doNotCacheIf("Native device state is live") { true }
    doFirst { check(run.isPresent) { "Use scripts/run-device-tests.py with an owned device" } }
}
