package org.asyncttwiz.android

import org.asyncttwiz.protocol.*
import org.junit.Test
import kotlin.test.*

/** The defending viewer sees the combat area mirrored vertically; the attacker's view stays as it was. */
class CombatMirrorTest {
    private val attacker = combatId(7)
    private val defender = combatId(8)

    private fun flipped(b: TableBounds, height: Float) = b.copy(y = height - b.y - b.height)

    // CMB-007
    @Test fun defenderGeometryIsTheAttackersMirroredVertically() {
        val state = attackTargetState()
        val names = mapOf(defender to "Bob")
        val own = CombatStackPresentation.combat(state.board, 48f, 68f, state.definitions, names, attacker)
        val mirror = CombatStackPresentation.combat(state.board, 48f, 68f, state.definitions, names, defender)
        assertFalse(own.mirrored)
        assertTrue(mirror.mirrored)
        assertEquals(own.width, mirror.width)
        assertEquals(own.height, mirror.height)
        assertEquals(own.dividers, mirror.dividers)
        assertEquals(own.laneDividers, mirror.laneDividers)
        // Attacker view: labels on top, blockers, then attackers at the bottom.
        assertEquals(0f, own.labelY)
        assertTrue(own.lanes.all { it.bounds.y == 0f })
        val ownAttackers = own.cards.filter { role(state, it) == CombatRole.ATTACKER }
        val ownBlockers = own.cards.filter { role(state, it) == CombatRole.BLOCKER }
        assertTrue(ownBlockers.all { b -> ownAttackers.all { a -> b.bounds.bottom <= a.bounds.y } })
        // Defender view: attackers on top, blockers below them, labels at the very bottom.
        assertEquals(mirror.height - mirror.labelHeight, mirror.labelY)
        assertTrue(mirror.lanes.all { it.bounds.y == mirror.labelY && it.bounds.bottom == mirror.height })
        val attackers = mirror.cards.filter { role(state, it) == CombatRole.ATTACKER }
        val blockers = mirror.cards.filter { role(state, it) == CombatRole.BLOCKER }
        assertTrue(attackers.all { a -> blockers.all { b -> a.bounds.bottom <= b.bounds.y } })
        assertTrue(mirror.cards.all { it.bounds.bottom <= mirror.labelY })
        assertEquals(0f, mirror.rowsTop)
        assertEquals(mirror.labelY, mirror.rowsBottom)
        // Everything is exactly flipped: cards, lane labels, drop regions and landing points.
        near(own.cards.map { flipped(it.bounds, own.height) }, mirror.cards.map { it.bounds })
        near(own.lanes.map { flipped(it.bounds, own.height) }, mirror.lanes.map { it.bounds })
        assertEquals(own.destinations.map { it.destination }, mirror.destinations.map { it.destination })
        near(own.destinations.map { flipped(it.bounds, own.height) }, mirror.destinations.map { it.bounds })
        near(own.destinations.map { TableBounds(it.landing.x, own.height - it.landing.y, 0f, 0f) },
            mirror.destinations.map { TableBounds(it.landing.x, it.landing.y, 0f, 0f) })
    }

