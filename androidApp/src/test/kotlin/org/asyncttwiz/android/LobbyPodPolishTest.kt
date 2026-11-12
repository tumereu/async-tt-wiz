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

    // LOB-052: no basic land steppers; basics are cards like any other
    @Test fun podPreparationHasNoBasicLandSteppers() {
        val fixture = ScreenFixtures().also { it.configure("prepare-deck-faded") }
        val preparation = fixture.lobby.state.preparation!!
        fixture.lobby.seed(fixture.lobby.state.copy(preparation = preparation.copy(podRules = PodGameRules())))
        try {
            compose.setContent { ScreenTheme { PreparationScreen(fixture.lobby, "a", false) } }
            for (land in BasicLand.entries) {
                compose.onNodeWithContentDescription("Add ${land.cardName}").assertDoesNotExist()
                compose.onNodeWithContentDescription("Remove ${land.cardName}").assertDoesNotExist()
            }
            compose.onNodeWithContentDescription("Meadow Sentinel, 3 copies, Mainboard").assertIsDisplayed()
            compose.onNodeWithContentDescription("Aegis Ward, 4 copies, Mainboard").assertIsDisplayed()
        } finally { fixture.lobby.close() }
    }

    // LOB-047, LOB-048
    @Test fun preparationShowsBothSectionsInOneScrollWithTypeGroupsAndATapMenu() {
        val fixture = ScreenFixtures().also { it.configure("prepare-deck-faded") }
        try {
            compose.setContent { ScreenTheme { PreparationScreen(fixture.lobby, "a", false) } }
            compose.onNodeWithText("Mainboard · 59").assertIsDisplayed()
            compose.onNodeWithText("Creatures · 59").assertIsDisplayed()
            val tile = compose.onNodeWithContentDescription("Meadow Sentinel, 3 copies, Mainboard").assertIsDisplayed()
            assertEquals(listOf("Move one Meadow Sentinel to Sideboard", "Move all Meadow Sentinel to Sideboard"),
                tile.fetchSemanticsNode().config[SemanticsActions.CustomActions].map { it.label })
            tile.performTouchInput { click() }
            compose.onNodeWithText("Move one to Sideboard").assertIsDisplayed()
            compose.onNodeWithText("Move all 3 to Sideboard").assertIsDisplayed()
            // LOB-048: no Inspect in the menu; holding a card inspects it
            compose.onNodeWithText("Inspect").assertDoesNotExist()
            compose.onNodeWithTag("preparation").performScrollToNode(hasText("Sideboard · 16"))
            compose.onNodeWithText("Sideboard · 16").assertIsDisplayed()
        } finally { fixture.lobby.close() }
    }

    // LOB-047, DRF-105, DRF-166 (owner, 2026-11-11): Prepare deck of a pod game has the deck's cover thumbnail and a pen in its
    // header beside the private name: the cover lists the deck's Main and Side cards alphabetically, the pen opens the name
    @Test fun podPreparationRenamesTheDeckAndChoosesItsCover() {
        val fixture = ScreenFixtures().also { it.configure("prepare-deck-faded") }
        val pods = java.lang.reflect.Proxy.newProxyInstance(Pods::class.java.classLoader, arrayOf(Pods::class.java)) { _, method, _ ->
            error("Screenshot attempted ${method.name}")
        } as Pods
        val worker = object : java.util.concurrent.AbstractExecutorService() {
            override fun execute(command: Runnable) = Unit
            override fun shutdown() = Unit
            override fun shutdownNow() = mutableListOf<Runnable>()
            override fun isShutdown() = false
            override fun isTerminated() = false
            override fun awaitTermination(timeout: Long, unit: java.util.concurrent.TimeUnit) = true
        }
        val lobby = LobbyController(java.lang.reflect.Proxy.newProxyInstance(Lobbies::class.java.classLoader, arrayOf(Lobbies::class.java)) { _, method, _ ->
            error("Screenshot attempted ${method.name}")
        } as Lobbies, { "fixture" }, worker, { it() }, accountId = { "a" }, podApi = pods)
        val state = fixture.lobby.state
        val preparation = state.preparation!!
        val sentinel = preparation.definitions.first { it.definition.name == "Meadow Sentinel" }.digest
        lobby.seed(state.copy(detail = state.detail!!.copy(podId = "pod-1", podName = "Cube night"), ownPodDeckName = "Cube night deck",
            preparation = preparation.copy(podRules = PodGameRules(), players = preparation.players.map { player ->
                player.copy(deck = player.deck?.copy(cover = sentinel)) })))
        try {
            compose.setContent { ScreenTheme { PreparationScreen(lobby, "a", false) } }
            compose.onNodeWithText("Cube night deck").assertIsDisplayed()
            compose.onNodeWithTag("preparation-cover").assertIsDisplayed().assertHasClickAction()
            compose.onNodeWithTag("preparation-rename").assertIsDisplayed()
            compose.onRoot().captureShot("$SCREENSHOTS/prepare-pod-deck-header.png", "Prepare deck of a pod game in its lobby: the " +
                "header shows the private deck name with the deck's cover thumbnail and a pen beside it, to choose another cover " +
                "or rename the deck, also after submitting.")
            compose.onNodeWithTag("preparation-cover").performClick()
            val names = listOf("Aegis Ward", "Ember Adept", "Meadow Sentinel", "Quiet Grove", "Training Land")
            val tops = names.map { name ->
                val digest = preparation.definitions.first { it.definition.name == name }.digest
                compose.onNodeWithTag("preparation-cover-$digest").assertIsDisplayed().getUnclippedBoundsInRoot().top
            }
            assertEquals(tops.sorted(), tops, "listed alphabetically")
            compose.onNode(hasTestTag("preparation-cover-$sentinel") and hasAnyDescendant(hasContentDescription("Selected cover")), useUnmergedTree = true)
                .assertExists()
            compose.onNodeWithText("Cancel").performClick()
            compose.onNodeWithTag("preparation-rename").performClick()
            compose.onNodeWithText("Deck name").assertIsDisplayed()
            compose.onNodeWithText("Save name").assertIsDisplayed()
        } finally { lobby.close(); fixture.lobby.close() }
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
