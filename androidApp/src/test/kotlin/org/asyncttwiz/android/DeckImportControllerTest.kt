package org.asyncttwiz.android

import org.asyncttwiz.storage.*
import org.junit.Test
import kotlin.test.*
import java.util.concurrent.AbstractExecutorService
import java.util.concurrent.TimeUnit

class DeckImportControllerTest {
    @Test fun urlImportUsesParsedPipelineRetainsSiteNameAndRequiresExplicitConfirmation() {
        val worker = Worker(); var fetches = 0; var reads = 0
        val parsed = DeckTextParser.parse("Deck\n2 Rune Bear\nSideboard\n1 Rune Shield")
        var received: ParsedDeck? = null
        val controller = DeckImportController({ error("Use parsed pipeline") }, worker) { it() }
        controller.connectUrl({ received = it; result(it.text) }, { fetches++; ImportedDeck("Site deck", parsed) })
        controller.open(); controller.fromUrl(true)
        controller.prefillUrl { reads++; "https://archidekt.com/decks/123" }
        controller.fromUrl(false); controller.fromUrl(true)
        controller.prefillUrl { reads++; "https://archidekt.com/decks/456" }
        assertEquals(1, reads); assertEquals(0, fetches)
        assertEquals("https://archidekt.com/decks/123", controller.state.url)
        controller.importUrl(); assertTrue(controller.state.busy); worker.drain()
        assertEquals(1, fetches); assertEquals(parsed, received)
        assertEquals("Site deck", controller.state.name); assertEquals(parsed.text, controller.state.text)
        assertFalse(controller.state.fromUrl); assertFalse(controller.state.busy)
        assertEquals(result(parsed.text), controller.state.result)
        controller.close()
    }

    @Test fun urlFailureKeepsDraftAndSuggestsPasteAndCorrectionsFenceDelayedResults() {
        val worker = Worker(); val posts = ArrayDeque<() -> Unit>()
        val controller = DeckImportController(::result, worker) { posts += it }
        controller.connectUrl(::resultParsed, { error("403") })
        controller.open(); controller.edit("1 Retained"); controller.name("Retained"); controller.fromUrl(true)
        controller.url("https://archidekt.com/decks/456")
        controller.importUrl(); worker.drain(); posts.removeFirst()()
        assertEquals("1 Retained", controller.state.text); assertEquals("Retained", controller.state.name)
        assertTrue(controller.state.error!!.contains("Paste list")); assertFalse(controller.state.busy)
        controller.connectUrl(::resultParsed, { ImportedDeck("Remote", DeckTextParser.parse("2 Remote")) })
        controller.importUrl(); worker.drain(); controller.url("archidekt:123"); posts.removeFirst()()
        assertEquals("Retained", controller.state.name); assertFalse(controller.state.busy)
        controller.importUrl(); worker.drain(); controller.edit("3 Corrected"); posts.removeFirst()()
        assertEquals("3 Corrected", controller.state.text); assertEquals("Retained", controller.state.name)
        controller.importUrl(); worker.drain(); controller.exit(); controller.open(); posts.removeFirst()()
        assertEquals("", controller.state.text); assertEquals("", controller.state.name)
        controller.close()
    }

    @Test fun nameChangesAndTabSwitchesDropDelayedUrlImports() {
        val changes: List<(DeckImportController) -> Unit> = listOf(
            { it.name("Corrected name") },
            { it.fromUrl(false) },
            { it.fromUrl(false); it.fromUrl(true) }
        )
        changes.forEach { change ->
            val worker = Worker(); val posts = ArrayDeque<() -> Unit>()
            val controller = DeckImportController(::result, worker) { posts += it }
            controller.connectUrl(::resultParsed, { ImportedDeck("Remote", DeckTextParser.parse("2 Remote")) })
            controller.open(); controller.edit("1 Retained"); controller.name("Retained"); controller.fromUrl(true)
            controller.url("archidekt:123"); controller.importUrl(); worker.drain()
            change(controller)
            val corrected = controller.state
            posts.removeFirst()()
            assertEquals(corrected, controller.state)
            assertEquals("1 Retained", controller.state.text)
            assertFalse(controller.state.busy); assertFalse(controller.state.fetchingUrl)
            controller.close()
        }
    }

    @Test fun anotherUrlImportSupersedesThePendingResponse() {
        val worker = Worker(); val posts = ArrayDeque<() -> Unit>(); var fetches = 0
        val controller = DeckImportController(::result, worker) { posts += it }
        controller.connectUrl(::resultParsed, { ImportedDeck("Remote ${++fetches}", DeckTextParser.parse("$fetches Remote")) })
        controller.open(); controller.name("Retained"); controller.fromUrl(true); controller.url("archidekt:123")
        controller.importUrl(); worker.drain()
        controller.importUrl(); worker.drain()
        assertEquals(2, fetches)
        posts.removeFirst()()
        assertEquals("Retained", controller.state.name); assertEquals("", controller.state.text)
        assertTrue(controller.state.busy); assertTrue(controller.state.fetchingUrl)
        posts.removeFirst()()
        assertEquals("Remote 2", controller.state.name); assertEquals("2 Remote", controller.state.text)
        assertFalse(controller.state.busy); assertFalse(controller.state.fetchingUrl)
        controller.close()
    }

