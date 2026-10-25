package org.asyncttwiz.server.game

import org.asyncttwiz.protocol.*
import org.asyncttwiz.server.ServiceRuntime
import org.asyncttwiz.server.database.FoundationMigrations
import org.asyncttwiz.server.database.OwnedPostgres
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class CardPanelIntegrationTest {
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
                assertEquals("accessDenied", send(PlayAction.DismissContents(panel.id), f.recipient).reason)
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
            }
        }
    }
}
