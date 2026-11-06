package org.asyncttwiz.android

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.filter
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import org.asyncttwiz.protocol.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** TRN-053 (I-D25; owner gallery review 2026-11-06): a player's emblems show as round chips by the life square; holding
 * one inspects all that player's emblems. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w412dp-h892dp-port-mdpi")
class EmblemChipTest {
    @get:Rule val compose = createComposeRule()

    private fun emblem(n: Int, player: String, text: String) =
        EngineRecord("emblem:$n", EngineRecordKind.Emblem, n.toLong(), "{}", player, text)

    private val long = "Whenever a creature you control deals combat damage to a player, search your library for a blue or " +
        "black creature card, put it onto the battlefield, then shuffle."

    /** You (a) have five emblems, the opponent (b) one long one. */
    private val play = TableFixture.play(library = 0).let { initial ->
        initial.copy(board = initial.board.copy(engineRecords = listOf(
                emblem(2, "a", "Creatures you control get +1/+1."),
                emblem(1, "b", long),
                emblem(3, "a", "At the beginning of your end step, draw a card."),
                emblem(5, "a", "Whenever you gain life, target opponent loses that much life."),
                emblem(6, "a", "Spells you cast cost {1} less to cast."),
                emblem(7, "a", "Your opponents can't gain life."),
                EngineRecord("effect:1", EngineRecordKind.Effect, 4, "{}"))))
    }

    // TRN-053: each player's emblems, oldest first; at most three chips and a "+N" chip for the rest
    @Test fun eachPlayersEmblemsOldestFirst() {
        assertEquals(listOf("Creatures you control get +1/+1.", "At the beginning of your end step, draw a card.",
            "Whenever you gain life, target opponent loses that much life.", "Spells you cast cost {1} less to cast.",
            "Your opponents can't gain life."), EmblemChips.texts(play.board, "a"))
        val scene = TablePresentation.scene(TableFixture.game, tableBoard(play), "a", 412f, 892f, false)
        val opponent = scene.items.single { it.id == "life/b" }
        val own = scene.items.single { it.id == "life/a" }
        assertEquals(listOf(long), opponent.emblemTexts)
        assertEquals(1, opponent.emblemChips.size)
        assertEquals(5, own.emblemTexts.size)
        assertEquals(EmblemChips.SHOWN + 1, own.emblemChips.size, "three chips and +2")
        assertTrue(own.emblems.isEmpty() && opponent.emblems.isEmpty(), "no text chips without a dungeon room")
    }

    // TRN-053: in portrait the round chips sit in a row at the right of the room right of the piles, clear of both hands;
    // landscape keeps them by the life square (under the opponent's, above your own)
    @Test fun chipsSitInARowByTheLifeSquareClearOfTheHands() {
        val scene = TablePresentation.scene(TableFixture.game, tableBoard(play), "a", 412f, 892f, false)
        val hands = scene.items.filter { it.kind == TableItemKind.HAND }.map { it.bounds }
        val piles = scene.items.filter { it.zone == PlayZone.LIBRARY || it.zone == PlayZone.GRAVEYARD || it.zone == PlayZone.EXILE }
        for (player in listOf("a", "b")) {
            val life = scene.items.single { it.id == "life/$player" }
            val area = assertNotNull(life.emblemArea)
            for (chip in life.emblemChips) {
                assertTrue(hands.none { it.intersection(chip) != null }, "$chip")
                assertTrue(piles.filter { it.owner == player }.all { it.bounds.right < chip.x })
                assertEquals(EmblemChips.SIZE, chip.width)
                assertTrue(chip.y >= area.y && chip.bottom <= area.bottom && chip.x >= area.right)
            }
            assertTrue(life.emblemChips.zipWithNext().all { (a, b) -> a.right < b.x && a.y == b.y }, "one row")
            val stacked = EmblemChips.stacked(area, player == "b", listOf(80f), listOf(1))
            assertTrue(stacked.all { it.x == area.x && it.right <= life.emblemChips.first().x }, "text chips left of the row")
        }
        val landscape = TablePresentation.scene(TableFixture.game, tableBoard(play), "a", 892f, 412f, true)
        val own = landscape.items.single { it.id == "life/a" }
        val opponent = landscape.items.single { it.id == "life/b" }
        assertNull(own.emblemArea)
        assertTrue(own.emblemChips.all { it.bottom < own.bounds.y && it.x >= own.bounds.x }, "above your own square")
        assertTrue(opponent.emblemChips.all { it.y > opponent.bounds.bottom && it.x >= opponent.bounds.x }, "under the opponent's")
        // Clear of the Stack between them: as many chips as fit, at least 20 dp, the last one "+N"
        val stack = landscape.items.single { it.zone == PlayZone.STACK && it.kind == TableItemKind.PILE }
        assertTrue(own.emblemChips.all { it.right < stack.bounds.x - 6f && it.width >= EmblemChips.MIN_SIZE }, "${own.emblemChips} ${stack.bounds}")
        assertTrue(own.emblemChips.size >= 2)
    }

