package org.asyncttwiz.android

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import org.asyncttwiz.android.MatchHistoryFixtures.alex
import org.asyncttwiz.android.MatchHistoryFixtures.morgan
import org.asyncttwiz.android.MatchHistoryFixtures.records
import org.asyncttwiz.android.MatchHistoryFixtures.riley
import org.asyncttwiz.protocol.*
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** The Stats screen's figures and a pod's standings (game history §3.2, 3.3), from invented records: Unfinished records
 * never count (§1.3). */
class MatchStatsTest {
    private val me = alex.accountId

    // LOB-130, LOB-132, LOB-127: the opponent chips, most matches first; the summary tiles
    @Test fun opponentsAndSummary() {
        assertEquals(listOf(riley to 5, morgan to 2), MatchStats.opponents(records, me).map { it.account to it.matches })
        val all = MatchStats.summary(records, me)
        assertEquals("4–1–1", all.record.text)
        assertEquals(6, all.record.matches, "the Unfinished match doesn't count")
        assertEquals("67%", all.record.rate.text)
        assertEquals(SuggestedResult.WIN to 1, all.current)
        assertEquals(2, all.longestWins)
        assertEquals(52.0 / 6, all.averageTurns!!, 1e-9)
        val withRiley = MatchStats.summary(MatchStats.with(records, me, riley.accountId), me)
        assertEquals("3–1–1", withRiley.record.text)
        val none = MatchStats.summary(MatchStats.with(records, me, morgan.accountId).filter { !it.finished }, me)
        assertEquals(0, none.record.matches); assertNull(none.averageTurns); assertNull(none.current)
    }

    // LOB-132: record over time, play/draw, how matches end, match length
    @Test fun graphs() {
        assertEquals(listOf(1 to 0, 1 to 1, 2 to 1, 3 to 1, 3 to 1, 4 to 1), MatchStats.running(records, me))
        val (play, draw) = MatchStats.playDraw(records, me)
        assertEquals(MatchStats.Rate(4, 4), play); assertEquals(MatchStats.Rate(0, 2), draw)
        assertEquals(listOf("By the rules" to 3, "Conceded" to 1, "Agreed" to 1, "Draw" to 1), MatchStats.endings(records, me))
        assertEquals(listOf("1–4" to 0, "5–8" to 3, "9–12" to 2, "13–16" to 1, "17–20" to 0, "21–24" to 0, "25–28" to 0, "29–32" to 0, "33+" to 0),
            MatchStats.lengths(records, me))
        assertEquals("—", MatchStats.Rate(0, 0).text)
    }

    // LOB-132: your decks, your decks vs theirs (hidden decks pooled as "Deck"), colours, extras
    @Test fun decksColoursAndExtras() {
        assertEquals(listOf("Ember rush" to "3–1–0", "Quiet grove" to "1–0–1"), MatchStats.decks(records, me).map { it.name to it.record.text })
        val grid = MatchStats.grid(MatchStats.with(records, me, riley.accountId), me)
        assertEquals(listOf("Ember rush", "Quiet grove"), grid.rows)
        assertEquals(listOf("Tide turners", "Deck"), grid.columns)
        assertEquals("2–1–0", grid.cells["Ember rush" to "Tide turners"]!!.text)
        assertEquals("1–0–0", grid.cells["Ember rush" to "Deck"]!!.text)
        assertEquals("0–0–1", grid.cells["Quiet grove" to "Deck"]!!.text)
        assertEquals(listOf("R" to MatchStats.Rate(3, 4), "GW" to MatchStats.Rate(1, 2)), MatchStats.colors(records, me))
        assertEquals("{G}{W}", MatchStats.colorSymbols("GW"))
        assertEquals(MatchStats.Rate(2, 2), MatchStats.afterMulligan(records, me))
        assertEquals(5, MatchStats.fastestWin(records, me))
        assertEquals(14, MatchStats.longestMatch(records, me))
        val weeks = MatchStats.perWeek(records, me, Instant.parse("2026-11-08T20:00:00Z"), ZoneOffset.UTC)
        assertEquals(8, weeks.size)
        assertEquals(LocalDate.of(2026, 11, 2) to 5, weeks.last())
        assertEquals(LocalDate.of(2026, 10, 26) to 1, weeks[6])
        assertEquals(6, weeks.sumOf { it.second })
    }

    // DRF-121: a pod's standings: each member's W–L–D in the pod's finished matches, best first, members without one at 0–0–0
    @Test fun podStandings() {
        val pod = listOf(
            MatchHistoryFixtures.record(10, riley, SuggestedResult.WIN, 8, 2),
            MatchHistoryFixtures.record(11, riley, SuggestedResult.DRAW, 8, 3),
            MatchHistoryFixtures.record(12, morgan, SuggestedResult.LOSS, 8, 4),
            MatchHistoryFixtures.record(13, morgan, null, 3, 5))
        val sam = AccountIdentity("d", "sam")
        val standings = MatchStats.standings(pod, listOf(sam, riley, alex, morgan))
        assertEquals(listOf("morgan" to "1–0–0", "alex" to "1–1–1", "sam" to "0–0–0", "riley" to "0–1–1"),
            standings.map { it.account.username to it.record.text })
    }

    // LOB-130, LOB-131: how a match ended, in a row and in its details
    @Test fun endingTexts() {
        val byRules = records.single { it.id == "r2" }
        assertEquals("Life 0", matchEndText(byRules))
        assertEquals("You reached 0 life", matchEndDetail(byRules, me))
        assertEquals("Agreed · “Infinite combo”", matchEndDetail(records.single { it.id == "r4" }, me))
        assertEquals("morgan conceded", matchEndDetail(records.single { it.id == "r3" }, me))
        assertEquals("Loop", matchEndText(records.single { it.id == "r5" }))
        assertEquals("Unfinished", matchEndText(records.single { it.id == "r6" }))
        assertEquals("Test Doom", lossCauseText(LossCause.CardEffect("Test Doom")))
        assertEquals("2 h 1 min", matchDuration(records.single { it.id == "r1" }))
    }
}
