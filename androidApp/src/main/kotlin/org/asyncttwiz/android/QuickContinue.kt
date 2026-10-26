package org.asyncttwiz.android

import org.asyncttwiz.protocol.GameLifecycle
import org.asyncttwiz.protocol.GameSummary
import org.asyncttwiz.protocol.PodSummary
import org.asyncttwiz.protocol.PodAttention
import org.asyncttwiz.protocol.PodPhase
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

internal data class QuickContinueEntry(val id: String, val updatedAt: String, val yours: Boolean,
    val game: GameSummary? = null, val pod: PodSummary? = null)

/** Own moves/picks oldest first, then other active games newest first, with stable type/id ties. */
internal fun quickContinue(games: List<GameSummary>, pods: List<PodSummary>, accountId: String): List<QuickContinueEntry> =
    (games.filter { it.lifecycle == GameLifecycle.ACTIVE }.map {
        QuickContinueEntry(it.id, it.updatedAt, it.attentionHolder == accountId, game = it)
    } + pods.filter { it.phase == PodPhase.DRAFTING && it.attention == PodAttention.YOUR_PICK }.map {
        QuickContinueEntry(it.id, it.updatedAt, true, pod = it)
    }).sortedWith { a, b ->
        when {
            a.yours != b.yours -> if (a.yours) -1 else 1
            Instant.parse(a.updatedAt) != Instant.parse(b.updatedAt) ->
                if (a.yours) Instant.parse(a.updatedAt).compareTo(Instant.parse(b.updatedAt))
                else Instant.parse(b.updatedAt).compareTo(Instant.parse(a.updatedAt))
            a.id != b.id -> a.id.compareTo(b.id)
            else -> (a.pod != null).compareTo(b.pod != null)
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
