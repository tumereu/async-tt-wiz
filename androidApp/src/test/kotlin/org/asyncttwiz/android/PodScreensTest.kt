package org.asyncttwiz.android

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.runtime.*
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.AnnotatedString
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

    @Test fun clearingNumberThenTypingReplacesValueAndPreservesCursorAcrossUpdates() {
        var value by mutableIntStateOf(18)
        val changes = mutableListOf<Int>()
        compose.setContent { ScreenTheme { Column {
            PodNumber("Grids", value, true) { value = it; changes += it }
            PodNumber("Grids open at once", 1, true) {}
        } } }
        val field = compose.onNodeWithText("Grids")
        field.performTextClearance()
        field.assertTextEquals("Grids", "")
        compose.runOnIdle { assertEquals(18, value); assertTrue(changes.isEmpty()) }
        field.performTextInput("3")
        field.assertTextEquals("Grids", "3")
        field.performTextInput("4")
        field.assertTextEquals("Grids", "34")
        field.performTextInputSelection(TextRange(1))
        field.performTextInput("5")
        field.assertTextEquals("Grids", "354")
        field.assert(SemanticsMatcher.expectValue(SemanticsProperties.TextSelectionRange, TextRange(2)))
        compose.runOnIdle { assertEquals(listOf(3, 34, 354), changes) }
        compose.onNodeWithText("Grids open at once").assertTextEquals("Grids open at once", "1")
    }

    @Test fun emptyAndOverflowNumbersRestoreLastValidOnBlurAndIdleFieldsFollowSettings() {
        var value by mutableIntStateOf(18)
        val changes = mutableListOf<Int>()
        compose.setContent { ScreenTheme { Column {
            PodNumber("Maximum sideboard cards", value, true) { value = it; changes += it }
            PodNumber("Other", 1, true) {}
        } } }
        val field = compose.onNodeWithText("Maximum sideboard cards")
        val other = compose.onNodeWithText("Other")
        field.performTextClearance()
        other.performClick()
        field.assertTextEquals("Maximum sideboard cards", "18")
        field.performTextReplacement("0")
        field.performTextReplacement("999999999999999999999999999")
        other.performClick()
        field.assertTextEquals("Maximum sideboard cards", "0")
        compose.runOnIdle { assertEquals(listOf(0), changes); value = 27 }
        field.assertTextEquals("Maximum sideboard cards", "27")
        field.performTextClearance()
        compose.runOnIdle { value = 42 }
        field.assertTextEquals("Maximum sideboard cards", "")
        other.performClick()
        field.assertTextEquals("Maximum sideboard cards", "42")
    }

    @Test fun setPickerSearchesNamesCodesAndDatesNewestFirstAndRedownloadsCachedRows() {
        var state by mutableStateOf(PodSetPickerState(open = true, loaded = true, rows = listOf(
            PodSetRow(DraftableSet("OLD", "Older set", "2000-01-01", 100), false),
            PodSetRow(DraftableSet("NEW", "Newer set", "2026-10-01", 200), true))))
        var chosen: String? = null
        var redownload: String? = null
        compose.setContent { ScreenTheme { Column {
            PodSetPickerContent(state, { state = state.copy(query = it) }, { chosen = it }, { redownload = it }, {})
        } } }
        val newer = compose.onNodeWithText("Newer set").fetchSemanticsNode().boundsInRoot
        val older = compose.onNodeWithText("Older set").fetchSemanticsNode().boundsInRoot
        assertTrue(newer.top < older.top)
        compose.onNodeWithContentDescription("Redownload NEW").performClick()
        assertEquals("NEW", redownload)
        compose.onNodeWithContentDescription("Redownload OLD").assertDoesNotExist()
        val search = compose.onNodeWithText("Search name, code or release date")
        search.performTextInput("oLd")
        compose.onNodeWithText("Newer set").assertDoesNotExist()
        compose.onNodeWithText("Older set").assertExists()
        search.performTextReplacement("2026-10")
        compose.onNodeWithText("Older set").assertDoesNotExist()
        compose.onNodeWithText("NEW · 2026-10-01").assertExists()
        search.performTextReplacement("newer")
        compose.onNodeWithText("Newer set").performClick()
        assertEquals("NEW", chosen)
        compose.runOnIdle { state = state.copy(busy = true, status = "Downloading NEW…") }
        compose.onNodeWithText("Downloading NEW…").assertExists()
        compose.onNodeWithContentDescription("Redownload NEW").assertIsNotEnabled()
        compose.runOnIdle { state = state.copy(busy = false, error = "Download failed.") }
        compose.onNodeWithText("Retry").assertExists()
    }

    @Test fun sourceLabelsAndUnlimitedSupplySurviveRecipeAndApproximateChanges() {
        var source by mutableStateOf(PodSource(kind = PodSourceKind.SET, setCode = "TST", setName = "Test set",
            boosterKind = SetBoosterKind.RECIPE_BASED, boosterName = "play"))
        compose.setContent { ScreenTheme { Column {
            PodSourceSummary(source); PodSourceSupply(source, 90)
        } } }
        compose.onNodeWithText("Test set (TST) · Play booster").assertIsDisplayed()
        compose.onNodeWithText("Unlimited (set boosters)").assertIsDisplayed()
        compose.runOnIdle { source = source.copy(boosterName = "draft") }
        compose.onNodeWithText("Test set (TST) · Draft booster").assertIsDisplayed()
        compose.runOnIdle { source = source.copy(boosterKind = SetBoosterKind.APPROXIMATE, boosterName = null) }
        compose.onNodeWithText("Test set (TST) · Approximate packs").assertIsDisplayed()
        compose.runOnIdle { source = PodSource("My cube", 540) }
        compose.onNodeWithText("My cube · 540 copies").assertIsDisplayed()
        compose.onNodeWithText("90 / 540 cards used").assertIsDisplayed()
        compose.onNodeWithText("Unlimited (set boosters)").assertDoesNotExist()
    }

    @Test fun recipeSetsHidePackSizeAndAllSetsHideColorBalancing() {
        var source by mutableStateOf(PodSource(kind = PodSourceKind.SET, boosterKind = SetBoosterKind.RECIPE_BASED))
        compose.setContent { ScreenTheme { Column { PodSettingsFields(DraftSettings(), 2, true, source) {} } } }
        compose.onNodeWithText("Cards per pack").assertDoesNotExist()
        compose.onNodeWithText("Advanced ▾").performClick()
        compose.onNodeWithText("Color balancing").assertDoesNotExist()
        compose.runOnIdle { source = source.copy(boosterKind = SetBoosterKind.APPROXIMATE) }
        compose.onNodeWithText("Cards per pack").assertExists()
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
        compose.onNodeWithText("Synergy deck").assertIsDisplayed()
        compose.onNodeWithText("Submit your deck to challenge other members.").assertDoesNotExist()
    }

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

    @Test fun modeChipsSwitchToGridDefaultsAndHideBoosterSettingsIncludingAdvanced() {
        var settings by mutableStateOf(DraftSettings())
        compose.setContent { ScreenTheme { Column { PodSettingsFields(settings, 2, true) { settings = it(settings) } } } }
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

    @Test fun twoSetupFieldCallbacksBeforeRecompositionPreserveBothEdits() {
        var settings by mutableStateOf(DraftSettings())
        compose.setContent { ScreenTheme { Column {
            PodSettingsFields(settings, 2, true) { settings = it(settings) }
        } } }
        val packs = compose.onNodeWithText("Packs per player").fetchSemanticsNode().config[SemanticsActions.SetText].action!!
        val size = compose.onNodeWithText("Cards per pack").fetchSemanticsNode().config[SemanticsActions.SetText].action!!
        compose.runOnIdle {
            packs(AnnotatedString("4"))
            size(AnnotatedString("12"))
            assertEquals(4, settings.packsPerPlayer)
            assertEquals(12, settings.cardsPerPack)
        }
        compose.onNodeWithText("Packs per player").assertTextEquals("Packs per player", "4")
        compose.onNodeWithText("Cards per pack").assertTextEquals("Cards per pack", "12")
    }
}
