package org.asyncttwiz.android

import android.content.Context
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import org.asyncttwiz.protocol.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w412dp-h892dp-port-mdpi")
class PodBuildViewTest {
    @get:Rule val compose = createComposeRule()
    private val card = CardDefinition("Test card", listOf(DefinitionFace("front", "Test card", typeLine = "Creature", manaCost = "{1}")))
    private val tile = PodBuildTile("digest", listOf("first", "second"), card, emptySet())
    private val moves = mutableListOf<Pair<String, PodDeckCategory>>()

    @Before fun clearPreferences() {
        ApplicationProvider.getApplicationContext<Context>().getSharedPreferences("pod-build", 0).edit().clear().commit()
    }

    @Test fun swipesFollowTheTableAndMoveOnlyOneCopy() {
        var category by mutableStateOf(PodDeckCategory.MAIN)
        compose.setContent { MaterialTheme {
            Box(Modifier.width(160.dp)) { PodBuildCard(tile, category, true, { id, destination -> moves += id to destination }, {}) }
        } }
        for (source in PodDeckCategory.entries) {
            compose.runOnIdle { category = source }
            val node = compose.onNodeWithTag("pod-build-card-first")
            node.performTouchInput { swipeRight() }
            compose.runOnIdle { assertEquals("first" to PodDeckRules.swipe(source, PodDeckSwipe.RIGHT), moves.last()) }
            node.performTouchInput { swipeLeft() }
            compose.runOnIdle { assertEquals("first" to PodDeckRules.swipe(source, PodDeckSwipe.LEFT), moves.last()) }
        }
        assertEquals(6, moves.size)
    }

