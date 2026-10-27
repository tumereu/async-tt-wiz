package org.asyncttwiz.android

import kotlin.test.*
import org.junit.Test

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
}
