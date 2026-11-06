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

    // CMB-009, CMB-027: dropping exactly onto the other pair's card now joins that pair on the far side
    // (CMB-027 overrides the old pair reordering here); the attackers' order still swaps.
    @Test fun droppingEitherSlotExactlyOntoTheOtherJoinsItsPairOnTheFarSideAndPreviewMatchesRecenteredResult() {
        val original = f.combat()
        val play = original.copy(board = original.board.copy(combat = original.board.combat!!.copy(
            pairs = original.board.combat!!.pairs.map { it.copy(blockers = emptyList()) })))
        val ids = play.board.combat!!.pairs.map { it.attackers.single().id }
        for (zoom in listOf(.65f, 1f, 2f)) for ((from, to) in listOf(0 to 1, 1 to 0)) {
            val scene = scene(play, zoom)
            val events = mutableListOf<TableIntent>()
            val gestures = TableGestureCoordinator(events::add)
            gestures.update(scene, true)
            val origin = scene.items.single { it.cardId == ids[from] }
            val target = scene.items.single { it.cardId == ids[to] }
            gestures.down(origin.bounds.center, 0)
            gestures.move(target.bounds.center, 80)
            val destination = assertIs<TableDestination.Combat>(gestures.preview.destination).destination
            assertEquals(play.board.combat!!.pairs[to].id, destination.pairId)
            assertEquals(CombatInsertion.GAP, destination.insertion)
            val prediction = predict(play, listOf(ids[from]), destination)
            assertEquals(ids.reversed(), prediction.board.combat!!.pairs.flatMap { it.attackers }.map { it.id })
            assertEquals(ids.reversed(), prediction.board.combat!!.pairs.single { it.id == destination.pairId }.attackers.map { it.id })
            val landing = tableDragLanding(scene, gestures.preview)
            val expected = scene(prediction, zoom).items.single { it.cardId == ids[from] }.bounds
            assertEquals(expected.x, landing.outlines.single().x, .001f)
            assertEquals(expected.y, landing.outlines.single().y, .001f)
            val (start, end) = assertNotNull(landing.combatInsertion)
            assertEquals(expected.x - CombatStackPresentation.cardGap(scene.combatCardWidth) / 2, start.x, .001f)
            assertEquals(start.x, end.x)
            assertEquals(scene.combatCardHeight * .6f, end.y - start.y, .001f)
            assertEquals(expected.center.y, (start.y + end.y) / 2, .001f)
            gestures.up(target.bounds.center, 100)
            assertEquals(TableDestination.Combat(destination), events.filterIsInstance<TableIntent.Move>().single().destination)
        }
    }

    // CMB-009
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
            gestures.update(scene, true)
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
            assertEquals(expected.x - CombatStackPresentation.cardGap(scene.combatCardWidth) / 2,
                assertNotNull(landing.combatInsertion).first.x, .001f)
            val before = landing.combatInsertion
            gestures.move(TablePoint(target.bounds.center.x + if (from == 0) -1f else 1f, target.bounds.center.y), 90)
            assertNotEquals(before, tableDragLanding(scene, gestures.preview).combatInsertion)
            gestures.cancel()
            assertNull(tableDragLanding(scene, gestures.preview).combatInsertion)
        }
    }

    // CMB-027, CMB-009
    @Test fun battlefieldCardCoveringHalfACombatCardGoesBesideItInsteadOfOntoTheDivider() {
        val play = f.combat()
        val lantern = f.source(f.a, "2/3").id
        val scope = TableScope(lantern, listOf(lantern))
        val pairs = play.board.combat!!.pairs
        val area = play.board.combat!!.id
        for (zoom in listOf(.65f, 1f, 2f)) {
            val scene = scene(play, zoom)
            val w = scene.combatCardWidth
            val margin = CombatStackPresentation.slotMargin(w)
            val first = scene.items.single { it.cardId == f.id("attacker1") }
            val second = scene.items.single { it.cardId == f.id("attacker2") }
            val divider = scene.combatDividers.single().first.x
            val y = second.bounds.center.y
            fun at(x: Float) = assertNotNull(scene.combatDestination(TablePoint(x, y), scope), "zoom $zoom, x $x")
            // Less than half of either neighbour covered: the divider still makes a new pair there (CMB-009).
            for (x in listOf(divider, divider - margin * .5f, divider + margin * .5f)) assertNull(at(x).pairId, "zoom $zoom, x $x")
            // Half a card or more: beside that card, on the side of the dragged card's centre, winning over the divider.
            assertEquals(CombatDestination(area, CombatRole.ATTACKER, pairs[1].id, insertion = CombatInsertion.GAP,
                cardGap = IdentityGap(null, f.id("attacker2"))), at(second.bounds.x + .01f), "zoom $zoom")
            assertEquals(IdentityGap(f.id("attacker1"), null), at(first.bounds.right - .01f).cardGap, "zoom $zoom")
            assertEquals(pairs[0].id, at(first.bounds.right - .01f).pairId, "zoom $zoom")
            assertEquals(IdentityGap(f.id("attacker2"), null), at(second.bounds.center.x + w * .45f).cardGap, "zoom $zoom")
            assertEquals(IdentityGap(null, f.id("attacker1")), at(first.bounds.center.x - w * .45f).cardGap, "zoom $zoom")
            val joined = predict(play, scope.cardIds, at(second.bounds.x + .01f)).board.combat!!.pairs.single { it.id == pairs[1].id }
            assertEquals(listOf(lantern, second.sourceId), joined.attackers.map { it.sourceId }, "zoom $zoom")
            // The blocker row works the same way.
            val blocker = scene.items.single { it.cardId == f.id("blocker") }
            assertEquals(CombatDestination(area, CombatRole.BLOCKER, pairs[0].id, insertion = CombatInsertion.GAP,
                cardGap = IdentityGap(f.id("blocker"), null)),
                scene.combatDestination(TablePoint(blocker.bounds.center.x + w * .3f, blocker.bounds.center.y), scope), "zoom $zoom")
        }
    }

    // CMB-027
    @Test fun combatCopyCoveringHalfACardOfAnotherPairJoinsThatPairAndWithinItsOwnPairChangesPlaces() {
        val play = f.combat()
        val pairs = play.board.combat!!.pairs
        val scene = scene(play)
        val second = scene.items.single { it.cardId == f.id("attacker2") }
        val scope = TableScope(f.id("attacker1"), listOf(f.id("attacker1")))
        val point = TablePoint(second.bounds.center.x - scene.combatCardWidth * .3f, second.bounds.center.y)
        val destination = assertNotNull(scene.combatDestination(point, scope))
        assertEquals(CombatDestination(play.board.combat!!.id, CombatRole.ATTACKER, pairs[1].id, insertion = CombatInsertion.GAP,
            cardGap = IdentityGap(null, f.id("attacker2"))), destination)
        val result = predict(play, scope.cardIds, destination).board.combat!!
        assertEquals(listOf(f.id("attacker1"), f.id("attacker2")), result.pairs.single { it.id == pairs[1].id }.attackers.map { it.id })
        assertEquals(pairs[0].blockers, result.pairs.single { it.id == pairs[0].id }.blockers)
        // Over the other card of its own pair it changes places within the pair.
        val together = play.copy(board = play.board.copy(combat = play.board.combat!!.copy(pairs = listOf(
            pairs[0].copy(attackers = pairs[0].attackers + pairs[1].attackers)))))
        val own = scene(together)
        val left = own.items.single { it.cardId == f.id("attacker1") }
        val right = own.items.single { it.cardId == f.id("attacker2") }
        val swap = assertNotNull(own.combatDestination(TablePoint(right.bounds.center.x + own.combatCardWidth * .2f, right.bounds.center.y), scope))
        assertEquals(IdentityGap(f.id("attacker2"), null), swap.cardGap)
        assertEquals(listOf(f.id("attacker2"), f.id("attacker1")),
            predict(together, scope.cardIds, swap).board.combat!!.pairs.single().attackers.map { it.id })
        val back = TableScope(f.id("attacker2"), listOf(f.id("attacker2")))
        assertEquals(IdentityGap(null, f.id("attacker1")), own.combatDestination(
            TablePoint(left.bounds.center.x - own.combatCardWidth * .2f, left.bounds.center.y), back)?.cardGap)
    }

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
