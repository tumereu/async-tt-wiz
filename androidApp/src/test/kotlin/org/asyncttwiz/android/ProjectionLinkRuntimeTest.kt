package org.asyncttwiz.android

import org.asyncttwiz.protocol.*
import org.junit.Test
import kotlin.test.*

class ProjectionLinkRuntimeTest {
    @Test fun normalToolkitMountPicksExactOccurrencesAndLowersFanoutThroughOneInvocation() {
        val current = ready()
        val (first, second, target) = current.board.stackEffects.map { it.id }
        val requests = mutableListOf<Pair<PlayAction, String?>>()
        val toolkit = TableToolkitController(id(7), { current }, { true }, { action, invocation ->
            requests += action to invocation; true
        })
        toolkit.openCard(TableScope(first, listOf(first, second)))
        assertEquals(listOf(first, second), toolkit.tokens.projectionScope!!.endpoints.map { it.occurrenceId })
        assertNull(toolkit.tokens.scope)
        toolkit.tokens.beginLink()
        val item = TableItem("card/$target", TableBounds(0f, 0f, 48f, 68f), "effect", "", kind = TableItemKind.CARD,
            cardId = target, sourceId = id(20), projection = true)
        toolkit.open(TableIntent.Actions(item.id, TableScope(target, listOf(target))), item, TableSelection())
        assertEquals(listOf(PlayAction.LinkProjections(ProjectionEndpoint(first), ProjectionEndpoint(target)),
            PlayAction.LinkProjections(ProjectionEndpoint(second), ProjectionEndpoint(target))), requests.map { it.first })
        assertEquals(1, requests.map { it.second }.distinct().size)
        assertNotNull(requests.first().second)
        assertTrue(current.board.projectionLinks.isEmpty(), "The UI callback never writes shared state")
    }
    private fun id(n: Int) = "00000000-0000-4000-8000-${n.toString().padStart(12, '0')}"
    private fun state(): PlayState {
        val card = PlayCard(id(20), TokenCatalog.blank.digest, id(7), id(10),
            TokenCatalog.blank.definition.faces.first().faceId, covered = true, token = true,
            placement = PlayPlacement(BoardPosition(0, 0), id(21)))
        return PlayState(id(1), id(2), id(3), id(4), PlayRoot(id(5), id(6)), id(5),
            BoardState(listOf(PlayPlayer(id(7)), PlayPlayer(id(8))), listOf(card),
                listOf(PlayContainer(id(10), PlayZone.BATTLEFIELD, id(7), listOf(card.id)), PlayContainer(id(11), PlayZone.STACK)),
                TurnState(id(7))), listOf(TokenCatalog.blank), emptyList(), InitialStarter(StarterChoice(id(7)), id(7)))
    }
    private fun command(action: PlayAction, n: Int, invocation: String = id(500)) = GameCommand(id(n),
        GameScope(id(1), playId = id(2)), GameAction.Play(action), id(9), "1", invocationId = invocation)
    private fun apply(play: PlayState, action: PlayAction, n: Int) = GameplayRules.change(play, id(7), command(action, n)).state!!
    private fun ready(): PlayState {
        var current = state()
        repeat(3) { n -> current = apply(current,
            PlayAction.Stack(StackAction.AddEffect(id(20), GameplayRules.stackDigest(current))), 100 + n) }
        return current
    }

