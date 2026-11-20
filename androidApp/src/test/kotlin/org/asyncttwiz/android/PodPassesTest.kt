package org.asyncttwiz.android

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import org.asyncttwiz.protocol.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals

/** Custom booster's passes in pod setup and in its invitation. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w412dp-h1300dp-port-mdpi")
class PodPassesTest {
    @get:Rule val compose = createComposeRule()

    // DRF-262, DRF-020: choosing Custom booster shows its passes; the creator edits a pass in its number dialog, adds one (a
    // copy of the last) and removes one, never the only one
    @Test fun theCreatorEditsAddsAndRemovesPasses() {
        var settings by mutableStateOf(DraftSettings.booster())
        compose.setContent { ScreenTheme { Column { PodSettingsFields(settings, 2, true) { settings = it(settings) } } } }
        compose.onNodeWithTag("pod-passes").assertDoesNotExist()
        compose.onNodeWithTag("pod-mode").performClick()
        compose.onNodeWithText("Custom booster · 2–8 players").performClick()
        compose.onNodeWithText("Passes (7)").assertIsDisplayed()
        listOf("Keep", "Burn", "Discard").forEach { compose.onNodeWithText(it).assertDoesNotExist() }
        compose.onNodeWithTag("pod-pass-0-pick").assertTextContains("Pick 1").performClick()
        compose.onNodeWithText("Pass 1 · Pick").assertIsDisplayed()
        val field = compose.onNode(hasSetTextAction())
        for (invalid in listOf("", "0", "30")) {
            field.performTextReplacement(invalid)
            compose.onNodeWithText("Save").assertIsNotEnabled()
        }
        field.performTextReplacement("3")
        compose.onNodeWithText("Save").performClick()
        compose.runOnIdle { assertEquals(DraftPass(3, 1), settings.passes.first()) }
        compose.onNodeWithTag("pod-pass-6-burn").performScrollTo().performClick()
        compose.onNode(hasSetTextAction()).performTextReplacement("0")
        compose.onNodeWithText("Save").performClick()
        compose.onNodeWithTag("pod-pass-add").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(listOf(DraftPass(1, 0), DraftPass(1, 0)), settings.passes.takeLast(2)) }
        compose.onNodeWithText("Passes (8)").assertExists()
        compose.onNodeWithTag("pod-pass-1-remove").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(listOf(DraftPass(3, 1)) + List(4) { DraftPass(1, 1) } + List(2) { DraftPass(1, 0) }, settings.passes) }
        compose.runOnIdle { settings = settings.copy(passes = listOf(DraftPass(2, 0))) }
        compose.onNodeWithTag("pod-pass-0-remove").assertIsNotEnabled()
    }

    // DRF-263: members read the passes without buttons
    @Test fun membersReadThePasses() {
        compose.setContent { ScreenTheme { Column {
            PodSettingsFields(DraftSettings.customBooster().copy(passes = listOf(DraftPass(2, 1), DraftPass(1, 0))), 2, false) { }
        } } }
        compose.onNodeWithText("Pass 1 · Pick 2 · Burn 1").assertIsDisplayed()
        compose.onNodeWithText("Pass 2 · Pick 1").assertIsDisplayed()
        compose.onNodeWithTag("pod-pass-add").assertDoesNotExist()
        compose.onNodeWithTag("pod-pass-0-pick").assertDoesNotExist()
    }

    // DRF-264: an invitation to a Custom booster pod reads its passes; others read as before
    @Test fun invitationsTellTheirPasses() {
        val bob = AccountIdentity("bob", "Bob")
        compose.setContent { ScreenTheme { PodsList(PodState(loaded = true, invitations = listOf(
            PodInvitation("p1", "Burn night", bob, "2026-11-07T00:00:00Z", DraftMode.CUSTOM_BOOSTER,
                listOf(DraftPass(2, 1)) + List(6) { DraftPass(1, 1) }),
            PodInvitation("p2", "Plain night", bob, "2026-11-06T00:00:00Z"))), "alice", {}, { _, _ -> }, {}) } }
        compose.onNodeWithText("Custom booster: Pick 2 · Burn 1, Pick 1 · Burn 1 ×6").assertIsDisplayed()
        compose.onAllNodesWithTag("pod-invitation-passes").assertCountEquals(1)
    }
}
