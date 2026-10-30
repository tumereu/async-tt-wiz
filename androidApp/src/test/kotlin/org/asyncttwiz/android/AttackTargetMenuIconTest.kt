package org.asyncttwiz.android

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.GpsFixed
import org.junit.Test
import org.junit.Assert.assertEquals

class AttackTargetMenuIconTest {
    /** The menu action and the "Choose attack target" history event share one crosshair. */
    @Test fun attackTargetActionUsesTheCrosshair() {
        assertEquals(Icons.Outlined.GpsFixed, menuIcon("Attack →"))
    }
}
