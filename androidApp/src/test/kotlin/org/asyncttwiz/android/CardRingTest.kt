package org.asyncttwiz.android

import org.asyncttwiz.protocol.*
import org.junit.Test
import kotlin.test.*

class CardRingTest {
    @Test fun normalMultiFaceMoreAndCounterPagesFitFiveRowsAndFacesCycleIndividually() {
        val ids = CardRingPages.defaults.flatMap { it.actions } + "Select / deselect"
        val pages = CardRingPages.resolve(ids)
        assertEquals(CardRingPages.defaults[2], pages[2])
        assertEquals(listOf("Select / deselect"), pages[3].actions)
        for (page in pages.slice(1..2)) assertTrue(CardRingPages.layout(page.actions).last().let { it.cell + it.span } <= 25)
        val definition = CardDefinition("Two faces", listOf(DefinitionFace("front", "Front"), DefinitionFace("back", "Back")))
        val submitted = SubmittedDefinition("digest", definition)
        val play = PlayState("game", "play", "a", "b", PlayRoot("root", "seed"), "root",
            BoardState(emptyList(), emptyList(), emptyList(), TurnState("a")), listOf(submitted), emptyList(), InitialStarter(StarterChoice("a"), "a"))
        val card = PlayCard("card", "digest", "a", "zone", "front")
        assertEquals(PlayAction.SetFace("card", "back"), nextPrintedFace(play, card))
        assertEquals(PlayAction.SetFace("card", "front"), nextPrintedFace(play, card.copy(faceId = "back")))
        assertNull(nextPrintedFace(play.copy(definitions = listOf(submitted.copy(definition = definition.copy(faces = definition.faces.take(1))))), card))
    }
    @Test fun ownerEntryIsAssignedToMoreOnlyWhenAvailableForAToken() {
        for (token in listOf(false, true)) {
            val ids = CardRingPages.defaults.flatMap { it.actions }.filter { !it.startsWith("empty/") && (it != "owner" || token) }.distinct()
            val pages = CardRingPages.resolve(ids)
            assertEquals(if (token) listOf("More") else emptyList(), pages.filter { "owner" in it.actions }.map { it.name })
            assertEquals(ids.toSet(), pages.flatMap { it.actions }.filterNot { it.startsWith("empty/") }.toSet())
        }
    }

    @Test fun pagesAreDataAndRepeatedNotesAreAllowedAcrossPagesNotWithinAPage() {
        val ids = CardRingPages.defaults.take(2).flatMap { it.actions } +
            listOf("flip", "otherSide", "switchFace", "Link", "Unlink", "resetCorner", "corner/LEFT/1", "Add Stack effect", "Move to Stack", "Remove token")
        val available = ids.distinct()
        val pages = CardRingPages.resolve(available)
        assertEquals(listOf("Everyday", "Counters", "More", "More 2"), pages.map { it.name })
        assertEquals(ids.toSet(), pages.flatMap { it.actions }.filterNot { it.startsWith("empty/") }.toSet())
        assertTrue(pages.all { it.actions.size == it.actions.distinct().size })
        assertEquals(CardRingPages.defaults[0], pages[0])
        assertEquals(9, pages[1].actions.size)
        assertEquals(15, CardRingPages.layout(pages[1].actions).sumOf { it.span })
        assertTrue(pages.all { CardRingPages.layout(it.actions).all { slot -> slot.cell + slot.span <= 25 } })
        assertTrue("Move to Stack" in pages[3].actions)
        val everydayMoves = listOf("Library top", "Library bottom", "Graveyard", "Exile")
        assertTrue(pages[0].actions.containsAll(everydayMoves))
        assertTrue(pages[2].actions.none { it in everydayMoves })
        assertTrue("switchFace" in pages[0].actions)
        val custom = CardRingPages.defaults.toMutableList().apply { this[0] = this[0].copy(actions = listOf("Clone", "inspect")) }
        assertEquals(listOf("Clone", "inspect"), CardRingPages.resolve(ids, custom)[0].actions)
    }

    @Test fun everydayHasStableFiveColumnCellsIncludingTemporaryMarksForCardsAndTokens() {
        val ids = CardRingPages.defaults.flatMap { it.actions }
        val card = CardRingPages.layout(CardRingPages.resolve(ids)[0].actions)
        assertEquals(listOf("inspect", "note", "corner", "Stack", "Declare without tapping", "flip", "switchFace",
            "Graveyard", "Exile", "Clone", "Ping / Unping", "Hand", "Library top", "Library bottom", "Shuffle into library",
            "damage", "eotCorner"), card.map { it.id })
        assertEquals(listOf(0, 1, 2, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 18), card.map { it.cell })
        val tokenIds = ids.filterNot { it in listOf("Hand", "Library top", "Library bottom", "Shuffle into library") } + "Remove token"
        val token = CardRingPages.layout(CardRingPages.resolve(tokenIds, token = true)[0].actions)
        assertEquals(card.map { it.cell }, token.map { it.cell })
        assertEquals("Remove token", token.single { it.cell == 12 }.id)
        assertTrue(token.filter { it.cell in 13..15 }.all { it.id.startsWith("empty/") })
        assertEquals(4, inlinePopupGridRows(card))
    }

