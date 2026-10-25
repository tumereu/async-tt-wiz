package org.asyncttwiz.android

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import org.asyncttwiz.protocol.*
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w412dp-h892dp-port-mdpi")
class CardPanelUiTest {
    @get:Rule val compose = createComposeRule()
    private val f = BasicTableFixture()
    private lateinit var ui: TableToolkitController
    private fun setup(viewer: String = f.actor) {
        f.connect()
        ui = TableToolkitController(viewer, { f.lobby.state.play }, { true }, f.lobby::gameplay)
    }
    private fun mount(viewer: String = f.actor, review: Boolean = false, pause: () -> Unit = {}) {
        compose.setContent { MaterialTheme {
            f.lobby.state.play?.let { play -> TableSurface(f.game, play, viewer, false, connected = !review, toolkit = ui, onIntent = ui::handle,
                runtime = TableRuntimeHooks(reviewing = review, pauseForInspection = pause))
            }
        } }
        compose.waitForIdle()
    }
    private fun show(selection: ContentsSelection = ContentsSelection.TOP, count: Int = 3, reveal: Boolean = false) {
        assertTrue(f.lobby.gameplay(PlayAction.ShowContents(f.container(PlayZone.LIBRARY), selection, count, reveal)))
    }
    private fun click(name: String) {
        compose.mainClock.advanceTimeBy(350)
        compose.onNodeWithContentDescription(name).performClick()
        compose.waitForIdle()
    }
    private fun scene(viewer: String = f.actor, width: Float = 412f, height: Float = 892f, landscape: Boolean = false) =
        TablePresentation.scene(f.game, f.play, viewer, width, height, landscape, ui.layoutState.value)
    private fun panel() = f.play.board.contentsDisplays.single()
    @After fun close() { f.lobby.close(); f.worker.drain() }

    @Test fun libraryOpensFloatingPanelTapMovesOneAndNewPanelActionsAreDisabled() {
        setup(); ui.openLibrary(f.container(PlayZone.LIBRARY)); mount()
        click("Look 1")
        compose.onNodeWithContentDescription("Your Look panel, 1 cards").assertIsDisplayed()
        compose.onNodeWithText("Dismiss").assertDoesNotExist()
        val item = scene().items.single { it.viewport.startsWith("cardPanel/") }
        compose.onNodeWithContentDescription(item.label).performClick()
        click("Hand")
        assertEquals(f.container(PlayZone.HAND), f.card(40).containerId)
        assertTrue(panel().cardIds.isEmpty())
        compose.runOnIdle { ui.openLibrary(f.container(PlayZone.LIBRARY)) }
        compose.mainClock.advanceTimeBy(350)
        for (name in listOf("Search", "Look 1", "Look N…", "Reveal 1", "Reveal N…"))
            compose.onNodeWithContentDescription(name).assertIsNotEnabled()
        compose.runOnIdle { ui.dismiss() }
        click("Close Look panel")
        assertTrue(f.play.board.contentsDisplays.isEmpty())
    }

    @Test fun searchGroupsCopiesByNameAndCloseIsAShuffleNotADialogDismissal() {
        setup(); show(ContentsSelection.ALL); mount()
        assertEquals(1, scene().cardPanels.single().rows.size)
        assertEquals(4, scene().cardPanels.single().rows.single().copies)
        val item = scene().items.single { it.viewport.startsWith("cardPanel/") }
        compose.onNodeWithContentDescription(item.label).performClick()
        click("Hand")
        assertEquals(3, panel().cardIds.size)
        assertEquals(3, scene().cardPanels.single().rows.single().copies)
        click("Close Search panel")
        assertTrue(f.play.board.contentsDisplays.isEmpty())
        val close = (f.commands().last().action as GameAction.Play).action as PlayAction.DismissContents
        assertNotNull(close.seed)
        assertEquals("3", f.play.currentGroup!!.count) // Open, one card move, close+shuffle.
    }

    @Test fun revealAndHandAreSeparateRequestsAndHistoryGroups() {
        setup(); show(); mount()
        val item = scene().items.first { it.viewport.startsWith("cardPanel/") }
        compose.onAllNodesWithContentDescription(item.label)[0].performClick()
        val count = f.commands().size
        click("Reveal+Hand")
        val commands = f.commands().drop(count)
        assertTrue((commands[0].action as GameAction.Play).action is PlayAction.RevealContentsCard)
        assertTrue((commands[1].action as GameAction.Play).action is PlayAction.MoveCard)
        assertNotEquals(commands[0].invocationId, commands[1].invocationId)
        val disclosure = GameplayRules.change(f.root, f.actor, f.commands().first()).state!!
        val revealed = GameplayRules.change(disclosure, f.actor, commands[0]).state!!
        assertNotEquals(revealed.currentGroup!!.id, f.play.currentGroup!!.id)
        assertTrue(f.card(40).knownToAll)
        val opponent = scene(f.other).items.single { it.cardId == f.id(40) }
        assertFalse(opponent.covered); assertTrue(opponent.known)
    }

