package org.asyncttwiz.android

import java.util.UUID
import org.asyncttwiz.protocol.*
import org.junit.Test
import kotlin.test.*

class GameCancellationTest {
    private fun id() = UUID.randomUUID().toString()
    private val owner = AccountIdentity(id(), "Owner")
    private val other = AccountIdentity(id(), "Other")
    private val game = GameSummary(id(), "Spark", "2026-10-23T00:00:00Z", owner, other, id(), id(), InvitationState.ACCEPTED)
    private class Wire : GameConnection {
        lateinit var listener: GameConnection.Listener
        val frames = mutableListOf<GameClientMessage>()
        override fun connect(token: String, listener: GameConnection.Listener) { this.listener = listener }
        override fun send(message: GameClientMessage): Boolean { frames.add(message); return true }
        override fun disconnect() = Unit
        override fun close() = Unit
    }
    private inner class Fixture {
        val wire = Wire(); var token: String? = "original"
        val settled = mutableListOf<GameResult>(); val changed = mutableListOf<GameSummary>()
        val session = GameSession(wire, { token }, { it() }, {}, changed::add, { _, result -> settled.add(result) }, {})
        fun connect(statuses: List<GameRequestStatus> = emptyList(), ended: GameSummary? = null, generation: String = "1") {
            session.watch(listOf(game)); wire.listener.opened(); message(GameWelcome())
            val stream = wire.frames.mapNotNull { when (it) { is OpenGameStream -> it.streamId; is ResumeGameStream -> it.streamId; else -> null } }.last()
            message(GameStreamReady(game.id, stream, generation, statuses, ended))
            if (ended == null) message(GameHead(game.id, game.revision, game))
        }
        fun message(message: GameServerMessage) = wire.listener.message(message)
        fun commands() = wire.frames.filterIsInstance<GameCommand>()
        fun result(command: GameCommand) = GameResult(command.requestId, command.scope, "accepted", ingressOrdinal = "1", commitRevision = "1")
    }
    @Test fun cancelledRecoverySettlesOriginalsWithoutSubscriptionOrStaleStateResurrection() {
        for (status in listOf("pending", "final", "notAdmitted")) {
            val f = Fixture(); f.connect(); assertTrue(f.session.submit(game, GameAction.CancelSetup))
            val original = f.commands().single(); val stale = f.wire.listener
            f.wire.listener.disconnected(); f.token = "renewed"
            val ended = game.copy(lifecycle = GameLifecycle.CANCELLED, revision = "2")
            val subscriptions = f.wire.frames.filterIsInstance<SubscribeGame>().size
            f.connect(listOf(GameRequestStatus(original.requestId, status, if (status == "final") f.result(original) else null)), ended, "2")
            assertEquals(ended, f.session.summary(game)); assertFalse(f.session.ready(game.id))
            assertEquals(subscriptions, f.wire.frames.filterIsInstance<SubscribeGame>().size)
            assertEquals(listOf(original), f.commands())
            assertEquals(status == "pending", f.session.pending(game.id))
            if (status == "pending") f.message(f.result(original))
            f.message(GameHead(game.id, "0", game, preparation = PreparationRules.empty(game)))
            f.session.watch(listOf(game)); stale.message(GameHead(game.id, "99", game.copy(revision = "99")))
            assertEquals(ended, f.session.summary(game)); assertNull(f.session.preparation(game)); assertFalse(f.session.pending(game.id))
            assertFalse(f.session.submit(game, GameAction.CancelSetup)); f.session.close()
        }
    }
    @Test fun remoteCancellationClearsPreparationAndUnsentTransferTailButKeepsAdmittedOriginals() {
        val f = Fixture(); f.connect()
        val definition = CardDefinition("Card", listOf(DefinitionFace(id(), "Face")))
        val submitted = SubmittedDefinition(DeckJson.digest(definition), definition)
        val source = SavedDeck(DeckSummary(id(), "Source", "1", game.createdAt, 600, 0), listOf(
            SavedDeckEntry(SavedDeckSection.MAINBOARD, submitted.digest, 600)))
        val base = PreparationRules.select(game, PreparationRules.empty(game), owner.accountId, id(), GameScope(game.id, game.preparationRound),
            GameAction.SelectDeck(source.deck.id, source.deck.revision), source, listOf(submitted)).state!!
        f.message(GameHead(game.id, "0", game, preparation = base))
        val capture = PreparationRules.capture(base, owner.accountId, submitted.digest, SavedDeckSection.MAINBOARD, true)!!
        assertTrue(f.session.transfer(game, owner.accountId, capture)); assertEquals(256, f.commands().size)
        assertEquals(344, f.session.queuedTransfers(game.id))
        val ended = game.copy(lifecycle = GameLifecycle.CANCELLED, revision = "2")
        f.message(GameRevision(game.id, "1", "2", id(), other.accountId, GameScope(game.id, game.preparationRound), ended))
        assertNull(f.session.acceptedPreparation(game.id)); assertNull(f.session.preparation(game)); assertEquals(0, f.session.queuedTransfers(game.id))
        assertTrue(f.session.pending(game.id)); val count = f.commands().size
        f.message(f.result(f.commands().first()))
        assertEquals(count, f.commands().size); assertNull(f.session.preparation(game)); assertEquals(ended, f.session.summary(game))
        f.session.clear(); assertFalse(f.session.pending(game.id)); f.session.close()
    }
    @Test fun changedRoundDoesNotRetargetCapturedCancellationAndNegativeReadFencesOldReplica() {
        val f = Fixture(); f.connect()
        val fresh = game.copy(preparationRound = id(), revision = "2")
        f.message(GameHead(game.id, "2", fresh, preparation = PreparationRules.empty(fresh)))
        assertFalse(f.session.submit(game, GameAction.CancelSetup)); assertTrue(f.commands().isEmpty())
        f.session.unavailable(game.id)
        f.message(GameHead(game.id, "2", fresh, preparation = PreparationRules.empty(fresh)))
        assertNull(f.session.acceptedPreparation(game.id)); assertFalse(f.session.ready(game.id))
        f.session.close()
    }
}
