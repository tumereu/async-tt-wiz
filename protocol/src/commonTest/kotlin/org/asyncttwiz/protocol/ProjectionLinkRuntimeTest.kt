package org.asyncttwiz.protocol

import kotlinx.serialization.encodeToString
import kotlin.test.*

class ProjectionLinkRuntimeTest {
    @Test fun retainedRuntimeChargesProjectionCreationAndColdReplaysCompoundCleanupWithoutPartialInverse() {
        val root = ready().let { it.copy(currentNodeId = it.root.id, currentGroup = null) }
        val (first, second) = root.board.stackEffects
        val linked = GameplayTree(root).play(id(7), command(link(first.id, second.id), 180)).tree!!
        val mutation = linked.nodes.getValue(linked.currentNodeId)
        assertEquals(1L, mutation.transition.effectUnits())
        assertEquals("workLimit", SelectiveUndo.change(linked, id(7), commandUndo(181), SelectiveUndoLimits(effectUnits = 0)).reason)
        assertNotNull(SelectiveUndo.change(linked, id(7), commandUndo(182), SelectiveUndoLimits(effectUnits = 1)).tree)
        val cleanup = linked.play(id(8), command(PlayAction.Stack(StackAction.RemoveEffect(first.id)), 183)).tree!!
        val compound = cleanup.nodes.getValue(cleanup.currentNodeId)
        assertTrue(compound.transition.effectUnits() > GameplayHistory.projectionLinkWorkUnits(compound.transition))
        assertNull(GameplayHistory.inverseProjectionLinks(cleanup.stateAt(cleanup.currentNodeId)!!.board, compound.transition))
        assertEquals(linked.stateAt(linked.currentNodeId)!!.board, cleanup.stateAt(compound.parentId)!!.board)
        val restored = HistoryContinuation.change(cleanup, id(8), commandUndo(184)).tree!!
        assertEquals(linked.stateAt(linked.currentNodeId)!!.board, restored.stateAt(restored.currentNodeId)!!.board)
        val redone = HistoryContinuation.change(restored, id(8), commandUndo(185).copy(action = GameAction.Redo)).tree!!
        assertEquals(cleanup.stateAt(cleanup.currentNodeId)!!.board, redone.stateAt(redone.currentNodeId)!!.board)
    }
    private fun id(n: Int) = "00000000-0000-4000-8000-${n.toString().padStart(12, '0')}"
    private fun state(): PlayState {
        val card = PlayCard(id(20), TokenCatalog.blank.digest, id(7), id(10),
            TokenCatalog.blank.definition.faces.first().faceId, corner = "5/2", note = "last live", token = true,
            placement = PlayPlacement(BoardPosition(0, 0), id(21)))
        return PlayState(id(1), id(2), id(3), id(4), PlayRoot(id(5), id(6)), id(5),
            BoardState(listOf(PlayPlayer(id(7)), PlayPlayer(id(8))), listOf(card),
                listOf(PlayContainer(id(10), PlayZone.BATTLEFIELD, id(7), listOf(card.id)),
                    PlayContainer(id(11), PlayZone.STACK), PlayContainer(id(12), PlayZone.GRAVEYARD, id(7))), TurnState(id(7))),
            listOf(TokenCatalog.blank), emptyList(), InitialStarter(StarterChoice(id(7)), id(7)))
    }
    private fun command(action: PlayAction, n: Int) = GameCommand(id(n), GameScope(id(1), playId = id(2)),
        GameAction.Play(action), id(9), "1", invocationId = id(500))
    private fun change(state: PlayState, action: PlayAction, n: Int): GameplayChange {
        val cmd = command(action, n)
        assertTrue(GameWire.valid(cmd))
        assertEquals(cmd, GameWire.decodeClient(GameWire.json.encodeToString<GameClientMessage>(cmd)))
        return GameplayRules.change(state, id(7), cmd).also { result ->
            result.mutation?.let { mutation ->
                assertEquals(result, GameplayHistory.replay(state, mutation))
                assertEquals(mutation, GameWire.json.decodeFromString<GameplayMutation>(GameWire.json.encodeToString(mutation)))
            }
        }
    }
    private fun apply(state: PlayState, action: PlayAction, n: Int) = change(state, action, n).also {
        assertEquals("accepted", it.outcome)
    }.state!!
    private fun effect(state: PlayState, n: Int) = apply(state,
        PlayAction.Stack(StackAction.AddEffect(id(20), GameplayRules.stackDigest(state))), n)
    private fun link(a: String, b: String) = PlayAction.LinkProjections(ProjectionEndpoint(a), ProjectionEndpoint(b))
    private fun ready(): PlayState {
        val opened = apply(state(), PlayAction.Combat(CombatAction.Open), 100)
        val declared = apply(opened, PlayAction.Combat(CombatAction.Declare(opened.board.combat!!.id, id(20))), 101)
        return effect(effect(effect(declared, 102), 103), 104)
    }

