package org.asyncttwiz.android

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import org.asyncttwiz.protocol.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w412dp-h892dp-port-mdpi")
class PodStatsViewTest {
    @get:Rule val compose = createComposeRule()

    @Test fun compactChartsExposeCountsAndDemandVersusSourcePercentagesWithoutCaptions() {
        val card = CardDefinition("Hybrid", listOf(DefinitionFace("front", "Hybrid", typeLine = "Creature", manaCost = "{W/U}{B/P}")))
        compose.setContent { MaterialTheme {
            PodStatsView(PodDeckBuild(listOf(PodDeckCopy("copy")), PodDeckBasics(plains = 39)),
                mapOf("copy" to DraftCopy("copy", "digest")), mapOf("digest" to card))
        } }
        compose.onNodeWithText("Main including basics").assertDoesNotExist()
        compose.onNodeWithContentDescription("0: 0, 1: 0, 2: 1, 3: 0, 4: 0, 5: 0, 6: 0, 7+: 0").assertIsDisplayed()
        compose.onNodeWithText("2: 1").assertDoesNotExist()
        compose.onNodeWithText("Creature: 1").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Noncreature: 0").assertIsDisplayed()
        compose.onNodeWithText("Land: 39").assertIsDisplayed()
        compose.onNodeWithContentDescription("W: pip demand 25%, mana sources 100%").assertIsDisplayed()
        compose.onNodeWithContentDescription("U: pip demand 25%, mana sources 0%").assertIsDisplayed()
        compose.onNodeWithContentDescription("B: pip demand 50%, mana sources 0%").assertIsDisplayed()
        compose.onNodeWithText("Mana sources · approximate").assertDoesNotExist()
    }

    @Test
    @Config(qualifiers = "w892dp-h412dp-land-mdpi")
    fun emptyStatsRenderZeroValuesInLandscape() {
        compose.setContent { MaterialTheme { PodStatsView(PodDeckBuild(), emptyMap(), emptyMap()) } }
        compose.onNodeWithContentDescription("0: 0, 1: 0, 2: 0, 3: 0, 4: 0, 5: 0, 6: 0, 7+: 0").assertIsDisplayed()
        compose.onNodeWithText("0: 0").assertDoesNotExist()
        compose.onNodeWithTag("pod-stats").performScrollToNode(hasContentDescription("G: pip demand 0%, mana sources 0%"))
        compose.onNodeWithContentDescription("G: pip demand 0%, mana sources 0%").assertIsDisplayed()
        compose.onNodeWithText("No colored pips").assertDoesNotExist()
    }
}
