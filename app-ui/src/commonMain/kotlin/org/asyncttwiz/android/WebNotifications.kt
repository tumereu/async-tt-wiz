package org.asyncttwiz.android

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.asyncttwiz.appui.Preferences
import org.asyncttwiz.protocol.GameLifecycle
import org.asyncttwiz.protocol.GameSummary
import org.asyncttwiz.protocol.RegisterInstallation
import org.asyncttwiz.protocol.WebPushSubscription

// The web client's notifications, install and tab title (docs/specs/web-client.md §9.1, §9.2; WEB-050 to WEB-055): the
// logic and the rows here are common code, shown only where the platform provides a browser ([LocalWebNotifications]: the
// web shell's); Android has none of them.

/** WEB-050: whether this browser lets the site show notifications. */
sealed interface BrowserPermission {
    data object Granted : BrowserPermission
    data object Denied : BrowserPermission
    /** Not asked yet, or the prompt was dismissed. */
    data object Undecided : BrowserPermission
    /** The browser has no notifications, service workers or push (or the page isn't a secure one). */
    data object Unsupported : BrowserPermission
}

/** WEB-054: how this browser installs the site. */
sealed interface BrowserInstall {
    /** The page runs as the installed app. */
    data object Installed : BrowserInstall
    /** The browser offers its install prompt. */
    data object Prompt : BrowserInstall
    /** iOS Safari: Share → Add to Home Screen. */
    data object Ios : BrowserInstall
    /** Another browser: from its own menu, if at all. */
    data object Menu : BrowserInstall
}

/** The browser's side of notifications and install (the web shell's, in JavaScript). */
interface BrowserPlatform {
    fun permission(): BrowserPermission
    /** The browser's own permission prompt; what was chosen. */
    suspend fun requestPermission(): BrowserPermission
    /** This browser's push subscription for the server's [serverKey] (made now when there is none, or one for another key);
     * null when the browser couldn't make one. Its kinds are left at their defaults. */
    suspend fun subscribe(serverKey: String): WebPushSubscription?
    suspend fun unsubscribe()
    fun install(): BrowserInstall
    /** Shows the browser's install prompt; whether the site was installed. */
    suspend fun promptInstall(): Boolean
}

/** The server's side: the VAPID key (null when the server sends no browser notifications) and the browser's installation. */
interface WebPushServer {
    suspend fun key(token: String): String?
    suspend fun register(token: String, installation: RegisterInstallation)
    suspend fun unregister(token: String, installationId: String)
}

/** WEB-051: the three kinds of notification (LOB-072's channels), each a switch. */
sealed class NotificationKind(val key: String, val label: String) {
    data object Invitations : NotificationKind("invitations", "Invitations")
    data object Games : NotificationKind("games", "Game activity")
    data object Drafts : NotificationKind("drafts", "Draft activity")
    companion object { val all = listOf(Invitations, Games, Drafts) }
}

/** Who is signed in: their session [token] and this page's [binding] of it (as the activity installation registers it). */
data class WebPushOwner(val accountId: String, val binding: String, val token: String)

/**
 * WEB-050, WEB-051, WEB-053: "Browser notifications", its kinds and the Games list's hint, kept in this browser's
 * [preferences]; the subscription is registered with the server for whoever is signed in ([session]).
 */
