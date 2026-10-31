package org.asyncttwiz.android

import org.asyncttwiz.protocol.*
import org.junit.Test
import java.util.UUID
import java.util.concurrent.AbstractExecutorService
import java.util.concurrent.TimeUnit
import kotlin.test.*

class LobbyPreparationTest {
    private fun id() = UUID.randomUUID().toString()
    private val owner = AccountIdentity(id(), "Inviter")
    private val other = AccountIdentity(id(), "Recipient")
    private val game = GameSummary(id(), "Azure Spark", "2026-10-22T00:00:00Z", owner, other, id(), id(), InvitationState.PENDING)
    private val definition = CardDefinition("Retained card", listOf(DefinitionFace(id(), "Retained card")))
    private val submitted = SubmittedDefinition(DeckJson.digest(definition), definition)
    private val source = SavedDeck(DeckSummary(id(), "Selected copy", "1", game.createdAt, 4, 2), listOf(
        SavedDeckEntry(SavedDeckSection.MAINBOARD, submitted.digest, 4), SavedDeckEntry(SavedDeckSection.SIDEBOARD, submitted.digest, 2)))
    private val alternate = source.copy(deck = source.deck.copy(id = id(), name = "Alternate"))
    private class Worker : AbstractExecutorService() {
        val queue = ArrayDeque<Runnable>()
        override fun execute(command: Runnable) { queue.add(command) }
        fun drain() { while (queue.isNotEmpty()) queue.removeFirst().run() }
        override fun shutdown() = Unit
        override fun shutdownNow() = mutableListOf<Runnable>()
        override fun isShutdown() = false
        override fun isTerminated() = false
        override fun awaitTermination(timeout: Long, unit: TimeUnit) = false
    }
    private class Wire : GameConnection {
        lateinit var listener: GameConnection.Listener
        val frames = mutableListOf<GameClientMessage>()
        override fun connect(token: String, listener: GameConnection.Listener) { this.listener = listener }
        override fun send(message: GameClientMessage): Boolean { frames.add(message); return true }
        override fun disconnect() = Unit
        override fun close() = Unit
    }
    private inner class Fixture : AutoCloseable {
        val worker = Worker(); val authWorker = Worker(); val posts = ArrayDeque<() -> Unit>(); val wire = Wire()
        var identity = owner; var decks = listOf(source, alternate); var draftDecks = emptyList<SavedDeck>(); var readFailure: String? = null
        var base = PreparationRules.empty(game).copy(players = PreparationRules.empty(game).players.map { it.copy(ready = true) })
        lateinit var lobby: LobbyController
        val auth = AuthController(object : Accounts {
            override suspend fun identity(token: String) = identity
            override suspend fun register(username: String, email: String, password: String) = identity
            override suspend fun login(email: String, password: String) = SessionCreated("B".repeat(43), identity)
            override suspend fun revoke(token: String) = Unit
            override fun close() = Unit
        }, object : SessionStore {
            override fun load() = StoredSession.Present("A".repeat(43))
            override fun save(token: String) = Unit
            override fun clear() = Unit
        }, authWorker, clearPrivateWork = { lobby.clear() }, post = { posts.add(it) })
        private val api = object : Lobbies {
            override suspend fun search(token: String, query: String) = UserSearch(emptyList())
            override suspend fun contacts(token: String, after: String?) = ContactList(emptyList())
            override suspend fun add(token: String, input: AddContact) = other
            override suspend fun create(token: String, input: CreateInvitation) = game
            override suspend fun games(token: String, invitations: Boolean, after: String?) = GameList(if (invitations) emptyList() else listOf(game))
            override suspend fun head(token: String, id: String) = game // Deliberately stale after socket updates.
            override fun close() = Unit
        }
        private val deckApi = object : Decks {
            override suspend fun list(token: String, after: String?) = DeckList(decks.map { it.deck })
            override suspend fun drafts(token: String) = DeckList(draftDecks.map { it.deck })
            override suspend fun read(token: String, id: String): SavedDeck {
                readFailure?.let { if (it == "authentication_required") auth.authenticationRequired(token); throw DeckFailure(it) }
                return (decks + draftDecks).find { it.deck.id == id } ?: throw DeckFailure("not_found")
            }
            override suspend fun definition(token: String, id: String, digest: String) = submitted
            override suspend fun submit(token: String, value: SubmitDefinitions): DefinitionsAccepted = error("Not a selection endpoint")
            override suspend fun create(token: String, value: CreateDeck): SavedDeck = error("Not a selection endpoint")
            override suspend fun update(token: String, id: String, value: UpdateDeck): SavedDeck = error("No source mutation")
            override suspend fun delete(token: String, id: String, value: DeleteDeck): DeckDeleted = error("No source mutation")
            override fun close() = Unit
        }
        init {
            lobby = LobbyController(api, auth::sessionToken, worker, { posts.add(it) }, gameConnection = wire,
                accountId = { auth.state.account?.accountId }, authenticationRequired = auth::authenticationRequired, deckApi = deckApi)
            drain(); lobby.open(game.id); drain(); ready()
        }
        fun drain() {
            while (worker.queue.isNotEmpty() || authWorker.queue.isNotEmpty() || posts.isNotEmpty()) {
                worker.drain(); authWorker.drain(); while (posts.isNotEmpty()) posts.removeFirst()()
            }
        }
        fun message(message: GameServerMessage) { wire.listener.message(message); drain() }
        fun ready(statuses: List<GameRequestStatus> = emptyList(), generation: String = "1") {
            wire.listener.opened(); drain(); message(GameWelcome(gameplayRulesVersion = CURRENT_GAMEPLAY_RULES_VERSION))
            val stream = wire.frames.mapNotNull { when (it) { is OpenGameStream -> it.streamId; is ResumeGameStream -> it.streamId; else -> null } }.last()
            message(GameStreamReady(game.id, stream, generation, statuses)); head(game, base)
        }
        fun head(summary: GameSummary, preparation: LobbyPreparation) = message(GameHead(game.id, summary.revision, summary, preparation = preparation))
        fun choose(saved: SavedDeck = source): GameCommand {
            lobby.chooseDeck(); drain(); lobby.highlightDeck(saved.deck); lobby.confirmDeck(); drain()
            return wire.frames.filterIsInstance<GameCommand>().last()
        }
        fun accepted(command: GameCommand) = PreparationRules.select(game, base, owner.accountId, command.requestId,
            command.scope, command.action as GameAction.SelectDeck, source, listOf(submitted)).state!!
        fun result(command: GameCommand, outcome: String = "accepted", reason: String? = null) =
            message(GameResult(command.requestId, command.scope, outcome, reason, "1", if (outcome == "accepted") "1" else null))
        fun login() { auth.edit(email = "wizard@example.test", password = "password"); auth.submit(); drain() }
        override fun close() { lobby.close(); auth.close(); drain() }
    }

