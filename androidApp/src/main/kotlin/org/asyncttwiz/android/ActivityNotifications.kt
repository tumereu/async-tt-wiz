package org.asyncttwiz.android

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import org.asyncttwiz.protocol.ActivityKind
import org.asyncttwiz.protocol.GameNoticeContent
import org.asyncttwiz.protocol.ResumePoint
import org.asyncttwiz.protocol.SuggestedResult

/** [subText]: the recipient's own deck in the game (I-D57), shown in the notification's header. */
internal data class GameNotificationText(val title: String, val text: String, val whenMillis: Long, val subText: String? = null)
internal fun gameNotificationText(content: GameNoticeContent) = GameNotificationText(
    "${content.gameName} · vs ${content.opponentName}",
    (content.gameOver?.let { if (content.byRules) "Game over · ${byRulesResult(it)}" // I-D53
        else if (content.conceded) "Game over · ${content.opponentName} conceded"
        else "Game over · ${content.opponentName} accepted ${gameOverResult(it)}" }
        ?: ((if (content.reminder) "${content.opponentName} is waiting for you"
        else if (content.opponentReady) "${content.opponentName} is ready" // LOB-114
        else if (content.branched) "${content.opponentName} responded differently · your later actions were taken back" // LOB-116
        else if (content.yourMove) "Your move"
        else "${content.opponentName}'s move") +
            // I-D57: where you would resume (the point you last saw, never further)
            content.resumeAt?.let { " · ${resumePointText(it, content.opponentName)}" }.orEmpty())) +
        (if (content.newMessage) " · new message" else ""),
    java.time.OffsetDateTime.parse(content.lastEventAt).toInstant().toEpochMilli(), content.deckName)

/** I-D57: the point a game notification says you would resume at, in the table's own words (the turn square, TRN-007):
 * "Turn 5, your turn, Upkeep" or "Turn 5, <opponent>'s turn, End step"; the opening is "Mulliganing" (TRN-027). */
internal fun resumePointText(point: ResumePoint, opponentName: String): String =
    if (point.turn == 0) "Mulliganing"
    else "Turn ${point.turn}, ${if (point.yours) "your" else "$opponentName's"} turn" + point.step?.let { ", ${it.label}" }.orEmpty()

/** Game history §4.5: the cleanup's warning 3 days before a game in play is deleted: "<game> · vs <opponent>" and
 * "Removed in 3 days if nobody plays". */
internal fun gameRemovalText(hint: org.asyncttwiz.protocol.ActivityHint): Pair<String, String> =
    "${hint.resourceName ?: "Game"} · vs ${hint.opponentName ?: "your opponent"}" to "Removed in 3 days if nobody plays"

/** I-D57: a pod notification's text: the set or cube the pod drafts. */
internal fun podNotificationDetail(hint: org.asyncttwiz.protocol.ActivityHint): String? =
    hint.sourceName?.takeIf { hint.kind.isPodActivity() }

/** I-D53: the rules' result, from the notified player's side. */
internal fun byRulesResult(outcome: SuggestedResult) = when (outcome) {
    SuggestedResult.WIN -> "You won"
    SuggestedResult.LOSS -> "You lost"
    SuggestedResult.DRAW -> "Draw"
}

/** LOB-111: the accepted suggestion, from its author's (the notified player's) side. */
private fun gameOverResult(outcome: SuggestedResult) = when (outcome) {
    SuggestedResult.WIN -> "your win"
    SuggestedResult.LOSS -> "your loss"
    SuggestedResult.DRAW -> "the draw"
}

object ActivityChannels {
    const val INVITATIONS = "invitations"
    const val GAME_ACTIVITY = "game_activity"
    const val DRAFT_ACTIVITY = "draft_activity"
    val names = mapOf(INVITATIONS to "Invitations", GAME_ACTIVITY to "Game activity", DRAFT_ACTIVITY to "Draft activity")
    fun channel(kind: ActivityKind) = when {
        kind == ActivityKind.INVITATION || kind == ActivityKind.POD_INVITATION -> INVITATIONS
        kind.isPodActivity() -> DRAFT_ACTIVITY
        else -> GAME_ACTIVITY
    }
    fun allowed(permission: Boolean, applicationEnabled: Boolean, channelEnabled: Boolean) =
        permission && applicationEnabled && channelEnabled
}

internal object ActivityPermissionPrompt {
    fun shouldPrompt(sdk: Int, signedIn: Boolean, destination: ShellDestination, selectedGame: String?,
        asked: Boolean, catalogBlocked: Boolean, granted: Boolean): Boolean =
        sdk >= 33 && signedIn && !asked && !catalogBlocked && !granted &&
            ((destination == ShellDestination.GAMES && selectedGame == null) || destination == ShellDestination.PODS)
}

