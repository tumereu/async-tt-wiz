import org.gradle.api.artifacts.dsl.LockMode
import org.gradle.api.artifacts.result.UnresolvedDependencyResult
import org.gradle.api.artifacts.component.ModuleComponentIdentifier

buildscript {
    configurations.getByName("classpath").resolutionStrategy.activateDependencyLocking()
}

plugins {
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.android.kmp.library) apply false
    alias(libs.plugins.sqldelight) apply false
}

allprojects {
    group = "org.asyncttwiz"
    version = "0.1.0"
    dependencyLocking {
        lockAllConfigurations()
        lockMode.set(LockMode.STRICT)
    }
}

// Resolve every configuration, including tooling/lint and both Android variants,
// when intentionally refreshing the checked-in locks. Ordinary builds never write locks.
tasks.register("resolveAndLockAll") {
    notCompatibleWithConfigurationCache("Explicit dependency maintenance across projects")
    doLast {
        require(gradle.startParameter.isWriteDependencyLocks) { "Use --write-locks" }
        allprojects.forEach { project ->
            project.configurations.filter { it.isCanBeResolved }.forEach { configuration ->
                logger.lifecycle("Locking ${project.path}:${configuration.name}")
                // AGP resolves artifacts through typed views. Resolving raw files
                // here is ambiguous for Android project variants; locks need the
                // complete dependency graph, not an untyped artifact selection.
                configuration.incoming.resolutionResult.allDependencies.forEach { dependency ->
                    if (dependency is UnresolvedDependencyResult) throw dependency.failure
                }
            }
        }
    }
}

tasks.register("verifyModuleBoundaries") {
    notCompatibleWithConfigurationCache("Examines the configured dependency graph")
    doLast {
        val expected = mapOf(
            ":core" to emptySet(),
            // I-D54: a game's state is the Arcanum engine's state, so protocol and the server use the engine.
            ":protocol" to setOf(":core", ":arcanum-engine", ":arcanum-parser"),
            ":client-storage" to setOf(":core", ":protocol", ":arcanum-parser"),
            ":androidApp" to setOf(":core", ":protocol", ":client-storage", ":automatum"),
            ":server" to setOf(":core", ":protocol", ":arcanum-api", ":arcanum-engine"),
            // Arcanum libraries are generic MTG code: they never depend on ATW modules.
            ":arcanum-api" to emptySet(),
            ":arcanum-parser" to setOf(":arcanum-api"),
            ":arcanum-engine" to setOf(":arcanum-api"),
            ":arcanum-tools" to setOf(":arcanum-parser"),
            // ATW's side of the integration: depends on both sides; nothing in Arcanum depends on it.
            ":automatum" to setOf(":protocol", ":arcanum-api", ":arcanum-engine"),
        )
        // Project dependencies allowed only in test configurations: these tests parse the invented cube cards.
        val testOnly = mapOf(
            ":arcanum-engine" to setOf(":arcanum-parser"), ":automatum" to setOf(":arcanum-parser"),
            ":androidApp" to setOf(":arcanum-parser"), ":protocol" to setOf(":arcanum-parser"),
            ":server" to setOf(":arcanum-parser"),
        )
        subprojects.forEach { project ->
            val allowedInTests = testOnly[project.path].orEmpty()
            val actual = project.configurations.flatMap { configuration ->
                val isTest = configuration.name.contains("test", ignoreCase = true)
                configuration.dependencies.withType<ProjectDependency>().mapNotNull { dependency ->
                    if (dependency.path == project.path) {
                        // AGP adds the app-under-test to instrumentation's graph.
                        check(isTest)
                        null
                    } else if (isTest && dependency.path in allowedInTests) null else dependency.path
                }
            }.toSet()
            check(actual == expected.getValue(project.path)) { "${project.path}: unexpected dependencies $actual" }
            logger.lifecycle("PASS ${project.path} -> ${actual.sorted()}")
        }
        listOf("jvmRuntimeClasspath", "androidRuntimeClasspath").forEach { name ->
            val components = project(":core").configurations.getByName(name)
                .incoming.resolutionResult.allComponents.mapNotNull { it.id as? ModuleComponentIdentifier }
            check(components.all {
                it.group == "org.jetbrains.kotlin" || (it.group == "org.jetbrains" && it.module == "annotations")
            }) { "Platform/framework dependency leaked into :core $name: $components" }
            logger.lifecycle("PASS :core $name contains only Kotlin/annotations: $components")
        }
        fileTree("core/src").matching { include("**/*.kt") }.forEach { source ->
            val imports = source.readLines().filter { it.startsWith("import ") }
            check(imports.all { it.startsWith("import kotlin.") }) { "Non-Kotlin import in shared semantics: $source" }
        }
        logger.lifecycle("PASS semantic source imports contain no generated database/UI/server types")
        val androidRuntime = project(":androidApp").configurations.getByName("debugRuntimeClasspath")
            .incoming.resolutionResult.allComponents.mapNotNull { it.id as? ModuleComponentIdentifier }
        check(androidRuntime.none { it.group.startsWith("com.google.android.gms") || it.group.startsWith("com.google.firebase") })
        logger.lifecycle("PASS normal Android runtime has no mandatory GMS/Firebase dependency")
    }
}

