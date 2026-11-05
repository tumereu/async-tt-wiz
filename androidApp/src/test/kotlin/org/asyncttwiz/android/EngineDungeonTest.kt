package org.asyncttwiz.android

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.input.Input
import arcanum.engine.state.ObjectId
import arcanum.engine.state.PlayerId
import org.asyncttwiz.automatum.EngineProjection
import org.asyncttwiz.protocol.game.GameDevice
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * TRN-069, LOB-117 (CR 309, 701.49): a game whose deck ventures, on the app's engine path: Alice's venture brings her dungeon into
 * the command zone, the board holds it as a card in no zone's list with the card data she brought, the table draws it after her
 * piles with its room in a chip, a tap does nothing and holding inspects it; the next venture asks her for a room and the board at
 * the earlier event still shows the earlier room. Invented cards and dungeons only.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w412dp-h892dp-port-mdpi")
class EngineDungeonTest {
    @get:Rule val compose = createComposeRule()
    private val f = EngineGameFixture(dungeons = true)
    private val alice = PlayerId(f.a)

    @After fun forget() = AutomatumBoards.reset()

    private fun map(game: EngineGame): ObjectId = game.device.model.engine.let { s ->
        s.player(alice).hand.first { s.obj(it)?.definition?.faces?.first()?.name == "Torch Map" }
    }

    /** Alice kept while Bob is away and ventured once: her Torch Map resolved and Crypt Door's ability too. */
    private fun ventured(): EngineGame = EngineGame(f.game.id, f.a, f.base, f.snapshot(otherKept = true)).apply {
        tick(1_000); keep(emptyList()); tick(2_000)
        assertIs<GameDevice.Acted.Sent>(send(listOf(Input.Cast(alice, map(this)))))
        tick(3_000)
    }

    private fun toolkit(game: EngineGame): TableToolkitController {
        val routes = EngineTableRoutes(f.a, { game }, {}, { game.play() }).apply { opponentName = "Bob" }
        return TableToolkitController(f.a, { game.play() }, { routes.canAct() }, { action, _ -> routes.local(listOf(action)) },
            playBatch = { actions, _ -> routes.local(actions) }, engine = routes)
    }

    @Test fun theDungeonIsOnTheTable() {
        val game = ventured()
        val play = game.play()
        val shown = assertNotNull(EngineProjection.dungeon(assertNotNull(play.engine).state, f.a), "Alice's dungeon is in her command zone")
        assertEquals("Lantern Crypt", shown.name)
        assertEquals("Crypt Door", shown.room)
        assertEquals("21", play.board.players.single { it.accountId == f.a }.life)
        // A card of the board in no zone's list, with the card data Alice's device brought; not an emblem
        val card = play.board.cards.single { it.id == shown.cardId }
        assertTrue(play.board.containers.none { shown.cardId in it.cards })
        assertEquals("Lantern Crypt", play.definitions.single { it.digest == card.definition }.definition.name)
        assertTrue(EmblemChips.texts(play.board, f.a).isEmpty())
        // The table: a small card after her piles, the room in the first chip
        val scene = TablePresentation.scene(f.game, play, f.a, 412f, 892f, false)
        val item = scene.items.single { it.kind == TableItemKind.DUNGEON }
        assertEquals(shown.cardId, item.cardId)
        val exile = scene.items.single { it.zone == org.asyncttwiz.protocol.PlayZone.EXILE && it.owner == f.a && it.kind == TableItemKind.PILE }
        assertTrue(item.bounds.x > exile.bounds.right, "after the Exile pile")
        assertEquals("Room: Crypt Door", scene.items.single { it.id == "life/${f.a}" }.emblems.first())
        assertNull(scene.items.firstOrNull { it.kind == TableItemKind.DUNGEON && it.owner == f.b }, "Bob has no dungeon")
        // A tap does nothing; holding inspects it
        val toolkit = toolkit(game)
        toolkit.open(TableIntent.Actions(item.id, null), item)
        assertNull(toolkit.peek); assertNull(toolkit.capture); assertNull(toolkit.panel)
        toolkit.peek(shown.cardId)
        assertEquals(shown.cardId, toolkit.peek?.cardId)
        render(game, toolkit(game))
        compose.onRoot().captureShot("$SCREENSHOTS/engine/dungeon-on-table.png",
            "Alice cast Torch Map and ventured into the dungeon: her Lantern Crypt sits as a small card after her Library, Graveyard and Exile, and the violet chip beside it says the venture marker is in Room: Crypt Door.")
    }

    // TRN-069, TRN-035: in landscape the dungeon fits in the sidebar, in the free row under the Library, unscrolled
    @Test @Config(qualifiers = "w892dp-h412dp-land-mdpi") fun theDungeonFitsTheLandscapeSidebar() {
        val game = ventured()
        val scene = TablePresentation.scene(f.game, game.play(), f.a, 892f, 412f, true)
        val item = scene.items.single { it.kind == TableItemKind.DUNGEON }
        val dock = scene.viewports.single { it.id == item.viewport }.bounds
        assertTrue(item.bounds.x >= dock.x && item.bounds.right <= dock.right && item.bounds.y >= dock.y && item.bounds.bottom <= dock.bottom,
            "${item.bounds} inside $dock")
        render(game, toolkit(game), landscape = true)
        compose.onRoot().captureShot("$SCREENSHOTS/engine/dungeon-on-table-landscape.png",
            "In landscape, after Alice ventured into the dungeon: her Lantern Crypt sits as a small card in the free row of the sidebar under her Library, fully visible, with the Room: Crypt Door chip above her life square.")
    }

    // CR 701.49b, 309.5a: the next venture asks Alice which room to follow; the board at the earlier event shows the earlier room
    @Test fun theNextVentureChoosesARoom() {
        val game = ventured()
        val before = game.device.session.pending.lastOrNull()?.id ?: game.device.session.localHead
        assertIs<GameDevice.Acted.Sent>(game.send(listOf(Input.Cast(alice, map(game)))))
        game.tick(4_000)
        val choice = assertIs<Decision.ChooseOption>(game.device.model.engine.pending?.decision)
        assertEquals(listOf("Bone Hall", "Dark Well"), choice.options)
        assertIs<GameDevice.Acted.Sent>(game.act(Input.Answer(choice.id, Answer.Option("Dark Well"))))
        game.tick(5_000)
        assertEquals("Dark Well", EngineProjection.dungeon(assertNotNull(game.play().engine).state, f.a)?.room)
        assertEquals("23", game.play().board.players.single { it.accountId == f.a }.life)
        assertEquals("Crypt Door", EngineProjection.dungeon(assertNotNull(assertNotNull(game.reviewPlay(before)).engine).state, f.a)?.room)
    }

    private fun render(game: EngineGame, toolkit: TableToolkitController, landscape: Boolean = false) {
        val routes = EngineTableRoutes(f.a, { game }, {}, { game.play() }).apply { opponentName = "Bob" }
        compose.setContent {
            MaterialTheme {
                NoRipples {
                    CompositionLocalProvider(LocalEngineRoutes provides routes) {
                        Box(Modifier.fillMaxSize()) {
                            TableSurface(f.game, game.play(), f.a, landscape, Modifier.fillMaxSize(), connected = true,
                                onIntent = toolkit::handle, toolkit = toolkit,
                                runtime = TableRuntimeHooks(navigation = {}, chat = {}, undo = { routes.undo() },
                                    undoHold = { routes.holdUndo() }, controlsConnected = true, liveView = true,
                                    waitingLine = routes.waitingLine(game.play())))
                        }
                    }
                }
            }
        }
        compose.waitForIdle()
    }
}
