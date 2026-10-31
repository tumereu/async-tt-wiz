package org.asyncttwiz.android

import org.junit.Test
import kotlin.math.sqrt
import kotlin.test.*

class TableFeelTest {
    @Test fun horizontalCounterDragWinsOverScrollableCounterRailAndCommitsOnce() {
        val counter = TableItem("counter/a/BLUE", TableBounds(0f, 0f, 48f, 48f), "Blue counter", "20",
            owner = "a", kind = TableItemKind.COUNTER, viewport = "counters", counter = org.asyncttwiz.protocol.CounterColor.BLUE)
        val scene = TableScene(listOf(counter), emptyList(),
            viewports = listOf(TableViewport("counters", TableBounds(0f, 0f, 200f, 48f), 400f, 0f)))
        val events = mutableListOf<TableIntent>()
        val gestures = TableGestureCoordinator(events::add)
        gestures.update(scene, TableSelection(), true)
        gestures.down(TablePoint(24f, 24f), 0)
        gestures.move(TablePoint(124f, 23f), 10)
        assertEquals(2, gestures.preview.delta)
        assertTrue(events.isEmpty())
        gestures.up(TablePoint(124f, 23f), 20)
        assertEquals(listOf<TableIntent>(TableIntent.PlayerDelta("a", org.asyncttwiz.protocol.CounterColor.BLUE, 2)), events)
    }

    // TBL-080, TRN-032
    @Test fun dominantAxisCompleteUnitStepsAndSigns() {
        // Every value drag (life, player and card counters, P/T, menu widgets) shares one 33.6dp unit: 70% of 48dp.
        assertEquals(48f * .7f, VALUE_DRAG_STEP_DP, .0001f)
        val u = VALUE_DRAG_STEP_DP
        val e = .01f
        for ((x, y, expected) in listOf(Triple(3 * u + e, 20f, 3L), Triple(-3 * u - e, -20f, -3L),
            Triple(20f, -3 * u - e, -3L), Triple(-20f, 3 * u + e, 3L), Triple(u, 0f, 1L),
            Triple(0f, u, 1L), Triple(u - e, 0f, 0L), Triple(-u + e, 0f, 0L),
            Triple(u, u, 1L), Triple(70f, -69f, 2L), Triple(69f, 70f, 2L))) {
            assertEquals(expected, valueDragDelta(x, y), "displacement $x, $y")
        }
        assertEquals(2L, valueDragDelta(140f, 20f, 2 * u)) // Same 70dp at density 2.
        assertEquals(0L, valueDragDelta(Float.NaN, 20f))
        assertEquals("+3 → 23", valueDragLabel("20", 3))
        assertEquals("−3 → 17", valueDragLabel("20", -3))
        assertEquals("+3 → 3", valueDragLabel(null, 3))
        assertEquals(-3L, valueDragDelta(10f, 3 * u + e, side = ValueDragSide.TOP))
        assertEquals(3L, valueDragDelta(-10f, -3 * u - e, side = ValueDragSide.TOP))
        assertEquals(3L, valueDragDelta(3 * u + e, -10f, side = ValueDragSide.TOP))
        assertEquals(-3L, valueDragDelta(-3 * u - e, 10f, side = ValueDragSide.TOP))
        // Player value drags use the same shared unit by default.
        val drag = PlayerDeltaDrag(PlayerValueTarget(TableActionScope(TableToolFixture.id(1), TableToolFixture.id(2)),
            TableToolFixture.id(7), org.asyncttwiz.protocol.CounterColor.BLUE))
        drag.move(3 * u + e, 0f)
        assertEquals(3L, drag.delta)
    }

    // TBL-080, TRN-032
    @Test fun dragCurveIsMonotonicSymmetricDensityIndependentAndAcceleratesAfterThreeUnits() {
        val u = VALUE_DRAG_STEP_DP
        for (unit in 1..3) {
            assertEquals((unit - 1).toLong(), valueDragDelta(unit * u - .01f, 0f))
            assertEquals(unit.toLong(), valueDragDelta(unit * u + .001f, 0f))
        }
        var previous = 0L
        for (dp in 0..1000) {
            val delta = valueDragDelta(dp.toFloat(), 0f)
            assertTrue(delta >= previous)
            assertEquals(-delta, valueDragDelta(-dp.toFloat(), 0f))
            assertEquals(delta, valueDragDelta(dp * 2f, 0f, u * 2f))
            previous = delta
        }
        // Same curve shape as the former 48dp unit, every distance 30% shorter (412dp → 288.4dp for ~30).
        for ((formerDp, expected) in listOf(48f to 1L, 144f to 3L, 200f to 5L, 250f to 8L,
            300f to 13L, 350f to 19L, 412f to 30L)) {
            assertEquals(expected, valueDragDelta(formerDp * .7f + .001f, 0f), "${formerDp * .7f} dp")
            assertEquals(expected, valueDragDelta(formerDp + .001f, 0f, 48f), "$formerDp dp at the old unit")
        }
        assertTrue(valueDragDelta(175f, 0f) - valueDragDelta(140f, 0f) > valueDragDelta(70f, 0f) - valueDragDelta(35f, 0f))
    }

