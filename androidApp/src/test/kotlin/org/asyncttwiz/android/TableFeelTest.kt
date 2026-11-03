package org.asyncttwiz.android

import org.junit.Test
import kotlin.math.sqrt
import kotlin.test.*

class TableFeelTest {





    // SYS-050, TBL-050
    @Test fun backDismissesLayersBeforeReviewAndDoubleBackExit() {
        val back = TableBackStateMachine()
        assertEquals(TableBackAction.CLOSE_OVERLAY, back.back(0, true, true))
        assertEquals(TableBackAction.EXIT_REVIEW, back.back(1, false, true))
        assertEquals(TableBackAction.PROMPT, back.back(3, false, false))
        assertEquals(TableBackAction.LEAVE, back.back(2003, false, false))
        assertEquals(TableBackAction.PROMPT, back.back(3000, false, false))
        assertEquals(TableBackAction.PROMPT, back.back(5001, false, false))
        assertEquals(TableBackAction.CLOSE_OVERLAY, back.back(5002, true, false))
        assertEquals(TableBackAction.PROMPT, back.back(5003, false, false))
        back.reset()
        assertEquals(TableBackAction.PROMPT, back.back(5004, false, false))
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
