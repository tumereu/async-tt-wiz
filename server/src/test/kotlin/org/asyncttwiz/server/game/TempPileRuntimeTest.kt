package org.asyncttwiz.server.game

import org.asyncttwiz.protocol.*
import org.asyncttwiz.server.ServiceRuntime
import org.asyncttwiz.server.database.FoundationMigrations
import org.asyncttwiz.server.database.OwnedPostgres
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class TempPileRuntimeTest {
    @Test fun `first move into a Temp pile creates it durably and the pile resolves back into the library`() {
        OwnedPostgres().start().use { db ->
            FoundationMigrations.migrate(db.settings)
            ServiceRuntime(db.settings).use { runtime ->
                runtime.start(); gameplayAwait(runtime::ready)
                val f = GameplayFixture(runtime)
                val owner = f.a.account.accountId
                fun state() = f.games.head(f.a, f.game.id).play!!
                val library = state().board.zoneContainer(owner, PlayZone.LIBRARY)!!
                val tempId = TempPiles.containerId(state().id, owner)
                val ids = library.cards.take(2)
                for (id in ids) {
                    val input = f.owner.command(PlayAction.MoveCard(id, MoveSource(library.id),
                        MoveDestination(tempId, MoveInsertion.TOP)))
                    val expected = GameplayRules.change(state(), owner, input)
                    val result = f.owner.execute(input)
                    assertEquals("accepted", result.outcome, result.reason)
                    assertEquals(expected.mutation, result.mutation)
                    assertEquals(expected.state, state())
                }
                val temp = TempPiles.container(state().board, owner)!!
                assertEquals(ids.reversed(), temp.cards)
                assertEquals(temp, TempPiles.container(f.games.head(f.b, f.game.id).play!!.board, owner))
                assertEquals(library.id, state().board.zoneContainer(owner, PlayZone.LIBRARY)!!.id)
                assertEquals("accepted", f.owner.execute(f.owner.command(PlayAction.ShuffleLibrary(tempId,
                    RandomV1.encodeSeed(ByteArray(32) { 7 })))).outcome)
                val beforeResolve = state()
                val batch = TempPiles.allToLibrary(beforeResolve.board, tempId, bottom = true)
                assertEquals("accepted", f.owner.execute(f.owner.command(PlayAction.NextStep)
                    .copy(action = GameAction.PlayBatch(batch))).outcome)
                assertTrue(TempPiles.container(state().board, owner)!!.cards.isEmpty())
                assertEquals(TempPiles.container(beforeResolve.board, owner)!!.cards,
                    state().board.containers.single { it.id == library.id }.cards.takeLast(2))
                var replay = f.root
                f.games.history(f.b, f.game.id, f.root.id, "0").mutations.forEach { replay = GameplayHistory.replay(replay, it).state!! }
                assertEquals(state(), replay)
                // The batch is one Undo.
                assertEquals("accepted", f.owner.execute(f.owner.command(PlayAction.NextStep).copy(action = GameAction.Undo)).outcome)
                assertEquals(beforeResolve.board, state().board)
            }
        }
    }
}
