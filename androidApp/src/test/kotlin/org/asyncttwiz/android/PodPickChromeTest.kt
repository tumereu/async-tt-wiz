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

    @Test fun avatarBadgesNeverChangeTheSlimRowOrAvatarPositions() {
        val alice = AccountIdentity("alice", "Alice")
        val bob = AccountIdentity("bob", "Bob")
        var waiting by mutableIntStateOf(0)
        val detail = PodDetail("pod", "Pod", PodPhase.DRAFTING, alice, DraftSettings(),
            PodSource("Cube", 540), DraftSupply(90, 540),
            listOf(PodMember(alice), PodMember(bob, packsWaiting = 2)), emptyList(), updatedAt = "now")
        compose.setContent { ScreenTheme { Column {
            PodDraftTabs(listOf(PodTab.DRAFT, PodTab.BUILD, PodTab.STATS), PodTab.DRAFT, {}) {
                IconButton({}, Modifier.size(32.dp)) { EntryIcon("Pod actions", "more") }
            }
            PodPickView(DraftPrivatePack("pack", 1, (1..15).map { DraftCopy("$it", "card") }),
                detail.settings, emptyMap(), false, { _, _ -> }, chrome = { control ->
                    PodDraftMemberRow(detail.copy(members = detail.members.map {
                        if (it.account.accountId == alice.accountId) it.copy(packsWaiting = waiting) else it
                    }), control)
                })
        } } }
        val aliceAvatar = compose.onNodeWithContentDescription("Alice's avatar")
        val bobAvatar = compose.onNodeWithContentDescription("Bob's avatar")
        aliceAvatar.assertWidthIsEqualTo(28.dp).assertHeightIsEqualTo(28.dp)
        compose.onNodeWithTag("pod-draft-members").assertHeightIsEqualTo(40.dp)
        compose.onNode(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Ready")).assertExists()
        compose.onNodeWithText("Alice").assertDoesNotExist()
        compose.onNodeWithText("Bob").assertDoesNotExist()
        val aliceBounds = aliceAvatar.fetchSemanticsNode().boundsInRoot
        val bobBounds = bobAvatar.fetchSemanticsNode().boundsInRoot
        val row = compose.onNodeWithTag("pod-draft-members").fetchSemanticsNode().boundsInRoot
        val control = compose.onNodeWithContentDescription("Cards per row").fetchSemanticsNode().boundsInRoot
        assertTrue(control.top >= row.top && control.bottom <= row.bottom)
        assertTrue(control.left >= bobBounds.right)
        val tabs = compose.onNodeWithTag("pod-tabs").fetchSemanticsNode().boundsInRoot
        val overflow = compose.onNodeWithContentDescription("Pod actions").fetchSemanticsNode().boundsInRoot
        val lastTab = compose.onNodeWithText("Stats").fetchSemanticsNode().boundsInRoot
        assertTrue(overflow.top >= tabs.top && overflow.bottom <= tabs.bottom && overflow.bottom <= row.top)
        assertTrue(overflow.left >= lastTab.right && overflow.width < lastTab.width)
        compose.onNodeWithContentDescription("Pod actions").assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.Selected))
        compose.onNodeWithContentDescription("Cards per row")
            .performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.SetProgress) { it(3f) }
        compose.onNodeWithContentDescription("More cards per row").performClick()
        compose.onNodeWithContentDescription("Cards per row").assert(hasText("4"))
        val firstCard = compose.onNodeWithTag("pod-pick-card-1").fetchSemanticsNode().boundsInRoot
        assertTrue(firstCard.top >= row.bottom && firstCard.top - row.bottom < 8)
        for (count in listOf(1, 9, 10, 100)) {
            compose.runOnIdle { waiting = count }
            assertEquals(aliceBounds, aliceAvatar.fetchSemanticsNode().boundsInRoot)
            assertEquals(bobBounds, bobAvatar.fetchSemanticsNode().boundsInRoot)
            compose.onNodeWithTag("pod-draft-members").assertHeightIsEqualTo(40.dp)
            compose.onNode(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "$count packs waiting")).assertExists()
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
