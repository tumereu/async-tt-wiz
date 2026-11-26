package org.asyncttwiz.android

import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ActivityShownNotificationsTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val manager = context.getSystemService(NotificationManager::class.java)
    private val owner = ActivityOwner("account", "binding")

    private fun post(key: String, binding: String = owner.binding) = manager.notify("activity:$binding:$key", 1,
        Notification.Builder(context, ActivityChannels.id(org.asyncttwiz.protocol.NotificationKind.Draft)).setSmallIcon(android.R.drawable.ic_dialog_info).build())
    private fun active() = manager.activeNotifications.map { it.tag }.toSet()

    // LOB-072
    @Test fun everyNotificationUsesTheLogoSilhouetteAsItsStatusBarIcon() {
        org.robolectric.Shadows.shadowOf(context as android.app.Application).grantPermissions(android.Manifest.permission.POST_NOTIFICATIONS)
        val notifications = ActivityNotifications(context) { "Cube night" }
        notifications.channels()
        val content = org.asyncttwiz.protocol.GameNoticeContent("Friday", "Alex", "2026-10-27T12:34:56+02:00", true)
        org.asyncttwiz.protocol.ActivityKind.entries.forEach { kind ->
            notifications.post(owner, ActivityNotice("${kind.name}:x", org.asyncttwiz.protocol.ActivityHint(kind.name, kind, "x", "1",
                notice = content.takeIf { kind == org.asyncttwiz.protocol.ActivityKind.GAME }), silent = false))
        }
        val posted = manager.activeNotifications
        assertEquals(org.asyncttwiz.protocol.ActivityKind.entries.size, posted.size)
        posted.forEach { assertEquals(R.drawable.ic_notification, it.notification.smallIcon.resId, it.tag) }
    }

    // LOB-242, LOB-243 (owner, 2026-11-19): a feed page's list of what still waits clears every other shown notification of this
    // sign-in (dealt with elsewhere), whatever its kind, and the next notice of each alerts again
    @Test fun aPageClearsTheNotificationsNothingWaitsForAnyMore() {
        ActivityNotifications(context).channels()
        listOf("game:g", "game:other", "pod:p", "pod-invite:a", "invite:i").forEach { post(it) }
        post("game:g", binding = "earlier-sign-in")
        val stored = mutableMapOf("game:other" to ActivityWatermark(4, "x", yourMove = true))
        val delivery = ActivityDelivery(object : ActivityDeliveryStore {
            override fun cursor() = "4"
            override fun watermark(key: String) = stored[key]
            override fun save(cursor: String, marks: Map<String, ActivityWatermark>): Boolean { stored.putAll(marks); return true }
        }) {}.also { it.bind(owner) }
        val page = org.asyncttwiz.protocol.ActivityPage(emptyList(), "4", "0", false, false, emptyList(), listOf("game:g", "pod:p"))
        ActivityNotifications(context).settle(owner, page, delivery)
        assertEquals(setOf("activity:binding:game:g", "activity:binding:pod:p", "activity:earlier-sign-in:game:g"), active())
        assertEquals(ActivityWatermark(4, "x", yourMove = false, alertNext = true), stored["game:other"])
        // A page that doesn't tell (made on the device) clears nothing
        ActivityNotifications(context).settle(owner, page.copy(openNotices = null), delivery)
        assertEquals(3, active().size)
    }

    // DRF-138, LOB-078
    @Test fun openingAGamePodOrThePodListClearsOnlyItsOwnNotifications() {
        ActivityNotifications(context).channels()
        listOf("game:g", "game:other", "pod:p", "pod:other", "pod-invite:a", "pod-invite:b", "invite:i").forEach { post(it) }
        post("pod-invite:c", binding = "earlier-sign-in")
        val notifications = ActivityNotifications(context)
        notifications.cancelShown(owner, ActivityScreen(game = "g"))
        notifications.cancelShown(owner, ActivityScreen(pod = "p"))
        assertEquals(setOf("game:other", "pod:other", "pod-invite:a", "pod-invite:b", "invite:i").map { "activity:binding:$it" }.toSet() +
            "activity:earlier-sign-in:pod-invite:c", active())
        notifications.cancelShown(owner, ActivityScreen(podList = true))
        assertEquals(setOf("game:other", "pod:other", "invite:i").map { "activity:binding:$it" }.toSet() +
            "activity:earlier-sign-in:pod-invite:c", active())
    }
}