    @Test fun pickerDraftIsPersonalAndCancelNeverChangesAcceptedPreparationOrReady() = Fixture().use { f ->
        f.lobby.chooseDeck(); f.drain(); assertNull(f.lobby.state.picker!!.draft)
        f.lobby.highlightDeck(source.deck); f.lobby.highlightDeck(alternate.deck)
        f.lobby.sync(); f.drain() // Same controller is retained by the normal ViewModel on rotation.
        assertEquals(alternate.deck, f.lobby.state.picker!!.draft)
        assertEquals(f.base, f.lobby.state.acceptedPreparation); assertEquals(f.base, f.lobby.state.preparation)
        assertTrue(f.wire.frames.filterIsInstance<GameCommand>().isEmpty())
        f.lobby.back(); assertNull(f.lobby.state.picker); assertEquals(game.id, f.lobby.state.selected)
        f.lobby.chooseDeck(); f.drain(); f.lobby.highlightDeck(source.deck); f.lobby.confirmDeck(); f.lobby.closePicker(); f.drain()
        assertTrue(f.wire.frames.filterIsInstance<GameCommand>().isEmpty()) // Close fences a delayed source read.
    }

    @Test fun oneConfirmationPredictsWithSharedRulesButReceiptAndResultDoNotInstallAcceptedState() = Fixture().use { f ->
        f.lobby.chooseDeck(); f.drain(); f.lobby.highlightDeck(source.deck); f.lobby.confirmDeck(); f.lobby.confirmDeck(); f.drain()
        val command = f.wire.frames.filterIsInstance<GameCommand>().single()
        assertTrue(GameWire.valid(command)); assertEquals(GameScope(game.id, game.preparationRound), command.scope)
        assertNull(f.lobby.state.picker); assertEquals(f.base, f.lobby.state.acceptedPreparation)
        val predicted = f.accepted(command); assertEquals(predicted, f.lobby.state.preparation)
        assertFalse(predicted.players[0].ready); assertTrue(predicted.players[1].ready)
        f.message(GameReceipt(command.requestId, command.scope, "1")); assertEquals(predicted, f.lobby.state.preparation)
        f.result(command); assertEquals(f.base, f.lobby.state.acceptedPreparation)
        assertEquals(predicted, f.lobby.state.preparation); assertTrue(game.id in f.lobby.state.pendingGames)
        f.head(game.copy(revision = "1"), predicted)
        assertEquals(predicted, f.lobby.state.acceptedPreparation); assertTrue(f.lobby.state.pendingGames.isEmpty())
        f.head(game, f.base); f.lobby.sync(); f.drain()
        assertEquals(predicted, f.lobby.state.preparation); assertEquals("1", f.lobby.state.detail!!.revision)
    }