    // CMB-007
    @Test fun defenderTableDropsDividersAndLabelsFollowTheMirror() {
        val f = TableStatesFixture
        val play = f.combat()
        for ((viewer, mirrored) in listOf(f.a to false, f.b to true)) {
            val scene = TablePresentation.scene(f.game, play, viewer, 892f, 412f, true)
            val attackers = scene.items.filter { it.viewport == "combat" &&
                CombatStackRules.location(play.board.combat!!, it.cardId!!)?.role == CombatRole.ATTACKER }
            val blockers = scene.items.filter { it.viewport == "combat" &&
                CombatStackRules.location(play.board.combat!!, it.cardId!!)?.role == CombatRole.BLOCKER }
            assertTrue(attackers.isNotEmpty() && blockers.isNotEmpty())
            val cards = attackers + blockers
            val labelTop = scene.combatLabels.minOf { it.bounds.y }
            val labelBottom = scene.combatLabels.maxOf { it.bounds.bottom }
            if (mirrored) {
                assertTrue(attackers.all { a -> blockers.all { b -> a.bounds.center.y < b.bounds.center.y } })
                assertTrue(cards.all { it.bounds.bottom <= labelTop + .01f })
                assertTrue(scene.combatDividers.all { (from, to) -> to.y <= labelTop + .01f && from.y < to.y })
            } else {
                assertTrue(blockers.all { b -> attackers.all { a -> b.bounds.center.y < a.bounds.center.y } })
                assertTrue(cards.all { it.bounds.y >= labelBottom - .01f })
                assertTrue(scene.combatDividers.all { (from, _) -> from.y >= labelBottom - .01f })
            }
            // Lane separators (if any) cross the whole area: both card rows and the label row, wherever it is.
            assertTrue(scene.combatLaneDividers.all { (from, to) -> from.y <= minOf(labelTop, cards.minOf { it.bounds.y }) + .01f &&
                to.y >= maxOf(labelBottom, cards.maxOf { it.bounds.bottom }) - .01f })
            // Each role's drop row contains that role's cards and never the label row.
            for (drop in scene.combatDrops.filter { it.destination.pairId != null && it.destination.insertion == CombatInsertion.APPEND }) {
                val own = if (drop.destination.role == CombatRole.ATTACKER) attackers else blockers
                val pairCards = own.filter { CombatStackRules.location(play.board.combat!!, it.cardId!!)?.pairId == drop.destination.pairId }
                assertTrue(pairCards.all { drop.bounds.contains(it.bounds.center) })
                assertTrue(if (mirrored) drop.bounds.bottom <= labelTop + .01f else drop.bounds.y >= labelBottom - .01f)
                assertEquals(drop.destination.role, scene.combatDestination(drop.landing, null)?.role)
            }
        }
    }

    @Test fun defenderDragLandingPreviewLandsInTheMirroredRow() {
        val f = TableStatesFixture
        val original = f.combat()
        val play = original.copy(board = original.board.copy(turn = original.board.turn.copy(accountId = f.b),
            combat = original.board.combat!!.copy(pairs = original.board.combat!!.pairs.map { it.copy(blockers = emptyList()) })))
        // Alice now defends: dragging Bob's attacker onto the other attacker's slot lands in the top row.
        val scene = TablePresentation.scene(f.game, play, f.a, 892f, 412f, true)
        val ids = play.board.combat!!.pairs.map { it.attackers.single().id }
        val events = mutableListOf<TableIntent>()
        val gestures = TableGestureCoordinator(events::add)
        gestures.update(scene, TableSelection(), true)
        val origin = scene.items.single { it.cardId == ids[0] }
        val target = scene.items.single { it.cardId == ids[1] }
        assertTrue(origin.bounds.bottom <= scene.combatLabels.minOf { it.bounds.y } + .01f)
        gestures.down(origin.bounds.center, 0)
        gestures.move(target.bounds.center, 80)
        val destination = assertIs<TableDestination.Combat>(gestures.preview.destination).destination
        assertEquals(CombatRole.ATTACKER, destination.role)
        val landing = tableDragLanding(scene, gestures.preview)
        assertEquals(target.bounds.center.y, landing.outlines.single().center.y, .01f)
    }

    private fun near(expected: List<TableBounds>, actual: List<TableBounds>) {
        assertEquals(expected.size, actual.size)
        for ((e, a) in expected.zip(actual)) {
            assertEquals(e.x, a.x, .001f); assertEquals(e.y, a.y, .001f)
            assertEquals(e.width, a.width, .001f); assertEquals(e.height, a.height, .001f)
        }
    }

    private fun role(state: PlayState, drawing: OccurrenceDrawing) =
        CombatStackRules.location(state.board.combat!!, drawing.viewId)!!.role
}
