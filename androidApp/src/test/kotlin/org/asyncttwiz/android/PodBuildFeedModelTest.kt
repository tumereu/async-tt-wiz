package org.asyncttwiz.android

import org.asyncttwiz.protocol.*
import org.junit.Test
import kotlin.test.*

class PodBuildFeedModelTest {
    private val labels = listOf("Lands", "0", "1", "2+")
    private fun tile(digest: String, vararg ids: String) = PodBuildTile(digest, ids.toList(), null, emptySet())
    private val buckets = listOf(listOf(tile("land", "l1")), emptyList(), listOf(tile("a", "a1", "a2"), tile("b", "b1")),
        listOf(tile("c", "c1")))

    @Test fun columnFeedExpandsStacksIntoOneCardPerCopyInDisplayOrder() {
        val feed = assertNotNull(podBuildFeed(PodDeckCategory.MAIN, buckets, labels, 2))
        assertEquals(listOf("a1", "a2", "b1"), feed.cards.map { it.copyId })
        assertEquals(listOf("a", "a", "b"), feed.cards.map { it.digest })
        assertEquals("Main · MV 1", feed.title)
        assertEquals("1 / 3", feed.progress)
        assertNull(podBuildFeed(PodDeckCategory.MAIN, buckets, labels, 1))
    }

    // DRF-109
    @Test fun sectionFeedWalksColumnsInOrderAndNamesTheCurrentCardsColumn() {
        val feed = assertNotNull(podBuildFeed(PodDeckCategory.SIDE, buckets, labels, null))
        assertEquals(listOf("l1", "a1", "a2", "b1", "c1"), feed.cards.map { it.copyId })
        assertEquals("Side · Lands", feed.title)
        assertEquals("Side · MV 1", feed.decide(null).title)
        val done = (1..5).fold(feed) { state, _ -> state.decide(null) }
        assertTrue(done.finished)
        assertEquals("Side", done.title)
        assertEquals("5 / 5", done.progress)
    }

    // DRF-107, DRF-108: swipes move a copy as in the columns (DRF-099); Maybe has a review too
    @Test fun swipesFollowTheColumnsAndMaybeHasAFeed() {
        val main = podBuildFeed(PodDeckCategory.MAIN, buckets, labels, null)!!
        // SYS-083: right one section up, left one down, wrapping
        assertEquals(PodDeckCategory.MAYBE to PodDeckCategory.SIDE, main.right to main.left)
        val side = podBuildFeed(PodDeckCategory.SIDE, buckets, labels, null)!!
        assertEquals(PodDeckCategory.MAIN to PodDeckCategory.MAYBE, side.right to side.left)
        val maybe = assertNotNull(podBuildFeed(PodDeckCategory.MAYBE, buckets, labels, null))
        assertEquals(PodDeckCategory.SIDE to PodDeckCategory.MAIN, maybe.right to maybe.left)
        assertEquals("Maybe · Lands", maybe.title)
    }

    // DRF-110
    @Test fun decisionsCountIntoTheSummaryAndStopAtTheEnd() {
        val feed = podBuildFeed(PodDeckCategory.MAIN, buckets, labels, 2)!!
        val done = feed.decide(null).decide(PodDeckCategory.SIDE).decide(PodDeckCategory.MAYBE)
        assertTrue(done.finished)
        assertNull(done.current)
        // The moves in the order of the review's buttons, right before left (DRF-099)
        assertEquals("Kept 1 · 1 to Maybe · 1 to Side", done.summary)
        assertSame(done, done.decide(PodDeckCategory.SIDE))
        assertEquals("2 to Maybe", podBuildFeed(PodDeckCategory.SIDE, buckets, labels, 2)!!
            .decide(PodDeckCategory.MAYBE).decide(PodDeckCategory.MAYBE).summary)
        assertEquals("Kept 1 · 1 to Main", podBuildFeed(PodDeckCategory.MAYBE, buckets, labels, 2)!!
            .decide(PodDeckCategory.MAIN).decide(null).summary)
        // Its own section is a keep
        assertEquals("Kept 1", podBuildFeed(PodDeckCategory.MAIN, buckets, labels, 3)!!.decide(PodDeckCategory.MAIN).summary)
    }

    @Test fun undoStepsBackOneCardAndOnlyReturnsMovedCopies() {
        val feed = podBuildFeed(PodDeckCategory.MAIN, buckets, labels, 2)!!
        assertFalse(feed.canUndo)
        assertEquals(feed to null, feed.undo())
        val (afterMove, restore) = feed.decide(null).decide(PodDeckCategory.SIDE).undo()
        assertEquals("a2", restore)
        assertEquals("a2", afterMove.current?.copyId)
        val (afterKeep, none) = afterMove.undo()
        assertNull(none)
        assertEquals("a1", afterKeep.current?.copyId)
        // Undo still works once finished (while the last card's flick plays).
        val (back, moved) = feed.decide(null).decide(null).decide(PodDeckCategory.MAYBE).undo()
        assertEquals("b1", moved)
        assertFalse(back.finished)
    }

    @Test fun columnLabelsReadAsManaValues() {
        assertEquals("Lands", podBuildFeedColumnLabel("Lands"))
        assertEquals("MV 5+", podBuildFeedColumnLabel("5+"))
    }

    @Test fun cardsSideBySideShareTheColumnWidthRowByRow() {
        assertEquals(100, podBuildCardWidth(100, 1, 1))
        assertEquals(49, podBuildCardWidth(100, 2, 1))
        assertEquals(32, podBuildCardWidth(98, 3, 1))
        assertEquals(1, podBuildCardWidth(1, 3, 1))
        assertEquals(listOf(0 to 0, 0 to 1, 0 to 2, 1 to 0, 1 to 1), (0..4).map { podBuildGridSlot(it, 3) })
        assertEquals(listOf(0 to 0, 1 to 0, 2 to 0), (0..2).map { podBuildGridSlot(it, 1) })
    }
}
