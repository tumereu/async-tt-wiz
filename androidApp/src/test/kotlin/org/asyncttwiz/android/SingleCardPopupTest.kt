package org.asyncttwiz.android

import org.asyncttwiz.protocol.*
import org.junit.Test
import kotlin.test.*

class SingleCardPopupTest {
    @Test fun tapsOnSeveralCardsLeaveOnlyTheLatestMenuAndTwoFingerPressesOpenNone() {
        val f = TableStatesFixture
        val play = f.initial
        val toolkit = TableToolkitController(f.a, { play }, { true }, { _, _ -> true })
        lateinit var gestures: TableGestureCoordinator
        val opened = mutableListOf<String?>()
        // Wired like the table host: every Actions intent opens through the one toolkit.
        gestures = TableGestureCoordinator({ intent ->
            if (intent is TableIntent.Actions) {
                val g = gestures
                opened += intent.scope?.contextId
                toolkit.open(g.capturedActions ?: intent, requireNotNull(g.actionItem), TableSelection())
            }
        })
        val scene = TablePresentation.scene(f.game, play, f.a, 412f, 892f, false)
        gestures.update(scene, TableSelection(), true)
        val (a, b) = scene.items.filter { item -> item.kind == TableItemKind.CARD && item.zone == PlayZone.BATTLEFIELD &&
            play.board.cards.any { it.id == item.cardId } && scene.hit(item.bounds.center)?.id == item.id }.take(2)

        gestures.down(a.bounds.center, 0); gestures.up(a.bounds.center, 10)
        gestures.down(b.bounds.center, 100); gestures.up(b.bounds.center, 110)
        assertEquals(listOf(a.cardId, b.cardId), opened)
        assertEquals(b.cardId, toolkit.capture?.contextViewId)
        assertEquals(b.cardId, toolkit.manual.scope?.contextId)

        // A second finger landing on another card is a pinch, never a second menu.
        gestures.down(a.bounds.center, 1000)
        gestures.transform(TablePoint(0f, 0f), 1f, TablePoint((a.bounds.center.x + b.bounds.center.x) / 2, a.bounds.center.y))
        gestures.up(a.bounds.center, 1010); gestures.cancel()
        assertEquals(2, opened.size)
        assertEquals(b.cardId, toolkit.manual.scope?.contextId)

        // Opening directly (e.g. from two pile-browser rows) also replaces, never stacks.
        toolkit.openCard(TableScope(requireNotNull(a.cardId), listOf(requireNotNull(a.cardId))))
        assertEquals(a.cardId, toolkit.capture?.contextViewId)
        assertEquals(a.cardId, toolkit.manual.scope?.contextId)
        assertNull(toolkit.panel)
    }
}
