package org.asyncttwiz.android

import org.asyncttwiz.protocol.DraftCopy
import org.asyncttwiz.protocol.DraftPrivatePack
import org.asyncttwiz.protocol.DraftSettings
import org.junit.Test
import kotlin.test.*

class PodPickSwipeTest {
    private fun drag(offset: Float) = PodPickSwipeState().drag(offset)

    @Test fun distanceThresholdMatchesFullTintInBothDirections() {
        for ((direction, mark) in listOf(1f to PodPickMark.KEEP, -1f to PodPickMark.BURN)) {
            assertEquals(.5f, drag(15f * direction).progress(100))
            assertNull(drag(29f * direction).finish(100, true).decision)
            assertEquals(1f, drag(30f * direction).progress(100))
            assertEquals(mark, drag(30f * direction).finish(100, true).decision)
            assertEquals(1f, drag(100f * direction).progress(100))
        }
        assertNull(drag(0f).finish(100, true).decision)
        assertNull(drag(100f).finish(0, true).decision)
    }

    @Test fun cancellationAndShortReleaseFadeAtTheirFinalOffsetWithoutCommitting() {
        for (finished in listOf(drag(80f).finish(100, true, cancelled = true), drag(-29f).finish(100, true))) {
            assertEquals(PodPickSwipePhase.FADING, finished.phase)
            assertNull(finished.decision)
            assertEquals(if (finished.offset > 0f) 80f else -29f, finished.offset)
            assertEquals(finished, finished.finish(100, true))
        }
        assertEquals(PodPickSwipeState(), PodPickSwipeState().finish(100, true, cancelled = true))
    }

    @Test fun finishingGestureCleanupDoesNotCancelACommittedReleaseAndNextDragStartsFresh() {
        val released = drag(40f).finish(100, true)
        assertEquals(released, released.finish(100, true, cancelled = true))
        val next = released.drag(-20f)
        assertEquals(PodPickSwipePhase.DRAGGING, next.phase)
        assertNull(next.decision)
        assertEquals(-20f, next.offset)
        assertTrue(next.generation > released.generation)
        assertEquals(next.generation, next.drag(-40f).generation)
    }

    @Test fun releaseRespectsDisabledBurnsAndTogglesExistingMarks() {
        assertNull(drag(-80f).finish(100, false).decision)
        val pack = DraftPrivatePack("pack", 1, (1..4).map { DraftCopy("$it", "definition") })
        val rules = PodPickRules(pack, DraftSettings(keep = 1, burn = 1))
        for ((offset, kind) in listOf(30f to PodPickMark.KEEP, -30f to PodPickMark.BURN)) {
            val empty = PodPickMarks().forPack(pack)
            val marked = empty.toggle("1", kind, rules)
            val decision = assertNotNull(drag(offset).finish(100, true).decision)
            assertEquals(empty, marked.toggle("1", decision, rules))
        }
    }
}
