package org.asyncttwiz.android

import org.asyncttwiz.protocol.*
import org.junit.Test
import kotlin.test.*

class StackRemovalFlightTest {
    private val source = "a/HAND/0"
    private fun move(state: PlayState, zone: PlayZone): PlayState {
        val destination = if (zone == PlayZone.STACK) "stack" else "a/$zone"
        return state.copy(board = state.board.copy(cards = state.board.cards.map {
            if (it.id == source) it.copy(containerId = destination, covered = false,
                placement = if (zone == PlayZone.BATTLEFIELD) PlayPlacement(BoardPosition(2, 1), "pile") else null) else it
        }, containers = state.board.containers.map {
            it.copy(cards = it.cards.filterNot { it == source } + if (it.id == destination) listOf(source) else emptyList())
        }))
    }
    private fun project(state: PlayState, ids: List<String> = listOf("effect")) = state.copy(board = state.board.copy(
        stackEffects = ids.map { StackEffect(it, source) }, containers = state.board.containers.map {
            if (it.zone == PlayZone.STACK) it.copy(cards = ids) else it }))
    private fun removeEffects(state: PlayState) = state.copy(board = state.board.copy(stackEffects = emptyList(),
        containers = state.board.containers.map { if (it.zone == PlayZone.STACK) it.copy(cards = emptyList()) else it }))

    @Test fun resolvingProjectionFliesFromItsStackSlotToEachSupportedDestinationForBothLayouts() {
        for (landscape in listOf(false, true)) for (zone in listOf(PlayZone.GRAVEYARD, PlayZone.BATTLEFIELD, PlayZone.EXILE)) {
            val initial = TableFixture.play(hand = 1)
            val before = TableFixture.scene(project(initial), landscape)
            val after = TableFixture.scene(move(initial, zone), landscape)
            val flight = stackRemovalFlights(before, after).single()
            assertEquals("effect", flight.entry.cardId)
            assertEquals(zone, flight.destination.zone)
            assertEquals(before.items.single { it.cardId == "effect" }.bounds, flight.entry.bounds)
            val destination = after.items.firstOrNull { it.cardId == source }
                ?: after.items.single { source in it.cards }
            assertEquals(destination.bounds, flight.destination.bounds)

            val animation = HistorySceneAnimation(before)
            animation.cardMoveSpeedDpPerMs = .5f
            assertTrue(animation.retarget(after, 1f))
            val start = animation.frame(0f).single { it.item.cardId == "effect" }
            assertEquals(flight.entry.bounds, start.bounds)
            assertEquals(1f, start.alpha)
            val middle = animation.frame(.25f).single { it.item.cardId == "effect" }
            val t = cardMoveProgress(start.bounds, destination.bounds, .25f, animation.durationMillis, .5f)
            assertEquals(start.bounds.center.x + (destination.bounds.center.x - start.bounds.center.x) * t,
                middle.bounds.center.x, .001f)
            // Fading has its own fixed clock, independent of the distance/easing of the flight.
            assertEquals(1f - (.25f * animation.durationMillis / CARD_FADE_DURATION_MILLIS).coerceIn(0f, 1f), middle.alpha, .001f)
            val early = animation.frame(.01f).single { it.item.cardId == "effect" }
            assertEquals(1f - .01f * animation.durationMillis / CARD_FADE_DURATION_MILLIS, early.alpha, .001f)
            assertTrue(middle.item.inTransit)
            val nearEnd = animation.frame(.9f).single { it.item.cardId == "effect" }
            assertTrue(nearEnd.alpha in 0f..1f && nearEnd.alpha < 1f)
            val end = animation.frame(1f).single { it.item.cardId == "effect" }
            assertEquals(destination.bounds, end.bounds)
            assertEquals(0f, end.alpha)
            assertFalse(end.item.inTransit)
            if (zone == PlayZone.BATTLEFIELD) {
                assertEquals(destination.bounds, animation.frame(0f).single { it.item.cardId == source }.bounds,
                    "Do not fly a duplicate real card from Hand")
            }
        }
    }

