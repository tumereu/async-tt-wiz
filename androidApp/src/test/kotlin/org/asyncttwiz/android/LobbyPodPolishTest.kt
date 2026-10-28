package org.asyncttwiz.android

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import org.asyncttwiz.protocol.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.*

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w412dp-h892dp-port-mdpi")
class LobbyPodPolishTest {
    @get:Rule val compose = createComposeRule()

    @Test fun basicsFitOneCompactRowAndKeepAccessibleCountsAndActions() {
        val basics = PodDeckBasics(plains = 12, swamp = 3, mountain = 4, forest = 5)
        val changes = mutableListOf<Pair<BasicLand, Int>>()
        compose.setContent { ScreenTheme {
            Box(Modifier.width(380.dp)) { PodBasicSteppers(basics, true, true) { land, delta -> changes.add(land to delta) } }
        } }
        val first = compose.onNodeWithContentDescription("Add Plains").getUnclippedBoundsInRoot()
        var previousRight = 0.dp
        for (land in BasicLand.entries) {
            val add = compose.onNodeWithContentDescription("Add ${land.cardName}").assertIsDisplayed().assertIsEnabled()
            val bounds = add.getUnclippedBoundsInRoot()
            assertEquals(first.top, bounds.top)
            assertEquals(first.bottom, bounds.bottom)
            assertTrue(bounds.bottom - bounds.top <= 40.dp)
            val remove = compose.onNodeWithContentDescription("Remove ${land.cardName}").assertIsDisplayed()
            assertTrue(remove.getUnclippedBoundsInRoot().left >= previousRight)
            previousRight = bounds.right
            compose.onNodeWithContentDescription("${land.cardName} count: ${land.count(basics)}").assertIsDisplayed()
            add.performTouchInput { click() }
            if (land == BasicLand.ISLAND) remove.assertIsNotEnabled()
            else remove.assertIsEnabled().performTouchInput { click() }
        }
        assertTrue(previousRight <= 380.dp)
        assertEquals(BasicLand.entries.flatMap { land ->
            listOf(land to 1) + if (land == BasicLand.ISLAND) emptyList() else listOf(land to -1)
        }, changes)
    }

    @Test fun cardLimitAndReadOnlyStateStillDisableBasicChanges() {
        var enabled by mutableStateOf(true)
        compose.setContent { ScreenTheme {
            PodBasicSteppers(PodDeckBasics(1, 1, 1, 1, 1), enabled, false) { _, _ -> fail("Disabled change") }
        } }
        for (land in BasicLand.entries) {
            compose.onNodeWithContentDescription("Add ${land.cardName}").assertIsNotEnabled()
            compose.onNodeWithContentDescription("Remove ${land.cardName}").assertIsEnabled()
        }
        compose.runOnIdle { enabled = false }
        for (land in BasicLand.entries) {
            compose.onNodeWithContentDescription("Add ${land.cardName}").assertIsNotEnabled()
            compose.onNodeWithContentDescription("Remove ${land.cardName}").assertIsNotEnabled()
        }
    }

    @Test fun podPreparationShowsMainboardCardsBelowBasicsWithoutScrolling() {
        val fixture = ScreenFixtures().also { it.configure("prepare-deck-faded") }
        val preparation = fixture.lobby.state.preparation!!
        fixture.lobby.seed(fixture.lobby.state.copy(preparation = preparation.copy(podRules = PodGameRules())))
        try {
            compose.setContent { ScreenTheme { PreparationScreen(fixture.lobby, "a", false) } }
            compose.onNodeWithContentDescription("Add Forest").assertIsDisplayed()
            compose.onNodeWithContentDescription("Meadow Sentinel, 3 copies, Mainboard").assertIsDisplayed()
            compose.onNodeWithContentDescription("Aegis Ward, 4 copies, Mainboard").assertIsDisplayed()
        } finally { fixture.lobby.close() }
    }

    @Test fun preparationShowsBothSectionsInOneScrollWithTypeGroupsAndATapMenu() {
        val fixture = ScreenFixtures().also { it.configure("prepare-deck-faded") }
        try {
            compose.setContent { ScreenTheme { PreparationScreen(fixture.lobby, "a", false) } }
            compose.onNodeWithText("Mainboard · 59").assertIsDisplayed()
            compose.onNodeWithText("Creatures · 59").assertIsDisplayed()
            val tile = compose.onNodeWithContentDescription("Meadow Sentinel, 3 copies, Mainboard").assertIsDisplayed()
            assertEquals(listOf("Move one Meadow Sentinel to Sideboard", "Move all Meadow Sentinel to Sideboard", "Inspect Meadow Sentinel"),
                tile.fetchSemanticsNode().config[SemanticsActions.CustomActions].map { it.label })
            tile.performTouchInput { click() }
            compose.onNodeWithText("Move one to Sideboard").assertIsDisplayed()
            compose.onNodeWithText("Move all 3 to Sideboard").assertIsDisplayed()
            compose.onNodeWithText("Inspect").assertIsDisplayed()
            compose.onNodeWithTag("preparation").performScrollToNode(hasText("Sideboard · 16"))
            compose.onNodeWithText("Sideboard · 16").assertIsDisplayed()
        } finally { fixture.lobby.close() }
    }

    @Test fun unloadedLobbyCoverUsesSmallEllipsizedTextInsideACardShape() {
        val fixture = ScreenFixtures().also { it.configure("ready-waiting") }
        val name = "Lingering Souls with an unusually long card name that must not overflow the cover".repeat(2)
        val card = CardDefinition(name, listOf(DefinitionFace("front", name)))
        val submitted = SubmittedDefinition(DeckJson.digest(card), card)
        val preparation = fixture.lobby.state.preparation!!
        fixture.lobby.seed(fixture.lobby.state.copy(preparation = preparation.copy(
            podRules = PodGameRules(), definitions = preparation.definitions + submitted,
            players = preparation.players.map { player -> player.copy(deck = player.deck?.copy(cover = submitted.digest)) })))
        try {
            compose.setContent { ScreenTheme { WaitingLobby(fixture.lobby, "a", null) } }
            val coverText = compose.onNodeWithText(name).assertIsDisplayed()
            val layouts = mutableListOf<TextLayoutResult>()
            coverText.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
            val layout = layouts.single()
            assertEquals(10.sp, layout.layoutInput.style.fontSize)
            assertTrue(layout.lineCount <= 3)
            assertTrue(layout.isLineEllipsized(layout.lineCount - 1))
            val cover = coverText.onParent()
            cover.assertWidthIsEqualTo(48.dp).assertHeightIsEqualTo(68.dp)
            val textBounds = coverText.getUnclippedBoundsInRoot()
            val coverBounds = cover.getUnclippedBoundsInRoot()
            assertTrue(textBounds.left >= coverBounds.left && textBounds.right <= coverBounds.right)
            assertTrue(textBounds.top >= coverBounds.top && textBounds.bottom <= coverBounds.bottom)
            compose.onNodeWithText("Training deck").assertIsDisplayed()
        } finally { fixture.lobby.close() }
    }
}
