package org.asyncttwiz.android

import org.asyncttwiz.protocol.*
import org.junit.Test
import kotlin.test.*

/** Test the production GameSession, not a test-only queue or replica. */
class GameplayMergeVerificationTest {
    private class Wire : GameConnection {
        lateinit var listener: GameConnection.Listener
        val sent = mutableListOf<GameClientMessage>()
        override fun connect(token: String, listener: GameConnection.Listener) { this.listener = listener }
        override fun send(message: GameClientMessage): Boolean { sent.add(message); return true }
        override fun disconnect() = Unit
        override fun close() = Unit
    }
    private class Fixture {
        val f = GameplayFixture()
        val library = f.id(24); val stack = f.id(25); val hand = f.id(26)
        val root = f.initial.copy(board = f.initial.board.copy(
            cards = (9..11).map { f.initial.board.cards.single().copy(id = f.id(it), containerId = library, placement = null) },
            containers = listOf(PlayContainer(library, PlayZone.LIBRARY, f.actor, (9..11).map(f::id)),
                PlayContainer(stack, PlayZone.STACK), PlayContainer(hand, PlayZone.HAND, f.actor))))
        val wire = Wire()
        val endpoints = mutableListOf<BoardState?>()
        val settled = mutableListOf<String>()
        val session: GameSession = GameSession(wire, { "A".repeat(43) }, { it() }, {}, {},
            { original, _ -> settled.add(original.requestId) }, { endpoints.add(display()) }, accountId = { f.actor })
        private fun display() = session.play(f.game)?.board
        fun receive(message: GameServerMessage) = wire.listener.message(message)
        fun connect() {
            session.watch(listOf(f.game)); wire.listener.opened(); receive(GameWelcome(gameplayRulesVersion = CURRENT_GAMEPLAY_RULES_VERSION))
            receive(GameStreamReady(f.game.id, wire.sent.filterIsInstance<OpenGameStream>().single().streamId, "1"))
            receive(f.head(root)); assertTrue(session.ready(f.game.id))
        }
        fun submit(action: PlayAction) = assertTrue(session.gameplay(f.game, f.actor, action))
        val commands get() = wire.sent.filterIsInstance<GameCommand>()
        fun update(from: PlayState, command: GameCommand, revision: Int): GameRevision {
            val change = GameplayRules.change(from, f.actor, command)
            assertEquals("accepted", change.outcome)
            return GameRevision(f.game.id, (revision - 1).toString(), revision.toString(), command.requestId,
                f.actor, command.scope, f.game.copy(revision = revision.toString()), play = change.state,
                mutation = change.mutation, group = change.state!!.currentGroup)
        }
        fun result(update: GameRevision) = GameResult(update.requestId, update.scope, "accepted",
            ingressOrdinal = "1", commitRevision = update.resultRevision, mutation = update.mutation)
    }

    @Test fun dependentStackOriginalsAndManualEditSurviveLastResultFirstWithoutRewindOrDoubleApplication() {
        val t = Fixture(); t.connect()
        fun add(card: Int) {
            val display = t.session.play(t.f.game)!!
            t.submit(PlayAction.MoveCard(t.f.id(card), MoveSource(t.library),
                MoveDestination(t.stack, MoveInsertion.TOP), GameplayRules.stackDigest(display)))
        }
        add(9); add(10); t.submit(PlayAction.SetNote(t.f.id(9), " preserved "))
        val originals = t.commands
        assertEquals(3, originals.size)
        val endpoint = t.session.play(t.f.game)!!.board
        val first = t.update(t.root, originals[0], 2)
        val second = t.update(first.play!!, originals[1], 3)
        val third = t.update(second.play!!, originals[2], 4)
        assertEquals(endpoint, third.play!!.board)
        t.endpoints.clear()
        t.receive(t.result(third)); t.receive(t.result(second)); t.receive(t.result(first))
        assertEquals(t.root, t.session.acceptedPlay(t.f.game))
        assertTrue(t.session.pending(t.f.game.id)) // Results have not installed accepted state.
        t.receive(first); t.receive(second); t.receive(third)
        t.receive(first); t.receive(t.result(second)); t.receive(t.f.head(t.root))
        assertEquals(third.play, t.session.acceptedPlay(t.f.game))
        assertTrue(t.endpoints.isNotEmpty()); assertTrue(t.endpoints.all { it == endpoint })
        assertFalse(t.session.pending(t.f.game.id)); assertEquals(originals, t.commands)
        assertEquals(originals.map { it.requestId }.reversed(), t.settled)
        t.session.close()
    }

    @Test fun failedTopDoesNotCancelLaterGuardedOriginalAndLateResultDoesNotRewindAcceptedManualState() {
        val t = Fixture(); t.connect()
        fun draw(card: Int) = PlayAction.MoveCard(t.f.id(card), MoveSource(t.library, edge = MoveSourceEdge.TOP),
            MoveDestination(t.hand, MoveInsertion.UNORDERED))
        t.submit(draw(9)); t.submit(draw(10)); t.submit(PlayAction.AdjustCounter(CounterTarget.PLAYER, t.f.actor, CounterColor.RED, "1"))
        val originals = t.commands
        val remote = t.update(t.root, t.f.command(PlayAction.MoveCard(t.f.id(9), MoveSource(t.library),
            MoveDestination(t.library, MoveInsertion.BOTTOM))), 2)
        t.receive(GameResult(originals[0].requestId, originals[0].scope, "rejected", "movementConflict", "1"))
        // The second TOP is inapplicable on the still-old accepted board; its original remains queued.
        t.receive(remote)
        val later = t.update(remote.play!!, originals[1], 3)
        val manual = t.update(later.play!!, originals[2], 4)
        t.receive(t.result(later))
        assertEquals(remote.play, t.session.acceptedPlay(t.f.game))
        assertEquals(t.library, t.session.play(t.f.game)!!.board.cards.single { it.id == t.f.id(10) }.containerId)
        t.receive(later); t.receive(manual); t.receive(t.result(manual)); t.receive(remote)
        assertEquals(manual.play, t.session.acceptedPlay(t.f.game))
        assertEquals("1", t.session.play(t.f.game)!!.board.players.first().counters[CounterColor.RED])
        assertEquals(originals, t.commands); assertFalse(t.session.pending(t.f.game.id))
        assertEquals(originals.map { it.requestId }, t.settled)
        t.session.close()
    }
}
