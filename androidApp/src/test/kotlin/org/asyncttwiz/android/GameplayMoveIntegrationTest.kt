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
    private class Fixture(handStarts: Boolean = false) {
        val data = GameplayFixture()
        // Board entity IDs are global: the Library must not alias any card in 9..11.
        val library = data.id(30); val hand = data.id(20); val field = data.id(21)
        val root = data.initial.copy(board = data.initial.board.copy(
            cards = (9..11).map { data.initial.board.cards.single().copy(id = data.id(it), containerId = if (handStarts) hand else library) },
            containers = listOf(PlayContainer(library, PlayZone.LIBRARY, data.actor, if (handStarts) emptyList() else (9..11).map(data::id)),
                PlayContainer(hand, PlayZone.HAND, data.actor, if (handStarts) (9..11).map(data::id) else emptyList()), PlayContainer(field, PlayZone.BATTLEFIELD, data.actor),
                PlayContainer(data.id(22), PlayZone.STACK))))
        val wire = Wire()
        val endpoints = mutableListOf<BoardState?>()
        val outcomes = mutableListOf<GameResult>()
        var now = 0L
        val session: GameSession = GameSession(wire, { "A".repeat(43) }, { it() }, {}, {}, { _, result -> outcomes.add(result) },
            { endpoints.add(display()) }, accountId = { data.actor }, now = { now })
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
        f.message(f.data.head(third.play!!, "4")); f.now += 2_000; f.session.tick()
        assertFalse(f.session.ready(f.data.game.id))
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

    @Test fun rapidRepeatedDragsTapStackAndTokenMovesReplayColdWithoutMissingHistory() {
        val f = Fixture(); f.connect()
        val actions = mutableListOf<PlayAction>()
        fun send(action: PlayAction) { actions.add(action); f.submit(action) }
        repeat(6) { round ->
            for (n in 9..11) {
                var card = f.card(f.session.play(f.data.game), n)
                send(PlayAction.MoveCard(card.id, MoveSource(card.containerId, card.placement),
                    MoveDestination(f.field, MoveInsertion.EMPTY, BoardPosition(n, round))))
                send(PlayAction.SetTapped(card.id, true))
                card = f.card(f.session.play(f.data.game), n)
                send(PlayAction.MoveCard(card.id, MoveSource(card.containerId, card.placement),
                    MoveDestination(f.data.id(22), MoveInsertion.TOP), expectedStackDigest = GameplayRules.stackDigest(f.session.play(f.data.game)!!)))
                send(PlayAction.MoveCard(card.id, MoveSource(f.data.id(22)), MoveDestination(f.hand, MoveInsertion.UNORDERED)))
            }
        }
        send(PlayAction.CreateToken(TokenCatalog.blank))
        val token = f.session.play(f.data.game)!!.board.cards.single { it.token }
        send(PlayAction.SetTapped(token.id, true))
        send(PlayAction.MoveCard(token.id, MoveSource(token.containerId, token.placement),
            MoveDestination(f.library, MoveInsertion.TOP)))
        assertTrue(f.session.play(f.data.game)!!.board.cards.none { it.id == token.id })
        var tree = GameplayTree(f.root)
        for ((index, command) in f.commands().withIndex()) {
            val next = HistoryContinuation.change(tree.copy(), f.data.actor, command)
            assertEquals("accepted", next.outcome, "${actions[index]}: ${next.reason}")
            tree = assertNotNull(next.tree)
            assertEquals(tree.stateAt(tree.currentNodeId), tree.copy().stateAt(tree.currentNodeId), "cold replay at $index")
        }
        assertEquals(f.session.play(f.data.game)!!.board, tree.stateAt(tree.currentNodeId)!!.board)
        f.session.close()
    }

    @Test fun ownHandPlayUndoPredictsBeforeMoveAcknowledgementAndNeverAllowsAPreUndoDrag() {
        val f = Fixture(handStarts = true); f.connect()
        val draw = PlayAction.MoveCard(f.data.id(9), MoveSource(f.hand), MoveDestination(f.field, MoveInsertion.EMPTY, BoardPosition(1, 1)))
        f.submit(draw)
        val move = f.commands().single()
        val moved = assertNotNull(HistoryContinuation.change(GameplayTree(f.root), f.data.actor, move).tree)
        assertTrue(f.session.historyControl(f.data.game, f.data.actor, GameAction.Undo))
        val undo = f.commands().last()
        assertTrue(moved.nodes.getValue(moved.currentNodeId).revealsInformation,
            "Playing an already-known Hand card reveals it to the opponent, but is still reversible")
        assertEquals(f.hand, f.card(f.session.play(f.data.game)).containerId,
            "Undo must publish its reverse-animation endpoint without either server acknowledgement")
        // This is the invalid original the old client could send while alone, without any opponent.
        val staleDrag = PlayAction.MoveCard(f.data.id(9), MoveSource(f.field, f.card(f.session.play(f.data.game)).placement),
            MoveDestination(f.hand, MoveInsertion.UNORDERED))
        val undone = assertNotNull(HistoryContinuation.change(moved, f.data.actor, undo).tree)
        assertEquals("movementConflict", HistoryContinuation.change(undone, f.data.actor, f.data.command(staleDrag)).reason)
        assertFalse(f.session.gameplay(f.data.game, f.data.actor, staleDrag))
        f.message(GameResult(undo.requestId, undo.scope, "accepted", ingressOrdinal = "2", commitRevision = "3"))
        assertFalse(f.session.gameplay(f.data.game, f.data.actor, staleDrag))
        val first = GameRevision(f.data.game.id, "1", "2", move.requestId, f.data.actor, move.scope,
            f.data.game.copy(revision = "2"), treeDelta = TreeSync.delta(GameplayTree(f.root), moved))
        f.message(first)
        f.message(GameRevision(f.data.game.id, "2", "3", undo.requestId, f.data.actor, undo.scope,
            f.data.game.copy(revision = "3"), treeDelta = TreeSync.delta(moved, undone)))
        assertEquals(f.hand, f.card(f.session.play(f.data.game)).containerId)
        f.submit(draw)
        f.session.close()
    }

    @Test fun acknowledgedOwnHandPlayUndoAndRedoPredictAndRejectionRestoresAcceptedBoard() {
        val f = Fixture(handStarts = true); f.connect()
        f.submit(PlayAction.MoveCard(f.data.id(9), MoveSource(f.hand),
            MoveDestination(f.field, MoveInsertion.EMPTY, BoardPosition(1, 1))))
        val move = f.commands().single()
        val initial = GameplayTree(f.root)
        val moved = assertNotNull(HistoryContinuation.change(initial, f.data.actor, move).tree)
        f.message(GameRevision(f.data.game.id, "1", "2", move.requestId, f.data.actor, move.scope,
            f.data.game.copy(revision = "2"), treeDelta = TreeSync.delta(initial, moved)))
        val accepted = f.session.acceptedPlay(f.data.game)
        assertTrue(f.session.historyControl(f.data.game, f.data.actor, GameAction.Undo))
        val undo = f.commands().last()
        assertEquals(f.hand, f.card(f.session.play(f.data.game)).containerId)
        assertEquals(accepted, f.session.acceptedPlay(f.data.game))
        f.message(GameResult(undo.requestId, undo.scope, "rejected", "referenceBlocked", ingressOrdinal = "2"))
        assertEquals(accepted, f.session.play(f.data.game))
        assertTrue(f.session.historyControl(f.data.game, f.data.actor, GameAction.Undo))
        assertEquals(f.hand, f.card(f.session.play(f.data.game)).containerId)
        assertTrue(f.session.historyControl(f.data.game, f.data.actor, GameAction.Redo))
        assertEquals(accepted!!.board, f.session.play(f.data.game)!!.board)
        f.session.close()
    }
}
