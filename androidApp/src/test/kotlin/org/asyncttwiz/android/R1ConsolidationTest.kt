package org.asyncttwiz.android

import org.asyncttwiz.protocol.*
import org.junit.Test
import kotlin.test.*

class R1ConsolidationTest {
    private class Wire : GameConnection {
        lateinit var listener: GameConnection.Listener
        val sent = mutableListOf<GameClientMessage>()
        override fun connect(token: String, listener: GameConnection.Listener) { this.listener = listener }
        override fun send(message: GameClientMessage): Boolean { sent.add(message); return true }
        override fun disconnect() = Unit
        override fun close() = Unit
    }

    @Test fun basicCallbacksAndResultSuggestionUseOneReplicaAndPreserveAcceptedTuples() {
        val f = GameplayFixture(); val wire = Wire()
        GameSession(wire, { "synthetic" }, { it() }, {}, {}, { _, _ -> }, {}, accountId = { f.actor }).use { session ->
            session.watch(listOf(f.game)); wire.listener.opened(); wire.listener.message(GameWelcome(gameplayRulesVersion = CURRENT_GAMEPLAY_RULES_VERSION))
            val stream = wire.sent.filterIsInstance<OpenGameStream>().single().streamId
            wire.listener.message(GameStreamReady(f.game.id, stream, "1")); wire.listener.message(f.head())
            val basic = BasicTableController({ session.play(f.game) }, { session.ready(f.game.id) },
                { action, invocation -> session.gameplay(f.game, f.actor, action, invocation) })
            assertTrue(basic.handle(TableIntent.PlayerDelta(f.actor, null, 3)))
            assertTrue(session.suggestResult(f.game, f.actor, ResultTarget(f.game.id, f.initial.id), SuggestedResult.DRAW))
            val commands = wire.sent.filterIsInstance<GameCommand>()
            assertEquals(2, commands.size); assertEquals(1, commands.map { it.streamId }.distinct().size)
            assertTrue(commands.all(GameWire::valid))
            assertEquals(f.initial, session.acceptedPlay(f.game))
            assertEquals("23", session.play(f.game)!!.board.players.first().life)
            assertEquals(SuggestedResult.DRAW, session.play(f.game)!!.board.resultSuggestion!!.outcome)
            var accepted = f.initial
            commands.forEachIndexed { index, command ->
                val change = GameplayRules.change(accepted, f.actor, command)
                assertEquals("accepted", change.outcome)
                wire.listener.message(GameResult(command.requestId, command.scope, "accepted", ingressOrdinal = "${index + 1}",
                    commitRevision = "${index + 2}", mutation = change.mutation))
                assertEquals(accepted, session.acceptedPlay(f.game), "a result is not a board snapshot")
                wire.listener.message(GameRevision(f.game.id, "${index + 1}", "${index + 2}", command.requestId,
                    f.actor, command.scope, f.game.copy(revision = "${index + 2}"), play = change.state,
                    mutation = change.mutation, group = change.state!!.currentGroup))
                accepted = change.state!!
            }
            assertFalse(session.pending(f.game.id)); assertEquals(accepted, session.acceptedPlay(f.game))
            assertEquals(HistoryCategory.S.key, accepted.currentGroup!!.category)
            assertEquals(f.initial.board.cards, accepted.board.cards)
            wire.listener.disconnected(false)
            assertFalse(basic.handle(TableIntent.PlayerDelta(f.actor, null, 1)))
            assertEquals(2, wire.sent.filterIsInstance<GameCommand>().size)
        }
    }
}
