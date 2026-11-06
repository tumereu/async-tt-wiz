package org.asyncttwiz.android

import org.junit.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SystemBarsTest {
    // SYS-079: button navigation shows the navigation bar everywhere but the game table; gestures keep it hidden
    @Test fun navigationBarShowsWithButtonNavigationOutsideTheTableOnly() {
        assertTrue(SystemBars.navigationShown(table = false, buttonNavigation = true))
        assertFalse(SystemBars.navigationShown(table = true, buttonNavigation = true))
        assertFalse(SystemBars.navigationShown(table = false, buttonNavigation = false))
        assertFalse(SystemBars.navigationShown(table = true, buttonNavigation = false))
    }

    // SYS-079: three and two buttons are button navigation; a phone that doesn't say (before Android 10) has buttons
    @Test fun navigationModeSettingTellsButtonsFromGestures() {
        assertTrue(SystemBars.buttonNavigation(0))
        assertTrue(SystemBars.buttonNavigation(1))
        assertFalse(SystemBars.buttonNavigation(2))
        assertTrue(SystemBars.buttonNavigation(null as Int?))
    }
}