    // TRN-032
    @Test fun lifeAndPlayerCountersSubtractTowardOtherPlayerInBothOrientations() {
        val f = TableStatesFixture
        for (landscape in listOf(false, true)) {
            val scene = TablePresentation.scene(f.game, f.combat(), f.a,
                if (landscape) 892f else 412f, if (landscape) 412f else 892f, landscape)
            for (item in scene.items.filter { it.kind in listOf(TableItemKind.LIFE, TableItemKind.COUNTER) }) {
                val d = 3 * VALUE_DRAG_STEP_DP + .1f
                for ((dx, dy, expected) in listOf(Triple(d, 1f, 3), Triple(-d, 1f, -3),
                    Triple(1f, if (item.owner == f.a) -d else d, -3),
                    Triple(1f, if (item.owner == f.a) d else -d, 3))) {
                    val sent = mutableListOf<TableIntent>()
                    val gestures = TableGestureCoordinator(sent::add)
                    gestures.update(scene, TableSelection(), true)
                    val start = item.visible.first().center
                    val end = TablePoint(start.x + dx, start.y + dy)
                    gestures.down(start, 0); gestures.move(end, 20)
                    assertEquals(expected, gestures.preview.delta)
                    assertTrue(sent.isEmpty())
                    gestures.up(end, 40)
                    assertEquals(listOf<TableIntent>(TableIntent.PlayerDelta(item.owner!!, item.counter, expected)), sent)
                }
            }
        }
    }

    @Test fun cardCounterDirectionUsesControllerNotOwner() {
        val board = TableStatesFixture.combat().board
        val card = board.cards.first()
        for (viewer in board.players.map { it.accountId }) {
            val other = board.players.first { it.accountId != viewer }.accountId
            val ownField = board.containers.single { it.accountId == viewer && it.zone == org.asyncttwiz.protocol.PlayZone.BATTLEFIELD }
            val otherField = board.containers.single { it.accountId == other && it.zone == org.asyncttwiz.protocol.PlayZone.BATTLEFIELD }
            assertEquals(ValueDragSide.BOTTOM, cardValueDragSide(board, card.copy(owner = other, containerId = ownField.id), viewer))
            assertEquals(ValueDragSide.TOP, cardValueDragSide(board, card.copy(owner = viewer, containerId = otherField.id), viewer))
        }
    }

    // SYS-050, TBL-050
    @Test fun backDismissesLayersBeforeReviewSelectionAndDoubleBackExit() {
        val back = TableBackStateMachine()
        assertEquals(TableBackAction.CLOSE_OVERLAY, back.back(0, true, true, true))
        assertEquals(TableBackAction.EXIT_REVIEW, back.back(1, false, true, true))
        assertEquals(TableBackAction.CLEAR_SELECTION, back.back(2, false, false, true))
        assertEquals(TableBackAction.PROMPT, back.back(3, false, false, false))
        assertEquals(TableBackAction.LEAVE, back.back(2003, false, false, false))
        assertEquals(TableBackAction.PROMPT, back.back(3000, false, false, false))
        assertEquals(TableBackAction.PROMPT, back.back(5001, false, false, false))
        assertEquals(TableBackAction.CLOSE_OVERLAY, back.back(5002, true, false, false))
        assertEquals(TableBackAction.PROMPT, back.back(5003, false, false, false))
        back.reset()
        assertEquals(TableBackAction.PROMPT, back.back(5004, false, false, false))
    }

    // TBL-037
    @Test fun tapAndUndoRotateRigidFaceThroughMidpointsAndRetargetWithoutMorphing() {
        val card = TableItem("card/a", TableBounds(100f, 100f, 28f, 40f), "Card", "Card",
            kind = TableItemKind.CARD, cardId = "a")
        val normal = TableScene(listOf(card), emptyList())
        val tapped = normal.copy(items = listOf(card.copy(tapped = true, bounds = TableBounds(94f, 106f, 40f, 28f))))
        val animation = HistorySceneAnimation(normal)
        repeat(2) { // Forward play/redo and history backwards/undo use the same renderer.
            assertTrue(animation.retarget(tapped, 1f))
            for ((progress, rotation) in listOf(0f to 0f, .25f to 22.5f, .5f to 45f, .75f to 67.5f, 1f to 90f)) {
                val frame = animation.frame(progress).single()
                assertEquals(TableCardTransform(28f, 40f, rotation), frame.item.cardTransform)
                assertEquals(card.bounds.center, frame.bounds.center)
                if (progress == .5f) assertEquals(68f / sqrt(2f), frame.bounds.width, .001f)
            }
            animation.retarget(normal, 1f)
            assertEquals(TableCardTransform(28f, 40f, 45f), animation.frame(.5f).single().item.cardTransform)
        }
        animation.retarget(tapped, 1f)
        val interrupted = animation.frame(.5f).single()
        animation.retarget(normal, .5f)
        assertEquals(interrupted.bounds, animation.frame(0f).single().bounds)
        assertEquals(TableCardTransform(28f, 40f, 22.5f), animation.frame(.5f).single().item.cardTransform)
    }

    @Test fun rotationIsATargetEvenIfTheLayoutReservesAnUnchangedFootprint() {
        val card = TableItem("a", TableBounds(0f, 0f, 28f, 40f), "Card", "", kind = TableItemKind.CARD, cardId = "a")
        val scene = TableScene(listOf(card), emptyList())
        assertNotEquals(scene.animationTargets, scene.copy(items = listOf(card.copy(tapped = true))).animationTargets)
    }
}
