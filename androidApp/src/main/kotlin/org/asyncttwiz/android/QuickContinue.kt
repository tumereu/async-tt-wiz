package org.asyncttwiz.android

import org.asyncttwiz.protocol.GameLifecycle
import org.asyncttwiz.protocol.GameSummary
import java.time.Instant

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
