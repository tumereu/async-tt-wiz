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
            .putExtra("kind", notice.hint.kind.name)
        val pending = PendingIntent.getActivity(context, 0, route, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = Notification.Builder(context, ActivityChannels.channel(notice.hint.kind))
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(if (notice.hint.kind.isPodActivity()) podActivityText(notice.hint.kind, podName(notice.hint.gameId))
                else if (notice.hint.kind == ActivityKind.INVITATION) "New invitation" else "Game activity")
            .setContentText(if (notice.hint.kind.isPodActivity()) "Open Pods to view current activity."
                else if (notice.hint.kind == ActivityKind.INVITATION) "Open Games to view the invitation." else "Open the game to catch up.")
            .setContentIntent(pending).setAutoCancel(true).setOnlyAlertOnce(notice.silent)
            // Platform Notification.Builder has no setSilent. A child-only group
            // with summary-only alerts also silences an extension after dismissal.
            .setGroup("activity:${owner.binding}:${notice.key}")
            .setGroupAlertBehavior(if (notice.silent) Notification.GROUP_ALERT_SUMMARY else Notification.GROUP_ALERT_ALL)
            .setVisibility(Notification.VISIBILITY_PRIVATE).build()
        manager.notify("activity:${owner.binding}:${notice.key}", 1, notification)
    }
    fun cancelGame(owner: ActivityOwner, game: String) { manager.cancel("activity:${owner.binding}:game:$game", 1) }
    fun cancelAll() { manager.cancelAll() }
}
