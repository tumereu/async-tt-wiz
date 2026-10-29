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

/** The one game order shared by the Games screen, the Menu quick list and the drawer. */
internal enum class GameOrderGroup {
    /** In play and the opponent made the latest event: the viewer holds the move flag. */
    THEIR_EVENT,
    /** Invitations and pod challenges (held or not) waiting for the viewer's answer. */
    INVITATION,
    /** In play with the viewer's own latest move, and invitations the viewer sent. */
    OWN_EVENT,
    /** Not in play: setup, declined, ended. */
    OTHER,
}

/** GameSummary has no latest-event author; the server-computed move flag ([GameSummary.attentionHolder])
 * passes to the viewer exactly when the opponent hands over, so it stands in for "the opponent acted last". */
internal fun gameOrderGroup(game: GameSummary, accountId: String): GameOrderGroup = when {
    game.lifecycle == GameLifecycle.ACTIVE -> if (game.attentionHolder == accountId) GameOrderGroup.THEIR_EVENT else GameOrderGroup.OWN_EVENT
    game.lifecycle != GameLifecycle.LOBBY || game.invitationState != InvitationState.PENDING -> GameOrderGroup.OTHER
    incomingInvitation(game, accountId) -> GameOrderGroup.INVITATION
    game.inviter.accountId == accountId -> GameOrderGroup.OWN_EVENT
    else -> GameOrderGroup.OTHER
}

/** Groups in [GameOrderGroup] order; the longest-waiting move first, everything else newest first. */
internal fun gameListOrder(accountId: String): Comparator<GameSummary> =
    panelOrder({ it.id }, { it.updatedAt }) { gameOrderGroup(it, accountId).ordinal }

internal fun orderedGames(games: List<GameSummary>, accountId: String): List<GameSummary> =
    games.distinctBy { it.id }.sortedWith(gameListOrder(accountId))

/** Menu panel: the first three of the shared order; declined and ended games are left out. */
internal fun quickContinueGames(games: List<GameSummary>, accountId: String): List<GameSummary> =
    orderedGames(games.filter { it.lifecycle == GameLifecycle.ACTIVE ||
        it.lifecycle == GameLifecycle.LOBBY && it.invitationState != InvitationState.DECLINED }, accountId).take(3)

/** A pending invitation or pod challenge (held or not) this player still has to answer. */
internal fun incomingInvitation(game: GameSummary, accountId: String) = game.lifecycle == GameLifecycle.LOBBY &&
    game.invitationState == InvitationState.PENDING && game.recipient.accountId == accountId

/** Drawer Games section: the games and the invitations needing an answer, in the shared order. */
internal fun navigationGameRows(games: List<GameSummary>, invitations: List<GameSummary>, accountId: String): List<GameSummary> =
    orderedGames(invitations.filter { incomingInvitation(it, accountId) } + games, accountId)

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
        // An unreadable time sorts as the oldest rather than failing the whole list.
        val activity = compareValues(runCatching { Instant.parse(time(a)) }.getOrNull(), runCatching { Instant.parse(time(b)) }.getOrNull())
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
