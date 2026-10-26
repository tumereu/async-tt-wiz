package org.asyncttwiz.android

import org.asyncttwiz.protocol.*
import kotlin.test.*
import org.junit.Test

internal fun manualId(n: Int) = "00000000-0000-4000-8000-${n.toString().padStart(12, '0')}"
internal fun manualState(): PlayState {
    val definition = CardDefinition("Secret front", listOf(DefinitionFace(manualId(11), "Secret front", listOf(manualId(12))),
        DefinitionFace(manualId(12), "Secret back", listOf(manualId(11)))))
    return PlayState(manualId(1), manualId(2), manualId(3), manualId(4), PlayRoot(manualId(5), manualId(6)), manualId(5),
        BoardState(listOf(PlayPlayer(manualId(7)), PlayPlayer(manualId(8))),
            listOf(PlayCard(manualId(9), "definition", manualId(8), manualId(10), manualId(11), corner = "2/3", explicitlyCovered = true),
                PlayCard(manualId(13), "definition", manualId(7), manualId(10), manualId(11), corner = "flying"),
                PlayCard(manualId(14), "definition", manualId(7), manualId(10), manualId(11), corner = "4")),
            listOf(PlayContainer(manualId(10), PlayZone.BATTLEFIELD, manualId(7), listOf(manualId(9), manualId(13), manualId(14)))),
            TurnState(manualId(7))), listOf(SubmittedDefinition("definition", definition)), emptyList(),
        InitialStarter(StarterChoice(manualId(7)), manualId(7)))
}

class ManualActionsTest {
    @Test fun projectionNoteDraftTargetsOccurrencesInsteadOfTheirSourceAndExpiresWithThem() {
        val original = manualState()
        val effect = StackEffect(manualId(80), manualId(9), note = "X=3")
        val state = original.copy(board = original.board.copy(stackEffects = listOf(effect)))
        val ui = ManualActions()
        ui.cards(state, manualId(9), listOf(manualId(9)), listOf(effect.id))
        ui.edit(ManualValue(ManualValueKind.NOTE), state, manualId(7))
        assertEquals("X=3", ui.editor!!.text)
        ui.editor!!.text = "target opponent"
        val sent = mutableListOf<PlayAction>()
        assertTrue(ui.submitValue(state, manualId(7)) { sent += it; true })
        assertEquals(listOf<PlayAction>(PlayAction.SetProjectionNote(effect.id, "target opponent")), sent)
        ui.projectionNote(state, effect.id, listOf(effect.id), manualId(7))
        assertTrue(ui.scope!!.usable(state))
        assertFalse(ui.scope!!.usable(original))
        assertEquals("", original.board.cards.first().note)
    }
    @Test fun contextCapturesSelectionWithoutLaterReflowOrReplacement() {
        val state = manualState()
        val ui = ManualActions()
        ui.cards(state, manualId(9), listOf(manualId(9), manualId(13), manualId(9)))
        val scope = ui.scope!!
        assertEquals(listOf(manualId(9), manualId(13)), scope.targetIds)
        val reordered = state.copy(board = state.board.copy(cards = state.board.cards.reversed()))
        assertEquals(listOf(manualId(9), manualId(13)), scope.cardActions(reordered) { PlayAction.ResetCorner(it.id) }
            .map { (it as PlayAction.ResetCorner).cardId })
        ui.cards(state, manualId(14), listOf(manualId(9), manualId(13)))
        assertEquals(listOf(manualId(14)), ui.scope!!.targetIds)
        val disappeared = state.copy(board = state.board.copy(cards = state.board.cards.filter { it.id != manualId(14) }))
        assertFalse(ui.scope!!.usable(disappeared))
        assertTrue(ui.scope!!.cardActions(disappeared) { PlayAction.ResetCorner(it.id) }.isEmpty())
        assertFalse(scope.usable(state.copy(id = manualId(99))))
    }

    @Test fun repeatedEditsRetainScopeAndRecomputeCurrentPreviewAndEligibility() {
        val original = manualState()
        val ui = ManualActions()
        ui.cards(original, manualId(9), listOf(manualId(9), manualId(13)))
        val scope = ui.scope!!
        val applicable = scope.cardActions(original) {
            if (ManualText.corner(it.corner, CornerPart.BOTH, 1) != null) PlayAction.AdjustCorner(it.id, CornerPart.BOTH, 1) else null
        }
        assertEquals(listOf(PlayAction.AdjustCorner(manualId(9), CornerPart.BOTH, 1)), applicable)
        val updated = original.copy(board = original.board.copy(cards = original.board.cards.map {
            if (it.id == manualId(9)) it.copy(corner = "8/9", owner = manualId(7), counters = mapOf(CounterColor.BLUE to "3")) else it
        }))
        assertSame(scope, ui.scope)
        assertEquals("8/9", scope.value(updated, ManualValue(ManualValueKind.CORNER), manualId(7)))
        assertEquals("3", scope.value(updated, ManualValue(ManualValueKind.COUNTER, CounterColor.BLUE), manualId(7)))
    }

    @Test fun localInspectionIsRegionBasedAndNeverSubmitsOrLeaksOtherSide() {
        val state = manualState()
        val ui = ManualActions()
        ui.cards(state, manualId(9), emptyList())
        ui.inspect(state, manualId(8)) // owner, but NOT own region
        assertTrue(ui.inspection!!.faceIds.isEmpty())
        ui.inspect(state, manualId(7))
        assertEquals(listOf(manualId(11), manualId(12)), ui.inspection!!.faceIds)
        val hidden = GameplayDisclosure(manualId(9), manualId(12), manualId(10), listOf(manualId(8)))
        ui.closeInspection()
        ui.inspected(state, manualId(7), hidden)
        assertNull(ui.inspection)
        ui.inspected(state, manualId(8), hidden)
        assertEquals(listOf(manualId(12)), ui.inspection!!.faceIds)
        assertTrue(ui.inspection!!.explicit)
        ui.clear()
        assertNull(ui.scope)
        assertNull(ui.inspection)
    }

    @Test fun productionButtonArbitratesClickHoldTooltipMovementAndCancel() {
        val press = ActionPress()
        var holds = 0
        press.start()
        assertTrue(press.release())
        assertFalse(press.release())
        press.start()
        press.hold(null)
        assertTrue(press.tooltip)
        assertFalse(press.release())
        assertFalse(press.tooltip)
        press.start()
        press.hold { holds++ }
        press.hold { holds++ }
        assertEquals(1, holds)
        assertFalse(press.tooltip)
        assertFalse(press.release())
        press.start()
        press.cancel()
        press.hold { holds++ }
        assertFalse(press.release())
        assertEquals(1, holds)
    }

    @Test fun emptyZeroAndDashArePresentationOnly() {
        val state = manualState()
        val ui = ManualActions()
        ui.player(state, manualId(7))
        val field = ManualValue(ManualValueKind.COUNTER, CounterColor.GRAY)
        assertNull(ui.scope!!.value(state, field, manualId(7)))
        assertEquals("", ManualText.playerCounterText(null))
        assertEquals("–", ManualText.cardCounterText(null))
        assertEquals("0", ManualText.playerCounterText("0"))
        assertTrue(state.board.players.first().counters.isEmpty())
    }
}
