package org.asyncttwiz.android

import kotlinx.coroutines.runBlocking
import org.asyncttwiz.appui.Preferences
import org.asyncttwiz.protocol.AccountIdentity
import org.asyncttwiz.protocol.GameLifecycle
import org.asyncttwiz.protocol.GameSummary
import org.asyncttwiz.protocol.InvitationState
import org.asyncttwiz.protocol.RegisterInstallation
import org.asyncttwiz.protocol.WebPushSubscription
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The browser's notification switch, its kinds, the Games list's hint and the tab's title (docs/specs/web-client.md §9.2). */
class WebNotificationsTest {
    private class MemoryPreferences : Preferences {
        val values = mutableMapOf<String, Any>()
        override fun getString(key: String, default: String?) = values[key] as? String ?: default
        override fun getStringSet(key: String, default: Set<String>?) = default
        override fun getBoolean(key: String, default: Boolean) = values[key] as? Boolean ?: default
        override fun getInt(key: String, default: Int) = default
        override fun getLong(key: String, default: Long) = default
        override fun getFloat(key: String, default: Float) = default
        override fun contains(key: String) = key in values
        override fun observe(listener: (String?) -> Unit): () -> Unit = {}
        override fun edit(): Preferences.Editor = object : Preferences.Editor {
            val changes = mutableMapOf<String, Any?>()
            override fun putString(key: String, value: String?): Preferences.Editor { changes[key] = value; return this }
            override fun putStringSet(key: String, value: Set<String>?): Preferences.Editor = this
            override fun putBoolean(key: String, value: Boolean): Preferences.Editor { changes[key] = value; return this }
            override fun putInt(key: String, value: Int): Preferences.Editor = this
            override fun putLong(key: String, value: Long): Preferences.Editor = this
            override fun putFloat(key: String, value: Float): Preferences.Editor = this
            override fun remove(key: String): Preferences.Editor { changes[key] = null; return this }
            override fun clear(): Preferences.Editor = this
            override fun apply() { commit() }
            override fun commit(): Boolean {
                changes.forEach { (key, value) -> if (value == null) values.remove(key) else values[key] = value }
                return true
            }
        }
    }

    private class Browser(var permission: BrowserPermission = BrowserPermission.Undecided,
        var answer: BrowserPermission = BrowserPermission.Granted) : BrowserPlatform {
        var subscribed = 0
        var unsubscribed = 0
        var subscription: WebPushSubscription? = WebPushSubscription("https://fcm.googleapis.com/fcm/send/abc", "B" + "A".repeat(86), "A".repeat(22))
        var installState: BrowserInstall = BrowserInstall.Prompt
        override fun permission() = permission
        override suspend fun requestPermission(): BrowserPermission { permission = answer; return answer }
        override suspend fun subscribe(serverKey: String): WebPushSubscription? { subscribed++; return subscription }
        override suspend fun unsubscribe() { unsubscribed++ }
        override fun install() = installState
        override suspend fun promptInstall(): Boolean { installState = BrowserInstall.Installed; return true }
    }

    private class Server(var key: String? = "server-key") : WebPushServer {
        val registered = mutableListOf<RegisterInstallation>()
        val unregistered = mutableListOf<String>()
        override suspend fun key(token: String) = key
        override suspend fun register(token: String, installation: RegisterInstallation) { registered += installation }
        override suspend fun unregister(token: String, installationId: String) { unregistered += installationId }
    }

    private val owner = WebPushOwner("account", "binding", "token")

    private fun notifications(browser: Browser, server: Server, preferences: Preferences = MemoryPreferences()) =
        WebNotifications(browser, preferences, server) { "installation" }

    // WEB-050, WEB-053: off by default with the hint showing; turning on asks the browser, then registers this browser's
    // subscription with every kind for the signed-in account; the hint is gone for good
    @Test fun turningOnAsksTheBrowserAndRegistersTheSubscription() = runBlocking {
        val preferences = MemoryPreferences()
        val browser = Browser(); val server = Server()
        val web = notifications(browser, server, preferences)
        assertFalse(web.state.on); assertTrue(web.state.showHint)
        web.session(owner)
        assertTrue(server.registered.isEmpty(), "nothing registered while off")
        web.turnOn()
        assertTrue(web.state.on); assertNull(web.state.note); assertFalse(web.state.showHint)
        val registration = server.registered.single()
        assertEquals("installation", registration.installationId); assertEquals("binding", registration.binding)
        assertEquals(browser.subscription, registration.webPush)
        val again = notifications(browser, server, preferences)
        assertTrue(again.state.on); assertFalse(again.state.showHint)
    }

