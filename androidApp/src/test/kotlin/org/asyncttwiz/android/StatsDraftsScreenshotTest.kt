package org.asyncttwiz.android

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import org.asyncttwiz.android.MatchHistoryFixtures.alex
import org.asyncttwiz.android.MatchHistoryFixtures.morgan
import org.asyncttwiz.android.MatchHistoryFixtures.records
import org.asyncttwiz.android.MatchHistoryFixtures.riley
import org.asyncttwiz.appui.AppPlatform
import org.asyncttwiz.protocol.*
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Stats' Drafts tab (owner, 2026-11-26; LOB-280, LOB-281): the drafts the server keeps, listed and opened into their draft
 * history with card ratings, also after their pod is gone. Invented drafts, cards and ratings; nothing is sent.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w412dp-h892dp-port-xhdpi")
class StatsDraftsScreenshotTest {
    @get:Rule val compose = createComposeRule()
    private val fixtures = ScreenFixtures()
    private val history get() = fixtures.lobby.history
    private val preferences get() = AppPlatform.current.preferences("pod-pick")

    @Before fun pinClock() { AppClock.pinned = Clock.fixed(Instant.parse("2026-11-26T20:00:00Z"), ZoneOffset.UTC) }
    @Before @After fun reset() {
        preferences.edit().remove("history-by-rating").remove("history-packs-pod-1").apply()
    }
    @After fun unpinClock() { AppClock.pinned = null }

    /** Invented cards: name, mana cost, type line. */
    private val cards = listOf(
        Triple("Dawnward Sentry", "{1}{W}", "Creature — Human Soldier"), Triple("Lantern Oath", "{W}", "Instant"),
        Triple("Skyhall Lancer", "{3}{W}", "Creature — Bird Knight"), Triple("Tidecall Adept", "{1}{U}", "Creature — Merfolk Wizard"),
        Triple("Mistweave", "{U}{U}", "Instant"), Triple("Archive Drake", "{3}{U}", "Creature — Drake"),
        Triple("Gravel Ghoul", "{1}{B}", "Creature — Zombie"), Triple("Bitter Harvest", "{B}", "Instant"),
        Triple("Cinder Whelp", "{R}", "Creature — Dragon"), Triple("Spark Lash", "{R}", "Instant"),
        Triple("Mossback Cub", "{G}", "Creature — Bear"), Triple("Thornwood Ranger", "{2}{G}", "Creature — Elf Ranger"),
        Triple("Brass Courier", "{2}", "Artifact Creature — Construct"), Triple("Sunken Causeway", "", "Land"),
    )
    private val definitions = cards.mapIndexed { n, (name, cost, type) ->
        SubmittedDefinition("def-$n", CardDefinition(name, listOf(DefinitionFace("face", name, typeLine = type, manaCost = cost,
            scryfallId = placeholderPrinting(name)))))
    }
    /** Invented GIH WRs (tenths of a percent); Brass Courier and the land have none. */
    private val table = cards.take(12).withIndex().associate { (n, card) -> card.first to 500 + (n * 37) % 120 }
    private fun copies(vararg n: Int) = n.map { DraftCopy("c$it", "def-$it") }

