package org.asyncttwiz.automatum

import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import org.asyncttwiz.protocol.MtgAction
import org.asyncttwiz.protocol.MtgPriority
import org.asyncttwiz.protocol.PlayAction
import org.asyncttwiz.protocol.PlayZone
import org.asyncttwiz.protocol.TurnPosition
import org.asyncttwiz.protocol.TurnState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Steps and turns through the engine (integration spec §3 I3): the step button is a priority pass, the host passes for
 * players with nothing to do (I-D18), and the engine's steps, turn-based actions and triggers come back as ATW's step and
 * turn events. Every call is checked by [RoundTrip] (rebuilt state = the engine's own, undo restores the board).
 */
class StepFlowTest {
    private val table = AtwTable("steps")
    private val alice get() = table.alice
    private val bob get() = table.bob

    private fun at(position: TurnPosition, number: Int, player: String = alice) {
        table.turn = TurnState(player, "$number", position.phase, position.step)
        table.priority = MtgPriority(player)
    }

    private fun game() = RoundTrip(table.state(), table.definitions)
    private fun position(rt: RoundTrip) = TurnPosition.of(rt.state.board.turn)
    private fun zone(rt: RoundTrip, card: String) =
        rt.state.board.containers.single { c -> c.id == rt.state.board.cards.single { it.id == card }.containerId }.zone
    private fun card(rt: RoundTrip, id: String) = rt.state.board.cards.single { it.id == id }
    private fun life(rt: RoundTrip, player: String) = rt.state.board.players.single { it.accountId == player }.life
    private fun hand(rt: RoundTrip, player: String) = rt.state.board.containers.single { it.zone == PlayZone.HAND && it.accountId == player }.cards

    // CR 500.2, 502.3, 503.1, 504.1, 603.2: from the opponent's end step a pass runs through their cleanup and Alice's
    // untap, upkeep (her "at the beginning of your upkeep" trigger) and draw, to her main phase, where she can play the
    // land she drew: one call, one batch, the host passing for everyone who had nothing to do (I-D18, I-D22)
    @Test fun passRunsToTheNextPointWhereSomeoneCanAct() {
        at(TurnPosition.END_STEP, 2, bob)
        val (mountain, mite) = table.put(alice, PlayZone.BATTLEFIELD, "Mountain", "Tinkered Mite", tapped = true)
        table.put(alice, PlayZone.BATTLEFIELD, "Gloomblossom Carrier")
        val (drawn) = table.put(alice, PlayZone.LIBRARY, "Mountain", "Island")
        table.put(bob, PlayZone.LIBRARY, "Island", "Island")
        val rt = game()
        rt.run(bob) { assertNull(StepFlow.pass(it, bob)) }
        assertEquals(1, rt.batches.size)
        assertEquals(TurnState(alice, "3", TurnPosition.PRECOMBAT_MAIN.phase, TurnPosition.PRECOMBAT_MAIN.step), rt.state.board.turn)
        assertEquals(MtgPriority(alice), rt.state.board.priority)
        assertTrue(!card(rt, mountain).tapped && !card(rt, mite).tapped, "the untap step untapped her permanents")
        assertEquals("19", life(rt, alice), "the upkeep trigger resolved")
        assertEquals(1, rt.state.board.cards.count { it.token })
        assertEquals(listOf(drawn), hand(rt, alice), "she drew in her draw step")
        val actions = rt.lastPlan!!.actions
        // ATW's events: Bob's pass, the turn change, passed steps, the step she acts in (StepRun, I-D22)
        assertEquals(PlayAction.EndTurn, actions.single { it == PlayAction.EndTurn || it is PlayAction.BeginTurn })
        val steps = actions.filterIsInstance<PlayAction.ChooseStep>()
        assertEquals(listOf(TurnPosition.CLEANUP, TurnPosition.UPKEEP, TurnPosition.DRAW, TurnPosition.PRECOMBAT_MAIN), steps.map { it.position })
        assertEquals(listOf(true, true, true, false), steps.map { it.through })
        // Only Bob's own pass is a visible priority event; the host's passes and priority given are folded
        val priorities = actions.indices.filter { (actions[it] as? PlayAction.Mtg)?.action is MtgAction.SetPriority }
        assertTrue(rt.lastPlan!!.tags[priorities.first()]?.folded != true)
        assertTrue(priorities.drop(1).all { rt.lastPlan!!.tags[it]?.folded == true })
        // The move goes to the turn player with the step she acts in (TRN-014)
        assertEquals(alice, rt.state.board.attentionHolder)
    }

    // CR 103.8a: the starting player skips the draw of their first turn
    @Test fun firstTurnDrawIsSkipped() {
        at(TurnPosition.UPKEEP, 1)
        table.put(alice, PlayZone.HAND, "Mountain")
        table.put(alice, PlayZone.LIBRARY, "Island", "Island")
        table.put(bob, PlayZone.LIBRARY, "Island")
        val rt = game()
        rt.run(alice) { StepFlow.pass(it, alice) }
        assertEquals(TurnPosition.PRECOMBAT_MAIN, position(rt))
        assertEquals(1, hand(rt, alice).size, "no card drawn on the starting player's first turn")
        assertEquals(listOf(TurnPosition.DRAW, TurnPosition.PRECOMBAT_MAIN),
            rt.lastPlan!!.actions.filterIsInstance<PlayAction.ChooseStep>().map { it.position })
    }

    // CR 117.3d, 117.4: a player who can still act isn't passed for: the step button then moves one priority pass on
    @Test fun aPlayerWhoCanActKeepsPriority() {
        at(TurnPosition.UPKEEP, 3)
        table.put(alice, PlayZone.BATTLEFIELD, "Mountain")
        table.put(alice, PlayZone.LIBRARY, "Island", "Island")
        table.put(bob, PlayZone.BATTLEFIELD, "Mountain")
        table.put(bob, PlayZone.HAND, "Storm Spark")
        table.put(bob, PlayZone.LIBRARY, "Island")
        val rt = game()
        rt.run(alice) { StepFlow.pass(it, alice) }
        assertEquals(TurnPosition.UPKEEP, position(rt), "Bob could cast Storm Spark: he holds priority")
        assertEquals(MtgPriority(bob, 1), rt.state.board.priority)
        // The draw step: Alice draws, has nothing to do and is passed for; Bob could still cast
        rt.run(bob) { StepFlow.pass(it, bob) }
        assertEquals(TurnPosition.DRAW, position(rt))
        assertEquals(MtgPriority(bob, 1), rt.state.board.priority)
        assertEquals(1, hand(rt, alice).size)
    }

    // CR 500.7, I-D12: an extra turn is the same player's turn again (BeginTurn), and the turn after it the other's
    @Test fun extraTurn() {
        at(TurnPosition.PRECOMBAT_MAIN, 3)
        table.put(alice, PlayZone.BATTLEFIELD, "Island", "Island")
        table.put(alice, PlayZone.HAND, "Time Stride", "Island", "Island")
        table.put(alice, PlayZone.LIBRARY, "Island", "Island", "Island")
        table.put(bob, PlayZone.LIBRARY, "Island", "Island", "Island")
        table.put(bob, PlayZone.HAND, "Island")
        val rt = game()
        rt.cast(alice, "Time Stride")
        rt.settle()
        rt.run(alice) { StepFlow.pass(it, alice) }
        assertTrue(rt.state.board.stackEffects.isEmpty(), "Time Stride resolved")
        rt.run(alice) { StepFlow.pass(it, alice, StepFlow.Until.EndOfTurn) }
        assertEquals(TurnState(alice, "4", TurnPosition.PRECOMBAT_MAIN.phase, TurnPosition.PRECOMBAT_MAIN.step), rt.state.board.turn)
        assertTrue(rt.lastPlan!!.actions.contains(PlayAction.BeginTurn(alice)))
        rt.run(alice) { StepFlow.pass(it, alice, StepFlow.Until.EndOfTurn) }
        assertEquals(bob, rt.state.board.turn.accountId)
        assertEquals("5", rt.state.board.turn.number)
        assertTrue(rt.lastPlan!!.actions.contains(PlayAction.EndTurn))
    }

    // CR 514.1: the active player discards down to seven in the cleanup step: a decision, answered on the board
    @Test fun cleanupDiscard() {
        at(TurnPosition.END_STEP, 3)
        val hand = table.put(alice, PlayZone.HAND, *Array(9) { "Storm Spark" })
        table.put(alice, PlayZone.LIBRARY, "Island")
        table.put(bob, PlayZone.LIBRARY, "Island", "Island")
        table.put(bob, PlayZone.HAND, "Island")
        val rt = game()
        rt.run(alice) { StepFlow.pass(it, alice) }
        assertEquals(TurnPosition.CLEANUP, position(rt))
        val discard = assertIs<Decision.ChooseObjects>(rt.decision)
        assertEquals(2, discard.min)
        rt.run(bob) { call -> StepFlow.answer(call, Answer.Objects(discard.options.take(2))) }
        assertEquals(7, hand.count { zone(rt, it) == PlayZone.HAND })
        assertEquals(2, hand.count { zone(rt, it) == PlayZone.GRAVEYARD })
        assertEquals(bob, rt.state.board.turn.accountId, "the turn goes on to Bob's")
    }

    // CR 514.2, 611.2a: "until end of turn" effects end in the cleanup step
    @Test fun untilEndOfTurnEnds() {
        at(TurnPosition.PRECOMBAT_MAIN, 3)
        val (lizard) = table.put(alice, PlayZone.BATTLEFIELD, "Sunning Mudlizard")
        table.put(alice, PlayZone.BATTLEFIELD, "Forest", "Forest")
        table.put(alice, PlayZone.LIBRARY, "Island")
        table.put(bob, PlayZone.LIBRARY, "Island", "Island")
        table.put(bob, PlayZone.HAND, "Island")
        val rt = game()
        rt.activate(alice, "Sunning Mudlizard")
        rt.settle()
        rt.resolveAll()
        assertTrue(rt.state.board.engineRecords.any { it.id.startsWith("effect:") }, "the pump is an effect record")
        assertEquals(3, rt.engine().let { arcanum.engine.Engine.view(it)[rt.obj("Sunning Mudlizard")]?.power })
        rt.run(alice) { StepFlow.pass(it, alice, StepFlow.Until.EndOfTurn) }
        assertEquals(bob, rt.state.board.turn.accountId)
        assertTrue(rt.state.board.engineRecords.none { it.id.startsWith("effect:") }, "the pump ended in the cleanup step")
        assertEquals(PlayZone.BATTLEFIELD, zone(rt, lizard))
    }

    // I-D28: only the player holding priority passes; a refused pass changes nothing
    @Test fun passWithoutPriorityIsRefused() {
        at(TurnPosition.PRECOMBAT_MAIN, 3)
        table.put(alice, PlayZone.LIBRARY, "Island")
        table.put(bob, PlayZone.LIBRARY, "Island")
        val rt = game()
        val call = rt.call()
        assertFailsWith<IllegalArgumentException> { StepFlow.pass(call, bob) }
        assertTrue(call.changes.isEmpty())
    }
}
