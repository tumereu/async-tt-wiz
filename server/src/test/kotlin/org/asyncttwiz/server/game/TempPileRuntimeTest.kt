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
    @Test fun `numbered Temp piles turn on and off and move between each other as shared undoable events`() {
        OwnedPostgres().start().use { db ->
            FoundationMigrations.migrate(db.settings)
            ServiceRuntime(db.settings).use { runtime ->
                runtime.start(); gameplayAwait(runtime::ready)
                val f = GameplayFixture(runtime)
                val owner = f.a.account.accountId
                fun state() = f.games.head(f.a, f.game.id).play!!
                fun accept(action: PlayAction) {
                    val input = f.owner.command(action)
                    val expected = GameplayRules.change(state(), owner, input)
                    val result = f.owner.execute(input)
                    assertEquals("accepted", result.outcome, result.reason)
                    assertEquals(expected.mutation, result.mutation)
                    assertEquals(expected.state, state())
                }
                val library = state().board.zoneContainer(owner, PlayZone.LIBRARY)!!
                accept(PlayAction.SetTempPile(owner, 2, true))
                assertTrue(TempPiles.container(state().board, owner, 2)!!.cards.isEmpty())
                // The first move into pile 3 creates it; pile 2 stays empty and on.
                val ids = library.cards.take(2)
                for (id in ids) accept(PlayAction.MoveCard(id, MoveSource(library.id),
                    MoveDestination(TempPiles.containerId(state().id, owner, 3), MoveInsertion.TOP)))
                assertEquals(ids.reversed(), TempPiles.container(state().board, owner, 3)!!.cards)
                assertEquals(listOf(1, 2, 3), TempPiles.visibleNumbers(f.games.head(f.b, f.game.id).play!!.board, owner))
                // Turning off is refused while the pile holds cards; pile 1 is never turned off.
                assertEquals("inapplicable", f.owner.execute(f.owner.command(PlayAction.SetTempPile(owner, 3, false))).reason)
                assertEquals("invalidInput", assertThrows(GameProtocolFailure::class.java) {
                    f.owner.execute(f.owner.command(PlayAction.SetTempPile(owner, 1, false))) }.code)
                val beforeAll = state()
                val all = TempPiles.allToPile(beforeAll.board, beforeAll.id, TempPiles.containerId(beforeAll.id, owner, 3), 1)
                assertEquals("accepted", f.owner.execute(f.owner.command(PlayAction.NextStep).copy(action = GameAction.PlayBatch(all))).outcome)
                assertEquals(ids.reversed(), TempPiles.container(state().board, owner, 1)!!.cards)
                // Once empty, the piles turn off.
                accept(PlayAction.SetTempPile(owner, 3, false))
                accept(PlayAction.SetTempPile(owner, 2, false))
                assertEquals(listOf(1), TempPiles.visibleNumbers(state().board, owner))
                var replay = f.root
                f.games.history(f.b, f.game.id, f.root.id, "0").mutations.forEach { replay = GameplayHistory.replay(replay, it).state!! }
                assertEquals(state(), replay)
                // Undo turns pile 2 back on.
                assertEquals("accepted", f.owner.execute(f.owner.command(PlayAction.NextStep).copy(action = GameAction.Undo)).outcome)
                assertNotNull(TempPiles.container(state().board, owner, 2))
            }
        }
    }
    @Test fun `rules v31 Temp pile search closes unshuffled and pile menu batches are single undoable invocations`() {
        OwnedPostgres().start().use { db ->
            FoundationMigrations.migrate(db.settings)
            ServiceRuntime(db.settings).use { runtime ->
                runtime.start(); gameplayAwait(runtime::ready)
                val f = GameplayFixture(runtime)
                val owner = f.a.account.accountId
                fun state() = f.games.head(f.a, f.game.id).play!!
                fun batch(actions: List<PlayAction>) {
                    val result = f.owner.execute(f.owner.command(PlayAction.NextStep).copy(action = GameAction.PlayBatch(actions)))
                    assertEquals("accepted", result.outcome, result.reason)
                }
                fun accept(action: PlayAction) {
                    val input = f.owner.command(action)
                    val expected = GameplayRules.change(state(), owner, input)
                    val result = f.owner.execute(input)
                    assertEquals("accepted", result.outcome, result.reason)
                    assertEquals(expected.mutation, result.mutation)
                    assertEquals(CURRENT_GAMEPLAY_RULES_VERSION, result.mutation!!.transition.rulesVersion)
                    assertEquals(expected.state, state())
                }
                val library = state().board.zoneContainer(owner, PlayZone.LIBRARY)!!
                val tempId = TempPiles.containerId(state().id, owner)
                batch(LibraryActions.moveCaptured(TempPiles.ensure(state().board, state().id, owner), library.cards.take(3),
                    MoveDestination(tempId, MoveInsertion.TOP)))
                val order = TempPiles.container(state().board, owner)!!.cards
                // The other player searches the pile; closing it leaves the order as it was.
                val search = f.recipient.command(PlayAction.ShowContents(tempId, ContentsSelection.ALL))
                assertEquals("accepted", f.recipient.execute(search).outcome)
                val display = state().board.contentsDisplays.single()
                accept(PlayAction.DismissContents(display.id, ContentsRest.SHUFFLE, RandomV1.encodeSeed(ByteArray(32) { 3 })))
                assertEquals(order, TempPiles.container(state().board, owner)!!.cards)
                // Top -> Temp 2 creates pile 2; Shuffle into library empties pile 1 as one Undo.
                batch(TempPiles.topToPile(state().board, state().id, tempId, 2))
                assertEquals(listOf(order.first()), TempPiles.container(state().board, owner, 2)!!.cards)
                val before = state()
                batch(TempPiles.shuffleIntoLibrary(before.board, tempId, RandomV1.encodeSeed(ByteArray(32) { 9 })))
                assertTrue(TempPiles.container(state().board, owner)!!.cards.isEmpty())
                var replay = f.root
                f.games.history(f.b, f.game.id, f.root.id, "0").mutations.forEach { replay = GameplayHistory.replay(replay, it).state!! }
                assertEquals(state(), replay)
                assertEquals("accepted", f.owner.execute(f.owner.command(PlayAction.NextStep).copy(action = GameAction.Undo)).outcome)
                assertEquals(before.board, state().board)
            }
        }
    }
}