    private val testSet = PodSource(kind = PodSourceKind.SET, setCode = "TST", setName = "Test Set",
        boosterKind = SetBoosterKind.RECIPE_BASED, boosterName = "play", packSets = listOf("TST"), botRatings = true)
    private val drafts = listOf(
        DraftRecordSummary("d1", "pod-1", "Friday night draft", testSet, DraftSettings(packsPerPlayer = 2, cardsPerPack = 6),
            "2026-11-25T21:10:00Z", listOf(alex, riley, PodBots.identity(1))),
        DraftRecordSummary("d2", "pod-2", "Solo practice", testSet.copy(setName = "Test Set"), DraftSettings(packsPerPlayer = 3),
            "2026-11-19T08:30:00Z", listOf(alex, PodBots.identity(1), PodBots.identity(2))),
        DraftRecordSummary("d3", "pod-3", "Arena warm-up", testSet, DraftSettings.arena(), "2026-11-02T17:45:00Z", listOf(alex)),
        DraftRecordSummary("d4", "pod-4", "Cube club", testSet, DraftSettings(), "2026-10-30T19:00:00Z", listOf(morgan, alex)))
    /** Friday night draft as the server kept it: Alice's three picks of round 1, the last a pack that came back, and round 2's first. */
    private val friday = DraftRecord(drafts.first(), PodDraftHistory(listOf(
        DraftHistoryPick(1, 1, copies(0, 3, 6, 8, 10, 13), kept = listOf("c8")),
        DraftHistoryPick(1, 2, copies(1, 4, 7, 11, 12), kept = listOf("c4")),
        DraftHistoryPick(1, 4, copies(0, 6, 13), kept = listOf("c6"), takenSince = copies(3, 10), returning = true),
        DraftHistoryPick(2, 1, copies(2, 5, 9, 11, 12, 13), kept = listOf("c9"), round = 2)),
        definitions, seating = listOf(alex.accountId, riley.accountId, PodBots.id(1))))
    private val filters = PodCardFilters(readRatings = { code -> if (code == "TST") table else emptyMap() })

    private fun show() {
        compose.setContent { ScreenTheme { NoRipples { StatsContent(history, alex.accountId, filters, null) } } }
        compose.mainClock.advanceTimeBy(500)
        compose.waitForIdle()
    }

    // LOB-130, LOB-280 (owner, 2026-11-26): the Drafts tab lists the kept drafts newest first, each with its pod's name, then its
    // set's name and the date
    @Test fun draftsTabListsTheKeptDrafts() {
        history.stats = HistoryList(records)
        history.drafts = HistoryList(drafts)
        show()
        compose.onNodeWithTag("stats-drafts-tab").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Friday night draft").assertIsDisplayed()
        compose.onNodeWithText("Test Set · 25 Nov 2026").assertIsDisplayed()
        val rows = compose.onAllNodes(hasTestTagPrefix("stats-draft-d")).fetchSemanticsNodes()
            .map { it.config[androidx.compose.ui.semantics.SemanticsProperties.TestTag] }
        kotlin.test.assertEquals(listOf("stats-draft-d1", "stats-draft-d2", "stats-draft-d3", "stats-draft-d4"), rows)
        captureShot("$SCREENSHOTS/stats/drafts.png", "Stats after tapping its Drafts tab with All chosen: alex's four kept drafts " +
            "newest first, each row the pod's name over its set and the date alex's own draft was done.")
    }

    // LOB-280: with an opponent chosen only the drafts they were in (choice)
    @Test fun anOpponentChipFiltersTheDrafts() {
        history.stats = HistoryList(records)
        history.drafts = HistoryList(drafts)
        history.choice = StatsChoice(tab = StatsTab.Drafts)
        show()
        compose.onNodeWithTag("stats-chip-${riley.accountId}").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Friday night draft").assertIsDisplayed()
        compose.onNodeWithText("Solo practice").assertDoesNotExist()
        compose.onNodeWithText("Cube club").assertDoesNotExist()
        captureShot("$SCREENSHOTS/stats/drafts-opponent.png", "The Drafts tab after tapping riley's chip: only the draft riley " +
            "was in is left (choice: the opponent chips filter the drafts too, by who was seated).")
    }

    // LOB-280: no kept draft yet says so
    @Test fun noDraftsSaysSo() {
        history.stats = HistoryList(records)
        history.drafts = HistoryList(emptyList())
        history.choice = StatsChoice(tab = StatsTab.Drafts)
        show()
        compose.onNodeWithText("No drafts yet. Drafts with card ratings show here once your own draft is done.").assertIsDisplayed()
    }

