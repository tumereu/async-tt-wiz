package org.asyncttwiz.android

import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.asyncttwiz.protocol.ActivityHint
import org.asyncttwiz.protocol.ActivityKind
import org.asyncttwiz.protocol.GameNoticeContent
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.Instant
import kotlin.test.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class WaitingGameChecksTest {
    private fun hint(game: String = "g", identity: String = "group", at: String = "2026-10-30T10:00:00Z", activity: Int = 1,
        yourMove: Boolean = true) = ActivityHint(activity.toString(), ActivityKind.GAME, game, identity, "opponent",
        GameNoticeContent("Friday", "Alex", at, yourMove, reminder = true))
    private val time = Instant.parse("2026-10-30T10:00:00Z").toEpochMilli()

    @Test fun checksBackOffFromFifteenMinutesToSixteenHoursThenRunDaily() {
        assertEquals(listOf(15L, 30L, 60L, 120L, 240L, 480L, 960L, 2400L, 3840L, 5280L), (0..9).map(WaitingChecks::offsetMinutes))
        assertEquals(WaitingChecks.DAILY_MINUTES, WaitingChecks.offsetMinutes(8) - WaitingChecks.offsetMinutes(7))
    }

    @Test fun onlyWaitingGamesWithoutAShowingOrDismissedNoticeArePosted() {
        val none: (String) -> WaitingDismissal? = { null }
        assertEquals(listOf("g"), WaitingChecks.select(listOf(hint()), emptySet(), none).map { it.gameId })
        assertTrue(WaitingChecks.select(listOf(hint(yourMove = false)), emptySet(), none).isEmpty())
        assertTrue(WaitingChecks.select(listOf(hint().copy(kind = ActivityKind.INVITATION)), emptySet(), none).isEmpty())
        assertTrue(WaitingChecks.select(listOf(hint().copy(notice = null)), emptySet(), none).isEmpty())
        // Already showing (push, feed or an earlier check: one tag per game) is never posted again.
        assertEquals(listOf("other"), WaitingChecks.select(listOf(hint(), hint("other")), setOf("g"), none).map { it.gameId })
    }

    @Test fun aSwipedAwayNoticeStaysAwayUntilTheGameWaitsOnANewEvent() {
        val marks = mapOf("g" to WaitingDismissal("group", time))
        fun posted(vararg waiting: ActivityHint) = WaitingChecks.select(waiting.toList(), emptySet(), marks::get).map { it.identity }
        assertTrue(posted(hint()).isEmpty())
        assertTrue(posted(hint(at = "2026-10-30T09:59:00Z", activity = 9)).isEmpty(), "A reminder of the same wait")
        assertEquals(listOf("next"), posted(hint(identity = "next", activity = 2)), "A new latest event")
        assertEquals(listOf("group"), posted(hint(at = "2026-10-30T10:05:00Z", activity = 3)), "A later event extending the group")
        assertEquals(listOf("group"), WaitingChecks.select(listOf(hint("h")), emptySet(), marks::get).map { it.identity })
        // A notice without event time (older servers) is dismissed for its identity.
        val legacy = mapOf("g" to WaitingDismissal("group", Long.MAX_VALUE))
        assertTrue(WaitingChecks.select(listOf(hint(at = "2026-10-31T10:00:00Z")), emptySet(), legacy::get).isEmpty())
        assertEquals(WaitingDismissal("a|b", 5), WaitingDismissal.decode(WaitingDismissal("a|b", 5).encode()))
        assertNull(WaitingDismissal.decode("garbage")); assertNull(WaitingDismissal.decode(null))
    }

    @Test fun theFallbackPostsTheSharedGameNotificationWhoseSwipeIsRecordedAndWhichIsNotRepostedWhileShowing() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        shadowOf(context as android.app.Application).grantPermissions(android.Manifest.permission.POST_NOTIFICATIONS)
        val owner = ActivityOwner("account", "binding")
        val notifications = ActivityNotifications(context).also { it.channels() }
        val waiting = hint()
        notifications.post(owner, ActivityNotice("game:g", waiting, silent = false))
        val shown = shadowOf(context.getSystemService(NotificationManager::class.java)).allNotifications.single()
        assertEquals("Alex is waiting for you", shown.extras.getString("android.text"))
        assertEquals(setOf("g"), notifications.showingGames(owner))
        assertTrue(notifications.showingGames(ActivityOwner("account", "earlier-sign-in")).isEmpty())
        assertTrue(WaitingChecks.select(listOf(waiting), notifications.showingGames(owner)) { null }.isEmpty())
        val delete = shadowOf(shown.deleteIntent)
        assertTrue(delete.isBroadcastIntent)
        assertEquals(ActivityDismissReceiver::class.java.name, delete.savedIntent.component?.className)
        assertEquals("binding", delete.savedIntent.getStringExtra("binding"))
        assertEquals("g", delete.savedIntent.getStringExtra("game"))
        assertEquals("group", delete.savedIntent.getStringExtra("identity"))
        assertEquals(time, delete.savedIntent.getLongExtra("when", 0))
    }
}
