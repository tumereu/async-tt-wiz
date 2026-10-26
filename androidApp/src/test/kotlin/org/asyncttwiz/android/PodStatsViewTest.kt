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

    @Test fun allChartsExposeTheirNumbersAndSourcesAreLabeledApproximate() {
        val card = CardDefinition("Hybrid", listOf(DefinitionFace("front", "Hybrid", typeLine = "Creature", manaCost = "{W/U}{B/P}")))
        compose.setContent { MaterialTheme {
            PodStatsView(PodDeckBuild(listOf(PodDeckCopy("copy")), PodDeckBasics(plains = 39)),
                mapOf("copy" to DraftCopy("copy", "digest")), mapOf("digest" to card))
        } }
        compose.onNodeWithText("Main including basics").assertIsDisplayed()
        compose.onNodeWithText("2: 1").assertIsDisplayed()
        compose.onNodeWithText("Creature: 1").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Noncreature: 0").assertIsDisplayed()
        compose.onNodeWithText("Land: 39").assertIsDisplayed()
        compose.onNodeWithText("W: 0.5").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("U: 0.5").assertIsDisplayed()
        compose.onNodeWithText("B: 1").assertIsDisplayed()
        compose.onNodeWithText("Mana sources · approximate").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("W: 39").performScrollTo().assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "w892dp-h412dp-land-mdpi")
    fun emptyStatsRenderZeroValuesInLandscape() {
        compose.setContent { MaterialTheme { PodStatsView(PodDeckBuild(), emptyMap(), emptyMap()) } }
        compose.onNodeWithText("0: 0").assertIsDisplayed()
        compose.onNodeWithTag("pod-stats").performScrollToNode(hasText("No colored pips"))
        compose.onNodeWithText("No colored pips").assertIsDisplayed()
        compose.onNodeWithTag("pod-stats").performScrollToNode(hasText("Mana sources · approximate"))
        compose.onNodeWithText("Mana sources · approximate").assertIsDisplayed()
    }
}
