package org.asyncttwiz.android

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.runtime.*
import androidx.compose.foundation.layout.Column
import org.asyncttwiz.protocol.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertTrue
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w412dp-h892dp-port-mdpi")
class PodScreensTest {
    @get:Rule val compose = createComposeRule()
    private val alice = AccountIdentity("alice", "Alice")
    private val bob = AccountIdentity("bob", "Bob")

    @Test fun pickRowShowsMemberNamesThroughAvatarsAndOpensPod() {
        var opened = false
        val pod = PodSummary("pod", "Synergy", PodPhase.DRAFTING, alice, listOf(alice, bob),
            PodAttention.YOUR_PICK, 2, "2026-10-26T00:00:00Z")
        compose.setContent { ScreenTheme { PodRow(pod, { opened = true }) } }
        compose.onNodeWithText("Your pick · 2 packs").assertIsDisplayed()
        compose.onNodeWithText("Synergy").performClick()
        assertTrue(opened)
    }

    @Test fun pendingInvitationsAppearAboveMyPodsAndHeaderDisappearsWhenEmpty() {
        val pod = PodSummary("pod", "My draft", PodPhase.SETUP, alice, listOf(alice, bob),
            PodAttention.NONE, 0, "2026-10-26T00:00:00Z")
        var state by mutableStateOf(PodState(loaded = true, pods = listOf(pod),
            invitations = listOf(PodInvitation("invited", "Join this draft", bob, "now"))))
        var response: Pair<String, Boolean>? = null
        compose.setContent { ScreenTheme { PodsList(state, {}, { id, accept -> response = id to accept }, {}) } }
        val invitation = compose.onNodeWithText("Invitations").fetchSemanticsNode().boundsInRoot
        val myPods = compose.onNodeWithText("MY PODS (1)").fetchSemanticsNode().boundsInRoot
        assertTrue(invitation.bottom < myPods.top)
        compose.onNodeWithText("Join this draft").assertIsDisplayed()
        compose.onNodeWithText("Accept").performClick()
        assertEquals("invited" to true, response)
        compose.onNodeWithText("Refresh").assertDoesNotExist()
        compose.runOnIdle { state = state.copy(invitations = emptyList()) }
        compose.onNodeWithText("Invitations").assertDoesNotExist()
        compose.onNodeWithText("No pending pod invitations.").assertDoesNotExist()
        compose.onNodeWithText("MY PODS (1)").assertIsDisplayed()
    }

    @Test fun gamesTabDisplaysBuildingAndSubmittedWithoutPrematureChallenges() {
        val detail = PodDetail("pod", "Synergy", PodPhase.DRAFTED, alice, DraftSettings(),
            PodSource("Cube", 540), DraftSupply(90, 540),
            listOf(PodMember(alice, building = true), PodMember(bob, submitted = true)), emptyList(),
            updatedAt = "2026-10-26T00:00:00Z")
        compose.setContent { ScreenTheme { PodGamesTab(detail, alice.accountId, null) } }
        compose.onNodeWithText("Alice").assertIsDisplayed()
        compose.onNodeWithText("building").assertIsDisplayed()
        compose.onNodeWithText("Bob").assertIsDisplayed()
        compose.onNodeWithText("deck submitted").assertIsDisplayed()
        compose.onNodeWithText("Challenge").assertDoesNotExist()
        compose.onNodeWithText("Submit your deck to challenge other members.").assertIsDisplayed()
    }

    @Test fun modeChipsSwitchToGridDefaultsAndHideBoosterSettingsIncludingAdvanced() {
        var settings by mutableStateOf(DraftSettings())
        compose.setContent { ScreenTheme { Column { PodSettingsFields(settings, 2, true) { settings = it } } } }
        compose.onNodeWithText("Booster").assertIsSelected()
        compose.onNodeWithText("Glimpse").assertExists()
        compose.onNodeWithText("Grid").assertExists().performClick().assertIsSelected()
        compose.runOnIdle { assertEquals(DraftSettings.grid(), settings) }
        compose.onNodeWithText("18").assertExists()
        compose.onNodeWithText("Grids").assertExists()
        compose.onNodeWithText("Packs per player").assertDoesNotExist()
        compose.onNodeWithText("Cards per pack").assertDoesNotExist()
        compose.onNodeWithText("Advanced ▾").performClick()
        compose.onNodeWithText("Grids open at once").assertExists()
        listOf("Keep per pick", "Burn per pick", "Discard remaining at", "Packs open at once", "Color balancing").forEach {
            compose.onNodeWithText(it).assertDoesNotExist()
        }
        compose.onNode(isToggleable()).assertIsEnabled().performClick()
        compose.runOnIdle { assertTrue(settings.twoPicks) }
        compose.onNodeWithText("Glimpse").performClick()
        compose.runOnIdle { assertEquals(DraftSettings.glimpse(), settings) }
        compose.onNodeWithText("Grids").assertDoesNotExist()
        compose.onNodeWithText("Two picks each").assertDoesNotExist()
        compose.onNodeWithText("Cards per pack").assertExists()
    }

    @Test fun twoPicksIsOnlyEnabledWithExactlyTwoMembersAndCreatorPermission() {
        var members by mutableIntStateOf(3)
        var enabled by mutableStateOf(true)
        compose.setContent { ScreenTheme { Column { PodSettingsFields(DraftSettings.grid(), members, enabled) {} } } }
        compose.onNode(isToggleable()).assertIsNotEnabled()
        compose.onNodeWithText("Two picks each requires exactly 2 members.").assertExists()
        compose.runOnIdle { members = 2 }
        compose.onNode(isToggleable()).assertIsEnabled()
        compose.runOnIdle { enabled = false }
        compose.onNode(isToggleable()).assertIsNotEnabled()
        compose.onNodeWithText("Grid").assertIsNotEnabled()
    }
}
