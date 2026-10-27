package org.asyncttwiz.android

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class HandMenuIconsTest {
    @Test fun discardUsesGraveyardAndPlayHasADistinctPlacementIcon() {
        assertEquals(menuIcon("Graveyard"), menuIcon("Discard"))
        assertEquals(menuIcon("Graveyard"), menuIcon("Discard random"))
        assertEquals(PlayCardIcon, menuIcon("Play"))
        assertNotEquals(menuIcon("Discard"), menuIcon("Play"))
        assertNotEquals(menuIcon("Unknown move"), menuIcon("Play"))
    }
}
