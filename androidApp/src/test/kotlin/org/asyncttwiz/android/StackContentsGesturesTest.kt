package org.asyncttwiz.android

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import org.asyncttwiz.protocol.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w412dp-h892dp-port-mdpi")
class StackContentsGesturesTest {
    @get:Rule val compose = createComposeRule()

    @Test fun portraitRowsOpenAnchoredBubblesDoubleMenuAndHoldInspection() = exercise(false)
    @Test @Config(qualifiers = "w892dp-h412dp-land-mdpi")
    fun landscapeRowsOpenAnchoredBubblesDoubleMenuAndHoldInspection() = exercise(true)

    private fun exercise(landscape: Boolean) {
        val f = TableStatesFixture
        val play = f.combat().let { p ->
            val definitions = p.definitions.associate { old ->
                val definition = old.definition.copy(faces = old.definition.faces.map { it.copy(rulesText = "First paragraph\nSecond paragraph") })
                old.digest to SubmittedDefinition(DeckJson.digest(definition), definition)
            }
            p.copy(definitions = definitions.values.toList(), board = p.board.copy(cards = p.board.cards.map {
                it.copy(definition = definitions.getValue(it.definition).digest)
            }))
        }
        val container = CombatStackRules.stack(play.board)
        val pile = PileContents(container.id, "Stack", container.cards, ordered = true)
        val entry = contentsEntries(play, pile, f.a).last() // Not the table's top entry.
        val actions = mutableListOf<String>()
        val inspections = mutableListOf<String>()
        val intents = mutableListOf<TableIntent>()
        compose.setContent {
            MaterialTheme {
                Box(Modifier.fillMaxSize()) {
                    ContentsScreen(play, pile, f.a, actions::add, inspections::add,
                        Modifier.width(344.dp).height(if (landscape) 220.dp else 480.dp),
                        onStackIntent = { intents += it; true }, stackWritable = true)
                }
            }
        }
        val row = compose.onNodeWithText(entry.location)
        row.performTouchInput { click() }
        compose.mainClock.advanceTimeBy(400)
        compose.onNodeWithContentDescription("Put to battlefield").assertExists()
        compose.onNodeWithContentDescription("Put to graveyard").assertExists()
        compose.onNodeWithContentDescription("Mark text · drag up or down").assertExists()
        assertTrue(actions.isEmpty()); assertTrue(intents.isEmpty())
        val root = compose.onRoot().fetchSemanticsNode().boundsInRoot
        val rowBounds = row.fetchSemanticsNode().boundsInRoot
        val above = compose.onNodeWithContentDescription("Put to battlefield").fetchSemanticsNode().boundsInRoot
        val below = compose.onNodeWithContentDescription("Put to graveyard").fetchSemanticsNode().boundsInRoot
        assertTrue(above.bottom <= rowBounds.top)
        // A bottom-edge row flips the otherwise off-screen graveyard bubble beside the upper one.
        assertTrue(below.top >= rowBounds.bottom || below.bottom <= rowBounds.top)
        assertFalse(above.overlaps(below))
        for (label in listOf("Put to battlefield", "Put to graveyard", "Mark text · drag up or down")) {
            val bounds = compose.onNodeWithContentDescription(label).fetchSemanticsNode().boundsInRoot
            assertTrue(bounds.left >= root.left && bounds.right <= root.right && bounds.top >= root.top && bounds.bottom <= root.bottom)
        }
        compose.onNodeWithContentDescription("Mark text · drag up or down").performTouchInput {
            down(center); moveTo(center + Offset(0f, -28f)); up()
        }
        assertEquals(listOf<TableIntent>(TableIntent.Submit(PlayAction.Stack(StackAction.MarkText(entry.cardId, 0)), null)), intents)
        intents.clear()
        compose.onNodeWithContentDescription("Put to battlefield").performTouchInput { click() }
        assertEquals(listOf<TableIntent>(TableIntent.StackDestination(entry.cardId, PlayZone.BATTLEFIELD)), intents)
        compose.onNodeWithContentDescription("Put to battlefield").assertDoesNotExist()
        row.performTouchInput { doubleClick() }
        assertEquals(listOf(entry.cardId), actions)
        row.performTouchInput { longClick(durationMillis = 600) }
        assertEquals(listOf(entry.cardId), inspections)
        row.performTouchInput { click() }
        compose.mainClock.advanceTimeBy(400)
        compose.onNodeWithContentDescription("Put to battlefield").assertExists()
        compose.onNodeWithText("Search by name").performTouchInput { click(Offset(4f, 4f)) }
        compose.onNodeWithContentDescription("Put to battlefield").assertDoesNotExist()
    }
}