    @Test fun panelDragUsesTableLandingAndRestChooserPreservesPanelOrder() {
        setup(); show(); mount()
        val before = panel().cardIds
        val s = scene()
        val card = s.items.single { it.cardId == before.first() }
        val field = s.fields.last()
        val target = TablePoint(field.bounds.right - 28f, field.bounds.center.y)
        compose.onNodeWithContentDescription("Shared table").performTouchInput {
            down(Offset(card.bounds.center.x, card.bounds.center.y)); moveTo(Offset(target.x, target.y), 100); up()
        }
        compose.waitForIdle()
        assertEquals(f.container(PlayZone.BATTLEFIELD), f.card(40).containerId)
        assertEquals(before.drop(1), panel().cardIds)
        val move = f.commands().map { (it.action as GameAction.Play).action }.filterIsInstance<PlayAction.MoveCard>().last()
        assertEquals(MoveInsertion.EMPTY, move.destination.insertion)
        click("Close Look panel")
        for (label in listOf("Top (panel order)", "Bottom (panel order)", "Bottom (random)", "Graveyard", "Shuffle into library"))
            compose.onNodeWithText(label).assertIsDisplayed()
        compose.onNodeWithText("Bottom (panel order)").performClick()
        compose.waitForIdle()
        assertTrue(f.play.board.contentsDisplays.isEmpty())
        assertEquals(before.drop(1), f.play.board.containers.single { it.id == f.container(PlayZone.LIBRARY) }.cards.takeLast(2))
    }

    @Test fun lookDragReordersInsideTheSamePanelAndBothOrientationsStayNarrow() {
        setup(); show(); mount()
        val before = panel().cardIds
        val rows = scene().cardPanels.single().rows
        compose.onNodeWithContentDescription("Shared table").performTouchInput {
            down(Offset(rows.first().bounds.center.x, rows.first().bounds.center.y))
            moveTo(Offset(rows.last().bounds.center.x, rows.last().bounds.bottom + 2), 100); up()
        }
        compose.waitForIdle()
        assertEquals(before.drop(1) + before.first(), panel().cardIds)
        assertTrue((f.commands().last().action as GameAction.Play).action is PlayAction.ReorderContents)
        for (s in listOf(scene(), scene(width = 892f, height = 412f, landscape = true))) {
            val p = s.cardPanels.single()
            assertEquals(124f, p.bounds.width)
            assertEquals(s.world!!.viewport.x - 6, p.bounds.x)
            assertTrue(p.bounds.bottom <= s.world!!.viewport.bottom)
            assertTrue(s.world!!.viewport.width - p.bounds.width >= 200)
        }
    }

    @Test fun opponentSeesBacksThenOneFlipAndCanHoldInspectDuringPausedReview() {
        setup(f.other); show(); var pauses = 0; mount(f.other, review = true) { pauses++ }
        val before = scene(f.other)
        assertTrue(before.cardPanels.single().rows.all { !it.entry.faceVisible && it.entry.name == "Card back" })
        assertTrue(before.items.filter { it.viewport.startsWith("cardPanel/") }.all { !before.canDrag(it) })
        val id = panel().cardIds.first()
        compose.runOnIdle { assertTrue(f.lobby.gameplay(PlayAction.RevealContentsCard(panel().id, id))) }
        compose.waitForIdle()
        val after = scene(f.other)
        val flipped = after.items.single { it.cardId == id }
        assertFalse(flipped.covered)
        assertEquals(1, after.cardPanels.single().rows.count { it.entry.faceVisible })
        val animation = HistorySceneAnimation(before)
        assertTrue(animation.retarget(after, 1f))
        assertTrue(animation.frame(.25f).single { it.item.cardId == id }.item.covered)
        assertFalse(animation.frame(.75f).single { it.item.cardId == id }.item.covered)
        assertTrue(animation.frame(.5f).single { it.item.cardId == id }.item.flipScale < .1f)
        val row = compose.onNodeWithContentDescription(flipped.label).assertIsDisplayed()
        compose.mainClock.autoAdvance = false
        row.performTouchInput { down(center) }
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(800)
        compose.runOnIdle {
            assertTrue(pauses > 0)
            assertEquals(id, ui.peek!!.cardId)
            assertTrue(ui.peek!!.faceIds.isNotEmpty())
        }
        row.performTouchInput { up() }
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        assertNull(ui.peek)
    }
}
