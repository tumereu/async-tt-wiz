package org.asyncttwiz.android

import org.junit.Test
import kotlin.test.*

class CardMoveAnimationTest {
    // TBL-038
    @Test fun travelDurationHasConstantSpeedFloorAndSceneCap() {
        assertEquals(1, cardMoveDurationMillis(0f))
        assertEquals(20, cardMoveDurationMillis(24f))
        assertEquals(100, cardMoveDurationMillis(120f))
        assertEquals(350, cardMoveDurationMillis(420f))
        assertEquals(350, cardMoveDurationMillis(10000f))
        assertEquals(50, cardMoveDurationMillis(120f, DEFAULT_CARD_MOVE_SPEED * 2f))
        assertEquals(350, cardMoveDurationMillis(120f, DEFAULT_CARD_MOVE_SPEED * .25f))
        assertEquals(33, cardMoveDurationMillis(120f, DEFAULT_CARD_MOVE_SPEED * 3f))
        assertEquals(90, cardMoveDurationMillis(250f, sceneDurationMillis = 90))
        assertFailsWith<IllegalArgumentException> { cardMoveDurationMillis(10f, 0f) }
    }

    // TBL-038
    @Test fun eachCardEasesOutArrivesEarlyAndWaitsForScene() {
        val start = TableBounds(0f, 0f, 20f, 30f)
        val near = start.copy(x = 24f)
        val far = start.copy(x = 900f)
        fun scene(vararg bounds: TableBounds) = TableScene(bounds.mapIndexed { index, b ->
            TableItem("card/$index", b, "Card", "", kind = TableItemKind.CARD, cardId = "$index") }, emptyList())
        val animation = HistorySceneAnimation(scene(start, start))
        animation.retarget(scene(near, far), 1f)
        assertEquals(350, animation.durationMillis)
        assertEquals(.75f, cardMoveProgress(start, near, 10f / 350, 350, DEFAULT_CARD_MOVE_SPEED), .001f)
        val frame = animation.frame(.5f)
        assertEquals(near, frame[0].bounds)
        assertFalse(frame[0].item.inTransit)
        assertEquals(675f, frame[1].bounds.x)
        assertTrue(frame[1].item.inTransit)
        assertEquals(near, animation.frame(.9f)[0].bounds)
        assertEquals(far, animation.frame(1f)[1].bounds)
    }

    // TBL-038
    @Test fun sceneUsesSpeedMultiplierAndLongerClockForLiveAndPlayback() {
        val start = TableItem("card/a", TableBounds(0f, 0f, 28f, 40f), "Card", "", kind = TableItemKind.CARD, cardId = "a")
        val target = start.copy(bounds = start.bounds.copy(x = 240f))
        for (multiplier in listOf(.25f, 1f, 3f)) {
            val animation = HistorySceneAnimation(TableScene(listOf(start), emptyList()))
            animation.cardMoveSpeedDpPerMs = DEFAULT_CARD_MOVE_SPEED * multiplier
            animation.retarget(TableScene(listOf(target), emptyList()), 1f)
            val duration = cardMoveDurationMillis(240f, animation.cardMoveSpeedDpPerMs)
            assertEquals(duration, animation.durationMillis)
            assertEquals(target.bounds, animation.frame(1f).single().bounds)
            assertTrue(animation.frame(.5f).single().item.inTransit)
            val halfway = animation.frame(.5f)
            animation.cardMoveSpeedDpPerMs = DEFAULT_CARD_MOVE_SPEED * .25f
            assertEquals(halfway, animation.frame(.5f), "Preference changes cannot desynchronize a running scene's channels")
            assertEquals(target.bounds, animation.frame(1f).single().bounds)
        }
    }
}
