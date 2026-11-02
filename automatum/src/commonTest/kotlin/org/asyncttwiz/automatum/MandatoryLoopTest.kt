package org.asyncttwiz.automatum

import arcanum.engine.Engine
import arcanum.engine.cards.Cards
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.setup.GameResult
import org.asyncttwiz.protocol.MtgPriority
import org.asyncttwiz.protocol.PlayZone
import org.asyncttwiz.protocol.TurnPosition
import org.asyncttwiz.protocol.TurnState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The host's automatic passes stop on a loop of mandatory actions (CR 104.4b, 732.4) instead of running to their cap. */
class MandatoryLoopTest {
    private val table = AtwTable("loops")
    private val alice get() = table.alice
    private val bob get() = table.bob

    /** Enters and is sacrificed, dies and returns: each lap the same state with new objects. */
    private val revenant = Cards.invent("Restless Revenant", "{1}{B}", "Creature — Spirit",
        "When this creature enters, sacrifice a creature.\nWhen this creature dies, return it to the battlefield under its owner's control.",
        "1/1")

    // TRN-060
    // CR 104.4b, 732.4: after Alice's pass the host passes for both (neither can do anything); the Revenant's triggers
    // bring the game back to the same state, so the batch stops there with the engine's decision done by hand saying the
    // game is a draw (the panel shows it, I-D37); answering it makes the game a draw
    @Test fun automaticPassesStopOnAMandatoryLoop() {
        table.turn = TurnState(alice, "3", TurnPosition.PRECOMBAT_MAIN.phase, TurnPosition.PRECOMBAT_MAIN.step)
        table.priority = MtgPriority(alice)
        table.put(alice, PlayZone.BATTLEFIELD, "Swamp", "Swamp")
        table.put(alice, PlayZone.HAND, revenant)
        table.put(alice, PlayZone.LIBRARY, "Island")
        table.put(bob, PlayZone.LIBRARY, "Island")
        val rt = RoundTrip(table.state(), table.definitions)
        rt.cast(alice, revenant)
        rt.settle()
        val call = rt.run(alice) { StepFlow.pass(it, alice) }
        val manual = assertIs<Decision.Manual>(rt.decision)
        assertTrue("draw" in manual.text, manual.text)
        val passes = call.changes.count { it is arcanum.engine.change.Change.Priority }
        assertTrue(passes < 40, "stopped after a lap or two, not at the cap: $passes priority changes")
        assertNull(Engine.result(rt.engine()), "a draw once answered")
        rt.run(bob) { StepFlow.answer(it, Answer.Done) }
        assertEquals(GameResult.Draw, Engine.result(rt.engine()))
    }
}
