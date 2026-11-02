package org.asyncttwiz.automatum

import arcanum.engine.input.Input
import arcanum.engine.state.PlayerId
import arcanum.engine.state.TargetRef
import org.asyncttwiz.protocol.EngineRecordKind
import org.asyncttwiz.protocol.GameplayMutation
import org.asyncttwiz.protocol.MtgAction
import org.asyncttwiz.protocol.PlayAction
import org.asyncttwiz.protocol.PlayZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * HIS-092 (I-D22): history events are natural branching points, one per possible player action. An automated batch's engine
 * bookkeeping and the priority passes nobody made themselves are folded into the batch's visible event; manual events
 * never are.
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
    private fun bookkeeping(m: GameplayMutation) = (m.action as? PlayAction.Mtg)?.action.let {
        it is MtgAction.Restamp || it is MtgAction.SetLedger || it is MtgAction.SetStackItem ||
            it is MtgAction.SetRecord && (it.before ?: it.after)?.kind != EngineRecordKind.Emblem
    }

    @Test fun bookkeepingIsFoldedIntoTheBatchsVisibleEvent() {
        val rt = game()
        rt.cast(alice, "Storm Spark")
        rt.target(TargetRef.Player(PlayerId(bob)))
        rt.settle()
        for (batch in rt.batches) {
            assertFalse(folded(batch.first()), "a batch's first event is never folded: ${batch.first().action}")
            // Folded events join the group of the visible event before them: the batch shows as its visible events
            for (m in batch.drop(1)) assertEquals(bookkeeping(m) || m.action is PlayAction.Mtg &&
                (m.action as PlayAction.Mtg).action is MtgAction.SetPriority, folded(m), "${m.action}")
            for ((before, m) in batch.zipWithNext()) if (folded(m))
                assertEquals(before.bookkeeping!!.toGroup!!.id, m.bookkeeping!!.toGroup!!.id, "joins the group before it")
        }
        assertTrue(rt.batches.flatten().any(::folded))
    }

    // D57 shows every pass; I-D22 shows only a pass a player made themselves
    @Test fun aPlayersOwnPassIsShownAndAnAutomaticPassIsFolded() {
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
        val passes = batch.filter { (it.action as? PlayAction.Mtg)?.action is MtgAction.SetPriority }
        assertEquals(listOf(false) + List(passes.size - 1) { true }, passes.map(::folded), "only Alice's own pass is shown")
        assertEquals(17, rt.engine().player(PlayerId(bob)).life, "the spell resolved")
        assertTrue(batch.any { !folded(it) && it.action is PlayAction.AdjustLife }, "the damage is shown")
    }
}
