package org.asyncttwiz.android

import androidx.compose.runtime.snapshots.Snapshot
import org.asyncttwiz.protocol.PlayAction
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
    private fun BasicTableFixture.tableReads() = reads {
        lobby.table.value; lobby.retainedTree(); lobby.displayTree(); lobby.retainedHistory(); lobby.resultLocked()
    }

    @Test fun backgroundRefreshWithUnchangedDataWritesNothingTheTableReads() {
        val f = BasicTableFixture()
        try {
            f.connect(); f.worker.drain()
            val table = f.tableReads()
            val view = f.lobby.table.value
            val tree = f.lobby.displayTree()
            val whole = reads { f.lobby.state }
            var flipped = false
            val changed = writes { repeat(3) { f.lobby.sync(); flipped = flipped || f.lobby.state.busy; f.worker.drain() } }
            assertTrue(flipped, "the refresh ran")
            assertTrue(changed.containsAll(whole), "the whole lobby state was rewritten")
            assertTrue(changed.intersect(table).isEmpty(), "but nothing the table reads")
            assertSame(view, f.lobby.table.value)
            assertSame(tree, f.lobby.displayTree())
        } finally { f.lobby.close(); f.worker.drain() }
    }

    @Test fun replacingTheDisplayTreeInvalidatesTreeReadersEvenWithoutALobbyFieldChange() {
        val f = BasicTableFixture()
        try {
            f.connect(); f.worker.drain()
            val before = f.lobby.displayTree()
            // The trees live in the session; this signal is what a tree reader observes beyond the lobby view.
            val signal = reads { f.lobby.displayTree() } - reads { f.lobby.table.value }
            assertTrue(signal.isNotEmpty())
            val changed = writes { assertTrue(f.lobby.gameplay(PlayAction.AdjustLife(f.actor, "1"))); f.worker.drain() }
            assertNotSame(before, f.lobby.displayTree())
            assertTrue(changed.containsAll(signal))
            // Nothing tree-related changing (a refresh) leaves the signal alone.
            assertTrue(writes { f.lobby.sync(); f.worker.drain() }.intersect(signal).isEmpty())
        } finally { f.lobby.close(); f.worker.drain() }
    }
}
