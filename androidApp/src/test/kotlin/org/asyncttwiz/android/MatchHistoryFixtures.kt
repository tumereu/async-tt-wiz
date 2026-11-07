package org.asyncttwiz.android

import org.asyncttwiz.protocol.*

/** Invented match records for the game history's tests and screenshots: Alex's matches against Riley and Morgan. */
internal object MatchHistoryFixtures {
    val alex = AccountIdentity("a", "alex")
    val riley = AccountIdentity("b", "riley")
    val morgan = AccountIdentity("c", "morgan")
    private val ember = MatchDeck("Ember rush", null, "R", "deck-ember", openable = true)
    private val grove = MatchDeck("Quiet grove", null, "GW", "deck-grove", openable = true)
    private val rileyTide = MatchDeck("Tide turners", null, "U", "deck-tide")

    /** One record: [day] of November 2026, [result] from Alex's side (null: Unfinished). */
    fun record(n: Int, opponent: AccountIdentity, result: SuggestedResult?, turns: Int, day: Int,
        ending: MatchEnding = MatchEnding.ByRules, mine: MatchDeck = ember, theirs: MatchDeck? = null, onThePlay: Boolean = true,
        mulligans: Int = 0, pod: String? = null): MatchRecord {
        val them = when (result) {
            SuggestedResult.WIN -> SuggestedResult.LOSS; SuggestedResult.LOSS -> SuggestedResult.WIN
            SuggestedResult.DRAW -> SuggestedResult.DRAW; null -> null
        }
        val cause = if (ending == MatchEnding.ByRules) LossCause.ZeroLife else null
        return MatchRecord("r$n", "g${n % 3}", listOf("Azure Spark", "Ember Gate", "Quiet Night")[n % 3], podId = pod?.let { "pod" },
            podName = pod, startedAt = "2026-11-%02dT10:00:00Z".format(day), endedAt = "2026-11-%02dT12:%02d:00Z".format(day, n % 60),
            turns = turns, players = listOf(
                MatchPlayer(alex, result, mulligans, if (result == SuggestedResult.LOSS) 0 else 12, onThePlay, mine,
                    lossCause = cause.takeIf { result == SuggestedResult.LOSS }),
                MatchPlayer(opponent, them, 0, if (result == SuggestedResult.WIN) 0 else 7, !onThePlay, theirs,
                    lossCause = cause.takeIf { result == SuggestedResult.WIN })),
            ending = if (result == null) MatchEnding.Unfinished else ending)
    }

    /** Alex's records, newest first (as the server lists them). */
    val records: List<MatchRecord> = listOf(
        record(1, riley, SuggestedResult.WIN, 7, 1),
        record(2, riley, SuggestedResult.LOSS, 9, 2, onThePlay = false, theirs = rileyTide),
        record(3, morgan, SuggestedResult.WIN, 5, 3, MatchEnding.Conceded("c"), mine = grove, mulligans = 1),
        record(4, riley, SuggestedResult.WIN, 11, 5, MatchEnding.Agreed("Infinite combo"), theirs = rileyTide, pod = "Cube night"),
        record(5, riley, SuggestedResult.DRAW, 14, 6, MatchEnding.Draw(loop = true), mine = grove, onThePlay = false),
        record(6, morgan, null, 4, 7),
        record(7, riley, SuggestedResult.WIN, 6, 8, theirs = rileyTide, mulligans = 1),
    ).reversed()
}
