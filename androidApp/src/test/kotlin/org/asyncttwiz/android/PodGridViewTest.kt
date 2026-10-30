package org.asyncttwiz.android

import androidx.compose.runtime.*
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import org.asyncttwiz.protocol.*
import org.asyncttwiz.protocol.PodGridView as GridViewData
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w412dp-h892dp-port-mdpi")
class PodGridViewTest {
    @get:Rule val compose = createComposeRule()
    private val alice = AccountIdentity("alice", "Alice")
    private val bob = AccountIdentity("bob", "Bob")
    private val cards = List(9) { DraftCopy("copy-$it", "definition-$it") }
    private val definitions = cards.associate { it.definition to CardDefinition("Card ${it.id.last()}", listOf(DefinitionFace("face", "Card ${it.id.last()}"))) }
    private val board = GridPublicBoard("grid-0", 1, cards, alice.accountId)
    private val mine = GridPrivateView(alice.accountId, emptyList(), board.id)
    private val initial = GridViewData(GridPublicView(1, 9, false, listOf("alice", "bob"), listOf(board), emptyList()), mine)

    private fun show(view: () -> GridViewData = { initial }, busy: () -> Boolean = { false },
        onConfirm: (String, GridLineType, Int) -> Unit = { _, _, _ -> }) {
        compose.setContent { ScreenTheme {
            PodGridView(view(), 18, definitions, listOf(alice, bob), busy(), onConfirm)
        } }
    }