    @Test fun revisionBeforeResultDoesNotReapplyPredictionOverNewerOtherDeviceChoiceOrReady() = Fixture().use { f ->
        val command = f.choose(); val applied = f.accepted(command)
        f.head(game.copy(revision = "1"), applied)
        val newer = applied.copy(players = applied.players.map { if (it.accountId == owner.accountId) it.copy(ready = true,
            deck = it.deck!!.copy(name = "Newer device choice", selectionRequestId = id())) else it })
        f.head(game.copy(invitationId = id(), invitationState = InvitationState.ACCEPTED, revision = "2"), newer)
        assertEquals(newer, f.lobby.state.preparation)
        f.result(command); assertEquals(newer, f.lobby.state.preparation)
        assertEquals(newer, f.lobby.state.acceptedPreparation)
    }

    // LOB-053
    @Test fun staleDeletedAndChangedSourcesNeverSubstituteAndServerRejectionRemovesOnlyPrediction() {
        for (failure in listOf("changed", "deleted", "serverChanged")) Fixture().use { f ->
            f.lobby.chooseDeck(); f.drain(); f.lobby.highlightDeck(source.deck)
            if (failure == "changed") f.decks = listOf(source.copy(deck = source.deck.copy(revision = "2")), alternate)
            if (failure == "deleted") f.decks = listOf(alternate)
            f.lobby.confirmDeck(); f.drain()
            if (failure == "serverChanged") {
                val command = f.wire.frames.filterIsInstance<GameCommand>().single()
                f.result(command, "rejected", "sourceChanged"); assertNull(f.lobby.state.picker)
                assertTrue(f.lobby.state.connectionMessage!!.contains("changed"))
            } else {
                assertNotNull(f.lobby.state.picker!!.error); assertTrue(f.wire.frames.filterIsInstance<GameCommand>().isEmpty())
                assertEquals(source.deck, f.lobby.state.picker!!.draft)
            }
            assertEquals(f.base, f.lobby.state.acceptedPreparation); assertEquals(f.base, f.lobby.state.preparation)
        }
    }

