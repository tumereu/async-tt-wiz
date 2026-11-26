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
import org.asyncttwiz.protocol.ActivityHint
import org.asyncttwiz.protocol.ActivityKind
import org.asyncttwiz.protocol.ActivityTexts
import org.asyncttwiz.protocol.NotificationKind

/**
 * LOB-072 (owner, 2026-11-26): one notification channel per kind of notification ([NotificationKind]): Your move, Reminders,
 * Chat, Invitations, Draft, and Game over & warnings, each with its own sound and vibration in Android's settings (LOB-278).
 */
object ActivityChannels {
    /** The three channels before these, deleted (exactly one version of the app exists; a new id keeps none of their settings). */
    val RETIRED = listOf("invitations", "game_activity", "draft_activity")
    fun id(kind: NotificationKind) = "notice_${kind.id}"
    /** Each channel's name by its id, in the order Settings lists the kinds. */
    val names: Map<String, String> get() = NotificationKind.all.associate { id(it) to it.label }
    /** The channel of [hint]'s notification; [reminder]: the app's own reminder of it (LOB-076). */
    fun channel(hint: ActivityHint, reminder: Boolean = false): String = id(NotificationKind.of(hint, reminder))
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
    fun channels() {
        ActivityChannels.RETIRED.filter { manager.getNotificationChannel(it) != null }.forEach {
            manager.deleteNotificationChannel(it)
            AppLog.info("activity", "Notification channel $it deleted (replaced by one per kind)")
        }
        manager.createNotificationChannels(NotificationKind.all.map { kind ->
            NotificationChannel(ActivityChannels.id(kind), kind.label, NotificationManager.IMPORTANCE_DEFAULT).apply { description = kind.explanation }
        })
    }
    /** Whether Android lets the notification of [channel] show: the permission, the app's notifications, the channel. */
    fun allowed(channel: String): Boolean = ActivityChannels.allowed(
        Build.VERSION.SDK_INT < 33 || context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED,
        manager.areNotificationsEnabled(), manager.getNotificationChannel(channel)?.importance != NotificationManager.IMPORTANCE_NONE)
    fun allowed(hint: ActivityHint, reminder: Boolean = false): Boolean = allowed(ActivityChannels.channel(hint, reminder))
    /** Posts [notice]; [reminder]: it is the app's own reminder of what waits (LOB-076), in the Reminders channel. */
    fun post(owner: ActivityOwner, notice: ActivityNotice, reminder: Boolean = false) {
        // Recheck OS controls immediately before posting; never change a user's existing channel.
        val channel = ActivityChannels.channel(notice.hint, reminder)
        if (!allowed(channel)) { AppLog.info("activity", "Notification ${notice.key} not posted: its channel $channel is off in Android"); return }
        val tag = "activity:${owner.binding}:${notice.key}"
        val chat = notice.hint.kind == ActivityKind.CHAT || notice.hint.kind == ActivityKind.POD_CHAT
        // LOB-277: a chat's newer message updates its notification still showing without alerting again
        val silent = notice.silent || chat && runCatching { manager.activeNotifications }.getOrNull().orEmpty().any { it.tag == tag }
        val route = Intent(context, MainActivity::class.java).setAction("org.asyncttwiz.ACTIVITY")
            .setData(android.net.Uri.parse("atw-activity://${owner.binding}/${notice.key}"))
            .putExtra("account", owner.accountId).putExtra("binding", owner.binding)
            .putExtra("game", notice.hint.gameId).putExtra("identity", notice.hint.identity)
            .putExtra("kind", notice.hint.kind.name).putExtra("pod", notice.hint.podId?.takeIf { notice.hint.held })
        val pending = PendingIntent.getActivity(context, 0, route, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val gameText = notice.hint.notice?.let(::gameNotificationText)
        val removal = if (notice.hint.kind == ActivityKind.GAME_REMOVAL) gameRemovalText(notice.hint) else null
        // LOB-277: a chat's: who wrote, where, and the newest message
        val chatText = if (chat) ActivityTexts.chat(notice.hint, podName(notice.hint.gameId)) else null
        val notification = Notification.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(if (chatText != null) chatText.first
                else if (notice.hint.kind == ActivityKind.INVITATION || notice.hint.kind == ActivityKind.POD_INVITATION)
                    invitationText(notice.hint, podName(notice.hint.gameId))
                else if (notice.hint.kind.isPodActivity()) podActivityText(notice.hint.kind, notice.hint.resourceName ?: podName(notice.hint.gameId))
                else removal?.first ?: gameText?.title ?: "Game activity")
            // I-D57: a pod's notice names the set or cube it drafts; a game's names your deck in its header
            .setContentText(chatText?.second ?: removal?.second ?: gameText?.text ?: podNotificationDetail(notice.hint))
            .apply { gameText?.let { setWhen(it.whenMillis); setShowWhen(true); it.subText?.let(::setSubText)
                setStyle(Notification.BigTextStyle().bigText(it.text)) } }
            .apply { chatText?.let { setStyle(Notification.BigTextStyle().bigText(it.second)) } }
            .setContentIntent(pending).setAutoCancel(true).setOnlyAlertOnce(silent)
            .apply { if (notice.hint.kind == ActivityKind.GAME || notice.hint.kind in WaitingChecks.POD_KINDS)
                setDeleteIntent(dismissal(owner, notice, gameText)) }
            // Platform Notification.Builder has no setSilent. A child-only group
            // with summary-only alerts also silences an extension after dismissal.
            .setGroup(tag)
            .setGroupAlertBehavior(if (silent) Notification.GROUP_ALERT_SUMMARY else Notification.GROUP_ALERT_ALL)
            .setVisibility(Notification.VISIBILITY_PRIVATE).build()
        manager.notify(tag, 1, notification)
        AppLog.info("activity", "Notification ${notice.key} posted in $channel${if (silent) ", quietly" else ""}" +
            if (reminder) " (the app's own reminder)" else "")
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
    /** Clears notifications for what the app now shows (LOB-277: a game's or pod's chat's with it); pod invitations are found
     * among active ones. */
    fun cancelShown(owner: ActivityOwner, screen: ActivityScreen) {
        val prefix = "activity:${owner.binding}:"
        screen.keys().forEach { manager.cancel("$prefix$it", 1) }
        if (screen.podList) runCatching { manager.activeNotifications }.getOrNull().orEmpty().forEach {
            val tag = it.tag ?: return@forEach
            if (tag.startsWith(prefix) && screen.shows(tag.removePrefix(prefix))) manager.cancel(tag, it.id)
        }
    }
    /** LOB-078 (owner, 2026-11-19): clears [owner]'s shown notifications whose key the server no longer lists in [open] (nothing
     * waits for them, however the player dealt with it); returns the keys cleared. */
    fun cancelSettled(owner: ActivityOwner, open: Set<String>): List<String> {
        val prefix = "activity:${owner.binding}:"
        val settled = runCatching { manager.activeNotifications }.getOrNull().orEmpty().mapNotNull { shown ->
            val key = shown.tag?.takeIf { it.startsWith(prefix) }?.removePrefix(prefix) ?: return@mapNotNull null
            if (key in open) null else { manager.cancel(shown.tag, shown.id); key }
        }
        if (settled.isNotEmpty()) AppLog.info("activity", "Cleared notifications nothing waits for any more: ${settled.joinToString()}")
        return settled
    }
    /** LOB-242: after [page] is applied, [owner]'s shown notifications the server no longer lists as waiting are cleared, and
     * [delivery] makes the next notice of each alert. */
    fun settle(owner: ActivityOwner, page: org.asyncttwiz.protocol.ActivityPage, delivery: ActivityDelivery) {
        val open = page.openNotices ?: return
        cancelSettled(owner, open.toSet()).takeIf { it.isNotEmpty() }?.let { delivery.settled(owner, it) }
    }
    fun cancelAll() { manager.cancelAll() }
}
