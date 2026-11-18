package org.asyncttwiz.android

import org.asyncttwiz.protocol.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * Hardening 2026-11-17 C: the one ordering rule for a game's server state ([RevisionOrder]): behind is ignored, in order
 * applies, ahead applies when it stands on its own and otherwise waits for the revisions in between (SYS-015: recovery
 * from missed updates is automatic). Each decision out of order is logged, and repeats don't flood the log.
 */
class RevisionOrderTest {
    private val gameId = "00000000-0000-4000-8000-000000000001"
    private val lines = mutableListOf<String>()
    private var now = 0L
    private val order = RevisionOrder(ConnectionLog({ now }) { _, line -> lines += line })
    private val summary = GameSummary(gameId, "Azure Spark", "2026-10-22T00:00:00Z", AccountIdentity("a", "A"),
        AccountIdentity("b", "B"), "p", "i", InvitationState.ACCEPTED)
    private fun revision(base: Int) = GameRevision(gameId, "$base", "${base + 1}", "r$base", "a", GameScope(gameId),
        summary.copy(revision = "${base + 1}"))

    // SYS-015: a repeat (a replay crossing a publication) is ignored; the next one applies and moves the received revision
    @Test fun aRepeatIsIgnoredAndTheNextApplies() {
        assertSame(RevisionOrder.Revision.InOrder, order.revision(revision(4), stepped = true), "nothing received yet: it applies")
        order.applied(gameId, "5")
        assertSame(RevisionOrder.Revision.Behind, order.revision(revision(4), stepped = true))
        assertSame(RevisionOrder.Revision.Behind, order.revision(revision(3), stepped = true))
        assertSame(RevisionOrder.Revision.InOrder, order.revision(revision(5), stepped = true))
        assertEquals("5", order.received(gameId), "placing doesn't apply")
        assertTrue(lines.any { "revision 5 came again (received 5): ignored" in it }, lines.toString())
    }

    // SYS-015: the open play's revision that came before the ones it rests on is held until they come, then applies after
    // them, in order; a game not open at the table takes it as it stands (a summary)
    @Test fun aRevisionAheadOfTheOpenPlayIsHeldUntilTheOnesBeforeItCome() {
        order.applied(gameId, "5")
        assertSame(RevisionOrder.Revision.Held, order.revision(revision(7), stepped = true))
        assertSame(RevisionOrder.Revision.Held, order.revision(revision(6), stepped = true))
        assertTrue(order.holding(gameId))
        assertSame(RevisionOrder.Revision.InOrder, order.revision(revision(5), stepped = true))
        assertEquals(listOf("7", "8"), order.applied(gameId, "6").map { it.resultRevision }, "the held ones follow, in order")
        assertFalse(order.holding(gameId))
        assertTrue(lines.any { "revision 8 on 7 came before the revisions after 5: held" in it }, lines.toString())
        assertTrue(lines.any { "2 held after them apply (7..8)" in it }, lines.toString())
        order.applied(gameId, "8")
        assertSame(RevisionOrder.Revision.InOrder, order.revision(revision(10), stepped = false), "a list game: it stands on its own")
    }

    // SYS-015: a head the open play loaded from covers what was held up to it; what follows it applies after it
    @Test fun aLoadFromAHeadCoversWhatWasHeldUpToIt() {
        order.applied(gameId, "5")
        listOf(7, 8, 9).forEach { order.revision(revision(it), stepped = true) }
        assertEquals(listOf("10"), order.loaded(gameId, "9").map { it.resultRevision })
        assertEquals("9", order.received(gameId))
        assertFalse(order.holding(gameId))
    }

    // SYS-015: a head or list read before the received revision is old; one ahead of the open play waits for the revisions
    // in between unless it stands for them (an engine head, an ended game); for a game not open at the table it applies
    @Test fun headsAndListReadsArePlacedAgainstTheReceivedRevision() {
        assertSame(RevisionOrder.Snapshot.Applies, order.snapshot(gameId, "3", "a head", stepped = true, whole = false))
        order.applied(gameId, "5")
        assertSame(RevisionOrder.Snapshot.Behind, order.snapshot(gameId, "4", "a list read", stepped = true, whole = true))
        assertSame(RevisionOrder.Snapshot.Applies, order.snapshot(gameId, "5", "a head", stepped = true, whole = false))
        assertSame(RevisionOrder.Snapshot.Waits, order.snapshot(gameId, "6", "a head", stepped = true, whole = false))
        assertSame(RevisionOrder.Snapshot.Applies, order.snapshot(gameId, "6", "an engine head", stepped = true, whole = true))
        assertSame(RevisionOrder.Snapshot.Applies, order.snapshot(gameId, "6", "a list read", stepped = false, whole = false))
        assertTrue(lines.any { "a head at revision 6 is ahead of the open play's 5: the table waits" in it }, lines.toString())
    }

    // SYS-015: left, the open play's held revisions apply as they stand; too many held are dropped and asked for again
    @Test fun heldRevisionsAreLetGoWhenThePlayIsLeftAndDroppedPastTheLimit() {
        order.applied(gameId, "5")
        order.revision(revision(8), stepped = true)
        assertEquals(listOf("9"), order.letGo(gameId).map { it.resultRevision })
        assertFalse(order.holding(gameId))
        repeat(RevisionOrder.MAX_HELD + 1) { order.revision(revision(7 + it), stepped = true) }
        assertFalse(order.holding(gameId), "dropped: a replay brings them")
    }

    // Owner rule 2026-11-09 (logging): a burst of replayed repeats is counted, not logged line by line
    @Test fun repeatsAreCountedNotLoggedOneByOne() {
        order.applied(gameId, "200")
        repeat(100) { order.revision(revision(100 + it), stepped = true) }
        assertEquals(ConnectionLog.BURST, lines.size, lines.toString())
        now += ConnectionLog.WINDOW_MILLIS
        order.revision(revision(150), stepped = true)
        assertTrue("97 more like it before this" in lines.last(), lines.last())
    }
}