    @Test fun realSpellLeavingStackUsesItsSlotNotItsFormerHandOrPileIncludingBuriedSpells() {
        for (landscape in listOf(false, true)) for (buried in listOf(false, true))
            for (zone in listOf(PlayZone.GRAVEYARD, PlayZone.BATTLEFIELD, PlayZone.EXILE)) {
                val initial = TableFixture.play(hand = 1)
                val stacked = move(initial, PlayZone.STACK).let { state -> if (!buried) state else {
                    val effects = (1..3).map { StackEffect("effect-$it", "b/HAND/0") }
                    state.copy(board = state.board.copy(stackEffects = effects, containers = state.board.containers.map {
                        if (it.zone == PlayZone.STACK) it.copy(cards = effects.map { it.id } + source) else it }))
                } }
                val resolved = move(stacked, zone)
                val before = TableFixture.scene(stacked, landscape)
                val after = TableFixture.scene(resolved, landscape)
                val flight = stackRemovalFlights(before, after).single()
                assertEquals(source, flight.entry.cardId)
                assertFalse(flight.entry.projection)
                assertEquals(zone, flight.destination.zone)
                if (buried) assertTrue(before.items.none { it.cardId == source })
                val animation = HistorySceneAnimation(before)
                animation.retarget(after, 1f)
                assertEquals(flight.entry.bounds, animation.frame(0f).single { it.item.cardId == source }.bounds)
                val rendered = TablePresentation.clip(after.copy(items = animation.frame(0f).map {
                    it.item.copy(bounds = it.bounds) }), if (landscape) 900f else 412f, if (landscape) 412f else 820f)
                assertTrue(rendered.items.single { it.cardId == source }.visible.isNotEmpty(),
                    "The real spell must be visible at its Stack slot before entering the Battlefield viewport")
                assertEquals(flight.destination.bounds, animation.frame(1f).single { it.item.cardId == source }.bounds)
            }
    }

    @Test fun planExcludesPlainRemovalsRetainedEntriesUnsupportedZonesAndFrozenEffects() {
        val initial = TableFixture.play(hand = 1)
        val projected = project(initial)
        assertTrue(stackRemovalFlights(TableFixture.scene(projected), TableFixture.scene(initial)).isEmpty())
        for (zone in listOf(PlayZone.HAND, PlayZone.LIBRARY, PlayZone.STACK)) {
            val before = project(move(initial, PlayZone.BATTLEFIELD))
            assertTrue(stackRemovalFlights(TableFixture.scene(before), TableFixture.scene(move(initial, zone))).isEmpty())
        }
        assertTrue(stackRemovalFlights(TableFixture.scene(projected),
            TableFixture.scene(project(move(initial, PlayZone.GRAVEYARD)))).isEmpty())
        val frozen = projected.copy(board = projected.board.copy(stackEffects = projected.board.stackEffects.map {
            it.copy(frozen = initial.board.cards.single { it.id == source }) }))
        assertTrue(stackRemovalFlights(TableFixture.scene(frozen), TableFixture.scene(move(initial, PlayZone.GRAVEYARD))).isEmpty())
        // Existing non-move removals still fly home and fade, independently of this new plan.
        val animation = HistorySceneAnimation(TableFixture.scene(projected))
        animation.retarget(TableFixture.scene(removeEffects(projected)), 1f)
        assertEquals(TableFixture.scene(initial).items.single { it.cardId == source }.bounds,
            animation.frame(1f).single { it.item.cardId == "effect" }.bounds)
    }

    @Test fun liveAndPlaybackBatchesAllowMotionButJumpsAndCatchUpClearIt() {
        val changes = TableChanges()
        val mutation = GameplayMutation("node", "parent", "branch", "request", "a", null,
            PlayAction.SetLife("a", "19"), GameplayTransition())
        assertFalse(changes.animates("node"))
        changes.apply(TableChangeBatch(listOf(TableAppliedEvent(mutation))))
        assertTrue(changes.animates("node"))
        assertFalse(changes.animates("unseen catch-up"))
        changes.apply(TableChangeBatch(jump = true))
        assertFalse(changes.animates("node"))
        changes.apply(TableChangeBatch(listOf(TableAppliedEvent(mutation, reverse = true))))
        assertTrue(changes.animates("parent"))
        changes.clear()
        assertFalse(changes.animates("parent"))
    }
}
