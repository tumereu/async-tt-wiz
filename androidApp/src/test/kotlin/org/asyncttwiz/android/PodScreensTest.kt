package org.asyncttwiz.android

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import org.asyncttwiz.protocol.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertTrue

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

    @Test fun gamesTabDisplaysBuildingAndSubmittedWithoutPrematureChallenges() {
        val detail = PodDetail("pod", "Synergy", PodPhase.DRAFTED, alice, DraftSettings(),
            PodSource("Cube", 540), DraftSupply(90, 540),
            listOf(PodMember(alice, building = true), PodMember(bob, submitted = true)), emptyList(),
            updatedAt = "2026-10-26T00:00:00Z")
        compose.setContent { ScreenTheme { PodGamesTab(detail) } }
        compose.onNodeWithText("Alice").assertIsDisplayed()
        compose.onNodeWithText("building").assertIsDisplayed()
        compose.onNodeWithText("Bob").assertIsDisplayed()
        compose.onNodeWithText("deck submitted").assertIsDisplayed()
        compose.onNodeWithText("Challenge").assertDoesNotExist()
    }
}
