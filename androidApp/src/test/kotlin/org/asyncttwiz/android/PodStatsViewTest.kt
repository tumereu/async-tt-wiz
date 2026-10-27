package org.asyncttwiz.android

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import org.asyncttwiz.protocol.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w412dp-h892dp-port-mdpi")
class PodStatsViewTest {
    @get:Rule val compose = createComposeRule()

    @Test fun donutsExposeIndependentSharesAndStackedCurveWithoutCaptions() {
        val card = CardDefinition("Hybrid", listOf(DefinitionFace("front", "Hybrid", typeLine = "Creature", manaCost = "{W/U}{B/P}")))
        compose.setContent { MaterialTheme {
            PodStatsView(PodDeckBuild(listOf(PodDeckCopy("copy")), PodDeckBasics(plains = 39)),
                mapOf("copy" to DraftCopy("copy", "digest")), mapOf("digest" to card))
        } }
        compose.onNodeWithText("Main including basics").assertDoesNotExist()
        compose.onNodeWithTag("pod-stats-curve").assertIsDisplayed()
            .assert(hasContentDescription("2: 1 creature, 0 noncreature", substring = true))
        compose.onNodeWithText("2: 1").assertDoesNotExist()
        compose.onNodeWithText("Creature: 1").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Noncreature: 0").assertIsDisplayed()
        compose.onNodeWithText("Land: 39").assertIsDisplayed()
        compose.onNodeWithContentDescription("Pips: W 25%, U 25%, B 50%, R 0%, G 0%").assertIsDisplayed()
        compose.onNodeWithContentDescription("Sources: W 100%, U 0%, B 0%, R 0%, G 0%").assertIsDisplayed()
        val pips = compose.onNodeWithTag("pod-stats-donut-Pips").getUnclippedBoundsInRoot()
        val sources = compose.onNodeWithTag("pod-stats-donut-Sources").getUnclippedBoundsInRoot()
        assertEquals(pips.top, sources.top)
        assertTrue(pips.right < sources.left)
        compose.onNodeWithTag("pod-stats-donut-Colors").performScrollTo().assertIsDisplayed()
            .assert(hasContentDescription("Multicolor 100%", substring = true))
        compose.onAllNodesWithTag("pod-color-bar").assertCountEquals(0)
        compose.onNodeWithText("Mana sources · approximate").assertDoesNotExist()
    }

    @Test
    @Config(qualifiers = "w892dp-h412dp-land-mdpi")
    fun emptyStatsRenderZeroValuesInLandscape() {
        compose.setContent { MaterialTheme { PodStatsView(PodDeckBuild(), emptyMap(), emptyMap()) } }
        compose.onNodeWithTag("pod-stats-curve").assertIsDisplayed()
        compose.onNodeWithText("0: 0").assertDoesNotExist()
        compose.onNodeWithTag("pod-stats-donut-Colors").performScrollTo().assertIsDisplayed()
        val donuts = listOf("Pips", "Sources", "Colors").map { title ->
            compose.onNodeWithTag("pod-stats-donut-$title")
                .assert(hasContentDescription("W 0%, U 0%, B 0%, R 0%, G 0%", substring = true))
                .getUnclippedBoundsInRoot()
        }
        assertEquals(donuts[0].top, donuts[1].top)
        assertEquals(donuts[1].top, donuts[2].top)
        assertTrue(donuts[0].right < donuts[1].left && donuts[1].right < donuts[2].left)
        compose.onNodeWithText("No colored pips").assertDoesNotExist()
    }

    @Test fun chartBoundsStayStableWhenSharesRefreshInLightAndDarkThemes() {
        val dark = mutableStateOf(false)
        val build = mutableStateOf(PodDeckBuild())
        compose.setContent { MaterialTheme(colorScheme = if (dark.value) darkColorScheme() else lightColorScheme()) {
            PodStatsView(build.value, emptyMap(), emptyMap())
        } }
        val before = compose.onNodeWithTag("pod-stats-donut-Sources").getUnclippedBoundsInRoot()
        compose.runOnIdle { build.value = PodDeckBuild(basics = PodDeckBasics(forest = 17)); dark.value = true }
        compose.onNodeWithTag("pod-stats-donut-Sources")
            .assert(hasContentDescription("G 100%", substring = true)).assertIsDisplayed()
        assertEquals(before, compose.onNodeWithTag("pod-stats-donut-Sources").getUnclippedBoundsInRoot())
    }
}
