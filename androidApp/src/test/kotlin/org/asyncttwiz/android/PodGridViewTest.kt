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
        onConfirm: (String, GridLineType, Int) -> Unit = { _, _, _ -> }, onBuild: () -> Unit = {}) {
        compose.setContent { ScreenTheme {
            PodGridView(view(), 18, definitions, listOf(alice, bob), busy(), onConfirm, onBuild)
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
        compose.onNodeWithText("Grid 1/18 · +0 waiting").assertIsDisplayed()
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

    @Test fun headGridIsShownWithOtherActionableGridsCountAndBusyDisablesActions() {
        var busy by mutableStateOf(false)
        val later = board.copy(id = "later", number = 2)
        show(view = { initial.copy(publicView = initial.publicView.copy(grids = listOf(board, later))) }, busy = { busy })
        compose.onNodeWithText("Grid 1/18 · +1 waiting").assertIsDisplayed()
        compose.onNodeWithTag("pod-grid-later-slot-0").assertDoesNotExist()
        compose.onNodeWithContentDescription("Select row 1").performClick()
        compose.runOnIdle { busy = true }
        compose.onNodeWithText("Confirming…").assertIsNotEnabled()
        compose.onNodeWithContentDescription("Select row 2").assertIsNotEnabled()
        assertHighlighted(3, false)
    }

    @Test fun waitingShowsAllOpenBoardsReadOnlyAndBuildShortcutAndStillInspects() {
        val waitingBoard = board.copy(currentPlayer = "bob")
        var builds = 0
        show(view = { initial.copy(publicView = initial.publicView.copy(grids = listOf(waitingBoard, waitingBoard.copy(id = "later", number = 2))),
            mine = mine.copy(headGridId = null)) }, onBuild = { builds++ })
        compose.onAllNodesWithText("Waiting for Bob").onFirst().assertIsDisplayed()
        compose.onAllNodesWithContentDescription("Select row 1").onFirst().assertIsNotEnabled()
        compose.onAllNodesWithContentDescription("Select column 2").onFirst().assertIsNotEnabled()
        compose.onNodeWithTag("pod-grid-later-slot-0").assertExists()
        compose.onNodeWithText("Confirm").assertDoesNotExist()
        compose.onNodeWithText("Build").performClick()
        assertEquals(1, builds)
        slot(0).onChildren().filterToOne(hasClickAction()).performTouchInput { longClick() }
        compose.onNodeWithContentDescription("Close card inspection").assertIsDisplayed().performClick()
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

    @Test fun logShowsLatestSelectionAndExpandsOnlyThisGridsEarlierSelections() {
        val first = GridPublicPickLog("bob", "grid-0", GridLine(GridLineType.ROW, 1), cards.take(3), emptyList())
        val latest = GridPublicPickLog("alice", "grid-0", GridLine(GridLineType.COLUMN, 0), cards.drop(3).take(2), emptyList())
        show(view = { initial.copy(publicView = initial.publicView.copy(log = listOf(first, first.copy(gridId = "other"), latest))) })
        compose.onNodeWithText("Alice took column 1: Card 3, Card 4").assertIsDisplayed()
        compose.onNodeWithText("Bob took row 2: Card 0, Card 1, Card 2").assertDoesNotExist()
        compose.onNodeWithContentDescription("Expand grid selections").performClick()
        compose.onAllNodesWithText("Bob took row 2: Card 0, Card 1, Card 2").assertCountEquals(1)
        compose.onNodeWithContentDescription("Collapse grid selections").performClick()
        compose.onNodeWithText("Bob took row 2: Card 0, Card 1, Card 2").assertDoesNotExist()
    }
}
