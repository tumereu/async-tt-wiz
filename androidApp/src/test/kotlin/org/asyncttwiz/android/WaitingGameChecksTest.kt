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

    // LOB-076
    @Test fun checksBackOffFromFifteenMinutesToSixteenHoursThenRunDaily() {
        assertEquals(listOf(15L, 30L, 60L, 120L, 240L, 480L, 960L, 2400L, 3840L, 5280L), (0..9).map(WaitingChecks::offsetMinutes))
        assertEquals(WaitingChecks.DAILY_MINUTES, WaitingChecks.offsetMinutes(8) - WaitingChecks.offsetMinutes(7))
    }

    // LOB-076
    @Test fun onlyWaitingGamesWithoutAShowingOrDismissedNoticeArePosted() {
        val none: (String) -> WaitingDismissal? = { null }
        assertEquals(listOf("g"), WaitingChecks.select(listOf(hint()), emptySet(), none).map { it.gameId })
        assertTrue(WaitingChecks.select(listOf(hint(yourMove = false)), emptySet(), none).isEmpty())
        assertTrue(WaitingChecks.select(listOf(hint().copy(kind = ActivityKind.INVITATION)), emptySet(), none).isEmpty())
        assertTrue(WaitingChecks.select(listOf(hint().copy(notice = null)), emptySet(), none).isEmpty())
        // Already showing (push, feed or an earlier check: one tag per game) is never posted again.
        assertEquals(listOf("other"), WaitingChecks.select(listOf(hint(), hint("other")), setOf("g"), none).map { it.gameId })
    }

    // LOB-076
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

    // LOB-076
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

    private fun podHint(pod: String = "p", activity: Int = 4, kind: ActivityKind = ActivityKind.POD_YOUR_PICK, attempt: String = "attempt") =
        ActivityHint(activity.toString(), kind, pod, attempt, resourceName = "Friday cube")

    // LOB-076, DRF-136
    @Test fun podsWaitingForYourPickOrBuildAreRemindedLikeGamesUnlessShowingOrSwipedForTheSameWait() {
        val none: (String) -> WaitingDismissal? = { null }
        val waiting = listOf(hint(), podHint(), podHint("q", 5, ActivityKind.POD_DRAFT_COMPLETE))
        assertEquals(listOf("p", "q"), WaitingChecks.selectPods(waiting, emptySet(), none).map { it.gameId })
        assertEquals(listOf("g"), WaitingChecks.select(waiting, emptySet(), none).map { it.gameId }, "Game checks ignore pods")
        assertTrue(WaitingChecks.selectPods(listOf(podHint(kind = ActivityKind.POD_DECKS_READY),
            podHint(kind = ActivityKind.POD_INVITATION)), emptySet(), none).isEmpty())
        assertEquals(listOf("q"), WaitingChecks.selectPods(waiting, setOf("p"), none).map { it.gameId })
        val marks = mapOf("p" to WaitingDismissal("attempt", 4))
        assertEquals(listOf("q"), WaitingChecks.selectPods(waiting, emptySet(), marks::get).map { it.gameId })
        // A new pick arriving at an empty queue, or the draft ending with a build to do, is something new.
        assertEquals(listOf("p"), WaitingChecks.selectPods(listOf(podHint(activity = 7)), emptySet(), marks::get).map { it.gameId })
        assertEquals(listOf("p"), WaitingChecks.selectPods(listOf(podHint(activity = 8, kind = ActivityKind.POD_DRAFT_COMPLETE)),
            emptySet(), marks::get).map { it.gameId })
        assertEquals(listOf("p"), WaitingChecks.selectPods(listOf(podHint(attempt = "redraft")), emptySet(), marks::get).map { it.gameId })
    }

    // LOB-076, DRF-136
    @Test fun aPodPickNoticeRecordsItsSwipeAgainstThePickThatStartedTheWait() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        shadowOf(context as android.app.Application).grantPermissions(android.Manifest.permission.POST_NOTIFICATIONS)
        val owner = ActivityOwner("account", "binding")
        val notifications = ActivityNotifications(context).also { it.channels() }
        val waiting = podHint()
        notifications.post(owner, ActivityNotice(waiting.notificationKey(), waiting, silent = false))
        val shown = shadowOf(context.getSystemService(NotificationManager::class.java)).allNotifications.single()
        assertEquals("Your pick — Friday cube", shown.extras.getString("android.title"))
        assertEquals(setOf("p"), notifications.showingPods(owner))
        assertTrue(notifications.showingGames(owner).isEmpty())
        assertTrue(WaitingChecks.selectPods(listOf(waiting), notifications.showingPods(owner)) { null }.isEmpty())
        val delete = shadowOf(shown.deleteIntent).savedIntent
        assertEquals(ActivityDismissReceiver::class.java.name, delete.component?.className)
        assertTrue(delete.getBooleanExtra("pod", false))
        assertEquals("p", delete.getStringExtra("game"))
        assertEquals("attempt", delete.getStringExtra("identity"))
        assertEquals(4L, delete.getLongExtra("when", 0))
    }
}