class WebNotifications(private val platform: BrowserPlatform, private val preferences: Preferences,
    private val server: WebPushServer, private val installationId: () -> String) {
    /** The switch's state: [on], the kinds switched on, a [note] under it (why it couldn't turn on), [busy] while it turns
     * on or off, and whether the Games list's hint was put away ([hintDismissed]). */
    data class State(val on: Boolean, val kinds: Set<NotificationKind>, val permission: BrowserPermission,
        val install: BrowserInstall, val hintDismissed: Boolean, val note: String? = null, val busy: Boolean = false) {
        /** WEB-053: the hint shows while notifications are off, unless put away or the browser can't or won't show them. */
        val showHint: Boolean get() = !on && !hintDismissed && permission != BrowserPermission.Denied &&
            permission != BrowserPermission.Unsupported
    }

    var state by mutableStateOf(State(preferences.getBoolean(ON, false),
        NotificationKind.all.filter { preferences.getBoolean(it.key, true) }.toSet(), platform.permission(), platform.install(),
        preferences.getBoolean(HINT_DISMISSED, false)))
        private set
    private var owner: WebPushOwner? = null

    /** The signed-in account (null: signed out); while notifications are on its installation is registered with this
     * browser's subscription. */
    suspend fun session(next: WebPushOwner?) {
        if (next == owner) return
        owner = next
        if (next != null && state.on) sync("signed in")
    }

    /** The page came back to the front: the permission may have changed in the browser's settings meanwhile. */
    suspend fun foreground() {
        refresh()
        if (state.on && owner != null) sync("page in front")
    }

    /** Reads the browser's permission and install state again. */
    fun refresh() { state = state.copy(permission = platform.permission(), install = platform.install()) }

    /** WEB-050: the switch turned on (or the hint's Turn on): the browser's prompt, then the subscription. */
    suspend fun turnOn() {
        if (state.busy || state.on) return
        state = state.copy(busy = true, note = null)
        try {
            val permission = if (platform.permission() == BrowserPermission.Unsupported) BrowserPermission.Unsupported
                else platform.requestPermission()
            state = state.copy(permission = permission)
            AppLog.info(TAG, "Browser notifications: permission ${permission.name()}")
            when (permission) {
                BrowserPermission.Unsupported -> { state = state.copy(note = UNSUPPORTED); return }
                BrowserPermission.Denied -> { state = state.copy(note = BLOCKED); return }
                BrowserPermission.Undecided -> return
                BrowserPermission.Granted -> Unit
            }
            state = state.copy(note = subscribe("turned on"))
            if (state.note == null) {
                preferences.edit().putBoolean(ON, true).putBoolean(HINT_DISMISSED, true).apply()
                state = state.copy(on = true, hintDismissed = true)
            }
        } finally { state = state.copy(busy = false) }
    }

    /** The switch turned off: the server forgets this browser, and the browser its subscription. */
    suspend fun turnOff() {
        if (state.busy || !state.on) return
        preferences.edit().putBoolean(ON, false).apply()
        state = state.copy(on = false, note = null, busy = true)
        try {
            val signedIn = owner
            if (signedIn != null) runCatching { server.unregister(signedIn.token, installationId()) }
                .onFailure { AppLog.warn(TAG, "Browser notifications off: the server wasn't told", it) }
            runCatching { platform.unsubscribe() }.onFailure { AppLog.warn(TAG, "Browser notifications off: unsubscribing failed", it) }
            AppLog.info(TAG, "Browser notifications turned off")
        } finally { state = state.copy(busy = false) }
    }

    /** WEB-051: one kind switched; the server is told at once. */
    suspend fun kind(kind: NotificationKind, on: Boolean) {
        preferences.edit().putBoolean(kind.key, on).apply()
        state = state.copy(kinds = if (on) state.kinds + kind else state.kinds - kind)
        AppLog.info(TAG, "Browser notifications: ${kind.label} ${if (on) "on" else "off"}")
        if (state.on) sync("kinds changed")
    }

    /** WEB-053: the hint's ✕: put away for good in this browser. */
    fun dismissHint() {
        preferences.edit().putBoolean(HINT_DISMISSED, true).apply()
        state = state.copy(hintDismissed = true)
        AppLog.info(TAG, "Notifications hint put away")
    }

    /** WEB-054: Settings → Install app, where the browser offers its prompt. */
    suspend fun install() {
        if (platform.install() != BrowserInstall.Prompt) return
        val installed = platform.promptInstall()
        AppLog.info(TAG, "Install prompt: ${if (installed) "installed" else "not installed"}")
        refresh()
    }

    /** Registers this browser's subscription and kinds for the signed-in account; a permission taken back in the browser's
     * settings turns the switch off. */
    private suspend fun sync(why: String) {
        if (platform.permission() != BrowserPermission.Granted) {
            AppLog.info(TAG, "Browser notifications were on, but the browser no longer allows them ($why): off")
            preferences.edit().putBoolean(ON, false).apply()
            state = state.copy(on = false, permission = platform.permission())
            return
        }
        subscribe(why)?.let { AppLog.warn(TAG, "Browser notifications: registering failed ($why): $it") }
    }

    /** The subscription made and registered; null when done, else the note saying why not. */
    private suspend fun subscribe(why: String): String? {
        val signedIn = owner ?: return FAILED
        return try {
            val key = server.key(signedIn.token) ?: return NOT_SET_UP
            val subscription = platform.subscribe(key) ?: return FAILED
            val kinds = state.kinds
            server.register(signedIn.token, RegisterInstallation(installationId(), signedIn.binding, webPush = subscription.copy(
                invitations = NotificationKind.Invitations in kinds, games = NotificationKind.Games in kinds,
                drafts = NotificationKind.Drafts in kinds)))
            AppLog.info(TAG, "Browser notifications registered ($why): ${kinds.joinToString { it.label }} via ${hostOf(subscription.endpoint)}")
            null
        } catch (failure: Exception) {
            AppLog.warn(TAG, "Browser notifications: subscribing failed ($why)", failure)
            FAILED
        }
    }

    companion object {
        const val TAG = "web-notifications"
        const val BLOCKED = "Blocked in your browser's site settings"
        const val UNSUPPORTED = "This browser can't show notifications"
        const val NOT_SET_UP = "Notifications aren't set up on this server"
        const val FAILED = "Couldn't turn notifications on. Try again."
        private const val ON = "on"
        private const val HINT_DISMISSED = "hint-dismissed"
        private fun hostOf(endpoint: String) = endpoint.substringAfter("://").substringBefore('/')
        private fun BrowserPermission.name() = when (this) {
            BrowserPermission.Granted -> "granted"
            BrowserPermission.Denied -> "denied"
            BrowserPermission.Undecided -> "undecided"
            BrowserPermission.Unsupported -> "unsupported"
        }
    }
}

