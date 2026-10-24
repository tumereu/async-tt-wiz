package org.asyncttwiz.android

import org.asyncttwiz.protocol.GameLifecycle
import org.asyncttwiz.protocol.GameSummary
import java.time.Instant
import java.time.Clock
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Activity ownership is independent of the turn player; only accepted summaries rank here. */
internal fun quickContinueGames(games: List<GameSummary>, accountId: String): List<GameSummary> =
    games.filter { it.lifecycle == GameLifecycle.ACTIVE }.sortedWith { a, b ->
        val aOwn = a.attentionHolder == accountId
        val bOwn = b.attentionHolder == accountId
        val aTime = Instant.parse(a.updatedAt)
        val bTime = Instant.parse(b.updatedAt)
        when {
            aOwn != bOwn -> if (aOwn) -1 else 1
            aTime != bTime -> if (aOwn) aTime.compareTo(bTime) else bTime.compareTo(aTime)
            else -> a.id.compareTo(b.id)
        }
    }.take(3)

internal fun quickContinueMove(game: GameSummary, accountId: String): String = when (game.attentionHolder) {
    accountId -> "Your move"
    game.opponent(accountId).accountId -> "${game.opponent(accountId).username}'s move"
    else -> "Move not available"
}

internal data class GameRowMove(val label: String, val highlighted: Boolean)

internal fun gameRowMove(game: GameSummary, accountId: String) =
    GameRowMove(quickContinueMove(game, accountId), game.attentionHolder == accountId)

internal fun gameLastActivity(game: GameSummary, clock: Clock = Clock.systemDefaultZone()): String = runCatching {
    val activity = Instant.parse(game.updatedAt).atZone(clock.zone)
    val today = LocalDate.now(clock)
    val pattern = when {
        activity.toLocalDate() == today -> "HH:mm"
        activity.year == today.year -> "d MMM"
        else -> "d MMM uuuu"
    }
    activity.format(DateTimeFormatter.ofPattern(pattern, Locale.ENGLISH))
}.getOrDefault("")
