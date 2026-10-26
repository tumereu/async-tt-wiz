package org.asyncttwiz.protocol

import kotlin.test.*

/** Full-engine requests: Stack layout is L; actual entries/removals remain isolated I anchors. */
class StackLayoutGroupingRepairTest {
    private class Journey {
        var state = gameplayFixture()
        private val mutations = mutableListOf<GameplayMutation>()
        private fun container(zone: PlayZone) = state.board.containers.single { it.zone == zone }

        fun step(action: PlayAction, request: Int, significance: HistorySignificance, icon: String,
            start: Int = request, representative: Int = request, count: Int = 1): GameplayMutation {
            val before = state
            val result = gameplayChange(before, action, request)
            assertEquals("accepted", result.outcome, "$action: ${result.reason}")
            val mutation = assertNotNull(result.mutation)
            val untap = action is PlayAction.SetTapped && !action.tapped
            assertEquals(HistorySemantic(significance, icon, untap = untap, rulesVersion = 2), mutation.grouping)
            assertEquals(result, GameplayHistory.replay(before, mutation))
            assertEquals(before.currentNodeId, mutation.parentId)
            state = assertNotNull(result.state)
            val group = assertNotNull(state.currentGroup)
            assertEquals(CommandIds.uuidV5(gameplayId(start), "group/0"), group.id)
            assertEquals(CommandIds.uuidV5(gameplayId(start), "board-node"), group.firstNodeId)
            assertEquals(mutation.id, group.lastNodeId)
            assertEquals(if (untap) HistoryCategory.U.key else significance.category.key, group.category)
            assertEquals(count.toString(), group.count)
            assertEquals(CommandIds.uuidV5(gameplayId(representative), "board-node"), group.representative?.nodeId)
            mutations += mutation
            assertEquals(group, mutations.fold<GameplayMutation, GameplayGroup?>(null, GameplayHistory::append))
            return mutation
        }

        fun move(card: Int, to: PlayZone, insertion: MoveInsertion, request: Int,
            significance: HistorySignificance, start: Int = request, representative: Int = request,
            count: Int = 1, order: List<String>): GameplayMutation {
            val before = state
            val original = before.board.cards.single { it.id == gameplayId(card) }
            val target = container(to)
            val action = PlayAction.MoveCard(original.id, MoveSource(original.containerId, original.placement),
                MoveDestination(target.id, insertion), if (to == PlayZone.STACK && original.containerId != target.id)
                    GameplayRules.stackDigest(before) else null)
            val mutation = step(action, request, significance,
                if (significance.category == HistoryCategory.I) "layers" else "open_with", start, representative, count)
            val movement = assertNotNull(mutation.transition.movement)
            assertEquals(original.id, movement.cardId)
            assertEquals(original.containerId, movement.from.containerId)
            assertEquals(target.id, movement.to.containerId)
            assertEquals(GameplayRules.stackDigest(before), movement.stackFrom)
            assertEquals(GameplayRules.stackDigest(state), movement.stackTo)
            assertNotEquals(movement.stackFrom, movement.stackTo)
            val leavingField = before.board.containers.single { it.id == original.containerId }.zone == PlayZone.BATTLEFIELD
            assertEquals(original.copy(containerId = target.id, placement = null, covered = to == PlayZone.HAND,
                tapped = false, note = if (original.containerId != target.id) "" else original.note,
                corner = if (leavingField) "3/5" else original.corner,
                counters = if (leavingField) emptyMap() else original.counters),
                state.board.cards.single { it.id == original.id })
            assertEquals(before.board.cards.filterNot { it.id == original.id }, state.board.cards.filterNot { it.id == original.id })
            assertEquals(order, container(PlayZone.STACK).cards)
            return mutation
        }
    }

    @Test fun realCardEntriesLayoutsAndRemovalsKeepEveryPrefixBoundary() {
        val journey = Journey()
        journey.move(10, PlayZone.STACK, MoveInsertion.TOP, 100, HistorySignificance.STACK_ENTRY,
            order = listOf(gameplayId(10)))
        journey.move(11, PlayZone.STACK, MoveInsertion.TOP, 101, HistorySignificance.STACK_ENTRY,
            order = listOf(gameplayId(11), gameplayId(10)))
        journey.move(10, PlayZone.STACK, MoveInsertion.TOP, 102, HistorySignificance.STACK_LAYOUT,
            order = listOf(gameplayId(10), gameplayId(11)))
        journey.move(10, PlayZone.STACK, MoveInsertion.BOTTOM, 103, HistorySignificance.STACK_LAYOUT,
            start = 102, representative = 102, count = 2, order = listOf(gameplayId(11), gameplayId(10)))
        journey.move(10, PlayZone.HAND, MoveInsertion.UNORDERED, 104, HistorySignificance.STACK_REMOVAL,
            order = listOf(gameplayId(11)))
        journey.move(11, PlayZone.HAND, MoveInsertion.UNORDERED, 105, HistorySignificance.STACK_REMOVAL,
            order = emptyList())
    }

