package org.asyncttwiz.android

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import org.asyncttwiz.protocol.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals

/**
 * Pivot review #11 (I-D52): screen behaviour whose every test went with the manual table, tested again as the app is now.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w412dp-h892dp-port-mdpi")
class PivotCoverageScreenTest {
    @get:Rule val compose = createComposeRule()
    private val f = TableStatesFixture

    // SYS-046: a value control is "− [value] +": the value between two icon buttons, which stop at the limits
    @Test fun aValueControlIsMinusValuePlus() {
        var value by mutableIntStateOf(1)
        compose.setContent { MaterialTheme { Stepper(value, 0, 2) { value = it } } }
        compose.onNodeWithContentDescription("Value 1").assertIsDisplayed()
        compose.onNodeWithContentDescription("One more").performClick()
        compose.onNodeWithContentDescription("Value 2").assertIsDisplayed()
        compose.onNodeWithContentDescription("One more").assertIsNotEnabled()
        compose.onNodeWithContentDescription("One less").performClick().performClick()
        compose.onNodeWithContentDescription("Value 0").assertIsDisplayed()
        compose.onNodeWithContentDescription("One less").assertIsNotEnabled()
        assertEquals(0, value)
    }

    // STK-035 (I-D52): Automatum resolves the Stack: the Stack list shows its entries and nothing to move or mark them
    @Test fun theStackListHasNoButtons() {
        val state = f.combat()
        compose.setContent { MaterialTheme { ManageStackDialog(tableBoard(state), f.a, {}) } }
        compose.onNodeWithText("Stack · 2").assertIsDisplayed()
        for (gone in listOf("Put to battlefield", "Put to graveyard", "Put to exile", "Remove from Stack", "Mark text", "Resolve"))
            compose.onAllNodes(hasText(gone, substring = true) or hasContentDescription(gone, substring = true)).assertCountEquals(0)
    }

    // ZON-033, TBL-070, SYS-040 (I-D52): a graveyard's menu has one cell, Search, shown (greyed) also while the pile is
    // empty; nothing in it moves the pile's cards
    @Test fun anEmptyPilesMenuShowsSearchGreyed() {
        val graveyard = f.initial.board.containers.first { it.zone == PlayZone.GRAVEYARD && it.accountId == f.a }
        val play = f.initial.copy(board = f.initial.board.copy(cards = f.initial.board.cards.filter { it.id !in graveyard.cards },
            containers = f.initial.board.containers.map { if (it.id == graveyard.id) it.copy(cards = emptyList()) else it }))
        val toolkit = TableToolkitController(f.a, { tableBoard(play) }, { true }, { _, _ -> true })
        toolkit.openPileMenu(graveyard.id)
        compose.setContent { MaterialTheme { Box(Modifier.fillMaxSize()) {
            TableSurface(f.game, tableBoard(play), f.a, false, Modifier.fillMaxSize(), connected = true, onIntent = toolkit::handle, toolkit = toolkit,
                runtime = TableRuntimeHooks(navigation = {}, chat = {}, undo = { true }, controlsConnected = true))
        } } }
        compose.onNodeWithContentDescription("Search").assertExists().assertIsNotEnabled()
        for (gone in listOf("Shuffle", "Move", "Put", "Exile all"))
            compose.onAllNodes(hasContentDescription(gone, substring = true)).assertCountEquals(0)
    }

    // ZON-038, ZON-035: a graveyard's search view opens from its menu's Search (or a double-tap) and closes on ✕ and when
    // its pile empties
    @Test fun aPileViewClosesOnItsCrossAndWhenThePileEmpties() {
        val graveyard = f.initial.board.containers.first { it.zone == PlayZone.GRAVEYARD && it.accountId == f.a && it.cards.isNotEmpty() }
        val emptied = f.initial.copy(board = f.initial.board.copy(cards = f.initial.board.cards.filter { it.id !in graveyard.cards },
            containers = f.initial.board.containers.map { if (it.id == graveyard.id) it.copy(cards = emptyList()) else it }))
        var play by androidx.compose.runtime.mutableStateOf(f.initial)
        val toolkit = TableToolkitController(f.a, { tableBoard(play) }, { true }, { _, _ -> true })
        compose.setContent { MaterialTheme { Box(Modifier.fillMaxSize()) {
            TableSurface(f.game, tableBoard(play), f.a, false, Modifier.fillMaxSize(), connected = true, onIntent = toolkit::handle, toolkit = toolkit,
                runtime = TableRuntimeHooks(navigation = {}, chat = {}, undo = { true }, controlsConnected = true))
        } } }
        fun close() = compose.onAllNodes(hasContentDescription("Close", substring = true) and hasContentDescription("panel", substring = true))
        compose.runOnIdle { toolkit.openPublicPile(graveyard.id, toggle = false) }
        close().assertCountEquals(1)
        close().onFirst().performClick()
        close().assertCountEquals(0)
        compose.runOnIdle { toolkit.openPublicPile(graveyard.id, toggle = false) }
        close().assertCountEquals(1)
        compose.runOnIdle { play = emptied }
        close().assertCountEquals(0)
        compose.runOnIdle { assertEquals(null, toolkit.layoutState.value.localPileId) }
    }
}
