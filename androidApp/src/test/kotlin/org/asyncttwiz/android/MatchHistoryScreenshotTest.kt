package org.asyncttwiz.android

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.unit.dp
import kotlin.test.assertEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import org.asyncttwiz.android.MatchHistoryFixtures.alex
import org.asyncttwiz.android.MatchHistoryFixtures.morgan
import org.asyncttwiz.android.MatchHistoryFixtures.records
import org.asyncttwiz.android.MatchHistoryFixtures.riley
import org.asyncttwiz.protocol.*
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Game history's screens (spec §3) with invented records: a lobby's History tab, a pod's standings and matches, and the
 * Stats screen's History, a record's details and Graphs (one opponent and All). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w412dp-h892dp-port-mdpi")
class MatchHistoryScreenshotTest {
    @get:Rule val compose = createComposeRule()
    private val fixtures = ScreenFixtures()
    private val history get() = fixtures.lobby.history

    @Before fun pinClock() { AppClock.pinned = Clock.fixed(Instant.parse("2026-11-08T20:00:00Z"), ZoneOffset.UTC) }
    @After fun unpinClock() { AppClock.pinned = null }

    // LOB-035, LOB-129: a game lobby's History tab lists the game's matches newest first
    @Test fun lobbyHistoryTab() {
        fixtures.configure("ready-waiting")
        val game = fixtures.lobby.state.detail!!
        history.games = mapOf(game.id to HistoryList(records.filter { it.gameId == "g2" }))
        compose.setContent { ScreenTheme { NoRipples { WaitingLobby(fixtures.lobby, alex.accountId, null) } } }
        compose.onNodeWithTag("lobby-history-tab").performClick()
        compose.onNodeWithTag("match-r5").assertIsDisplayed()
        compose.onNodeWithText("Loop", substring = true).assertIsDisplayed()
        captureShot("$SCREENSHOTS/match-history/lobby-history.png",
            "A game lobby's History tab, opened by tapping History, lists the game's two matches newest first: a loop draw and a loss.")
    }

    // LOB-129: an empty History tab says so
    @Test fun lobbyHistoryTabEmpty() {
        fixtures.configure("ready-waiting")
        history.games = mapOf(fixtures.lobby.state.detail!!.id to HistoryList(emptyList()))
        compose.setContent { ScreenTheme { WaitingLobby(fixtures.lobby, alex.accountId, null) } }
        compose.onNodeWithTag("lobby-history-tab").performClick()
        compose.onNodeWithText("No finished matches yet.").assertIsDisplayed()
    }

    // DRF-121: the pod's Games tab: members, then the standings and every match of the pod without decks
    @Test fun podStandingsAndMatches() {
        val detail = PodDetail("pod", "Cube night", PodPhase.DRAFTED, alex, DraftSettings(), PodSource("Cube", 540), DraftSupply(90, 540),
            listOf(PodMember(alex, submitted = true), PodMember(riley, submitted = true), PodMember(morgan, submitted = true)), emptyList(),
            updatedAt = "2026-11-08T00:00:00Z")
        val pod = listOf(MatchHistoryFixtures.record(20, riley, SuggestedResult.WIN, 8, 6, pod = "Cube night"),
            MatchHistoryFixtures.record(21, morgan, SuggestedResult.LOSS, 12, 5, MatchEnding.Conceded("a"), pod = "Cube night"))
            .map { r -> r.copy(players = r.players.map { it.copy(deck = null) }) }
        history.pods = mapOf("pod" to HistoryList(PodMatches(pod, listOf(PodMatchInPlay("g9", "Ember Gate", listOf(riley, morgan), 4)))))
        compose.setContent { ScreenTheme { PodGamesTab(detail, alex.accountId, fixtures.lobby) } }
        compose.onNodeWithTag("pod-standings").assertIsDisplayed()
        compose.onNodeWithText("riley vs morgan").assertIsDisplayed()
        compose.onNodeWithText("alex won").assertIsDisplayed()
        captureShot("$SCREENSHOTS/match-history/pod-games.png",
            "A pod's Games tab shows its members, the standings (morgan 1–0–0 first) and its matches: one in play at turn 4 and two finished, without decks.")
    }

    // LOB-130: Stats' History with All chosen: every record newest first, the Unfinished one outlined
    @Test fun statsHistory() {
        history.stats = HistoryList(records)
        compose.setContent { ScreenTheme { NoRipples { StatsContent(history, alex.accountId) {} } } }
        compose.onNodeWithTag("stats-chip-all").assertIsDisplayed()
        compose.onNodeWithText("Unfinished").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("match-r7").performScrollTo()
        captureShot("$SCREENSHOTS/match-history/stats-history.png",
            "The Stats screen's History tab with All chosen lists alex's records newest first, with riley and morgan as opponent chips.")
    }

