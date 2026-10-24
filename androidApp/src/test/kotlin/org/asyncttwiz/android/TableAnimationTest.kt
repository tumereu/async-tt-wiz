package org.asyncttwiz.android

import org.asyncttwiz.protocol.*
import org.junit.Test
import kotlin.test.*

class TableAnimationTest {
    private fun moved(play: PlayState, ids: List<String>, destination: String): PlayState = play.copy(board = play.board.copy(
        cards = play.board.cards.map { if (it.id in ids) it.copy(containerId = destination,
            placement = if (destination.endsWith("BATTLEFIELD")) PlayPlacement(BoardPosition(2, 2), it.id) else null) else it },
        containers = play.board.containers.map { c -> c.copy(cards = c.cards.filterNot { it in ids } + if (c.id == destination) ids else emptyList()) }))

    @Test fun confirmationBeforeHistoryIndexNeverPublishesTheOlderBoardAgain() {
        val initial = TableFixture.play(hand = 1)
        val predicted = moved(initial, listOf("a/HAND/0"), "a/BATTLEFIELD").copy(currentNodeId = "predicted-node")
        val endpoint = TableEndpointPresentation()
        endpoint.presented(initial)
        assertEquals(predicted, endpoint.live(predicted, initial, awaitingIndex = false))
        endpoint.presented(predicted)
        val animation = HistorySceneAnimation(TableFixture.scene(initial))
        animation.retarget(TableFixture.scene(predicted), 1f)
        val confirmed = predicted.copy()
        val duringIndexing = endpoint.live(confirmed, initial, awaitingIndex = true)!!
        assertEquals(confirmed, duringIndexing)
        assertFalse(animation.retarget(TableFixture.scene(duringIndexing), .8f))
        assertFalse(animation.retarget(TableFixture.scene(confirmed), 1f))

        // A correction to that same predicted node also goes straight to the accepted endpoint,
        // not via the old indexed board and then forward for a second time.
        val correction = confirmed.copy(board = confirmed.board.copy(cards = confirmed.board.cards.map {
            if (it.id == "a/HAND/0") it.copy(placement = PlayPlacement(BoardPosition(3, 2), it.id)) else it
        }))
        val current = animation.frame(.8f).single { it.item.cardId == "a/HAND/0" }.bounds
        assertEquals(correction, endpoint.live(correction, initial, awaitingIndex = true))
        assertTrue(animation.retarget(TableFixture.scene(correction), .8f))
        assertEquals(current, animation.frame(0f).single { it.item.cardId == "a/HAND/0" }.bounds)

        // Unseen remote nodes still wait for history's foreground/catch-up classification.
        assertEquals(initial, endpoint.live(correction.copy(currentNodeId = "remote-node"), initial, awaitingIndex = true))
    }

    @Test fun matchingConfirmationDoesNotRestartEvenMidFlightOrChangeCardIdentity() {
        val initial = TableFixture.play(hand = 1)
        val predicted = TableFixture.scene(moved(initial, listOf("a/HAND/0"), "a/BATTLEFIELD"))
        val animation = HistorySceneAnimation(TableFixture.scene(initial))
        assertTrue(animation.retarget(predicted, 1f))
        val before = animation.frame(.8f)
        // Simulate a receipt changing labels/disclosure metadata and iteration order, not geometry.
        val confirmed = predicted.copy(items = predicted.items.reversed().map { it.copy(label = "Accepted ${it.label}") })
        assertEquals(predicted.animationTargets, confirmed.animationTargets)
        assertFalse(animation.retarget(confirmed, .8f))
        assertEquals(before.map { it.bounds }, animation.frame(.8f).map { it.bounds })
        assertTrue(animation.frame(.8f).all { it.item.label.startsWith("Accepted") })
        assertFalse(animation.retarget(confirmed, 1f))
        assertEquals(predicted.items.single { it.cardId == "a/HAND/0" }.bounds,
            animation.frame(1f).single { it.item.cardId == "a/HAND/0" }.bounds)
    }

    @Test fun correctionStartsAtCurrentDrawnBoundsAndRemainsVisibleAcrossViewports() {
        val initial = TableFixture.play(hand = 1)
        val target = TableFixture.scene(moved(initial, listOf("a/HAND/0"), "a/BATTLEFIELD"))
        val animation = HistorySceneAnimation(TableFixture.scene(initial))
        animation.retarget(target, 1f)
        val current = animation.frame(.8f).single { it.item.cardId == "a/HAND/0" }.bounds
        val corrected = target.copy(items = target.items.map { if (it.cardId == "a/HAND/0")
            it.copy(bounds = it.bounds.copy(x = it.bounds.x + 56f)) else it })
        assertTrue(animation.retarget(corrected, .8f))
        assertEquals(current, animation.frame(0f).single { it.item.cardId == "a/HAND/0" }.bounds)
        val half = animation.frame(.5f)
        val card = half.single { it.item.cardId == "a/HAND/0" }
        assertEquals((current.x + corrected.items.single { it.cardId == "a/HAND/0" }.bounds.x) / 2, card.bounds.x, .001f)
        val rendered = TablePresentation.clip(corrected.copy(items = half.map { it.item.copy(bounds = it.bounds) }), 412f, 820f)
        assertTrue(rendered.items.single { it.cardId == "a/HAND/0" }.visible.isNotEmpty())
    }

    @Test fun drawsStartAtLibraryAndReachEachHandSlotForBothPlayersAndOrientations() {
        for (landscape in listOf(false, true)) for (owner in listOf("a", "b")) for (count in listOf(1, 3)) {
            val initial = TableFixture.play(hand = 2)
            val ids = initial.board.containers.single { it.id == "$owner/LIBRARY" }.cards.take(count)
            val source = TableFixture.scene(initial, landscape)
            val target = TableFixture.scene(moved(initial, ids, "$owner/HAND"), landscape)
            val animation = HistorySceneAnimation(source)
            assertTrue(animation.retarget(target, 1f))
            val library = source.items.single { it.id == "$owner/LIBRARY" }
            for (id in ids) {
                val start = animation.frame(0f).single { it.item.cardId == id }
                assertEquals(library.bounds.x + 8f, start.bounds.x)
                assertEquals(library.bounds.y + 8f, start.bounds.y)
                assertEquals(1f, start.alpha)
                assertEquals(owner != "a", start.item.covered)
                val half = animation.frame(.5f).single { it.item.cardId == id }
                assertTrue(half.item.inTransit)
                assertNotEquals(start.bounds, half.bounds)
                val rendered = TablePresentation.clip(target.copy(items = animation.frame(.5f).map { it.item.copy(bounds = it.bounds) }),
                    if (landscape) 900f else 412f, if (landscape) 412f else 820f)
                assertTrue(rendered.items.single { it.cardId == id }.visible.isNotEmpty())
                val end = animation.frame(1f).single { it.item.cardId == id }
                assertEquals(target.items.single { it.cardId == id }.bounds, end.bounds)
                assertFalse(end.item.inTransit)
            }
        }
    }

    @Test fun realStackCardUsesSameAnimationIdentityAsItsHandDrawing() {
        val initial = TableFixture.play(hand = 1)
        val id = "a/HAND/0"
        val source = TableFixture.scene(initial)
        val target = TableFixture.scene(moved(initial, listOf(id), "stack"))
        val animation = HistorySceneAnimation(source)
        animation.retarget(target, 1f)
        val cards = animation.frame(0f).filter { it.item.cardId == id }
        assertEquals(1, cards.size)
        assertEquals(source.items.single { it.cardId == id }.bounds, cards.single().bounds)
        assertEquals(1f, cards.single().alpha)
    }
}
