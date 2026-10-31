package org.asyncttwiz.server.game

import org.asyncttwiz.protocol.*
import org.asyncttwiz.server.ServiceRuntime
import org.asyncttwiz.server.database.FoundationMigrations
import org.asyncttwiz.server.database.FreshDatabase
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class HandReorderRuntimeTest {
    @Test fun `hand gap move is durable replayable and undoable without disclosure`() {
        FreshDatabase().start().use { db ->
            FoundationMigrations.migrate(db.settings)
            ServiceRuntime(db.settings).use { runtime ->
                runtime.start(); gameplayAwait(runtime::ready)
                val f = GameplayFixture(runtime)
                fun state() = f.games.head(f.a, f.game.id).play!!
                val hand = state().board.containers.single { it.zone == PlayZone.HAND && it.accountId == f.a.account.accountId }
                val library = state().board.containers.single { it.zone == PlayZone.LIBRARY && it.accountId == f.a.account.accountId }
                repeat(3) {
                    val id = state().board.containers.single { it.id == library.id }.cards.first()
                    assertEquals("accepted", f.owner.execute(f.owner.command(PlayAction.MoveCard(id, MoveSource(library.id, edge = MoveSourceEdge.TOP),
                        MoveDestination(hand.id, MoveInsertion.UNORDERED)))).outcome)
                }
                val before = state()
                val ids = before.board.containers.single { it.id == hand.id }.cards
                val input = f.owner.command(PlayAction.MoveCard(ids.last(), MoveSource(hand.id),
                    MoveDestination(hand.id, MoveInsertion.GAP, beforeId = ids.first())))
                val expected = GameplayRules.change(before, f.a.account.accountId, input)
                val result = f.owner.execute(input)
                assertEquals("accepted", result.outcome)
                assertEquals(expected.mutation, result.mutation)
                assertEquals(expected.state, state())
                assertEquals(result, f.owner.admit(input))
                assertTrue(result.mutation!!.transition.disclosures.isEmpty())
                assertFalse(result.mutation!!.revealsInformation)
                assertEquals(listOf(ids.last()) + ids.dropLast(1), f.games.head(f.b, f.game.id).play!!.board.containers.single { it.id == hand.id }.cards)
                var replay = f.root
                f.games.history(f.b, f.game.id, f.root.id, "0").mutations.forEach { replay = GameplayHistory.replay(replay, it).state!! }
                assertEquals(state(), replay)
                assertEquals("accepted", f.owner.execute(f.owner.command(PlayAction.NextStep).copy(action = GameAction.Undo)).outcome)
                assertEquals(before.board, state().board)
                assertEquals("accepted", f.owner.execute(f.owner.command(PlayAction.NextStep).copy(action = GameAction.Redo)).outcome)
                assertEquals(expected.state!!.board, state().board)
            }
        }
    }
}
