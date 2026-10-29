package org.asyncttwiz.android

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import org.asyncttwiz.protocol.*
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w412dp-h892dp-port-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CardPanelUiTest {
    @get:Rule val compose = createComposeRule()
    private val f = BasicTableFixture()
    private lateinit var ui: TableToolkitController
    private fun setup(viewer: String = f.actor) {
        f.connect()
        ui = TableToolkitController(viewer, { f.lobby.state.play }, { true }, f.lobby::gameplay, playBatch = f.lobby::gameplayBatch)
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

    @Test fun lookPanelOffersExistingStackAction() = stackFromPanel(PlayZone.LIBRARY, ContentsSelection.TOP)
    @Test fun searchPanelOffersExistingStackAction() = stackFromPanel(PlayZone.LIBRARY, ContentsSelection.ALL)
    @Test fun graveyardPanelOffersExistingStackAction() = stackFromPanel(PlayZone.GRAVEYARD)
    @Test fun exilePanelOffersExistingStackAction() = stackFromPanel(PlayZone.EXILE)

    private fun stackFromPanel(zone: PlayZone, selection: ContentsSelection = ContentsSelection.TOP) {
        setup()
        if (zone == PlayZone.LIBRARY) show(selection, count = 1)
        else {
            assertTrue(f.move(40, TableDestination.Container(f.container(zone), zone)))
            ui.openPublicPile(f.container(zone))
        }
        mount()
        val item = scene().items.first { it.viewport.startsWith("cardPanel/") }
        compose.onAllNodesWithContentDescription(item.label)[0].performClick()
        val before = f.commands().size
        click("Stack")
        val action = assertIs<PlayAction.Stack>((f.commands().drop(before).single().action as GameAction.Play).action)
        assertEquals(item.cardId, assertIs<StackAction.AddEffect>(action.action).sourceId)
        assertEquals(f.container(zone), f.card(40).containerId)
        assertEquals(item.cardId, f.play.board.stackEffects.single().sourceId)
        assertTrue(CardVisibility.isVisibleFaceUp(f.play.board, f.card(40), f.other))
    }

    @Test fun libraryPanelAutoClosesOnLastMenuMoveAndAnotherCanOpenImmediately() {
        setup(); ui.openLibrary(f.container(PlayZone.LIBRARY)); mount()
        click("Look 1")
        compose.onNodeWithContentDescription("Your Look panel, 1 cards").assertIsDisplayed()
        compose.onNodeWithText("Dismiss").assertDoesNotExist()
        val item = scene().items.single { it.viewport.startsWith("cardPanel/") }
        compose.onNodeWithContentDescription(item.label).performClick()
        click("Hand")
        assertEquals(f.container(PlayZone.HAND), f.card(40).containerId)
        assertTrue(f.play.board.contentsDisplays.isEmpty())
        compose.onNodeWithContentDescription("Close Look panel").assertDoesNotExist()
        compose.onNodeWithContentDescription("Panel rest choices").assertDoesNotExist()
        compose.runOnIdle { ui.openLibrary(f.container(PlayZone.LIBRARY)) }
        compose.mainClock.advanceTimeBy(350)
        for (name in listOf("Search", "Look 1", "Look N…", "Reveal 1", "Reveal N…"))
            compose.onNodeWithContentDescription(name).assertIsEnabled()
        click("Look 1")
        click("Close Look panel")
        click("Top")
        assertTrue(f.play.board.contentsDisplays.isEmpty())
    }

    @Test fun searchGroupsCopiesByNameAndCloseIsAShuffleNotADialogDismissal() {
        setup(); show(ContentsSelection.ALL); mount()
        assertEquals(1, scene().cardPanels.single().rows.size)
        assertEquals(4, scene().cardPanels.single().rows.single().copies)
        val item = scene().items.single { it.viewport.startsWith("cardPanel/") }
        assertEquals(4, item.copies)
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

    @Test fun revealAndHandAreOneRequestWithSeparateHistoryGroups() {
        setup(); show(); mount()
        val item = scene().items.first { it.viewport.startsWith("cardPanel/") }
        compose.onAllNodesWithContentDescription(item.label)[0].performClick()
        val count = f.commands().size
        click("Reveal+Hand")
        assertEquals(1, f.commands().drop(count).size)
        val commands = f.commands().drop(count).playMembers()
        assertTrue((commands[0].action as GameAction.Play).action is PlayAction.RevealContentsCard)
        assertTrue((commands[1].action as GameAction.Play).action is PlayAction.MoveCard)
        assertEquals(commands[0].invocationId, commands[1].invocationId)
        val disclosure = GameplayRules.change(f.root, f.actor, f.commands().first()).state!!
        val revealed = GameplayRules.change(disclosure, f.actor, commands[0]).state!!
        assertNotEquals(revealed.currentGroup!!.id, f.play.currentGroup!!.id)
        assertFalse(f.card(40).knownToAll)
        val opponent = scene(f.other).items.single { it.cardId == f.id(40) }
        assertTrue(opponent.covered); assertFalse(opponent.panelRevealed)
        assertEquals("", opponent.value)
        assertNull(opponent.artwork)
        val moved = f.lobby.displayTree()!!.nodes.values.last { it.action is PlayAction.MoveCard }
        assertTrue(historyText(moved, revealed, f.other).description.contains(f.definition.faces.single().name))
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
        for (label in listOf("Top", "Bottom", "Shuffle", "Graveyard", "Exile"))
            compose.onNodeWithContentDescription(label).assertIsDisplayed()
        compose.onNodeWithText("Bottom (random)").assertDoesNotExist()
        click("Bottom")
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
            assertEquals(104f, p.bounds.width)
            assertEquals(28f, p.rows.first().bounds.width)
            assertEquals(40f, p.rows.first().bounds.height)
            assertEquals(s.world!!.viewport.x - 6, p.bounds.x)
            assertTrue(p.bounds.bottom <= 892f)
            assertTrue(s.world!!.viewport.width - p.bounds.width >= 200)
        }
    }

    @Test fun opponentSeesBacksThenOneFlipAndCanHoldInspectDuringPausedReview() {
        setup(f.other); show(); var pauses = 0; mount(f.other, review = true) { pauses++ }
        val before = scene(f.other)
        assertTrue(before.cardPanels.single().rows.all { !it.entry.faceVisible && it.entry.name == "Card back" })
        assertTrue(before.items.filter { it.viewport.startsWith("cardPanel/") }.all { it.covered && it.value.isEmpty() })
        compose.onNodeWithText("Card back").assertDoesNotExist()
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

    @Test fun twoCardPanelUsesThreeColumnsAndStaysInsideTheBattlefield() {
        setup(); show(count = 2); mount()
        compose.onNodeWithContentDescription("Your Look panel, 2 cards").assertWidthIsEqualTo(104.dp)
        for (s in listOf(scene(), scene(width = 892f, height = 412f, landscape = true))) {
            val p = s.cardPanels.single()
            assertEquals(104f, p.bounds.width)
            assertEquals(s.world!!.visibleViewport.height, p.bounds.height)
            assertEquals(0f, p.viewport.maxScrollY)
            assertEquals(32f, p.rows.last().bounds.x - p.rows.first().bounds.x)
            assertTrue(p.rows.last().bounds.bottom < p.bounds.bottom)
        }
    }

    private fun opponentPanel(selection: ContentsSelection, reveal: Boolean = false): ContentsDisplay {
        val ids = if (selection == ContentsSelection.ALL) (40..43).map(f::id) else listOf(f.id(40), f.id(41))
        val display = ContentsDisplay(f.id(500), f.other, f.container(PlayZone.LIBRARY), selection, ids.size, reveal, ids, panelVersion = 3)
        f.connect(f.root.copy(board = f.root.board.copy(contentsDisplays = listOf(display))))
        ui = TableToolkitController(f.actor, { f.lobby.state.play }, { true }, f.lobby::gameplay, playBatch = f.lobby::gameplayBatch)
        mount()
        compose.onNodeWithContentDescription("Opponent ${display.title} panel, ${ids.size} cards").assertIsDisplayed()
        return display
    }

    @Test fun eitherPlayerClosesAForgottenLookPanelWithTheAuthorsChoices() {
        val display = opponentPanel(ContentsSelection.TOP)
        // Select all and card actions stay the author's; closing is anyone's.
        compose.onNodeWithContentDescription("Select all in Look panel").assertDoesNotExist()
        click("Close opponent's Look panel")
        for (label in listOf("Top", "Bottom", "Shuffle", "Graveyard", "Exile")) compose.onNodeWithContentDescription(label).assertIsEnabled()
        click("Top")
        assertTrue(f.play.board.contentsDisplays.isEmpty())
        val close = assertIs<PlayAction.DismissContents>((f.commands().last().action as GameAction.Play).action)
        assertEquals(display.id, close.displayId)
        assertEquals(ContentsRest.TOP, close.rest)
        assertEquals(display.cardIds, f.play.board.containers.single { it.id == f.container(PlayZone.LIBRARY) }.cards.take(2))
        assertTrue(f.play.board.cards.none { CardVisibility.isVisibleFaceUp(f.play.board, it, f.actor) })
    }

    @Test fun eitherPlayerClosesAForgottenRevealWithTheFullRestChoice() {
        opponentPanel(ContentsSelection.TOP, reveal = true)
        click("Done opponent's Reveal panel")
        for (label in listOf("Top", "Bottom", "Shuffle", "Graveyard", "Exile")) compose.onNodeWithContentDescription(label).assertIsEnabled()
        click("Graveyard")
        assertTrue(f.play.board.contentsDisplays.isEmpty())
        assertEquals(listOf(f.id(40), f.id(41)).toSet(), f.play.board.containers.single { it.id == f.container(PlayZone.GRAVEYARD) }.cards.toSet())
    }

    @Test fun eitherPlayerClosesAForgottenSearchAsTheUsualShuffle() {
        val display = opponentPanel(ContentsSelection.ALL)
        click("Close opponent's Search panel")
        compose.onNodeWithContentDescription("Panel rest choices").assertDoesNotExist()
        assertTrue(f.play.board.contentsDisplays.isEmpty())
        val close = assertIs<PlayAction.DismissContents>((f.commands().last().action as GameAction.Play).action)
        assertEquals(display.id, close.displayId)
        assertNotNull(close.seed)
    }

    @Test fun legacyTwoPanelsRemainSideBySideAndClosableInEitherOrientation() {
        setup(); show(count = 2)
        val own = panel()
        val opponents = (140..143).map { n -> f.card(40).copy(id = f.id(n), owner = f.other,
            containerId = f.container(PlayZone.LIBRARY, f.other)) }
        val opponent = own.copy(id = f.id(500), author = f.other, containerId = f.container(PlayZone.LIBRARY, f.other),
            cardIds = opponents.map { it.id })
        val play = f.play.copy(board = f.play.board.copy(cards = f.play.board.cards + opponents,
            contentsDisplays = listOf(own, opponent)))
        compose.setContent { MaterialTheme { TableSurface(f.game, play, f.actor, false) } }
        compose.onNodeWithContentDescription("Opponent Look panel, 4 cards").assertWidthIsEqualTo(72.dp)
        compose.onNodeWithContentDescription("Your Look panel, 2 cards").assertIsDisplayed()
        compose.onNodeWithText("Card back").assertDoesNotExist()
        for (landscape in listOf(false, true)) {
            val s = TablePresentation.scene(f.game, play, f.actor, if (landscape) 892f else 412f,
                if (landscape) 412f else 892f, landscape)
            val (left, right) = s.cardPanels
            assertEquals(f.other, left.display.author)
            assertTrue(left.bounds.right < right.bounds.x)
            assertEquals(left.bounds.y, right.bounds.y)
            assertEquals(left.bounds.height, right.bounds.height)
            assertTrue(s.cardPanels.all { it.columns <= 2 })
            assertEquals(0f, right.viewport.maxScrollY)
        }
    }

    @Test fun quickActionsUseShortLabelsWithoutGrowingCells() {
        setup(); show(count = 2); mount()
        val item = scene().items.first { it.viewport.startsWith("cardPanel/") }
        compose.onAllNodesWithContentDescription(item.label)[0].performClick()
        compose.mainClock.advanceTimeBy(350)
        for ((name, label) in listOf("Reveal+Hand" to "Reveal\nhand", "Battlefield tapped" to "Tapped",
            "Reveal+Top" to "Reveal\ntop")) {
            compose.onNodeWithContentDescription(name).assertWidthIsEqualTo(CardRingGeometry.cell.dp)
                .assertHeightIsEqualTo(CardRingGeometry.cell.dp)
            assertEquals(label, panelCardActionLabel(panelCardActions.single { it.label == name }))
        }
        compose.onNodeWithContentDescription("Hand").assertWidthIsEqualTo(CardRingGeometry.cell.dp)
            .assertHeightIsEqualTo(CardRingGeometry.cell.dp)
    }

    @Test fun localViewSitsBesideLookHasFixedGreyedMenuAndSupportsHoldAndDragOut() {
        setup()
        assertTrue(f.move(40, TableDestination.Container(f.container(PlayZone.GRAVEYARD), PlayZone.GRAVEYARD)))
        show(count = 2)
        val beforeOpen = f.commands().size
        ui.openPublicPile(f.container(PlayZone.GRAVEYARD))
        mount()
        assertEquals(beforeOpen, f.commands().size)
        val s = scene()
        val local = s.cardPanels.last()
        assertEquals(PlayZone.GRAVEYARD, local.localZone)
        assertTrue(s.cardPanels.first().bounds.right < local.bounds.x)
        val item = s.items.single { it.cardId == f.id(40) }
        compose.onNodeWithContentDescription(item.label).performClick()
        compose.mainClock.advanceTimeBy(350)
        compose.onNodeWithContentDescription(item.label).assertIsSelected()
        for (label in listOf("Graveyard", "Reveal+Hand", "Reveal+Top"))
            compose.onNodeWithContentDescription(label).assertIsNotEnabled()
        assertEquals(10, panelCardActions.size)
        val menu = compose.onNodeWithContentDescription("Hand").fetchSemanticsNode().boundsInRoot
        assertEquals(s.cardPanels.maxOf { it.bounds.right } + 4, menu.left, .1f)
        assertEquals(local.viewport.bounds.y + 2, menu.top, .1f)
        compose.runOnIdle { ui.dismiss() }
        compose.mainClock.autoAdvance = false
        compose.onNodeWithContentDescription("Shared table").performTouchInput {
            down(Offset(item.bounds.center.x, item.bounds.center.y))
        }
        compose.mainClock.advanceTimeBy(800)
        compose.runOnIdle { assertEquals(f.id(40), ui.peek?.cardId) }
        compose.onNodeWithContentDescription("Shared table").performTouchInput { up() }
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        val target = s.fields.last().bounds.let { TablePoint(it.right - 28f, it.center.y) }
        compose.onNodeWithContentDescription("Shared table").performTouchInput {
            down(Offset(item.bounds.center.x, item.bounds.center.y)); moveTo(Offset(target.x, target.y), 100); up()
        }
        compose.waitForIdle()
        assertEquals(f.container(PlayZone.BATTLEFIELD), f.card(40).containerId)
        assertTrue(scene().cardPanels.none { it.localZone != null })
        assertTrue(f.commands().drop(beforeOpen).none { (it.action as GameAction.Play).action is PlayAction.ReorderContents })
        assertNull(ui.layoutState.value.localPileId)
        assertEquals(1, scene().cardPanels.size)
    }

    @Test fun remoteLiveRevealSettlesFaceUpThenWaitsTheConfiguredPauseBeforeOnwardMove() {
        setup(); show(count = 2)
        val opened = f.lobby.displayTree()!!.materializedCopy()
        val id = panel().cardIds.first()
        ui.cardPanelAction(panel(), id, PlayZone.HAND, reveal = true)
        val onward = f.lobby.displayTree()!!.materializedCopy()
        val revealed = onward.nodes.values.single { it.action is PlayAction.RevealContentsCard }.id
        val player = HistoryPlayback(HistoryTree(opened.initial.root.id, emptyList(), opened.currentNodeId, "open", source = opened))
        var settled by mutableStateOf<String?>(null)
        var animate by mutableStateOf(false)
        var displayed: TableScene? = null
        compose.mainClock.autoAdvance = false
        compose.setContent { MaterialTheme {
            val play = onward.stateAt(player.position.cursorId)!!
            SideEffect { displayed = TablePresentation.scene(f.game, play, f.other, 412f, 892f, false) }
            LivePanelRevealPacing(player, 1_500, animate && settled == player.position.cursorId)
            TableSurface(f.game, play, f.other, false,
                runtime = TableRuntimeHooks(onSceneSettled = { settled = it }, sceneVersion = player.position.displayVersion))
        } }
        compose.mainClock.advanceTimeBy(250)
        compose.runOnIdle {
            player.receive(HistoryTree(onward.initial.root.id, emptyList(), onward.currentNodeId, "onward", source = onward), f.other)
        }
        compose.mainClock.advanceTimeByFrame()
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(400)
        compose.runOnIdle {
            assertEquals(HistoryMode.LIVE, player.position.mode)
            assertEquals(revealed, player.position.cursorId)
            assertEquals(revealed, settled)
            assertFalse(displayed!!.items.single { it.cardId == id }.covered)
        }
        // No pause starts until the host says the endpoint animation is done.
        compose.mainClock.advanceTimeBy(2_000)
        compose.runOnIdle { assertEquals(revealed, player.position.cursorId); animate = true }
        compose.waitForIdle()
        compose.mainClock.advanceTimeByFrame()
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(1_400)
        compose.runOnIdle {
            assertEquals(revealed, player.position.cursorId)
            assertTrue(displayed!!.items.single { it.cardId == id }.viewport.startsWith("cardPanel/"))
        }
        compose.mainClock.advanceTimeBy(150)
        compose.runOnIdle {
            assertEquals(onward.currentNodeId, player.position.cursorId)
            assertEquals(HistoryMode.LIVE, player.position.mode)
            assertFalse(player.livePanelReveal)
        }
    }
}
