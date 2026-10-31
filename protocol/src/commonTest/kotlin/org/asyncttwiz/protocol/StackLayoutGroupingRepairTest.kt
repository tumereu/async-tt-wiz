package org.asyncttwiz.protocol

import kotlin.test.*

/** Full-engine requests: Stack layout is L; actual entries/removals remain isolated I anchors. Every entry
 * points at a real card that stays where it is (STK-001, STK-003). */
class StackLayoutGroupingRepairTest {
    private class Journey {
        var state = gameplayFixture()
        private val mutations = mutableListOf<GameplayMutation>()
        private fun container(zone: PlayZone) = state.board.containers.single { it.zone == zone }

        fun step(action: PlayAction, request: Int, significance: HistorySignificance, icon: String,
            start: Int = request, representative: Int = request, count: Int = 1,
            category: HistoryCategory = significance.category): GameplayMutation {
            val before = state
            val result = gameplayChange(before, action, request)
            assertEquals("accepted", result.outcome, "$action: ${result.reason}")
            val mutation = assertNotNull(result.mutation)
            val untap = action is PlayAction.SetTapped && !action.tapped
            val entryId = if (significance == HistorySignificance.STACK_ENTRY)
                (action as? PlayAction.MoveCard)?.cardId ?: mutation.transition.combatStack?.effects?.single()?.id else null
            assertEquals(HistorySemantic(significance, HistoryIcon.fromWire(icon), untap = untap, rulesVersion = 3, stackEntryId = entryId), mutation.grouping)
            assertEquals(result, GameplayHistory.replay(before, mutation))
            assertEquals(before.currentNodeId, mutation.parentId)
            state = assertNotNull(result.state)
            val group = assertNotNull(state.currentGroup)
            assertEquals(CommandIds.uuidV5(gameplayId(start), "group/0"), group.id)
            assertEquals(CommandIds.uuidV5(gameplayId(start), "board-node"), group.firstNodeId)
            assertEquals(mutation.id, group.lastNodeId)
            assertEquals(if (untap) HistoryCategory.U.key else category.key, group.category)
            assertEquals(count.toString(), group.count)
            assertEquals(CommandIds.uuidV5(gameplayId(representative), "board-node"), group.representative?.nodeId)
            mutations += mutation
            assertEquals(group, mutations.fold<GameplayMutation, GameplayGroup?>(null, GameplayHistory::append))
            return mutation
        }

        /** A Stack entry pointing at [card]; a real card never enters the Stack itself (STK-003). */
        fun entry(card: Int, request: Int, start: Int = request, representative: Int = request, count: Int = 1): String {
            val before = state
            val mutation = step(PlayAction.Stack(StackAction.AddEffect(gameplayId(card), GameplayRules.stackDigest(before))),
                request, HistorySignificance.STACK_ENTRY, "layers", start, representative, count)
            val effect = mutation.transition.combatStack!!.effects.single().id
            assertEquals(listOf(effect) + container(PlayZone.STACK).cards.drop(1), container(PlayZone.STACK).cards)
            assertEquals(before.board.cards, state.board.cards) // The real card stays where it is.
            return effect
        }
    }

    @Test fun entriesAndRemovalsKeepEveryPrefixBoundary() {
        val journey = Journey()
        val ten = journey.entry(10, 100)
        val eleven = journey.entry(11, 101)
        assertEquals(listOf(eleven, ten), CombatStackRules.stack(journey.state.board).cards)
        journey.step(PlayAction.Stack(StackAction.RemoveEffect(eleven)), 102, HistorySignificance.STACK_REMOVAL, "layers_clear")
        val card = journey.state.board.cards.single { it.id == gameplayId(10) }
        val hand = journey.state.board.containers.single { it.zone == PlayZone.HAND }
        journey.step(PlayAction.MoveCard(card.id, MoveSource(card.containerId, card.placement), MoveDestination(hand.id,
            MoveInsertion.UNORDERED), removeStackEntryId = ten), 103, HistorySignificance.STACK_REMOVAL, "layers_clear")
        assertTrue(CombatStackRules.stack(journey.state.board).cards.isEmpty())
    }

    @Test fun effectLayoutsShareFirstRepresentativeButRemovalsStayIsolated() {
        val journey = Journey()
        val first = journey.entry(12, 200)
        val second = journey.entry(10, 201)
        journey.step(PlayAction.Stack(StackAction.ReorderEffect(first, IdentityGap(second), IdentityGap(null, second))),
            202, HistorySignificance.STACK_LAYOUT, "open_with")
        journey.step(PlayAction.Stack(StackAction.ReorderEffect(first, IdentityGap(null, second), IdentityGap(second))),
            203, HistorySignificance.STACK_LAYOUT, "open_with", start = 202, representative = 202, count = 2)
        assertEquals(listOf(second, first), CombatStackRules.stack(journey.state.board).cards)
        journey.step(PlayAction.Stack(StackAction.RemoveEffect(first)), 204, HistorySignificance.STACK_REMOVAL, "layers_clear")
        journey.step(PlayAction.Stack(StackAction.RemoveEffect(second)), 205, HistorySignificance.STACK_REMOVAL, "layers_clear")
        assertTrue(journey.state.board.stackEffects.isEmpty())
    }

    @Test fun tapPrefixesTakeActualLayoutEntryRemovalAndInspectionRepresentativesWithoutMovingStarts() {
        val journey = Journey()
        journey.state = journey.state.withBattlefieldCards(gameplayId(14))
        val ten = journey.entry(10, 300)
        val eleven = journey.entry(11, 301)
        // Orientation prefixes still group with Stack actions, but address a Battlefield card.
        journey.step(PlayAction.SetTapped(gameplayId(14), true), 302, HistorySignificance.ORIENTATION, "rotate_90_degrees_cw",
            start = 301, representative = 301, count = 2, category = HistoryCategory.I)
        journey.step(PlayAction.Stack(StackAction.ReorderEffect(ten, IdentityGap(eleven), IdentityGap(null, eleven))),
            303, HistorySignificance.STACK_LAYOUT, "open_with")
        journey.step(PlayAction.SetTapped(gameplayId(14), false), 304, HistorySignificance.ORIENTATION, "rotate_90_degrees_cw")
        journey.step(PlayAction.Stack(StackAction.RemoveEffect(ten)), 305, HistorySignificance.STACK_REMOVAL, "layers_clear")
        journey.step(PlayAction.SetTapped(gameplayId(14), true), 306, HistorySignificance.ORIENTATION, "rotate_90_degrees_cw")
        journey.entry(12, 307, start = 306, representative = 307, count = 2)
        journey.step(PlayAction.SetTapped(gameplayId(14), false), 308, HistorySignificance.ORIENTATION, "rotate_90_degrees_cw")
        val card = journey.state.board.cards.single { it.id == gameplayId(14) }
        journey.step(PlayAction.InspectFace(card.id, card.faceId, card.containerId), 309,
            HistorySignificance.EXPLICIT_DISCLOSURE, "visibility")
    }
}
