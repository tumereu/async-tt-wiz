package org.asyncttwiz.android

import android.os.Looper
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.test.junit4.v2.createComposeRule
import java.io.File
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The unit tests' Compose harness: a composition coroutine that comes back from work on another thread (the end of a
 * `withContext(Dispatchers.IO)`, as the avatar chooser's search and several screens do) resumes on the main thread, so
 * the state it writes is recomposed, measured and laid out there, as on a phone.
 *
 * Compose's older test rule (`androidx.compose.ui.test.junit4.createComposeRule`) runs the composition on an unconfined
 * dispatcher: such a coroutine went on running on the worker, whose apply notifications resumed the recomposer there
 * and ran a whole frame, measure and layout included, while the main thread measured the same window. That was the
 * intermittent "performMeasureAndLayout called during measure layout" (AvatarTest 2026-11-16, PendingOutcomeTableTest
 * before it). The v2 rule queues the composition's work for the main thread; [everyComposeTestUsesTheQueuingRule] keeps
 * every test on it.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ComposeTestThreadsTest {
    @get:Rule val compose = createComposeRule()

    @Test fun compositionCoroutinesAndFramesStayOnTheMainThread() {
        val main = Looper.getMainLooper().thread
        val elsewhere = ConcurrentLinkedQueue<String>()
        fun onMain(what: String) { if (Thread.currentThread() !== main) elsewhere += "$what on ${Thread.currentThread().name}" }
        val rounds = AtomicInteger()
        compose.setContent {
            var count by remember { mutableIntStateOf(0) }
            LaunchedEffect(Unit) {
                repeat(ROUNDS) {
                    withContext(Dispatchers.IO) { Thread.sleep(1) }
                    onMain("the coroutine resumed")
                    count++
                    rounds.incrementAndGet()
                }
            }
            onMain("composed")
            Text("Rounds: $count", Modifier.layout { measurable, constraints ->
                onMain("measured")
                val placeable = measurable.measure(constraints)
                layout(placeable.width, placeable.height) { placeable.place(0, 0) }
            })
        }
        compose.waitUntil(timeoutMillis = 60_000) { rounds.get() == ROUNDS }
        compose.waitForIdle()
        assertEquals(emptyList<String>(), elsewhere.toList().distinct())
    }

    @Test fun everyComposeTestUsesTheQueuingRule() {
        var root = File("").absoluteFile
        while (!File(root, "settings.gradle.kts").isFile) root = root.parentFile
        // The older rule's factories, imported or written out; the v2 ones live in ...junit4.v2 and ...test.v2
        val older = Regex("""androidx\.compose\.ui\.test\.(junit4\.)?(createComposeRule|createAndroidComposeRule|createEmptyComposeRule|runComposeUiTest|runAndroidComposeUiTest|runEmptyComposeUiTest)\b""")
        val sources = listOf("androidApp/src/test", "e2e/src/test").flatMap { File(root, it).walk().filter { f -> f.extension == "kt" }.toList() }
        val found = sources.flatMap { file ->
            file.readLines().withIndex().filter { (_, line) -> line.trimStart().let { !it.startsWith("*") && !it.startsWith("//") } && older.containsMatchIn(line) }
                .map { (index, line) -> "${file.relativeTo(root)}:${index + 1}: ${line.trim()}" }
        }
        check(sources.size > 300) { "the scan found too little: ${sources.size} files" }
        assertEquals("Compose tests on the older (unconfined) test rule; import it from androidx.compose.ui.test.junit4.v2 instead",
            emptyList<String>(), found)
    }

    private companion object {
        const val ROUNDS = 50
    }
}
