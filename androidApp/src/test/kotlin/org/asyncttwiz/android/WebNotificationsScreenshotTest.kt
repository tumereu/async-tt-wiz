package org.asyncttwiz.android

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import kotlinx.coroutines.runBlocking
import org.asyncttwiz.appui.AppPlatform
import org.asyncttwiz.protocol.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The browser's Settings rows and the Games list's hint (docs/specs/web-client.md §9.1, §9.2), drawn as the web shell
 * provides them, at phone size. Android never shows them (no [LocalWebNotifications] there). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w412dp-h892dp-port-mdpi")
class WebNotificationsScreenshotTest {
    @get:Rule val compose = createComposeRule()

    private class Browser(var answer: BrowserPermission) : BrowserPlatform {
        var permission: BrowserPermission = BrowserPermission.Undecided
        override fun permission() = permission
        override suspend fun requestPermission(): BrowserPermission { permission = answer; return answer }
        override suspend fun subscribe(serverKey: String) =
            WebPushSubscription("https://fcm.googleapis.com/fcm/send/abc", "B" + "A".repeat(86), "A".repeat(22))
        override suspend fun unsubscribe() = Unit
        override fun install() = BrowserInstall.Prompt
        override suspend fun promptInstall() = false
    }

    private class Server : WebPushServer {
        val registered = mutableListOf<RegisterInstallation>()
        override suspend fun key(token: String) = "key"
        override suspend fun register(token: String, installation: RegisterInstallation) { registered += installation }
        override suspend fun unregister(token: String, installationId: String) = Unit
    }

    private fun web(answer: BrowserPermission, server: Server = Server()): WebNotifications {
        val preferences = AppPlatform.current.preferences("web-notifications-shot").also { it.edit().clear().commit() }
        return WebNotifications(Browser(answer), preferences, server) { "installation" }.also {
            runBlocking { it.session(WebPushOwner(ScreenFixtures.alex.accountId, "binding", "token")) }
        }
    }

    // WEB-050, WEB-051, WEB-054: Settings → This browser, notifications turned on with its kinds, and Install app
    @Test fun settingsRowsWithNotificationsOn() {
        val server = Server()
        val web = web(BrowserPermission.Granted, server)
        compose.setContent { ScreenTheme { CompositionLocalProvider(LocalWebNotifications provides web) {
            Surface(Modifier.fillMaxSize()) { NoRipples { SettingsContent(null, logout = {}) } }
        } } }
        compose.onNodeWithText("Invitations").assertDoesNotExist()
        compose.onNodeWithTag("browser-notifications").performScrollTo().performClick()
        compose.waitUntil(5_000) { web.state.on }
        compose.onNodeWithTag("notification-kind-games").performScrollTo().performClick()
        compose.waitUntil(5_000) { server.registered.lastOrNull()?.webPush?.games == false }
        assertEquals(listOf(true, false), server.registered.map { it.webPush?.games })
        compose.onNodeWithTag("install-app").performScrollTo()
        compose.onNodeWithTag("browser-settings").captureShot("$SCREENSHOTS/web/browser-settings.png",
            "Settings in a browser: Browser notifications turned on (the browser allowed them), its three kinds with Game activity switched off, and Install app with the browser's prompt.")
    }

    // WEB-050: the browser refused: the switch is back off with where to change it
    @Test fun blockedNotifications() {
        val web = web(BrowserPermission.Denied)
        compose.setContent { ScreenTheme { CompositionLocalProvider(LocalWebNotifications provides web) {
            Surface(Modifier.fillMaxSize()) { NoRipples { SettingsContent(null, logout = {}) } }
        } } }
        compose.onNodeWithTag("browser-notifications").performScrollTo().performClick()
        compose.waitUntil(5_000) { web.state.note != null }
        compose.onNodeWithTag("browser-notifications").assertIsOff()
        compose.onNodeWithTag("browser-notifications-note").assertTextEquals(WebNotifications.BLOCKED)
        compose.onNodeWithTag("browser-settings").captureShot("$SCREENSHOTS/web/browser-notifications-blocked.png",
            "Settings in a browser after Browser notifications was turned on and the browser refused: the switch is off again, saying it is blocked in the browser's site settings.")
    }

    // WEB-053, LOB-023: the Games list's hint above the games; ✕ puts it away
    @Test fun gamesListHint() {
        val web = web(BrowserPermission.Granted)
        val fixtures = ScreenFixtures().also { it.configure("play") }
        compose.setContent { ScreenTheme { CompositionLocalProvider(LocalWebNotifications provides web) {
            Surface(Modifier.fillMaxSize()) { NoRipples { GamesContent(fixtures.lobby, ScreenFixtures.alex.accountId) } }
        } } }
        compose.onNodeWithText("Get notified when it's your move").assertIsDisplayed()
        val hint = compose.onNodeWithTag("notifications-hint").fetchSemanticsNode().boundsInRoot
        val games = compose.onNodeWithText("GAMES").fetchSemanticsNode().boundsInRoot
        assertTrue(hint.bottom < games.top, "the hint above the games")
        captureShot("$SCREENSHOTS/web/games-hint.png",
            "The Games list in a browser with notifications off: the hint card \"Get notified when it's your move\" with Turn on and ✕ above the games.")
        compose.onNodeWithTag("notifications-hint-close").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("notifications-hint").assertDoesNotExist()
    }
}
