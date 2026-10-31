package org.asyncttwiz.android

import org.asyncttwiz.protocol.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.*

/** Rules v29: up to four numbered Temp piles in the dock; counters take their own row only when needed. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w412dp-h892dp-port-mdpi")
class TempPilesLayoutTest {
    private val playId = "00000000-0000-4000-8000-000000000123"
    private val game = LiveTableFixture.game.copy(playId = playId)

    /** [piles] turned on for both players; [counters] set for a. */
    private fun play(piles: List<Int>, counters: Map<CounterColor, String> = emptyMap()): PlayState {
        val base = LiveTableFixture.play().copy(id = playId)
        val containers = listOf("a", "b").flatMap { owner -> piles.filter { it != 1 }.map { n ->
            PlayContainer(TempPiles.containerId(playId, owner, n), PlayZone.LIBRARY, owner, temp = true, tempNumber = n) } }
        return base.copy(board = base.board.copy(containers = base.board.containers + containers,
            players = base.board.players.map { if (it.accountId == "a") it.copy(counters = counters) else it }))
    }

    private fun scene(play: PlayState, landscape: Boolean = false, size: Float = 1f) = TablePresentation.scene(game, play, "a",
        if (landscape) 892f else 412f, if (landscape) 412f else 892f, landscape, appearance = CardAppearance(cardSize = size))

    private fun TableScene.temps(owner: String) = items.filter { it.temp && it.owner == owner }.sortedBy { it.tempNumber }
    private fun TableScene.counterItems(owner: String) = items.filter { it.kind == TableItemKind.COUNTER && it.owner == owner }

    @Test fun visiblePilesFollowExileInNumberOrderWithPlaceholdersForEmptyOnes() {
        val scene = scene(play(listOf(1, 3, 4)))
        for (owner in listOf("a", "b")) {
            val temps = scene.temps(owner)
            assertEquals(listOf(1, 3, 4), temps.map { it.tempNumber })
            assertEquals(listOf(1, 3, 4).map { TempPiles.containerId(playId, owner, it) }, temps.map { it.id })
            assertTrue(temps.all { it.cards.isEmpty() && it.zone == PlayZone.LIBRARY })
            val exile = scene.items.single { it.id == "$owner/EXILE" }
            assertTrue(temps.first().bounds.x > exile.bounds.right)
            assertEquals(temps.sortedBy { it.bounds.x }, temps)
        }
    }

    // ZON-045
    @Test fun portraitCountersShrinkThenTakeTheirOwnRowOnlyWithSeveralPilesAndNoRoom() {
        val one = scene(play(listOf(1)))
        val three = scene(play(listOf(1, 2, 3)))
        val four = scene(play(listOf(1, 2, 3, 4)))
        val dockRow = one.items.single { it.id == "a/LIBRARY" }.bounds
        for (s in listOf(one, three)) {
            // Same row as the piles, never panning: the grid is narrower but its content fits its port.
            val port = s.viewports.single { it.id == "counters/a" }
            assertEquals(port.bounds.width, port.contentWidth, .01f)
            assertTrue(s.counterItems("a").all { it.bounds.y < dockRow.bottom + 12f })
            assertTrue(s.temps("a").last().bounds.right <= port.bounds.x)
        }
        assertTrue(three.viewports.single { it.id == "counters/a" }.bounds.width <
            one.viewports.single { it.id == "counters/a" }.bounds.width, "three piles shrink the counter grid")
        // Four piles leave no room: own counters go under the pile row, the opponent's above it.
        val ownLibrary = four.items.single { it.id == "a/LIBRARY" }.bounds
        val oppLibrary = four.items.single { it.id == "b/LIBRARY" }.bounds
        val own = four.counterItems("a"); val opp = four.counterItems("b")
        assertEquals(6, own.size); assertEquals(6, opp.size)
        assertTrue(own.all { it.bounds.y >= ownLibrary.bottom }, "own counters below the pile row")
        assertTrue(opp.all { it.bounds.bottom <= oppLibrary.y }, "opponent counters above the pile row")
        assertEquals(1, own.map { it.bounds.y }.distinct().size, "one row of six")
        for (owner in listOf("a", "b")) {
            val port = four.viewports.single { it.id == "counters/$owner" }
            assertEquals(port.bounds.width, port.contentWidth, .01f)
            assertTrue(four.viewports.single { it.id == "dock/$owner" }.maxScroll == 0f, "the pile row does not pan")
            assertTrue(four.temps(owner).all { it.visible.isNotEmpty() })
        }
        // The extra rows come out of the battlefield, not off screen.
        assertTrue(four.world!!.viewport.height < one.world!!.viewport.height)
        assertTrue(own.all { it.bounds.bottom <= 892f })
    }

    // ZON-045
    @Test fun largerPilesNeedTheirOwnCounterRowSooner() {
        val small = scene(play(listOf(1, 2)), size = 1f)
        val large = scene(play(listOf(1, 2)), size = 1.5f)
        val smallLibrary = small.items.single { it.id == "a/LIBRARY" }.bounds
        val largeLibrary = large.items.single { it.id == "a/LIBRARY" }.bounds
        assertTrue(small.counterItems("a").all { it.bounds.y < smallLibrary.bottom })
        assertTrue(large.counterItems("a").all { it.bounds.y >= largeLibrary.bottom })
        // A single pile never moves the counters to their own row.
        val single = scene(play(listOf(1)), size = 2f)
        assertTrue(single.counterItems("a").all { it.bounds.y < single.items.single { it.id == "a/LIBRARY" }.bounds.bottom })
    }

    // ZON-045
    @Test fun landscapePilesShareTheCounterRowUntilCountersNeedTheirOwn() {
        val quiet = scene(play(listOf(1, 2, 3)), landscape = true)
        val exile = quiet.items.single { it.id == "a/EXILE" }.bounds
        val temps = quiet.temps("a")
        assertEquals(listOf(1, 2, 3), temps.map { it.tempNumber })
        // Right-aligned under Exile, in number order, inside the sidebar.
        assertTrue(temps.last().bounds.center.x in exile.x..exile.right)
        assertEquals(temps.sortedBy { it.bounds.x }, temps)
        assertTrue(temps.all { it.bounds.y >= exile.bottom && it.bounds.x >= 0f })
        // A far-right counter value would have to pan beside two piles, so the counters get their own row.
        val busy = scene(play(listOf(1, 2), mapOf(CounterColor.GRAY to "3")), landscape = true)
        val busyTemps = busy.temps("a")
        val gray = busy.counterItems("a").single()
        assertTrue(gray.bounds.y >= busyTemps.first().bounds.bottom, "own counter row below the Temp row")
        val port = busy.viewports.single { it.id == "counters/a" }
        assertTrue(gray.bounds.right <= port.bounds.right + .01f, "the value is visible without panning")
        // With one pile the old layout stays.
        val single = scene(play(listOf(1), mapOf(CounterColor.GRAY to "3")), landscape = true)
        assertTrue(single.counterItems("a").single().bounds.y < single.temps("a").single().bounds.bottom)
    }

    @Test fun ownerRedirectKeepsThePileNumber() {
        val play = play(listOf(1, 3))
        val scene = scene(play)
        val redirected = scene.ownerMoveDestination("b/BATTLEFIELD/0",
            TableDestination.Container(TempPiles.containerId(playId, "a", 3), PlayZone.LIBRARY))
        assertEquals(TempPiles.containerId(playId, "b", 3), (redirected as TableDestination.Container).id)
        val board = ownerMoveDestination(play.board, "b/BATTLEFIELD/0",
            TableDestination.Container(TempPiles.containerId(playId, "a", 3), PlayZone.LIBRARY), playId)
        assertEquals(TempPiles.containerId(playId, "b", 3), (board as TableDestination.Container).id)
    }

    // SYS-040, ZON-042
    @Test fun cardMenuOffersAllFourPilesDisabledOnlyWhereNothingCanMove() {
        val base = play(listOf(1, 2))
        val two = TempPiles.containerId(playId, "a", 2)
        val play = base.copy(board = base.board.copy(
            cards = base.board.cards.map { if (it.id == "a/GRAVEYARD/0") it.copy(containerId = two) else it },
            containers = base.board.containers.map { when (it.id) {
                "a/GRAVEYARD" -> it.copy(cards = it.cards - "a/GRAVEYARD/0")
                two -> it.copy(cards = listOf("a/GRAVEYARD/0"))
                else -> it
            } }))
        val toolkit = TableToolkitController("a", { play }, { true }, { _, _ -> true })
        val tools = tempPileTools(toolkit, play, listOf("a/GRAVEYARD/0"), enabled = true)
        assertEquals(listOf("Temp 1", "Temp 2", "Temp 3", "Temp 4"), tools.map { it.name })
        assertEquals(listOf(true, false, true, true), tools.map { it.enabled })
        assertTrue(tempPileTools(toolkit, play, listOf("a/GRAVEYARD/0"), enabled = false).none { it.enabled })
        // A selection with one movable card keeps the pile enabled.
        assertTrue(tempPileTools(toolkit, play, listOf("a/GRAVEYARD/0", "a/GRAVEYARD/1"), enabled = true)[1].enabled)
    }
}
