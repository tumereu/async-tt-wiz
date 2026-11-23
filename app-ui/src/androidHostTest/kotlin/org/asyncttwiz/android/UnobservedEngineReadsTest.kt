package org.asyncttwiz.android

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The UI never reads what an engine game published from a plain field where composition (or a snapshot-observing block)
 * should follow it. Prod 0.3.1 stalled games (43efa2ba8): an effect keyed on `EngineGame.version`, a plain value no snapshot
 * observes, never restarted once the table skipped recompositions, and the table waited until something unrelated redrew it.
 *
 * The compiler guards most of it: `EngineGame.version` is internal to app-engine, and `EngineGame.shown` (with
 * `needsReload`, `transition`, `historyLoadingNow`) needs an opt-in ([UnobservedEngineState]), so the UI reads
 * [observedShown] and [shownVersion], snapshot state. What the compiler can't tell is whether an opt-in sits where
 * composition reads it; this keeps the opt-ins in the UI's shipped code (app-ui, webApp) to the files listed here, which
 * hold no composable code and no snapshot-observing block (`derivedStateOf`, `snapshotFlow`, effects, `remember`), and keeps
 * the UI's build scripts from opting whole compilations in.
 */
class UnobservedEngineReadsTest {
    private val root = generateSequence(File("").absoluteFile) { it.parentFile }.first { File(it, "settings.gradle.kts").exists() }

    /** The UI's files that read the plain fields on purpose, outside composition, and why. */
    private val allowed = mapOf(
        "app-ui/src/commonMain/kotlin/org/asyncttwiz/android/EngineGameState.kt" to
            "the snapshot state itself: starts from the plain values, observedShown reads shownVersion before the field",
        "app-ui/src/commonMain/kotlin/org/asyncttwiz/android/GameSession.kt" to
            "the server stream's handling (revisions, heads, reloads), never in composition",
        "app-ui/src/wasmJsMain/kotlin/org/asyncttwiz/android/EngineBench.kt" to
            "the browser benchmark's flows, driven by callbacks",
    )

    private val optIn = Regex("""UnobservedEngineState\s*::\s*class|@UnobservedEngineState\b""")
    private val observing = Regex("""@Composable|\bderivedStateOf\b|\bsnapshotFlow\b|\bLaunchedEffect\b|\bDisposableEffect\b|""" +
        """\bproduceState\b|\bremember\w*\s*[({]""")

    private fun shipped(): List<File> = listOf("app-ui/src", "webApp/src", "webApp/engine/src").flatMap { dir ->
        File(root, dir).listFiles().orEmpty().filter { it.name.endsWith("Main") }
            .flatMap { set -> set.walk().filter { it.isFile && it.extension == "kt" }.toList() }
    }

    private fun File.path() = relativeTo(root).invariantSeparatorsPath

    private fun String.isComment() = trimStart().let { it.startsWith("//") || it.startsWith("*") || it.startsWith("/*") }

    @Test
    fun plainEngineReadsStayOutOfComposition() {
        val files = shipped()
        assertTrue(files.size > 100, "the UI's sources were found (${files.size} under $root)")
        val unlisted = files.filter { it.path() !in allowed && optIn.containsMatchIn(it.readText()) }.flatMap { file ->
            file.readLines().withIndex().filter { optIn.containsMatchIn(it.value) }.map { "${file.path()}:${it.index + 1}: ${it.value.trim()}" }
        }
        assertEquals(emptyList(), unlisted, "Plain reads of an engine game's published state (@OptIn(UnobservedEngineState::class)) " +
            "outside this test's list: composable code reads game.observedShown / game.shownVersion (snapshot state), so it is " +
            "composed again and its effects restart with every board published. Only code no composition reads may opt in; " +
            "list such a file here with why.")
        val observingListed = allowed.keys.flatMap { path ->
            File(root, path).readLines().withIndex().filter { (_, line) -> !line.isComment() && observing.containsMatchIn(line) }
                .map { "$path:${it.index + 1}: ${it.value.trim()}" }
        }
        assertEquals(emptyList(), observingListed, "A file listed for plain engine reads holds composable or snapshot-observing " +
            "code, where a plain read isn't followed: read observedShown / shownVersion there, or move that code out")
    }

    @Test
    fun listedFilesStillReadPlainly() {
        val stale = allowed.keys.filter { path -> File(root, path).let { !it.exists() || !optIn.containsMatchIn(it.readText()) } }
        assertEquals(emptyList(), stale, "Listed for plain engine reads but no longer opting in: take them off the list")
    }

    @Test
    fun uiBuildsDontOptInWholesale() {
        val scripts = listOf("app-ui/build.gradle.kts", "webApp/build.gradle.kts", "webApp/engine/build.gradle.kts")
        val optingIn = scripts.filter { File(root, it).readLines().any { line -> "UnobservedEngineState" in line && !line.trimStart().startsWith("//") } }
        assertEquals(emptyList(), optingIn, "The UI's builds opt in to UnobservedEngineState as a whole: composable code would " +
            "read the plain fields unseen. Opt in where the read is meant (this test's list) instead")
    }
}
