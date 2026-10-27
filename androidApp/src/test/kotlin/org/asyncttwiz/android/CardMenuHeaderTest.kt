package org.asyncttwiz.android

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w412dp-h892dp-port-mdpi")
class CardMenuHeaderTest {
    @get:Rule val compose = createComposeRule()
    @Test fun headerShowsOnlyCurrentTitleWrapsAndJumpsThroughScrollableTwentyPageList() {
        val pages = listOf("Everyday", "Counters", "More") + (2..18).map { "More $it" }
        var selected by mutableIntStateOf(0)
        compose.setContent { MaterialTheme { InlinePopupPages(pages, selected, OwnTableColor) { selected = it } } }
        compose.onNodeWithText("Everyday").assertIsDisplayed()
        compose.onNodeWithText("Counters").assertDoesNotExist()
        compose.onNodeWithContentDescription("Previous page").performClick()
        compose.runOnIdle { assertEquals(19, selected) }
        compose.onNodeWithText("More 18").assertIsDisplayed()
        compose.onNodeWithContentDescription("Next page").performClick()
        compose.runOnIdle { assertEquals(0, selected) }
        compose.onNodeWithContentDescription("Choose action page").performClick()
        compose.onNodeWithContentDescription("More 15 page").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(16, selected) }
        compose.onNodeWithText("More 15").assertIsDisplayed()
        compose.onNodeWithContentDescription("Everyday page").assertDoesNotExist()
    }
}
