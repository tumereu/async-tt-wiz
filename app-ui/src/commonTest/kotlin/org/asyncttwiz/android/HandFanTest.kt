package org.asyncttwiz.android

import org.asyncttwiz.protocol.*
import kotlin.math.abs
import kotlin.test.*

/** Your hand as a fan in landscape (ZON-138 to ZON-142, owner 2026-11-25), the desktop table's fan (WEB-073) shared. */
class HandFanTest {
    private val bar = 412f - TableControlsHeight

    private fun scene(hand: Int, turn: Float? = null, raised: String? = null, size: Float = 1f, play: PlayState = TableFixture.play(hand = hand),
        handRow: List<HandRowCard> = emptyList(), landscape: Boolean = true) =
        TablePresentation.scene(TableFixture.game, tableBoard(play), "a", if (landscape) 892f else 412f, if (landscape) 412f else 892f, landscape,
            TableLayoutState(scroll = turn?.let { mapOf("a/HAND" to it) }.orEmpty(), raised = raised), handRow = handRow,
            appearance = CardAppearance(customizeAreas = true, areas = mapOf(CardArea.HAND to size)))

    private fun TableScene.hand() = items.single { it.kind == TableItemKind.HAND && it.owner == "a" }
    private fun TableScene.cards() = items.filter { it.kind == TableItemKind.CARD && it.viewport == hand().viewport }
    private fun TableScene.card(n: Int) = items.single { it.cardId == "a/HAND/$n" }
    private fun TableScene.turn() = assertNotNull(viewports.single { it.id == hand().viewport }.turn)
    /** Whether a card lies wholly below the screen's bottom (turned away). */
    private fun TableItem.sunk() = bounds.y >= 412f

    /** The most cards a still fan holds at this hand size. */
    private fun capacity(size: Float = 1f) = (1..30).first { scene(it + 1, size = size).turn().turns }

    // ZON-138 (owner 2026-11-25): in landscape your hand is a fan between your piles and the controls, its cards turned a little
    // and overlapping, standing over the battlefield and partly below the screen's bottom, without the hand row's background (no
    // white row) and its count in the bar; portrait keeps its row
    @Test fun inLandscapeYourHandIsAFanBetweenYourPilesAndTheControls() {
        for (size in listOf(1f, 2.5f)) {
            val scene = scene(5, size = size)
            val hand = scene.hand()
            assertTrue(hand.handFan)
            val port = scene.viewports.single { it.id == hand.viewport }
            assertFalse(port.hand, "no hand row background is drawn under the fan")
            val cards = scene.cards().sortedBy { it.bounds.x }
            assertEquals(5, cards.size)
            assertTrue(cards.all { it.cardTransform != null && it.handSlot != null })
            assertTrue(cards.first().cardTransform!!.rotation < 0f && cards.last().cardTransform!!.rotation > 0f, "turned either way")
            assertTrue(cards.zipWithNext().all { (a, b) -> b.bounds.x < a.bounds.right }, "overlapping")
            assertTrue(cards.all { it.bounds.bottom > 412f }, "partly below the screen's bottom")
            val h = 40f * size
            assertEquals(412f - h * HandFan.LANDSCAPE_SHOWN, hand.bounds.y, .5f)
            val piles = TablePresentation.Landscape.rowRight(892f, own = true)
            val controls = 892f - TablePresentation.Landscape.controls(892f)
            assertTrue(cards.all { it.bounds.x > piles && it.bounds.right < controls }, "between the piles and the controls")
            assertEquals(TableBounds(hand.bounds.x, bar, hand.bounds.width, 412f - bar), hand.handBand, "its own part in the bar only")
            assertTrue(hand.bounds.right <= controls)
        }
        // 2.5x cards stand over the battlefield, which is laid out above them
        val large = scene(5, size = 2.5f)
        assertTrue(large.hand().bounds.y < bar)
        assertEquals(large.hand().bounds.y - 4f, large.world!!.viewport.bottom, .5f)
        // Portrait keeps its row
        val portrait = scene(5, landscape = false)
        assertFalse(portrait.hand().handFan)
        assertTrue(portrait.cards().all { it.cardTransform == null })
    }