    private fun resultParsed(parsed: ParsedDeck) = result(parsed.text)

    @Test fun installationDisablesRetryAndNewMetadataRelooksUpOnlyAnUnresolvedDraft() {
        val worker = Worker()
        var calls = 0
        val controller = DeckImportController({ calls++; result(it) }, worker) { it() }
        val installed = CatalogMetadata("2026-10-24", "5.2.2")
        controller.open(); controller.edit("1 Unknown"); controller.lookup(); worker.drain()
        assertEquals(1, calls)
        val issues = controller.state.result
        controller.catalogChanged(null, installing = true)
        controller.lookup(); worker.drain()
        assertEquals(1, calls); assertEquals(issues, controller.state.result)
        controller.catalogChanged(installed, installing = false)
        assertTrue(controller.state.busy)
        assertEquals(issues, controller.state.result, "Keep correction feedback in place during retry")
        worker.drain(); assertEquals(2, calls)
        controller.catalogChanged(installed, installing = false); worker.drain(); assertEquals(2, calls)
        controller.exit(); controller.catalogChanged(installed.copy(date = "2026-10-25"), false)
        worker.drain(); assertEquals(2, calls)
    }

    @Test fun metadataArrivingDuringLookupRetriesAfterAnUnresolvedResultButNotAfterSuccess() {
        val worker = Worker()
        var calls = 0
        var resolved = false
        val controller = DeckImportController({ text -> calls++
            if (resolved) DeckImportResult(text, emptyList(), emptyList(), CatalogMetadata("2026-10-24", "5.2.2"))
            else result(text)
        }, worker) { it() }
        val installed = CatalogMetadata("2026-10-24", "5.2.2")
        controller.open(); controller.edit("1 Unknown"); controller.lookup()
        controller.catalogChanged(installed, false); worker.drain()
        assertEquals(2, calls)
        resolved = true
        controller.lookup(); controller.catalogChanged(installed.copy(version = "5.2.3"), false); worker.drain()
        assertEquals(3, calls)
        controller.catalogChanged(installed.copy(date = "2026-10-25"), false); worker.drain()
        assertEquals(3, calls)
    }

    @Test fun recreationLookupCannotInvalidateRetainedPreviewOrName() {
        val worker = Worker()
        val printing = CatalogPrinting("id", "Name", "SET", "1", "English", "", null, false, emptyList(), "{}")
        val controller = DeckImportController({ text -> DeckImportResult(text,
            listOf(ResolvedDeckEntry(DeckSection.MAINBOARD, 1, listOf(printing), listOf(1))), emptyList(),
            CatalogMetadata("2026-10-11", "5.2.2")) }, worker) { it() }
        controller.open(); controller.edit("1 Name"); controller.lookup(); worker.drain()
        controller.review(); controller.name("Retained name")
        val before = controller.state
        controller.lookup(); worker.drain()
        assertEquals(before, controller.state); assertTrue(controller.state.result!!.resolved)
        controller.back(); assertFalse(controller.state.preview); assertEquals("1 Name", controller.state.text)
        controller.review(); assertEquals("Retained name", controller.state.name)
    }
    private class Worker : AbstractExecutorService() {
        val queue = mutableListOf<Runnable>()
        override fun execute(command: Runnable) { queue += command }
        override fun shutdown() = Unit
        override fun shutdownNow(): MutableList<Runnable> = mutableListOf()
        override fun isShutdown() = false
        override fun isTerminated() = false
        override fun awaitTermination(timeout: Long, unit: TimeUnit) = false
        fun drain() { while (queue.isNotEmpty()) queue.removeAt(0).run() }
    }
    private fun result(text: String) = DeckImportResult(text, emptyList(), listOf(ImportIssue(2, "unknown")), null)
    @Test fun correctionsExitLogoutAndCloseFenceDelayedLookup() {
        val worker = Worker(); val posts = mutableListOf<() -> Unit>()
        val controller = DeckImportController(::result, worker) { posts += it }
        controller.open(); controller.edit("private text"); controller.lookup(); worker.drain()
        controller.edit("corrected text"); posts.removeAt(0)()
        assertEquals("corrected text", controller.state.text); assertNull(controller.state.result)
        controller.lookup(); worker.drain(); controller.exit(); controller.open(); posts.removeAt(0)()
        assertEquals("", controller.state.text); assertNull(controller.state.result)
        controller.edit("another account"); controller.lookup(); worker.drain(); controller.close(); posts.removeAt(0)()
        assertFalse(controller.state.active); assertEquals("", controller.state.text)
    }
    @Test fun lookupErrorsPreserveInputAndExceptionsAreActionable() {
        val worker = Worker()
        val controller = DeckImportController({ error("storage") }, worker) { it() }
        controller.open(); controller.edit("\n1 Exact  Text\n"); controller.lookup()
        assertTrue(controller.state.busy); worker.drain()
        assertEquals("\n1 Exact  Text\n", controller.state.text)
        assertNull(controller.state.result)
        assertTrue(controller.state.lookupFailed)
        assertTrue(controller.state.error!!.contains("retry"))
        controller.review(); assertFalse(controller.state.preview)
        controller.back(); assertTrue(controller.state.discard); assertTrue(controller.state.active)
        controller.exit(); assertFalse(controller.state.active)
    }
}
