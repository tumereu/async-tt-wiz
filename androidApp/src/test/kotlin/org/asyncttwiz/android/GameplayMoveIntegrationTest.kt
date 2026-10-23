package org.asyncttwiz.android

import org.asyncttwiz.protocol.*
import org.junit.Test
import kotlin.test.*

/** Production session/replica and shared processor, with delivery ordering controlled at the wire. */
class GameplayMoveIntegrationTest {
    private class Wire : GameConnection {
        lateinit var listener: GameConnection.Listener
        val frames = mutableListOf<GameClientMessage>()
        val reads = mutableListOf<(GameplayBaseline?) -> Unit>()
        override fun connect(token: String, listener: GameConnection.Listener) { this.listener = listener }
        override fun send(message: GameClientMessage): Boolean { frames.add(message); return true }
        override fun baseline(gameId: String, requestIds: List<String>, complete: (GameplayBaseline?) -> Unit): Boolean {
            reads.add(complete); return true
        }
        override fun disconnect() = Unit
        override fun close() = Unit
    }
    private class Fixture {
        val data = GameplayFixture()
        // Board entity IDs are global: the Library must not alias any card in 9..11.
        val library = data.id(30); val hand = data.id(20); val field = data.id(21)
        val root = data.initial.copy(board = data.initial.board.copy(
            cards = (9..11).map { data.initial.board.cards.single().copy(id = data.id(it), containerId = library) },
            containers = listOf(PlayContainer(library, PlayZone.LIBRARY, data.actor, (9..11).map(data::id)),
                PlayContainer(hand, PlayZone.HAND, data.actor), PlayContainer(field, PlayZone.BATTLEFIELD, data.actor),
                PlayContainer(data.id(22), PlayZone.STACK))))
        val wire = Wire()
        val endpoints = mutableListOf<BoardState?>()
        val outcomes = mutableListOf<GameResult>()
        val session: GameSession = GameSession(wire, { "A".repeat(43) }, { it() }, {}, {}, { _, result -> outcomes.add(result) },
            { endpoints.add(display()) }, accountId = { data.actor })
        private fun display() = session.play(data.game)?.board
        fun message(message: GameServerMessage) = wire.listener.message(message)
        fun connect() {
            session.watch(listOf(data.game)); wire.listener.opened(); message(GameWelcome())
            val stream = wire.frames.filterIsInstance<OpenGameStream>().single().streamId
            message(GameStreamReady(data.game.id, stream, "1")); message(data.head(root))
            assertTrue(session.ready(data.game.id))
        }
        fun submit(action: PlayAction) = assertTrue(session.gameplay(data.game, data.actor, action))
        fun commands() = wire.frames.filterIsInstance<GameCommand>()
        fun card(state: PlayState?, id: Int = 9) = state!!.board.cards.single { it.id == data.id(id) }
        fun update(from: PlayState, command: GameCommand, base: String, result: String): GameRevision {
            val change = GameplayRules.change(from, data.actor, command)
            assertEquals("accepted", change.outcome)
            return GameRevision(data.game.id, base, result, command.requestId, data.actor, command.scope,
                data.game.copy(revision = result), play = change.state, mutation = change.mutation, group = change.state!!.currentGroup)
        }
        fun result(update: GameRevision) = GameResult(update.requestId, update.scope, "accepted", ingressOrdinal = "1",
            commitRevision = update.resultRevision, mutation = update.mutation)
        fun snapshot(vararg updates: GameRevision): GameplaySnapshot {
            val start = GameRevision(data.game.id, "0", "1", root.startRequestId, data.actor,
                GameScope(data.game.id, data.game.preparationRound), data.game, play = root)
            val last = updates.last()
            return GameplaySnapshot(data.head(last.play!!, last.resultRevision), listOf(start) + updates)
        }
    }