    // ZON-139 (owner 2026-11-25): as many cards as fit with at least 20 dp each make a still fan (about 10 on an 892 dp screen with
    // 1x cards); with more, the hand's cards turn like a wheel through that many places, cards turned away sunk below the screen's
    // bottom; it stops with the first card at the fan's right end or the last at its left end
    @Test fun moreCardsThanFitTurnLikeAWheelBetweenItsEnds() {
        val fits = capacity()
        assertTrue(fits in 9..12, "about 10 fit at 1x: $fits")
        assertTrue(capacity(2.5f) < fits, "fewer at 2.5x")
        val still = scene(fits)
        assertFalse(still.turn().turns)
        assertTrue(still.cards().zipWithNext().all { (a, b) -> b.handSlot!!.x - a.handSlot!!.x >= HandFan.MIN_SLOT - .01f })
        val n = fits + 4
        val turning = scene(n)
        val turn = turning.turn()
        assertEquals(HandFan.MIN_SLOT, turn.pitch, .001f)
        assertEquals(fits, turn.slots)
        assertEquals(-(fits - 1) * turn.pitch, turn.min, .01f)
        assertEquals((n - 1) * turn.pitch, turn.max, .01f)
        // At rest from its first card: the first [fits] in view, each with its own part of the fan; the rest turned away
        for (i in 0 until n) {
            val card = turning.card(i)
            if (i < fits) { assertNotNull(card.handSlot); assertTrue(card.bounds.y < 412f) }
            else { assertNull(card.handSlot); assertTrue(card.sunk(), "card $i turned away") }
        }
        // Turned three cards on: the first three sink at the left, the next [fits] in view in the hand's order
        val on = scene(n, turn = 3 * turn.pitch)
        assertTrue((0 until 3).all { on.card(it).sunk() })
        val shown = (3 until 3 + fits).map { on.card(it) }
        assertTrue(shown.all { !it.sunk() && it.handSlot != null })
        assertTrue(shown.zipWithNext().all { (a, b) -> a.handSlot!!.x < b.handSlot!!.x }, "in the hand's order")
        assertEquals(still.cards().map { it.handSlot!!.x }.sorted(), shown.map { it.handSlot!!.x }, "in the still fan's places")
        // Halfway between two places a card sinks part of the way along the curve, turned further out
        val half = scene(n, turn = 2.5f * turn.pitch)
        val sinking = half.card(2)
        assertTrue(sinking.bounds.y in on.card(3).bounds.y..412f && sinking.cardTransform!!.rotation < shown.first().cardTransform!!.rotation)
        // The ends: never further than the first card at the right end or the last at the left end
        val right = scene(n, turn = -10000f)
        assertEquals(turn.min, right.turn().value, .01f)
        assertEquals(still.cards().maxOf { it.handSlot!!.x }, right.card(0).handSlot!!.x, .01f)
        assertTrue((1 until n).all { right.card(it).sunk() })
        val left = scene(n, turn = 10000f)
        assertEquals(still.cards().minOf { it.handSlot!!.x }, left.card(n - 1).handSlot!!.x, .01f)
        assertTrue((0 until n - 1).all { left.card(it).sunk() })
        // It rests on whole cards
        assertEquals(3 * turn.pitch, turn.settled(3.4f * turn.pitch), .01f)
        assertEquals(turn.max, turn.settled(10000f), .01f)
    }

    // ZON-140 (owner 2026-11-25): a small "+N" at each end with cards turned away there; the hand's count stays
    @Test fun aPlusNAtEachEndWithCardsTurnedAway() {
        val fits = capacity()
        val n = fits + 5
        val start = scene(n)
        assertEquals(listOf(5), start.hand().fanPlus.map { it.second })
        assertTrue(start.hand().fanPlus.single().first.x > start.hand().bounds.center.x, "at the right end")
        assertEquals(n.toString(), start.hand().value)
        val pitch = start.turn().pitch
        val middle = scene(n, turn = 2 * pitch).hand().fanPlus
        assertEquals(listOf(2, 3), middle.sortedBy { it.first.x }.map { it.second })
        assertTrue(scene(fits).hand().fanPlus.isEmpty())
        // ... drawn over the cards at the fan's bottom
        assertTrue(middle.all { it.first.y > bar && it.first.y < 412f })
    }