    @Test fun actualCreationUndirectedNoopRemovalAndCapturedIdentityNeverAliasSource() {
        val initial = ready()
        val (a, b, c) = initial.board.stackEffects
        val created = change(initial, link(a.id, b.id), 110)
        val mutation = created.mutation!!
        val connection = created.state!!.board.projectionLinks.single()
        assertEquals(CommandIds.uuidV5(id(110), "projection-link/0"), connection.id)
        assertEquals(CombatStackRules.projectionPair(a.id, b.id), connection.first to connection.second)
        assertEquals(listOf(connection), mutation.transition.combatStack!!.addedLinks)
        assertTrue(created.state.board.links.isEmpty())
        assertEquals(initial.board.cards, created.state.board.cards)
        assertEquals(initial.board, GameplayHistory.inverseProjectionLinks(created.state.board, mutation.transition))
        assertEquals(1L, GameplayHistory.projectionLinkWorkUnits(mutation.transition))
        assertEquals(setOf(connection.id, a.id, b.id), GameplayHistory.projectionLinkReferences(mutation.transition))
        assertEquals(listOf(ProjectionLinkChange(null, connection)), GameplayHistory.projectionLinkChanges(mutation.transition))
        assertEquals(HistorySignificance.LINK_EDIT, mutation.grouping!!.significance)
        assertEquals("link", mutation.grouping.icon)
        for (n in listOf(110, 111)) {
            val repeated = change(created.state, link(b.id, a.id), n)
            assertEquals("noOp", repeated.outcome); assertEquals(created.state, repeated.state)
            assertNull(repeated.mutation)
        }
        val sibling = apply(created.state, link(a.id, c.id), 112)
        assertEquals(2, sibling.board.projectionLinks.size) // same source does not deduplicate occurrences
        val removed = change(sibling, PlayAction.RemoveProjectionLink(connection.id), 113)
        assertEquals(listOf(connection), removed.mutation!!.transition.combatStack!!.removedLinks)
        assertEquals("link_off", removed.mutation.grouping!!.icon)
        assertEquals(sibling.board, GameplayHistory.inverseProjectionLinks(removed.state!!.board, removed.mutation.transition))
        assertEquals("noOp", change(removed.state, PlayAction.RemoveProjectionLink(connection.id), 114).outcome)
        assertEquals("missingReference", change(initial, link(id(20), b.id), 115).reason)
        assertEquals("missingReference", change(initial, link(id(7), b.id), 116).reason)
        assertEquals("missingReference", change(initial, PlayAction.Link(LinkEndpoint(LinkEndpointKind.CARD, a.id),
            LinkEndpoint(LinkEndpointKind.CARD, id(20))), 117).reason)
        assertEquals("inapplicable", change(initial, link(a.id, a.id), 118).reason)
        assertFalse(GameplayRules.valid(link("invalid", a.id)))
        val vanished = apply(initial, PlayAction.Stack(StackAction.RemoveEffect(a.id)), 119)
        assertEquals("missingReference", change(vanished, link(a.id, b.id), 120).reason)
        assertEquals("replayBlocked", GameplayHistory.replay(vanished, mutation).reason)
        assertTrue(vanished.board.stackEffects.any { it.id == c.id })
        val tampered = mutation.copy(transition = mutation.transition.copy(combatStack = mutation.transition.combatStack!!.copy(
            addedLinks = listOf(connection.copy(second = c.id)))))
        assertEquals("replayBlocked", GameplayHistory.replay(initial, tampered).reason)
        val afterSourceEdit = apply(initial, PlayAction.SetNote(id(20), "unrelated"), 121)
        assertEquals(mutation.transition, GameplayHistory.replay(afterSourceEdit, mutation).mutation!!.transition)
    }

