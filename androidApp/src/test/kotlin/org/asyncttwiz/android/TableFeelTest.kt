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
        gestures.move(TablePoint(54f, 23f), 10)
        assertEquals(3, gestures.preview.delta)
        assertTrue(events.isEmpty())
        gestures.up(TablePoint(54f, 23f), 20)
        assertEquals(listOf<TableIntent>(TableIntent.PlayerDelta("a", org.asyncttwiz.protocol.CounterColor.BLUE, 3)), events)
    }

    @Test fun dominantAxisCompleteTenDpStepsAndSigns() {
        for ((x, y, expected) in listOf(Triple(39f, 20f, 3L), Triple(-39f, -20f, -3L),
            Triple(20f, -39f, 3L), Triple(-20f, 39f, -3L), Triple(10f, 0f, 1L),
            Triple(0f, 10f, -1L), Triple(9.99f, 0f, 0L), Triple(-9.99f, 0f, 0L),
            Triple(10f, 10f, -1L), Triple(100f, -99f, 10L), Triple(99f, 100f, -10L))) {
            assertEquals(expected, valueDragDelta(x, y), "displacement $x, $y")
        }
        assertEquals(3L, valueDragDelta(60f, 20f, 20f)) // Same 30dp at density 2.
        assertEquals(0L, valueDragDelta(Float.NaN, 20f))
        assertEquals("+3 -> 23", valueDragLabel("20", 3))
        assertEquals("-3 -> 17", valueDragLabel("20", -3))
        assertEquals("+3 -> 3", valueDragLabel(null, 3))
    }

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
