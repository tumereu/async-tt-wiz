package org.asyncttwiz.android

import org.asyncttwiz.protocol.*
import org.junit.Test
import kotlin.test.*

class CardRingTest {
    @Test fun pagesAreDataAndEveryAvailableActionIsOnExactlyOnePage() {
        val ids = CardRingPages.defaults.take(2).flatMap { it.actions } +
            listOf("flip", "otherSide", "face/0", "face/1", "Link", "Unlink", "resetCorner", "corner/LEFT/1", "Add Stack effect", "Move to Stack", "Remove token")
        val pages = CardRingPages.resolve(ids)
        assertEquals(listOf("Everyday", "Counters", "More"), pages.map { it.name })
        assertEquals(ids.toSet(), pages.flatMap { it.actions }.toSet())
        assertEquals(ids.size, pages.flatMap { it.actions }.size)
        assertEquals(CardRingPages.defaults[0], pages[0])
        assertEquals(6, pages[1].actions.size)
        assertTrue(pages[2].actions.indexOf("face/0") < pages[2].actions.indexOf("Link"))
        val custom = CardRingPages.defaults.toMutableList().apply { this[0] = this[0].copy(actions = listOf("Clone", "inspect")) }
        assertEquals(listOf("Clone", "inspect"), CardRingPages.resolve(ids, custom)[0].actions)
    }

    @Test fun inputGuardUsesPressStartNotReleaseAndIncludesTheWholeDoubleTapWindow() {
        val guard = CardRingInputGuard(1000)
        assertFalse(guard.accepts(999))
        assertFalse(guard.accepts(1000))
        assertFalse(guard.accepts(1250))
        val startedDuringGuard = guard.accepts(1279)
        assertFalse(startedDuringGuard) // still false when this press eventually releases
        assertTrue(guard.accepts(1280))
    }

    @Test fun allPagesFitWithoutCoveringTheCardAtEdgesCornersAndFontScales() {
        for ((width, height) in listOf(360f to 640f, 412f to 892f, 892f to 412f, 640f to 360f)) {
            for (font in listOf(1f, 1.3f, 2f)) for (x in listOf(0f, width / 2 - 24, width - 48))
                for (y in listOf(0f, height / 2 - 34, height - 68)) for (own in listOf(true, false)) {
                    val card = TableBounds(x, y, 48f, 68f)
                    val side = ringBubbleSide(font)
                    val pages = listOf(
                        List(8) { RingSize(if (it == 5) 144 + side - 56 else side, side) },
                        List(6) { RingSize(144 + side - 56, side) },
                        List(40) { RingSize(side, side) })
                    for (sizes in pages) {
                        val result = CardRingGeometry.place(width, height, card, sizes + RingSize(88f, 48f), own)
                        val regions = result.grid?.let(::listOf) ?: result.bubbles
                        for (b in regions) {
                            assertTrue(b.x >= 0 && b.y >= 0 && b.right <= width && b.bottom <= height, "$width×$height $card $b")
                            assertNull(b.intersection(card), "$card overlaps $b")
                        }
                        for ((i, b) in result.bubbles.withIndex()) {
                            assertTrue(b.width >= 48 && b.height >= 48)
                            for (other in result.bubbles.drop(i + 1)) assertNull(b.intersection(other))
                        }
                        result.grid?.let { grid ->
                            assertTrue(grid.width >= sizes.maxOf { it.width })
                            assertTrue(grid.height >= 104f)
                            val rows = ringGridRows(sizes, minOf(grid.width, 408f))
                            assertEquals(sizes, rows.flatten())
                            assertTrue(rows.all { row -> row.sumOf { it.width.toDouble() } + 8 * (row.size - 1) <= grid.width + .001 })
                        }
                    }
                }
        }
    }

    @Test fun openSpaceUsesAFullRingAndRightEdgeOpensInward() {
        val sizes = List(6) { RingSize(56f, 56f) } + RingSize(88f, 48f)
        val middle = TableBounds(422f, 180f, 48f, 68f)
        val full = CardRingGeometry.place(892f, 412f, middle, sizes, true)
        assertNull(full.grid)
        assertTrue(full.bubbles.any { it.y < middle.y })
        assertTrue(full.bubbles.any { it.y > middle.bottom })
        val edge = TableBounds(364f, 400f, 48f, 68f)
        val fan = CardRingGeometry.place(412f, 892f, edge, sizes, true)
        assertNull(fan.grid)
        assertTrue(fan.bubbles.all { it.center.x < edge.center.x })
    }

    @Test fun stackSingleTapStillOpensShortcutsAndDoubleTapOpensActionsWithoutRotation() {
        val f = TableStatesFixture
        val play = f.combat()
        val base = TablePresentation.scene(f.game, play, f.a, 412f, 892f, false)
        val item = base.items.single { it.cardId == f.id("effect") }
        val point = item.visible.first().center
        val events = mutableListOf<TableIntent>()
        val gestures = TableGestureCoordinator(events::add)
        gestures.update(base, TableSelection(), true)
        gestures.down(point, 0); gestures.up(point, 20)
        assertEquals(listOf<TableIntent>(TableIntent.StackBubbles(item.cardId)), events)
        val bubbles = TablePresentation.stackBubbles(base, play, item.cardId, 412f, 892f)
        gestures.update(base.copy(stackBubbles = bubbles), TableSelection(), true)
        gestures.down(point, 80); gestures.up(point, 100)
        assertEquals(listOf(TableScope(item.cardId!!, listOf(item.cardId))), events.filterIsInstance<TableIntent.Actions>().map { it.scope })
        assertEquals(1, events.count { it == TableIntent.DismissTapUi })
        assertTrue(events.none { it is TableIntent.Rotate })
    }
}