    // WEB-050: refused, the switch goes back off and says where it is blocked; a dismissed prompt just leaves it off; a
    // browser without notifications and a server without them say so
    @Test fun refusedDismissedUnsupportedAndNotSetUp() = runBlocking {
        val denied = notifications(Browser(answer = BrowserPermission.Denied), Server()).also { it.session(owner); it.turnOn() }
        assertFalse(denied.state.on); assertEquals(WebNotifications.BLOCKED, denied.state.note)
        assertFalse(denied.state.showHint, "WEB-053: no hint where the browser blocks them")
        val dismissed = notifications(Browser(answer = BrowserPermission.Undecided), Server()).also { it.session(owner); it.turnOn() }
        assertFalse(dismissed.state.on); assertNull(dismissed.state.note); assertTrue(dismissed.state.showHint)
        val unsupported = notifications(Browser(permission = BrowserPermission.Unsupported), Server())
        assertFalse(unsupported.state.showHint)
        unsupported.session(owner); unsupported.turnOn()
        assertFalse(unsupported.state.on); assertEquals(WebNotifications.UNSUPPORTED, unsupported.state.note)
        val server = Server(key = null)
        val noKey = notifications(Browser(), server).also { it.session(owner); it.turnOn() }
        assertFalse(noKey.state.on); assertEquals(WebNotifications.NOT_SET_UP, noKey.state.note); assertTrue(server.registered.isEmpty())
        val noSubscription = notifications(Browser().apply { subscription = null }, Server()).also { it.session(owner); it.turnOn() }
        assertFalse(noSubscription.state.on); assertEquals(WebNotifications.FAILED, noSubscription.state.note)
    }

    // WEB-051: each kind is a switch kept in the browser, and the server is told at once
    @Test fun kindsAreKeptAndRegistered() = runBlocking {
        val preferences = MemoryPreferences()
        val server = Server()
        val web = notifications(Browser(), server, preferences)
        web.session(owner); web.turnOn()
        assertEquals(NotificationKind.all.toSet(), web.state.kinds)
        web.kind(NotificationKind.Games, false)
        val registration = server.registered.last().webPush!!
        assertTrue(registration.invitations); assertFalse(registration.games); assertTrue(registration.drafts)
        assertEquals(setOf(NotificationKind.Invitations, NotificationKind.Drafts), notifications(Browser(), Server(), preferences).state.kinds)
    }

    // WEB-050: turning off tells the server and drops the browser's subscription; a permission taken back in the browser's
    // settings turns the switch off when the page comes back; signing in registers for the new account
    @Test fun offRevokedAndAnotherAccount() = runBlocking {
        val browser = Browser(); val server = Server()
        val web = notifications(browser, server)
        web.session(owner); web.turnOn()
        web.session(WebPushOwner("other", "binding-2", "token-2"))
        assertEquals("binding-2", server.registered.last().binding)
        web.turnOff()
        assertFalse(web.state.on); assertEquals(listOf("installation"), server.unregistered); assertEquals(1, browser.unsubscribed)
        web.turnOn(); assertTrue(web.state.on)
        browser.permission = BrowserPermission.Denied
        web.foreground()
        assertFalse(web.state.on); assertFalse(web.state.showHint)
    }

    // WEB-053: ✕ hides the hint for good in this browser
    @Test fun theHintsCloseIsForGood() {
        val preferences = MemoryPreferences()
        val web = notifications(Browser(), Server(), preferences)
        web.dismissHint()
        assertFalse(web.state.showHint)
        assertFalse(notifications(Browser(), Server(), preferences).state.showHint)
    }

    // WEB-054: Install app uses the browser's prompt where there is one
    @Test fun installUsesTheBrowsersPrompt() = runBlocking {
        val browser = Browser()
        val web = notifications(browser, Server())
        assertEquals(BrowserInstall.Prompt, web.state.install)
        web.install()
        assertEquals(BrowserInstall.Installed, web.state.install)
    }

    // WEB-055: "(N) ATW", the games in play waiting for your move
    @Test fun tabTitleCountsGamesWaitingForYou() {
        val me = AccountIdentity("me", "Me"); val them = AccountIdentity("them", "Them")
        fun game(id: String, holder: String?, lifecycle: GameLifecycle = GameLifecycle.ACTIVE) = GameSummary(id, "Game $id",
            "2026-11-14T10:00:00Z", me, them, "1", "i$id", InvitationState.ACCEPTED, lifecycle, attentionHolder = holder)
        assertEquals("ATW", webTabTitle(emptyList(), "me"))
        assertEquals("ATW", webTabTitle(listOf(game("a", "them")), "me"))
        assertEquals("(2) ATW", webTabTitle(listOf(game("a", "me"), game("b", "me"), game("c", "them"),
            game("d", "me", GameLifecycle.LOBBY)), "me"))
        assertEquals("ATW", webTabTitle(listOf(game("a", "me")), null))
    }
}
