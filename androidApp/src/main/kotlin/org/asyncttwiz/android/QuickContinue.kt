package org.asyncttwiz.android

import org.asyncttwiz.protocol.GameLifecycle
import org.asyncttwiz.protocol.GameSummary
import org.asyncttwiz.protocol.PodSummary
import org.asyncttwiz.protocol.PodAttention
import org.asyncttwiz.protocol.PodInvitation
import org.asyncttwiz.protocol.InvitationState
import java.time.Instant
import java.time.Clock
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Each menu panel ranks actions, other active items, then invitations, independently. */
internal fun quickContinueGames(games: List<GameSummary>, accountId: String): List<GameSummary> =
    games.distinctBy { it.id }.filter { it.lifecycle == GameLifecycle.ACTIVE ||
        it.lifecycle == GameLifecycle.LOBBY && it.invitationState != InvitationState.DECLINED
    }.sortedWith(panelOrder({ it.id }, { it.updatedAt }) {
        when {
            it.invitationState == InvitationState.PENDING -> 2
            it.lifecycle == GameLifecycle.ACTIVE && it.attentionHolder == accountId -> 0
            else -> 1
        }
    }).take(3)

internal data class QuickContinuePod(val pod: PodSummary? = null, val invitation: PodInvitation? = null) {
    val id: String get() = pod?.id ?: requireNotNull(invitation).podId
    val updatedAt: String get() = pod?.updatedAt ?: requireNotNull(invitation).invitedAt
}

internal fun quickContinuePods(pods: List<PodSummary>, invitations: List<PodInvitation>): List<QuickContinuePod> =
    (pods.map { QuickContinuePod(pod = it) } + invitations.filter { invite -> pods.none { it.id == invite.podId } }
        .map { QuickContinuePod(invitation = it) })
        .sortedWith(panelOrder({ it.id }, { it.updatedAt }) {
            when {
                it.invitation != null -> 2
                it.pod?.attention == PodAttention.YOUR_PICK || it.pod?.attention == PodAttention.BUILD -> 0
                else -> 1
            }
        }).take(3)

private fun <T> panelOrder(id: (T) -> String, time: (T) -> String, priority: (T) -> Int): Comparator<T> =
    Comparator { a, b ->
        val rank = priority(a).compareTo(priority(b))
        val activity = Instant.parse(time(a)).compareTo(Instant.parse(time(b)))
        when {
            rank != 0 -> rank
            activity != 0 -> if (priority(a) == 0) activity else -activity
            else -> id(a).compareTo(id(b))
        }
    }

internal fun quickContinueMove(game: GameSummary, accountId: String): String = when (game.attentionHolder) {
    accountId -> "Your move"
    game.opponent(accountId).accountId -> "${game.opponent(accountId).username}'s move"
    else -> "Move not available"
}

internal data class GameRowMove(val label: String, val highlighted: Boolean)

internal fun gameRowMove(game: GameSummary, accountId: String) =
    if (game.lifecycle == GameLifecycle.ACTIVE)
        GameRowMove(quickContinueMove(game, accountId), game.attentionHolder == accountId)
    else GameRowMove(if (game.invitationState == InvitationState.PENDING) "Invitation" else "Setup", false)

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
