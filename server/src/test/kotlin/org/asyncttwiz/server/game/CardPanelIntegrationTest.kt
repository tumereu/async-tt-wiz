package org.asyncttwiz.server.game

import org.asyncttwiz.protocol.*
import org.asyncttwiz.server.ServiceRuntime
import org.asyncttwiz.server.database.FoundationMigrations
import org.asyncttwiz.server.database.OwnedPostgres
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class CardPanelIntegrationTest {
    @Test fun `opponent loses the current face grant after moving the last revealed panel card to hand`() {
        OwnedPostgres().start().use { db ->
            FoundationMigrations.migrate(db.settings)
            ServiceRuntime(db.settings).use { runtime ->
                runtime.start(); gameplayAwait(runtime::ready)
                val f = GameplayFixture(runtime, deckSize = 10)
                val actor = f.a.account.accountId
                val opponent = f.b.account.accountId
                fun state() = f.games.head(f.b, f.game.id).play!!
                fun send(action: PlayAction) = f.owner.execute(f.owner.command(action))
                val library = state().board.containers.single { it.zone == PlayZone.LIBRARY && it.accountId == actor }
                val hand = state().board.containers.single { it.zone == PlayZone.HAND && it.accountId == actor }
                for (revealAll in listOf(false, true)) {
                    assertEquals("accepted", send(PlayAction.ShowContents(library.id, ContentsSelection.TOP, 1, revealAll)).outcome)
                    val panel = state().board.contentsDisplays.single()
                    val id = panel.cardIds.single()
                    if (!revealAll) assertEquals("accepted", send(PlayAction.RevealContentsCard(panel.id, id)).outcome)
                    val revealed = state()
                    assertFalse(ManualDisclosure.permittedFaces(revealed, revealed.board.cards.single { it.id == id }, opponent, true).isEmpty())
                    val result = send(PlayAction.MoveCard(id, MoveSource(library.id), MoveDestination(hand.id, MoveInsertion.UNORDERED)))
                    assertEquals("accepted", result.outcome)
                    val hidden = state()
                    val card = hidden.board.cards.single { it.id == id }
                    assertEquals(hand.id, card.containerId)
                    assertTrue(card.covered)
                    assertFalse(card.knownToAll)
                    assertTrue(hidden.board.contentsDisplays.isEmpty())
                    assertFalse(CardVisibility.isVisibleFaceUp(hidden.board, card, opponent))
                    assertTrue(ManualDisclosure.permittedFaces(hidden, card, opponent, true).isEmpty())
                    assertTrue(result.mutation!!.transition.disclosures.none { opponent in it.viewers })
                    assertEquals(revealed, GameplayHistory.unapply(hidden, result.mutation!!))
                    // Closing in the move frees the game-wide panel slot immediately.
                }
            }
        }
    }

    @Test fun `panel commands validate authorship membership and one panel through the real service`() {
        OwnedPostgres().start().use { db ->
            FoundationMigrations.migrate(db.settings)
            ServiceRuntime(db.settings).use { runtime ->
                runtime.start(); gameplayAwait(runtime::ready)
                val f = GameplayFixture(runtime, deckSize = 10)
                val actor = f.a.account.accountId
                fun state() = f.games.head(f.a, f.game.id).play!!
                val library = state().board.containers.single { it.zone == PlayZone.LIBRARY && it.accountId == actor }
                fun send(action: PlayAction, stream: GameplayFixture.Stream = f.owner) = stream.execute(stream.command(action))
                assertEquals("accepted", send(PlayAction.ShowContents(library.id, ContentsSelection.TOP, 3)).outcome)
                val panel = state().board.contentsDisplays.single()
                assertEquals("panelAlreadyOpen", send(PlayAction.ShowContents(library.id, ContentsSelection.ALL)).reason)
                val opponentLibrary = state().board.containers.single { it.zone == PlayZone.LIBRARY && it.accountId != actor }
                assertEquals("panelAlreadyOpen", send(PlayAction.ShowContents(opponentLibrary.id, ContentsSelection.TOP), f.recipient).reason)
                // Rules v29: the opponent may close it too, but never into a public pile it has not seen.
                assertEquals("accessDenied", send(PlayAction.DismissContents(panel.id, ContentsRest.GRAVEYARD), f.recipient).reason)
                assertEquals("accessDenied", send(PlayAction.RevealContentsCard(panel.id, panel.cardIds.first()), f.recipient).reason)
                assertEquals("accepted", send(PlayAction.ReorderContents(panel.id, panel.cardIds.last(), panel.cardIds.first())).outcome)
                val reveal = send(PlayAction.RevealContentsCard(panel.id, panel.cardIds.first()))
                assertEquals("accepted", reveal.outcome)
                assertEquals(2, reveal.mutation!!.transition.disclosures.single().viewers.size)
                val opponent = f.games.head(f.b, f.game.id).play!!
                assertEquals(state().board.contentsDisplays, opponent.board.contentsDisplays)
                val rest = state().board.contentsDisplays.single().cardIds
                assertEquals("accepted", send(PlayAction.DismissContents(panel.id, ContentsRest.BOTTOM)).outcome)
                assertTrue(state().board.contentsDisplays.isEmpty())
                assertEquals(rest, state().board.containers.single { it.id == library.id }.cards.takeLast(3))
                assertEquals("noOp", send(PlayAction.DismissContents(panel.id)).outcome)
                assertEquals("accepted", send(PlayAction.ShowContents(library.id, ContentsSelection.TOP, 2)).outcome)
                val forgotten = state().board.contentsDisplays.single()
                assertEquals("accepted", send(PlayAction.DismissContents(forgotten.id), f.recipient).outcome)
                assertTrue(state().board.contentsDisplays.isEmpty())
                assertEquals(forgotten.cardIds, state().board.containers.single { it.id == library.id }.cards.take(2))
            }
        }
    }
}
