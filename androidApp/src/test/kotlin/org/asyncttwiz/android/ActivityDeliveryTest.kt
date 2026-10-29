package org.asyncttwiz.android

import kotlin.test.*
import org.junit.Test
import org.asyncttwiz.protocol.*

class ActivityDeliveryTest {
    private class Store : ActivityDeliveryStore {
        var cursor: String? = null
        val marks = mutableMapOf<String, ActivityWatermark>()
        var writable = true
        override fun cursor() = cursor
        override fun watermark(key: String) = marks[key]
        override fun save(cursor: String, marks: Map<String, ActivityWatermark>): Boolean {
            if (!writable) return false
            this.cursor = cursor; this.marks.putAll(marks); return true
        }
    }
    private val alice = ActivityOwner("alice", "session-1")
    private val bob = ActivityOwner("bob", "session-2")
    private fun hint(id: Long, group: String = "group-1", kind: ActivityKind = ActivityKind.GAME, game: String = "game") =
        ActivityHint(id.toString(), kind, game, group, "opponent")
    private fun page(vararg hints: ActivityHint, reset: Boolean = false, cursor: String = hints.lastOrNull()?.activityId ?: "0") =
        ActivityPage(hints.toList(), cursor, "0", reset, false)

    @Test fun serverReadyNoticesSurviveAnAdvancedSyncCursorAndAlertAgainOnlyOnNewYourMove() {
        val store = Store(); val posted = mutableListOf<ActivityNotice>()
        val delivery = ActivityDelivery(store, posted::add).also { it.bind(alice) }
        val content = GameNoticeContent("Game", "Opponent", "2026-10-27T12:00:00Z", false)
        val first = hint(1).copy(notice = content)
        delivery.apply(alice, page(first).copy(readyNotices = emptyList())) { true }
        assertEquals("1", store.cursor); assertTrue(posted.isEmpty())
        delivery.apply(alice, page(cursor = "1").copy(readyNotices = listOf(first))) { true }
        assertEquals(1, posted.size); assertFalse(posted.last().silent)
        delivery.apply(alice, page(cursor = "1").copy(readyNotices = listOf(first))) { true }
        assertEquals(1, posted.size)
        fun next(id: Long, move: Boolean) = delivery.apply(alice, page(cursor = id.toString()).copy(
            readyNotices = listOf(hint(id, "new-group-$id").copy(notice = content.copy(yourMove = move))))) { true }
        next(2, false); assertTrue(posted.last().silent)
        next(3, true); assertFalse(posted.last().silent)
        next(4, true); assertTrue(posted.last().silent)
        delivery.display("game", true)
        next(5, true); assertEquals(4, posted.size)
        delivery.display("game", false)
        next(6, true); assertEquals(5, posted.size); assertFalse(posted.last().silent)
    }

    @Test fun firstNoticeAfterOpeningTheGameAndWaitingRemindersAlertAgain() {
        val store = Store(); val posted = mutableListOf<ActivityNotice>()
        val delivery = ActivityDelivery(store, posted::add).also { it.bind(alice) }
        val content = GameNoticeContent("Game", "Opponent", "2026-10-27T12:00:00Z", false)
        fun next(id: Long, notice: GameNoticeContent = content) = delivery.apply(alice, page(cursor = id.toString()).copy(
            readyNotices = listOf(hint(id, "group-$id").copy(notice = notice)))) { true }
        next(1); next(2)
        assertEquals(listOf(false, true), posted.map { it.silent })
        // Opened mid-way through the opponent's moves, then closed: their next move alerts again.
        delivery.display("game", true); delivery.display(null, true)
        next(3); assertFalse(posted.last().silent)
        next(4); assertTrue(posted.last().silent, "Later updates of that shown notice stay quiet")
        // The waiting reminder replaces the shown notice under the same key and alerts.
        next(5, content.copy(reminder = true))
        assertFalse(posted.last().silent); assertEquals("game:game", posted.last().key)
        assertEquals(1, posted.map { it.key }.distinct().size)
        // A reminder and a newer move in one poll: the newer text is shown, still alerting.
        delivery.apply(alice, page(cursor = "7").copy(readyNotices = listOf(hint(7, "group-7").copy(notice = content),
            hint(6, "group-6").copy(notice = content.copy(reminder = true))))) { true }
        assertEquals("7", posted.last().hint.activityId); assertFalse(posted.last().silent)
    }

