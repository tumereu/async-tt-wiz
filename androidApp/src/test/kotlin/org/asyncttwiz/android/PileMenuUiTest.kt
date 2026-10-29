package org.asyncttwiz.android

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import org.asyncttwiz.protocol.*
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.*

/** Graveyard/Exile pile menus, the unified panel card menu acting on the panel's selection, and panel persistence. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w412dp-h892dp-port-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PileMenuUiTest {
    @get:Rule val compose = createComposeRule()
    private val f = BasicTableFixture()
    private lateinit var ui: TableToolkitController
    @After fun close() { f.lobby.close(); f.worker.drain() }

    /** Cards 40-42 (own) in the actor's [zone]; card 43 belongs to the opponent but lies there too. */
    private fun setup(zone: PlayZone = PlayZone.GRAVEYARD, mixed: Boolean = true, viewer: String = f.actor) {
        val pile = f.container(zone)
        val moved = if (mixed) (40..43) else (40..42)
        val root = f.root.copy(board = f.root.board.copy(
            cards = f.root.board.cards.map { card ->
                val n = (40..43).single { f.id(it) == card.id }
                card.copy(containerId = if (n in moved) pile else card.containerId, covered = n !in moved,
                    owner = if (n == 43) f.other else card.owner)
            },
            containers = f.root.board.containers.map { c -> when (c.id) {
                pile -> c.copy(cards = moved.map(f::id))
                f.container(PlayZone.LIBRARY) -> c.copy(cards = (40..43).filterNot { it in moved }.map(f::id))
                else -> c
            } }))
        f.connect(root)
        ui = TableToolkitController(viewer, { f.lobby.state.play }, { true }, f.lobby::gameplay, playBatch = f.lobby::gameplayBatch)
    }
    private fun mount(viewer: String = f.actor) {
        compose.setContent { MaterialTheme {
            f.lobby.state.play?.let { play -> TableSurface(f.game, play, viewer, false, connected = true, toolkit = ui, onIntent = ui::handle) }
        } }
        compose.waitForIdle()
    }
    private fun scene(viewer: String = f.actor) = TablePresentation.scene(f.game, f.play, viewer, 412f, 892f, false, ui.layoutState.value)
    private fun pileLabel(container: String) = scene().items.single { it.id == container }.label
    private fun click(name: String) {
        // The popup's input guard reads the system clock, which Robolectric advances apart from the compose clock.
        compose.mainClock.advanceTimeBy(1000)
        compose.onNodeWithContentDescription(name).performClick()
        compose.waitForIdle()
    }
    private fun newCommands(before: Int) = f.commands().drop(before)

    @Test fun pileTapOpensItsMenuAndDoubleTapOpensTheSearchPanelForEitherPlayersPiles() {
        setup()
        mount()
        for (pile in listOf(f.container(PlayZone.GRAVEYARD), f.container(PlayZone.EXILE), f.container(PlayZone.GRAVEYARD, f.other))) {
            compose.runOnIdle { ui.closeLocalPile() }
            compose.mainClock.advanceTimeBy(600)
            compose.onNodeWithContentDescription(pileLabel(pile)).performTouchInput { click() }
            compose.mainClock.advanceTimeBy(500)
            compose.runOnIdle {
                assertEquals("pile/$pile", ui.panel)
                assertNull(ui.layoutState.value.localPileId)
            }
            compose.onNodeWithContentDescription("Search").assertExists()
            compose.mainClock.advanceTimeBy(600)
            compose.onNodeWithContentDescription(pileLabel(pile)).performTouchInput { doubleClick() }
            compose.mainClock.advanceTimeBy(500)
            compose.runOnIdle {
                // Empty piles have no panel; the double-tap still never opens the menu again.
                val expected = pile.takeIf { id -> f.play.board.containers.single { it.id == id }.cards.isNotEmpty() }
                assertEquals(expected, ui.layoutState.value.localPileId)
                assertNull(ui.panel)
            }
        }
        assertTrue(f.commands().isEmpty())
    }

    @Test fun pileMenuShowsEveryButtonAlwaysNeverAllToHandAndGreysThemWhenEmpty() {
        setup()
        mount()
        val all = listOf("Search", "All to library top", "All to library bottom", "Shuffle all into library", "All to exile", "All to Temp 1")
        compose.runOnIdle { ui.openPileMenu(f.container(PlayZone.GRAVEYARD)) }
        compose.mainClock.advanceTimeBy(350)
        for (name in all) compose.onNodeWithContentDescription(name).assertIsEnabled()
        compose.onNodeWithContentDescription("All to hand").assertDoesNotExist()
        compose.onNodeWithContentDescription("All to graveyard").assertDoesNotExist()
        compose.runOnIdle { ui.openPileMenu(f.container(PlayZone.EXILE)) }
        compose.mainClock.advanceTimeBy(350)
        for (name in all.map { if (it == "All to exile") "All to graveyard" else it })
            compose.onNodeWithContentDescription(name).assertIsNotEnabled()
        compose.onNodeWithContentDescription("All to hand").assertDoesNotExist()
    }

    @Test fun allToExileIsOneInvocationIntoEachOwnersExile() {
        setup()
        mount()
        compose.runOnIdle { ui.openPileMenu(f.container(PlayZone.GRAVEYARD)) }
        val before = f.commands().size
        click("All to exile")
        assertEquals(1, newCommands(before).size)
        assertEquals(4, newCommands(before).single().playActions().count { it is PlayAction.MoveCard })
        for (n in 40..42) assertEquals(f.container(PlayZone.EXILE), f.card(n).containerId)
        assertEquals(f.container(PlayZone.EXILE, f.other), f.card(43).containerId)
        assertTrue(f.play.board.containers.single { it.id == f.container(PlayZone.GRAVEYARD) }.cards.isEmpty())
        assertNull(ui.panel)
    }

    @Test fun allToLibraryTopIsOneRandomOrderInvocationIntoOwnersLibraries() = allToLibrary(bottom = false)
    @Test fun allToLibraryBottomIsOneRandomOrderInvocationIntoOwnersLibraries() = allToLibrary(bottom = true)
    private fun allToLibrary(bottom: Boolean) {
        setup()
        ui.openPileMenu(f.container(PlayZone.GRAVEYARD))
        val before = f.commands().size
        assertTrue(ui.pileToZone(f.container(PlayZone.GRAVEYARD), PlayZone.LIBRARY, bottom = bottom))
        assertEquals(1, newCommands(before).size)
        val library = f.play.board.containers.single { it.id == f.container(PlayZone.LIBRARY) }.cards
        assertEquals((40..42).map(f::id).toSet(), library.toSet())
        assertEquals(listOf(f.id(43)), f.play.board.containers.single { it.id == f.container(PlayZone.LIBRARY, f.other) }.cards)
        assertNull(ui.panel)
    }

    @Test fun shuffleAllIntoLibraryMovesAndShufflesEveryReceivingLibraryInOneInvocation() {
        setup(PlayZone.EXILE)
        mount()
        compose.runOnIdle { ui.openPileMenu(f.container(PlayZone.EXILE)) }
        val before = f.commands().size
        click("Shuffle all into library")
        val actions = newCommands(before).single().playActions()
        assertEquals(setOf(f.container(PlayZone.LIBRARY), f.container(PlayZone.LIBRARY, f.other)),
            actions.filterIsInstance<PlayAction.ShuffleLibrary>().map { it.containerId }.toSet())
        for (n in 40..42) assertEquals(f.container(PlayZone.LIBRARY), f.card(n).containerId)
        assertEquals(f.container(PlayZone.LIBRARY, f.other), f.card(43).containerId)
    }

    @Test fun allToTempOneAndOpponentUsingTheMenuOnTheOwnersPile() {
        setup(mixed = false, viewer = f.other)
        mount(f.other)
        compose.runOnIdle { ui.openPileMenu(f.container(PlayZone.GRAVEYARD)) }
        val before = f.commands().size
        click("All to Temp 1")
        assertEquals(1, newCommands(before).size)
        val temp = TempPiles.container(f.play.board, f.actor, 1)!!
        assertEquals((40..42).map(f::id).toSet(), temp.cards.toSet())
    }

    @Test fun panelMenuActsOnTheSelectedCardsOfThatPanelOnlyAsOneInvocation() {
        setup(mixed = false)
        // A card elsewhere (Library) is selected too: it is never part of the panel action.
        val elsewhere = f.id(43)
        ui.openPublicPile(f.container(PlayZone.GRAVEYARD))
        ui.selectionState.value = TableSelection(ids = setOf(f.id(40), f.id(41), elsewhere))
        mount()
        // Same-name copies share one grouped row, represented by its first card (40).
        val item = scene().items.single { it.cardId == f.id(40) && it.viewport.startsWith("cardPanel/") }
        compose.mainClock.advanceTimeBy(600)
        compose.onNodeWithContentDescription(item.label).performClick()
        compose.mainClock.advanceTimeBy(350)
        compose.onNodeWithContentDescription("Panel card actions").assertExists()
        // Every unified cell is shown; the panel's own zone is greyed.
        for (action in panelCardActions) compose.onNodeWithContentDescription(action.name).assertExists()
        compose.onNodeWithContentDescription("Graveyard").assertIsNotEnabled()
        compose.onNodeWithContentDescription("Reveal+Hand").assertIsNotEnabled()
        val before = f.commands().size
        click("Exile")
        assertEquals(1, newCommands(before).size)
        assertEquals(f.container(PlayZone.EXILE), f.card(40).containerId)
        assertEquals(f.container(PlayZone.EXILE), f.card(41).containerId)
        assertEquals(f.container(PlayZone.GRAVEYARD), f.card(42).containerId)
        assertEquals(f.container(PlayZone.LIBRARY), f.card(43).containerId)
        // An unselected card: only that card.
        val single = scene().items.single { it.cardId == f.id(42) && it.viewport.startsWith("cardPanel/") }
        compose.mainClock.advanceTimeBy(600)
        compose.onNodeWithContentDescription(single.label).performClick()
        click("Battlefield tapped")
        assertEquals(f.container(PlayZone.BATTLEFIELD), f.card(42).containerId)
        assertTrue(f.card(42).tapped)
        assertEquals(f.container(PlayZone.EXILE), f.card(40).containerId)
    }

    @Test fun panelRectangleSelectsEveryCopyOfAGroupedRowAndNothingElse() {
        setup(mixed = false)
        ui.openPublicPile(f.container(PlayZone.GRAVEYARD))
        val scene = scene()
        val panel = scene.cardPanels.single { it.localZone == PlayZone.GRAVEYARD }
        assertEquals(3, panel.rows.single().copies)
        val row = panel.rows.single().bounds
        assertEquals((40..42).map(f::id).toSet(), scene.panelRectangle(panel.viewport.id,
            TableBounds(row.x - 2f, row.y - 2f, scene.fields.last().bounds.right, 8f)))
    }

    @Test fun twoSelectedPanelCardsGoOntoTheLibraryInRandomOrderAsOneInvocation() {
        setup(mixed = false)
        ui.openPublicPile(f.container(PlayZone.GRAVEYARD))
        ui.selectionState.value = TableSelection(ids = setOf(f.id(40), f.id(42)))
        val before = f.commands().size
        assertTrue(ui.localPanelAction(listOf(f.id(40), f.id(42)), PlayZone.LIBRARY, bottom = true))
        assertEquals(1, newCommands(before).size)
        assertEquals(setOf(f.id(40), f.id(42)), f.play.board.containers.single { it.id == f.container(PlayZone.LIBRARY) }.cards.takeLast(2).toSet())
        assertEquals(f.container(PlayZone.GRAVEYARD), f.card(41).containerId)
    }

    @Test fun changingTheInteractionModeKeepsGraveyardAndSearchPanelsOpen() {
        setup(mixed = false)
        assertTrue(f.lobby.gameplay(PlayAction.ShowContents(f.container(PlayZone.LIBRARY), ContentsSelection.ALL, 1, false)))
        ui.openPublicPile(f.container(PlayZone.GRAVEYARD))
        mount()
        compose.mainClock.advanceTimeBy(600)
        val before = f.commands().size
        for (step in 1..3) {
            compose.onNodeWithContentDescription("Interaction mode").performClick()
            compose.mainClock.advanceTimeBy(600)
            compose.runOnIdle {
                assertEquals(f.container(PlayZone.GRAVEYARD), ui.layoutState.value.localPileId)
                assertEquals(1, f.play.board.contentsDisplays.size)
            }
        }
        assertEquals(before, f.commands().size)
        assertTrue(scene().cardPanels.any { it.localZone == PlayZone.GRAVEYARD })
    }
}