    @Test fun typedCapturedOccurrenceFanoutQueuesIndividualOriginalsWithOneInvocationBeforeResults() {
        var current = ready()
        val original = current
        val (a, b, c) = current.board.stackEffects.map { ProjectionEndpoint(it.id) }
        val queued = mutableListOf<GameCommand>()
        var invocations = 0
        var request = 200
        val controller = TokenLinkController(current = { current }, submit = { error("No single source callback") },
            submitInvocation = { actions ->
                invocations++
                actions.forEach { queued += command(it, request++, id(600 + invocations)) }
                true
            })
        val selected = mutableListOf(a, b, a)
        controller.openOccurrences(a, selected)
        selected.clear(); selected += c
        controller.beginLink()
        assertTrue(controller.pick(LinkEndpoint(LinkEndpointKind.CARD, id(20)))) // incompatible hit consumed
        assertTrue(queued.isEmpty()); assertTrue(controller.pickingLink)
        assertTrue(controller.pick(c)); assertFalse(controller.pickingLink)
        assertEquals(1, invocations); assertEquals(2, queued.size)
        assertEquals(1, queued.map { it.invocationId }.distinct().size)
        assertEquals(2, queued.map { it.requestId }.distinct().size)
        assertEquals(listOf(PlayAction.LinkProjections(a, c), PlayAction.LinkProjections(b, c)), queued.map { (it.action as GameAction.Play).action })
        assertEquals(original, current) // no result read and no fake local board write
        queued.forEach { current = GameplayRules.change(current, id(7), it).state!! }
        assertEquals(2, current.board.projectionLinks.size)
        assertTrue(current.board.links.isEmpty()); assertEquals(original.board.cards, current.board.cards)
        assertEquals(listOf(a, b), controller.projectionScope!!.endpoints)
        assertNull(controller.scope)
        assertFalse(controller.pick(c)) // no ordinary tap consumed outside picking
        val labels = LinkActions.projectionEndpoints(current)
        assertEquals(3, labels.size); assertEquals(3, labels.map { it.endpoint }.distinct().size)
        assertTrue(labels.all { it.label.startsWith("Covered card") })
        assertEquals(2, LinkActions.connections(current, controller.projectionScope!!).size)
        val link = current.board.projectionLinks.first()
        queued.clear(); controller.links(); controller.removeProjectionLink(link.id)
        assertEquals(2, invocations)
        assertEquals(PlayAction.RemoveProjectionLink(link.id), (queued.single().action as GameAction.Play).action)
        current = GameplayRules.change(current, id(7), queued.single()).state!!
        assertEquals(1, current.board.projectionLinks.size)
        val before = queued.size
        controller.clone(); controller.removeTokens(); controller.removeLink(link.id)
        assertEquals(before, queued.size) // occurrence scope is not a source-card selection
    }

    @Test fun vanishedCapturesNeverSubstituteSiblingsAndFrozenEndpointsKeepPreciseRemoval() {
        var current = ready()
        val (a, b, c) = current.board.stackEffects.map { ProjectionEndpoint(it.id) }
        val batches = mutableListOf<List<PlayAction>>()
        val controller = TokenLinkController({ current }, { error("must not edit source") }, { batches += it; true })
        controller.openOccurrences(a, listOf(a, b))
        current = apply(current, PlayAction.Stack(StackAction.RemoveEffect(a.occurrenceId)), 210)
        controller.beginLink(); assertTrue(controller.pick(a))
        assertNotNull(controller.error); assertTrue(batches.isEmpty()); assertTrue(controller.pickingLink)
        assertTrue(controller.pick(c))
        assertEquals(listOf(PlayAction.LinkProjections(b, c)), batches.single())
        current = apply(current, batches.single().single(), 211)
        val link = current.board.projectionLinks.single()
        current = apply(current, PlayAction.RemoveToken(id(20)), 212)
        assertEquals(listOf(link), current.board.projectionLinks)
        assertTrue(current.board.stackEffects.all { it.frozen != null })
        controller.openOccurrences(b, listOf(b, c)); controller.links()
        assertEquals(listOf(link), LinkActions.connections(current, controller.projectionScope!!))
        controller.removeProjectionLink(link.id)
        assertEquals(listOf(PlayAction.RemoveProjectionLink(link.id)), batches.last())
        val count = batches.size
        controller.removeProjectionLink(id(999)); assertEquals(count, batches.size)
        current = current.copy(id = id(999))
        controller.beginLink(); controller.pick(c); controller.removeProjectionLink(link.id)
        assertEquals(count, batches.size)
        assertFailsWith<IllegalArgumentException> { controller.openOccurrences(ProjectionEndpoint(id(20)), emptyList()) }
    }

    @Test fun failedInvocationRetainsCaptureAndLegacySingleCallbackCannotSilentlyLoseInvocationIdentity() {
        val current = ready()
        val (a, b, c) = current.board.stackEffects.map { ProjectionEndpoint(it.id) }
        var accepted = false
        val batches = mutableListOf<List<PlayAction>>()
        val controller = TokenLinkController({ current }, { error("not a source edit") }, { batches += it; accepted })
        controller.openOccurrences(a, listOf(a, b)); controller.beginLink(); controller.pick(c)
        assertTrue(controller.pickingLink); assertNotNull(controller.error)
        accepted = true; controller.pick(c)
        assertEquals(batches.first(), batches.last()); assertFalse(controller.pickingLink); assertNull(controller.error)
        val legacy = TokenLinkController({ current }) { error("occurrences need a session invocation adapter") }
        legacy.openOccurrences(a, listOf(a, b)); legacy.beginLink(); assertTrue(legacy.pick(c))
        assertTrue(legacy.pickingLink); assertNotNull(legacy.error)
        legacy.open(id(20), emptyList()); legacy.beginLink(); assertTrue(legacy.pick(b)) // mixed endpoint consumed, no send
        assertTrue(legacy.pickingLink)
    }
}