    private fun render(landscape: Boolean = false) {
        val toolkit = TableToolkitController("a", { engineTableBoard(play, "a") }, { true }, { _, _ -> true })
        compose.setContent { MaterialTheme {
            EngineFixtureTable(play, "a") {
                TableSurface(TableFixture.game, engineTableBoard(play, "a"), "a", landscape, Modifier.fillMaxSize(), connected = true,
                    onIntent = toolkit::handle, toolkit = toolkit)
            }
        } }
        compose.waitForIdle()
    }

    @Test fun emblemChipsPortrait() {
        render()
        assertEquals(5, compose.onAllNodesWithTag("emblem-chip").fetchSemanticsNodes().size)
        compose.onNodeWithContentDescription("2 more emblems").assertExists()
        compose.onRoot().captureShot("$SCREENSHOTS/emblem-chips-portrait.png",
            "Portrait table where you have five emblems and the opponent one: each emblem is a small round violet chip " +
                "(an emblem icon, as these invented emblems have no art) in a row right of its player's piles, your first three " +
                "and a +2 chip, clear of both hands.")
    }

    @Config(qualifiers = "w892dp-h412dp-land-mdpi")
    @Test fun emblemChipsLandscape() {
        render(landscape = true)
        compose.onRoot().captureShot("$SCREENSHOTS/emblem-chips-landscape.png",
            "Landscape table where you have five emblems and the opponent one: the round emblem chips sit in a row by each " +
                "life square, under the opponent's and above your own, as many as fit before the Stack, the last a +N chip.")
    }

    // TRN-053: holding any emblem chip inspects all that player's emblems with their full text; the release closes it
    @Test fun holdingAnEmblemInspectsThemAll() {
        render()
        compose.mainClock.autoAdvance = false
        // Your first emblem's chip (the chips are the only nodes with this tag; Inspect's tiles have none)
        val chip = compose.onAllNodesWithTag("emblem-chip").filter(hasContentDescription("Emblem: Creatures you control get +1/+1.")).onFirst()
        chip.performTouchInput { down(center) }
        compose.mainClock.advanceTimeBy(700)
        compose.waitForIdle()
        compose.mainClock.advanceTimeByFrame()
        compose.mainClock.advanceTimeByFrame()
        assertTrue(compose.onAllNodesWithTag("card-inspection").fetchSemanticsNodes().isNotEmpty(), "Inspect open")
        // Each emblem's tile in Inspect, and the chip of each of the first three
        EmblemChips.texts(play.board, "a").forEachIndexed { n, text ->
            assertEquals(if (n < EmblemChips.SHOWN) 2 else 1, compose.onAllNodesWithContentDescription("Emblem: $text").fetchSemanticsNodes().size, text)
        }
        captureShot("$SCREENSHOTS/emblem-inspect.png",
            "Holding your first emblem chip: Inspect shows all five of your emblems over the dimmed screen, one under another, " +
                "each a dark panel with the emblem icon (no art for these invented emblems), Emblem and its full text.")
        chip.performTouchInput { up() }
        compose.mainClock.advanceTimeBy(100)
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        assertTrue(compose.onAllNodesWithTag("card-inspection").fetchSemanticsNodes().isEmpty(), "closed on release")
    }
}
