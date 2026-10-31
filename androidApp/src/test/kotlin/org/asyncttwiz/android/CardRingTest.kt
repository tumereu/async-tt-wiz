package org.asyncttwiz.android

import org.asyncttwiz.protocol.*
import org.junit.Test
import kotlin.test.*

class CardRingTest {
    // TBL-076
    @Test fun printedFacesCycleIndividually() {
        val definition = CardDefinition("Two faces", listOf(DefinitionFace("front", "Front"), DefinitionFace("back", "Back")))
        val submitted = SubmittedDefinition("digest", definition)
        val play = PlayState("game", "play", "a", "b", PlayRoot("root", "seed"), "root",
            BoardState(emptyList(), emptyList(), emptyList(), TurnState("a")), listOf(submitted), emptyList(), InitialStarter(StarterChoice("a"), "a"))
        val card = PlayCard("card", "digest", "a", "zone", "front")
        assertEquals(PlayAction.SetFace("card", "back"), nextPrintedFace(play, card))
        assertEquals(PlayAction.SetFace("card", "front"), nextPrintedFace(play, card.copy(faceId = "back")))
        assertNull(nextPrintedFace(play.copy(definitions = listOf(submitted.copy(definition = definition.copy(faces = definition.faces.take(1))))), card))
    }
    // TBL-094
    @Test fun battlefieldModificationsAreDisabledElsewhereExceptStackAndProjectionNotes() {
        for (zone in PlayZone.entries) {
            assertEquals(zone == PlayZone.BATTLEFIELD, cardModificationEnabled(zone))
            assertEquals(zone in listOf(PlayZone.BATTLEFIELD, PlayZone.STACK), cardModificationEnabled(zone, note = true))
            assertFalse(cardModificationEnabled(zone, projection = true))
            assertTrue(cardModificationEnabled(zone, note = true, projection = true))
        }
    }

    @Test fun widgetsSkipRowEndCells() {
        val slots = CardRingPages.layout(listOf("a", "b", "c", "d", "corner", "counter/RED", "damage", "eotCorner"))
        assertEquals(listOf(0, 1, 2, 3, 5, 7, 10, 12), slots.map { it.cell })
        assertTrue(slots.all { it.cell % 5 + it.span <= 5 })
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

    // TBL-065
    @Test fun pagesWrapInBothDirections() {
        val pages = CardRingPages.pages(CardMenuLayout.CARD)
        assertEquals(pages.lastIndex, CardRingPages.adjacent(0, -1, pages.size))
        assertEquals(0, CardRingPages.adjacent(pages.lastIndex, 1, pages.size))
    }

    // TBL-079
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

    // TBL-069
    @Test fun inputGuardUsesPressStartNotReleaseAndIncludesTheWholeDoubleTapWindow() {
        val guard = CardRingInputGuard(1000)
        assertFalse(guard.accepts(999))
        assertFalse(guard.accepts(1000))
        assertFalse(guard.accepts(1250))
        val startedDuringGuard = guard.accepts(1279)
        assertFalse(startedDuringGuard) // still false when this press eventually releases
        assertTrue(guard.accepts(1280))
    }

    // TBL-080
    @Test fun cornerDragSupportsIntegersPairsNegativeStepsAndCheckedOverflow() {
        assertEquals("-2/3", cornerDragValue("2/7", -4))
        assertEquals("15", cornerDragValue("12", 3))
        assertNull(cornerDragValue("*/3", 1))
        assertNull(cornerDragValue("${Long.MAX_VALUE}/3", 1))
        assertEquals("−4 → -2/3", cornerDragLabel("2/7", -4))
    }

    // TBL-067
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

    // TBL-067
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
        for (page in CardMenuLayout.entries.flatMap { CardRingPages.pages(it) }) {
            assertTrue(page.actions.size <= 25)
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