    // ZON-141 (owner 2026-11-25): a raised card stays raised when the fan turns it away: popped out, whole in view, over the end
    // it went past
    @Test fun aRaisedCardTurnedAwayStaysRaisedOverTheFansEnd() {
        val fits = capacity()
        val n = fits + 3
        val pitch = scene(n).turn().pitch
        val inView = scene(n, raised = "a/HAND/1").card(1)
        assertTrue(inView.raised && inView.bounds.bottom < 412f)
        val away = scene(n, turn = 3 * pitch, raised = "a/HAND/1")
        val card = away.card(1)
        assertTrue(card.raised)
        assertTrue(card.bounds.y >= 0f && card.bounds.bottom <= 412f, "whole in view: ${card.bounds}")
        assertTrue(abs(card.bounds.center.x - away.card(3).bounds.center.x) < pitch * 2, "over the fan's left end")
        assertNull(card.handSlot)
        assertEquals(card.id, away.hit(TablePoint(card.bounds.center.x, card.bounds.y + 4f))?.id, "found by what shows of it")
    }

    // ZON-142 (owner 2026-11-25): a card drawn into a turning fan lands in its place; turned away, the "+N" counts it and the fan
    // doesn't turn by itself
    @Test fun aDrawnCardLandsInItsPlaceAndTheFanDoesntTurn() {
        val fits = capacity()
        val n = fits + 2
        val pitch = scene(n).turn().pitch
        val before = scene(n, turn = pitch)
        val play = TableFixture.play(hand = n + 1)
        val after = scene(n + 1, turn = pitch, play = play)
        assertEquals(before.turn().value, after.turn().value)
        assertEquals((1 until 1 + fits).map { before.card(it).handSlot }, (1 until 1 + fits).map { after.card(it).handSlot })
        assertTrue(after.card(n).sunk())
        assertEquals(listOf(1, 1), before.hand().fanPlus.sortedBy { it.first.x }.map { it.second })
        assertEquals(listOf(1, 2), after.hand().fanPlus.sortedBy { it.first.x }.map { it.second })
    }

    // ZON-138, ZON-139, ZON-079: your cards from other zones stay at the fan's right end, after the divider, while the hand turns
    @Test fun cardsFromOtherZonesStayAtTheRightEndAfterTheDivider() {
        val base = TableFixture.play(hand = 14)
        val graveyard = PlayCard("a/GRAVEYARD/0", TableFixture.submitted.digest, "a", "a/GRAVEYARD", "face")
        val play = base.copy(board = base.board.copy(cards = base.board.cards + graveyard, containers = base.board.containers.map {
            if (it.id == "a/GRAVEYARD") it.copy(cards = listOf(graveyard.id)) else it }))
        val row = listOf(HandRowCard(graveyard.id, PlayZone.GRAVEYARD))
        for (turn in listOf(0f, 3 * HandFan.MIN_SLOT, -2 * HandFan.MIN_SLOT)) {
            val scene = scene(14, turn = turn, play = play, handRow = row)
            assertTrue(scene.turn().turns)
            val extra = scene.items.single { it.id == "handRow/${graveyard.id}" }
            val divider = assertNotNull(scene.hand().handDivider)
            val inView = scene.cards().filter { it.handZone == null && !it.sunk() }
            assertTrue(inView.all { it.handSlot!!.right <= divider }, "the hand's cards before the divider")
            assertTrue(extra.handSlot!!.x > divider && !extra.sunk())
            assertEquals(extra.handSlot, scene(14, play = play, handRow = row).items.single { it.id == extra.id }.handSlot, "it stays put")
        }
    }

