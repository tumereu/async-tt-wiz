package org.asyncttwiz.android

import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performTouchInput

/** Taps the open card menu's [description] cell past the menu's opening guard (TBL-069). */
internal fun ComposeContentTestRule.ringClick(description: String) {
    mainClock.advanceTimeBy(350)
    onNodeWithContentDescription(description).performTouchInput { advanceEventTime(350); click() }
}
