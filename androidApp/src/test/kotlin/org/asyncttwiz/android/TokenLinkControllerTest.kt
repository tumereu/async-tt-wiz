package org.asyncttwiz.android

import org.asyncttwiz.protocol.*
import org.junit.Test
import kotlin.test.*

class TokenLinkControllerTest {
    private fun id(n: Int) = "00000000-0000-4000-8000-${n.toString().padStart(12, '0')}"
    private fun state() = PlayState(id(1), id(2), id(3), id(4), PlayRoot(id(5), id(6)), id(5),
        BoardState(listOf(PlayPlayer(id(7)), PlayPlayer(id(8))),
            listOf(PlayCard(id(9), TokenCatalog.blank.digest, id(7), id(12), TokenCatalog.blank.definition.faces.first().faceId),
                PlayCard(id(10), TokenCatalog.blank.digest, id(7), id(12), TokenCatalog.blank.definition.faces.first().faceId, token = true),
                PlayCard(id(11), TokenCatalog.blank.digest, id(8), id(12), TokenCatalog.blank.definition.faces.first().faceId)),
            listOf(PlayContainer(id(12), PlayZone.BATTLEFIELD, id(7), listOf(id(9), id(10), id(11)))), TurnState(id(7))),
        listOf(TokenCatalog.blank), emptyList(), InitialStarter(StarterChoice(id(7)), id(7)))

    @Test fun selectedAndUnselectedContextScopeSurvivesReflowSelectionAndSync() {
        var current = state()
        val sent = mutableListOf<PlayAction>()
        val controller = TokenLinkController({ current }) { sent += it; true }
        val selected = mutableListOf(id(9), id(10))
        controller.open(id(9), selected)
        selected.clear(); selected += id(11)
        current = current.copy(board = current.board.copy(cards = current.board.cards.reversed().map { it.copy(note = "sync") }))
        controller.clone()
        assertEquals<List<PlayAction>>(listOf(PlayAction.CloneToken(id(9)), PlayAction.CloneToken(id(10))), sent)
        sent.clear()
        controller.open(id(11), listOf(id(9), id(10)))
        assertEquals(listOf(id(11)), controller.scope!!.cardIds)
        controller.clone()
        assertEquals<List<PlayAction>>(listOf(PlayAction.CloneToken(id(11))), sent)
        assertEquals(listOf(id(11)), selected) // controller never mutates personal selection
        assertTrue(current.board.cards.all { it.note == "sync" }) // no fake local shared edit
    }

    @Test fun capturedSelectionFansOutToCardAndPlayerWithoutOrdinaryTap() {
        val current = state()
        val sent = mutableListOf<PlayAction>()
        val controller = TokenLinkController({ current }) { sent += it; true }
        controller.open(id(9), listOf(id(9), id(10), id(9)))
        controller.beginLink()
        val card = LinkEndpoint(LinkEndpointKind.CARD, id(11))
        assertTrue(controller.pick(card))
        assertEquals<List<PlayAction>>(listOf(PlayAction.Link(LinkEndpoint(LinkEndpointKind.CARD, id(9)), card),
            PlayAction.Link(LinkEndpoint(LinkEndpointKind.CARD, id(10)), card)), sent)
        assertFalse(controller.pickingLink)
        assertFalse(controller.pick(card))
        sent.clear(); controller.beginLink()
        val player = LinkEndpoint(LinkEndpointKind.PLAYER, id(8))
        assertTrue(controller.pick(player))
        assertEquals(2, sent.size)
        assertTrue(sent.all { it is PlayAction.Link && it.second == player })
        sent.clear(); controller.beginLink()
        controller.pick(LinkEndpoint(LinkEndpointKind.CARD, id(9)))
        assertEquals(1, sent.size) // no self-link, no losing the other selected source
    }

    @Test fun removalSkipsRealCardsAndVanishedTargetsNeverSubstitutes() {
        var current = state()
        val sent = mutableListOf<PlayAction>()
        val controller = TokenLinkController({ current }) { sent += it; true }
        controller.open(id(9), listOf(id(9), id(10)))
        controller.removeTokens()
        assertEquals<List<PlayAction>>(listOf(PlayAction.RemoveToken(id(10))), sent)
        sent.clear()
        current = current.copy(board = current.board.copy(cards = current.board.cards.filter { it.id != id(10) }))
        controller.clone()
        assertEquals<List<PlayAction>>(listOf(PlayAction.CloneToken(id(9))), sent)
        sent.clear(); controller.beginLink()
        assertTrue(controller.pick(LinkEndpoint(LinkEndpointKind.CARD, id(10)))) // consumes stale hit
        assertNotNull(controller.error); assertTrue(sent.isEmpty())
        current = current.copy(id = id(99)) // reset/new incarnation must not retarget
        controller.clone(); controller.removeTokens()
        assertTrue(sent.isEmpty())
    }

    @Test fun failedQueueKeepsInputsAndLinksRemovalOnlyAddressesCapturedConnections() {
        var current = state()
        var allowed = false
        val sent = mutableListOf<PlayAction>()
        val controller = TokenLinkController({ current }) { sent += it; allowed }
        controller.catalog(); controller.create(TokenCatalog.blank)
        assertTrue(controller.showingCatalog); assertNotNull(controller.error)
        allowed = true; controller.create(TokenCatalog.blank)
        assertFalse(controller.showingCatalog); assertNull(controller.error)
        assertEquals<List<PlayAction>>(listOf(PlayAction.CreateToken(TokenCatalog.blank), PlayAction.CreateToken(TokenCatalog.blank)), sent)
        val relevant = PlayLink(id(20), LinkEndpoint(LinkEndpointKind.CARD, id(9)), LinkEndpoint(LinkEndpointKind.PLAYER, id(7)))
        val other = PlayLink(id(21), LinkEndpoint(LinkEndpointKind.CARD, id(10)), LinkEndpoint(LinkEndpointKind.PLAYER, id(8)))
        current = current.copy(board = current.board.copy(links = listOf(relevant, other)))
        controller.open(id(9), emptyList()); sent.clear()
        controller.links(); controller.removeLink(other.id); controller.removeLink(relevant.id)
        assertEquals<List<PlayAction>>(listOf(PlayAction.RemoveLink(relevant.id)), sent)
        assertEquals(listOf(relevant, other), current.board.links)
    }

    @Test fun malformedOrUnknownCapturedIdsAreNotSilentlyDropped() {
        val current = state()
        val controller = TokenLinkController({ current }) { error("must not send") }
        assertFailsWith<IllegalArgumentException> { controller.open("bad", emptyList()) }
        assertFailsWith<IllegalArgumentException> { controller.open(id(9), listOf(id(9), id(99))) }
    }
}
