package org.asyncttwiz.android

import org.asyncttwiz.protocol.*
import org.junit.Test
import kotlin.test.*

class CardRingTest {
    @Test fun pagesAreDataAndEveryAvailableActionIsOnExactlyOnePage() {
        val ids = CardRingPages.defaults.take(2).flatMap { it.actions } +
            listOf("flip", "otherSide", "face/0", "face/1", "Link", "Unlink", "resetCorner", "corner/LEFT/1", "Add Stack effect", "Move to Stack", "Remove token", "Library top", "Graveyard")
        val pages = CardRingPages.resolve(ids)
        assertEquals(listOf("Everyday", "Counters", "More", "Move"), pages.map { it.name })
        assertEquals(ids.toSet(), pages.flatMap { it.actions }.toSet())
        assertEquals(ids.size, pages.flatMap { it.actions }.size)
        assertEquals(CardRingPages.defaults[0], pages[0])
        assertEquals(7, pages[1].actions.size)
        assertEquals(14, CardRingPages.layout(pages[1].actions).sumOf { it.span })
        assertTrue(pages.all { CardRingPages.layout(it.actions).all { slot -> slot.cell + slot.span <= 16 } })
        assertTrue(pages[3].actions.containsAll(listOf("Move to Stack", "Library top", "Graveyard")))
        assertTrue(pages[2].actions.indexOf("face/0") < pages[2].actions.indexOf("Link"))
        val custom = CardRingPages.defaults.toMutableList().apply { this[0] = this[0].copy(actions = listOf("Clone", "inspect")) }
        assertEquals(listOf("Clone", "inspect"), CardRingPages.resolve(ids, custom)[0].actions)
    }

    @Test fun overflowingPagesSpillForwardWithoutDuplicatesOrGrowingTheGrid() {
        val ids = List(40) { "extra/$it" }
        val pages = CardRingPages.resolve(ids)
        assertEquals(ids.take(16), pages[2].actions)
        assertEquals(ids.drop(16).take(16), pages[3].actions)
        assertEquals(ids.drop(32), pages[0].actions)
        assertEquals(ids.toSet(), pages.flatMap { it.actions }.toSet())
        assertTrue(pages.all { it.actions.size <= 16 })
    }

    @Test fun unusuallyManyPileDestinationsRemainReachableInBoundedContinuationPages() {
        val ids = List(80) { "Move to pile $it" }
        val pages = CardRingPages.resolve(ids)
        assertEquals(ids.toSet(), pages.flatMap { it.actions }.toSet())
        assertEquals(ids.size, pages.sumOf { it.actions.size })
        assertTrue(pages.all { it.actions.size <= 16 })
    }

    @Test fun widgetsSkipRowEndCellsAndSpillAsAWhole() {
        val actions = List(15) { "extra/$it" } + "corner"
        val custom = CardRingPages.defaults.mapIndexed { index, page -> page.copy(actions = if (index == 0) actions else emptyList()) }
        val pages = CardRingPages.resolve(actions, custom)
        assertEquals(actions.take(15), pages[0].actions)
        assertEquals(listOf("corner"), pages[1].actions)
        val slots = CardRingPages.layout(listOf("a", "b", "c", "corner", "counter/RED"))
        assertEquals(listOf(0, 1, 2, 4, 6), slots.map { it.cell })
        assertTrue(slots.all { it.cell % 4 + it.span <= 4 })
    }

    @Test fun compoundWidgetKeepsOriginalCallbacksAndDisabledState() {
        var calls = 0
        val entry = RingEntry("corner", "P/T", MenuControl("Set corner") { calls += 10 },
            RingCompound("2/3", MenuControl("Decrease corner", false) { calls-- }, MenuControl("Increase corner") { calls++ }))
        assertEquals(2, CardRingPages.span(entry.id))
        assertFalse(entry.compound!!.minus.enabled)
        assertEquals("2/3", entry.compound.value)
        entry.control.click(); entry.compound.plus.click()
        assertEquals(11, calls)
    }

