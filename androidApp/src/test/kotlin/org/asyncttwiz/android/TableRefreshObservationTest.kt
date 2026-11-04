package org.asyncttwiz.android

import androidx.compose.runtime.snapshots.Snapshot
import org.junit.Test
import kotlin.test.*

/** The open table observes only its narrow lobby reads, never the whole state a games-list refresh rewrites. */
class TableRefreshObservationTest {
    /** Snapshot state objects that [read] observes, i.e. what would invalidate a composable doing it. */
    private fun reads(read: () -> Unit): Set<Any> {
        val seen = mutableSetOf<Any>()
        Snapshot.observe(readObserver = { seen.add(it) }) { read() }
        return seen
    }
    /** Snapshot state objects written (with a changed value) while [write] runs. */
    private fun writes(write: () -> Unit): Set<Any> {
        val changed = mutableSetOf<Any>()
        Snapshot.sendApplyNotifications()
        val handle = Snapshot.registerApplyObserver { objects, _ -> changed.addAll(objects) }
        try { write(); Snapshot.sendApplyNotifications() } finally { handle.dispose() }
        return changed
    }

    // SYS-028 (on engine state, R4b: the table reads the lobby's narrow view, whose board is the engine game's)
    @Test fun backgroundRefreshWithUnchangedDataWritesNothingTheTableReads() {
        val f = EngineLobbyFixture()
        try {
            f.connect(); f.worker.drain()
            val table = reads { f.lobby.table.value }
            val view = f.lobby.table.value
            assertNotNull(view.board)
            val whole = reads { f.lobby.state }
            var flipped = false
            val changed = writes { repeat(3) { f.lobby.sync(); flipped = flipped || f.lobby.state.busy; f.worker.drain() } }
            assertTrue(flipped, "the refresh ran")
            assertTrue(changed.containsAll(whole), "the whole lobby state was rewritten")
            assertTrue(changed.intersect(table).isEmpty(), "but nothing the table reads")
            assertSame(view, f.lobby.table.value)
        } finally { f.close() }
    }
}
