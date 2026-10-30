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
        var readFailure = false
        var draft: SavedDeck? = null
        val copies = mutableListOf<CopyDeck>()
        override suspend fun drafts(token: String) = DeckList(listOfNotNull(draft?.deck))
        override suspend fun copy(token: String, id: String, value: CopyDeck): SavedDeck {
            copies += value; if (fail) throw DeckFailure("network")
            val source = draft!!.takeIf { it.deck.id == id }!!
            return SavedDeck(source.deck.copy(id = UUID.nameUUIDFromBytes(value.requestId.encodeToByteArray()).toString(), draft = null),
                source.entries).also { saved = it }
        }
        override suspend fun list(token: String, after: String?): DeckList {
            listReads++; afterList?.invoke()
            return DeckList(if (missing) emptyList() else listOf(saved.deck), next = if (afterList != null) "next" else null)
        }
        override suspend fun read(token: String, id: String): SavedDeck { reads++; draft?.takeIf { it.deck.id == id }?.let { return it }; if (missing) throw DeckFailure("not_found"); if (readFailure) throw DeckFailure("network"); afterRead(); return saved }
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
    @Test fun draftDecksListSeparatelyAndSaveAsDeckOpensAnIndependentCopyKeepingOneRequestIdentity() {
        val api = Api(); val worker = Worker()
        val draft = SavedDeck(summary.copy(id = UUID.randomUUID().toString(), name = "Friday pod draft",
            draft = DraftDeckOrigin(UUID.randomUUID().toString(), "Friday pod")), entries)
        api.draft = draft
        val controller = DeckLibraryController(api, { "token" }, worker) { it() }
        try {
            controller.refresh(); worker.drain()
            assertEquals(listOf(summary), controller.state.decks); assertEquals(listOf(draft.deck), controller.state.draftDecks)
            controller.open(summary.id); worker.drain(); controller.saveAsDeck()
            assertTrue(api.copies.isEmpty()) // only draft decks offer Save as deck
            controller.open(draft.deck.id); worker.drain()
            api.fail = true; controller.saveAsDeck(); worker.drain()
            assertNotNull(controller.state.error); assertEquals(draft.deck.id, controller.state.selected)
            api.fail = false; controller.saveAsDeck(); worker.drain()
            assertEquals(1, api.copies.distinct().size)
            val copy = api.saved.deck
            assertNull(copy.draft); assertEquals(copy.id, controller.state.selected)
            assertEquals(copy, controller.state.detail!!.saved.deck)
            assertTrue(copy in controller.state.decks); assertEquals(listOf(draft.deck), controller.state.draftDecks)
            controller.back(); worker.drain()
            assertEquals(listOf(draft.deck), controller.state.draftDecks)
        } finally { controller.close(); worker.drain() }
    }
    @Test fun draftDeckPresentationNamesTheLimitAndShowsThePodOnlyWhenTheNameHidesIt() {
        assertEquals("DRAFT DECKS (7 / 20)", draftDecksHeading(7))
        assertTrue(DRAFT_DECKS_EXPLANATION.contains("newest 20"))
        val origin = DraftDeckOrigin(UUID.randomUUID().toString(), "Friday pod")
        assertEquals("4 main / 2 side", deckRowDetail(summary))
        assertEquals("4 main / 2 side", deckRowDetail(summary.copy(name = "Friday pod draft", draft = origin)))
        assertEquals("4 main / 2 side · Friday pod", deckRowDetail(summary.copy(name = "Azorius fliers", draft = origin)))
        assertEquals("draft deck", deckKind(summary.copy(draft = origin))); assertEquals("saved deck", deckKind(summary))
        assertEquals(emptyList(), deckPickerSections(emptyList()))
        assertEquals(listOf("Draft decks" to listOf(summary.copy(draft = origin))), deckPickerSections(listOf(summary.copy(draft = origin))))
    }
    @Test fun explicitReloadPreservesPersonalDraftSectionThenReflectsOtherDeviceDeletion() {
        val api = Api(); val worker = Worker()
        val controller = DeckLibraryController(api, { "token" }, worker) { it() }
        controller.open(summary.id); worker.drain(); controller.section(SavedDeckSection.SIDEBOARD)
        controller.edit(DeckDialog.RENAME); controller.name("Unfinished personal name")
        api.saved = api.saved.copy(deck = summary.copy(name = "Other device", revision = "2"))
        controller.retry(); worker.drain()
        assertEquals("Other device", controller.state.detail!!.saved.deck.name)
        assertEquals("Unfinished personal name", controller.state.draftName)
        assertEquals(DeckDialog.RENAME, controller.state.dialog); assertEquals(SavedDeckSection.SIDEBOARD, controller.state.section)
        controller.dismiss(); controller.edit(DeckDialog.COVER); controller.cover("sha256:${"0".repeat(64)}")
        assertEquals(digest, controller.state.draftCover) // no row-index/foreign-cover substitution
        controller.dismiss(); api.missing = true; controller.retry(); worker.drain()
        assertNull(controller.state.selected); assertNull(controller.state.detail); assertTrue(controller.state.error!!.contains("deleted"))
    }
    @Test fun explicitReloadKeepsAcceptedDeckAndReportsErrorsUntilSuccessfulRetry() {
        val api = Api(); val worker = Worker(); val posts = ArrayDeque<() -> Unit>()
        val controller = DeckLibraryController(api, { "token" }, worker) { posts.add(it) }
        fun deliver() { worker.drain(); while (posts.isNotEmpty()) posts.removeFirst()() }
        try {
            controller.open(summary.id); deliver()
            api.readFailure = true
            controller.retry()
            deliver()
            assertNotNull(controller.state.error)
            assertEquals(summary, controller.state.detail!!.saved.deck)
            controller.retry(); deliver()
            assertNotNull(controller.state.error)
            assertFalse(controller.state.busy)
            api.readFailure = false
            controller.retry(); deliver()
            assertNull(controller.state.error)
            assertEquals(summary, controller.state.detail!!.saved.deck)
        } finally { controller.close(); worker.drain() }
    }
    @Test fun uncertainMetadataRetryIsImmutableAndRefreshNeverInstallsAnOldOutcome() {
        val api = Api(); val worker = Worker(); val posts = mutableListOf<() -> Unit>()
        val controller = DeckLibraryController(api, { "token" }, worker) { posts += it }
        fun drain() { repeat(3) { worker.drain(); while (posts.isNotEmpty()) posts.removeAt(0)() } }
        controller.open(summary.id); drain(); controller.edit(DeckDialog.RENAME); controller.name(" Renamed 🧙 ")
        api.fail = true; controller.confirm(); controller.confirm(); drain()
        assertEquals(3, api.updates.size); assertEquals(1, api.updates.distinct().size); assertNotNull(controller.state.error)
        assertEquals(" Renamed 🧙 ", controller.state.draftName); assertEquals("Accepted", controller.state.detail!!.saved.deck.name)
        controller.retry(); drain(); controller.confirm(); drain()
        assertEquals(6, api.updates.size); assertEquals(1, api.updates.distinct().size)
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
        assertEquals(6, api.updates.size)
        assertNotEquals(api.updates[0].requestId, api.updates[3].requestId)
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
        controller.open(summary.id); deliver(); controller.retry(); worker.drain(); controller.back()
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