    @Test fun guardedMovementAndManualTuplesShareInflightSessionAndBaselineWithoutAcknowledgementRewind() {
        val f = Fixture(); f.connect()
        f.submit(PlayAction.MoveCard(f.data.id(9), MoveSource(f.library, edge = MoveSourceEdge.TOP),
            MoveDestination(f.field, MoveInsertion.EMPTY, BoardPosition(2, -1))))
        f.submit(PlayAction.SetNote(f.data.id(9), " retained manual note "))
        f.submit(PlayAction.SetCounter(CounterTarget.PLAYER, f.data.actor, CounterColor.BLUE, "2"))
        val commands = f.commands(); assertEquals(3, commands.size)
        val predicted = f.session.play(f.data.game)!!.board
        val first = f.update(f.root, commands[0], "1", "2")
        val second = f.update(first.play!!, commands[1], "2", "3")
        val third = f.update(second.play!!, commands[2], "3", "4")
        assertEquals(predicted, third.play!!.board)
        f.endpoints.clear()
        f.message(f.result(first))
        assertEquals(f.root, f.session.acceptedPlay(f.data.game))
        f.message(first); f.message(second); f.message(f.result(second)); f.message(first)
        assertTrue(f.endpoints.all { it == predicted })
        assertEquals(BoardPosition(2, -1), f.card(f.session.play(f.data.game)).placement!!.position)
        assertEquals("retained manual note", f.card(f.session.play(f.data.game)).note)
        // Missing third delta forces the existing complete-record baseline path, with its final original.
        f.message(f.data.head(third.play!!, "4")); assertFalse(f.session.ready(f.data.game.id))
        f.wire.reads.single()(GameplayBaseline(f.snapshot(first, second, third),
            listOf(GameRequestStatus(commands[2].requestId, "final", f.result(third)))))
        assertTrue(f.session.ready(f.data.game.id)); assertFalse(f.session.pending(f.data.game.id))
        assertEquals(third.play, f.session.acceptedPlay(f.data.game))
        f.message(first); f.message(f.result(first)); f.message(f.data.head(f.root))
        assertEquals(third.play, f.session.acceptedPlay(f.data.game))
        assertTrue(f.endpoints.all { it == predicted }); assertEquals(3, f.commands().size)
    }

    @Test fun rejectedEarlierMoveDoesNotDropGuardedLaterOriginalOrReviveItsWithdrawnPrediction() {
        val f = Fixture(); f.connect()
        fun draw(id: Int) = PlayAction.MoveCard(f.data.id(id), MoveSource(f.library, edge = MoveSourceEdge.TOP),
            MoveDestination(f.hand, MoveInsertion.UNORDERED))
        f.submit(draw(9)); f.submit(draw(10)); f.submit(PlayAction.SetNote(f.data.id(10), "independent"))
        val commands = f.commands()
        // The remote reorder wins FIFO; first captured TOP fails but the second captured TOP is now valid.
        val remote = f.update(f.root, f.data.command(PlayAction.MoveCard(f.data.id(9), MoveSource(f.library),
            MoveDestination(f.library, MoveInsertion.BOTTOM))), "1", "2")
        f.message(GameResult(commands[0].requestId, commands[0].scope, "rejected", "movementConflict", "1"))
        assertEquals(f.library, f.card(f.session.play(f.data.game), 10).containerId)
        f.message(remote)
        assertEquals(f.library, f.card(f.session.play(f.data.game), 10).containerId)
        assertEquals("independent", f.card(f.session.play(f.data.game), 10).note)
        val later = f.update(remote.play!!, commands[1], "2", "3")
        f.message(f.result(later))
        assertEquals(remote.play, f.session.acceptedPlay(f.data.game))
        assertEquals(f.library, f.card(f.session.play(f.data.game), 10).containerId)
        f.message(later)
        assertEquals(f.hand, f.card(f.session.play(f.data.game), 10).containerId)
        assertEquals("independent", f.card(f.session.play(f.data.game), 10).note)
        val manual = f.update(later.play!!, commands[2], "3", "4")
        f.message(manual); f.message(f.result(manual)); f.message(remote)
        assertEquals(manual.play, f.session.acceptedPlay(f.data.game))
        assertFalse(f.session.pending(f.data.game.id)); assertEquals(commands, f.commands())
        assertEquals(listOf("rejected", "accepted", "accepted"), f.outcomes.map { it.outcome })
    }
}
