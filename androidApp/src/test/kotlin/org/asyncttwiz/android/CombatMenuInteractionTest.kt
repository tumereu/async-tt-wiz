package org.asyncttwiz.android

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import org.asyncttwiz.protocol.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.*

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w412dp-h892dp-port-mdpi")
class CombatMenuInteractionTest {
    @get:Rule val compose = createComposeRule()
    private val f = TableStatesFixture
    private val batches = mutableListOf<List<PlayAction>>()
    private fun render(menu: Boolean, targeted: Boolean = false): TableToolkitController {
        val original = f.combat()
        val walker = CardDefinition("Walker", listOf(DefinitionFace(f.id("walkerFace"), "Walker", typeLine = "Planeswalker", loyalty = "4")))
        val definition = SubmittedDefinition(DeckJson.digest(walker), walker)
        val field = original.board.containers.single { it.zone == PlayZone.BATTLEFIELD && it.accountId == f.b }
        val target = PlayCard(f.id("walkerCard"), definition.digest, f.b, field.id, walker.faces.single().faceId,
            covered = false, corner = "4", placement = PlayPlacement(BoardPosition(10, 0), f.id("walkerPile")))
        val state = if (!targeted) original else original.copy(definitions = original.definitions + definition,
            board = original.board.copy(cards = original.board.cards + target,
                containers = original.board.containers.map { if (it.id == field.id) it.copy(cards = it.cards + target.id) else it },
                combat = original.board.combat!!.let { area -> area.copy(pairs = area.pairs.map { it.copy(attackTarget = target.id) }) }))
        val toolkit = TableToolkitController(f.a, { state }, { true }, { _, _ -> error("Expected batch") },
            playBatch = { actions, _ -> batches += actions; true })
        if (menu) toolkit.openCard(TableScope(f.id("attacker1"), listOf(f.id("attacker1"), f.id("attacker2"))))
        compose.setContent {
            MaterialTheme { TableSurface(f.game, state, f.a, false, Modifier.fillMaxSize(), connected = true,
                toolkit = toolkit, onIntent = toolkit::handle) }
        }
        compose.waitForIdle()
        return toolkit
    }

    @Test fun projectionUsesTheOrdinaryPagedGridAndRemovalUsesTheCapturedMultiselection() {
        render(menu = true)
        compose.ringPage(1)
        compose.onNodeWithContentDescription("Set corner").assertExists()
        compose.onNodeWithContentDescription("Attack →").assertExists()
        compose.onNodeWithContentDescription("Remove from combat").performClick()
        val actions = batches.single().map { assertIs<PlayAction.Combat>(it).action }
        assertEquals(setOf(f.id("attacker1"), f.id("attacker2")), actions.map { assertIs<CombatAction.Remove>(it).occurrenceId }.toSet())
    }

    @Test fun attackTargetPickerUsesOneBatchForAllSelectedAttackerPairs() {
        val toolkit = render(menu = true, targeted = true)
        compose.ringPage(1)
        compose.onNodeWithContentDescription("Attack →").performClick()
        compose.onNodeWithText("Bob").performClick()
        assertEquals(2, batches.single().size)
        val targets = batches.single().map { assertIs<CombatAction.SetAttackTarget>(assertIs<PlayAction.Combat>(it).action) }
        assertEquals(setOf(f.id("pair1"), f.id("pair2")), targets.map { it.pairId }.toSet())
        assertTrue(targets.all { it.cardId == null })
        assertNull(toolkit.panel)
    }

    @Test fun selectAttackersVisiblyEnablesTheInteractionModeToggle() {
        val toolkit = render(menu = false)
        compose.runOnIdle {
            toolkit.selectAttackers(TableActionIntent.SelectAttackers(toolkit.table.scope()!!, advance = false))
        }
        compose.onNodeWithContentDescription("Interaction mode").assert(
            SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "select, 0 selected"))
        assertEquals(TableMode.SELECT, toolkit.selectionState.value.mode)
    }

    @Test fun projectionNoteEditsEverySelectedOccurrenceInOneBatch() {
        render(menu = true)
        compose.ringPage(1)
        compose.onNodeWithContentDescription("Edit card note").performClick()
        compose.onNode(hasSetTextAction()).performTextReplacement("combat note")
        compose.onNodeWithText("Set", useUnmergedTree = true).performClick()
        val notes = batches.single().map { assertIs<PlayAction.SetProjectionNote>(it) }
        assertEquals(setOf(f.id("attacker1"), f.id("attacker2")), notes.map { it.occurrenceId }.toSet())
        assertTrue(notes.all { it.value == "combat note" })
    }

    @Test fun projectionCornerEditsEverySelectedSourceInOneBatch() {
        render(menu = true)
        compose.ringPage(1)
        compose.onNodeWithContentDescription("Set corner").performClick()
        compose.onNode(hasSetTextAction()).performTextReplacement("5")
        compose.onNodeWithText("Set", useUnmergedTree = true).performClick()
        val corners = batches.single().map { assertIs<PlayAction.SetCorner>(it) }
        assertEquals(setOf(f.source(f.a, "1/3").id, f.source(f.a, "1/2").id), corners.map { it.cardId }.toSet())
        assertTrue(corners.all { it.value == "5" })
    }
}
