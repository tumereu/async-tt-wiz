package org.asyncttwiz.android

import org.asyncttwiz.protocol.*
import kotlin.test.*
import org.junit.Test

class ValueEditorTest {
    @Test fun typingAndCancelNeverPublishAndSyncDoesNotOverwriteDraft() {
        val state = manualState()
        val ui = ManualActions()
        ui.cards(state, manualId(9), emptyList())
        val field = ManualValue(ManualValueKind.CORNER)
        ui.edit(field, state, manualId(7))
        assertEquals("2/3", ui.editor!!.text)
        ui.editor!!.text = ""
        val synced = state.copy(board = state.board.copy(cards = state.board.cards.map {
            if (it.id == manualId(9)) it.copy(corner = "9/9") else it
        }))
        assertEquals("9/9", ui.scope!!.value(synced, field, manualId(7)))
        assertEquals("", ui.editor!!.text)
        ui.cancelEditor()
        assertNull(ui.editor)
        assertNotNull(ui.scope)
        assertEquals("2/3", state.board.cards.first().corner)
    }

    @Test fun completedSetUsesCapturedIdsKeepsMenuAndSendsOneRequestPerSource() {
        val state = manualState()
        val ui = ManualActions()
        ui.cards(state, manualId(9), listOf(manualId(9), manualId(13)))
        val scope = ui.scope
        ui.edit(ManualValue(ManualValueKind.NOTE), state, manualId(7))
        ui.editor!!.text = "  multiline\n note  "
        val requests = mutableListOf<PlayAction>()
        assertTrue(ui.submitValue(state, manualId(7)) { requests.add(it) })
        assertEquals(listOf<PlayAction>(PlayAction.SetNote(manualId(9), "  multiline\n note  "),
            PlayAction.SetNote(manualId(13), "  multiline\n note  ")), requests)
        assertSame(scope, ui.scope)
        assertNull(ui.editor)
    }

    @Test fun failedReceiptRetainsInputAndNoHiddenInspectionIsInferredFromReceipt() {
        val state = manualState()
        val ui = ManualActions()
        ui.cards(state, manualId(9), emptyList())
        ui.edit(ManualValue(ManualValueKind.COUNTER, CounterColor.BLUE), state, manualId(7))
        ui.editor!!.text = "0"
        assertFalse(ui.submitValue(state, manualId(7)) { false })
        assertEquals("0", ui.editor!!.text)
        assertNull(ui.inspection)
    }

    @Test fun lostContextOrIncarnationNeverTargetsReplacementAndInvalidInputCannotSubmit() {
        val state = manualState()
        val ui = ManualActions()
        ui.cards(state, manualId(9), emptyList())
        ui.edit(ManualValue(ManualValueKind.CORNER), state, manualId(7))
        var requests = 0
        ui.editor!!.text = "\u0000"
        assertFalse(ui.submitValue(state, manualId(7)) { requests++; true })
        ui.editor!!.text = "new"
        assertFalse(ui.submitValue(state.copy(id = manualId(99)), manualId(7)) { requests++; true })
        val missing = state.copy(board = state.board.copy(cards = state.board.cards.drop(1)))
        assertFalse(ui.submitValue(missing, manualId(7)) { requests++; true })
        assertEquals(0, requests)
    }

    @Test fun lifeAndAbsentCounterDraftsRemainDistinct() {
        val state = manualState()
        val ui = ManualActions()
        ui.player(state, manualId(8))
        ui.edit(ManualValue(ManualValueKind.LIFE), state, manualId(7))
        assertEquals("20", ui.editor!!.text)
        ui.editor!!.text = "-2"
        val requests = mutableListOf<PlayAction>()
        assertTrue(ui.submitValue(state, manualId(7)) { requests.add(it) })
        assertEquals(listOf<PlayAction>(PlayAction.SetLife(manualId(8), "-2")), requests)
        ui.edit(ManualValue(ManualValueKind.COUNTER, CounterColor.RED), state, manualId(7))
        assertEquals("", ui.editor!!.text)
        assertTrue(ui.submitValue(state, manualId(7)) { requests.add(it) })
        assertEquals(PlayAction.SetCounter(CounterTarget.PLAYER, manualId(8), CounterColor.RED, ""), requests.last())
    }
}
