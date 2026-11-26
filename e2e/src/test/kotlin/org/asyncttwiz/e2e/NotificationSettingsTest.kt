package org.asyncttwiz.e2e

import android.app.Notification
import android.app.NotificationManager
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.isOff
import androidx.compose.ui.test.isOn
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import kotlinx.coroutines.runBlocking
import org.asyncttwiz.android.AccountApi
import org.asyncttwiz.android.ActivityApi
import org.asyncttwiz.android.ActivityDelivery
import org.asyncttwiz.android.ActivityNotifications
import org.asyncttwiz.android.ActivityOwner
import org.asyncttwiz.android.ActivityWatermark
import org.asyncttwiz.android.ActivityWindowStore
import org.asyncttwiz.android.ShellDestination
import org.asyncttwiz.protocol.*
import org.junit.Test
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Notification settings (owner, 2026-11-26): Settings → Notifications, kept on the server for the account, decides what
 * every phone and browser of it is notified of. Bob has a second phone: the app's notification code and its feed poll (the
 * path without push); his first phone and his browser show the same Settings.
 */
class NotificationSettingsTest : E2e() {
    /** Bob's second phone: its notifications, its delivery state, and the feed poll the app's worker makes. */
    private inner class SecondPhone(val account: TestAccount) {
        val owner = ActivityOwner(account.id, UUID.randomUUID().toString())
        val notifications = ActivityNotifications(bob.activity).also {
            org.robolectric.Shadows.shadowOf(bob.activity.application).grantPermissions(android.Manifest.permission.POST_NOTIFICATIONS)
            it.channels()
        }
        private var cursor: String? = null
        private val marks = mutableMapOf<String, ActivityWatermark>()
        private var pending = mapOf<String, PendingActivity>()
        val delivery = ActivityDelivery(object : ActivityWindowStore {
            override fun cursor() = cursor
            override fun watermark(key: String) = marks[key]
            override fun save(cursor: String, marks: Map<String, ActivityWatermark>): Boolean {
                this@SecondPhone.cursor = cursor; this@SecondPhone.marks += marks; return true
            }
            override fun pending() = pending
            override fun saveWindow(cursor: String, marks: Map<String, ActivityWatermark>, pending: Map<String, PendingActivity>): Boolean {
                this@SecondPhone.pending = pending; return save(cursor, marks)
            }
        }) { notice -> notifications.post(owner, notice) }.also { it.bind(owner) }

        /** One poll, as the app's worker makes it (ActivityWorker): pages applied, Android's channels asked. */
        fun poll() = runBlocking {
            ActivityApi(E2eStack.origin).use { api ->
                do {
                    val page = api.page(account.token, cursor)
                    check(delivery.apply(owner, page) { notifications.allowed(it) })
                    notifications.settle(owner, page, delivery)
                } while (page.hasMore)
            }
        }

        private var polled = 0L
        fun pollNowAndThen() { if (System.nanoTime() - polled > 1_000_000_000L) { polled = System.nanoTime(); poll() } }

        private fun shown(key: String) = bob.activity.getSystemService(NotificationManager::class.java).activeNotifications
            .singleOrNull { it.tag == "activity:${owner.binding}:$key" }?.notification
        /** The text of the notification of [key] showing on this phone, null when none shows. */
        fun text(key: String): String? = shown(key)?.extras?.getCharSequence(Notification.EXTRA_TEXT)?.toString()
        /** Its channel (LOB-072). */
        fun channel(key: String): String? = shown(key)?.channelId
        fun clearAll() = notifications.cancelAll()
    }

    private fun notificationSettings(account: TestAccount) =
        runBlocking { AccountApi(E2eStack.origin).use { it.accountSettings(account.token) } }.notifications

    // LOB-276, LOB-277, LOB-072, WEB-051 (owner, 2026-11-26): Alice's chat message shows on Bob's second phone as the chat's
    // notification, in the Chat channel; Bob switches Chat off in Settings on his first phone: the server keeps it for the
    // account, and Alice's next message notifies none of his devices (an invitation still does; the server's browser pushes
    // follow the same switch, ChatNoticeTest); Bob switches it on again in Settings in his browser, and her next message
    // notifies his phone again
    @Test fun aKindSwitchedOffInSettingsNotifiesNoDeviceOfTheAccount() {
        launchPlayers()
        val phone = SecondPhone(bob.account)
        phone.poll() // the feed's first page: nothing shown from it
        val game = ServerSetup.invitation(alice.account, bob.account, "E2E chat ${UUID.randomUUID().toString().take(6)}")
        val key = "chat:${game.id}"
        ServerSetup.gameChat(alice.account, game, "Hello Bob")
        waitUntil("the chat's notification on Bob's second phone") { phone.pollNowAndThen(); phone.text(key) == "Hello Bob" }
        assertEquals("notice_chat", phone.channel(key))

        // Bob's first phone: Settings → Notifications → Chat off
        bob.auth.navigate(ShellDestination.SETTINGS)
        waitUntil("Bob's Chat switch") { bob.shows(hasTestTag("notification-kind-chat") and isOn()) }
        bob.tagged("notification-kind-chat").performScrollTo().performClick()
        waitUntil("Chat off on the server") { !notificationSettings(bob.account).chat }
        assertTrue(bob.shows(hasTestTag("notification-kind-chat") and isOff()))
        phone.clearAll()

        E2eBrowser().use { web ->
            try {
                web.signIn(bob.account, "/settings")
                web.waitFor("#notification-kind-chat", 120_000.0)

                // Alice's next message: no device is told of it; an invitation of hers after it still notifies
                ServerSetup.gameChat(alice.account, game, "Still there?")
                val invitation = ServerSetup.invite(alice.account, bob.account, "E2E after the chat")
                waitUntil("the invitation's notification on Bob's second phone") {
                    phone.pollNowAndThen(); phone.text("invite:${invitation.invitationId}") != null ||
                        phone.channel("invite:${invitation.invitationId}") != null
                }
                assertNull(phone.text(key), "Chat is off: no notification of Alice's message")

                // Bob's browser: Settings → Notifications → Chat on again; the server keeps it for all his devices
                web.page.mouse().move(200.0, 400.0)
                waitUntil("Chat on screen in Bob's browser") {
                    val box = web.page.locator("#notification-kind-chat").first().boundingBox()
                    (box != null && box.height > 0 && box.y > 60 && box.y + box.height < 800).also {
                        if (!it) web.page.mouse().wheel(0.0, if (box != null && box.y <= 60) -200.0 else 200.0) }
                }
                web.tap("#notification-kind-chat")
                waitUntil("Chat on again on the server") { notificationSettings(bob.account).chat }
                ServerSetup.gameChat(alice.account, game, "Back on?")
                waitUntil("the chat's notification again") { phone.pollNowAndThen(); phone.text(key)?.endsWith("Back on?") == true }
                val faults = web.faults()
                assertTrue(faults.isEmpty(), "The page reported faults:\n${faults.joinToString("\n")}")
            } catch (failure: Throwable) {
                web.evidence("NotificationSettingsTest")
                throw failure
            }
        }
    }
}
