package org.asyncttwiz.android

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import java.util.concurrent.AbstractExecutorService
import java.util.concurrent.TimeUnit
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w412dp-h892dp-port-mdpi")
class ImportTabsTest {
    @get:Rule val compose = createComposeRule()
    private class Worker : AbstractExecutorService() {
        val queue = ArrayDeque<Runnable>()
        override fun execute(command: Runnable) { queue += command }
        fun drain() { while (queue.isNotEmpty()) queue.removeFirst().run() }
        override fun shutdown() = Unit
        override fun shutdownNow() = mutableListOf<Runnable>()
        override fun isShutdown() = false
        override fun isTerminated() = false
        override fun awaitTermination(timeout: Long, unit: TimeUnit) = true
    }
    private fun clipboard(value: String) {
        ApplicationProvider.getApplicationContext<Context>().getSystemService(ClipboardManager::class.java)
            .setPrimaryClip(ClipData.newPlainText("Import", value))
    }

    @Test fun deckTabsKeepSeparateFieldsAndAutopasteOnlyOnceWithoutFetching() {
        val worker = Worker()
        val controller = DeckImportController({ error("No lookup expected") }, worker) { it() }
        controller.connectUrl({ error("No resolution expected") }, { error("No automatic fetch") })
        controller.open()
        clipboard("https://archidekt.com/decks/123")
        try {
            compose.setContent { ScreenTheme { DeckImportScreen(controller, false, null, null) } }
            compose.onNodeWithTag("deck-name").assertExists()
            compose.onNodeWithTag("deck-list").assertExists()
            compose.onNodeWithTag("deck-url").assertDoesNotExist()
            compose.onNodeWithText("From URL").performClick()
            compose.onNodeWithTag("deck-url").assertTextContains("https://archidekt.com/decks/123")
            compose.onNodeWithTag("deck-name").assertDoesNotExist()
            compose.onNodeWithTag("deck-list").assertDoesNotExist()
            compose.onNodeWithTag("deck-url").performTextReplacement("mtggoldfish:456")
            clipboard("https://moxfield.com/decks/aBcDeFgHiJkLmNoPqRsT_1")
            compose.onNodeWithText("Paste list").performClick()
            compose.onNodeWithText("From URL").performClick()
            compose.onNodeWithTag("deck-url").assertTextContains("mtggoldfish:456")
            assertFalse(controller.state.busy)
            assertTrue(worker.queue.isEmpty())
        } finally { controller.close() }
    }

    @Test fun cubeTabsKeepSeparateFieldsAndDoNotImportClipboardAutomatically() {
        val worker = Worker()
        val controller = CubeController(DeckApi("https://invalid.example"), { null }, { _, _ -> error("No lookup expected") },
            { error("No resolution expected") }, worker, { it() }, { error("No automatic fetch") })
        controller.newCube()
        clipboard("https://cubecobra.com/cube/list/runes")
        try {
            compose.setContent { ScreenTheme { CubeScreen(controller, null) } }
            compose.onNodeWithText("Cube Cobra URL or cube ID").assertExists()
            compose.onNodeWithText("https://cubecobra.com/cube/list/runes").assertExists()
            compose.onNodeWithText("Cube name").assertDoesNotExist()
            compose.onNodeWithText("Card list").assertDoesNotExist()
            compose.onNodeWithText("Paste list").performClick()
            compose.onNodeWithText("Cube name").assertExists()
            compose.onNodeWithText("Card list").assertExists()
            compose.onNodeWithText("Cube Cobra URL or cube ID").assertDoesNotExist()
            clipboard("another-cube")
            compose.onNodeWithText("Cube Cobra").performClick()
            compose.onNodeWithText("https://cubecobra.com/cube/list/runes").assertExists()
            assertFalse(controller.state.busy)
            assertTrue(worker.queue.isEmpty())
        } finally { controller.close(); worker.drain() }
    }
}
