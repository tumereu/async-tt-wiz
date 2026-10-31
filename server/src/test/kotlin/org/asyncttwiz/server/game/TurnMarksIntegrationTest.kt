package org.asyncttwiz.server.game

import org.asyncttwiz.protocol.*
import org.asyncttwiz.server.ServiceRuntime
import org.asyncttwiz.server.database.FoundationMigrations
import org.asyncttwiz.server.database.FreshDatabase
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class TurnMarksIntegrationTest {
    // TRN-003
    @Test fun `temporary marks persist replay and clear in one reversible server event`() {
        FreshDatabase().start().use { db ->
            FoundationMigrations.migrate(db.settings)
            ServiceRuntime(db.settings).use { runtime ->
                runtime.start(); gameplayAwait(runtime::ready)
                val f = GameplayFixture(runtime)
                val root = f.root
                val library = root.board.containers.single { it.zone == PlayZone.LIBRARY && it.accountId == f.a.account.accountId }
                val field = root.board.containers.single { it.zone == PlayZone.BATTLEFIELD && it.accountId == f.a.account.accountId }
                val card = library.cards.first()
                fun play(action: PlayAction) = f.owner.execute(f.owner.command(action)).also { assertEquals("accepted", it.outcome) }
                play(PlayAction.MoveCard(card, MoveSource(library.id), MoveDestination(field.id, MoveInsertion.EMPTY, BoardPosition(0, 0))))
                play(PlayAction.SetNote(card, "permanent"))
                play(PlayAction.SetDamage(card, "2"))
                play(PlayAction.SetEotCorner(card, "+3/+3"))
                play(PlayAction.SetEotNote(card, "can't block"))
                val marked = f.games.head(f.a, f.game.id).play!!
                val endCommand = f.owner.command(PlayAction.EndTurn)
                val end = f.owner.execute(endCommand)
                assertEquals("accepted", end.outcome)
                val transition = end.mutation!!.transition
                assertEquals(CURRENT_GAMEPLAY_RULES_VERSION, transition.rulesVersion)
                assertEquals(3, transition.fields.count { it.targetId == card })
                val cleared = f.games.head(f.b, f.game.id).play!!
                val after = cleared.board.cards.single { it.id == card }
                assertEquals("", after.damage); assertEquals("", after.eotCorner); assertEquals("", after.eotNote)
                assertEquals("permanent", after.note)
                assertEquals(marked.board, GameplayHistory.reverse(cleared, end.mutation!!))
                val undo = f.owner.execute(endCommand.copy(requestId = gameplayId(), invocationId = null, action = GameAction.Undo))
                assertEquals("accepted", undo.outcome)
                assertEquals(marked.board, f.games.head(f.b, f.game.id).play!!.board)
                val redo = f.owner.execute(endCommand.copy(requestId = gameplayId(), invocationId = null, action = GameAction.Redo))
                assertEquals("accepted", redo.outcome)
                assertEquals(cleared.board, f.games.head(f.b, f.game.id).play!!.board)
                val history = f.games.history(f.b, f.game.id, root.id, "0")
                var replay = root
                history.mutations.forEach { replay = GameplayHistory.replay(replay, it).state!! }
                assertEquals(cleared.board, replay.board)
            }
        }
    }
}