class ActivityNotifications(private val context: Context, private val podName: (String) -> String? = { null }) {
    private val manager = context.getSystemService(NotificationManager::class.java)
    fun channels() { manager.createNotificationChannels(ActivityChannels.names.map { (id, name) ->
        NotificationChannel(id, name, NotificationManager.IMPORTANCE_DEFAULT)
    }) }
    fun allowed(kind: ActivityKind): Boolean = ActivityChannels.allowed(
        Build.VERSION.SDK_INT < 33 || context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED,
        manager.areNotificationsEnabled(), manager.getNotificationChannel(ActivityChannels.channel(kind))?.importance != NotificationManager.IMPORTANCE_NONE)
    fun post(owner: ActivityOwner, notice: ActivityNotice) {
        // Recheck OS controls immediately before posting; never change a user's existing channel.
        if (!allowed(notice.hint.kind)) return
        val route = Intent(context, MainActivity::class.java).setAction("org.asyncttwiz.ACTIVITY")
            .setData(android.net.Uri.parse("atw-activity://${owner.binding}/${notice.key}"))
            .putExtra("account", owner.accountId).putExtra("binding", owner.binding)
            .putExtra("game", notice.hint.gameId).putExtra("identity", notice.hint.identity)
            .putExtra("kind", notice.hint.kind.name).putExtra("pod", notice.hint.podId?.takeIf { notice.hint.held })
        val pending = PendingIntent.getActivity(context, 0, route, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val gameText = notice.hint.notice?.let(::gameNotificationText)
        val removal = if (notice.hint.kind == ActivityKind.GAME_REMOVAL) gameRemovalText(notice.hint) else null
        val notification = Notification.Builder(context, ActivityChannels.channel(notice.hint.kind))
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(if (notice.hint.kind == ActivityKind.INVITATION || notice.hint.kind == ActivityKind.POD_INVITATION)
                    invitationText(notice.hint, podName(notice.hint.gameId))
                else if (notice.hint.kind.isPodActivity()) podActivityText(notice.hint.kind, notice.hint.resourceName ?: podName(notice.hint.gameId))
                else removal?.first ?: gameText?.title ?: "Game activity")
            // I-D57: a pod's notice names the set or cube it drafts; a game's names your deck in its header
            .setContentText(removal?.second ?: gameText?.text ?: podNotificationDetail(notice.hint))
            .apply { gameText?.let { setWhen(it.whenMillis); setShowWhen(true); it.subText?.let(::setSubText)
                setStyle(Notification.BigTextStyle().bigText(it.text)) } }
            .setContentIntent(pending).setAutoCancel(true).setOnlyAlertOnce(notice.silent)
            .apply { if (notice.hint.kind == ActivityKind.GAME || notice.hint.kind in WaitingChecks.POD_KINDS)
                setDeleteIntent(dismissal(owner, notice, gameText)) }
            // Platform Notification.Builder has no setSilent. A child-only group
            // with summary-only alerts also silences an extension after dismissal.
            .setGroup("activity:${owner.binding}:${notice.key}")
            .setGroupAlertBehavior(if (notice.silent) Notification.GROUP_ALERT_SUMMARY else Notification.GROUP_ALERT_ALL)
            .setVisibility(Notification.VISIBILITY_PRIVATE).build()
        manager.notify("activity:${owner.binding}:${notice.key}", 1, notification)
    }
    /** Swiping a game or pod pick/build notice away (any source) is remembered against its latest event; see [WaitingChecks]. */
    private fun dismissal(owner: ActivityOwner, notice: ActivityNotice, text: GameNotificationText?): PendingIntent {
        val pod = notice.hint.kind in WaitingChecks.POD_KINDS
        return PendingIntent.getBroadcast(context, 0, Intent(context, ActivityDismissReceiver::class.java)
            .setData(android.net.Uri.parse("atw-dismiss://${owner.binding}/${notice.key}"))
            .putExtra("binding", owner.binding).putExtra("game", notice.hint.gameId).putExtra("identity", notice.hint.identity)
            .putExtra("pod", pod).putExtra("when", if (pod) WaitingChecks.podMark(notice.hint) else text?.whenMillis ?: Long.MAX_VALUE),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }
    /** Game IDs whose notification (push, feed or fallback; same tag) is showing now. */
    fun showingGames(owner: ActivityOwner): Set<String> = showing("activity:${owner.binding}:game:")
    /** Pod IDs whose pod notification (feed or fallback; same tag) is showing now. */
    fun showingPods(owner: ActivityOwner): Set<String> = showing("activity:${owner.binding}:pod:")
    private fun showing(prefix: String): Set<String> = runCatching { manager.activeNotifications }.getOrNull().orEmpty()
        .mapNotNull { it.tag?.takeIf { tag -> tag.startsWith(prefix) }?.removePrefix(prefix) }.toSet()
    /** Clears notifications for what the app now shows; pod invitations are found among active ones. */
    fun cancelShown(owner: ActivityOwner, screen: ActivityScreen) {
        val prefix = "activity:${owner.binding}:"
        screen.game?.let { manager.cancel("${prefix}game:$it", 1) }
        screen.pod?.let { manager.cancel("${prefix}pod:$it", 1) }
        if (screen.podList) runCatching { manager.activeNotifications }.getOrNull().orEmpty().forEach {
            val tag = it.tag ?: return@forEach
            if (tag.startsWith(prefix) && screen.shows(tag.removePrefix(prefix))) manager.cancel(tag, it.id)
        }
    }
    fun cancelAll() { manager.cancelAll() }
}