    @Test fun tapOffersTwoDestinationsAndHoldInspects() {
        var inspections = 0
        compose.setContent { MaterialTheme {
            Box(Modifier.width(160.dp)) { PodBuildCard(tile, PodDeckCategory.MAIN, true,
                { id, destination -> moves += id to destination }, { inspections++ }) }
        } }
        compose.onNodeWithText("×2").assertExists()
        compose.onNodeWithTag("pod-build-card-first").performClick()
        compose.onNodeWithText("→ Side").assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(listOf("first" to PodDeckCategory.SIDE), moves) }
        compose.onNodeWithTag("pod-build-card-first").performTouchInput { longClick() }
        compose.runOnIdle { assertEquals(1, inspections) }
        compose.onNodeWithText("→ Trash").assertDoesNotExist()
    }

    @Test fun submittedCardsHaveNoMoveGesturesOrMenusButCanStillBeInspected() {
        var inspections = 0
        compose.setContent { MaterialTheme {
            Box(Modifier.width(160.dp)) { PodBuildCard(tile, PodDeckCategory.MAIN, false,
                { id, destination -> moves += id to destination }, { inspections++ }) }
        } }
        val node = compose.onNodeWithTag("pod-build-card-first")
        node.performClick()
        compose.onNodeWithText("→ Side").assertDoesNotExist()
        node.performTouchInput { swipeRight() }
        node.performTouchInput { swipeLeft() }
        node.performTouchInput { longClick() }
        compose.runOnIdle { assertTrue(moves.isEmpty()); assertEquals(1, inspections) }
    }

    @Test fun verticalDragOnACardScrollsInsteadOfMovingIt() {
        lateinit var scroll: ScrollState
        compose.setContent { MaterialTheme {
            scroll = rememberScrollState()
            Column(Modifier.width(160.dp).height(300.dp).verticalScroll(scroll)) {
                PodBuildCard(tile, PodDeckCategory.MAIN, true, { id, destination -> moves += id to destination }, {})
                Spacer(Modifier.height(1000.dp))
            }
        } }
        compose.onNodeWithTag("pod-build-card-first").performTouchInput { swipeUp() }
        compose.runOnIdle { assertTrue(scroll.value > 0); assertTrue(moves.isEmpty()) }
    }

    @Test fun buildShowsCountsValidatesSubmitAndReportsBasicsWithoutMutatingServerData() {
        var build by mutableStateOf(PodDeckBuild(listOf(PodDeckCopy("first")), PodDeckBasics(plains = 38)))
        var requestedBasics: PodDeckBasics? = null
        var submits = 0
        compose.setContent { MaterialTheme {
            PodBuildView(build, mapOf("first" to DraftCopy("first", "digest")), mapOf("digest" to card), 0,
                true, true, { _, _ -> }, { requestedBasics = it }, { submits++ })
        } }
        compose.onNodeWithText("Main 1 + 38 basics = 39").assertIsDisplayed()
        compose.onNodeWithText("Main needs 40 cards").assertIsDisplayed()
        compose.onNodeWithText("Submit").assertIsNotEnabled()
        compose.onNodeWithContentDescription("Remove Island").assertIsNotEnabled()
        compose.onNodeWithContentDescription("Add Plains").performClick()
        compose.runOnIdle { assertEquals(PodDeckBasics(plains = 39), requestedBasics); assertEquals(38, build.basics.plains) }
        compose.runOnIdle { build = build.copy(basics = requestedBasics!!) }
        compose.onNodeWithText("Main 1 + 39 basics = 40").assertIsDisplayed()
        compose.onNodeWithText("Submit").assertIsEnabled().performClick()
        compose.onNodeWithText("Submit your deck? It is locked afterwards.").assertIsDisplayed()
        compose.onNodeWithText("Cancel").performClick()
        compose.runOnIdle { assertEquals(0, submits) }
        compose.onNodeWithText("Submit").performClick()
        compose.onAllNodesWithText("Submit").onLast().performClick()
        compose.runOnIdle { assertEquals(1, submits) }
    }

    @Test fun columnAndBasicControlsStayOnCompactSingleLinesAndTilesKeepTheirShape() {
        val longName = "An unusually long card name that should be ellipsized inside a card-shaped placeholder".repeat(2)
        compose.setContent { ScreenTheme {
            PodBuildView(PodDeckBuild(listOf(PodDeckCopy("first")), PodDeckBasics(plains = 38)),
                mapOf("first" to DraftCopy("first", "digest")), mapOf("digest" to card.copy(name = longName)), null,
                true, false, { _, _ -> }, {}, {})
        } }
        assertTrue(with(compose.onNodeWithContentDescription("Columns").getUnclippedBoundsInRoot()) { bottom - top } <= 40.dp)
        assertTrue(with(compose.onNodeWithTag("pod-build-basics").getUnclippedBoundsInRoot()) { bottom - top } <= 40.dp)
        compose.onNodeWithContentDescription("Add Forest").assertIsDisplayed()
        val bounds = compose.onNodeWithTag("pod-build-card-first").fetchSemanticsNode().boundsInRoot
        assertTrue(kotlin.math.abs(bounds.height / bounds.width - 1.4f) < .03f)
        compose.onNodeWithContentDescription("More Columns").performClick()
        compose.onNodeWithText("Columns 8").assertIsDisplayed()
    }

    @Test fun submittedBuildHidesAllEditingControlsAndTrashStartsExpanded() {
        compose.setContent { MaterialTheme {
            PodBuildView(PodDeckBuild(listOf(PodDeckCopy("first", PodDeckCategory.TRASH))),
                mapOf("first" to DraftCopy("first", "digest")), mapOf("digest" to card), null,
                false, false, { _, _ -> fail("read-only") }, { fail("read-only") }, { fail("read-only") })
        } }
        compose.onNodeWithText("Deck submitted").assertIsDisplayed()
        compose.onNodeWithText("Submit").assertDoesNotExist()
        compose.onNodeWithContentDescription("Add Plains").assertDoesNotExist()
        compose.onNodeWithTag("pod-build").performScrollToNode(hasTestTag("pod-build-card-first"))
        compose.onNodeWithTag("pod-build-card-first").assertIsDisplayed()
        compose.onNodeWithText("Trash 1").assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "w892dp-h412dp-land-mdpi")
    fun landscapeUsesItsOwnColumnPreferenceAndFitsAllColumns() {
        val preferences = ApplicationProvider.getApplicationContext<Context>().getSharedPreferences("pod-build", 0)
        preferences.edit().putInt("columns-1", 3).putInt("columns-2", 10).commit()
        compose.setContent { MaterialTheme {
            PodBuildView(PodDeckBuild(), emptyMap(), emptyMap(), null, true, false, { _, _ -> }, {}, {})
        } }
        compose.onNodeWithText("Columns 10").assertIsDisplayed()
        compose.onAllNodesWithText("8+").onFirst().assertIsDisplayed()
        compose.onNodeWithContentDescription("Columns").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.SetProgress) { it(4f) }
        compose.onNodeWithText("Columns 4").assertIsDisplayed()
        compose.runOnIdle {
            assertEquals(4, preferences.getInt("columns-2", 0))
            assertEquals(3, preferences.getInt("columns-1", 0))
        }
    }
}
