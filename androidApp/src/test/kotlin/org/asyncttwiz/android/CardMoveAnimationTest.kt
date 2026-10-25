package org.asyncttwiz.android

import org.junit.Test
import kotlin.test.*

class CardMoveAnimationTest {
    @Test fun travelDurationHasConstantSpeedFloorAndSceneCap() {
        assertEquals(1, cardMoveDurationMillis(0f))
        assertEquals(10, cardMoveDurationMillis(25f))
        assertEquals(100, cardMoveDurationMillis(250f))
        assertEquals(180, cardMoveDurationMillis(450f))
        assertEquals(180, cardMoveDurationMillis(10000f))
        assertEquals(50, cardMoveDurationMillis(250f, 5f))
        assertEquals(90, cardMoveDurationMillis(250f, sceneDurationMillis = 90))
        assertFailsWith<IllegalArgumentException> { cardMoveDurationMillis(10f, 0f) }
    }

    @Test fun eachCardEasesOutArrivesEarlyAndWaitsForScene() {
        val start = TableBounds(0f, 0f, 20f, 30f)
        val near = start.copy(x = 25f)
        val far = start.copy(x = 900f)
        fun scene(vararg bounds: TableBounds) = TableScene(bounds.mapIndexed { index, b ->
            TableItem("card/$index", b, "Card", "", kind = TableItemKind.CARD, cardId = "$index") }, emptyList())
        val animation = HistorySceneAnimation(scene(start, start))
        animation.retarget(scene(near, far), 1f)
        assertEquals(180, animation.durationMillis)
        assertEquals(.75f, cardMoveProgress(start, near, 5f / 180, 180, 2.5f), .001f)
        val frame = animation.frame(.5f)
        assertEquals(near, frame[0].bounds)
        assertFalse(frame[0].item.inTransit)
        assertEquals(675f, frame[1].bounds.x)
        assertTrue(frame[1].item.inTransit)
        assertEquals(near, animation.frame(.9f)[0].bounds)
        assertEquals(far, animation.frame(1f)[1].bounds)
    }
}
