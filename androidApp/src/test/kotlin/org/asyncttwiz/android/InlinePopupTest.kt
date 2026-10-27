package org.asyncttwiz.android

import org.junit.Test
import kotlin.test.*

class InlinePopupTest {
    @Test fun popupSizesClampAtAllEdgesAndOrientations() {
        for ((w, h) in listOf(360f to 640f, 640f to 360f, 412f to 892f, 892f to 412f))
            for ((pw, ph) in listOf(216f to 216f, 264f to 304f, 360f to 560f))
                for (x in listOf(0f, w / 2, w - 28)) for (y in listOf(0f, h / 2, h - 40)) {
                    val popup = inlinePopupBounds(w, h, TableBounds(x, y, 28f, 40f), true, pw, ph)
                    assertTrue(popup.x >= 12 && popup.y >= 12)
                    assertTrue(popup.right <= w - 12 && popup.bottom <= h - 12)
                }
    }

    @Test fun sharedHostPreservesExistingCardGridPlacement() {
        val anchor = TableBounds(100f, 500f, 28f, 40f)
        val expected = assertNotNull(CardRingGeometry.place(412f, 892f, anchor, true)).square
        assertEquals(expected, inlinePopupBounds(412f, 892f, anchor, true, CardRingGeometry.squareSide, CardRingGeometry.squareSide))
    }

    @Test fun measuredContentKeepsOnlyOccupiedRowsAndCanFitBesideTheAnchor() {
        assertEquals(0, inlinePopupGridRows(emptyList()))
        assertEquals(1, inlinePopupGridRows(CardRingPages.layout(listOf("inspect"))))
        assertEquals(1, inlinePopupGridRows(CardRingPages.layout(listOf("inspect", "corner", "note", "Clone"))))
        assertEquals(4, inlinePopupGridRows(CardRingPages.layout(List(16) { "action$it" })))
        val anchor = TableBounds(320f, 130f, 48f, 100f)
        val bounds = inlinePopupBounds(640f, 360f, anchor, true, 198f, 72f)
        assertEquals(198f, bounds.width)
        assertEquals(72f, bounds.height)
        assertNull(bounds.intersection(anchor))
    }

    @Test fun quantitySelectionAndStepsAreBoundedWithoutOverflow() {
        val value = selectedLibraryQuantity("123")
        assertEquals(androidx.compose.ui.text.TextRange(0, 3), value.selection)
        assertEquals(0, stepLibraryQuantity("0", -1))
        assertEquals(2, stepLibraryQuantity("1", 1))
        assertEquals(0, stepLibraryQuantity("invalid", -1))
        assertEquals(Int.MAX_VALUE, stepLibraryQuantity(Int.MAX_VALUE.toString(), 1))
    }

    @Test fun eitherLibraryOpensTheSameActionsWithoutSubmittingSharedView() {
        val play = LiveTableFixture.play()
        val sent = mutableListOf<org.asyncttwiz.protocol.PlayAction>()
        val toolkit = TableToolkitController("a", { play }, { true }, { action, _ -> sent += action; true })
        toolkit.openLibrary("b/LIBRARY")
        assertEquals("library", toolkit.panel); assertNotNull(toolkit.library)
        assertEquals("b/LIBRARY", toolkit.library!!.controller.libraryId)
        assertEquals("b/HAND", toolkit.library!!.controller.handId)
        toolkit.openLibrary("a/LIBRARY")
        assertEquals("library", toolkit.panel); assertNotNull(toolkit.library)
        assertTrue(sent.isEmpty())
    }
}
