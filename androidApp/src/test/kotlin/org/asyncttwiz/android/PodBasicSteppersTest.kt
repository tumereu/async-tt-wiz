package org.asyncttwiz.android

import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import org.asyncttwiz.protocol.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PodBasicSteppersTest {
    @get:Rule val compose = createComposeRule()
    @Test fun fiveBasicSteppersHaveAccessibleCountsAndDisableNegativeDisconnectedAndFullDeckEdits() {
        var basics by mutableStateOf(PodDeckBasics(plains = 1))
        var connected by mutableStateOf(true)
        var canAdd by mutableStateOf(true)
        val edits = mutableListOf<Pair<BasicLand, Int>>()
        compose.setContent { ScreenTheme { PodBasicSteppers(basics, connected, canAdd) { land, delta ->
            edits.add(land to delta); basics = land.withCount(basics, land.count(basics) + delta); true
        } } }
        for (land in BasicLand.entries) compose.onNodeWithContentDescription("Add ${land.cardName}").assertIsEnabled()
        compose.onNodeWithContentDescription("Remove Forest").assertIsNotEnabled()
        compose.onNodeWithContentDescription("Remove Plains").performClick()
        compose.onNodeWithContentDescription("Plains count: 0").assertExists()
        compose.onNodeWithContentDescription("Remove Plains").assertIsNotEnabled()
        compose.onNodeWithContentDescription("Add Island").performClick()
        assertEquals(listOf(BasicLand.PLAINS to -1, BasicLand.ISLAND to 1), edits)
        compose.runOnIdle { canAdd = false }
        compose.onNodeWithContentDescription("Add Island").assertIsNotEnabled()
        compose.onNodeWithContentDescription("Remove Island").assertIsEnabled()
        compose.runOnIdle { connected = false }
        for (land in BasicLand.entries) {
            compose.onNodeWithContentDescription("Remove ${land.cardName}").assertIsNotEnabled()
            compose.onNodeWithContentDescription("Add ${land.cardName}").assertIsNotEnabled()
        }
    }
}
