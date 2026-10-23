package org.asyncttwiz.android

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import io.ktor.http.content.OutgoingContent
import kotlinx.coroutines.runBlocking
import org.asyncttwiz.protocol.*
import org.junit.Test
import java.util.UUID
import java.util.concurrent.AbstractExecutorService
import java.util.concurrent.TimeUnit
import kotlin.test.*

class DeckManagementTest {
    @Test fun ownerChangeMidPaginationOrDetailReadStopsFurtherPrivateCallsAndDropsResults() {
        for (detail in listOf(false, true)) {
            val worker = Worker(); val api = Api(); val posts = mutableListOf<() -> Unit>()
            val controller = DeckLibraryController(api, { "token" }, worker) { posts += it }
            if (detail) api.afterRead = controller::clear else api.afterList = controller::clear
            if (detail) controller.open(summary.id) else controller.refresh()
            worker.drain()
            assertEquals(0, api.definitionReads)
            assertEquals(if (detail) 0 else 1, api.listReads)
            assertTrue(posts.isEmpty())
            assertNull(controller.state.detail)
            assertFalse(controller.state.busy)
            controller.close(); worker.drain()
        }
    }
    private val card = CardDefinition("Text without art", listOf(DefinitionFace(UUID.randomUUID().toString(), "Text without art")))
    private val digest = DeckJson.digest(card)
    private val summary = DeckSummary(UUID.randomUUID().toString(), "Accepted", "1", "2026-10-22T00:00:00Z", 4, 2, DeckCover(digest, card.name))
    private val entries = listOf(SavedDeckEntry(SavedDeckSection.MAINBOARD, digest, 4), SavedDeckEntry(SavedDeckSection.SIDEBOARD, digest, 2))
    private inner class Api : Decks {
        var afterRead: () -> Unit = {}
        var afterList: (() -> Unit)? = null
        var definitionReads = 0; var listReads = 0
        var saved = SavedDeck(summary, entries)
        val updates = mutableListOf<UpdateDeck>(); val deletes = mutableListOf<DeleteDeck>()
        var fail = false; var missing = false; var reads = 0
        override suspend fun list(token: String, after: String?): DeckList {
            listReads++; afterList?.invoke()
            return DeckList(if (missing) emptyList() else listOf(saved.deck), next = if (afterList != null) "next" else null)
        }
        override suspend fun read(token: String, id: String): SavedDeck { reads++; if (missing) throw DeckFailure("not_found"); afterRead(); return saved }
        override suspend fun definition(token: String, id: String, digest: String): SubmittedDefinition {
            definitionReads++; return SubmittedDefinition(digest, card)
        }
        override suspend fun update(token: String, id: String, value: UpdateDeck): SavedDeck {
            updates += value
            if (fail) throw DeckFailure("network")
            saved = saved.copy(deck = saved.deck.copy(name = value.name ?: saved.deck.name, revision = "2"))
            return saved
        }
        override suspend fun delete(token: String, id: String, value: DeleteDeck): DeckDeleted {
            deletes += value; if (fail) throw DeckFailure("network"); missing = true; return DeckDeleted(id)
        }
        override suspend fun submit(token: String, value: SubmitDefinitions) = DefinitionsAccepted(emptyList())
        override suspend fun create(token: String, value: CreateDeck) = saved
        override fun close() = Unit
    }
    private class Worker : AbstractExecutorService() {
        private val queue = mutableListOf<Runnable>()
        override fun execute(command: Runnable) { queue += command }
        override fun shutdown() = Unit
        override fun shutdownNow() = mutableListOf<Runnable>()
        override fun isShutdown() = false
        override fun isTerminated() = false
        override fun awaitTermination(timeout: Long, unit: TimeUnit) = false
        fun drain() { while (queue.isNotEmpty()) queue.removeAt(0).run() }
    }
    @Test fun syncPreservesPersonalDraftSectionAndAcceptedContentOnFailureThenReflectsOtherDeviceDeletion() {
        val api = Api(); val worker = Worker()
        val controller = DeckLibraryController(api, { "token" }, worker) { it() }
        controller.open(summary.id); worker.drain(); controller.section(SavedDeckSection.SIDEBOARD)
        controller.edit(DeckDialog.RENAME); controller.name("Unfinished personal name")
        api.saved = api.saved.copy(deck = summary.copy(name = "Other device", revision = "2"))
        controller.sync(); worker.drain()
        assertEquals("Other device", controller.state.detail!!.saved.deck.name)
        assertEquals("Unfinished personal name", controller.state.draftName)
        assertEquals(DeckDialog.RENAME, controller.state.dialog); assertEquals(SavedDeckSection.SIDEBOARD, controller.state.section)
        controller.dismiss(); controller.edit(DeckDialog.COVER); controller.cover("sha256:${"0".repeat(64)}")
        assertEquals(digest, controller.state.draftCover) // no row-index/foreign-cover substitution
        controller.dismiss(); api.missing = true; controller.sync(); worker.drain()
        assertNull(controller.state.selected); assertNull(controller.state.detail); assertTrue(controller.state.error!!.contains("deleted"))
    }
    @Test fun uncertainMetadataRetryIsImmutableAndRefreshNeverInstallsAnOldOutcome() {
        val api = Api(); val worker = Worker(); val posts = mutableListOf<() -> Unit>()
        val controller = DeckLibraryController(api, { "token" }, worker) { posts += it }
        fun drain() { repeat(3) { worker.drain(); while (posts.isNotEmpty()) posts.removeAt(0)() } }
        controller.open(summary.id); drain(); controller.edit(DeckDialog.RENAME); controller.name(" Renamed 🧙 ")
        api.fail = true; controller.confirm(); controller.confirm(); drain()
        assertEquals(1, api.updates.size); assertNotNull(controller.state.error)
        assertEquals(" Renamed 🧙 ", controller.state.draftName); assertEquals("Accepted", controller.state.detail!!.saved.deck.name)
        controller.sync(); drain(); controller.confirm(); drain()
        assertEquals(api.updates[0], api.updates[1])
        api.fail = false; controller.confirm(); worker.drain()
        api.saved = api.saved.copy(deck = summary.copy(name = "Newer other-device edit", revision = "3"))
        while (posts.isNotEmpty()) posts.removeAt(0)(); drain()
        assertEquals("Newer other-device edit", controller.state.detail!!.saved.deck.name)
        assertNull(controller.state.dialog); assertEquals(entries, controller.state.detail!!.saved.entries)
    }
    @Test fun changedDraftGetsNewIdentityAndLogoutFencesQueuedOrCompletedPrivateMutationsAndReads() {
        val api = Api(); val worker = Worker(); val posts = mutableListOf<() -> Unit>()
        val controller = DeckLibraryController(api, { "token" }, worker) { posts += it }
        fun deliver() { worker.drain(); while (posts.isNotEmpty()) posts.removeAt(0)() }
        controller.open(summary.id); deliver(); controller.edit(DeckDialog.RENAME); controller.name("First")
        api.fail = true; controller.confirm(); deliver(); controller.name("Second"); controller.confirm(); deliver()
        assertNotEquals(api.updates[0].requestId, api.updates[1].requestId)
        controller.dismiss(); controller.edit(DeckDialog.DELETE); controller.dismiss(); assertTrue(api.deletes.isEmpty())
        controller.edit(DeckDialog.DELETE); controller.confirm(); controller.clear(); deliver(); assertTrue(api.deletes.isEmpty())
        controller.open(summary.id); deliver(); controller.edit(DeckDialog.DELETE); api.fail = false; controller.confirm(); worker.drain()
        controller.clear(); while (posts.isNotEmpty()) posts.removeAt(0)()
        assertNull(controller.state.detail); assertTrue(controller.state.decks.isEmpty()); assertFalse(controller.state.loaded)
    }
    @Test fun staleReadCannotReopenPreviousDeckOrOverwriteMutationAndDeleteRetryKeepsIdentity() {
        val api = Api(); val worker = Worker(); val posts = mutableListOf<() -> Unit>()
        val controller = DeckLibraryController(api, { "token" }, worker) { posts += it }
        fun deliver() { repeat(3) { worker.drain(); while (posts.isNotEmpty()) posts.removeAt(0)() } }
        controller.open(summary.id); deliver(); controller.sync(); worker.drain(); controller.back()
        while (posts.isNotEmpty()) posts.removeAt(0)(); deliver(); assertNull(controller.state.selected)
        controller.open(summary.id); deliver(); controller.edit(DeckDialog.DELETE); api.fail = true; controller.confirm(); deliver()
        controller.confirm(); deliver(); assertEquals(api.deletes[0], api.deletes[1])
        api.fail = false; controller.confirm(); deliver(); assertTrue(controller.state.decks.isEmpty()); assertNull(controller.state.selected)
    }
    @Test fun transportUsesCapturedTargetAndNormalPutDeleteJsonWithoutRedirects() = runBlocking {
        val update = UpdateDeck(UUID.randomUUID().toString(), cover = digest); val delete = DeleteDeck(UUID.randomUUID().toString())
        val methods = mutableListOf<HttpMethod>()
        DeckApi("https://decks.example", HttpClient(MockEngine { request ->
            assertEquals("/v1/decks/${summary.id}", request.url.encodedPath)
            assertEquals("Bearer ${"A".repeat(43)}", request.headers["Authorization"])
            methods += request.method
            val body = (request.body as OutgoingContent.ByteArrayContent).bytes().decodeToString()
            val result = if (request.method == HttpMethod.Put) {
                assertEquals(update, DeckJson.format.decodeFromString<UpdateDeck>(body)); DeckJson.format.encodeToString(SavedDeck(summary, entries))
            } else { assertEquals(delete, DeckJson.format.decodeFromString<DeleteDeck>(body)); DeckJson.format.encodeToString(DeckDeleted(summary.id)) }
            respond(result, headers = headersOf("Content-Type", "application/json"))
        }) { followRedirects = false }).use { api ->
            assertEquals(entries, api.update("A".repeat(43), summary.id, update).entries)
            assertEquals(summary.id, api.delete("A".repeat(43), summary.id, delete).id)
        }
        assertEquals(listOf(HttpMethod.Put, HttpMethod.Delete), methods)
    }
}
