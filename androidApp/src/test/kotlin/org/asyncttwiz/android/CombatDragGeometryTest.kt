package org.asyncttwiz.android

import org.asyncttwiz.protocol.*
import org.junit.Test
import kotlin.test.*

class CombatDragGeometryTest {
    private val f = TableStatesFixture
    private fun scene(play: PlayState, zoom: Float = 1f) = TablePresentation.scene(f.game, tableBoard(play), f.a,
        892f, 412f, true, TableLayoutState(camera = TableCamera(zoom = zoom)))

    private fun predict(play: PlayState, ids: List<String>, destination: CombatDestination) =
        CombatBoards(f.a).place(play, ids, destination)

    // CMB-008
    @Test fun insertingIntoEmptyOpposingRowCentersTheCardInTheWiderSlot() {
        val original = f.combat()
        val first = original.board.combat!!.pairs.first()
        val second = original.board.combat!!.pairs.last()
        val play = original.copy(board = original.board.copy(combat = original.board.combat!!.copy(
            pairs = listOf(first.copy(attackers = first.attackers + second.attackers, blockers = emptyList()),
                second.copy(attackers = emptyList(), blockers = first.blockers)))))
        val scene = scene(play)
        val source = scene.items.single { it.cardId == first.blockers.single().id }
        val row = scene.combatDrops.single { it.destination.pairId == first.id &&
            it.destination.role == CombatRole.BLOCKER && it.destination.insertion == CombatInsertion.APPEND }
        val scope = TableScope(source.cardId!!, listOf(source.cardId))
        val destination = assertNotNull(scene.combatDestination(row.bounds.center))
        assertEquals(first.id, destination.pairId)
        val prediction = predict(play, scope.cardIds, destination)
        val expected = scene(prediction).items.single { it.cardId == source.cardId }.bounds
        val landing = tableDragLanding(scene, TableGesturePreview(scope = scope, destination = TableDestination.Combat(destination)))
        assertEquals(expected.x, landing.outlines.single().x, .001f)
        val attackers = scene(prediction).items.filter { it.viewport == "combat" && it.cardId != source.cardId }
        assertEquals((attackers.minOf { it.bounds.x } + attackers.maxOf { it.bounds.right }) / 2, expected.center.x, .001f)
    }
}