    @Test fun pollPushOverlapRestartAndGroupExtensionUseOneReplacement() {
        val store = Store(); val posted = mutableListOf<ActivityNotice>()
        fun delivery() = ActivityDelivery(store) { notice ->
            assertEquals(notice.hint.identity, store.watermark(notice.key)?.identity)
            assertEquals(notice.hint.activityId.toLong(), store.watermark(notice.key)?.activity)
            posted += notice
        }.also { it.bind(alice) }
        val first = delivery()
        assertTrue(first.apply(alice, page(hint(1), hint(2), hint(3))) { true })
        assertEquals(1, posted.size); assertFalse(posted.last().silent)
        assertTrue(first.apply(alice, page(hint(3))) { true }); assertEquals(1, posted.size)
        val restarted = delivery()
        assertTrue(restarted.apply(alice, page(hint(4))) { true }); assertTrue(posted.last().silent)
        assertTrue(restarted.apply(alice, page(hint(5, "group-2"))) { true }); assertFalse(posted.last().silent)
        assertEquals(1, posted.map { it.key }.distinct().size)
    }
    @Test fun laterPodPickAndCompletionAlertAgainWithSameAttemptAndReplacementKey() {
        val store = Store(); val posted = mutableListOf<ActivityNotice>()
        val delivery = ActivityDelivery(store, posted::add).also { it.bind(alice) }
        listOf(ActivityKind.POD_YOUR_PICK, ActivityKind.POD_YOUR_PICK, ActivityKind.POD_DRAFT_COMPLETE).forEachIndexed { index, kind ->
            val page = page(hint(index + 1L, "same-attempt", kind, "pod"))
            delivery.apply(alice, page) { true }
            delivery.apply(alice, page) { true } // Poll/push overlap must not alert twice.
        }
        assertEquals(listOf(false, false, false), posted.map { it.silent })
        assertEquals(listOf("pod:pod", "pod:pod", "pod:pod"), posted.map { it.key })
    }
    @Test fun reviewIsActiveDisplayButBackgroundIsNotAndInvitesRemainIndependent() {
        val store = Store(); val posted = mutableListOf<ActivityNotice>()
        val delivery = ActivityDelivery(store, posted::add).also { it.bind(alice); it.display("game", true) }
        delivery.apply(alice, page(hint(1), hint(2, "attempt", ActivityKind.INVITATION), hint(3, game = "other"))) { true }
        assertEquals(setOf(ActivityKind.INVITATION, ActivityKind.GAME), posted.map { it.hint.kind }.toSet())
        assertTrue(posted.none { it.hint.kind == ActivityKind.GAME && it.hint.gameId == "game" })
        assertEquals(1L, store.marks["game:game"]?.activity)
        delivery.display("game", false)
        delivery.apply(alice, page(hint(4, "next-group"))) { true }
        assertEquals("game", posted.last().hint.gameId)
    }
    @Test fun openPodAndPodListSuppressTheirNotificationsOnlyWhileResumed() {
        val store = Store(); val posted = mutableListOf<ActivityNotice>()
        val delivery = ActivityDelivery(store, posted::add).also { it.bind(alice) }
        delivery.display(ActivityScreen(pod = "pod"), true)
        delivery.apply(alice, page(hint(1, "a", ActivityKind.POD_YOUR_PICK, "pod"),
            hint(2, "b", ActivityKind.POD_YOUR_PICK, "other"), hint(3, "c", ActivityKind.POD_INVITATION, "invited"))) { true }
        assertEquals(listOf("pod:other", "pod-invite:invited"), posted.map { it.key })
        assertEquals(1L, store.marks["pod:pod"]?.activity)
        delivery.display(ActivityScreen(podList = true), true)
        delivery.apply(alice, page(hint(4, "d", ActivityKind.POD_INVITATION, "another"),
            hint(5, "e", ActivityKind.POD_DRAFT_COMPLETE, "pod"))) { true }
        assertEquals(listOf("pod:other", "pod-invite:invited", "pod:pod"), posted.map { it.key })
        assertEquals(4L, store.marks["pod-invite:another"]?.activity)
        delivery.display(ActivityScreen(pod = "pod"), false)
        delivery.apply(alice, page(hint(6, "f", ActivityKind.POD_YOUR_PICK, "pod"))) { true }
        assertEquals("pod:pod", posted.last().key); assertEquals(4, posted.size)
    }
    @Test fun displayedScreenFollowsDestinationAndSelection() {
        assertEquals(ActivityScreen(game = "g"), ActivityScreen.of(ShellDestination.GAMES, "g", PodState(selected = "p")))
        assertEquals(ActivityScreen(pod = "p"), ActivityScreen.of(ShellDestination.PODS, "g", PodState(selected = "p")))
        assertEquals(ActivityScreen(podList = true), ActivityScreen.of(ShellDestination.PODS, null, PodState()))
        assertEquals(ActivityScreen.NONE, ActivityScreen.of(ShellDestination.MENU, "g", PodState(selected = "p")))
        assertTrue(ActivityScreen(game = "g").shows("game:g")); assertFalse(ActivityScreen(game = "g").shows("pod:g"))
        assertTrue(ActivityScreen(pod = "p").shows("pod:p")); assertFalse(ActivityScreen(pod = "p").shows("pod-invite:p"))
        assertTrue(ActivityScreen(podList = true).shows("pod-invite:any")); assertFalse(ActivityScreen(podList = true).shows("pod:any"))
        assertFalse(ActivityScreen.NONE.shows("invite:any"))
    }
    @Test fun permissionChannelAndResetSuppressionAdvanceDeliveryOnly() {
        val store = Store(); val posted = mutableListOf<ActivityNotice>()
        val delivery = ActivityDelivery(store, posted::add).also { it.bind(alice) }
        delivery.apply(alice, page(hint(1))) { false }
        delivery.apply(alice, page(hint(2, "old"), reset = true, cursor = "r:200:2")) { true }
        assertEquals("r:200:2", store.cursor); assertTrue(posted.isEmpty())
        delivery.apply(alice, page(hint(3, "old"), reset = true, cursor = "200")) { true }
        delivery.apply(alice, page(hint(201, "fresh"))) { true }
        assertEquals(1, posted.size); assertFalse(posted.single().silent)
    }
    @Test fun logoutSwitchRevocationAndStorageFailureFenceLateCallbacksBeforeWritingOrPosting() {
        val store = Store(); val posted = mutableListOf<ActivityNotice>()
        val delivery = ActivityDelivery(store, posted::add).also { it.bind(alice) }
        delivery.bind(null)
        assertFalse(delivery.apply(alice, page(hint(1))) { true }); assertNull(store.cursor)
        delivery.bind(bob)
        assertFalse(delivery.apply(alice, page(hint(2))) { true }); assertTrue(store.marks.isEmpty())
        delivery.bind(alice.copy(binding = "new-session"))
        assertFalse(delivery.apply(alice, page(hint(3))) { true }); assertTrue(posted.isEmpty())
        delivery.bind(alice); store.writable = false
        assertFalse(delivery.apply(alice, page(hint(4))) { true }); assertTrue(posted.isEmpty())
    }
    @Test fun cursorFetchIsIndependentOfNotificationPermissionAndRetriesAreBounded() {
        assertEquals(15L, ActivityPolling.PERIOD_MINUTES)
        assertTrue(ActivityPolling.NETWORK_REQUIRED)
        assertEquals(30L, ActivityPolling.retrySeconds(0)); assertEquals(60L, ActivityPolling.retrySeconds(1))
        assertEquals(120L, ActivityPolling.retrySeconds(1, 120))
        assertEquals(15360L, ActivityPolling.retrySeconds(9)); assertEquals(21600L, ActivityPolling.retrySeconds(10))
        assertNull(ActivityPolling.retrySeconds(11))
        assertNull(ActivityPolling.retrySeconds(0, 21601))
        val store = Store(); val delivery = ActivityDelivery(store) { fail("Muted notification must not post") }
        delivery.bind(alice); delivery.apply(alice, page(hint(1))) { false }
        assertEquals("1", delivery.cursor(alice)); assertNull(delivery.cursor(bob))
    }
}