    @Test fun mixedRealCardAndEffectLayoutsShareFirstRepresentativeButRemovalsStayIsolated() {
        val journey = Journey()
        // Effects require a real permitted source, not a synthetic Stack-only category fixture.
        journey.state = assertNotNull(gameplayChange(journey.state,
            PlayAction.MoveCard(gameplayId(12), MoveSource(gameplayId(20)),
                MoveDestination(gameplayId(23), MoveInsertion.UNORDERED)), 199).state)
        val source = journey.state.board.cards.single { it.id == gameplayId(12) }
        journey.step(PlayAction.Stack(StackAction.AddEffect(source.id, GameplayRules.stackDigest(journey.state))),
            200, HistorySignificance.STACK_ENTRY, "layers")
        val effect = journey.state.board.stackEffects.single()
        journey.move(10, PlayZone.STACK, MoveInsertion.TOP, 201, HistorySignificance.STACK_ENTRY,
            order = listOf(gameplayId(10), effect.id))
        journey.move(10, PlayZone.STACK, MoveInsertion.BOTTOM, 202, HistorySignificance.STACK_LAYOUT,
            order = listOf(effect.id, gameplayId(10)))
        journey.step(PlayAction.Stack(StackAction.ReorderEffect(effect.id, IdentityGap(null, gameplayId(10)),
            IdentityGap(gameplayId(10)))), 203, HistorySignificance.STACK_LAYOUT, "open_with",
            start = 202, representative = 202, count = 2)
        assertEquals(listOf(gameplayId(10), effect.id), CombatStackRules.stack(journey.state.board).cards)
        assertEquals(effect, journey.state.board.stackEffects.single())
        journey.step(PlayAction.Stack(StackAction.RemoveEffect(effect.id)), 204, HistorySignificance.STACK_REMOVAL, "layers_clear")
        assertTrue(journey.state.board.stackEffects.isEmpty())
        assertEquals(listOf(gameplayId(10)), CombatStackRules.stack(journey.state.board).cards)
        journey.move(10, PlayZone.HAND, MoveInsertion.UNORDERED, 205, HistorySignificance.STACK_REMOVAL, order = emptyList())
        assertEquals(source, journey.state.board.cards.single { it.id == source.id })
    }

    @Test fun tapPrefixesTakeActualLayoutEntryRemovalAndInspectionRepresentativesWithoutMovingStarts() {
        val journey = Journey()
        journey.state = journey.state.withBattlefieldCards(gameplayId(14))
        journey.move(10, PlayZone.STACK, MoveInsertion.TOP, 300, HistorySignificance.STACK_ENTRY,
            order = listOf(gameplayId(10)))
        journey.move(11, PlayZone.STACK, MoveInsertion.TOP, 301, HistorySignificance.STACK_ENTRY,
            order = listOf(gameplayId(11), gameplayId(10)))
        // Orientation prefixes still group with Stack actions, but address a Battlefield card.
        journey.step(PlayAction.SetTapped(gameplayId(14), true), 302, HistorySignificance.ORIENTATION, "rotate_90_degrees_cw")
        journey.move(10, PlayZone.STACK, MoveInsertion.TOP, 303, HistorySignificance.STACK_LAYOUT,
            start = 302, representative = 303, count = 2, order = listOf(gameplayId(10), gameplayId(11)))
        journey.step(PlayAction.SetTapped(gameplayId(14), false), 304, HistorySignificance.ORIENTATION, "rotate_90_degrees_cw")
        journey.move(10, PlayZone.HAND, MoveInsertion.UNORDERED, 305, HistorySignificance.STACK_REMOVAL,
            order = listOf(gameplayId(11)))
        journey.step(PlayAction.SetTapped(gameplayId(14), true), 306, HistorySignificance.ORIENTATION, "rotate_90_degrees_cw")
        journey.move(12, PlayZone.STACK, MoveInsertion.TOP, 307, HistorySignificance.STACK_ENTRY,
            start = 306, representative = 307, count = 2, order = listOf(gameplayId(12), gameplayId(11)))
        journey.step(PlayAction.SetTapped(gameplayId(14), false), 308, HistorySignificance.ORIENTATION, "rotate_90_degrees_cw")
        val card = journey.state.board.cards.single { it.id == gameplayId(11) }
        journey.step(PlayAction.InspectFace(card.id, card.faceId, card.containerId), 309,
            HistorySignificance.EXPLICIT_DISCLOSURE, "visibility")
    }
}
