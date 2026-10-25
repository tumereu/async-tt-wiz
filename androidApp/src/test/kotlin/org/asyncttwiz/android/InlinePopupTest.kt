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
        assertEquals(expected, inlinePopupBounds(412f, 892f, anchor, true, 216f, 216f))
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
