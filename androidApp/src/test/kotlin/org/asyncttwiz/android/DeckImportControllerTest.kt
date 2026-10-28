package org.asyncttwiz.android

import org.asyncttwiz.storage.*
import org.junit.Test
import kotlin.test.*
import java.util.concurrent.AbstractExecutorService
import java.util.concurrent.TimeUnit

class DeckImportControllerTest {
    @Test fun linkImportUsesParsedPipelineFillsTheEditorAndClosesTheDialog() {
        val worker = Worker(); var fetches = 0; var reads = 0
        val parsed = DeckTextParser.parse("Deck\n2 Rune Bear\nSideboard\n1 Rune Shield")
        var received: ParsedDeck? = null
        val controller = DeckImportController({ error("Use parsed pipeline") }, worker) { it() }
        controller.connectUrl({ received = it; result(it.text) }, { fetches++; ImportedDeck("Site deck", parsed) })
        controller.open(); controller.edit("1 Replaced")
        controller.prefillUrl { reads++; "https://archidekt.com/decks/999" }
        assertEquals(0, reads, "The clipboard is read only once the dialog opens")
        controller.openLink()
        controller.prefillUrl { reads++; "https://archidekt.com/decks/123" }
        controller.closeLink(); controller.openLink()
        controller.prefillUrl { reads++; "https://archidekt.com/decks/456" }
        assertEquals(1, reads); assertEquals(0, fetches)
        assertEquals("https://archidekt.com/decks/123", controller.state.url)
        controller.importUrl(); assertTrue(controller.state.busy); assertTrue(controller.state.linkDialog); worker.drain()
        assertEquals(1, fetches); assertEquals(parsed, received)
        assertEquals("Site deck", controller.state.name); assertEquals(parsed.text, controller.state.text)
        assertFalse(controller.state.linkDialog); assertFalse(controller.state.busy)
        assertEquals(result(parsed.text), controller.state.result)
        // The result is an ordinary draft: closing it asks to discard.
        controller.back(); assertTrue(controller.state.discard)
        controller.close()
    }

    @Test fun linkImportKeepsATypedNameAndAResolvedCover() {
        val worker = Worker()
        val printing = { id: String -> CatalogPrinting(id, id, "SET", "1", "English", "", null, false, emptyList(), """{"name":"$id"}""") }
        val resolve = { text: String -> DeckImportResult(text, text.lines().filter { it.matches(Regex("\\d+ .+")) }.map {
            ResolvedDeckEntry(DeckSection.MAINBOARD, 1, listOf(printing(it.substringAfter(' '))), listOf(1)) }, emptyList(), null) }
        var next = "1 Bear\n1 Shield"
        val controller = DeckImportController(resolve, worker) { it() }
        controller.connectUrl({ resolve(it.text) }, { ImportedDeck("Site deck", DeckTextParser.parse(next)) })
        controller.open(); controller.name("My deck"); controller.edit("1 Shield\n1 Owl"); controller.lookup(); worker.drain()
        val shield = controller.coverCards.first { it.definition.name == "Shield" }.digest
        controller.chooseCover(); controller.cover(shield)
        controller.openLink(); controller.url("archidekt:1"); controller.importUrl(); worker.drain()
        assertEquals("My deck", controller.state.name)
        assertEquals(listOf("Bear", "Shield"), controller.state.result!!.entries.map { it.printing.name })
        assertEquals(shield, controller.state.cover); assertEquals("Shield", controller.currentCover!!.name)
        next = "1 Owl"
        controller.openLink(); controller.importUrl(); worker.drain()
        assertNull(controller.state.cover); assertEquals("Owl", controller.currentCover!!.name)
        controller.close()
    }

    @Test fun linkFailureStaysInTheDialogKeepsTheDraftAndCorrectionsFenceDelayedResults() {
        val worker = Worker(); val posts = ArrayDeque<() -> Unit>()
        val controller = DeckImportController(::result, worker) { posts += it }
        controller.connectUrl(::resultParsed, { error("403") })
        controller.open(); controller.edit("1 Retained"); controller.name("Retained"); controller.openLink()
        controller.url("https://archidekt.com/decks/456")
        controller.importUrl(); worker.drain(); posts.removeFirst()()
        assertEquals("1 Retained", controller.state.text); assertEquals("Retained", controller.state.name)
        assertTrue(controller.state.linkDialog); assertNull(controller.state.error)
        assertTrue(controller.state.linkError!!.contains("Paste the list")); assertFalse(controller.state.busy)
        controller.url("not a link"); controller.importUrl()
        assertEquals("Enter a supported deck URL or site:ID.", controller.state.linkError)
        controller.connectUrl(::resultParsed, { ImportedDeck("Remote", DeckTextParser.parse("2 Remote")) })
        controller.url("archidekt:456")
        controller.importUrl(); worker.drain(); controller.url("archidekt:123"); posts.removeFirst()()
        assertEquals("Retained", controller.state.name); assertFalse(controller.state.busy)
        controller.importUrl(); worker.drain(); controller.edit("3 Corrected"); posts.removeFirst()()
        assertEquals("3 Corrected", controller.state.text); assertEquals("Retained", controller.state.name)
        controller.importUrl(); worker.drain(); controller.exit(); controller.open(); posts.removeFirst()()
        assertEquals("", controller.state.text); assertEquals("", controller.state.name)
        controller.close()
    }

    @Test fun cancelAndBackDropDelayedLinkImports() {
        val changes: List<(DeckImportController) -> Unit> = listOf(
            { it.closeLink() },
            { it.back() },
            { it.closeLink(); it.openLink() }
        )
        changes.forEach { change ->
            val worker = Worker(); val posts = ArrayDeque<() -> Unit>()
            val controller = DeckImportController(::result, worker) { posts += it }
            controller.connectUrl(::resultParsed, { ImportedDeck("Remote", DeckTextParser.parse("2 Remote")) })
            controller.open(); controller.edit("1 Retained"); controller.name("Retained"); controller.openLink()
            controller.url("archidekt:123"); controller.importUrl(); worker.drain()
            change(controller)
            val corrected = controller.state
            posts.removeFirst()()
            assertEquals(corrected, controller.state)
            assertEquals("1 Retained", controller.state.text)
            assertFalse(controller.state.busy); assertFalse(controller.state.fetchingUrl)
            assertFalse(controller.state.discard)
            controller.close()
        }
    }

    @Test fun anotherUrlImportSupersedesThePendingResponse() {
        val worker = Worker(); val posts = ArrayDeque<() -> Unit>(); var fetches = 0
        val controller = DeckImportController(::result, worker) { posts += it }
        controller.connectUrl(::resultParsed, { ImportedDeck("Remote ${++fetches}", DeckTextParser.parse("$fetches Remote")) })
        controller.open(); controller.openLink(); controller.url("archidekt:123")
        controller.importUrl(); worker.drain()
        controller.importUrl(); worker.drain()
        assertEquals(2, fetches)
        posts.removeFirst()()
        assertEquals("", controller.state.name); assertEquals("", controller.state.text)
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
