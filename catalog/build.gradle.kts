import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// The card catalog (docs/specs/web-client.md section 3): the trimmed catalog's format and reader (common code, read by
// the server and the clients; `src/shared`: its files and the card records the app indexes it into, JVM and Android)
// and, on the JVM only, the MTGJSON import (`src/mtgjson`) and the catalog build with its support marks, run at release
// (`catalogCommand`). The app's tests compile `src/mtgjson` into their own sources to index invented MTGJSON fixtures.
plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kmp.library)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    jvmToolchain(21)
    jvm {
        compilerOptions.jvmTarget.set(JvmTarget.JVM_21)
        testRuns["test"].executionTask.configure {
            useJUnitPlatform()
            maxHeapSize = "1g"
            systemProperty("catalog.fixture", rootProject.file("tests/fixtures/card-catalog").absolutePath)
        }
    }
    android {
        namespace = "org.asyncttwiz.catalog"
        compileSdk = 36
        minSdk = 29
        compilerOptions.jvmTarget.set(JvmTarget.JVM_17)
    }
    sourceSets {
        commonMain.dependencies {
            api(project(":protocol"))
            implementation(libs.serialization.json)
        }
        jvmMain {
            kotlin.srcDir("src/shared/kotlin")
            kotlin.srcDir("src/mtgjson/kotlin")
            dependencies {
                api(libs.gson)
                // The support marks (section 3.8): Automatum's check of each listed card, at catalog build only.
                implementation(project(":automatum"))
            }
        }
        androidMain {
            kotlin.srcDir("src/shared/kotlin")
            dependencies { api(libs.gson) }
        }
        commonTest.dependencies { implementation(libs.kotlin.test) }
        jvmTest.dependencies {
            implementation(libs.kotlin.test.junit5)
            implementation(project.dependencies.platform(libs.junit.bom))
            runtimeOnly(libs.junit.jupiter)
            runtimeOnly(libs.junit.launcher)
        }
    }
}

val catalogMain = kotlin.jvm().compilations.getByName("main")

// The catalog command (CatalogCommand.kt): build the catalog, verify the pinned file, move the pin (scripts/catalog-pin.sh, the
// release, the dev stack). ./scripts/gradle.sh :catalog:catalogCommand --args="build --out <dir>"
tasks.register<JavaExec>("catalogCommand") {
    group = "catalog"
    description = "Builds the card catalog, verifies the pinned MTGJSON file or moves the pin (--args=\"build|verify|move ...\")."
    classpath = files(catalogMain.output.allOutputs, catalogMain.runtimeDependencyFiles)
    mainClass.set("org.asyncttwiz.catalog.CatalogCommandKt")
    maxHeapSize = "4g"
    workingDir = rootProject.projectDir
    systemProperty("atw.catalogRoot", rootProject.projectDir.absolutePath)
}

// The invented test cards' catalog (tests/fixtures/card-catalog) for the server tests and the e2e stack: build/test-cards-catalog.
tasks.register<JavaExec>("testCardsCatalog") {
    group = "catalog"
    description = "Builds the test cards' card catalog into build/test-cards-catalog."
    classpath = files(catalogMain.output.allOutputs, catalogMain.runtimeDependencyFiles)
    mainClass.set("org.asyncttwiz.catalog.CatalogCommandKt")
    val fixture = rootProject.file("tests/fixtures/card-catalog")
    val output = layout.buildDirectory.dir("test-cards-catalog")
    inputs.dir(fixture)
    outputs.dir(output)
    args("build", "--no-pin", "--out", output.get().asFile.absolutePath, "--file", "$fixture/AllPrintings.json",
        "--sets", "$fixture/supported-sets.txt", "--cards", "$fixture/supported-cards.txt")
}