    @Test fun onlyTheValueCellKeepsTheCounterDragCallbackAndRawValue() {
        var delta = 0L
        val entry = RingEntry("counter/BLUE", "blue", MenuControl("Set blue counter") {},
            RingCompound("—", MenuControl("Decrease blue counter") {}, MenuControl("Increase blue counter") {},
                drag = { delta = it }, dragValue = "0"))
        assertEquals(2, CardRingPages.span(entry.id))
        assertEquals("0", entry.compound!!.dragValue)
        assertNotNull(entry.compound.drag).invoke(4)
        assertEquals(4L, delta)
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

    @Test fun cornerDragSupportsIntegersPairsNegativeStepsAndCheckedOverflow() {
        assertEquals("-2/3", cornerDragValue("2/7", -4))
        assertEquals("15", cornerDragValue("12", 3))
        assertNull(cornerDragValue("*/3", 1))
        assertNull(cornerDragValue("${Long.MAX_VALUE}/3", 1))
        assertEquals("-4 -> -2/3", cornerDragLabel("2/7", -4))
    }

    @Test fun squareFitsWithoutCoveringTheCardAtEdgesAndCorners() {
        for ((width, height) in listOf(360f to 640f, 412f to 892f, 892f to 412f, 640f to 360f)) {
            for (x in listOf(0f, width / 2 - 24, width - 48))
                for (y in listOf(0f, height / 2 - 34, height - 68)) for (own in listOf(true, false)) {
                    val card = TableBounds(x, y, 48f, 68f)
                    val b = assertNotNull(CardRingGeometry.place(width, height, card, own)).square
                    assertEquals(CardRingGeometry.squareSide, b.width)
                    assertEquals(b.width, b.height)
                    assertTrue(b.x >= 12 && b.y >= 12 && b.right <= width - 12 && b.bottom <= height - 12, "$width×$height $card $b")
                    assertNull(b.intersection(card), "$card overlaps $b")
                }
        }
    }

    @Test fun largestFittingSideWinsAndOwnershipBreaksAboveBelowTies() {
        val portrait = TableBounds(182f, 412f, 48f, 68f)
        assertEquals(CardRingSide.ABOVE, CardRingGeometry.place(412f, 892f, portrait, true)?.side)
        assertEquals(CardRingSide.BELOW, CardRingGeometry.place(412f, 892f, portrait, false)?.side)
        assertEquals(CardRingSide.ABOVE, CardRingGeometry.place(412f, 892f, portrait.copy(y = 650f), false)?.side)
        assertEquals(CardRingSide.BELOW, CardRingGeometry.place(412f, 892f, portrait.copy(y = 100f), true)?.side)
        assertEquals(CardRingSide.LEFT, CardRingGeometry.place(892f, 412f, TableBounds(800f, 172f, 48f, 68f), true)?.side)
        assertEquals(CardRingSide.RIGHT, CardRingGeometry.place(892f, 412f, TableBounds(44f, 172f, 48f, 68f), false)?.side)
    }

    @Test fun clampingKeepsSquareInsideTwelveDpMarginAndDoesNotShiftItAcrossPages() {
        val card = TableBounds(0f, 800f, 48f, 68f)
        val opening = assertNotNull(CardRingGeometry.place(412f, 892f, card, true))
        assertEquals(12f, opening.square.x)
        for (page in CardRingPages.resolve(List(40) { "action/$it" })) {
            assertTrue(page.actions.size <= 16)
            assertEquals(opening, CardRingGeometry.place(412f, 892f, card, true))
        }
        val right = assertNotNull(CardRingGeometry.place(412f, 892f, card.copy(x = 364f), true))
        assertEquals(400f, right.square.right)
    }

    @Test fun impossibleViewportDoesNotPlaceASquareOverTheCard() {
        assertNull(CardRingGeometry.place(240f, 240f, TableBounds(96f, 86f, 48f, 68f), true))
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
