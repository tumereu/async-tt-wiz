package org.asyncttwiz.android

import org.asyncttwiz.protocol.*
import org.junit.Test
import kotlin.test.*

class CardMenuLayoutTest {
    private val f = TableStatesFixture

    // TBL-071, CMB-003 (owner 2026-11-06: a combat copy has no menu)
    @Test fun contextPicksTheLayoutFromTheTappedView() {
        val play = f.combat()
        val board = play.board
        val sentinel = f.source(f.a, "1/3")
        assertEquals(CardMenuLayout.CARD, CardRingPages.context(board, sentinel.id))
        assertNull(CardRingPages.context(board, f.id("attacker1")))
        assertNull(CardRingPages.context(board, f.id("blocker")))
        assertTrue(CardRingPages.combatCopy(board, f.id("attacker1")) && CardRingPages.combatCopy(board, f.id("blocker")))
        assertFalse(CardRingPages.combatCopy(board, sentinel.id))
        // Stack entries have no card menu: tapping the Stack opens the Stack list (STK-018). A real card in the Stack, only
        // in a state recorded before r35 (STK-003), has none either.
        assertNull(CardRingPages.context(board, f.id("effect")))
        val stackCard = CombatStackRules.stack(board).cards.single { it != f.id("effect") }
        assertNull(CardRingPages.context(board, stackCard))
        val hand = board.containers.single { it.zone == PlayZone.HAND && it.accountId == f.a }
        assertEquals(CardMenuLayout.HAND, CardRingPages.context(board, hand.cards.first()))
        val library = board.containers.single { it.zone == PlayZone.LIBRARY && it.accountId == f.a }
        assertEquals(CardMenuLayout.PANEL, CardRingPages.context(board, library.cards.first()))
        assertNull(CardRingPages.context(board, f.id("missing")))
        assertTrue(CardRingPages.declaredAttacker(board, listOf(sentinel.id)))
        assertFalse(CardRingPages.declaredAttacker(board, listOf(f.source(f.b, "2/3").id)))
    }

    // TBL-048, ZON-003
    @Test fun holdingTheHandBackgroundPreviewsNothingButAHandCardStillDoes() {
        val play = f.initial
        val scene = TablePresentation.scene(f.game, tableBoard(play), f.a, 412f, 892f, false)
        val hand = scene.items.single { it.kind == TableItemKind.HAND && it.owner == f.a }
        assertTrue(hand.cards.isNotEmpty())
        val background = (0..40).flatMap { x -> (0..10).map { y ->
            TablePoint(hand.bounds.x + hand.bounds.width * x / 40f, hand.bounds.y + hand.bounds.height * y / 10f) } }
            .first { scene.hit(it)?.id == hand.id }
        val events = mutableListOf<TableIntent>()
        val gesture = TableGestureCoordinator(events::add)
        gesture.update(scene, true)
        gesture.down(background, 0); gesture.tick(700); gesture.up(background, 750)
        assertTrue(events.none { it is TableIntent.Peek }, "$events")
        val card = scene.items.first { it.kind == TableItemKind.CARD && it.zone == PlayZone.HAND && it.owner == f.a && it.visible.isNotEmpty() }
        val point = card.visible.first().center
        events.clear()
        gesture.down(point, 1000); gesture.tick(1700); gesture.up(point, 1750)
        assertEquals(listOf<TableIntent>(TableIntent.Peek(card.cardId), TableIntent.Peek(null, released = true)), events)
    }
}
