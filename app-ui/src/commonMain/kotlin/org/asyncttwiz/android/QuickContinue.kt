package org.asyncttwiz.android

import org.asyncttwiz.protocol.GameLifecycle
import org.asyncttwiz.protocol.GameSummary
import org.asyncttwiz.protocol.PodSummary
import org.asyncttwiz.protocol.PodAttention
import org.asyncttwiz.protocol.PodInvitation
import org.asyncttwiz.protocol.InvitationState
import org.asyncttwiz.appui.parseInstant

/** The one game order shared by the Games screen, the Menu quick list and the drawer. */
enum class GameOrderGroup {
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
fun gameOrderGroup(game: GameSummary, accountId: String): GameOrderGroup = when {
    game.lifecycle == GameLifecycle.ACTIVE -> if (game.attentionHolder == accountId) GameOrderGroup.THEIR_EVENT else GameOrderGroup.OWN_EVENT
    game.lifecycle != GameLifecycle.LOBBY || game.invitationState != InvitationState.PENDING -> GameOrderGroup.OTHER
    incomingInvitation(game, accountId) -> GameOrderGroup.INVITATION
    game.inviter.accountId == accountId -> GameOrderGroup.OWN_EVENT
    else -> GameOrderGroup.OTHER
}

/** Groups in [GameOrderGroup] order; the longest-waiting move first, everything else newest first. */
fun gameListOrder(accountId: String): Comparator<GameSummary> =
    panelOrder({ it.id }, { it.updatedAt }) { gameOrderGroup(it, accountId).ordinal }

fun orderedGames(games: List<GameSummary>, accountId: String): List<GameSummary> =
    games.distinctBy { it.id }.sortedWith(gameListOrder(accountId))

/** Menu panel: the first three of the shared order; declined and ended games are left out. */
fun quickContinueGames(games: List<GameSummary>, accountId: String): List<GameSummary> =
    orderedGames(games.filter { it.lifecycle == GameLifecycle.ACTIVE ||
        it.lifecycle == GameLifecycle.LOBBY && it.invitationState != InvitationState.DECLINED }, accountId).take(3)

/** A pending invitation or pod challenge (held or not) this player still has to answer. */
fun incomingInvitation(game: GameSummary, accountId: String) = game.lifecycle == GameLifecycle.LOBBY &&
    game.invitationState == InvitationState.PENDING && game.recipient.accountId == accountId

/** LOB-018 (owner, 2026-11-25): one row of the side menu's list: a game or game invitation, a pod, or an invitation to a pod. */
sealed interface SideMenuRow {
    val id: String
    /** When it last changed: how long it has waited, or how recent its activity is. */
    val updatedAt: String
    data class Game(val game: GameSummary) : SideMenuRow {
        override val id get() = game.id
        override val updatedAt get() = game.updatedAt
    }
    data class Pod(val pod: PodSummary) : SideMenuRow {
        override val id get() = pod.id
        override val updatedAt get() = pod.updatedAt
    }
    data class PodInvite(val invitation: PodInvitation) : SideMenuRow {
        override val id get() = invitation.podId
        override val updatedAt get() = invitation.invitedAt
    }
}

/** LOB-018: the side menu lists this many games and pods (fewer only when there are fewer). */
const val SideMenuListSize = 5

/** LOB-269: [row]'s waiting dot (LOB-267): a game's, a pod's, and blue for an invitation to a pod. */
fun sideMenuDot(row: SideMenuRow, accountId: String): WaitingDot? = when (row) {
    is SideMenuRow.Game -> gameWaitingDot(row.game, accountId)
    is SideMenuRow.Pod -> podWaitingDot(row.pod)
    is SideMenuRow.PodInvite -> podInvitationWaitingDot
}

/** LOB-018: where [row] sorts: red, blue, green (LOB-268's dot order), then everything else, and last the game invitations
 * you can't answer yet (a challenge held for your deck, LOB-025). */
private fun sideMenuGroup(row: SideMenuRow, accountId: String): Int = when (val dot = sideMenuDot(row, accountId)) {
    WaitingDot.Move -> 0
    WaitingDot.Pick -> 1
    WaitingDot.Build -> 2
    null -> if (row is SideMenuRow.Game && incomingInvitation(row.game, accountId)) 4 else 3
}

/**
 * LOB-018 (owner, 2026-11-25): the side menu's one list: the [SideMenuListSize] most important of the games, the game
 * invitations to answer, the pods and the invitations to pods you are not in yet, games and pods mixed: what lights a red
 * dot, then blue, then green (each longest waiting first, as LOB-022), then the rest (most recent first), and last the
 * invitations you can't answer yet (most recent first). A pod's games are rows of their own; the game or pod open keeps
 * its place (only its dot is left out).
 */
fun sideMenuRows(games: List<GameSummary>, invitations: List<GameSummary>, pods: List<PodSummary>,
    podInvitations: List<PodInvitation>, accountId: String): List<SideMenuRow> {
    val rows = (invitations.filter { incomingInvitation(it, accountId) } + games).distinctBy { it.id }.map { SideMenuRow.Game(it) } +
        orderedPods(pods, podInvitations).map { entry -> entry.pod?.let { SideMenuRow.Pod(it) } ?: SideMenuRow.PodInvite(requireNotNull(entry.invitation)) }
    return rows.sortedWith(Comparator { a, b ->
        val groupA = sideMenuGroup(a, accountId)
        val rank = groupA.compareTo(sideMenuGroup(b, accountId))
        // An unreadable time sorts as the oldest rather than failing the whole list.
        val activity = compareValues(runCatching { parseInstant(a.updatedAt) }.getOrNull(), runCatching { parseInstant(b.updatedAt) }.getOrNull())
        when {
            rank != 0 -> rank
            activity != 0 -> if (groupA <= 2) activity else -activity
            else -> a.id.compareTo(b.id)
        }
    }).take(SideMenuListSize)
}

data class QuickContinuePod(val pod: PodSummary? = null, val invitation: PodInvitation? = null) {
    val id: String get() = pod?.id ?: requireNotNull(invitation).podId
    val updatedAt: String get() = pod?.updatedAt ?: requireNotNull(invitation).invitedAt
}

/** The pod order shared by the Pods screen, the Menu panel and the drawer, mirroring the game order (LOB-022). */
enum class PodOrderGroup {
    /** Your pick or your deck build is waiting: longest waiting first. */
    YOURS,
    /** Pod invitations waiting for your answer: newest first. */
    INVITATION,
    /** Everything else: newest first. */
    OTHER,
}

fun podOrderGroup(entry: QuickContinuePod): PodOrderGroup = when {
    entry.invitation != null -> PodOrderGroup.INVITATION
    entry.pod?.attention == PodAttention.YOUR_PICK || entry.pod?.attention == PodAttention.BUILD -> PodOrderGroup.YOURS
    else -> PodOrderGroup.OTHER
}

/** Pods and the invitations to pods you are not yet in, in the shared pod order. */
fun orderedPods(pods: List<PodSummary>, invitations: List<PodInvitation>): List<QuickContinuePod> =
    (pods.distinctBy { it.id }.map { QuickContinuePod(pod = it) } + invitations.distinctBy { it.podId }
        .filter { invite -> pods.none { it.id == invite.podId } }.map { QuickContinuePod(invitation = it) })
        .sortedWith(panelOrder({ it.id }, { it.updatedAt }) { podOrderGroup(it).ordinal })

/** Menu panel: the first three of the shared pod order. */
fun quickContinuePods(pods: List<PodSummary>, invitations: List<PodInvitation>): List<QuickContinuePod> =
    orderedPods(pods, invitations).take(3)

private fun <T> panelOrder(id: (T) -> String, time: (T) -> String, priority: (T) -> Int): Comparator<T> =
    Comparator { a, b ->
        val rank = priority(a).compareTo(priority(b))
        // An unreadable time sorts as the oldest rather than failing the whole list.
        val activity = compareValues(runCatching { parseInstant(time(a)) }.getOrNull(), runCatching { parseInstant(time(b)) }.getOrNull())
        when {
            rank != 0 -> rank
            activity != 0 -> if (priority(a) == 0) activity else -activity
            else -> id(a).compareTo(id(b))
        }
    }

fun quickContinueMove(game: GameSummary, accountId: String): String = when (game.attentionHolder) {
    accountId -> "Your move"
    game.opponent(accountId).accountId -> "${game.opponent(accountId).username}'s move"
    else -> "Move not available"
}

data class GameRowMove(val label: String, val highlighted: Boolean)

fun gameRowMove(game: GameSummary, accountId: String) =
    if (game.lifecycle == GameLifecycle.ACTIVE)
        GameRowMove(quickContinueMove(game, accountId), game.attentionHolder == accountId)
    else GameRowMove(if (game.invitationState == InvitationState.PENDING) "Invitation" else "Setup", false)
