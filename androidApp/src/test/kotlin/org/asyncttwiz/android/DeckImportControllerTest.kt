package org.asyncttwiz.android

import org.asyncttwiz.storage.*
import org.junit.Test
import kotlin.test.*
import java.util.concurrent.AbstractExecutorService
import java.util.concurrent.TimeUnit

class DeckImportControllerTest {
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
        assertTrue(controller.state.result!!.issues.single().message.contains("retry"))
        controller.review(); assertFalse(controller.state.preview)
        controller.back(); assertFalse(controller.state.active)
    }
}