    @Test fun countersFillLastColumnAndMoreKeepsExactlyThreeRowsWithAnIntentionalEotNoteDuplicate() {
        val pages = CardRingPages.resolve(CardRingPages.defaults.flatMap { it.actions } + "extra")
        assertFalse("eotNote" in pages[0].actions)
        val counters = CardRingPages.layout(pages[1].actions)
        assertEquals(listOf(0, 2, 4, 5, 7, 9, 10, 12, 14), counters.map { it.cell })
        assertEquals(listOf("note", "eotNote", "resetCounters"), counters.filter { it.cell % 5 == 4 }.map { it.id })
        val more = CardRingPages.layout(pages[2].actions)
        assertEquals((0..14).toList(), more.map { it.cell })
        assertEquals(listOf("owner", "otherSide", "Link", "Unlink", "eotNote"), more.take(5).map { it.id })
        assertEquals(listOf("corner/LEFT/1", "corner/LEFT/-1", "corner/RIGHT/1", "corner/RIGHT/-1", "resetCorner"), more.drop(5).take(5).map { it.id })
        assertEquals(listOf("eotCorner/LEFT/1", "eotCorner/LEFT/-1", "eotCorner/RIGHT/1", "eotCorner/RIGHT/-1", "Temp pile"), more.drop(10).map { it.id })
        assertEquals(listOf("Counters", "More"), pages.filter { "eotNote" in it.actions }.map { it.name })
        assertEquals(listOf("extra"), pages[3].actions)
        val saved = CardRingPages.defaults.map { it.copy(actions = listOf("eotNote", "eotNote")) }
        assertTrue(CardRingPages.resolve(listOf("eotNote"), saved).all { it.actions == listOf("eotNote") })
    }

    @Test fun battlefieldModificationsAreDisabledElsewhereExceptStackAndProjectionNotes() {
        for (zone in PlayZone.entries) {
            assertEquals(zone == PlayZone.BATTLEFIELD, cardModificationEnabled(zone))
            assertEquals(zone in listOf(PlayZone.BATTLEFIELD, PlayZone.STACK), cardModificationEnabled(zone, note = true))
            assertFalse(cardModificationEnabled(zone, projection = true))
            assertTrue(cardModificationEnabled(zone, note = true, projection = true))
        }
    }

    @Test fun overflowingPagesSpillForwardWithoutDuplicatesOrGrowingTheGrid() {
        val ids = List(40) { "extra/$it" }
        val pages = CardRingPages.resolve(ids)
        assertEquals(ids.take(25), pages[3].actions)
        assertEquals(ids.drop(25), pages[4].actions)
        assertTrue(pages[0].actions.isEmpty())
        assertEquals(ids.toSet(), pages.flatMap { it.actions }.filterNot { it.startsWith("empty/") }.toSet())
        assertTrue(pages.all { it.actions.size <= 25 })
    }

    @Test fun unusuallyManyPileDestinationsRemainReachableInBoundedContinuationPages() {
        val ids = List(80) { "Move to pile $it" }
        val pages = CardRingPages.resolve(ids)
        assertEquals(ids.toSet(), pages.flatMap { it.actions }.filterNot { it.startsWith("empty/") }.toSet())
        assertEquals(ids.size, pages.sumOf { page -> page.actions.count { !it.startsWith("empty/") } })
        assertTrue(pages.all { it.actions.size <= 25 })
    }

    @Test fun widgetsSkipRowEndCellsAndSpillAsAWhole() {
        val actions = List(24) { "extra/$it" } + "corner"
        val custom = CardRingPages.defaults.mapIndexed { index, page -> page.copy(actions = if (index == 0) actions else emptyList()) }
        val pages = CardRingPages.resolve(actions, custom)
        assertEquals(actions.take(24), pages[0].actions)
        assertEquals(listOf("corner"), pages[2].actions)
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

    @Test fun fifteenPlusPagesHaveOnlyMoreContinuationsAndWrapInBothDirections() {
        val ids = List(400) { "Move to pile $it" }
        val pages = CardRingPages.resolve(ids)
        assertEquals(listOf("Everyday", "Counters", "More") + (2..17).map { "More $it" }, pages.map { it.name })
        assertEquals(ids, pages.flatMap { it.actions }.filterNot { it.startsWith("empty/") })
        assertEquals(pages.lastIndex, CardRingPages.adjacent(0, -1, pages.size))
        assertEquals(0, CardRingPages.adjacent(pages.lastIndex, 1, pages.size))
        assertTrue(pages.all { page -> CardRingPages.layout(page.actions).all { it.cell + it.span <= 25 } })
    }

    @Test fun handHasOnlyItsOwnTwoPagesAndExactActions() {
        val ids = CardRingPages.defaults.flatMap { it.actions } + CardRingPages.hand.flatMap { it.actions }
        val pages = CardRingPages.resolve(ids, inHand = true)
        assertEquals(listOf("Hand", "Random"), pages.map { it.name })
        assertEquals(listOf("inspect", "Play", "Reveal", "Discard", "Exile", "Library top", "Library bottom", "Shuffle into library"), pages[0].actions)
        assertEquals(listOf("Discard random", "Reveal random", "Exile random", "Random to library bottom"), pages[1].actions)
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
        assertEquals("−4 → -2/3", cornerDragLabel("2/7", -4))
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
