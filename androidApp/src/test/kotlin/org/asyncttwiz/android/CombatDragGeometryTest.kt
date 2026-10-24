package org.asyncttwiz.android

import org.asyncttwiz.protocol.*
import org.junit.Test
import kotlin.test.*

class CombatDragGeometryTest {
    private val f = TableStatesFixture
    private fun scene(play: PlayState, zoom: Float = 1f) = TablePresentation.scene(f.game, play, f.a,
        892f, 412f, true, TableLayoutState(camera = TableCamera(zoom = zoom)))

    private fun predict(play: PlayState, ids: List<String>, destination: CombatDestination) =
        CombatStackController.place(play, f.a, ids, destination) { action ->
            GameCommand(java.util.UUID.randomUUID().toString(), GameScope(play.gameId, playId = play.id),
                GameAction.Play(action), play.id, "1")
        }.prediction

    @Test fun midpointInsertionIsMonotonicWithSymmetricExactCenterCrossing() {
        val midpoints = listOf(20f, 60f, 100f)
        assertEquals(0, combatInsertionIndex(midpoints, 19f, 100f))
        assertEquals(0, combatInsertionIndex(midpoints, 20f, 100f))
        assertEquals(3, combatInsertionIndex(midpoints, 100f, 20f))
        assertEquals(3, combatInsertionIndex(midpoints, 101f, 20f))
        for (origin in listOf<Float?>(null, 20f, 100f)) {
            val indices = (0..120).map { combatInsertionIndex(midpoints, it.toFloat(), origin) }
            assertTrue(indices.zipWithNext().all { (a, b) -> a <= b })
        }
    }

    @Test fun droppingEitherSlotExactlyOntoTheOtherSwapsOrderAndPreviewMatchesRecenteredResult() {
        val original = f.combat()
        val play = original.copy(board = original.board.copy(combat = original.board.combat!!.copy(
            pairs = original.board.combat!!.pairs.map { it.copy(blockers = emptyList()) })))
        val ids = play.board.combat!!.pairs.map { it.attackers.single().id }
        for (zoom in listOf(.65f, 1f, 2f)) for ((from, to) in listOf(0 to 1, 1 to 0)) {
            val scene = scene(play, zoom)
            val events = mutableListOf<TableIntent>()
            val gestures = TableGestureCoordinator(events::add)
            gestures.update(scene, TableSelection(), true)
            val origin = scene.items.single { it.cardId == ids[from] }
            val target = scene.items.single { it.cardId == ids[to] }
            gestures.down(origin.bounds.center, 0)
            gestures.move(target.bounds.center, 80)
            val destination = assertIs<TableDestination.Combat>(gestures.preview.destination).destination
            assertNull(destination.pairId)
            val prediction = predict(play, listOf(ids[from]), destination)
            assertEquals(ids.reversed(), prediction.board.combat!!.pairs.flatMap { it.attackers }.map { it.id })
            val landing = tableDragLanding(scene, gestures.preview)
            val expected = scene(prediction, zoom).items.single { it.cardId == ids[from] }.bounds
            assertEquals(expected.x, landing.outlines.single().x, .001f)
            assertEquals(expected.y, landing.outlines.single().y, .001f)
            val (start, end) = assertNotNull(landing.combatInsertion)
            assertEquals(expected.x - CombatStackPresentation.slotMargin(scene.cardWidth), start.x, .001f)
            assertEquals(start.x, end.x)
            assertEquals(scene.cardHeight * .6f, end.y - start.y, .001f)
            assertEquals(expected.center.y, (start.y + end.y) / 2, .001f)
            gestures.up(target.bounds.center, 100)
            assertEquals(TableDestination.Combat(destination), events.filterIsInstance<TableIntent.Move>().single().destination)
        }
    }

    @Test fun sameSlotCardsReorderBothWaysAndLiveIndicatorFollowsResolvedInsertion() {
        val original = f.combat()
        val attackers = original.board.combat!!.pairs.flatMap { it.attackers }
        val play = original.copy(board = original.board.copy(combat = original.board.combat!!.copy(
            pairs = listOf(original.board.combat!!.pairs.first().copy(attackers = attackers)))))
        val scene = scene(play)
        for ((from, to) in listOf(0 to 1, 1 to 0)) {
            val origin = scene.items.single { it.cardId == attackers[from].id }
            val target = scene.items.single { it.cardId == attackers[to].id }
            val gestures = TableGestureCoordinator({})
            gestures.update(scene, TableSelection(), true)
            gestures.down(origin.bounds.center, 0)
            gestures.move(target.bounds.center, 80)
            val destination = assertIs<TableDestination.Combat>(gestures.preview.destination).destination
            assertNotNull(destination.pairId)
            assertEquals(CombatInsertion.GAP, destination.insertion)
            val prediction = predict(play, listOf(attackers[from].id), destination)
            assertEquals(attackers.reversed(), prediction.board.combat!!.pairs.single().attackers)
            val landing = tableDragLanding(scene, gestures.preview)
            val expected = scene(prediction).items.single { it.cardId == attackers[from].id }.bounds
            assertEquals(expected.x, landing.outlines.single().x, .001f)
            assertEquals(expected.x - CombatStackPresentation.cardGap(scene.cardWidth) / 2,
                assertNotNull(landing.combatInsertion).first.x, .001f)
            val before = landing.combatInsertion
            gestures.move(TablePoint(target.bounds.center.x + if (from == 0) -1f else 1f, target.bounds.center.y), 90)
            assertNotEquals(before, tableDragLanding(scene, gestures.preview).combatInsertion)
            gestures.cancel()
            assertNull(tableDragLanding(scene, gestures.preview).combatInsertion)
        }
    }

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
        val destination = assertNotNull(scene.combatDestination(row.bounds.center, scope))
        assertEquals(first.id, destination.pairId)
        val prediction = predict(play, scope.cardIds, destination)
        val expected = scene(prediction).items.single { it.cardId == source.cardId }.bounds
        val landing = tableDragLanding(scene, TableGesturePreview(scope = scope, destination = TableDestination.Combat(destination)))
        assertEquals(expected.x, landing.outlines.single().x, .001f)
        val attackers = scene(prediction).items.filter { it.viewport == "combat" && it.cardId != source.cardId }
        assertEquals((attackers.minOf { it.bounds.x } + attackers.maxOf { it.bounds.right }) / 2, expected.center.x, .001f)
    }
}
