package org.asyncttwiz.android

import org.asyncttwiz.protocol.*
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** STK-050: the Inspect tile of an ability on the Stack names what was chosen for it, as far as the viewer may see. */
class StackAbilityInspectTest {
    private val f = TableStatesFixture

    private fun board(targets: List<MtgRef>): TableBoard {
        val source = f.source(f.a, "1/3")
        val item = MtgStackItem(MtgRef.StackItem("entry"), MtgStackKind.Triggered, f.a, targets = mapOf("target" to targets),
            timestamp = 1)
        return tableBoard(f.initial.copy(board = f.initial.board.copy(
            stackEffects = listOf(StackEffect("entry", source.id, item = item)))))
    }

    // STK-050
    @Test fun anAbilitysTileNamesItsTargetsAsTheViewerSeesThem() {
        val blocker = f.source(f.b, "2/3")
        val name = f.initial.definitions.single { it.digest == blocker.definition }.definition.faces
            .single { it.faceId == blocker.faceId }.name
        val hidden = f.initial.board.cards.first { it.containerId == f.id("b/HAND") }
        val names = mapOf(f.b to "Bob")
        assertEquals(listOf("Targets: Bob, $name"),
            StackAbilities.details(board(listOf(MtgRef.Player(f.b), MtgRef.Card(blocker.id, 0))), "entry", f.a, names))
        assertEquals(listOf("Target: You"), StackAbilities.details(board(listOf(MtgRef.Player(f.a))), "entry", f.a, names))
        // A card the viewer can't see is never named
        assertEquals(listOf("Target: a hidden card"),
            StackAbilities.details(board(listOf(MtgRef.Card(hidden.id, 0))), "entry", f.a, names))
        assertTrue(StackAbilities.details(board(emptyList()), "entry", f.a, names).isEmpty())
    }

    // STK-050, TBL-104 (owner 2026-11-06): a spell on the Stack shows what was chosen for it as abilities do: X, then targets
    @Test fun aSpellsChosenXAndTargetsAreListed() {
        val spell = f.source(f.a, "1/3")
        val item = MtgStackItem(MtgRef.Card(spell.id, 0), MtgStackKind.Spell, f.a, targets = mapOf("target" to listOf(MtgRef.Player(f.b))),
            values = mapOf("X" to 3), timestamp = 1)
        val play = tableBoard(f.initial.copy(board = f.initial.board.copy(stackEffects = listOf(StackEffect(spell.id, spell.id, item = item)))))
        assertEquals(listOf("X = 3", "Target: Bob"), StackAbilities.details(play, spell.id, f.a, mapOf(f.b to "Bob")))
    }

    // STK-046: the token's chip reads "T" for a triggered ability, "A" for an activated one
    @Test fun theKindChipSaysTriggeredOrActivated() {
        assertEquals("T", StackAbilityShown(triggered = true, source = "Herald", text = "").letter)
        assertEquals("A", StackAbilityShown(triggered = false, source = "Totem", text = "").letter)
    }
}