    // ZON-139, ZON-081, ZON-077 (owner 2026-11-25): a sideways drag on the fan (a card's too) turns it, never past its ends; let go
    // without a flick it rests on the nearest whole card; up or down drags the card; a still fan doesn't move sideways
    @Test fun aSidewaysDragTurnsTheFanAndUpDragsTheCard() {
        val fits = capacity()
        val n = fits + 4
        val scene = scene(n)
        val turn = scene.turn()
        val events = mutableListOf<TableIntent>()
        val gestures = TableGestureCoordinator(events::add)
        gestures.update(scene, true)
        val card = scene.card(2)
        val at = card.handSlot!!.center
        assertEquals(card.id, scene.hit(at)?.id)
        // Leftwards 2.3 cards (turning the hand on), resting before letting go
        val to = TablePoint(at.x - 2.3f * turn.pitch, at.y)
        gestures.down(at, 0); gestures.move(TablePoint(at.x - 10f, at.y), 20); gestures.move(to, 60); gestures.move(to, 300)
        val scrolls = events.filterIsInstance<TableIntent.Scroll>()
        assertTrue(scrolls.all { it.viewportId == "a/HAND" })
        assertEquals(2.3f * turn.pitch, scrolls.last().offset, .01f)
        gestures.up(to, 320)
        var time = 336L
        while (gestures.busy && time < 3000) { gestures.tick(time); time += 16 }
        assertFalse(gestures.busy)
        assertEquals(2 * turn.pitch, events.filterIsInstance<TableIntent.Scroll>().last().offset, .01f, "rests on a whole card")
        assertTrue(events.none { it is TableIntent.Camera || it is TableIntent.Move || it is TableIntent.Actions })
        // Never past its end
        events.clear()
        gestures.down(at, 1000); gestures.move(TablePoint(at.x + 2000f, at.y), 1100); gestures.move(TablePoint(at.x + 2000f, at.y), 1400)
        assertEquals(turn.min, events.filterIsInstance<TableIntent.Scroll>().last().offset, .01f)
        gestures.up(TablePoint(at.x + 2000f, at.y), 1420)
        // Up drags the card
        events.clear()
        gestures.down(at, 3000); gestures.move(TablePoint(at.x, at.y - 120f), 3100); gestures.up(TablePoint(at.x, at.y - 120f), 3120)
        assertTrue(events.none { it is TableIntent.Scroll })
        // A still fan doesn't move sideways
        val stillScene = scene(fits)
        val still = TableGestureCoordinator(events::add).also { it.update(stillScene, true) }
        events.clear()
        val p = stillScene.card(1).handSlot!!.center
        still.down(p, 0); still.move(TablePoint(p.x - 80f, p.y), 50); still.up(TablePoint(p.x - 80f, p.y), 300)
        assertTrue(events.none { it is TableIntent.Scroll || it is TableIntent.Camera || it is TableIntent.Move }, "$events")
    }

    // ZON-139 (owner 2026-11-25): a flick carries the fan on a little after the finger lets go, slowing down, onto a whole card
    @Test fun aFlickCarriesTheFanOnAndSlowsDown() {
        val n = capacity() + 8
        val scene = scene(n)
        val turn = scene.turn()
        val events = mutableListOf<TableIntent>()
        val gestures = TableGestureCoordinator(events::add)
        gestures.update(scene, true)
        val at = scene.card(1).handSlot!!.center
        // 60 dp leftwards in 60 ms: 1000 dp/s
        gestures.down(at, 0)
        for (step in 1..6) gestures.move(TablePoint(at.x - 10f * step, at.y), 10L * step)
        val released = events.filterIsInstance<TableIntent.Scroll>().last().offset
        assertEquals(60f, released, .01f)
        gestures.up(TablePoint(at.x - 60f, at.y), 60)
        assertTrue(gestures.busy, "it carries on")
        val glide = mutableListOf<Float>()
        var time = 76L
        while (gestures.busy && time < 5000) { gestures.tick(time); events.filterIsInstance<TableIntent.Scroll>().lastOrNull()?.let { glide += it.offset }; time += 16 }
        assertFalse(gestures.busy)
        val rest = glide.last()
        assertTrue(rest > released + turn.pitch, "carried on past where it was let go: $rest")
        assertEquals(0f, rest % turn.pitch, .01f, "onto a whole card")
        // (its last step lands it on the card)
        val steps = glide.distinct().zipWithNext { a, b -> b - a }.dropLast(1)
        assertTrue(steps.zipWithNext().all { (a, b) -> b <= a + .01f }, "slowing down: $steps")
        // A touch stops a gliding fan where it is; as the touch ends the fan settles on a whole card from there
        val again = TableGestureCoordinator(events::add).also { it.update(scene, true) }
        again.down(at, 10_000)
        for (step in 1..6) again.move(TablePoint(at.x - 10f * step, at.y), 10_000L + 10L * step)
        again.up(TablePoint(at.x - 60f, at.y), 10_060)
        again.tick(10_076); again.tick(10_092)
        val stopped = events.filterIsInstance<TableIntent.Scroll>().last().offset
        again.down(TablePoint(at.x, at.y - 200f), 10_100)
        again.up(TablePoint(at.x, at.y - 200f), 10_120)
        events.clear()
        time = 10_136L
        while (again.busy && time < 15_000) { again.tick(time); time += 16 }
        val settled = events.filterIsInstance<TableIntent.Scroll>().last().offset
        assertEquals(0f, settled % turn.pitch, .01f, "on a whole card")
        assertTrue(abs(settled - stopped) <= turn.pitch / 2 + .01f, "near where the touch stopped it: $stopped -> $settled")
    }
}