    // LOB-281, DRF-315, DRF-316, DRF-317, DRF-210 (owner, 2026-11-26): tapping a kept draft opens its draft history as the pod
    // showed it, with the seating, the card ratings, By rating and the packs chip, though the pod is gone; Close returns to the list
    @Test fun aKeptDraftOpensItsDraftHistory() {
        history.stats = HistoryList(records)
        history.drafts = HistoryList(drafts)
        history.draftRecords = mapOf("d1" to HistoryList(friday))
        history.choice = StatsChoice(tab = StatsTab.Drafts)
        show()
        compose.onNodeWithTag("stats-draft-d1").performClick()
        compose.mainClock.advanceTimeBy(500)
        compose.waitForIdle()
        compose.onNodeWithTag("pod-history-dialog").assertIsDisplayed()
        compose.onNodeWithTag("pod-ratings-citation").assertTextEquals("Ratings: 17Lands")
        compose.onNodeWithTag("pod-history-0-c8-rating", useUnmergedTree = true).assertExists()
        compose.onNodeWithTag("pod-history-0-c13-rating", useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithTag("pod-history-by-rating").assertIsDisplayed()
        compose.onNodeWithTag("pod-history-returning").assertTextEquals("All packs")
        compose.onNodeWithTag("pod-history-seat-round").assertTextEquals("Round 1 · passed to riley · clockwise")
        captureShot("$SCREENSHOTS/stats/draft-history.png", "Tapping Friday night draft in Stats' Drafts tab opens its draft " +
            "history, the pod's own dialog: the seat ring of alex, riley and Bot 1, every pick of round 1 with alex's keep green " +
            "and each rated card's win rate chip, the citation at the top, By rating and All packs under the title.")
        // The packs chip works as in the pod's dialog
        compose.onNodeWithTag("pod-history-returning").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Pack 1 · Pick 4").assertExists()
        compose.onNodeWithText("Pack 1 · Pick 1").assertDoesNotExist()
        compose.onNodeWithTag("pod-history-returning").performClick()
        compose.onNodeWithTag("pod-history-returning").performClick()
        compose.onNode(hasContentDescription("Close draft history")).performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("pod-history-dialog").assertDoesNotExist()
        compose.onNodeWithTag("stats-draft-d1").assertIsDisplayed()
    }

    // LOB-281, DRF-211: a kept draft's history shows "Loading…" until it comes, and a failure offers Retry, as the pod's dialog does
    @Test fun aKeptDraftLoadsAndOffersRetryOnAFailure() {
        history.stats = HistoryList(records)
        history.drafts = HistoryList(drafts)
        history.choice = StatsChoice(tab = StatsTab.Drafts)
        show()
        // Opening it reads it (the fixtures' worker runs nothing, so it stays on its way)
        compose.onNodeWithTag("stats-draft-d2").performClick()
        compose.waitForIdle()
        kotlin.test.assertEquals(true, history.draftRecords["d2"]?.loading)
        compose.onNodeWithText("Loading…").assertIsDisplayed()
        compose.runOnIdle { history.draftRecords = mapOf("d2" to HistoryList(failed = true)) }
        compose.onNodeWithText("Couldn't load the draft history").assertIsDisplayed()
        compose.onNodeWithTag("pod-history-retry").assertIsDisplayed()
    }

    /** A Rotisserie of Test Set alex drafted with riley and Bot 1, as the server kept it: every take, oldest first. */
    private val rotisserie = DraftRecordSummary("d5", "pod-5", "Rotisserie night", testSet, DraftSettings.rotisserie().copy(cardsPerPlayer = 4),
        "2026-11-27T19:00:00Z", listOf(riley, alex, PodBots.identity(1)))
    private val rotisserieRecord = DraftRecord(rotisserie, PodDraftHistory(emptyList(), definitions,
        seating = listOf(riley.accountId, alex.accountId, PodBots.id(1))), listOf(
            DraftRecordTake(riley.accountId, copies(0)), DraftRecordTake(alex.accountId, copies(3)), DraftRecordTake(PodBots.id(1), copies(6)),
            DraftRecordTake(PodBots.id(1), copies(8)), DraftRecordTake(alex.accountId, copies(10)), DraftRecordTake(riley.accountId, copies(4)),
            DraftRecordTake(riley.accountId, copies(12)), DraftRecordTake(alex.accountId, copies(9)), DraftRecordTake(PodBots.id(1), copies(1)),
            DraftRecordTake(PodBots.id(1), copies(11)), DraftRecordTake(alex.accountId, copies(5)), DraftRecordTake(riley.accountId, copies(7))))

    // LOB-281, DRF-160, DRF-315, DRF-316, DRF-317 (owner, 2026-11-27): a kept Rotisserie draft opens in its Picks: a chip per
    // opponent and their picks newest first with each rated card's win rate, the citation, and By rating in the title row
    @Test fun aKeptRotisserieDraftOpensItsPicks() {
        history.stats = HistoryList(records)
        history.drafts = HistoryList(listOf(rotisserie) + drafts)
        history.draftRecords = mapOf("d5" to HistoryList(rotisserieRecord))
        history.choice = StatsChoice(tab = StatsTab.Drafts)
        show()
        compose.onNodeWithTag("stats-draft-d5").performClick()
        compose.mainClock.advanceTimeBy(500)
        compose.waitForIdle()
        compose.onNodeWithText("Picks").assertIsDisplayed()
        compose.onNodeWithTag("pod-history-dialog").assertDoesNotExist()
        compose.onNodeWithText("riley (4)").assertIsDisplayed()
        compose.onNodeWithText("Bot 1 (4)").assertIsDisplayed()
        compose.onNodeWithTag("pod-ratings-citation", useUnmergedTree = true).assertExists()
        compose.onNodeWithTag("pod-picks-c7-rating", useUnmergedTree = true).assertExists()
        compose.onNodeWithTag("pod-picks-c12-rating", useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithTag("pod-history-by-rating").assertIsDisplayed()
        captureShot("$SCREENSHOTS/stats/draft-picks.png", "Tapping Rotisserie night in Stats' Drafts tab opens its Picks, the pod's " +
            "own dialog: riley's chip chosen, riley's four picks newest first with each rated card's win rate chip (Brass Courier has none), " +
            "the citation above them and By rating in the title row.")
        compose.onNodeWithText("Bot 1 (4)").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("pod-picks-c11", useUnmergedTree = true).assertExists()
        compose.onNode(hasContentDescription("Close picks")).performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Picks").assertDoesNotExist()
        compose.onNodeWithTag("stats-draft-d5").assertIsDisplayed()
    }

    // LOB-281: a kept Picks draft shows "Loading…" until it comes, and a failure offers Retry
    @Test fun aKeptPicksDraftLoadsAndOffersRetryOnAFailure() {
        history.stats = HistoryList(records)
        history.drafts = HistoryList(listOf(rotisserie))
        history.choice = StatsChoice(tab = StatsTab.Drafts)
        show()
        compose.onNodeWithTag("stats-draft-d5").performClick()
        compose.waitForIdle()
        kotlin.test.assertEquals(true, history.draftRecords["d5"]?.loading)
        compose.onNodeWithText("Loading…").assertIsDisplayed()
        compose.runOnIdle { history.draftRecords = mapOf("d5" to HistoryList(failed = true)) }
        compose.onNodeWithText("Couldn't load the picks").assertIsDisplayed()
        compose.onNodeWithTag("pod-picks-retry").assertIsDisplayed()
    }

    private fun hasTestTagPrefix(prefix: String) = SemanticsMatcher("test tag starts with $prefix") { node ->
        node.config.getOrElseNullable(androidx.compose.ui.semantics.SemanticsProperties.TestTag) { null }?.startsWith(prefix) == true
    }
}
