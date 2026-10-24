package org.asyncttwiz.server.game

import org.asyncttwiz.protocol.*
import org.asyncttwiz.server.ServiceRuntime
import org.asyncttwiz.server.database.FoundationMigrations
import org.asyncttwiz.server.database.OwnedPostgres
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class StackQuickActionsRuntimeTest {
    @Test fun `hand projections reveal and hide in opponent views and all source destinations replay with Undo Redo`() {
        OwnedPostgres().start().use { db ->
            FoundationMigrations.migrate(db.settings)
            ServiceRuntime(db.settings).use { runtime ->
                runtime.start(); gameplayAwait(runtime::ready)
                val f = GameplayFixture(runtime)
                val owner = f.a.account.accountId
                val opponent = f.b.account.accountId
                fun state() = f.games.head(f.a, f.game.id).play!!
                val cardId = state().board.cards.first { it.owner == owner }.id
                fun apply(action: PlayAction): GameResult {
                    val before = state()
                    val input = f.owner.command(action)
                    val predicted = GameplayRules.change(before, owner, input)
                    val result = f.owner.execute(input)
                    assertNotEquals("rejected", result.outcome, "$action: ${result.reason}")
                    assertEquals(predicted.outcome, result.outcome)
                    assertEquals(predicted.state, state())
                    assertEquals(predicted.mutation, result.mutation)
                    return result
                }
                fun control(action: GameAction) {
                    assertEquals("accepted", f.owner.execute(f.owner.command(PlayAction.NextStep).copy(action = action)).outcome)
                }
                fun undoRedo() {
                    val before = state().board
                    control(GameAction.Undo); control(GameAction.Redo)
                    assertEquals(before, state().board)
                }
                fun move(zone: PlayZone) {
                    val play = state()
                    val card = play.board.cards.single { it.id == cardId }
                    val target = play.board.containers.single { it.zone == zone && it.accountId == owner }
                    val destination = when (zone) {
                        PlayZone.BATTLEFIELD -> MoveDestination(target.id, MoveInsertion.EMPTY, BoardPosition(0, 1))
                        PlayZone.LIBRARY -> MoveDestination(target.id, MoveInsertion.TOP)
                        else -> MoveDestination(target.id, MoveInsertion.UNORDERED)
                    }
                    GameplayRules.moveActions(play, PlayAction.MoveCard(cardId, MoveSource(card.containerId, card.placement), destination)).forEach(::apply)
                }
                for (source in listOf(PlayZone.HAND, PlayZone.GRAVEYARD, PlayZone.EXILE, PlayZone.BATTLEFIELD)) {
                    move(source)
                    val before = state().board.cards.single { it.id == cardId }
                    apply(PlayAction.Stack(StackAction.AddEffect(cardId, GameplayRules.stackDigest(state()))))
                    assertEquals(before, state().board.cards.single { it.id == cardId })
                    undoRedo()
                    var entry = state().board.stackEffects.single().id
                    val view = f.games.head(f.b, f.game.id).play!!
                    assertTrue(CardVisibility.isVisibleFaceUp(view.board, view.board.cards.single { it.id == cardId }, opponent))
                    assertFalse(ManualDisclosure.permittedFaces(view, CombatStackRules.inspection(view.board, entry)!!, opponent, true).isEmpty())
                    for (target in listOf(PlayZone.BATTLEFIELD, PlayZone.GRAVEYARD)) {
                        StackQuickActions.actions(state(), entry, target).forEach(::apply)
                        assertTrue(state().board.stackEffects.isEmpty())
                        assertEquals(target, state().board.containers.single { it.id == state().board.cards.single { c -> c.id == cardId }.containerId }.zone)
                        // Recreate the source projection for the next bubble direction.
                        undoRedo()
                        if (target == PlayZone.BATTLEFIELD) {
                            move(source)
                            apply(PlayAction.Stack(StackAction.AddEffect(cardId, GameplayRules.stackDigest(state()))))
                            entry = state().board.stackEffects.single().id
                        }
                    }
                }
                move(PlayZone.HAND)
                apply(PlayAction.Stack(StackAction.AddEffect(cardId, GameplayRules.stackDigest(state()))))
                val entry = state().board.stackEffects.single().id
                apply(PlayAction.Stack(StackAction.RemoveEffect(entry))); undoRedo()
                val hidden = f.games.head(f.b, f.game.id).play!!
                val card = hidden.board.cards.single { it.id == cardId }
                assertFalse(CardVisibility.isVisibleFaceUp(hidden.board, card, opponent))
                assertTrue(ManualDisclosure.permittedFaces(hidden, card, opponent, true).isEmpty())
                // Old real-card Stack events remain accepted and replayable.
                move(PlayZone.BATTLEFIELD)
                val old = state()
                val real = old.board.cards.single { it.id == cardId }
                apply(PlayAction.MoveCard(real.id, MoveSource(real.containerId, real.placement),
                    MoveDestination(CombatStackRules.stack(old.board).id, MoveInsertion.TOP), GameplayRules.stackDigest(old)))
                move(PlayZone.GRAVEYARD); undoRedo()
                var replay = f.root
                f.games.history(f.b, f.game.id, f.root.id, "0").mutations.forEach {
                    replay = GameplayHistory.replay(replay, it).state!!
                }
                assertEquals(state(), replay)
            }
        }
    }

    @Test fun `marker and literal graveyard bubble travel through real FIFO requests history replay and Undo`() {
        OwnedPostgres().start().use { db ->
            FoundationMigrations.migrate(db.settings)
            ServiceRuntime(db.settings).use { runtime ->
                runtime.start(); gameplayAwait(runtime::ready)
                val f = GameplayFixture(runtime)
                fun state() = f.games.head(f.a, f.game.id).play!!
                fun apply(action: PlayAction): GameResult {
                    val before = state()
                    val input = f.owner.command(action)
                    val expected = GameplayRules.change(before, f.a.account.accountId, input)
                    val result = f.owner.execute(input)
                    assertEquals("accepted", result.outcome)
                    assertEquals(expected.mutation, result.mutation)
                    assertEquals(expected.state, state())
                    assertEquals(result, f.owner.admit(input))
                    return result
                }
                val definition = TokenCatalog.blank.definition.let { it.copy(faces = it.faces.map { face ->
                    face.copy(rulesText = "Flying\nLiteral second paragraph")
                }) }
                apply(PlayAction.CreateToken(SubmittedDefinition(DeckJson.digest(definition), definition)))
                val token = state().board.cards.single { it.token }
                apply(PlayAction.Stack(StackAction.AddEffect(token.id, GameplayRules.stackDigest(state()))))
                val entry = state().board.stackEffects.single().id
                apply(PlayAction.Stack(StackAction.MarkText(entry, 1)))
                assertEquals(mapOf(entry to 1), f.games.head(f.b, f.game.id).play!!.board.stackTextMarks)
                val marked = state()
                val invocation = gameplayId()
                val actions = StackQuickActions.actions(marked, entry, PlayZone.GRAVEYARD)
                assertEquals(1, actions.size)
                val inputs = actions.map { f.owner.command(it).copy(invocationId = invocation) }
                inputs.forEach { assertTrue(f.owner.admit(it) is GameReceipt) }
                inputs.forEach { assertTrue(f.games.executeOne()); assertEquals("accepted", f.owner.result(it).outcome) }
                assertTrue(state().board.stackEffects.isEmpty()); assertTrue(state().board.stackTextMarks.isEmpty())
                assertFalse(state().board.cards.any { it.id == token.id })
                val history = f.games.history(f.b, f.game.id, f.root.id, "0")
                var replay = f.root
                history.mutations.forEach { replay = GameplayHistory.replay(replay, it).state!! }
                assertEquals(state(), replay)
                val undo = f.owner.command(PlayAction.NextStep).copy(action = GameAction.Undo)
                assertEquals("accepted", f.owner.execute(undo).outcome)
                assertEquals(marked.board, state().board)
            }
        }
    }
}
