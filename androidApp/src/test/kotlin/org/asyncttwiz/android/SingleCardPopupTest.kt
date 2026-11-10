package org.asyncttwiz.android

import org.asyncttwiz.protocol.*
import org.junit.Test
import kotlin.test.*

class SingleCardPopupTest {
    // SYS-043
    @Test fun tapsOnSeveralCardsLeaveOnlyTheLatestMenuAndTwoFingerPressesOpenNone() {
        val f = TableStatesFixture
        // Bob's two Forests told apart (one damaged), so each lies alone rather than in one stack (TBL-053)
        val play = f.initial.let { p -> p.copy(board = p.board.copy(cards = p.board.cards.map { card ->
            if (card.containerId == f.id("b/BATTLEFIELD") && card.id == p.board.containers.single { it.id == f.id("b/BATTLEFIELD") }.cards.first())
                card.copy(damage = "1") else card })) }
        // Bob's table: a tap on another player's permanent opens nothing (TBL-124), his own lands open their menus
        val toolkit = TableToolkitController(f.b, { engineTableBoard(play, f.b) }, { true }, { _, _ -> true })
        lateinit var gestures: TableGestureCoordinator
        val opened = mutableListOf<String?>()
        // Wired like the table host: every Actions intent opens through the one toolkit.
        gestures = TableGestureCoordinator({ intent ->
            if (intent is TableIntent.Actions) {
                val g = gestures
                opened += intent.scope?.contextId
                toolkit.open(g.capturedActions ?: intent, requireNotNull(g.actionItem))
            }
        })
        val scene = TablePresentation.scene(f.game, engineTableBoard(play, f.b), f.b, 412f, 892f, false)
        gestures.update(scene, true)
        // Bob's own lands: their menus list their mana abilities, greyed while he can't act (TBL-115, TBL-123); a card offering
        // nothing opens no menu (TBL-116)
        fun forest(id: String?) = play.board.cards.singleOrNull { it.id == id }?.let { card ->
            card.owner == f.b && play.definitions.single { it.digest == card.definition }.definition.name == "Forest" } == true
        val (a, b) = scene.items.filter { item -> item.kind == TableItemKind.CARD && item.zone == PlayZone.BATTLEFIELD &&
            forest(item.cardId) && scene.hit(item.bounds.center)?.id == item.id }.take(2)

        gestures.down(a.bounds.center, 0); gestures.up(a.bounds.center, 10)
        gestures.down(b.bounds.center, 100); gestures.up(b.bounds.center, 110)
        assertEquals(listOf(a.cardId, b.cardId), opened)
        assertEquals(b.cardId, toolkit.capture?.contextViewId)
        assertEquals(b.cardId, toolkit.capture?.contextViewId)

        // A second finger landing on another card is a pinch, never a second menu.
        gestures.down(a.bounds.center, 1000)
        gestures.transform(TablePoint(0f, 0f), 1f, TablePoint((a.bounds.center.x + b.bounds.center.x) / 2, a.bounds.center.y))
        gestures.up(a.bounds.center, 1010); gestures.cancel()
        assertEquals(2, opened.size)
        assertEquals(b.cardId, toolkit.capture?.contextViewId)

        // Opening directly (e.g. from two pile-browser rows) also replaces, never stacks.
        toolkit.openCard(TableScope(requireNotNull(a.cardId), listOf(requireNotNull(a.cardId))))
        assertEquals(a.cardId, toolkit.capture?.contextViewId)
        assertEquals(a.cardId, toolkit.capture?.contextViewId)
        assertNull(toolkit.panel)
    }
}