    // DRF-133, LOB-040, LOB-055
    @Test fun draftDecksArePlayableAndListedAfterOrdinaryDecksUnderTheirOwnHeading() = Fixture().use { f ->
        val draft = source.copy(deck = source.deck.copy(id = id(), name = "Friday pod draft", draft = DraftDeckOrigin(id(), "Friday pod")))
        f.decks = listOf(source); f.draftDecks = listOf(draft)
        f.lobby.chooseDeck(); f.drain()
        assertEquals(listOf(source.deck, draft.deck), f.lobby.state.picker!!.decks)
        assertEquals(listOf(null to listOf(source.deck), "Draft decks" to listOf(draft.deck)), deckPickerSections(f.lobby.state.picker!!.decks))
        f.lobby.highlightDeck(draft.deck); f.lobby.confirmDeck(); f.drain()
        assertEquals(GameAction.SelectDeck(draft.deck.id, "1"), f.wire.frames.filterIsInstance<GameCommand>().last().action)
    }

    @Test fun emptyCollectionAndContextualImportReturnDoNotAutoSelectOrSend() = Fixture().use { f ->
        f.decks = emptyList(); f.lobby.chooseDeck(); f.drain(); assertTrue(f.lobby.state.picker!!.decks.isEmpty())
        f.lobby.confirmDeck(); assertTrue(f.wire.frames.filterIsInstance<GameCommand>().isEmpty())
        f.lobby.importForPicker(); assertTrue(f.lobby.state.picker!!.importing)
        f.decks = listOf(source); assertTrue(f.lobby.importedForPicker()); f.drain()
        assertFalse(f.lobby.state.picker!!.importing); assertNull(f.lobby.state.picker!!.draft)
        assertEquals(listOf(source.deck), f.lobby.state.picker!!.decks)
        assertFalse(f.lobby.importedForPicker()); assertTrue(f.wire.frames.filterIsInstance<GameCommand>().isEmpty())
    }

    // LOB-041
    @Test fun deckSavedFromPickerImportComesBackHighlightedButNotConfirmed() = Fixture().use { f ->
        f.lobby.chooseDeck(); f.drain(); f.lobby.highlightDeck(alternate.deck); f.lobby.importForPicker()
        val imported = source.copy(deck = source.deck.copy(id = id(), name = "Imported"))
        f.decks = listOf(source, alternate, imported)
        assertTrue(f.lobby.importedForPicker(imported.deck))
        assertEquals(imported.deck, f.lobby.state.picker!!.draft); assertFalse(f.lobby.state.picker!!.importing)
        f.drain()
        assertEquals(imported.deck, f.lobby.state.picker!!.draft)
        assertEquals(1, f.lobby.state.picker!!.decks.count { it.id == imported.deck.id })
        assertTrue(f.wire.frames.filterIsInstance<GameCommand>().isEmpty())
        // Saves that did not start in the picker report false: the caller opens the Saved deck page.
        assertFalse(f.lobby.importedForPicker(imported.deck))
        f.lobby.closePicker(); assertFalse(f.lobby.importedForPicker(imported.deck))
    }

    // LOB-045, SYS-018
    @Test fun chooseDeckWorksWhileReconnectingAndTheSelectionIsSentOnceConnected() {
        for (leave in listOf(false, true)) Fixture().use { f ->
            f.wire.listener.disconnected(false); f.drain()
            assertFalse(game.id in f.lobby.state.connectedGames)
            f.lobby.chooseDeck(); f.drain()
            assertEquals(listOf(source.deck, alternate.deck), f.lobby.state.picker!!.decks)
            f.lobby.highlightDeck(source.deck); f.lobby.confirmDeck(); f.drain(); f.lobby.confirmDeck(); f.drain()
            assertTrue(f.lobby.state.picker!!.confirming); assertNull(f.lobby.state.picker!!.error)
            assertEquals(setOf(game.id), f.lobby.state.queuedGames)
            if (leave) f.lobby.closePicker()
            f.lobby.sync(); f.drain(); f.ready(generation = "2")
            val commands = f.wire.frames.filterIsInstance<GameCommand>()
            if (leave) assertTrue(commands.isEmpty())
            else assertEquals(GameAction.SelectDeck(source.deck.id, source.deck.revision), commands.single().action)
            assertNull(f.lobby.state.picker); assertTrue(f.lobby.state.queuedGames.isEmpty())
        }
    }

