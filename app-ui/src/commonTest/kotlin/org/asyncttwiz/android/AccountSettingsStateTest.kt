package org.asyncttwiz.android

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.asyncttwiz.protocol.AccountSettings
import org.asyncttwiz.protocol.NotificationKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class AccountSettingsStateTest {
    /** The account's settings on the server; its reads wait for [read] when one is given. */
    private class Server(var stored: AccountSettings) : AccountSettingsSource {
        var read: CompletableDeferred<Unit>? = null
        override suspend fun cached() = stored
        override suspend fun refresh(): AccountSettings { val was = stored; read?.await(); return was }
        override suspend fun set(settings: AccountSettings) { stored = settings }
    }

    // LOB-276 (full e2e run 2026-11-27, NotificationSettingsTest): a switch pressed while Settings' own read of the server is
    // on its way stays as pressed when that read comes back with what the server had before the press
    @Test fun aSwitchPressedWhileTheSettingsAreReadStaysAsPressed() = runTest {
        val server = Server(AccountSettings()).apply { read = CompletableDeferred() }
        val state = AccountSettingsState(server, this)
        state.load()
        runCurrent()
        assertTrue(!state.settings!!.notifications.allows(NotificationKind.Chat), "Chat off by default")
        state.setNotification(NotificationKind.Chat, true)
        runCurrent()
        assertTrue(server.stored.notifications.allows(NotificationKind.Chat), "Chat on on the server")
        server.read!!.complete(Unit)
        runCurrent()
        assertTrue(state.settings!!.notifications.allows(NotificationKind.Chat), "the switch still on")
    }

    // LOB-276: with nothing pressed meanwhile, the server's settings replace the device's copy
    @Test fun theServersSettingsReplaceTheCopyShownFirst() = runTest {
        val server = Server(AccountSettings())
        val state = AccountSettingsState(object : AccountSettingsSource by server {
            override suspend fun cached() = AccountSettings()
            override suspend fun refresh() = AccountSettings().let { it.copy(notifications = it.notifications.with(NotificationKind.Chat, true)) }
        }, this)
        state.load()
        runCurrent()
        assertEquals(true, state.settings?.notifications?.allows(NotificationKind.Chat))
    }
}
