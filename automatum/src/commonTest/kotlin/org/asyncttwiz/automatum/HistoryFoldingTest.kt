package org.asyncttwiz.automatum

import arcanum.engine.input.Input
import arcanum.engine.state.PlayerId
import arcanum.engine.state.TargetRef
import org.asyncttwiz.protocol.GameplayMutation
import org.asyncttwiz.protocol.MtgAction
import org.asyncttwiz.protocol.MtgPriority
import org.asyncttwiz.protocol.PlayAction
import org.asyncttwiz.protocol.PlayZone
import org.asyncttwiz.protocol.TurnPosition
import org.asyncttwiz.protocol.TurnState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * HIS-092 (I-D22, I-D35): history events are natural branching points, one per point where a player can act. An automated
 * batch runs from a player's action to the next such point: its first event (the action) is shown and everything
 * Automatum did by itself after it is folded into it; manual events never are.
 */
class HistoryFoldingTest {
    private val table = AtwTable("folding")
    private val alice get() = table.alice
    private val bob get() = table.bob

    private fun game(): RoundTrip {
        table.put(alice, PlayZone.BATTLEFIELD, "Mountain")
        table.put(alice, PlayZone.HAND, "Storm Spark")
        table.put(alice, PlayZone.LIBRARY, "Mountain", "Mountain")
        table.put(bob, PlayZone.LIBRARY, "Island", "Island")
        return RoundTrip(table.state(), table.definitions)
    }

    private fun folded(m: GameplayMutation) = m.grouping?.folded == true

    @Test fun everythingAfterTheActionIsFoldedIntoIt() {
        val rt = game()
        rt.cast(alice, "Storm Spark")
        rt.target(TargetRef.Player(PlayerId(bob)))
        rt.settle()
        for (batch in rt.batches) {
            assertFalse(folded(batch.first()), "a batch's first event is never folded: ${batch.first().action}")
            for (m in batch.drop(1)) assertTrue(folded(m), "${m.action}")
            // Folded events join the group of the visible event before them: the batch shows as its first event
            for ((before, m) in batch.zipWithNext())
                assertEquals(before.bookkeeping!!.toGroup!!.id, m.bookkeeping!!.toGroup!!.id, "joins the group before it")
        }
        assertTrue(rt.batches.flatten().any(::folded))
    }

    // D57 shows every pass; I-D22 shows only a pass a player made themselves; I-D35 folds what it sets off
    @Test fun aPlayersOwnPassLeadsAndWhatFollowsIsFolded() {
        val rt = game()
        rt.cast(alice, "Storm Spark")
        rt.target(TargetRef.Player(PlayerId(bob)))
        rt.settle()
        val before = rt.batches.size
        // Alice passes herself; the host passes for Bob, who can't respond (I-D3, I-D18), in the same call
        val call = EngineCall(rt.state, rt.definitions, log = EngineLog.from(rt.tags, rt.state, rt.definitions), seeds = rt::seed)
        call.perform(Input.PassPriority(PlayerId(alice)))
        call.perform(Input.PassPriority(PlayerId(bob)), automatic = true)
        rt.apply(call, alice)
        val batch = rt.batches.drop(before).single()
        assertTrue((batch.first().action as? PlayAction.Mtg)?.action is MtgAction.SetPriority, "Alice's own pass leads")
        assertFalse(folded(batch.first()))
        assertEquals(17, rt.engine().player(PlayerId(bob)).life, "the spell resolved")
        assertTrue(batch.any { folded(it) && it.action is PlayAction.AdjustLife }, "the damage is folded into the pass")
    }

    // I-D35, TRN-057: the step button's pass runs through the opponent's cleanup and Alice's untap, upkeep and draw to her
    // main phase: one event, the steps passed on the way and the draw folded into it
    @Test fun aStepRunIsOneEvent() {
        table.turn = TurnState(bob, "2", TurnPosition.END_STEP.phase, TurnPosition.END_STEP.step)
        table.priority = MtgPriority(bob)
        table.put(alice, PlayZone.HAND, "Mountain")
        table.put(alice, PlayZone.LIBRARY, "Mountain", "Mountain")
        table.put(bob, PlayZone.LIBRARY, "Island", "Island")
        val rt = RoundTrip(table.state(), table.definitions)
        rt.run(bob) { StepFlow.pass(it, bob) }
        val batch = rt.batches.single()
        assertEquals(TurnPosition.PRECOMBAT_MAIN, TurnPosition.of(rt.state.board.turn))
        assertEquals(1, batch.count { !folded(it) }, "${batch.filterNot(::folded).map { it.action }}")
        assertTrue(batch.any { folded(it) && it.action == PlayAction.EndTurn })
        assertTrue(batch.any { folded(it) && it.action is PlayAction.MoveCard }, "the draw is folded")
    }
}
