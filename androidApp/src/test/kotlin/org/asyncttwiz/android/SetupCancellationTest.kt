package org.asyncttwiz.android

import kotlinx.serialization.encodeToString
import org.asyncttwiz.protocol.*
import org.junit.Test
import java.util.UUID
import java.util.concurrent.AbstractExecutorService
import java.util.concurrent.TimeUnit
import kotlin.test.*

internal class CancellationFixture(recipient: Boolean = false, pendingInvite: Boolean = false) : AutoCloseable {
    private fun id() = UUID.randomUUID().toString()
    val owner = AccountIdentity(id(), "Inviter")
    val other = AccountIdentity(id(), "Recipient")
    val game = GameSummary(id(), "Captured Spark", "2026-10-23T00:00:00Z", owner, other, id(), id(),
        if (pendingInvite) InvitationState.PENDING else InvitationState.ACCEPTED)
    var httpGame = game
    class Wire : GameConnection {
        lateinit var listener: GameConnection.Listener
        val frames = mutableListOf<GameClientMessage>()
        override fun connect(token: String, listener: GameConnection.Listener) { this.listener = listener }
        override fun send(message: GameClientMessage): Boolean { frames.add(message); return true }
        override fun disconnect() = Unit
        override fun close() = Unit
    }
    private class Worker : AbstractExecutorService() {
        override fun execute(command: Runnable) = command.run()
        override fun shutdown() = Unit
        override fun shutdownNow() = mutableListOf<Runnable>()
        override fun isShutdown() = false
        override fun isTerminated() = false
        override fun awaitTermination(timeout: Long, unit: TimeUnit) = false
    }
    val wire = Wire()
    val lobby = LobbyController(object : Lobbies {
        override suspend fun search(token: String, query: String) = UserSearch(emptyList())
        override suspend fun contacts(token: String, after: String?) = ContactList(emptyList())
        override suspend fun add(token: String, input: AddContact) = other
        override suspend fun create(token: String, input: CreateInvitation) = game
        override suspend fun games(token: String, invitations: Boolean, after: String?) = GameList(if (invitations) emptyList() else listOf(httpGame))
        override suspend fun head(token: String, id: String) = httpGame
        override fun close() = Unit
    }, { "token" }, Worker(), { it() }, gameConnection = wire, accountId = { if (recipient) other.accountId else owner.accountId })
    init {
        lobby.open(game.id)
        wire.listener.opened(); message(GameWelcome(gameplayRulesVersion = CURRENT_GAMEPLAY_RULES_VERSION))
        val stream = wire.frames.filterIsInstance<OpenGameStream>().single()
        message(GameStreamReady(game.id, stream.streamId, "1"))
        head(game)
    }
    fun message(value: GameServerMessage) = wire.listener.message(value)
    fun head(value: GameSummary, preparation: LobbyPreparation? = null, play: PlayState? = null) =
        message(GameHead(value.id, value.revision, value, preparation = preparation, play = play))
    fun commands() = wire.frames.filterIsInstance<GameCommand>()
    fun play(): PlayState {
        val definition = CardDefinition("Retained", listOf(DefinitionFace(id(), "Retained")))
        val submitted = SubmittedDefinition(DeckJson.digest(definition), definition)
        val source = SavedDeck(DeckSummary(id(), "Source", "1", game.createdAt, 1, 0),
            listOf(SavedDeckEntry(SavedDeckSection.MAINBOARD, submitted.digest, 1)))
        var preparation = PreparationRules.empty(game)
        for (actor in listOf(owner, other)) preparation = PreparationRules.select(game, preparation, actor.accountId, id(),
            GameScope(game.id, game.preparationRound), GameAction.SelectDeck(source.deck.id, "1"), source, listOf(submitted)).state!!
        return StartRules.initialize(game, preparation.copy(starter = StarterChoice(owner.accountId),
            players = preparation.players.map { it.copy(ready = true) }), id(),
            mapOf(owner.accountId to "A".repeat(43), other.accountId to "_".repeat(42) + "8"), null)
    }
    override fun close() = lobby.close()
}

