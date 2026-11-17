@file:OptIn(arcanum.engine.state.ArcanumTestSetup::class)

package org.asyncttwiz.android

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import arcanum.engine.Engine
import arcanum.engine.state.PlayerId
import arcanum.engine.state.setup
import org.asyncttwiz.automatum.EngineProjection
import org.asyncttwiz.automatum.StepFlow
import org.asyncttwiz.protocol.PlayState
import org.asyncttwiz.protocol.PlayZone
import org.asyncttwiz.protocol.game.EngineView
import org.asyncttwiz.protocol.game.GameModel
import org.asyncttwiz.protocol.game.LineRandom
import org.asyncttwiz.protocol.game.TableState
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** TBL-138, TBL-197: a card printed under another name (LOB-150) shows that name on the table and in Inspect, which labels its picture
 * with the card's Oracle name; Automatum's card stays the Oracle one. Your Meadow Sentinel is printed as "Dawnbreak Warden". */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w412dp-h892dp-port-mdpi")
class PrintedNameUiTest {
    @get:Rule val compose = createComposeRule()
    @After fun forget() = AutomatumBoards.reset()

    private val f = TableStatesFixture
    private val sentinel = f.source(f.a, "1/3")
    private val names = mapOf(f.b to "Bob", f.a to "Alice")

    /** The fixture's game with the Meadow Sentinel's card data printed as [PRINTED] (its digest, Automatum's card, unchanged),
     * with a picture of its own whose name bar reads [PRINTED], as such a printing's picture does. */
    private val play: PlayState = f.initial.copy(definitions = f.initial.definitions.map { submitted ->
        if (submitted.digest != sentinel.definition) submitted
        else submitted.copy(definition = submitted.definition.copy(faces = submitted.definition.faces.map {
            it.copy(printedName = PRINTED, scryfallId = placeholderPrinting(PRINTED))
        }))
    })

    /** The table [f.a] draws of [play], as [EngineFixture] makes it. */
    private fun board(): TableBoard {
        val state = TestEngineState.of(play.copy(board = play.board.copy(engineRecords = emptyList())))
        StepFlow.settledPriority(state)?.let { state.setup.priority = it }
        val model = GameModel(state, TableState(secret = LineRandom.secret(ByteArray(32) { 7 })))
        val me = PlayerId(f.a)
        val visible = model.visible(me)
        val engine = EngineView(model.engine, model.table, me, visible)
        val values = EngineBoardValues.make(EngineProjection.Board(play.board, play.definitions), play.gameId, engine.state, f.a,
            Engine.view(engine.state), visible, EngineWorker.Direct)
        return TableBoard(play.gameId, play.id, play.root.id, play.board, play.definitions, engine, values)
    }

    // TBL-138: the table names the card by its printed name, and so does what it reads aloud
    @Test fun theTableShowsThePrintedName() {
        val table = board()
        val definition = play.definitions.single { it.digest == sentinel.definition }.definition
        assertEquals("Meadow Sentinel", definition.name)
        assertEquals(PRINTED, definition.shownName)
        val item = TablePresentation.scene(f.game, table, f.a, 412f, 892f, false, computed = table.values!!.computed).items
            .single { it.sourceId == sentinel.id && it.zone == PlayZone.BATTLEFIELD && it.viewport != "combat" }
        assertTrue(item.label.startsWith("$PRINTED, owner "), item.label)
        val toolkit = TableToolkitController(f.a, { table }, { true }, { _, _ -> true })
        compose.setContent { MaterialTheme {
            EngineFixtureTable(play, f.a) {
                TableSurface(f.game, table, f.a, false, Modifier.fillMaxSize(), connected = true, onIntent = toolkit::handle, toolkit = toolkit)
            }
        } }
        compose.waitForIdle()
        compose.onRoot().captureShot("$SCREENSHOTS/printed-name/table.png",
            "Portrait table where your Meadow Sentinel is a printing under another name: its card on your battlefield is titled " +
                "\"Dawnbreak Warden\", the name its printing gives it, while Automatum plays it as the Meadow Sentinel.")
    }

    // TBL-138, TBL-197, TBL-104: Inspect shows the printed name on the picture and labels it with the Oracle name at its bottom
    @Test fun inspectLabelsTheOracleName() {
        val table = board()
        val groups = gameInspectGroups(table, f.a, ManualInspection(f.game.id, play.id, sentinel.id, listOf(sentinel.faceId)), sentinel, names)
        val face = groups.first().tiles.first() as InspectTile.Face
        assertEquals(PRINTED, face.name)
        assertEquals("Meadow Sentinel", face.oracleName)
        compose.setContent { MaterialTheme { NoRipples { InspectContent(groups, ScrollState(0)) } } }
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Card image: $PRINTED").assertExists()
        compose.onNodeWithTag(INSPECT_ORACLE_NAME_TAG, useUnmergedTree = true).assertExists()
        compose.onNodeWithText("Oracle name: Meadow Sentinel", useUnmergedTree = true).assertExists()
        captureShot("$SCREENSHOTS/printed-name/inspect.png",
            "Holding your Meadow Sentinel printed as \"Dawnbreak Warden\": Inspect shows the card under its printed name, with a " +
                "dark label at the bottom of its picture reading \"Oracle name: Meadow Sentinel\".")
    }

    private companion object {
        const val PRINTED = "Dawnbreak Warden"
    }
}
