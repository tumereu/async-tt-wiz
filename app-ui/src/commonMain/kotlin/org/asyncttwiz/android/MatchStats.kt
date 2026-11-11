package org.asyncttwiz.android

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters
import org.asyncttwiz.protocol.*

/**
 * The game history's figures (spec game-history §3.3, the Stats screen's Graphs; §3.2, a pod's standings), worked out
 * from the viewer's records. Unfinished records are listed but never count (§1.3): every figure here reads finished
 * matches only, oldest first where order matters.
 */
object MatchStats {
    const val COLOURLESS = "Colourless"

    /** A share of matches: [wins] of [matches]; [percent] null when there were none. */
    data class Rate(val wins: Int, val matches: Int) {
        val percent: Int? get() = if (matches == 0) null else Math.round(wins * 100.0 / matches).toInt()
        val text: String get() = percent?.let { "$it%" } ?: "—"
    }

    data class Record(val wins: Int = 0, val losses: Int = 0, val draws: Int = 0) {
        val matches: Int get() = wins + losses + draws
        val rate: Rate get() = Rate(wins, matches)
        val text: String get() = "$wins–$losses–$draws"
        operator fun plus(result: SuggestedResult?) = when (result) {
            SuggestedResult.WIN -> copy(wins = wins + 1)
            SuggestedResult.LOSS -> copy(losses = losses + 1)
            SuggestedResult.DRAW -> copy(draws = draws + 1)
            null -> this
        }
    }

    /** The top tiles: matches, W–L–D, win %, current streak (newest results alike), longest winning streak, average turns. */
    data class Summary(val record: Record, val current: Pair<SuggestedResult, Int>?, val longestWins: Int, val averageTurns: Double?)

    /** An opponent chip: the opponent and how many matches (records) they played with the viewer. */
    data class Opponent(val account: AccountIdentity, val matches: Int)

    /** A deck of the viewer's with its record. */
    data class DeckLine(val sourceId: String, val name: String, val cover: DeckCover?, val record: Record)

    /** The viewer's decks against the opponent's: rows (the viewer's deck names), columns (theirs, hidden ones pooled as
     * "Deck"), each cell the record. */
    data class Grid(val rows: List<String>, val columns: List<String>, val cells: Map<Pair<String, String>, Record>)

    /** A pod member's standing in the pod's matches. */
    data class Standing(val account: AccountIdentity, val record: Record)

    private fun List<MatchRecord>.finished(me: String) = filter { it.finished && it.player(me) != null }.sortedBy { it.endedAt }
    private fun MatchRecord.result(me: String) = player(me)?.result

    /** The opponents, most matches first (then by name). */
    fun opponents(records: List<MatchRecord>, me: String): List<Opponent> =
        records.mapNotNull { it.opponent(me)?.account }.groupBy { it.accountId }
            .map { (_, accounts) -> Opponent(accounts.first(), accounts.size) }
            .sortedWith(compareByDescending<Opponent> { it.matches }.thenBy { it.account.username.lowercase() })

    /** The records with [opponent] (null: all). */
    fun with(records: List<MatchRecord>, me: String, opponent: String?): List<MatchRecord> =
        if (opponent == null) records else records.filter { it.opponent(me)?.account?.accountId == opponent }

    fun summary(records: List<MatchRecord>, me: String): Summary {
        val finished = records.finished(me)
        val results = finished.map { it.result(me) }
        val record = results.fold(Record()) { acc, r -> acc + r }
        val current = results.lastOrNull()?.let { last -> last to results.reversed().takeWhile { it == last }.size }
        var longest = 0; var run = 0
        for (r in results) { run = if (r == SuggestedResult.WIN) run + 1 else 0; longest = maxOf(longest, run) }
        return Summary(record, current, longest, finished.map { it.turns }.average().takeIf { finished.isNotEmpty() })
    }

    /** Record over time: after each finished match, the running wins of the viewer and of the opponent (one opponent), or
     * the viewer's running wins and losses (All). */
    fun running(records: List<MatchRecord>, me: String): List<Pair<Int, Int>> {
        var first = 0; var second = 0
        return records.finished(me).map { record ->
            when (record.result(me)) {
                SuggestedResult.WIN -> first++
                SuggestedResult.LOSS -> second++
                else -> {}
            }
            first to second
        }
    }

    /** Win % on the play and on the draw. */
    fun playDraw(records: List<MatchRecord>, me: String): Pair<Rate, Rate> {
        val finished = records.finished(me)
        fun rate(onThePlay: Boolean) = finished.filter { it.player(me)!!.onThePlay == onThePlay }
            .let { list -> Rate(list.count { it.result(me) == SuggestedResult.WIN }, list.size) }
        return rate(true) to rate(false)
    }

