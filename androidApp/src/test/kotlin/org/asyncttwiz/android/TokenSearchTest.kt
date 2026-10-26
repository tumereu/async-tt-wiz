package org.asyncttwiz.android

import kotlinx.coroutines.*
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import org.asyncttwiz.protocol.*
import org.asyncttwiz.storage.*
import org.junit.Test
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import kotlin.test.*

class TokenSearchTest {
    @get:Rule val temporary = TemporaryFolder()
    private fun id(n: Int) = "00000000-0000-4000-8000-${n.toString().padStart(12, '0')}"
    private fun definition(n: Int, name: String) = CardDefinition(name,
        listOf(DefinitionFace(id(n), name, rulesText = "Exact $n", sourceData = "{\"exact\":$n}")),
        provider = "synthetic", printingId = id(n), token = true).let { SubmittedDefinition(DeckJson.digest(it), it) }
    private val goblin = definition(1, "Goblin")
    private val spirit = definition(2, "Spirit")
    private fun cursor(generation: String = "generation-1", query: String = "") = CatalogTokenCursor(generation, query, "goblin", id(1))
    private data class Request(val query: String, val cursor: CatalogTokenCursor?, val result: CompletableDeferred<TokenSearchPage>)
    private class Source : TokenSearchSource {
        val requests = mutableListOf<Request>()
        var generation = "generation-1"
        var selectionCheck: CompletableDeferred<Boolean>? = null
        override suspend fun search(query: String, cursor: CatalogTokenCursor?): TokenSearchPage {
            val request = Request(query, cursor, CompletableDeferred())
            requests += request
            // Deliberately emulate a driver that finishes even after the coroutine was cancelled.
            return withContext(NonCancellable) { request.result.await() }
        }
        override suspend fun isCurrent(generation: String) = selectionCheck?.await() ?: (this.generation == generation)
        fun succeed(index: Int, definitions: List<SubmittedDefinition>, next: CatalogTokenCursor? = null,
            generation: String = this.generation) = requests[index].result.complete(TokenSearchPage(generation, definitions, next))
        fun fail(index: Int, error: Exception) = requests[index].result.completeExceptionally(error)
    }
    private fun scope() = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)

    @Test fun asyncLoadingPagingEmptyAndImmediateExactOnceSelectionUseOnePanelState() {
        val source = Source()
        val scope = scope()
        val sent = mutableListOf<SubmittedDefinition>()
        val search = TokenSearchController(source, scope, { true }, { sent += it; true })
        search.search()
        assertTrue(search.loading)
        assertEquals(listOf(TokenCatalog.blank), search.choices)
        assertNull(search.error)
        source.succeed(0, listOf(goblin), cursor())
        assertFalse(search.loading)
        assertEquals(listOf(TokenCatalog.blank, goblin), search.choices)
        search.more(); search.more()
        assertTrue(search.loading)
        assertEquals(2, source.requests.size, "Repeated load-more while pending cannot duplicate a page")
        assertEquals(cursor(), source.requests[1].cursor)
        source.succeed(1, listOf(goblin, spirit))
        assertEquals(listOf(TokenCatalog.blank, goblin, spirit), search.choices)
        assertNull(search.next)
        search.search("no match")
        assertTrue(search.loading)
        assertTrue(search.choices.isEmpty(), "Old query choices disappear immediately")
        source.succeed(2, emptyList())
        assertFalse(search.loading); assertTrue(search.choices.isEmpty()); assertNull(search.error)
        search.search("gob")
        source.succeed(3, listOf(goblin))
        search.select(goblin); search.select(goblin)
        assertEquals(listOf(goblin), sent, "One exact submitted definition, no confirm or local board success")
        search.close(); scope.cancel()
    }

    @Test fun absentFailedDownloadAndQueryErrorsRetainBlankAndAllowRetryWithoutFakeSuccess() {
        val source = Source()
        val scope = scope()
        var queued = false
        val attempts = mutableListOf<SubmittedDefinition>()
        val search = TokenSearchController(source, scope, { true }, { attempts += it; queued })
        search.search()
        source.fail(0, CatalogNotInstalledException())
        assertFalse(search.loading)
        assertTrue(search.error!!.contains("not installed"))
        assertEquals(listOf(TokenCatalog.blank), search.choices)
        search.select(TokenCatalog.blank)
        assertEquals(listOf(TokenCatalog.blank), attempts)
        queued = true
        search.select(TokenCatalog.blank); search.select(TokenCatalog.blank)
        assertEquals(listOf(TokenCatalog.blank, TokenCatalog.blank), attempts, "Refused enqueue can retry; accepted enqueue is once")
        search.close()

        val retry = TokenSearchController(source, scope, { true }, { true }, listOf(spirit))
        retry.search("spirit")
        source.fail(1, IllegalStateException("Catalog download failed. Retry card records."))
        assertTrue(retry.error!!.contains("download failed"))
        assertEquals(listOf(spirit), retry.choices, "Retained exact snapshots do not depend on a successful download")
        retry.search()
        assertNull(retry.error); assertTrue(retry.loading)
        source.succeed(2, listOf(spirit))
        assertNull(retry.error); assertFalse(retry.loading)
        assertEquals(listOf(spirit), retry.choices)
        retry.close(); scope.cancel()
    }

    @Test fun staleQueryAccountPlayAndClosedPanelCallbacksCannotPublishOrSubmit() {
        val source = Source()
        val scope = scope()
        var identity = "account-A/play-A"
        val captured = identity
        val sent = mutableListOf<SubmittedDefinition>()
        val search = TokenSearchController(source, scope, { identity == captured }, { sent += it; true })
        search.search("gob")
        search.search("spirit")
        source.succeed(1, listOf(spirit))
        source.succeed(0, listOf(goblin))
        assertEquals("spirit", search.query)
        assertEquals(listOf(spirit), search.choices)
        assertFalse(search.loading)
        identity = "account-B/play-A"
        search.select(spirit); search.select(TokenCatalog.blank)
        assertTrue(sent.isEmpty())
        search.search("other")
        assertEquals(2, source.requests.size, "Stale account cannot start catalog work")
        identity = captured
        search.search("gob")
        identity = "account-A/play-B"
        source.fail(2, IllegalStateException("old account/play error"))
        assertNull(search.error)
        search.close()
        search.select(TokenCatalog.blank)
        assertTrue(sent.isEmpty())

        val closed = TokenSearchController(source, scope, { true }, { sent += it; true })
        closed.search()
        closed.close()
        source.succeed(3, listOf(goblin))
        assertTrue(closed.downloaded.isEmpty()); assertFalse(closed.loading)
        closed.select(TokenCatalog.blank)
        assertTrue(sent.isEmpty())
        scope.cancel()
    }

    @Test fun changedGenerationRejectsStalePagesAndSelectionsButKeepsOrdinaryBlank() {
        val source = Source()
        val scope = scope()
        val sent = mutableListOf<SubmittedDefinition>()
        val search = TokenSearchController(source, scope, { true }, { sent += it; true })
        search.search()
        source.generation = "generation-2"
        source.succeed(0, listOf(goblin), generation = "generation-1")
        assertTrue(search.downloaded.isEmpty()); assertNotNull(search.error)
        search.search()
        source.succeed(1, listOf(goblin), cursor("generation-2"))
        search.more()
        source.generation = "generation-3"
        source.fail(2, CatalogGenerationChangedException())
        assertTrue(search.downloaded.isEmpty()); assertNull(search.next); assertNotNull(search.error)
        search.search()
        source.succeed(3, listOf(spirit))
        source.generation = "generation-4"
        search.select(spirit)
        assertTrue(sent.isEmpty()); assertTrue(search.downloaded.isEmpty()); assertNotNull(search.error)
        search.select(TokenCatalog.blank)
        assertEquals(listOf(TokenCatalog.blank), sent)
        search.close(); scope.cancel()
    }

    @Test fun delayedSelectionIsFencedAndDoubleTapDoesNotQueueTwice() {
        val source = Source()
        val scope = scope()
        var active = true
        val sent = mutableListOf<SubmittedDefinition>()
        val search = TokenSearchController(source, scope, { active }, { sent += it; true })
        search.search("gob")
        source.succeed(0, listOf(goblin))
        source.selectionCheck = CompletableDeferred()
        search.select(goblin); search.select(goblin)
        assertTrue(search.selecting); assertTrue(sent.isEmpty())
        active = false
        source.selectionCheck!!.complete(true)
        assertTrue(sent.isEmpty(), "No enqueue after logout/not-ready while generation check was suspended")
        search.close()
        source.selectionCheck = null

        val next = TokenSearchController(source, scope, { true }, { sent += it; true })
        next.search("gob")
        source.succeed(1, listOf(goblin))
        source.selectionCheck = CompletableDeferred()
        next.select(goblin)
        next.search("spirit")
        source.selectionCheck!!.complete(true)
        assertTrue(sent.isEmpty(), "Changing the query also fences a previously captured result click")
        source.selectionCheck = null
        source.succeed(2, listOf(spirit))
        next.select(spirit); next.select(spirit)
        assertEquals(listOf(spirit), sent)
        next.close(); scope.cancel()
    }

    @Test fun exactMultiFaceSnapshotsUseExistingEncodingAndNeverChooseAnOrdinaryCard() {
        fun face(n: Int, side: String, other: Int) = CatalogPrinting(id(n), if (side == "a") "Dawn Spirit" else "Dusk Spirit", "SYN", "1", "English",
            side, null, true, listOf(id(other)),
            """{"faceName":"Exact $side","type":"Token Creature — Spirit","text":"Raw $side Ω","power":"*","toughness":"2","layout":"transform"}""")
        val front = face(11, "a", 12)
        val back = face(12, "b", 11)
        val page = CatalogTokenPage("installed-1", CatalogMetadata("2026-10-23", "5.2.2"),
            listOf(CatalogToken(front, listOf(front, back)), CatalogToken(back, listOf(front, back))), null)
        val exact = TokenActions.definitions(page).single()
        assertEquals(id(11), exact.definition.printingId)
        assertEquals("2026-10-23/5.2.2", exact.definition.sourceVersion)
        assertEquals(listOf(id(11), id(12)), exact.definition.faces.map { it.faceId })
        assertEquals(listOf(front.json, back.json), exact.definition.faces.map { it.sourceData })
        assertEquals(listOf(listOf(id(12)), listOf(id(11))), exact.definition.faces.map { it.otherFaceIds })
        assertEquals(listOf("*/2", "*/2"), exact.definition.faces.map { it.corner })
        assertEquals(DeckJson.digest(exact.definition), exact.digest)
        assertTrue(TokenLinkRules.validDefinition(exact))
        val ordinary = exact.definition.copy(token = false).let { SubmittedDefinition(DeckJson.digest(it), it) }
        assertEquals(listOf(TokenCatalog.blank), TokenActions.catalog(listOf(ordinary), ""))
        val scope = scope()
        val source = Source()
        val sent = mutableListOf<SubmittedDefinition>()
        val search = TokenSearchController(source, scope, { true }, { sent += it; true })
        search.search("dusk")
        source.succeed(0, listOf(exact))
        assertEquals(listOf(exact), search.choices, "Matching a back face must not be filtered again by canonical front name")
        search.select(exact)
        assertSame(exact, sent.single(), "Selection forwards the exact loaded object, without rebuilding defaults")
        search.close(); scope.cancel()
    }

    @Test fun productionRepositoryFactoryRunsOffCallingUiThreadIncludingFailures() = runBlocking {
        val callingThread = Thread.currentThread()
        val factoryThreads = mutableListOf<Thread>()
        val source = RepositoryTokenSearchSource {
            factoryThreads += Thread.currentThread()
            throw CatalogNotInstalledException()
        }
        source.warm(prepare = true)
        assertFailsWith<CatalogNotInstalledException> { source.search("goblin", null) }
        assertFailsWith<CatalogNotInstalledException> { source.isCurrent("old-generation") }
        assertEquals(2, factoryThreads.size)
        assertTrue(factoryThreads.all { it !== callingThread })
        val scope = scope()
        val sent = mutableListOf<SubmittedDefinition>()
        val controller = TokenSearchController(source, scope, { true }, { sent += it; true }, localSearch = true)
        assertTrue(controller.error!!.contains("not installed"))
        controller.search()
        assertFalse(controller.loading)
        assertEquals(listOf(TokenCatalog.blank), controller.choices)
        controller.select(TokenCatalog.blank)
        assertEquals(listOf(TokenCatalog.blank), sent)
        controller.close(); scope.cancel()
    }

    @Test fun neverWarmedSourceResolvesMissingCatalogInsteadOfWaitingForStartupForever() = runBlocking {
        val source = RepositoryTokenSearchSource { throw CatalogNotInstalledException() }
        withTimeout(5_000) {
            assertFailsWith<CatalogNotInstalledException> { source.search("", null) }
        }
        assertTrue(source.cachedError()!!.contains("not installed"))
    }

    @Test fun productionSourceReusesPreparedSnapshotUntilImportPublishesAnotherGeneration() = runBlocking {
        var opens = 0
        val repository = CatalogRepository(temporary.newFolder("catalog")) {
            opens++
            JdbcSqliteDriver("jdbc:sqlite:${it.absolutePath}")
        }
        fun install(name: String) = repository.stage { index ->
            index.addBatch(listOf(CatalogPrinting(id(1), name, "SYN", "1", "English", "", id(2), true,
                emptyList(), """{"type":"Token Creature","power":"1","toughness":"1"}""")))
            CatalogMetadata("same-metadata", "5")
        }
        install("Goblin")
        val source = RepositoryTokenSearchSource { repository }
        source.warm()
        val page = source.cached()!!
        val reads = opens
        repeat(3) {
            source.warm()
            assertSame(page, source.cached())
            assertSame(page, source.search("", null))
        }
        assertEquals(reads, opens, "Cached generations never reread/decode definitions")
        install("Spirit")
        assertFalse(source.isCurrent(page.generation))
        source.warm()
        assertNotSame(page, source.cached())
        assertEquals(listOf("Spirit"), source.cached()!!.definitions.map { it.definition.name })
        assertEquals(listOf("Goblin"), page.definitions.map { it.definition.name })
    }

    @Test fun cachedOrderingMatchesFavoritesSemanticsAndFindsBackFaceNames() {
        val front = definition(10, "Dawn Spirit")
        val double = front.definition.copy(layout = "transform", faces = listOf(
            front.definition.faces.single().copy(otherFaceIds = listOf(id(11))),
            DefinitionFace(id(11), "Dusk Spirit", otherFaceIds = listOf(id(10)))))
            .let { SubmittedDefinition(DeckJson.digest(it), it) }
        val definitions = listOf(spirit, goblin, definition(12, "Food"), double)
        val index = TokenChoiceIndex(definitions.map(CatalogTokenChoice::from))
        for (favorites in listOf(TokenFavorites(), TokenFavorites(emptyList()), TokenFavorites(listOf(TokenIdentity.key(spirit.definition))))) {
            val ordered = index.ordered(favorites)
            assertEquals(TokenActions.ordered(listOf(TokenCatalog.blank) + definitions, favorites), ordered.map { it.submitted })
            assertEquals(listOf(double), TokenChoiceIndex.filter(ordered, " DuSk "))
            assertTrue(TokenChoiceIndex.filter(ordered, "not found").isEmpty())
        }
    }

    @Test fun startupFailureWhileTypingCannotLeaveLoadingSpinnerRunning() {
        val completion = CompletableDeferred<TokenSearchPage>()
        var failure: String? = null
        val source = object : TokenSearchSource {
            override fun cachedError() = failure
            override suspend fun search(query: String, cursor: CatalogTokenCursor?) = completion.await()
            override suspend fun isCurrent(generation: String) = true
        }
        val scope = scope()
        val controller = TokenSearchController(source, scope, { true }, { true }, localSearch = true)
        controller.search()
        assertTrue(controller.loading)
        failure = CatalogNotInstalledException().message
        controller.search("Blank")
        assertFalse(controller.loading)
        assertEquals(listOf(TokenCatalog.blank), controller.choices)
        assertEquals(failure, controller.error)
        controller.close(); scope.cancel()
    }

    @Test fun cachedGenerationIsImmediateAcrossOpensAndFilteringFiveThousandTokensIsCheap() {
        val definitions = (1..5000).map { definition(it, "Token ${it.toString().padStart(4, '0')}") }
        val page = TokenSearchPage("prepared", definitions, null, definitions.map(CatalogTokenChoice::from))
        var searches = 0
        val source = object : TokenSearchSource {
            override fun cached() = page
            override suspend fun search(query: String, cursor: CatalogTokenCursor?): TokenSearchPage { searches++; error("Not on open") }
            override suspend fun isCurrent(generation: String) = true
        }
        val scope = scope()
        repeat(2) {
            val controller = TokenSearchController(source, scope, { true }, { true }, localSearch = true)
            assertSame(page.definitions, controller.downloaded)
            assertFalse(controller.loading)
            assertEquals(5001, controller.choices.size)
            controller.search()
            controller.search("TOKEN 0123")
            assertEquals(listOf(definitions[122]), controller.choices)
            assertFalse(controller.loading)
            controller.close()
        }
        assertEquals(0, searches)
        val ordered = page.index!!.ordered(TokenFavorites())
        repeat(30) { TokenChoiceIndex.filter(ordered, "token 01") }
        val start = System.nanoTime()
        repeat(100) { TokenChoiceIndex.filter(ordered, listOf("t", "TO", "token 0", "0123", "not found")[it % 5]) }
        val averageMs = (System.nanoTime() - start) / 100.0 / 1_000_000
        assertTrue(averageMs < 16, "5000-token per-keystroke filtering averaged $averageMs ms")
        scope.cancel()
    }

    @Test fun realTokenControllerQueuesExactlyOneCreateWithoutLocalBoardMutationOrExtraConfirmation() {
        var current = PlayState(id(20), id(21), id(22), id(23), PlayRoot(id(24), id(25)), id(24),
            BoardState(listOf(PlayPlayer(id(26)), PlayPlayer(id(27))), emptyList(), emptyList(), TurnState(id(26))),
            listOf(TokenCatalog.blank), emptyList(), InitialStarter(StarterChoice(id(26)), id(26)))
        val before = current
        val actions = mutableListOf<PlayAction>()
        val tokenLinks = TokenLinkController({ current }) { actions += it; true }
        tokenLinks.catalog()
        val source = Source()
        val scope = scope()
        val search = TokenSearchController(source, scope, { tokenLinks.showingCatalog },
            { tokenLinks.create(it); !tokenLinks.showingCatalog })
        search.search("gob")
        source.succeed(0, listOf(goblin))
        search.select(goblin); search.select(goblin)
        assertEquals<List<PlayAction>>(listOf(PlayAction.CreateToken(goblin)), actions)
        assertFalse(tokenLinks.showingCatalog)
        assertEquals(before, current)
        assertTrue(current.board.cards.isEmpty())
        search.close()
        tokenLinks.catalog()
        current = current.copy(id = id(28))
        tokenLinks.create(TokenCatalog.blank)
        assertEquals(1, actions.size, "Real request adapter also rejects an old play incarnation")
        scope.cancel()
    }
}
