package org.asyncttwiz.android

import java.util.UUID
import java.util.concurrent.AbstractExecutorService
import java.util.concurrent.TimeUnit
import org.asyncttwiz.protocol.*
import org.junit.Test
import kotlin.test.*

class LobbyCancellationTest {
    private fun id() = UUID.randomUUID().toString()
    private val owner = AccountIdentity(id(), "Owner")
    private val other = AccountIdentity(id(), "Other")
    private val game = GameSummary(id(), "Spark", "2026-10-23T00:00:00Z", owner, other, id(), id(), InvitationState.ACCEPTED)
    private fun prepared(): LobbyPreparation {
        val definition = CardDefinition("Retained card", listOf(DefinitionFace(id(), "Face")))
        val submitted = SubmittedDefinition(DeckJson.digest(definition), definition)
        val source = SavedDeck(DeckSummary(id(), "Source", "1", game.createdAt, 3, 0), listOf(
            SavedDeckEntry(SavedDeckSection.MAINBOARD, submitted.digest, 3)))
        var state = PreparationRules.empty(game)
        for (actor in listOf(owner, other)) state = PreparationRules.select(game, state, actor.accountId, id(), GameScope(game.id, game.preparationRound),
            GameAction.SelectDeck(source.deck.id, source.deck.revision), source, listOf(submitted)).state!!
        return state.copy(starter = StarterChoice(owner.accountId))
    }
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
    private inner class Fixture(val actor: AccountIdentity = owner, decks: Decks? = null) : AutoCloseable {
        val worker = Worker(); val posts = ArrayDeque<() -> Unit>(); val wire = Wire()
        var httpGame = game; var visible = true; var createFails = false
        val creates = mutableListOf<CreateInvitation>(); var opens = 0
        val lobby = LobbyController(object : Lobbies {
            override suspend fun search(token: String, query: String) = UserSearch(emptyList())
            override suspend fun contacts(token: String, after: String?) = ContactList(listOf(other))
            override suspend fun add(token: String, input: AddContact) = other
            override suspend fun create(token: String, input: CreateInvitation): GameSummary {
                creates.add(input); if (createFails) throw LobbyFailure("network"); return game
            }
            override suspend fun games(token: String, invitations: Boolean, after: String?) = GameList(
                if (visible && invitations == (actor == other && httpGame.invitationState == InvitationState.PENDING)) listOf(httpGame) else emptyList())
            override suspend fun head(token: String, id: String): GameSummary {
                if (!visible) throw LobbyFailure("not_found"); return httpGame
            }
            override fun close() = Unit
        }, { "token" }, worker, { posts.add(it) }, openGame = { opens++ }, gameConnection = wire, accountId = { actor.accountId }, deckApi = decks)
        init { lobby.open(game.id); drain(); connect(); head(game, PreparationRules.empty(game)) }
        fun drain() { while (worker.queue.isNotEmpty() || posts.isNotEmpty()) {
            worker.drain(); while (posts.isNotEmpty()) posts.removeFirst()()
        } }
        fun message(message: GameServerMessage) { wire.listener.message(message); drain() }
        fun connect() {
            wire.listener.opened(); drain(); message(GameWelcome())
            val stream = wire.frames.filterIsInstance<OpenGameStream>().last().streamId
            message(GameStreamReady(game.id, stream, "1"))
        }
        fun head(summary: GameSummary, preparation: LobbyPreparation? = null, play: PlayState? = null) =
            message(GameHead(game.id, summary.revision, summary, preparation = preparation, play = play))
        fun cancelRevision(revision: String = "2") = GameRevision(game.id, "1", revision, id(), other.accountId,
            GameScope(game.id, game.preparationRound), game.copy(lifecycle = GameLifecycle.CANCELLED, revision = revision))
        fun commands() = wire.frames.filterIsInstance<GameCommand>()
        override fun close() { lobby.close(); drain() }
    }
    @Test fun remoteCancellationFencesStaleHttpAndLateAcceptOutcomeCannotNavigateOrResurrect() = Fixture(other).use { f ->
        val pending = game.copy(invitationState = InvitationState.PENDING, revision = "1")
        f.head(pending); f.httpGame = pending
        f.lobby.respond(pending, true); val accept = f.commands().single()
        f.lobby.refresh(); f.worker.drain() // retain old completed HTTP callback until after cancellation
        f.wire.listener.message(f.cancelRevision()); f.posts.removeLast()()
        assertNull(f.lobby.state.selected); assertTrue(f.lobby.state.games.isEmpty()); assertTrue(f.lobby.state.invitations.isEmpty())
        f.drain(); assertNull(f.lobby.state.selected); assertTrue(f.lobby.state.invitations.isEmpty())
        val opens = f.opens
        f.message(GameResult(accept.requestId, accept.scope, "accepted", ingressOrdinal = "1", commitRevision = "1"))
        assertEquals(opens, f.opens); assertNull(f.lobby.state.selected)
        assertTrue(f.lobby.state.games.isEmpty()); assertTrue(f.lobby.state.invitations.isEmpty())
        assertNull(f.lobby.state.preparation); assertNull(f.lobby.state.play)
    }
    @Test fun eitherParticipantDispatchesCapturedRoundButNeverCurrentReplacementOrStartedPlay() {
        for (actor in listOf(owner, other)) Fixture(actor).use { f ->
            assertTrue(f.lobby.cancelSetup(game)); val command = f.commands().single()
            assertEquals(GameAction.CancelSetup, command.action); assertEquals(GameScope(game.id, game.preparationRound), command.scope)
            assertFalse(f.lobby.cancelSetup(game))
            f.message(GameResult(command.requestId, command.scope, "accepted", ingressOrdinal = "1", commitRevision = "1"))
            assertNotNull(f.lobby.state.detail) // A result is not accepted lifecycle state.
            f.message(f.cancelRevision()); assertFalse(f.lobby.cancelSetup(game)); assertNull(f.lobby.state.selected)
        }
        Fixture().use { f ->
            val fresh = game.copy(preparationRound = id(), revision = "1"); f.head(fresh, PreparationRules.empty(fresh))
            assertFalse(f.lobby.cancelSetup(game)); assertTrue(f.commands().isEmpty())
            f.head(fresh.copy(lifecycle = GameLifecycle.ACTIVE, revision = "2")); assertFalse(f.lobby.cancelSetup(fresh))
        }
    }
    @Test fun negativeHeadAndListOmissionRemoveContextWithoutDiscardingOtherListsOrRevivingFromCreateOutcome() = Fixture().use { f ->
        f.lobby.begin(other); f.lobby.name("Original"); f.createFails = true; f.lobby.create(); f.drain()
        assertTrue(f.lobby.state.uncertain)
        f.visible = false; f.lobby.refresh(); f.drain()
        assertNull(f.lobby.state.selected); assertTrue(f.lobby.state.games.isEmpty()); assertNull(f.lobby.state.preparation)
        assertEquals(listOf(other), f.lobby.state.contacts)
        f.createFails = false; val opens = f.opens; f.lobby.create(); f.drain()
        assertEquals(listOf(f.creates[0], f.creates[0]), f.creates); assertEquals(opens, f.opens)
        assertNull(f.lobby.state.selected); assertTrue(f.lobby.state.games.isEmpty())
    }
    @Test fun leavingAndReopeningDoesNotCancelOrWithdrawAcceptedReady() = Fixture().use { f ->
        val prepared = PreparationRules.empty(game).let { it.copy(players = it.players.map { player -> player.copy(ready = true) }) }
        f.head(game.copy(revision = "1"), prepared)
        f.lobby.back(); f.drain(); assertNull(f.lobby.state.selected); assertTrue(f.commands().isEmpty())
        f.lobby.open(game.id); f.drain()
        assertEquals(prepared, f.lobby.state.acceptedPreparation); assertTrue(f.commands().isEmpty())
    }
    @Test fun remoteCancellationClosesPickerAndFencesItsDelayedReadAndPreparationInspection() {
        val decks = object : Decks {
            override suspend fun list(token: String, after: String?) = DeckList(emptyList())
            override suspend fun read(token: String, id: String): SavedDeck = error("unused")
            override suspend fun definition(token: String, id: String, digest: String): SubmittedDefinition = error("unused")
            override suspend fun submit(token: String, value: SubmitDefinitions): DefinitionsAccepted = error("unused")
            override suspend fun create(token: String, value: CreateDeck): SavedDeck = error("unused")
            override suspend fun update(token: String, id: String, value: UpdateDeck): SavedDeck = error("unused")
            override suspend fun delete(token: String, id: String, value: DeleteDeck): DeckDeleted = error("unused")
            override fun close() = Unit
        }
        Fixture(decks = decks).use { f ->
            f.lobby.chooseDeck(); assertNotNull(f.lobby.state.picker)
            f.message(f.cancelRevision()); assertNull(f.lobby.state.picker); assertNull(f.lobby.state.selected)
        }
        Fixture().use { f ->
            val base = prepared(); f.head(game.copy(revision = "1"), base)
            f.lobby.prepareDeck(); f.lobby.inspectPrepared(base.definitions.single().digest)
            assertTrue(f.lobby.state.preparing); assertNotNull(f.lobby.inspection.state)
            f.message(f.cancelRevision())
            assertFalse(f.lobby.state.preparing); assertNull(f.lobby.inspection.state); assertNull(f.lobby.state.acceptedPreparation)
        }
    }
    @Test fun startBeforeCancellationResultKeepsExistingBoardAndHttpRecoveryCanUnfenceNewerLiveState() = Fixture().use { f ->
        val base = prepared(); f.head(game, base)
        assertTrue(f.lobby.cancelSetup(game)); val cancel = f.commands().single()
        val play = StartRules.initialize(game, base.copy(players = base.players.map { it.copy(ready = true) }), id(),
            mapOf(owner.accountId to "A".repeat(43), other.accountId to "_".repeat(42) + "8"), null)
        val active = game.copy(lifecycle = GameLifecycle.ACTIVE, playId = play.id, revision = "1")
        f.head(active, play = play)
        f.message(GameResult(cancel.requestId, cancel.scope, "rejected", "lifecycleEnded", "2"))
        assertEquals(active, f.lobby.state.detail); assertEquals(play, f.lobby.state.play)
        assertFalse(f.lobby.cancelSetup(game))
        f.visible = false; f.lobby.refresh(); f.drain(); assertNull(f.lobby.state.selected)
        f.visible = true; f.httpGame = active.copy(revision = "2"); f.lobby.refresh(); f.drain()
        assertEquals(listOf(f.httpGame), f.lobby.state.games)
    }
}