    @Test fun sameAccountReauthKeepsPickerAndUncertainOriginalButLogoutAndAccountSwitchFenceBoth() {
        for (switch in listOf(false, true)) Fixture().use { f ->
            f.lobby.chooseDeck(); f.drain(); f.lobby.highlightDeck(source.deck)
            f.readFailure = "authentication_required"; f.lobby.confirmDeck(); f.drain(); assertNull(f.auth.state.account)
            assertEquals(source.deck, f.lobby.state.picker!!.draft)
            if (switch) f.identity = other
            f.login()
            if (switch) {
                assertNull(f.lobby.state.picker); assertNull(f.lobby.state.preparation)
            } else {
                f.readFailure = null; f.lobby.sync(); f.drain(); f.ready(generation = "2")
                f.lobby.confirmDeck(); f.drain(); val original = f.wire.frames.filterIsInstance<GameCommand>().single()
                val predicted = f.lobby.state.preparation; val old = f.wire.listener
                f.message(GameAdmissionError("unauthorized", false)); f.login(); f.lobby.sync(); f.drain()
                f.ready(listOf(GameRequestStatus(original.requestId, "notAdmitted")), "3")
                assertEquals(original.copy(generation = "3"), f.wire.frames.filterIsInstance<GameCommand>().last())
                assertEquals(predicted, f.lobby.state.preparation)
                old.message(GameHead(game.id, "99", game.copy(revision = "99"), preparation = f.base)); f.drain()
                assertEquals(predicted, f.lobby.state.preparation)
            }
            val stale = f.wire.listener; f.auth.logout(); f.drain()
            stale.message(GameHead(game.id, "100", game.copy(revision = "100"), preparation = f.base)); f.drain()
            assertNull(f.lobby.state.preparation); assertNull(f.lobby.state.picker); assertTrue(f.lobby.state.pendingGames.isEmpty())
        }
    }

    @Test fun newProcessReadsAcceptedOnlyAndSameRoundInvitationChangesPreserveCopy() {
        val accepted = Fixture().use { f ->
            val command = f.choose(); val state = f.accepted(command)
            f.head(game.copy(revision = "1"), state); f.result(command)
            for (invitation in InvitationState.entries) f.head(game.copy(invitationId = id(), invitationState = invitation, revision = "2"), state)
            assertEquals(state, f.lobby.state.preparation); state
        }
        Fixture().use { fresh ->
            fresh.head(game.copy(revision = "2"), accepted)
            assertEquals(accepted, fresh.lobby.state.preparation)
            assertTrue(fresh.wire.frames.filterIsInstance<GameCommand>().isEmpty()); assertNull(fresh.lobby.state.picker)
        }
    }

    @Test fun changePickerCancelKeepsExistingAcceptedCopyAndNewRoundOrStartCannotSubmitOldDraft() = Fixture().use { f ->
        val command = f.choose(); val applied = f.accepted(command)
        f.head(game.copy(revision = "1"), applied); f.result(command)
        f.lobby.chooseDeck(); f.drain(); f.lobby.highlightDeck(alternate.deck)
        f.lobby.back(); assertEquals(applied, f.lobby.state.preparation)
        assertEquals(1, f.wire.frames.filterIsInstance<GameCommand>().size)
        f.lobby.chooseDeck(); f.drain(); f.lobby.highlightDeck(alternate.deck)
        f.lobby.confirmDeck() // Read is queued but the accepted lifecycle changes before its callback.
        val next = game.copy(preparationRound = id(), revision = "2")
        f.wire.listener.message(GameHead(game.id, next.revision, next, preparation = PreparationRules.empty(next)))
        // Deliver the revision before the queued source-read callback, as the normal main queue can.
        f.posts.removeFirst()(); f.drain()
        assertEquals(1, f.wire.frames.filterIsInstance<GameCommand>().size); assertNotNull(f.lobby.state.picker!!.error)
        f.lobby.closePicker()
        val active = next.copy(lifecycle = GameLifecycle.ACTIVE, revision = "3")
        f.head(active, PreparationRules.empty(active)); f.lobby.chooseDeck(); assertNull(f.lobby.state.picker)
    }

