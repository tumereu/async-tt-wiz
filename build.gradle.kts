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
    alias(libs.plugins.compose.multiplatform) apply false
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.android.kmp.library) apply false
    alias(libs.plugins.sqldelight) apply false
}

// Kotlin/Wasm toolchain downloads come from the repositories in settings.gradle.kts (FAIL_ON_PROJECT_REPOS), so the
// Kotlin plugin must not add its own.
allprojects {
    plugins.withType<org.jetbrains.kotlin.gradle.targets.wasm.nodejs.WasmNodeJsRootPlugin> {
        extensions.getByType<org.jetbrains.kotlin.gradle.targets.wasm.nodejs.WasmNodeJsEnvSpec>().downloadBaseUrl.set(null as String?)
    }
    plugins.withType<org.jetbrains.kotlin.gradle.targets.wasm.nodejs.WasmNodeJsPlugin> {
        extensions.getByType<org.jetbrains.kotlin.gradle.targets.wasm.nodejs.WasmNodeJsEnvSpec>().downloadBaseUrl.set(null as String?)
    }
    plugins.withType<org.jetbrains.kotlin.gradle.targets.wasm.yarn.WasmYarnPlugin> {
        extensions.getByType<org.jetbrains.kotlin.gradle.targets.wasm.yarn.WasmYarnRootEnvSpec>().downloadBaseUrl.set(null as String?)
    }
    plugins.withType<org.jetbrains.kotlin.gradle.targets.wasm.binaryen.BinaryenPlugin> {
        extensions.getByType<org.jetbrains.kotlin.gradle.targets.wasm.binaryen.BinaryenEnvSpec>().downloadBaseUrl.set(null as String?)
    }
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
            ":client-storage" to setOf(":core", ":protocol", ":arcanum-parser", ":catalog"),
            ":androidApp" to setOf(":core", ":protocol", ":client-storage", ":automatum", ":app-ui"),
            // The shared Compose UI for Android and the web (docs/specs/web-client.md §1).
            ":app-ui" to setOf(":core", ":protocol", ":automatum", ":client-storage", ":app-engine"),
            // The app's engine side without UI (a game's device on engine state), shared by app-ui and the engine's worker.
            ":app-engine" to setOf(":core", ":protocol", ":automatum", ":client-storage"),
            // The web client's page: the shared UI in a browser.
            ":webApp" to setOf(":app-ui"),
            // The engine's Web Worker: the engine boundary's engine side (automatum) and the device (app-engine), no UI.
            ":webApp:engine" to setOf(":automatum", ":app-engine"),
            ":server" to setOf(":core", ":protocol", ":arcanum-api", ":arcanum-engine", ":catalog"),
            // The card catalog: its format and reader, the MTGJSON import, and the build with Automatum's support marks.
            ":catalog" to setOf(":protocol", ":automatum"),
            // Arcanum libraries are generic MTG code: they never depend on ATW modules.
            ":arcanum-api" to emptySet(),
            ":arcanum-parser" to setOf(":arcanum-api"),
            ":arcanum-engine" to setOf(":arcanum-api"),
            ":arcanum-tools" to setOf(":arcanum-parser", ":arcanum-engine"),
            // ATW's side of the integration: depends on both sides; nothing in Arcanum depends on it.
            ":automatum" to setOf(":protocol", ":arcanum-api", ":arcanum-engine"),
            // End-to-end tests run :androidApp's compiled tests against an installed :server; no project dependencies.
            ":e2e" to emptySet(),
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

// The Arcanum engine's test setup (@ArcanumTestSetup: GameState.of, GameState.start, GameState.setup) builds and changes a
// game state outside the rules. Only test source sets may opt in to it: this fails when main code opts in (an @OptIn of
// it, a declaration marked with it, which propagates the opt-in) or a build script opts a whole compilation in.
// The engine's own TestSetup.kt, which declares it, is the one exception. Runs before every test task.
val checkArcanumTestSetup = tasks.register("checkArcanumTestSetup") {
    val sources = fileTree(".") {
        include("*/src/*Main/**/*.kt", "*/src/main/**/*.kt")
        exclude("**/build/**", "arcanum-engine/src/commonMain/kotlin/arcanum/engine/state/TestSetup.kt")
    }
    val scripts = fileTree(".") {
        include("*/build.gradle.kts", "gradle.properties", "*/gradle.properties")
        exclude("**/build/**")
    }
    inputs.files(sources).withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.files(scripts).withPathSensitivity(PathSensitivity.RELATIVE)
    val marker = layout.buildDirectory.file("arcanum-test-setup/checked")
    outputs.file(marker)
    val root = rootDir
    doLast {
        val inSource = Regex("""ArcanumTestSetup\s*::\s*class|@(arcanum\.engine\.state\.)?ArcanumTestSetup\b""")
        val inScript = Regex("""(optIn|opt-in|OptIn)\W.*ArcanumTestSetup""")
        val problems = sources.files.sortedBy { it.path }.flatMap { file ->
            file.readLines().withIndex().filter { inSource.containsMatchIn(it.value) }
                .map { (i, line) -> "${file.relativeTo(root).invariantSeparatorsPath}:${i + 1}: main code opts in to @ArcanumTestSetup\n    ${line.trim()}" }
        } + scripts.files.sortedBy { it.path }.flatMap { file ->
            file.readLines().withIndex().filter { inScript.containsMatchIn(it.value) }
                .map { (i, line) -> "${file.relativeTo(root).invariantSeparatorsPath}:${i + 1}: a build script opts in to @ArcanumTestSetup\n    ${line.trim()}" }
        }
        check(problems.isEmpty()) {
            "Arcanum test setup check failed: only test source sets may opt in (per file, @file:OptIn(ArcanumTestSetup::class))\n" +
                problems.joinToString("\n")
        }
        marker.get().asFile.apply { parentFile.mkdirs(); writeText("${sources.files.size} main files checked\n") }
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
            "lobby.md" to setOf("LOB"), "draft.md" to setOf("DRF"), "rules.md" to setOf("SYS"), "web.md" to setOf("WEB"))
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
// Client crash safety (after prod crash WCRF3Q): client main code has no `!!` except the few in
// gradle/client-not-null-allowlist.txt, each with its reason. Elsewhere the types prove the value is there, or the code
// falls back and reports the fault (org.asyncttwiz.protocol.Faults). An entry that no longer matches fails too, so the
// list stays exact. Runs before every test task.
val checkClientNotNull = tasks.register("checkClientNotNull") {
    val sources = fileTree(".") {
        include("app-ui/src/*Main/**/*.kt", "automatum/src/*Main/**/*.kt", "protocol/src/*Main/**/*.kt",
            "client-storage/src/*Main/**/*.kt", "androidApp/src/main/**/*.kt")
        exclude("**/build/**")
    }
    val allowlist = file("gradle/client-not-null-allowlist.txt")
    inputs.files(sources).withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.file(allowlist).withPathSensitivity(PathSensitivity.RELATIVE)
    val marker = layout.buildDirectory.file("client-not-null/checked")
    outputs.file(marker)
    val root = rootDir
    doLast {
        // Code lines (comments and string contents blanked, string templates kept) that hold a `!!`.
        fun assertions(text: String): List<Pair<Int, String>> {
            val code = StringBuilder(text.length)
            var i = 0
            fun blank(count: Int) { repeat(count) { if (i < text.length) { code.append(if (text[i] == '\n') '\n' else ' '); i++ } } }
            fun keep(count: Int) { repeat(count) { if (i < text.length) { code.append(text[i]); i++ } } }
            // Innermost last: "code" (the file, or a string template's braces), "string", "raw"; with code's brace depth.
            val modes = mutableListOf("code")
            val depths = mutableListOf(0)
            while (i < text.length) {
                val c = text[i]
                if (modes.last() == "code") when {
                    text.startsWith("//", i) -> blank(text.indexOf('\n', i).let { if (it < 0) text.length else it } - i)
                    text.startsWith("/*", i) -> blank(text.indexOf("*/", i + 2).let { if (it < 0) text.length else it + 2 } - i)
                    text.startsWith("\"\"\"", i) -> { keep(3); modes += "raw"; depths += 0 }
                    c == '"' -> { keep(1); modes += "string"; depths += 0 }
                    c == '\'' -> { keep(1); while (i < text.length && text[i] != '\'' && text[i] != '\n') blank(if (text[i] == '\\') 2 else 1); keep(1) }
                    c == '{' -> { depths[depths.lastIndex]++; keep(1) }
                    c == '}' && depths.last() == 0 && modes.size > 1 -> { modes.removeLast(); depths.removeLast(); keep(1) }
                    c == '}' -> { depths[depths.lastIndex]--; keep(1) }
                    else -> keep(1)
                } else when {
                    modes.last() == "raw" && text.startsWith("\"\"\"", i) -> { keep(3); modes.removeLast(); depths.removeLast() }
                    modes.last() == "string" && c == '"' -> { keep(1); modes.removeLast(); depths.removeLast() }
                    modes.last() == "string" && c == '\\' -> blank(2)
                    text.startsWith("\${", i) -> { keep(2); modes += "code"; depths += 0 }
                    else -> blank(1)
                }
            }
            val original = text.lines()
            return code.toString().lines().withIndex().filter { "!!" in it.value }.map { (index, _) -> index + 1 to original[index].trim() }
        }
        val entries = allowlist.readLines().map(String::trim).filter { it.isNotEmpty() && !it.startsWith("#") }
        val problems = mutableListOf<String>()
        val allowed = entries.mapNotNull { line ->
            val parts = line.split(" | ")
            if (parts.size != 3 || parts.any { it.isBlank() }) { problems += "allowlist entry not 'file | code line | reason': $line"; null }
            else parts[0] to parts[1]
        }
        val used = mutableSetOf<Pair<String, String>>()
        sources.files.sortedBy { it.path }.forEach { file ->
            val path = file.relativeTo(root).invariantSeparatorsPath
            assertions(file.readText()).forEach { (number, line) ->
                if (path to line in allowed) used += path to line
                else problems += "$path:$number: `!!` in client main code: make the types prove it, or fall back and Faults.report it (or allow-list it with a reason in gradle/client-not-null-allowlist.txt)\n    $line"
            }
        }
        (allowed - used).forEach { (path, line) -> problems += "allowlist entry matches no `!!` any more: $path | $line" }
        check(problems.isEmpty()) { "Client not-null check failed:\n" + problems.joinToString("\n") }
        marker.get().asFile.apply { parentFile.mkdirs(); writeText("${used.size} allowed\n") }
    }
}
subprojects {
    tasks.withType<Test>().configureEach {
        dependsOn(checkFeatureCatalog)
        dependsOn(checkClientNotNull)
        dependsOn(checkArcanumTestSetup)
        // Test JVMs keep temp files in the module's build directory, not /tmp: here /tmp is a small in-memory disk,
        // and Robolectric unpacks ~200 MB per test JVM that stays behind whenever a JVM is killed.
        val tmp = layout.buildDirectory.dir("tmp/test-jvm").get().asFile
        systemProperty("java.io.tmpdir", tmp.absolutePath)
        doFirst { tmp.deleteRecursively(); tmp.mkdirs() }
    }
}
