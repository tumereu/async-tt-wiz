package org.asyncttwiz.android

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.runtime.*
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import org.asyncttwiz.protocol.*
import org.asyncttwiz.storage.DraftableSet
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertTrue
import kotlin.test.assertFalse
import kotlin.test.assertEquals
import kotlin.test.assertNull

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w412dp-h892dp-port-mdpi")
class PodScreensTest {
    @get:Rule val compose = createComposeRule()
    private val alice = AccountIdentity("alice", "Alice")
    private val bob = AccountIdentity("bob", "Bob")

    // DRF-022
    @Test fun numberPopupKeepsEditsLocalUntilSaveAndRejectsEmptyOverflowAndOutOfRange() {
        var saved: Int? = null
        compose.setContent { ScreenTheme {
            PodSettingPopup(PodSetting.KEEP, DraftSettings(cardsPerPack = 5, burn = 2), {}) { saved = it }
        } }
        val field = compose.onNode(hasSetTextAction())
        compose.onNodeWithText("1–3").assertExists()
        compose.onNodeWithText("Pack size − burn: 3").assertDoesNotExist()
        for (invalid in listOf("", "0", "4", "999999999999999999999999")) {
            field.performTextReplacement(invalid)
            compose.onNodeWithText("Save").assertIsNotEnabled()
        }
        field.performTextReplacement("3")
        assertEquals(null, saved)
        compose.onNodeWithText("Save").assertIsEnabled().performClick()
        assertEquals(3, saved)
    }

    // DRF-025
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