class SetupCancellationTest {
    @Test fun capturedScopeCodecAndSingleConfirmForBothAdmittedPlayersAndPendingInviter() {
        for ((recipient, pending) in listOf(false to false, true to false, false to true)) CancellationFixture(recipient, pending).use { f ->
            f.lobby.requestCancelSetup(f.game)
            f.lobby.confirmCancelSetup(); f.lobby.confirmCancelSetup()
            val command = f.commands().single()
            assertEquals(GameAction.CancelSetup, command.action)
            assertEquals(GameScope(f.game.id, f.game.preparationRound), command.scope)
            assertTrue(GameWire.valid(command))
            assertEquals(command, GameWire.decodeClient(GameWire.json.encodeToString<GameClientMessage>(command)))
            assertContains(GameWire.json.encodeToString<GameClientMessage>(command), "cancelSetup")
            assertFalse(GameWire.valid(command.copy(scope = command.scope.copy(invitationId = f.game.invitationId))))
            assertFalse(GameWire.valid(command.copy(scope = command.scope.copy(playId = UUID.randomUUID().toString()))))
            assertNull(f.lobby.setupCancellation.confirmation)
            assertEquals(f.game, f.lobby.state.detail) // No optimistic cancellation/list removal.
            assertTrue(f.game.id in f.lobby.state.pendingGames)
        }
        CancellationFixture(recipient = true, pendingInvite = true).use { f ->
            f.lobby.requestCancelSetup(f.game); assertNull(f.lobby.setupCancellation.confirmation)
            assertFalse(f.lobby.cancelSetup(f.game)); assertTrue(f.commands().isEmpty())
        }
    }

    @Test fun disconnectedOrPendingDoesNotDispatchAndDismissalDoesNotRecallAnOriginal() = CancellationFixture().use { f ->
        f.lobby.requestCancelSetup(f.game); f.wire.listener.disconnected(false)
        f.lobby.confirmCancelSetup()
        assertNotNull(f.lobby.setupCancellation.error); assertEquals(f.game, f.lobby.setupCancellation.confirmation)
        assertTrue(f.commands().isEmpty()); f.lobby.setupCancellation.dismiss()
        assertEquals(f.game, f.lobby.state.detail)
    }

    @Test fun sameRoundRevisionDoesNotRetargetButNewRoundAndStartedPlayFenceOldDialog() = CancellationFixture().use { f ->
        f.lobby.requestCancelSetup(f.game)
        f.head(f.game.copy(revision = "1"))
        assertEquals(f.game, f.lobby.setupCancellation.confirmation)
        f.lobby.confirmCancelSetup(); val original = f.commands().single()
        assertFalse(f.lobby.cancelSetup(f.game)) // Existing pending work, no duplicate.
        f.message(GameResult(original.requestId, original.scope, "rejected", "gameChanged", "1"))
        f.lobby.requestCancelSetup(f.game)
        val round = f.game.copy(preparationRound = UUID.randomUUID().toString(), revision = "2")
        f.head(round)
        assertNull(f.lobby.setupCancellation.confirmation); assertFalse(f.lobby.cancelSetup(f.game))
        assertEquals(1, f.commands().size)
    }

    @Test fun startWinsAndLateCancellationOutcomeCannotReplaceExistingPlay() = CancellationFixture().use { f ->
        f.lobby.requestCancelSetup(f.game); f.lobby.confirmCancelSetup()
        val original = f.commands().single(); val play = f.play()
        val active = f.game.copy(lifecycle = GameLifecycle.ACTIVE, playId = play.id, revision = "1")
        f.head(active, play = play)
        assertNull(f.lobby.setupCancellation.confirmation)
        assertFalse(f.lobby.cancelSetup(f.game))
        f.message(GameResult(original.requestId, original.scope, "rejected", "gameStarted", "1"))
        f.lobby.sync() // Deliberately stale HTTP lobby summary.
        assertEquals(active, f.lobby.state.detail); assertEquals(play, f.lobby.state.play)
        assertEquals(1, f.commands().size)
    }

    @Test fun staleOpenDialogClosesWhenStartOrRemoteCancellationIsLearnedWithoutSending() {
        for (lifecycle in listOf(GameLifecycle.ACTIVE, GameLifecycle.CANCELLED)) CancellationFixture().use { f ->
            f.lobby.requestCancelSetup(f.game)
            val play = if (lifecycle == GameLifecycle.ACTIVE) f.play() else null
            f.head(f.game.copy(lifecycle = lifecycle, playId = play?.id, revision = "1"), play = play)
            f.lobby.confirmCancelSetup()
            f.lobby.requestCancelSetup(f.game); f.lobby.showGameContext(f.game) // Late old-row handler.
            assertNull(f.lobby.setupCancellation.confirmation); assertNull(f.lobby.setupCancellation.context)
            assertTrue(f.commands().isEmpty())
            if (play != null) assertEquals(play, f.lobby.state.play)
        }
    }
}