    @Test fun productionControllerCapturesGestureStartThenSubmitsMultiplePerCardTransfersWithoutInvitationPendingGate() = Fixture().use { f ->
        val selection = f.choose(); val accepted = f.accepted(selection)
        f.head(game.copy(revision = "1"), accepted); f.result(selection)
        val captured = f.lobby.captureTransfer(submitted.digest, SavedDeckSection.MAINBOARD, true)!!
        assertEquals(4L, captured.count)
        val side = PreparationRules.capture(accepted, owner.accountId, submitted.digest, SavedDeckSection.SIDEBOARD, false)!!
        val arrival = PreparationRules.transfer(game, accepted, owner.accountId, GameScope(game.id, game.preparationRound),
            side.action(side.entries.single(), 0)).state!!
        f.head(game.copy(revision = "2"), arrival)
        assertTrue(f.lobby.transfer(captured))
        assertEquals("4× Retained card → Sideboard", f.lobby.state.transferFeedback?.text)
        val commands = f.wire.frames.filterIsInstance<GameCommand>().drop(1)
        assertEquals(4, commands.size)
        assertEquals(listOf("0", "1", "2", "3"), commands.map { (it.action as GameAction.TransferPreparedCard).cardIndex })
        assertEquals(1L, f.lobby.state.preparation!!.players[0].deck!!.count(SavedDeckSection.MAINBOARD))
        assertEquals(arrival, f.lobby.state.acceptedPreparation)
        val next = f.lobby.captureTransfer(submitted.digest, SavedDeckSection.MAINBOARD, false)!!
        assertTrue(f.lobby.transfer(next)); assertEquals(6, f.wire.frames.filterIsInstance<GameCommand>().size)
        assertEquals(0L, f.lobby.state.preparation!!.players[0].deck!!.count(SavedDeckSection.MAINBOARD))
        val stale = f.wire.listener; f.auth.logout(); f.drain()
        assertNull(f.lobby.state.transferFeedback); assertNull(f.lobby.inspection.state)
        stale.message(GameHead(game.id, "99", game.copy(revision = "99"), preparation = accepted)); f.drain()
        assertNull(f.lobby.state.preparation); assertFalse(f.lobby.transfer(captured))
    }

    @Test fun transfersComposeAfterUnacknowledgedSelectionAndAccountSwitchClearsOriginalsAndCapturedTail() = Fixture().use { f ->
        val selection = f.choose() // No receipt, result or revision yet.
        val selected = f.accepted(selection)
        val captured = f.lobby.captureTransfer(submitted.digest, SavedDeckSection.MAINBOARD, true)!!
        assertTrue(f.lobby.transfer(captured))
        val transfers = f.wire.frames.filterIsInstance<GameCommand>().drop(1)
        assertEquals(4, transfers.size); assertEquals(f.base, f.lobby.state.acceptedPreparation)
        assertEquals(0L, f.lobby.state.preparation!!.players[0].deck!!.count(SavedDeckSection.MAINBOARD))
        f.head(game.copy(revision = "1"), selected)
        assertEquals(0L, f.lobby.state.preparation!!.players[0].deck!!.count(SavedDeckSection.MAINBOARD))
        val old = f.wire.listener
        f.message(GameAdmissionError("unauthorized", false)); f.identity = other; f.login()
        assertNull(f.lobby.state.preparation); assertTrue(f.lobby.state.pendingGames.isEmpty())
        old.message(GameHead(game.id, "99", game.copy(revision = "99"), preparation = selected)); f.drain()
        assertNull(f.lobby.state.preparation); assertFalse(f.lobby.transfer(captured))
    }
}
