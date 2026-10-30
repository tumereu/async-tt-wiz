package org.asyncttwiz.android

import androidx.compose.material3.IconButton
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.*
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import org.asyncttwiz.protocol.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w412dp-h892dp-port-mdpi")
class PodPickChromeTest {
    @get:Rule val compose = createComposeRule()

    @Test fun avatarBadgesNeverChangeTheSlimRowAndTheHeaderCogDrivesThePickGrid() {
        val alice = AccountIdentity("alice", "Alice")
        val bob = AccountIdentity("bob", "Bob")
        var waiting by mutableIntStateOf(0)
        val detail = PodDetail("pod", "Pod", PodPhase.DRAFTING, alice, DraftSettings(),
            PodSource("Cube", 540), DraftSupply(90, 540),
            listOf(PodMember(alice), PodMember(bob, packsWaiting = 2)), emptyList(), updatedAt = "now")
        var navigations = 0
        compose.setContent { ScreenTheme { Column {
            val columns = rememberPodPickColumns()
            val build = rememberPodBuildColumns()
            val pack = DraftPrivatePack("pack", 1, (1..15).map { DraftCopy("$it", "card") })
            PodPlayHeader(detail.copy(members = detail.members.map {
                if (it.account.accountId == alice.accountId) it.copy(packsWaiting = waiting) else it
            }), { navigations++ }) { PodViewSettingsButton(columns, build) }
            PodDraftTabs(listOf(PodTab.DRAFT, PodTab.BUILD, PodTab.STATS), PodTab.DRAFT, {}) {
                IconButton({}, Modifier.size(32.dp)) { EntryIcon("Pod actions", "more") }
            }
            PodPickView(pack, detail.settings, emptyMap(), false, { _, _ -> }, columns = columns)
        } } }
        val aliceAvatar = compose.onNodeWithContentDescription("Alice's avatar")
        val bobAvatar = compose.onNodeWithContentDescription("Bob's avatar")
        aliceAvatar.assertWidthIsEqualTo(28.dp).assertHeightIsEqualTo(28.dp)
        compose.onNodeWithTag("pod-play-header").assertHeightIsEqualTo(PodPlayHeaderHeight)
        compose.onNode(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Ready")).assertExists()
        compose.onNodeWithText("Alice").assertDoesNotExist()
        compose.onNodeWithText("Bob").assertDoesNotExist()
        val aliceBounds = aliceAvatar.fetchSemanticsNode().boundsInRoot
        val bobBounds = bobAvatar.fetchSemanticsNode().boundsInRoot
        val header = compose.onNodeWithTag("pod-play-header").fetchSemanticsNode().boundsInRoot
        val title = compose.onNodeWithTag("pod-play-title").assertTextEquals("Pod").fetchSemanticsNode().boundsInRoot
        val menu = compose.onNodeWithContentDescription("Open navigation").fetchSemanticsNode().boundsInRoot
        assertTrue(aliceBounds.top >= title.bottom && title.left >= menu.right)
        // Layout settings live behind the cog at the header's right end, not in the pick area.
        compose.onNodeWithContentDescription("Cards per row").assertDoesNotExist()
        val cog = compose.onNodeWithContentDescription("View settings").fetchSemanticsNode().boundsInRoot
        assertTrue(cog.top >= header.top && cog.bottom <= header.bottom)
        assertTrue(cog.left >= bobBounds.right)
        assertEquals(header.right, cog.right, .5f)
        val tabs = compose.onNodeWithTag("pod-tabs").fetchSemanticsNode().boundsInRoot
        assertEquals(header.bottom, tabs.top)
        val overflow = compose.onNodeWithContentDescription("Pod actions").fetchSemanticsNode().boundsInRoot
        val lastTab = compose.onNodeWithText("Stats").fetchSemanticsNode().boundsInRoot
        assertTrue(overflow.top >= tabs.top && overflow.bottom <= tabs.bottom)
        assertTrue(overflow.left >= lastTab.right && overflow.width < lastTab.width)
        compose.onNodeWithContentDescription("Pod actions").assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.Selected))
        compose.onNodeWithContentDescription("Open navigation").performClick()
        compose.runOnIdle { assertEquals(1, navigations) }
        compose.onNodeWithContentDescription("View settings").performClick()
        compose.onNodeWithText("Draft · cards per row").assertIsDisplayed()
        compose.onNodeWithText("Build · columns").assertIsDisplayed()
        compose.onNodeWithContentDescription("Cards per row")
            .performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.SetProgress) { it(3f) }
        // The panel stays open while values change.
        compose.onNodeWithContentDescription("More cards per row").performClick()
        compose.onNodeWithContentDescription("Cards per row").assert(hasText("4"))
        androidx.test.espresso.Espresso.pressBack()
        compose.onNodeWithText("Draft · cards per row").assertDoesNotExist()
        val firstCard = compose.onNodeWithTag("pod-pick-card-1").fetchSemanticsNode().boundsInRoot
        assertTrue(firstCard.top >= tabs.bottom && firstCard.top - tabs.bottom < 8)
        // The panel's value drives the grid below it: four cards per row.
        assertEquals(firstCard.top, compose.onNodeWithTag("pod-pick-card-4").fetchSemanticsNode().boundsInRoot.top, .5f)
        assertTrue(compose.onNodeWithTag("pod-pick-card-5").fetchSemanticsNode().boundsInRoot.top > firstCard.bottom)
        for (count in listOf(1, 9, 10, 100)) {
            compose.runOnIdle { waiting = count }
            assertEquals(aliceBounds, aliceAvatar.fetchSemanticsNode().boundsInRoot)
            assertEquals(bobBounds, bobAvatar.fetchSemanticsNode().boundsInRoot)
            compose.onNodeWithTag("pod-play-header").assertHeightIsEqualTo(PodPlayHeaderHeight)
            compose.onNode(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "$count packs waiting")).assertExists()
            // Counts above nine show "9+", widening the circle into a pill of the same height, still inside the slim row.
            val label = if (count > 9) "9+" else "$count"
            val badge = compose.onAllNodesWithTag("pod-member-badge", useUnmergedTree = true)
                .filter(hasAnyDescendant(hasText(label)).or(hasText(label))).onFirst()
            badge.assertHeightIsEqualTo(12.dp)
            val bounds = badge.fetchSemanticsNode().boundsInRoot
            val text = compose.onNodeWithText(label, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
            assertTrue(bounds.left <= text.left && text.right <= bounds.right, "The badge text fits inside the badge")
            assertTrue(bounds.bottom <= header.bottom)
            // Never narrower than the circle; how much "9+" widens it depends on the font.
            assertTrue(bounds.width >= 11.5f)
            if (count <= 9) assertEquals(12f, bounds.width, .5f)
        }
    }

    @Test fun holdingColumnIconAndNumberShowsContextualTooltipWithoutChangingValue() {
        var value by mutableIntStateOf(3)
        var description by mutableStateOf("Cards per row")
        compose.setContent { ScreenTheme {
            PodColumnControl("$value", description, value, 1..10) { value = it }
        } }
        for (label in listOf("Cards per row", "Columns")) {
            compose.runOnIdle { description = label }
            compose.onAllNodesWithText("3", useUnmergedTree = true).onLast().performTouchInput { down(center); advanceEventTime(600) }
            compose.mainClock.advanceTimeBy(600)
            compose.onNodeWithText(label).assertIsDisplayed()
            compose.onAllNodesWithText("3", useUnmergedTree = true).onLast().performTouchInput { up() }
            compose.runOnIdle { assertEquals(3, value) }
            compose.mainClock.advanceTimeBy(2_000)
            compose.onNodeWithText(label).assertDoesNotExist()
        }
        compose.onNodeWithContentDescription("More columns").performClick()
        compose.onNodeWithContentDescription("Columns").assert(hasText("4"))
    }
}
