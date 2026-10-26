package org.asyncttwiz.server.game

import org.asyncttwiz.protocol.*
import org.asyncttwiz.server.ServiceRuntime
import org.asyncttwiz.server.database.FoundationMigrations
import org.asyncttwiz.server.database.OwnedPostgres
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class KeepTopRevealedIntegrationTest {
    @Test fun `either player controls a durable undoable continuous reveal shared with the opponent`() {
        OwnedPostgres().start().use { db ->
            FoundationMigrations.migrate(db.settings)
            ServiceRuntime(db.settings).use { runtime ->
                runtime.start(); gameplayAwait(runtime::ready)
                val f = GameplayFixture(runtime, deckSize = 10)
                val actor = f.a.account.accountId
                val opponent = f.b.account.accountId
                fun state() = f.games.head(f.b, f.game.id).play!!
                val libraryId = state().board.containers.single { it.zone == PlayZone.LIBRARY && it.accountId == actor }.id
                val handId = state().board.containers.single { it.zone == PlayZone.HAND && it.accountId == actor }.id
                fun send(action: PlayAction, stream: GameplayFixture.Stream = f.owner) = stream.execute(stream.command(action))
                fun control(action: GameAction, stream: GameplayFixture.Stream = f.recipient) =
                    stream.execute(stream.command(PlayAction.NextStep).copy(action = action))
                fun assertTop() {
                    val play = state()
                    val library = play.board.containers.single { it.id == libraryId }
                    assertTrue(library.keepTopRevealed)
                    assertEquals(library.cards.first(), library.knownTop.first())
                    val top = play.board.cards.single { it.id == library.cards.first() }
                    for (viewer in listOf(actor, opponent)) {
                        assertTrue(CardVisibility.isVisibleFaceUp(play.board, top, viewer))
                        assertFalse(ManualDisclosure.permittedFaces(play, top, viewer, true).isEmpty())
                    }
                    assertEquals(play.board, f.games.head(f.a, f.game.id).play!!.board)
                }
                val initial = state()
                val on = PlayAction.SetKeepTopRevealed(libraryId, true)
                val command = f.recipient.command(on)
                val enabled = f.recipient.execute(command)
                assertEquals("accepted", enabled.outcome)
                f.recipient.admit(command)
                assertEquals(enabled, f.recipient.result(command)) // Retry returns the original recorded disclosure.
                assertTop()
                val enabledState = state()
                assertEquals("accepted", control(GameAction.Undo).outcome)
                assertEquals(initial.board, state().board)
                assertEquals("accepted", control(GameAction.Redo).outcome)
                assertTop()
                assertEquals(enabledState, state())
                val beforeDraw = state()
                val draw = send(LibraryActions.draw(beforeDraw.board, libraryId, handId, 1).single())
                assertEquals("accepted", draw.outcome)
                assertTrue(draw.mutation!!.transition.disclosures.single { it.containerId == libraryId }.automatic)
                assertTop()
                assertEquals(beforeDraw, GameplayHistory.unapply(state(), draw.mutation!!))
                assertEquals("accepted", send(PlayAction.ShuffleLibrary(libraryId, RandomV1.encodeSeed(ByteArray(32)))).outcome)
                assertTop()
                val beforeDisable = state()
                assertEquals("accepted", send(PlayAction.SetKeepTopRevealed(libraryId, false)).outcome)
                assertFalse(state().board.containers.single { it.id == libraryId }.keepTopRevealed)
                val disabledState = state()
                assertEquals("accepted", control(GameAction.Undo, f.owner).outcome)
                assertTop()
                assertEquals(beforeDisable, state())
                assertEquals("accepted", control(GameAction.Redo, f.owner).outcome)
                assertEquals(disabledState, state())
                assertFalse(state().board.cards.filter { it.containerId == libraryId }.any { CardVisibility.isVisibleFaceUp(state().board, it, opponent) })
            }
        }
    }
}