    // LOB-131: a record's details, opened by tapping its row
    @Test fun statsDetails() {
        history.stats = HistoryList(records)
        compose.setContent { ScreenTheme { NoRipples { StatsContent(history, alex.accountId) {} } } }
        compose.onNodeWithTag("match-r4").performScrollTo().performClick()
        compose.onNodeWithTag("match-details").assertIsDisplayed()
        compose.onNodeWithText("Agreed · “Infinite combo”").assertIsDisplayed()
        compose.onNodeWithText("Open deck").assertIsDisplayed()
        captureShot("$SCREENSHOTS/match-history/stats-details.png",
            "Tapping the agreed win against riley opens its details: reason, pod, play/draw, mulligans, final life, both decks and Open deck.")
    }

    // LOB-132: Graphs with one opponent chosen: tiles, record over time (both players), and the decks against theirs
    @Test @Config(qualifiers = "w412dp-h2400dp-port-mdpi") fun statsGraphsOneOpponent() {
        history.stats = HistoryList(records)
        history.choice = StatsChoice(opponent = riley.accountId, graphs = true)
        compose.setContent { ScreenTheme { StatsContent(history, alex.accountId) {} } }
        compose.onNodeWithText("Your decks vs theirs").assertIsDisplayed()
        captureShot("$SCREENSHOTS/match-history/stats-graphs-opponent.png",
            "The Graphs tab with riley chosen shows the tiles (3–1–1), both players' running wins and the decks against riley's.")
    }

    // LOB-132: Graphs with All: your running wins and losses; no deck grid
    @Test @Config(qualifiers = "w412dp-h2400dp-port-mdpi") fun statsGraphsAll() {
        history.stats = HistoryList(records)
        history.choice = StatsChoice(graphs = true)
        compose.setContent { ScreenTheme { StatsContent(history, alex.accountId) {} } }
        compose.onNodeWithText("Your wins 4", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Your decks vs theirs").assertDoesNotExist()
        captureShot("$SCREENSHOTS/match-history/stats-graphs-all.png",
            "The Graphs tab with All chosen shows the tiles (4–1–1), alex's running wins and losses and every other graph down to matches per week.")
    }

    private fun assertTilesFillTheWidth(perRow: Int) {
        val whole = compose.onNodeWithTag("stats-tiles").getUnclippedBoundsInRoot()
        val tiles = listOf("Matches", "W–L–D", "Win %", "Current streak", "Longest streak", "Average turns")
            .map { compose.onNodeWithTag("stats-tile-$it").getUnclippedBoundsInRoot() }
        for (row in tiles.chunked(perRow)) {
            assertEquals(whole.left.value, row.first().left.value, 1f)
            assertEquals(whole.right.value, row.last().right.value, 1f)
            assertEquals(1, row.map { it.top }.distinct().size)
            assertEquals(1, row.map { it.bottom }.distinct().size)
            assertEquals(row.first().right.value - row.first().left.value, row.last().right.value - row.last().left.value, 1f)
        }
    }

    // LOB-132: the top tiles fill the whole width: two rows of three in portrait
    @Test @Config(qualifiers = "w412dp-h892dp-port-mdpi") fun statsTilesFillThePortraitWidth() {
        history.stats = HistoryList(records)
        history.choice = StatsChoice(graphs = true)
        compose.setContent { ScreenTheme { StatsContent(history, alex.accountId) {} } }
        assertTilesFillTheWidth(3)
    }

    // LOB-132: and all six in one row in landscape, beside the permanent side menu's 220 dp too; the match length in four-turn
    // ranges and the colours as mana symbols
    @Test @Config(qualifiers = "w892dp-h2400dp-port-mdpi") fun statsGraphsInLandscape() {
        history.stats = HistoryList(records)
        history.choice = StatsChoice(graphs = true)
        compose.setContent { ScreenTheme { Box(Modifier.width(672.dp)) { StatsContent(history, alex.accountId) {} } } }
        assertTilesFillTheWidth(6)
        compose.onNodeWithTag("stats-colors-GW", useUnmergedTree = true).assertTextEquals("{G}{W}").assertIsDisplayed()
        compose.onNode(hasContentDescription("1–4: 0, 5–8: 3, 9–12: 2, 13–16: 1, 17–20: 0, 21–24: 0, 25–28: 0, 29–32: 0, 33+: 0")).assertExists()
        captureShot("$SCREENSHOTS/match-history/stats-graphs-landscape.png", "The Graphs tab with All chosen at the width beside " +
            "the side menu in landscape: six tiles fill the row, match length runs in four-turn ranges to 33+, and win % by colours shows mana symbols.")
    }

    // LOB-132: no finished match: Graphs says so instead of drawing empty graphs
    @Test fun statsGraphsEmpty() {
        history.stats = HistoryList(records.filter { !it.finished })
        history.choice = StatsChoice(graphs = true)
        compose.setContent { ScreenTheme { StatsContent(history, alex.accountId) {} } }
        compose.onNodeWithText("No finished matches yet. Graphs appear once a match is finished.").assertIsDisplayed()
    }
}