    @Test fun repositionRetainsLinksAndEveryDisappearanceCleansOnlyIncidentOccurrencesInOneMutation() {
        var current = ready()
        val combat = CombatStackRules.occurrences(current.board.combat).single()
        val (a, b, c) = current.board.stackEffects
        current = apply(current, link(combat.id, a.id), 130)
        current = apply(current, link(a.id, b.id), 131)
        current = apply(current, link(b.id, c.id), 132)
        val card = current.board.cards.single()
        val sourceLink = PlayAction.Link(LinkEndpoint(LinkEndpointKind.CARD, card.id), LinkEndpoint(LinkEndpointKind.PLAYER, id(8)))
        current = apply(current, sourceLink, 133)
        val original = current
        current = apply(current, PlayAction.MoveCard(card.id, MoveSource(card.containerId, card.placement),
            MoveDestination(card.containerId, MoveInsertion.EMPTY, BoardPosition(7, 8))), 134)
        val area = current.board.combat!!
        current = apply(current, PlayAction.Combat(CombatAction.Place(card.id, CombatDestination(area.id, CombatRole.BLOCKER,
            area.pairs.single().id), CombatStackRules.location(area, combat.id))), 135)
        val order = CombatStackRules.stack(current.board).cards
        current = apply(current, PlayAction.Stack(StackAction.ReorderEffect(order.last(), IdentityGap(order[1], null),
            IdentityGap(null, order.first()))), 136)
        assertEquals(original.board.projectionLinks, current.board.projectionLinks)
        assertEquals(original.board.links, current.board.links)

        val cross = current.board.projectionLinks.single { combat.id in listOf(it.first, it.second) }
        for ((n, action) in listOf<PlayAction>(
            PlayAction.Combat(CombatAction.Close(area.id)), PlayAction.Combat(CombatAction.Remove(area.id, combat.id)),
            PlayAction.RemoveToken(card.id), PlayAction.MoveCard(card.id,
                MoveSource(card.containerId, current.board.cards.single().placement), MoveDestination(id(12), MoveInsertion.UNORDERED))
        ).withIndex()) {
            val result = change(current, action, 140 + n)
            assertEquals("accepted", result.outcome)
            val transition = result.mutation!!.transition
            assertEquals(listOf(cross), transition.combatStack!!.removedLinks)
            assertEquals(current.board.projectionLinks - cross, result.state!!.board.projectionLinks)
            assertEquals(1L, GameplayHistory.projectionLinkWorkUnits(transition))
            assertNull(GameplayHistory.inverseProjectionLinks(result.state.board, transition), "Never invert compound cleanup partially")
            assertNull(GameplayHistory.reverse(result.state, result.mutation))
            if (n >= 2) {
                assertTrue(result.state.board.cards.isEmpty())
                assertTrue(result.state.board.links.isEmpty())
                assertEquals(List(3) { current.board.cards.single() }, result.state.board.stackEffects.map { it.frozen })
                assertEquals(current.board.stackEffects.map { it.id }, result.state.board.stackEffects.map { it.id })
            } else {
                assertEquals(current.board.cards, result.state.board.cards)
                assertEquals(current.board.links, result.state.board.links)
            }
        }
        val frozen = apply(current, PlayAction.RemoveToken(card.id), 150)
        val resolved = change(frozen, PlayAction.Stack(StackAction.RemoveEffect(a.id)), 151)
        val incident = frozen.board.projectionLinks.filter { a.id in listOf(it.first, it.second) }
        assertEquals(incident, resolved.mutation!!.transition.combatStack!!.removedLinks)
        assertEquals(frozen.board.projectionLinks - incident.toSet(), resolved.state!!.board.projectionLinks)
        assertEquals(listOf(b.id, c.id), resolved.state.board.stackEffects.map { it.id })
        assertTrue(resolved.state.board.stackEffects.all { it.frozen == frozen.board.stackEffects.first().frozen })
        assertTrue(original.board.cards.isNotEmpty()); assertEquals(3, original.board.projectionLinks.size) // immutable history
    }

    @Test fun exactInverseRefusesChangedEndpointsCompoundFamiliesAndBrokenUniqueness() {
        val initial = ready()
        val (a, b) = initial.board.stackEffects
        val created = change(initial, link(a.id, b.id), 160)
        val transition = created.mutation!!.transition
        val board = created.state!!.board
        val link = board.projectionLinks.single()
        assertNull(GameplayHistory.inverseProjectionLinks(board.copy(projectionLinks = listOf(link.copy(first = id(999)))), transition))
        assertNull(GameplayHistory.inverseProjectionLinks(board, transition.copy(fields = listOf(
            GameplayFieldChange(id(20), GameplayField.NOTE, "x", "y")))))
        assertNull(GameplayHistory.inverseTokenLinks(board, transition))
        val removed = change(created.state, PlayAction.RemoveProjectionLink(link.id), 161)
        val gone = apply(removed.state!!, PlayAction.Stack(StackAction.RemoveEffect(a.id)), 162)
        assertNull(GameplayHistory.inverseProjectionLinks(gone.board, removed.mutation!!.transition))
        val duplicate = board.copy(projectionLinks = board.projectionLinks + link.copy(id = id(999), first = link.second, second = link.first))
        assertNull(CombatStackRules.closeInvariants(board, duplicate))
        assertNull(CombatStackRules.closeInvariants(board, board.copy(projectionLinks = listOf(link.copy(id = id(20))))))
        val root = initial.copy(currentNodeId = initial.root.id, currentGroup = null)
        val tree = GameplayTree(root).play(id(7), command(link(a.id, b.id), 164)).tree!!
        val undone = SelectiveUndo.change(tree, id(7), commandUndo(163)).tree!!
        assertEquals(initial.board, undone.stateAt(undone.currentNodeId)!!.board)
    }
    private fun commandUndo(n: Int) = command(PlayAction.SetTapped(id(20), true), n).copy(action = GameAction.Undo)
}