    /** How matches end: by the rules, conceded, agreed, draw (a draw by the rules). */
    fun endings(records: List<MatchRecord>, me: String): List<Pair<String, Int>> {
        val finished = records.finished(me)
        return listOf("By the rules" to finished.count { it.ending is MatchEnding.ByRules },
            "Conceded" to finished.count { it.ending is MatchEnding.Conceded },
            "Agreed" to finished.count { it.ending is MatchEnding.Agreed },
            "Draw" to finished.count { it.ending is MatchEnding.Draw })
    }

    /** Match length in turns: a histogram of four-turn ranges from 1–4 to 29–32, then 33+. */
    fun lengths(records: List<MatchRecord>, me: String): List<Pair<String, Int>> {
        val turns = records.finished(me).map { it.turns }
        val buckets = (0 until 8).map { "${it * 4 + 1}–${it * 4 + 4}" to (if (it == 0) 0 else it * 4 + 1)..(it * 4 + 4) } + ("33+" to 33..Int.MAX_VALUE)
        return buckets.map { (label, range) -> label to turns.count { it in range } }
    }

    /** The viewer's decks (by source), most played first. */
    fun decks(records: List<MatchRecord>, me: String): List<DeckLine> = records.finished(me)
        .mapNotNull { r -> r.player(me)?.deck?.let { it to r.result(me) } }.groupBy { it.first.sourceId }
        .map { (source, played) -> DeckLine(source, played.last().first.name, played.last().first.cover,
            played.fold(Record()) { acc, (_, result) -> acc + result }) }
        .sortedWith(compareByDescending<DeckLine> { it.record.matches }.thenBy { it.name.lowercase() })

    /** The viewer's decks against the opponent's (their hidden decks pooled as "Deck"). */
    fun grid(records: List<MatchRecord>, me: String): Grid {
        val cells = mutableMapOf<Pair<String, String>, Record>()
        for (r in records.finished(me)) {
            val mine = r.player(me)?.deck?.name ?: "Deck"
            val theirs = r.opponent(me)?.deck?.name ?: "Deck"
            cells[mine to theirs] = (cells[mine to theirs] ?: Record()) + r.result(me)
        }
        return Grid(cells.keys.map { it.first }.distinct().sortedBy { it.lowercase() },
            cells.keys.map { it.second }.distinct().sortedWith(compareBy<String> { it == "Deck" }.thenBy { it.lowercase() }), cells)
    }

    /** [colors]' WUBRG letters as mana symbols for CardText ("GW" → "{G}{W}"), as Stats draws its pips (DRF-116). */
    fun colorSymbols(colors: String): String = colors.map { "{$it}" }.joinToString("")

    /** Win % by the colours of the viewer's deck (WUBRG letters; [COLOURLESS] for none). */
    fun colors(records: List<MatchRecord>, me: String): List<Pair<String, Rate>> = records.finished(me)
        .groupBy { it.player(me)?.deck?.colors?.ifEmpty { COLOURLESS } ?: COLOURLESS }
        .map { (colors, list) -> colors to Rate(list.count { it.result(me) == SuggestedResult.WIN }, list.size) }
        .sortedWith(compareByDescending<Pair<String, Rate>> { it.second.matches }.thenBy { it.first })

    /** Win % in matches where the viewer took a mulligan. */
    fun afterMulligan(records: List<MatchRecord>, me: String): Rate = records.finished(me).filter { (it.player(me)!!.mulligans ?: 0) > 0 }
        .let { list -> Rate(list.count { it.result(me) == SuggestedResult.WIN }, list.size) }

    /** The fewest turns of a win, and the most turns of any match. */
    fun fastestWin(records: List<MatchRecord>, me: String): Int? = records.finished(me).filter { it.result(me) == SuggestedResult.WIN }.minOfOrNull { it.turns }
    fun longestMatch(records: List<MatchRecord>, me: String): Int? = records.finished(me).maxOfOrNull { it.turns }

    /** Matches per week (weeks from Monday, in [zone]) for the [weeks] weeks up to [now]'s, oldest first. */
    fun perWeek(records: List<MatchRecord>, me: String, now: Instant, zone: ZoneId, weeks: Int = 8): List<Pair<LocalDate, Int>> {
        fun week(instant: Instant): LocalDate = instant.atZone(zone).toLocalDate().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val current = week(now)
        val counts = records.finished(me).groupingBy { week(Instant.parse(it.endedAt)) }.eachCount()
        return (weeks - 1 downTo 0).map { back -> current.minusWeeks(back.toLong()).let { it to (counts[it] ?: 0) } }
    }

    /** A pod's standings: each member's W–L–D in the pod's finished matches, best record first (most wins, then fewest
     * losses, then most draws, then by name); members who played none at 0–0–0. */
    fun standings(records: List<MatchRecord>, members: List<AccountIdentity>): List<Standing> {
        val finished = records.filter { it.finished }
        return members.map { member -> Standing(member, finished.fold(Record()) { acc, r -> acc + r.player(member.accountId)?.result }) }
            .sortedWith(compareByDescending<Standing> { it.record.wins }.thenBy { it.record.losses }
                .thenByDescending { it.record.draws }.thenBy { it.account.username.lowercase() })
    }
}
