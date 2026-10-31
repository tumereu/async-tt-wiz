package org.asyncttwiz.android

import org.asyncttwiz.protocol.*
import org.junit.Test
import kotlin.test.*

class StackManageActionsTest {
    private val f = TableStatesFixture

    // STK-019, STK-021
    @Test fun fiveIdentityBoundControlsHaveNoReorderOrSourceOwnerAction() {
        val state = f.combat()
        val id = state.board.stackEffects.first().id
        val intents = mutableListOf<TableIntent>()
        val controls = StackMenuActions.controls(state, id, true, intents::add)
        assertEquals(listOf("Remove from Stack", "Put to battlefield", "Put to graveyard", "Copy", "Change controller"), controls.map { it.name })
        assertTrue(controls.all { it.enabled })
        controls.forEach { it.click() }
        assertEquals(listOf(TableIntent.Submit(StackMenuActions.remove(id), null), TableIntent.StackDestination(id, PlayZone.BATTLEFIELD),
            TableIntent.StackDestination(id, PlayZone.GRAVEYARD), TableIntent.Submit(StackMenuActions.copy(state, id), null),
            TableIntent.Submit(StackMenuActions.changeController(state, id), null)), intents)
        val controller = (intents.last() as TableIntent.Submit).action as PlayAction.Stack
        val action = assertIs<StackAction.SetController>(controller.action)
        assertEquals(state.board.players.single { it.accountId != CombatStackRules.controller(state.board, id) }.accountId,
            action.accountId)
        assertTrue(StackMenuActions.controls(state, id, false, {}).none { it.enabled })
    }

    // STK-019
    @Test fun copiesDisableRealDestinationsAndRealCardsDisableProjectionOnlyEdits() {
        val state = f.combat()
        val id = state.board.stackEffects.first().id
        val copy = state.copy(board = state.board.copy(stackEffects = state.board.stackEffects.map { it.copy(isCopy = true) }))
        assertEquals(listOf(true, false, false, true, true), StackMenuActions.controls(copy, id, true, {}).map { it.enabled })
        assertFailsWith<IllegalArgumentException> { StackMenuActions.destination(copy, id, PlayZone.BATTLEFIELD) }
        val real = state.board.cards.first().id
        assertEquals(listOf(false, true, true, true, false), StackMenuActions.controls(state, real, true, {}).map { it.enabled })
    }
}
