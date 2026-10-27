package org.asyncttwiz.android

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.runtime.*
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.semantics.SemanticsProperties
import org.asyncttwiz.protocol.*
import org.asyncttwiz.storage.DraftableSet
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

    @Test fun numberPopupKeepsEditsLocalUntilSaveAndRejectsEmptyOverflowAndOutOfRange() {
        var saved: Int? = null
        compose.setContent { ScreenTheme {
            PodSettingPopup(PodSetting.KEEP, DraftSettings(cardsPerPack = 5, burn = 2), {}) { saved = it }
        } }
        val field = compose.onNode(hasSetTextAction())
        compose.onNodeWithText("1–3").assertExists()
        compose.onNodeWithText("Pack size − burn: 3").assertExists()
        for (invalid in listOf("", "0", "4", "999999999999999999999999")) {
            field.performTextReplacement(invalid)
            compose.onNodeWithText("Save").assertIsNotEnabled()
        }
        field.performTextReplacement("3")
        assertEquals(null, saved)
        compose.onNodeWithText("Save").assertIsEnabled().performClick()
        assertEquals(3, saved)
    }

    @Test fun sideboardPopupUsesSquareUnlimitedToggleAndPreservesFiniteInput() {
        var saved: Int? = 10
        compose.setContent { ScreenTheme {
            PodSettingPopup(PodSetting.SIDEBOARD, DraftSettings(sideboardLimit = 7), {}) { saved = it }
        } }
        val field = compose.onNodeWithText("0–2147483647")
        field.performTextReplacement("0")
        compose.onNodeWithText("Save").performClick()
        assertEquals(0, saved)
        val unlimited = compose.onNodeWithContentDescription("Unlimited sideboard")
        unlimited.performClick().assertIsSelected()
        field.assertIsNotEnabled()
        compose.onNodeWithText("Save").performClick()
        assertEquals(null, saved)
        unlimited.performClick().assertIsNotSelected()
        field.assertIsEnabled().assertTextEquals("0–2147483647", "0")
    }

    @Test fun setPickerSearchesNamesCodesAndDatesNewestFirstAndRedownloadsCachedRows() {
        var state by mutableStateOf(PodSetPickerState(open = true, loaded = true, rows = listOf(
            PodSetRow(DraftableSet("OLD", "Older set", "2000-01-01", 100, "core"), false),
            PodSetRow(DraftableSet("NEW", "Newer set", "2026-10-01", 200, "expansion"), true),
            PodSetRow(DraftableSet("CMD", "Commander set", "2027-10-01", 200, "commander"), false))))
        var chosen: String? = null
        var redownload: String? = null
        compose.setContent { ScreenTheme { Column {
            PodSetPickerContent(state, { state = state.copy(query = it) }, { chosen = it }, { redownload = it }, {})
        } } }
        val newer = compose.onNodeWithText("Newer set").fetchSemanticsNode().boundsInRoot
        val older = compose.onNodeWithText("Older set").fetchSemanticsNode().boundsInRoot
        assertTrue(newer.top < older.top)
        compose.onNodeWithText("Commander set").assertDoesNotExist()
        compose.onNodeWithContentDescription("Redownload NEW").performClick()
        assertEquals("NEW", redownload)
        compose.onNodeWithContentDescription("Redownload OLD").assertDoesNotExist()
        val search = compose.onNodeWithText("Search name, code or release date")
        search.performTextInput("oLd")
        compose.onNodeWithText("Newer set").assertDoesNotExist()
        compose.onNodeWithText("Older set").assertExists()
        search.performTextReplacement("2026-10")
        compose.onNodeWithText("Older set").assertDoesNotExist()
        compose.onNodeWithText("Newer set").assertExists()
        search.performTextReplacement("newer")
        compose.onNodeWithText("Newer set").performClick()
        assertEquals("NEW", chosen)
        compose.runOnIdle { state = state.copy(busy = true, status = "Downloading NEW…") }
        compose.onNodeWithText("Downloading NEW…").assertDoesNotExist()
        compose.onNodeWithContentDescription("Redownload NEW").assertIsNotEnabled()
        compose.runOnIdle { state = state.copy(busy = false, error = "Download failed.") }
        compose.onNodeWithContentDescription("Retry source").assertExists()
    }

    @Test fun sourceRowShowsOnlyTheNameWhenBoosterOrSupplyChanges() {
        var source by mutableStateOf(PodSource(kind = PodSourceKind.SET, setCode = "TST", setName = "Test set",
            boosterKind = SetBoosterKind.RECIPE_BASED, boosterName = "play"))
        compose.setContent { ScreenTheme { Column {
            PodSettingsForm("Pod", DraftSettings(), source, 2, true, {}, {}, {})
        } } }
        compose.onNodeWithText("Test set").assertIsDisplayed()
        compose.onNodeWithContentDescription("Change source").assertIsDisplayed()
        compose.onNodeWithText("Unlimited (set boosters)").assertDoesNotExist()
        compose.runOnIdle { source = source.copy(boosterName = "draft") }
        compose.onNodeWithText("Test set").assertIsDisplayed()
        compose.runOnIdle { source = source.copy(boosterKind = SetBoosterKind.APPROXIMATE, boosterName = null) }
        compose.onNodeWithText("Test set").assertIsDisplayed()
        compose.runOnIdle { source = PodSource("My cube", 540) }
        compose.onNodeWithText("My cube").assertIsDisplayed()
        compose.onNodeWithText("Test set").assertDoesNotExist()
    }

    @Test fun recipeSetsHidePackSizeAndAllSetsHideColorBalancing() {
        var source by mutableStateOf(PodSource(kind = PodSourceKind.SET, boosterKind = SetBoosterKind.RECIPE_BASED))
        compose.setContent { ScreenTheme { Column { PodSettingsFields(DraftSettings(), 2, true, source) {} } } }
        compose.onNodeWithText("Pack size").assertDoesNotExist()
        compose.onNodeWithText("Advanced ▾").assertDoesNotExist()
        compose.onNodeWithText("Color balancing").assertDoesNotExist()
        compose.runOnIdle { source = source.copy(boosterKind = SetBoosterKind.APPROXIMATE) }
        compose.onNodeWithText("Pack size").assertExists()
        compose.onNodeWithText("Color balancing").assertDoesNotExist()
        compose.runOnIdle { source = PodSource("Cube", 540) }
        compose.onNodeWithText("Color balancing").assertExists()
    }

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

    @Test fun retainedChallengeRetryIsReachableEvenWithNoPodsAndDisabledWhileSending() {
        var state by mutableStateOf(LobbyState(retryPodChallengeName = "Original game"))
        var retries = 0
        compose.setContent { ScreenTheme { Column {
            PodChallengeRetry(state) { retries++ }
            PodsList(PodState(loaded = true), {}, { _, _ -> }, {})
        } } }
        compose.onNodeWithText("MY PODS (0)").assertIsDisplayed()
        compose.onNodeWithText("Challenge not confirmed: Original game").assertIsDisplayed()
        compose.onNodeWithText("Retry challenge").assertIsEnabled().performClick()
        compose.runOnIdle { assertEquals(1, retries); state = state.copy(mutating = true) }
        compose.onNodeWithText("Retry challenge").assertIsNotEnabled()
        compose.runOnIdle { state = state.copy(mutating = false, retryPodChallengeName = null) }
        compose.onNodeWithText("Retry challenge").assertDoesNotExist()
    }

    @Test fun modeChipsSwitchToGridDefaultsAndHideBoosterSettingsIncludingAdvanced() {
        var settings by mutableStateOf(DraftSettings())
        compose.setContent { ScreenTheme { Column { PodSettingsFields(settings, 2, true) { settings = it(settings) } } } }
        compose.onNodeWithText("Booster").assertIsSelected()
        compose.onNodeWithText("Glimpse").assertExists()
        compose.onNodeWithText("Grid").assertExists().performClick().assertIsSelected()
        compose.runOnIdle { assertEquals(DraftSettings.grid(), settings) }
        compose.onNodeWithText("18").assertExists()
        compose.onNodeWithText("Grids").assertExists()
        compose.onNodeWithText("Packs").assertDoesNotExist()
        compose.onNodeWithText("Pack size").assertDoesNotExist()
        compose.onNodeWithText("Advanced ▾").assertDoesNotExist()
        compose.onNodeWithText("Open grids").assertExists()
        listOf("Keep", "Burn", "Discard", "Open packs", "Color balancing").forEach {
            compose.onNodeWithText(it).assertDoesNotExist()
        }
        compose.onNode(isToggleable()).assertIsEnabled().performClick()
        compose.runOnIdle { assertTrue(settings.twoPicks) }
        compose.onNodeWithText("Glimpse").performClick()
        compose.runOnIdle { assertEquals(DraftSettings.glimpse(), settings) }
        compose.onNodeWithText("Grids").assertDoesNotExist()
        compose.onNodeWithText("Two picks each").assertDoesNotExist()
        compose.onNodeWithText("Pack size").assertExists()
    }

    @Test fun twoPicksIsOnlyEnabledWithExactlyTwoMembersAndCreatorPermission() {
        var members by mutableIntStateOf(3)
        var enabled by mutableStateOf(true)
        compose.setContent { ScreenTheme { Column { PodSettingsFields(DraftSettings.grid(), members, enabled) {} } } }
        compose.onNode(isToggleable()).assertIsNotEnabled()
        compose.onNodeWithText("Two picks each requires exactly 2 members.").assertDoesNotExist()
        compose.runOnIdle { members = 2 }
        compose.onNode(isToggleable()).assertIsEnabled()
        compose.runOnIdle { enabled = false }
        compose.onNode(isToggleable()).assertIsNotEnabled()
        compose.onNodeWithText("Grid").assertIsNotEnabled()
    }

    @Test fun sharedFormShowsEverySettingAsASquareTileWithoutAdvancedToggle() {
        var settings by mutableStateOf(DraftSettings())
        compose.setContent { ScreenTheme { Column {
            PodSettingsForm("New pod", settings, null, 2, true, {}, {}, { settings = it(settings) })
        } } }
        compose.onNodeWithText("No source").assertIsDisplayed()
        compose.onNodeWithContentDescription("Change source").assertIsDisplayed()
        for (label in listOf("Packs", "Pack size", "Keep", "Burn", "Discard", "Open packs", "Sideboard")) {
            val bounds = compose.onNodeWithText(label).fetchSemanticsNode().boundsInRoot
            assertEquals(bounds.width, bounds.height)
        }
        compose.onNodeWithText("Advanced ▾").assertDoesNotExist()
        compose.onNodeWithText("∞").assertExists()
    }

    @Test fun sharedPlayerPickerHasAGapCentredEmptyStateAndSearchSpinner() {
        val fixture = ScreenFixtures()
        fixture.lobby.seed(LobbyState(loaded = true))
        try {
            compose.setContent { ScreenTheme { PlayerPicker(fixture.lobby) {} } }
            compose.onNodeWithText("Choose player").assertDoesNotExist()
            compose.onNodeWithText("Back").assertDoesNotExist()
            val tab = compose.onNodeWithText("Contacts").fetchSemanticsNode().boundsInRoot
            val field = compose.onNodeWithContentDescription("Filter contacts").fetchSemanticsNode().boundsInRoot
            assertTrue(field.top - tab.bottom >= 16)
            val empty = compose.onNodeWithText("No players found").fetchSemanticsNode().boundsInRoot
            val root = compose.onRoot().fetchSemanticsNode().boundsInRoot
            assertEquals(root.center.x, empty.center.x, 1f)
            assertEquals((field.bottom + root.bottom) / 2, empty.center.y, 1f)
            compose.runOnIdle { fixture.lobby.seed(LobbyState(loaded = true, contactsTab = ContactsTab.SEARCH, searching = true)) }
            compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.ProgressBarRangeInfo)).assertCountEquals(1)
            compose.onNodeWithText("Loading players…").assertDoesNotExist()
            compose.onNodeWithText("No players found").assertDoesNotExist()
        } finally { fixture.lobby.close() }
    }

    @Test fun podBackgroundRefreshDoesNotAddALoaderOrMoveTheList() {
        val pod = PodSummary("pod", "My draft", PodPhase.SETUP, alice, listOf(alice), PodAttention.NONE, 0, "now")
        var state by mutableStateOf(PodState(loaded = true, pods = listOf(pod)))
        compose.setContent { ScreenTheme { PodsList(state, {}, { _, _ -> }, {}) } }
        val before = compose.onNodeWithText("My draft").fetchSemanticsNode().boundsInRoot
        compose.runOnIdle { state = state.copy(loading = true, pending = 1) }
        assertEquals(before, compose.onNodeWithText("My draft").fetchSemanticsNode().boundsInRoot)
        compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.ProgressBarRangeInfo)).assertCountEquals(0)
        compose.onNodeWithText("Uploading set source and saving pod…").assertDoesNotExist()
    }
}
