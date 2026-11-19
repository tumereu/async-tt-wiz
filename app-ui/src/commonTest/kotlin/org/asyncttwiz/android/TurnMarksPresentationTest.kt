package org.asyncttwiz.android

import org.asyncttwiz.protocol.*
import kotlin.test.Test
import kotlin.test.*

class TurnMarksPresentationTest {
    // TBL-024
    @Test fun damageFillIsOnlyANumericEffectiveToughnessHint() {
        assertFalse(damageIsLethal("2", "3/3"))
        assertTrue(damageIsLethal("3", "3/3"))
        assertTrue(damageIsLethal("4", "3/3"))
        assertFalse(damageIsLethal("3", "2/*"))
        assertFalse(damageIsLethal("", "2/3"))
        assertFalse(damageIsLethal("x", "2/3"))
        assertTrue(damageIsLethal("9999999999999999999999", "2/9999999999999999999998"))
    }

    // TBL-029, TBL-030
    @Test fun tablePresentationCarriesEffectiveValuesAndDoesNotLeakCoveredMarks() {
        val f = TableStatesFixture
        val initial = f.combat()
        val source = f.source(f.a, "1/3")
        val state = initial.copy(board = initial.board.copy(cards = initial.board.cards.map {
            if (it.id == source.id) it.copy(damage = "2", eotNote = "can't block") else it
        }))
        val item = TablePresentation.scene(f.game, tableBoard(state), f.a, 412f, 892f, false).items.single { it.cardId == source.id }
        assertEquals("1/3", item.corner); assertEquals("2", item.damage)
        val covered = state.copy(board = state.board.copy(cards = state.board.cards.map {
            if (it.id == source.id) it.copy(covered = true, explicitlyCovered = true) else it
        }))
        // Rules v23: a face-down Battlefield card's marks are public; only its face is covered.
        val faceDown = TablePresentation.scene(f.game, tableBoard(covered), f.a, 412f, 892f, false).items.single { it.cardId == source.id }
        assertTrue(faceDown.covered)
        assertEquals("2", faceDown.damage); assertEquals("1/3", faceDown.corner)
        // A card whose face is truly hidden (here: in the opponent's Hand) still leaks no marks.
        val stack = covered.board.containers.single { it.zone == PlayZone.HAND && it.accountId != f.a }
        val onStack = covered.copy(board = covered.board.copy(cards = covered.board.cards.map {
            if (it.id == source.id) it.copy(containerId = stack.id, placement = null) else it },
            containers = covered.board.containers.map { when {
                it.id == stack.id -> it.copy(cards = it.cards + source.id)
                source.id in it.cards -> it.copy(cards = it.cards - source.id)
                else -> it } }))
        assertTrue(cardValuesHidden(onStack.board, onStack.board.cards.single { it.id == source.id }, f.a))
        val hidden = TablePresentation.scene(f.game, tableBoard(onStack), f.a, 412f, 892f, false).items.single { it.cardId == source.id }
        assertEquals("", hidden.damage); assertEquals("", hidden.corner)
    }
}
