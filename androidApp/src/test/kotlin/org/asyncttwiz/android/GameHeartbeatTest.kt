package org.asyncttwiz.android

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.*
import org.asyncttwiz.protocol.GamePresence
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class GameHeartbeatTest {
    @Test fun everyInputCountsOnceUntilHeartbeatAndLeavingIsExplicit() {
        val tracker = GameHeartbeatTracker("installation-window")
        tracker.input(); assertNull(tracker.heartbeat())
        assertFalse(tracker.display("game").single().didSomething)
        // Touches, pan/zoom, inspect, menus, chat and scrolling all use the same input bit.
        repeat(20) { tracker.input() }
        assertTrue(tracker.heartbeat()!!.didSomething)
        assertFalse(tracker.heartbeat()!!.didSomething)
        assertTrue(tracker.display("game").isEmpty())
        tracker.input()
        val switched = tracker.display("other")
        assertEquals(listOf("game", "other"), switched.map { it.gameId })
        assertTrue(switched[0].left); assertTrue(switched[0].didSomething)
        assertFalse(switched[1].left); assertFalse(switched[1].didSomething)
        assertTrue(tracker.display(null).single().left)
        assertTrue(tracker.display(null).isEmpty()); assertNull(tracker.heartbeat())
    }

    @Test fun leavingIsAlsoSentOverHttpBecauseAQueuedSocketSendCanBeLost() {
        val socket = mutableListOf<GamePresence>(); val http = mutableListOf<GamePresence>(); val done = CountDownLatch(1)
        val controller = GameHeartbeatController(http = { token, presence ->
            assertEquals("token", token); synchronized(http) { http += presence }; done.countDown()
        }) { _, presence -> socket += presence; true }
        controller.display("game", "token")
        controller.display(null, null) // Activity.onPause
        assertTrue(done.await(5, TimeUnit.SECONDS))
        assertEquals(listOf(false, true), socket.map { it.left })
        assertEquals(listOf(true), synchronized(http) { http.map { it.left } }, "Only leaving is duplicated; the socket took the rest")
        controller.close()
    }
}
