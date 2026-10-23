package org.asyncttwiz.android

import org.junit.Test
import kotlin.test.*

class TableSelectionTest {
    @Test fun actionsCaptureSelectionOrSingleWithoutMutatingSelection() {
        val selection = TableSelection(linkedSetOf("b", "a"), TableMode.SELECT)
        val captured = selection.scope("a", listOf("a", "b", "c"))
        assertEquals(listOf("a", "b"), captured.cardIds)
        assertEquals(listOf("c"), selection.scope("c").cardIds)
        val later = selection.toggle("a").toggle("c").copy(mode = TableMode.NORMAL)
        assertEquals(listOf("a", "b"), captured.cardIds)
        assertEquals(setOf("b", "c"), later.ids)
        assertEquals(setOf("b", "a"), selection.ids)
    }
    @Test fun orientationAndSuccessfulDragClearOnlyForUnselectedContextAndRetainMode() {
        val s = TableSelection(setOf("a", "b"), TableMode.SELECT)
        assertEquals(s, s.afterMove("a")); assertEquals(s, s.orientation("a"))
        assertEquals(TableSelection(emptySet(), TableMode.SELECT), s.afterMove("c"))
        assertEquals(emptySet(), s.orientation("c").ids)
        assertEquals(setOf("a"), s.retain(setOf("a", "c")).ids)
    }
    @Test fun visibleRectangleSubtractDoesNotSelectHiddenBoundingBox() {
        val square = TableBounds(0f, 0f, 100f, 100f)
        val pieces = square.subtract(TableBounds(5f, 5f, 100f, 100f))
        assertEquals(975f, pieces.sumOf { (it.width * it.height).toDouble() }.toFloat())
        assertTrue(pieces.none { it.contains(TablePoint(50f, 50f)) })
        assertEquals(emptyList(), square.subtract(square))
        assertEquals(listOf(square), square.subtract(TableBounds(101f, 0f, 1f, 1f)))
    }
}