    private fun slot(index: Int) = compose.onNodeWithTag("pod-grid-grid-0-slot-$index")
    private fun assertHighlighted(index: Int, selected: Boolean) = slot(index).assert(
        SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, if (selected) "Highlighted" else "Unselected"))

    @Test fun onlyNonemptyLinesAreSelectableAndHighlightUsesRowMajorPositions() {
        val sparse = board.copy(positions = List(9) { if (it == 4) cards[it] else null })
        val rules = PodGridRules(sparse, mine)
        for (type in GridLineType.entries) for (index in -1..3) {
            assertEquals(index == 1, rules.canSelect(GridLine(type, index)))
        }
        val row = GridLine(GridLineType.ROW, 1)
        assertEquals(listOf(3, 4, 5), rules.positions(row))
        assertEquals(listOf(1, 4, 7), rules.positions(GridLine(GridLineType.COLUMN, 1)))
        assertTrue(rules.highlighted(3, row)) // Includes the gap, without awarding it.
        assertFalse(rules.highlighted(0, row))
        assertFalse(rules.highlighted(4, null))
        assertFalse(PodGridRules(sparse.copy(currentPlayer = "bob"), mine).canSelect(row))
        assertFalse(PodGridRules(sparse.copy(id = "later"), mine).canSelect(row))
    }

    @Test fun arrowsHighlightLinesAndConfirmSendsGridLineWithoutChangingBoard() {
        var confirmed: Triple<String, GridLineType, Int>? = null
        show(onConfirm = { grid, line, index -> confirmed = Triple(grid, line, index) })
        compose.onNodeWithText("Your pick · Grid 1/18").assertIsDisplayed()
        compose.onNodeWithText("Confirm").assertIsNotEnabled()
        compose.onNodeWithContentDescription("Select row 2").performClick().assertIsSelected()
        repeat(9) { assertHighlighted(it, it in 3..5) }
        compose.onNodeWithContentDescription("Select column 3").performClick().assertIsSelected()
        repeat(9) { assertHighlighted(it, it % 3 == 2) }
        compose.onNodeWithText("Confirm").assertIsEnabled().performClick()
        assertEquals(Triple("grid-0", GridLineType.COLUMN, 2), confirmed)
        repeat(9) { slot(it).assertExists() }
    }

    @Test fun emptySlotsKeepGeometryAndEmptyLinesHaveDisabledArrows() {
        val positions = cards.mapIndexed { index, copy -> if (index < 3) null else copy }
        show(view = { initial.copy(publicView = initial.publicView.copy(grids = listOf(board.copy(positions = positions)))) })
        compose.onNodeWithContentDescription("Select row 1").assertIsNotEnabled()
        compose.onNodeWithContentDescription("Select row 2").assertIsEnabled()
        compose.onNodeWithContentDescription("Select column 1").assertIsEnabled()
        val empty = slot(0).fetchSemanticsNode().boundsInRoot
        val occupied = slot(3).fetchSemanticsNode().boundsInRoot
        assertEquals(empty.width, occupied.width)
        assertEquals(empty.height, occupied.height)
        compose.onAllNodesWithContentDescription("Empty slot").assertCountEquals(3)
    }

    @Test fun yourPickComesFirstThenOtherOpenGridsThenFinishedOnesWithArrowsOnlyOnYourPick() {
        var busy by mutableStateOf(false)
        val later = board.copy(id = "later", number = 2)
        val waiting = board.copy(id = "waiting", number = 3, currentPlayer = "bob")
        val done = board.copy(id = "done", number = 4, positions = List(9) { null }, currentPlayer = null, finished = true)
        val older = done.copy(id = "older", number = 5)
        show(view = { initial.copy(publicView = initial.publicView.copy(grids = listOf(waiting, later, board),
            finishedGrids = listOf(done, older)), mine = mine.copy(queue = listOf(board.id, later.id))) }, busy = { busy })
        val headers = listOf("Your pick · Grid 1/18", "Your pick next · Grid 2/18", "Waiting for Bob · Grid 3/18",
            "Finished · Grid 4/18", "Finished · Grid 5/18")
        val tops = headers.map { compose.onNodeWithText(it).fetchSemanticsNode().positionInRoot.y }
        assertEquals(tops.sorted(), tops)
        compose.onAllNodesWithContentDescription("Select row 1").assertCountEquals(1)
        compose.onAllNodesWithContentDescription("Select column 3").assertCountEquals(1)
        compose.onNodeWithTag("pod-grid-later-slot-0").assertExists()
        compose.onNodeWithContentDescription("Select row 1").performClick()
        compose.runOnIdle { busy = true }
        compose.onNodeWithText("Confirm").assertIsNotEnabled()
        compose.onNodeWithContentDescription("Select row 2").assertIsNotEnabled()
        assertHighlighted(3, false)
    }

    @Test fun waitingShowsAllOpenBoardsReadOnlyWithoutBuildShortcutAndStillPreviews() {
        val waitingBoard = board.copy(currentPlayer = "bob")
        show(view = { initial.copy(publicView = initial.publicView.copy(grids = listOf(waitingBoard, waitingBoard.copy(id = "later", number = 2))),
            mine = mine.copy(headGridId = null)) })
        compose.onNodeWithText("Waiting for other players").assertIsDisplayed().assertHasNoClickAction()
        compose.onNodeWithText("Waiting for Bob · Grid 1/18").assertExists()
        compose.onAllNodesWithContentDescription("Select row 1").assertCountEquals(0)
        compose.onNodeWithTag("pod-grid-later-slot-0").assertExists()
        compose.onNodeWithText("Confirm").assertDoesNotExist()
        compose.onNodeWithText("Build").assertDoesNotExist()
        assertHoldPreview(slot(0))
    }

    private fun assertHoldPreview(target: SemanticsNodeInteraction) {
        target.performTouchInput { down(center); advanceEventTime(80) }
        compose.mainClock.advanceTimeBy(80)
        compose.onNodeWithTag("pod-pick-art-preview").assertDoesNotExist()
        target.performTouchInput { advanceEventTime(80) }
        compose.mainClock.advanceTimeBy(80)
        compose.onNodeWithTag("pod-pick-art-preview").assertIsDisplayed()
        compose.onNodeWithContentDescription("Close card inspection").assertDoesNotExist()
        target.performTouchInput { up() }
        compose.onNodeWithTag("pod-pick-art-preview").assertDoesNotExist()
    }

    @Test fun gridUsesInstantReleaseToClosePreviewWithoutSelectingALine() {
        show()
        assertHoldPreview(slot(0))
        compose.onNodeWithText("Confirm").assertIsNotEnabled()
        repeat(9) { assertHighlighted(it, false) }
    }

    @Test fun tapOnAGridCardOpensTheFullInspectorWithoutSelectingALine() {
        show()
        slot(4).performClick()
        compose.onNodeWithContentDescription("Close card inspection").assertIsDisplayed()
        compose.onNode(hasText("Card 4") and hasAnyAncestor(hasContentDescription("Card inspection content"))).assertIsDisplayed()
        compose.onNodeWithContentDescription("Close card inspection").performClick()
        compose.onNodeWithContentDescription("Close card inspection").assertDoesNotExist()
        repeat(9) { assertHighlighted(it, false) }
        compose.onNodeWithText("Confirm").assertIsNotEnabled()
    }

    @Test fun selectionResetsAfterServerRefillEvenIfGridAndPlayerStayTheSame() {
        var view by mutableStateOf(initial)
        show(view = { view })
        compose.onNodeWithContentDescription("Select row 1").performClick()
        compose.runOnIdle { view = view.copy(publicView = view.publicView.copy(grids = listOf(board.copy(positions = cards.reversed())))) }
        compose.onNodeWithText("Confirm").assertIsNotEnabled()
        repeat(9) { assertHighlighted(it, false) }
    }

    @Test fun selectionsInOtherParallelGridsDoNotResetMyHighlight() {
        var view by mutableStateOf(initial)
        show(view = { view })
        compose.onNodeWithContentDescription("Select row 1").performClick()
        compose.runOnIdle { view = view.copy(publicView = view.publicView.copy(log = listOf(
            GridPublicPickLog("bob", "other", GridLine(GridLineType.ROW, 0), cards.take(3), emptyList())))) }
        compose.onNodeWithText("Confirm").assertIsEnabled()
        repeat(9) { assertHighlighted(it, it < 3) }
    }

    @Test fun cardsStayInPlaceWithPickersRefillsAndDiscardsInsteadOfATextLog() {
        val slots = cards.mapIndexed { index, copy -> when (index) {
            0, 1, 2 -> GridPublicSlot(copy.copy(id = "new-$index"), if (index == 1) "bob" else null, refill = true)
            4, 7 -> GridPublicSlot(copy, "bob")
            6, 8 -> GridPublicSlot(copy, "alice")
            else -> GridPublicSlot(copy)
        } }
        val done = board.copy(positions = List(9) { null }, currentPlayer = null, finished = true, slots = slots)
        // Alice's row 1 was refilled; Bob's column 2 and Alice's row 3 were picked from the refilled board.
        val log = listOf(GridPublicPickLog("alice", "grid-0", GridLine(GridLineType.ROW, 0), cards.take(3), emptyList()),
            GridPublicPickLog("bob", "grid-0", GridLine(GridLineType.COLUMN, 1), listOf(slots[1].copy!!, cards[4], cards[7]), emptyList()),
            GridPublicPickLog("alice", "grid-0", GridLine(GridLineType.ROW, 2), listOf(cards[6], cards[8]), listOf(cards[3], cards[5])))
        show(view = { initial.copy(publicView = initial.publicView.copy(grids = emptyList(), finishedGrids = listOf(done), log = log),
            mine = mine.copy(headGridId = null)) })
        compose.onNodeWithText("Finished · Grid 1/18").assertExists()
        slot(1).assertContentDescriptionEquals("Card 1, picked by Bob, new")
        slot(0).assertContentDescriptionEquals("Card 0, discarded, new")
        slot(4).assertContentDescriptionEquals("Card 4, picked by Bob")
        slot(8).assertContentDescriptionEquals("Card 8, picked by Alice")
        slot(3).assertContentDescriptionEquals("Card 3, discarded")
        compose.onAllNodesWithText("New").assertCountEquals(3)
        // The board before the refill, above in the same frame: Alice's original row, nothing discarded yet.
        val before = compose.onNodeWithText("Before refill").fetchSemanticsNode().positionInRoot.y
        val after = compose.onNodeWithText("After refill").fetchSemanticsNode().positionInRoot.y
        assertTrue(before < after && after < slot(0).fetchSemanticsNode().positionInRoot.y)
        fun earlier(index: Int) = compose.onNodeWithTag("pod-grid-grid-0-stage0-slot-$index")
        (0..2).forEach { earlier(it).assertContentDescriptionEquals("Card $it, picked by Alice") }
        (3..8).forEach { earlier(it).assertContentDescriptionEquals("Card $it") }
        compose.onNode(hasTestTag("pod-grid-grid-0-group") and hasAnyDescendant(hasTestTag("pod-grid-grid-0-stage0-slot-0")) and
            hasAnyDescendant(hasTestTag("pod-grid-grid-0-slot-0"))).assertExists()
        compose.onNodeWithText("took", substring = true).assertDoesNotExist()
        compose.onAllNodesWithContentDescription("Select row 1").assertCountEquals(0)
        assertHoldPreview(slot(3))
    }

    @Test fun selectionDimsOtherLiveCardsButNeverPickedOrDiscardedOnes() {
        val plain = PodGridCell(cards[0], null, false, false)
        assertEquals(1f, podGridArtAlpha(plain, live = true, highlighted = false, selecting = false))
        assertEquals(1f, podGridArtAlpha(plain, live = true, highlighted = true, selecting = true))
        val dimmed = podGridArtAlpha(plain, live = true, highlighted = false, selecting = true)
        val discarded = podGridArtAlpha(plain.copy(discarded = true), live = false, highlighted = false, selecting = true)
        assertTrue(dimmed < 1f && discarded < dimmed)
        assertEquals(1f, podGridArtAlpha(plain.copy(pickedBy = "bob"), live = false, highlighted = false, selecting = true))
    }

    @Test fun bandsRunAlongEachPickedLineWithEarlierPicksOnTop() {
        fun pick(player: String, line: GridLine) = GridPublicPickLog(player, "grid-0", line, emptyList(), emptyList())
        val picks = listOf(pick("alice", GridLine(GridLineType.ROW, 0)), pick("bob", GridLine(GridLineType.COLUMN, 1)),
            pick("carol", GridLine(GridLineType.ROW, 2)), pick("dave", GridLine(GridLineType.COLUMN, 0)),
            GridPublicPickLog("erin", "grid-0", null, emptyList(), emptyList()))
        // Drawing order, bottom first: where lines cross, the earlier pick lies on top.
        fun players(position: Int) = podGridBands(picks, position).map { it.player }
        assertEquals(listOf("dave", "alice"), players(0))
        assertEquals(listOf("bob", "alice"), players(1))
        assertEquals(listOf("alice"), players(2))
        assertEquals(listOf("dave"), players(3))
        assertEquals(listOf("bob"), players(4))
        assertEquals(emptyList(), players(5))
        assertEquals(listOf("dave", "carol"), players(6))
        assertEquals(listOf("carol", "bob"), players(7))
    }

    @Test fun onlyRefilledGridsGetBoardLabels() {
        assertNull(podGridStageLabel(0, 1))
        assertEquals(listOf("Before refill", "After refill"), List(2) { podGridStageLabel(it, 2) })
        assertEquals(listOf("Before refill", "After refill 1", "After refill 2"), List(3) { podGridStageLabel(it, 3) })
    }

    @Test fun liveRefilledGridShowsTheEarlierBoardAboveWithArrowsOnlyOnTheCurrentOne() {
        val refills = List(3) { DraftCopy("refill-$it", "definition-$it") }
        val live = board.copy(positions = refills + cards.drop(3),
            slots = (refills + cards.drop(3)).mapIndexed { index, copy -> GridPublicSlot(copy, refill = index < 3) })
        val log = listOf(GridPublicPickLog("bob", "grid-0", GridLine(GridLineType.ROW, 0), cards.take(3), emptyList()))
        val unrefilled = board.copy(id = "plain", number = 2, currentPlayer = "bob")
        show(view = { initial.copy(publicView = initial.publicView.copy(grids = listOf(live, unrefilled), log = log)) })
        compose.onNodeWithText("Before refill").assertExists()
        compose.onNodeWithTag("pod-grid-grid-0-stage0-slot-0").assertContentDescriptionEquals("Card 0, picked by Bob")
        slot(0).assertContentDescriptionEquals("Card 0, new")
        compose.onAllNodesWithContentDescription("Select row 1").assertCountEquals(1)
        compose.onNodeWithContentDescription("Select row 1").performClick()
        repeat(9) { assertHighlighted(it, it < 3) }
        compose.onNodeWithTag("pod-grid-grid-0-stage0-slot-0").assert(
            SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Unselected"))
        // The earlier board lines up with the arrowed one, column by column.
        val earlier = compose.onNodeWithTag("pod-grid-grid-0-stage0-slot-4").fetchSemanticsNode().boundsInRoot
        val current = slot(4).fetchSemanticsNode().boundsInRoot
        assertEquals(current.left, earlier.left)
        assertEquals(current.width, earlier.width)
        // A grid without a refill keeps one board and no labels.
        compose.onAllNodesWithText("After refill").assertCountEquals(1)
        compose.onNodeWithTag("pod-grid-plain-stage0-slot-0").assertDoesNotExist()
    }

    @Test fun pickerColoursUseYourTableColourAndDistinctOthers() {
        val colors = podGridColors(listOf("bob", "alice", "carol"), "alice")
        assertEquals(OwnTableColor, colors["alice"])
        assertEquals(OpponentTableColor, colors["bob"])
        assertEquals(3, colors.values.distinct().size)
    }
}
