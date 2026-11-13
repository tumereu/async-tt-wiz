package org.asyncttwiz.automatum.boundary

import arcanum.engine.input.Block
import arcanum.engine.input.Input
import arcanum.engine.state.CardId
import arcanum.engine.state.ObjectId
import arcanum.engine.state.PlayerId
import org.asyncttwiz.protocol.game.GameDevice
import org.asyncttwiz.protocol.game.PlaySettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Web client step 4c part 3 (docs/specs/web-client.md §1 item 3): the device's requests and answers across the engine
 * boundary ([DeviceMessage]) and the page's ledger of a game's requests ([DeviceCalls]): every request and answer comes
 * back the same from its JSON string, device messages are told apart from the planning ones on the shared channel, answers
 * reach their callbacks in order, and a closed game's calls end with their fallback while later answers are dropped.
 */
class DeviceMessagesTest {
    private val logged = mutableListOf<Pair<Boolean, String>>()
    private val log = EngineLog { warn, text -> logged += warn to text }

    private fun card(n: Int) = ObjectId.Card(CardId("c$n"), 0)

    @Test fun requestsAndAnswersComeBackTheSameFromTheirText() {
        val requests = listOf(
            DeviceRequest.Pass(1, 7),
            DeviceRequest.Act(2, null, Input.PassPriority(PlayerId("alice"))),
            DeviceRequest.Assume(3, 9, listOf(Block(card(1), card(2)))),
            DeviceRequest.Send(4, 9, listOf(Input.PassPriority(PlayerId("bob")))),
            DeviceRequest.Settings(5, PlaySettings()),
            DeviceRequest.Undo(6, 10, "event-1"),
            DeviceRequest.Tick(7, 1_000),
            DeviceRequest.DismissCombatHold(8, GameDevice.CombatHold("e", GameDevice.CombatMoment.Damage), "its time"),
            DeviceRequest.Close(9))
        for (r in requests) {
            val text = DeviceMessage.request("game-1", r)
            assertTrue(DeviceMessage.isDevice(text), text)
            val back = DeviceMessage.decode(text)
            assertEquals("game-1", back.game)
            assertEquals(r, back.request)
        }
        val answers = listOf(
            DeviceAnswer.Done(1, DeviceResult.Acted(ActedWire.Refused("stale"))),
            DeviceAnswer.Done(2, DeviceResult.Undo(GameDevice.Undo.Free("e"))),
            DeviceAnswer.Done(3, DeviceResult.Text(null)),
            DeviceAnswer.Failed(4, "IllegalStateException: no"),
            DeviceAnswer.Log(listOf(LogLine(4, "engine", "a line"))),
            DeviceAnswer.LoadFailed)
        for (a in answers) assertEquals(a, DeviceMessage.decode(DeviceMessage.answer("game-1", a)).answer)
    }

    // The planning requests share the worker's channel: neither side takes one for the other's
    @Test fun deviceMessagesAreToldApartFromPlanningOnes() {
        val planning = EngineMessages.encode(EngineRequest.HandPlaysOf(1, "game-1", 1, "alice"))
        assertFalse(DeviceMessage.isDevice(planning))
        assertTrue(DeviceMessage.isDevice(DeviceMessage.answer("", DeviceAnswer.Log(emptyList()))))
    }

    @Test fun answersReachTheirCallbacksInOrder() {
        val calls = DeviceCalls(log)
        val got = mutableListOf<String>()
        val first = calls.next(); calls.ask(first, "pass", { DeviceResult.None }) { got += "pass $it" }
        val second = calls.next(); calls.ask(second, "take", { DeviceResult.None }) { got += "take $it" }
        assertEquals(2, calls.awaited)
        assertTrue(calls.done(first, DeviceResult.Flag(true)))
        assertTrue(calls.done(second, DeviceResult.Text("x")))
        assertEquals(listOf("pass ${DeviceResult.Flag(true)}", "take ${DeviceResult.Text("x")}"), got)
        assertTrue(logged.none { it.first }, "nothing out of order: $logged")
        // An answer nothing waits for is logged, not run
        assertFalse(calls.done(first, DeviceResult.None))
        assertTrue(logged.any { it.first && "nothing waits" in it.second })
    }

    @Test fun anAnswerSkippingOlderRequestsIsLoggedAndTheOlderStillWait() {
        val calls = DeviceCalls(log)
        val a = calls.next(); calls.ask(a, "pass", { DeviceResult.None }) {}
        val b = calls.next(); calls.ask(b, "undo", { DeviceResult.None }) {}
        calls.done(b, DeviceResult.None)
        assertTrue(logged.any { it.first && "before older requests $a" in it.second }, "$logged")
        assertEquals(1, calls.awaited)
    }

    @Test fun aFailedRequestGetsItsFallback() {
        val calls = DeviceCalls(log)
        var got: DeviceResult? = null
        val id = calls.next()
        calls.ask(id, "pass", { DeviceResult.Acted(ActedWire.Refused("failed")) }) { got = it }
        calls.failed(id, "boom")
        assertEquals(DeviceResult.Acted(ActedWire.Refused("failed")), got)
    }

    // A game closed on the page (or its worker lost): every waiting call ends with its fallback, later answers are dropped
    // and later asks end at once
    @Test fun aClosedGamesCallsEndAndItsLateAnswersAreDropped() {
        val calls = DeviceCalls(log)
        val got = mutableListOf<DeviceResult>()
        val id = calls.next()
        calls.ask(id, "endTurn", { DeviceResult.Flag(false) }) { got += it }
        calls.close("closed on the page")
        assertEquals(listOf<DeviceResult>(DeviceResult.Flag(false)), got)
        assertFalse(calls.done(id, DeviceResult.None))
        assertTrue(logged.any { !it.first && "dropped" in it.second })
        calls.ask(calls.next(), "pass", { DeviceResult.Flag(false) }) { got += it }
        assertEquals(2, got.size)
        assertEquals(0, calls.awaited)
    }
}
