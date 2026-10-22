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
    doFirst { check(run.isPresent) { "Use scripts/run-device-tests.py with an owned device" } }
}