// The feature catalog (docs/features) is the list of behaviours the owner wants kept. This keeps its IDs unique and in
// the right file, and fails when a test, a catalog entry or the backlog cites an ID that no longer exists, so removing
// an entry forces a look at the tests that protect it. Runs before every test task.
val checkFeatureCatalog = tasks.register("checkFeatureCatalog") {
    // Hidden files (e.g. a .review.md the owner is filling in) are scratch notes, not catalog.
    val catalog = fileTree("docs/features") { include("*.md"); exclude("README.md", ".*") }
    val backlog = file("docs/backlog.md")
    val tests = fileTree(".") {
        include("*/src/*Test/**/*.kt", "*/src/test/**/*.kt")
        exclude("**/build/**")
    }
    inputs.files(catalog, backlog, tests).withPathSensitivity(PathSensitivity.RELATIVE)
    val marker = layout.buildDirectory.file("feature-catalog/checked")
    outputs.file(marker)
    val root = rootDir
    doLast {
        val prefixes = mapOf("table.md" to setOf("TBL"), "zones.md" to setOf("ZON"),
            "turns-combat-stack.md" to setOf("TRN", "CMB", "STK"), "history.md" to setOf("HIS"),
            "lobby.md" to setOf("LOB"), "draft.md" to setOf("DRF"), "rules.md" to setOf("SYS"))
        val id = Regex("""\b(?:${prefixes.values.flatten().joinToString("|")})-\d{3}\b""")
        val entry = Regex("""^- \*\*([A-Z]{3}-\d{3})\*\* \S""")
        val problems = mutableListOf<String>()
        val defined = mutableMapOf<String, String>()
        catalog.files.sortedBy { it.name }.forEach { file ->
            val allowed = prefixes[file.name] ?: emptySet<String>().also { problems += "${file.name}: not a known catalog file" }
            file.readLines().forEach { line ->
                val found = entry.find(line)?.groupValues?.get(1) ?: return@forEach
                if (found.substringBefore('-') !in allowed) problems += "${file.name}: $found has the wrong prefix for this file"
                defined.put(found, file.name)?.let { problems += "$found is defined in both $it and ${file.name}" }
            }
        }
        fun cited(file: File) = file.readLines().withIndex().flatMap { (index, line) ->
            id.findAll(line).map { it.value to "${file.relativeTo(root)}:${index + 1}" }.toList()
        }
        (catalog.files + backlog + tests.files).flatMap(::cited).forEach { (cite, where) ->
            if (cite !in defined) problems += "$where cites $cite, which is not in docs/features"
        }
        check(problems.isEmpty()) { "Feature catalog check failed:\n" + problems.joinToString("\n") }
        marker.get().asFile.apply { parentFile.mkdirs(); writeText("${defined.size} entries\n") }
    }
}
subprojects {
    tasks.withType<Test>().configureEach {
        dependsOn(checkFeatureCatalog)
        // Test JVMs keep temp files in the module's build directory, not /tmp: here /tmp is a small in-memory disk,
        // and Robolectric unpacks ~200 MB per test JVM that stays behind whenever a JVM is killed.
        val tmp = layout.buildDirectory.dir("tmp/test-jvm").get().asFile
        systemProperty("java.io.tmpdir", tmp.absolutePath)
        doFirst { tmp.deleteRecursively(); tmp.mkdirs() }
    }
}
