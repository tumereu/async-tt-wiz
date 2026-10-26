package org.asyncttwiz.android

import org.asyncttwiz.protocol.*
import org.junit.Test
import kotlin.test.*

class TokenLinkControllerTest {
    @Test fun staleCardAndOccurrenceOpenCallbacksDoNotReplacePersonalScopeOrSubmit() {
        val current = state()
        val controller = TokenLinkController({ current }) { error("No implicit submission") }
        controller.open(id(9), emptyList())
        val scope = controller.scope
        controller.open(id(10), listOf(id(10), id(99)))
        assertEquals(scope, controller.scope)
        controller.open(id(99), emptyList())
        assertEquals(scope, controller.scope)
        controller.openOccurrences(ProjectionEndpoint(id(99)), emptyList())
        assertNull(controller.projectionScope)
        assertEquals(scope, controller.scope)
        controller.openOccurrences(ProjectionEndpoint(id(9)), listOf(ProjectionEndpoint(id(9)), ProjectionEndpoint(id(99))))
        assertNull(controller.projectionScope)
        assertEquals(scope, controller.scope)
    }
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
        var batches = 0
        val controller = TokenLinkController({ current }, { sent += it; true }, { batches++; sent += it; true })
        val selected = mutableListOf(id(9), id(10))
        controller.open(id(9), selected)
        selected.clear(); selected += id(11)
        current = current.copy(board = current.board.copy(cards = current.board.cards.reversed().map { it.copy(note = "sync") }))
        controller.clone()
        assertEquals<List<PlayAction>>(listOf(PlayAction.CloneToken(id(9)), PlayAction.CloneToken(id(10))), sent)
        assertEquals(1, batches)
        sent.clear()
        controller.open(id(11), listOf(id(9), id(10)))
        assertEquals(listOf(id(11)), controller.scope!!.cardIds)
        controller.clone()
        assertEquals<List<PlayAction>>(listOf(PlayAction.CloneToken(id(11))), sent)
        assertEquals(1, batches)
        assertEquals(listOf(id(11)), selected) // controller never mutates personal selection
        assertTrue(current.board.cards.all { it.note == "sync" }) // no fake local shared edit
    }

    @Test fun selectedPairsSkipExistingLinksAndShareOneInvocation() {
        fun endpoint(n: Int) = LinkEndpoint(LinkEndpointKind.CARD, id(n))
        val current = state().let { it.copy(board = it.board.copy(links = listOf(PlayLink(id(20), endpoint(10), endpoint(9))))) }
        val batches = mutableListOf<List<PlayAction>>()
        val controller = TokenLinkController({ current }, { error("Must use one invocation") }, { batches += it; true })
        controller.open(id(9), listOf(id(9), id(10), id(11), id(9)))
        assertTrue(controller.canLink()); assertTrue(controller.linkSelected())
        assertEquals(listOf(PlayAction.Link(endpoint(9), endpoint(11)), PlayAction.Link(endpoint(10), endpoint(11))), batches.single())
        controller.open(id(9), emptyList())
        assertFalse(controller.canLink()); assertFalse(controller.linkSelected())
        assertEquals(1, batches.size)
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
        sent.clear()
        assertFalse(controller.canLink()); assertFalse(controller.linkSelected())
        assertTrue(sent.isEmpty())
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

    @Test fun malformedCapturedIdsAreRejectedAndUnknownSelectionIsRefusedWithoutTrimming() {
        val current = state()
        val controller = TokenLinkController({ current }) { error("must not send") }
        assertFailsWith<IllegalArgumentException> { controller.open("bad", emptyList()) }
        assertFailsWith<IllegalArgumentException> { controller.open(id(9), listOf("bad")) }
        controller.open(id(9), listOf(id(9), id(99)))
        assertNull(controller.scope, "An unknown member refuses the entire selection, not just that member")
    }
}
