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

/** TRN-053 (I-D25): a player's emblems show as chips with their text by the life square. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w412dp-h892dp-port-mdpi")
class EmblemChipTest {
    @get:Rule val compose = createComposeRule()

    private fun emblem(n: Int, player: String, text: String) =
        EngineRecord("emblem:$n", EngineRecordKind.Emblem, n.toLong(), "{}", player, text)

    private val play = TableFixture.play(library = 0).let { initial ->
        initial.copy(board = initial.board.copy(players = initial.board.players.map { if (it.accountId == "a") it.copy(status = "OK?") else it },
            engineRecords = listOf(
                emblem(2, "a", "Creatures you control get +1/+1."),
                emblem(1, "b", "Whenever a creature you control deals combat damage to a player, search your library for a blue or " +
                    "black creature card, put it onto the battlefield, then shuffle."),
                emblem(3, "a", "At the beginning of your end step, draw a card."),
                EngineRecord("effect:1", EngineRecordKind.Effect, 4, "{}"))))
    }

    @Test fun eachPlayersEmblemsOldestFirst() {
        assertEquals(listOf("Emblem: Creatures you control get +1/+1.", "Emblem: At the beginning of your end step, draw a card."),
            EmblemChips.texts(play.board, "a"))
        val scene = TablePresentation.scene(TableFixture.game, play, "a", 412f, 892f, false)
        assertEquals(1, scene.items.single { it.id == "life/b" }.emblems.size)
        assertEquals(2, scene.items.single { it.id == "life/a" }.emblems.size)
    }

    @Test fun emblemChipsPortrait() {
        val toolkit = TableToolkitController("a", { play }, { true }, { _, _ -> true })
        compose.setContent { MaterialTheme {
            TableSurface(TableFixture.game, play, "a", false, Modifier.fillMaxSize(), connected = true,
                onIntent = toolkit::handle, toolkit = toolkit)
        } }
        compose.waitForIdle()
        compose.onRoot().captureShot("$SCREENSHOTS/emblem-chips-portrait.png",
            "Portrait table where you have the status \"OK?\" and two emblems and the opponent one long emblem: each emblem is " +
                "a violet chip with its text by the life square, yours above your status bubble, the opponent's below their " +
                "square, a long text cut after two lines.")
    }
}