/** The web shell's notifications and install; null on Android (none of the rows show). */
val LocalWebNotifications = staticCompositionLocalOf<WebNotifications?> { null }

/** WEB-055: the browser tab's title, "(N) ATW" with N the games waiting for [accountId]'s move (their "Your move" chips). */
fun webTabTitle(games: List<GameSummary>, accountId: String?): String {
    val waiting = if (accountId == null) 0 else games.count { it.lifecycle == GameLifecycle.ACTIVE && it.attentionHolder == accountId }
    return if (waiting > 0) "($waiting) ATW" else "ATW"
}

/** WEB-050, WEB-051, WEB-054: Settings' "This browser" rows: Browser notifications with its kinds, and Install app. */
@Composable
fun BrowserSettings(web: WebNotifications) {
    val scope = rememberCoroutineScope()
    val state = web.state
    Column(Modifier.fillMaxWidth().testTag("browser-settings")) {
        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("browser-notifications")
            .toggleable(state.on, enabled = !state.busy, role = Role.Switch, onValueChange = { on ->
                scope.launch { if (on) web.turnOn() else web.turnOff() }
            }), verticalAlignment = Alignment.CenterVertically) {
            SettingsLabel("Browser notifications", "When it's your move, and for invitations and drafts, in this browser",
                Modifier.weight(1f).padding(end = 12.dp, top = 4.dp, bottom = 4.dp))
            Switch(checked = state.on, onCheckedChange = null, enabled = !state.busy)
        }
        state.note?.let { note ->
            Text(note, Modifier.fillMaxWidth().padding(bottom = 8.dp).testTag("browser-notifications-note"),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }
        if (state.on) for (kind in NotificationKind.all) {
            val on = kind in state.kinds
            Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(start = 16.dp).testTag("notification-kind-${kind.key}")
                .toggleable(on, role = Role.Switch, onValueChange = { scope.launch { web.kind(kind, it) } }),
                verticalAlignment = Alignment.CenterVertically) {
                SettingsLabel(kind.label, modifier = Modifier.weight(1f).padding(end = 12.dp))
                Switch(checked = on, onCheckedChange = null)
            }
        }
        InstallAppRow(state.install) { scope.launch { web.install() } }
    }
}

/** WEB-054: Settings → "Install app": the browser's prompt, or how to install where there is none. */
@Composable
private fun InstallAppRow(install: BrowserInstall, prompt: () -> Unit) {
    val enabled = install == BrowserInstall.Prompt
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("install-app")
        .clickable(enabled = enabled, role = Role.Button, onClick = prompt),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        val color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (install == BrowserInstall.Installed) .38f else 1f)
        Icon(Icons.Outlined.Download, null, Modifier.size(22.dp), tint = color)
        SettingsLabel(if (install == BrowserInstall.Installed) "Installed" else "Install app", when (install) {
            BrowserInstall.Installed -> "ATW runs as an app on this device"
            BrowserInstall.Prompt -> "Open ATW in its own window, from your home screen or desktop"
            BrowserInstall.Ios -> "In Safari: Share → Add to Home Screen"
            BrowserInstall.Menu -> "Use your browser's menu to install ATW, where it offers that"
        }, Modifier.weight(1f).padding(vertical = 4.dp))
    }
}

/** WEB-053: the Games list's one hint card: "Get notified when it's your move", Turn on and ✕. */
@Composable
fun NotificationsHintCard(web: WebNotifications, modifier: Modifier = Modifier) {
    val scope = rememberCoroutineScope()
    val state = web.state
    OutlinedCard(modifier.fillMaxWidth().testTag("notifications-hint")) {
        Row(Modifier.padding(start = 16.dp, top = 8.dp, bottom = 8.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(Icons.Outlined.NotificationsNone, null, tint = MaterialTheme.colorScheme.primary)
            Column(Modifier.weight(1f)) {
                Text("Get notified when it's your move", style = MaterialTheme.typography.bodyLarge)
                state.note?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
            }
            TextButton({ scope.launch { web.turnOn() } }, enabled = !state.busy, modifier = Modifier.testTag("notifications-hint-on")) {
                Text("Turn on")
            }
            IconButton(web::dismissHint, Modifier.testTag("notifications-hint-close").semantics { contentDescription = "Hide this hint" }) {
                Icon(Icons.Outlined.Close, null)
            }
        }
    }
}
