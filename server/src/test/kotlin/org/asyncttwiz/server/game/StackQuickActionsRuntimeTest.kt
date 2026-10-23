package org.asyncttwiz.server.game

import org.asyncttwiz.protocol.*
import org.asyncttwiz.server.ServiceRuntime
import org.asyncttwiz.server.database.FoundationMigrations
import org.asyncttwiz.server.database.OwnedPostgres
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class StackQuickActionsRuntimeTest {
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
                assertEquals(2, actions.size)
                val inputs = actions.map { f.owner.command(it).copy(invocationId = invocation) }
                inputs.forEach { assertTrue(f.owner.admit(it) is GameReceipt) }
                inputs.forEach { assertTrue(f.games.executeOne()); assertEquals("accepted", f.owner.result(it).outcome) }
                assertTrue(state().board.stackEffects.isEmpty()); assertTrue(state().board.stackTextMarks.isEmpty())
                assertFalse(state().board.cards.any { it.id == token.id })
                val history = f.games.history(f.b, f.game.id, f.root.id, "0")
                var replay = f.root
                history.mutations.forEach { replay = GameplayHistory.replay(replay, it).state!! }
                assertEquals(state(), replay)
                repeat(2) {
                    val undo = f.owner.command(PlayAction.NextStep).copy(action = GameAction.Undo)
                    assertEquals("accepted", f.owner.execute(undo).outcome)
                }
                assertEquals(marked.board, state().board)
            }
        }
    }
}
