package org.asyncttwiz.android

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
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

/** TRN-053 (I-D25): a player's emblems show as chips with their text by the life square. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w412dp-h892dp-port-mdpi")
class EmblemChipTest {
    @get:Rule val compose = createComposeRule()

    private fun emblem(n: Int, player: String, text: String) =
        EngineRecord("emblem:$n", EngineRecordKind.Emblem, n.toLong(), "{}", player, text)

    private val play = TableFixture.play(library = 0).let { initial ->
        initial.copy(board = initial.board.copy(engineRecords = listOf(
                emblem(2, "a", "Creatures you control get +1/+1."),
                emblem(1, "b", "Whenever a creature you control deals combat damage to a player, search your library for a blue or " +
                    "black creature card, put it onto the battlefield, then shuffle."),
                emblem(3, "a", "At the beginning of your end step, draw a card."),
                EngineRecord("effect:1", EngineRecordKind.Effect, 4, "{}"))))
    }

    @Test fun eachPlayersEmblemsOldestFirst() {
        assertEquals(listOf("Emblem: Creatures you control get +1/+1.", "Emblem: At the beginning of your end step, draw a card."),
            EmblemChips.texts(play.board, "a"))
        val scene = TablePresentation.scene(TableFixture.game, TableBoard.of(play), "a", 412f, 892f, false)
        assertEquals(1, scene.items.single { it.id == "life/b" }.emblems.size)
        assertEquals(2, scene.items.single { it.id == "life/a" }.emblems.size)
    }

    // TRN-053: in portrait the chips stack in the room right of the piles, clear of both hands; landscape keeps them by
    // the life square
    @Test fun portraitChipsSitRightOfThePilesClearOfTheHands() {
        val scene = TablePresentation.scene(TableFixture.game, TableBoard.of(play), "a", 412f, 892f, false)
        val hands = scene.items.filter { it.kind == TableItemKind.HAND }.map { it.bounds }
        val piles = scene.items.filter { it.zone == PlayZone.LIBRARY || it.zone == PlayZone.GRAVEYARD || it.zone == PlayZone.EXILE }
        for (player in listOf("a", "b")) {
            val life = scene.items.single { it.id == "life/$player" }
            val area = assertNotNull(life.emblemArea)
            assertTrue(hands.none { it.intersection(area) != null }, "$area")
            assertTrue(piles.filter { it.owner == player }.all { it.bounds.right < area.x })
            val chips = EmblemChips.stacked(area, player == "b", listOf(150f, 120f), listOf(2, 1))
            assertTrue(chips.all { it.x == area.x && it.y >= area.y && it.bottom <= area.bottom }, "$chips in $area")
            assertTrue(chips[0].bottom < chips[1].y)
        }
        val landscape = TablePresentation.scene(TableFixture.game, TableBoard.of(play), "a", 892f, 412f, true)
        assertNull(landscape.items.single { it.id == "life/a" }.emblemArea)
    }

    @Test fun emblemChipsPortrait() {
        val toolkit = TableToolkitController("a", { TableBoard.of(play) }, { true }, { _, _ -> true })
        compose.setContent { MaterialTheme {
            TableSurface(TableFixture.game, TableBoard.of(play), "a", false, Modifier.fillMaxSize(), connected = true,
                onIntent = toolkit::handle, toolkit = toolkit)
        } }
        compose.waitForIdle()
        compose.onRoot().captureShot("$SCREENSHOTS/emblem-chips-portrait.png",
            "Portrait table where you have two emblems and the opponent one long emblem: each emblem is a violet chip with " +
                "its text in the free room right of its player's piles, clear of both hands, a long text cut after two lines.")
    }
}