    // DRF-088
    @Test fun setPickerSearchesNamesCodesAndDatesNewestFirstAndRedownloadsCachedRows() {
        var state by mutableStateOf(PodSetPickerState(open = true, loaded = true, rows = listOf(
            PodSetRow(DraftableSet("OLD", "Older set", "2000-01-01", 100, "core"), false),
            PodSetRow(DraftableSet("NEW", "Newer set", "2026-10-01", 200, "expansion"), true),
            PodSetRow(DraftableSet("CMD", "Commander set", "2027-10-01", 200, "commander"), false),
            PodSetRow(DraftableSet("FUT", "Future set", java.time.LocalDate.now().plusDays(1).toString(), 200, "expansion"), false),
            PodSetRow(DraftableSet("TOD", "Today's set", java.time.LocalDate.now().toString(), 200, "expansion"), false))))
        var chosen: String? = null
        var redownload: String? = null
        compose.setContent { ScreenTheme { Column {
            PodSetPickerContent(state, { state = state.copy(query = it) }, { chosen = it }, { redownload = it }, {})
        } } }
        val newer = compose.onNodeWithText("Newer set").fetchSemanticsNode().boundsInRoot
        val older = compose.onNodeWithText("Older set").fetchSemanticsNode().boundsInRoot
        assertTrue(newer.top < older.top)
        compose.onNodeWithText("Commander set").assertDoesNotExist()
        compose.onNodeWithText("Future set").assertDoesNotExist()
        compose.onNodeWithText("Today's set").assertIsDisplayed()
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

    @Test fun setWithMissingTypeStaysVisibleAndSelectableDuringBackgroundRefresh() {
        val state = PodSetPickerState(open = true, loaded = true, busy = true, rows = listOf(
            PodSetRow(DraftableSet("OLD", "Legacy set", "2000-01-01", 100), true)))
        var chosen: String? = null
        compose.setContent { ScreenTheme { Column {
            PodSetPickerContent(state, {}, { chosen = it }, {}, {})
        } } }
        compose.onNodeWithText("No sets found").assertDoesNotExist()
        compose.onNodeWithContentDescription("Retry source").assertDoesNotExist()
        compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.ProgressBarRangeInfo)).assertCountEquals(0)
        compose.onNodeWithText("Legacy set").assertIsEnabled().performClick()
        assertEquals("OLD", chosen)
    }

    // DRF-018
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

    // DRF-024, DRF-026
    @Test fun recipeSetsHidePackSizeAndAllSetsHideColorBalancing() {
        var source by mutableStateOf(PodSource(kind = PodSourceKind.SET, boosterKind = SetBoosterKind.RECIPE_BASED))
        var settings by mutableStateOf(DraftSettings())
        compose.setContent { ScreenTheme { Column { PodSettingsFields(settings, 2, true, source) { settings = it(settings) } } } }
        compose.onNodeWithText("Pack size").assertDoesNotExist()
        compose.onNodeWithText("Advanced ▾").assertDoesNotExist()
        compose.onNodeWithText("Color balancing").assertDoesNotExist()
        compose.runOnIdle { source = source.copy(boosterKind = SetBoosterKind.APPROXIMATE) }
        compose.onNodeWithText("Pack size").assertExists()
        compose.onNodeWithText("Color balancing").assertDoesNotExist()
        compose.runOnIdle { source = PodSource("Cube", 540) }
        compose.onNodeWithText("Color balancing").assertExists()
        // A square tile like the numeric settings: a tap toggles it.
        compose.onNode(isToggleable() and hasText("Color balancing")).assertIsOn().performClick()
        compose.runOnIdle { assertEquals(false, settings.colorBalance) }
        compose.onNode(isToggleable() and hasText("Color balancing")).assertIsOff()
        compose.onNodeWithText("Off").assertExists()
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

    // DRF-012
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

    // DRF-121
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
        compose.onNodeWithText("Synergy deck").assertDoesNotExist()
        compose.onNodeWithText("Submit your deck to challenge other members.").assertDoesNotExist()
    }

    // SYS-024
    @Test fun challengeErrorsDoNotShiftContentAndRetainedRetryOutlastsTransientErrorEvenWithoutPods() {
        var state by mutableStateOf(LobbyState())
        var retries = 0
        compose.setContent { ScreenTheme { Box(Modifier.fillMaxSize()) {
            PodsList(PodState(loaded = true), {}, { _, _ -> }, {})
            PodChallengeFeedback(state, { retries++ }, { state = state.copy(error = null) }, Modifier.matchParentSize())
        } } }
        compose.onNodeWithText("MY PODS (0)").assertIsDisplayed()
        val before = compose.onNodeWithText("MY PODS (0)").getUnclippedBoundsInRoot()
        compose.runOnIdle { state = state.copy(error = "Could not send challenge", retryPodChallengeName = "Original game") }
        compose.onNodeWithText("Could not send challenge").assertIsDisplayed()
        assertEquals(before, compose.onNodeWithText("MY PODS (0)").getUnclippedBoundsInRoot())
        compose.mainClock.advanceTimeBy(7_100)
        compose.onNodeWithText("Could not send challenge").assertDoesNotExist()
        compose.onNodeWithContentDescription("Retry challenge").assertIsEnabled().performClick()
        compose.runOnIdle { assertEquals(1, retries); state = state.copy(mutating = true) }
        compose.onNodeWithContentDescription("Retry challenge").assertIsNotEnabled()
        compose.runOnIdle { state = state.copy(mutating = false, retryPodChallengeName = null) }
        compose.onNodeWithContentDescription("Retry challenge").assertDoesNotExist()
    }

    @Test fun rejectedChallengeShowsDismissibleErrorWithoutRetainedRetryAndCanFailAgain() {
        val error = lobbyError(LobbyFailure("opponent_not_member"))
        var state by mutableStateOf(LobbyState(error = error))
        compose.setContent { ScreenTheme {
            PodChallengeFeedback(state, {}, { state = state.copy(error = null) }, Modifier.fillMaxSize())
        } }
        compose.onNodeWithText(error).assertIsDisplayed()
        compose.onNodeWithContentDescription("Retry challenge").assertDoesNotExist()
        compose.onNodeWithContentDescription("Dismiss message").performClick()
        compose.onNodeWithText(error).assertDoesNotExist()
        compose.runOnIdle { state = state.copy(error = error) }
        compose.onNodeWithText(error).assertIsDisplayed()
    }

    // DRF-020
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
        compose.onNode(twoPicks).assertIsEnabled().performClick()
        compose.runOnIdle { assertTrue(settings.twoPicks) }
        compose.onNodeWithText("Glimpse").performClick()
        compose.runOnIdle { assertEquals(DraftSettings.glimpse(), settings) }
        compose.onNodeWithText("Grids").assertDoesNotExist()
        compose.onNodeWithText("Two picks each").assertDoesNotExist()
        compose.onNodeWithText("Pack size").assertExists()
    }

    // DRF-028
    @Test fun twoPicksIsOnlyEnabledWithExactlyTwoMembersAndCreatorPermission() {
        var members by mutableIntStateOf(3)
        var enabled by mutableStateOf(true)
        compose.setContent { ScreenTheme { Column { PodSettingsFields(DraftSettings.grid(), members, enabled) {} } } }
        compose.onNode(twoPicks).assertIsNotEnabled()
        compose.onNodeWithText("Two picks each requires exactly 2 members.").assertDoesNotExist()
        compose.runOnIdle { members = 2 }
        compose.onNode(twoPicks).assertIsEnabled()
        compose.runOnIdle { enabled = false }
        compose.onNode(twoPicks).assertIsNotEnabled()
        compose.onNodeWithText("Grid").assertIsNotEnabled()
    }

    private val twoPicks = isToggleable() and !hasText("Fresh boosters")
    private val freshBoosters = isToggleable() and hasText("Fresh boosters")

    // DRF-027
    @Test fun freshBoostersIsAlwaysShownButOnlyTogglesForSetGrids() {
        var source by mutableStateOf(PodSource(kind = PodSourceKind.SET, boosterKind = SetBoosterKind.RECIPE_BASED))
        var settings by mutableStateOf(DraftSettings())
        var enabled by mutableStateOf(true)
        compose.setContent { ScreenTheme { Column { PodSettingsFields(settings, 2, enabled, source) { settings = it(settings) } } } }
        // Booster mode: shown, greyed and inert.
        compose.onNode(freshBoosters).assertExists().assertIsOn().assertIsNotEnabled()
        compose.onNodeWithText("Grid").performClick()
        compose.runOnIdle { assertTrue(settings.freshBoosters) }
        compose.onNode(freshBoosters).assertIsEnabled().performClick()
        compose.runOnIdle { assertFalse(settings.freshBoosters) }
        compose.onNode(freshBoosters).assertIsOff()
        // Switching modes keeps the choice; a cube source makes it inapplicable again.
        compose.onNodeWithText("Glimpse").performClick()
        compose.onNodeWithText("Grid").performClick()
        compose.runOnIdle { assertFalse(settings.freshBoosters) }
        compose.runOnIdle { source = PodSource("Cube", 540) }
        compose.onNode(freshBoosters).assertExists().assertIsNotEnabled()
        compose.runOnIdle { source = PodSource(kind = PodSourceKind.SET); enabled = false }
        compose.onNode(freshBoosters).assertExists().assertIsNotEnabled()
    }

    // DRF-018, DRF-022
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

    // DRF-078
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

    // DRF-071
    @Test fun waitingTextNamesPlayersHoldingPacksAndGridRoundsWhenKnown() {
        val carol = AccountIdentity("carol", "Carol")
        val booster = PodDetail("pod", "Pod", PodPhase.DRAFTING, alice, DraftSettings(), PodSource("Cube", 540),
            DraftSupply(90, 540), listOf(PodMember(alice), PodMember(bob, packsWaiting = 2), PodMember(carol)),
            emptyList(), updatedAt = "now")
        assertEquals("Waiting for Bob", podWaitingText(booster, alice.accountId))
        val withRound = booster.copy(draft = org.asyncttwiz.protocol.DraftPrivateView(alice.accountId, null, 0, emptyList(), round = 3, rounds = 6))
        assertEquals("All your picks are done\nBob is finishing\nRound 3 of 6", podWaitingText(withRound, alice.accountId))
        val incoming = withRound.copy(draft = withRound.draft!!.copy(incoming = 3))
        assertEquals("Waiting for Bob\n3 packs will come back to you\nRound 3 of 6", podWaitingText(incoming, alice.accountId))
        assertEquals("Waiting for Bob\n1 pack will come back to you\nRound 3 of 6",
            podWaitingText(incoming.copy(draft = incoming.draft!!.copy(incoming = 1)), alice.accountId))
        val idle = withRound.copy(members = withRound.members.map { it.copy(packsWaiting = 0) })
        assertEquals("All your picks are done\nOther players are finishing\nRound 3 of 6", podWaitingText(idle, alice.accountId))
        assertEquals("Waiting for other players\n2 packs will come back to you\nRound 3 of 6",
            podWaitingText(idle.copy(draft = idle.draft!!.copy(incoming = 2)), alice.accountId))
        val pair = withRound.copy(members = withRound.members.map { if (it.account == carol) it.copy(packsWaiting = 1) else it })
        assertEquals("All your picks are done\nBob, Carol are finishing\nRound 3 of 6", podWaitingText(pair, alice.accountId))
        val both = booster.copy(members = booster.members.map { if (it.account == carol) it.copy(packsWaiting = 1) else it })
        assertEquals("Waiting for Bob, Carol", podWaitingText(both, alice.accountId))
        val nobody = booster.copy(members = booster.members.map { it.copy(packsWaiting = 0) })
        assertEquals("Waiting for other players", podWaitingText(nobody, alice.accountId))
        val board = GridPublicBoard("grid", 3, List(9) { null }, bob.accountId)
        val grid = booster.copy(settings = DraftSettings.grid(), members = listOf(PodMember(alice), PodMember(bob, packsWaiting = 1)),
            grid = org.asyncttwiz.protocol.PodGridView(GridPublicView(2, 6, false, listOf(alice.accountId, bob.accountId), listOf(board), emptyList()),
                GridPrivateView(alice.accountId, emptyList(), null)))
        assertEquals("Waiting for Bob · Round 2 of 6", podWaitingText(grid, alice.accountId))
        // Members open grid rounds early: the caller's own round wins, and the server's counts say who still picks.
        val early = grid.copy(grid = grid.grid!!.copy(mine = GridPrivateView(alice.accountId, emptyList(), null, round = 3, rounds = 6)))
        assertEquals("Waiting for Bob · Round 3 of 6", podWaitingText(early, alice.accountId))
    }

    // DRF-057
    @Test fun pickOrientationNamesRoundPickAndWhoPassedThePack() {
        val pack = org.asyncttwiz.protocol.DraftPrivatePack("pack", 1, emptyList(), pick = 2, passedBy = bob.accountId)
        val detail = PodDetail("pod", "Pod", PodPhase.DRAFTING, alice, DraftSettings(), PodSource("Cube", 540),
            DraftSupply(90, 540), listOf(PodMember(alice), PodMember(bob)), emptyList(), updatedAt = "now",
            draft = org.asyncttwiz.protocol.DraftPrivateView(alice.accountId, pack, 0, emptyList(), round = 2, rounds = 6))
        assertEquals("Round 2/6 · Pick 2 · from Bob", podPickOrientationText(detail))
        val own = detail.copy(draft = detail.draft!!.copy(head = pack.copy(pick = 1, passedBy = null)))
        assertEquals("Round 2/6 · Pick 1 · your pack", podPickOrientationText(own))
        val departed = detail.copy(draft = detail.draft!!.copy(head = pack.copy(passedBy = "gone")))
        assertEquals("Round 2/6 · Pick 2 · from another player", podPickOrientationText(departed))
        assertNull(podPickOrientationText(detail.copy(draft = detail.draft!!.copy(head = null))))
    }
}
