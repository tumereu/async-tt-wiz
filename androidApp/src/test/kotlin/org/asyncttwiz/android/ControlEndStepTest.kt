package org.asyncttwiz.android

import arcanum.api.TurnStep
import org.asyncttwiz.protocol.game.GameDevice
import org.asyncttwiz.protocol.game.ResponseMode
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Owner bug 2026-11-07: a player in Control couldn't stop at their own end step. In Control the step arrow in their second
 * main phase reads "End step" and its tap stops at their end step, where they hold priority; holding it is still End turn;
 * Normal and Quick keep "End turn" there. Alice's table on the simulated server ([EngineTableKit]), Bob away with her
 * phone assuming his passes.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w412dp-h892dp-port-mdpi")
class ControlEndStepTest {
    private val kit = EngineTableKit()
    private val toolkit = kit.toolkit

    @After fun forget() = AutomatumBoards.reset()

    /** The step arrow Alice's table shows now, with her response mode ([EngineTableRoutes.control]). */
    private fun arrow(): AutomatumStep = AutomatumStep.of(kit.alice.play(), kit.routes.control())

    private fun tapArrow() = assertTrue(toolkit.automatumStep(TableActionIntent.Next(kit.scope)), "the step arrow's tap: ${toolkit.error}")

    /** Alice holds priority with the Stack empty in her own [step]. */
    private fun aliceHolds(step: TurnStep): Boolean = kit.state().let { s ->
        kit.alice.device.status == GameDevice.Status.YourMove && s.turn.active == kit.me && s.turn.step == step &&
            s.stack.isEmpty() && s.priority.holder == kit.me
    }

    /** Both keep, Alice's phone set to [mode] (assuming Bob's passes, he is away), then on to her second main phase with
     * the step arrow's taps (To combat, Next, No attack: Control stops on the way, CMB-047). */
    private fun toMain2(mode: ResponseMode) {
        kit.opening()
        kit.alice.settings(kit.alice.shown!!.settings.copy(mode = mode, assume = org.asyncttwiz.protocol.game.AssumeResolves.Always))
        kit.scenario.online[kit.f.b] = false
        kit.settle(bobActs = false)
        assertEquals(mode == ResponseMode.FullControl, kit.routes.control())
        repeat(20) {
            if (aliceHolds(TurnStep.PostcombatMain)) return
            assertEquals(kit.me, kit.state().turn.active, "still Alice's turn on the way to her second main phase")
            if (kit.routes.arrowShown()) tapArrow()
            kit.settle(bobActs = false)
        }
        fail("Alice's second main phase: ${kit.state().turn}; ${kit.alice.device.status}")
    }

    // TRN-063, STK-036 (owner bug 2026-11-07): in Control the second main phase's arrow reads "End step"; its tap stops at
    // Alice's own end step with her holding priority, where the arrow reads "End turn" and its tap hands the turn over
    @Test fun controlTapsFromTheSecondMainPhaseToTheEndStep() {
        toMain2(ResponseMode.FullControl)
        assertEquals(AutomatumStep.ToEndStep, arrow())
        assertEquals("End step", arrow().label)
        tapArrow()
        kit.settle(bobActs = false)
        assertTrue(aliceHolds(TurnStep.End), "Control stops at Alice's own end step: ${kit.state().turn}; ${kit.alice.device.status}")
        assertEquals(AutomatumStep.EndTurn, arrow())
        assertEquals("End turn", arrow().label)
        tapArrow()
        kit.settle(bobActs = false)
        assertEquals(kit.other, kit.state().turn.active, "the end step's tap hands the turn over")
    }

    // TRN-017, TRN-057: holding the arrow in Control's second main phase is still End turn, past Alice's end step
    @Test fun holdingTheArrowInControlStillEndsTheTurn() {
        toMain2(ResponseMode.FullControl)
        assertTrue(toolkit.automatumStep(TableActionIntent.End(kit.scope)), "End turn")
        kit.settle(bobActs = false)
        assertEquals(kit.other, kit.state().turn.active, "the turn ended without stopping at Alice's end step")
    }

    // TRN-063, TRN-057: in Normal the second main phase's arrow reads "End turn" and its tap ends the turn, as before
    @Test fun normalTapsEndTheTurnFromTheSecondMainPhase() = endsTurnFromMain2(ResponseMode.Normal)

    // TRN-063, TRN-057: in Quick the second main phase's arrow reads "End turn" and its tap ends the turn, as before
    @Test fun quickTapsEndTheTurnFromTheSecondMainPhase() = endsTurnFromMain2(ResponseMode.AutoSkip)

    private fun endsTurnFromMain2(mode: ResponseMode) {
        toMain2(mode)
        assertEquals(AutomatumStep.EndTurn, arrow())
        assertEquals("End turn", arrow().label)
        tapArrow()
        kit.settle(bobActs = false)
        assertEquals(kit.other, kit.state().turn.active, "the tap ended Alice's turn")
    }
}
