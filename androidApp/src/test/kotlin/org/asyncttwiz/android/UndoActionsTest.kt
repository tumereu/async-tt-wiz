package org.asyncttwiz.android

import org.asyncttwiz.protocol.*
import kotlin.test.*
import org.junit.Test

class UndoActionsTest {
    @Test fun tapAndHoldAreMutuallyExclusiveCancelAndUnavailableHoldNeverUndo() {
        val actions = mutableListOf<UndoIntent>()
        var availability = UndoAvailability(true, true, true)
        val controller = UndoController({ GameScope("game", playId = "play") }, { availability }) { _, intent ->
            actions.add(intent); true
        }
        val gesture = UndoGesture(controller.callbacks)
        gesture.press(); gesture.release()
        assertEquals(listOf(UndoIntent.UNDO), actions)
        gesture.press(); gesture.hold(); gesture.hold(); gesture.release()
        assertEquals(listOf(UndoIntent.UNDO, UndoIntent.REDO), actions)
        gesture.press(); gesture.cancel(); gesture.release()
        assertEquals(2, actions.size)
        availability = availability.copy(canRedo = false)
        gesture.press(); gesture.hold(); gesture.release()
        assertEquals(2, actions.size)
        availability = availability.copy(connected = false)
        gesture.press(); gesture.release()
        assertEquals(2, actions.size)
    }

    @Test fun explicitCallbacksUseTypedCurrentPlayRequestNotCapturedNodeOrLocalStack() {
        var scope: GameScope? = GameScope("game", playId = "play-one")
        val requests = mutableListOf<Pair<GameScope, GameAction>>()
        val controller = UndoController({ scope }, { UndoAvailability(true, true, true) }) { current, intent ->
            requests.add(current to intent.action); true
        }
        controller.callbacks.undo()
        scope = GameScope("game", playId = "play-two")
        controller.callbacks.redo()
        assertEquals(listOf(GameScope("game", playId = "play-one") to GameAction.Undo,
            GameScope("game", playId = "play-two") to GameAction.Redo), requests)
        scope = GameScope("game", preparationId = "preparation")
        assertFalse(controller.request(UndoIntent.UNDO))
        scope = null
        assertFalse(controller.request(UndoIntent.REDO))
        assertEquals(2, requests.size)
    }

    @Test fun readinessIsRecheckedForExplicitAndGestureRoutesWithoutAcknowledgementBarrier() {
        var readiness = UndoAvailability(true, true, true)
        var submissions = 0
        val controller = UndoController({ GameScope("game", playId = "play") }, { readiness }) { _, _ -> submissions++; true }
        repeat(3) { controller.callbacks.undo() }
        assertEquals(3, submissions) // no local busy gate: FIFO assigns latest-own at processing time
        readiness = readiness.copy(connected = false)
        controller.callbacks.undo(); controller.callbacks.redo()
        assertEquals(3, submissions)
        readiness = UndoAvailability(true, false, true)
        controller.callbacks.undo(); controller.callbacks.redo()
        assertEquals(4, submissions)
    }
}
