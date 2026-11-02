package org.asyncttwiz.automatum

import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.state.PlayerId
import arcanum.engine.state.TargetRef
import org.asyncttwiz.protocol.Automatum
import org.asyncttwiz.protocol.CommandIds
import org.asyncttwiz.protocol.FlowRevert
import org.asyncttwiz.protocol.GameAction
import org.asyncttwiz.protocol.GameCommand
import org.asyncttwiz.protocol.GameScope
import org.asyncttwiz.protocol.GameplayTree
import org.asyncttwiz.protocol.MtgPriority
import org.asyncttwiz.protocol.PlayBatches
import org.asyncttwiz.protocol.PlayState
import org.asyncttwiz.protocol.PlayZone
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Priority, responding, resolution and triggers as shared flows (integration spec I-D3, I-D8, I-D11, I-D18, §3 I2):
 * every call is one ATW batch that rebuilds to the engine's own result ([RoundTrip]). */
class SharedFlowTest {
    private val table = AtwTable("shared")
    private val alice get() = table.alice
    private val bob get() = table.bob
    private val random = Random(11)
    private fun seed() = org.asyncttwiz.protocol.RandomV1.encodeSeed(random.nextBytes(32))
    private fun seeds() = CastFlow.Seeds(::seed)

    private fun log(rt: RoundTrip) = EngineLog.from(rt.tags, rt.state, table.definitions)
    private fun flow(rt: RoundTrip) = SharedFlow(rt.state, table.definitions, log(rt), ::seed)
    private fun life(rt: RoundTrip, player: String) = rt.state.board.players.single { it.accountId == player }.life
    private fun zone(rt: RoundTrip, card: String) = rt.state.board.containers.single { card in it.cards }.zone
    private fun skipOff(rt: RoundTrip, player: String) {
        rt.state = rt.state.copy(board = rt.state.board.copy(players = rt.state.board.players.map {
            if (it.accountId == player) it.copy(skipWhenNoChoice = false) else it }))
    }

    /** [player] casts [card] at [target] with the suggested mana, as the app sends it ([CastFlow.sent]). */
    private fun cast(rt: RoundTrip, player: String, card: String, target: TargetRef): EngineCall {
        val plays = HandPlays.of(rt.state, table.definitions, player, log(rt))
        var flow = CastFlow.start(rt.state, table.definitions, player, log(rt), plays.of(card).first(), seeds())
        flow = flow.answer(Answer.Targets(listOf(target)))
        flow = flow.answer(Answer.ManaPayment((flow.pending as Decision.ManaPayment).suggestion))
        return flow.sent().also { rt.apply(it, player) }
    }
    private fun player(id: String) = TargetRef.Player(PlayerId(id))

    // CR 117.3c, 117.4, 608.2, I-D3, I-D18: the caster passes, the opponent has nothing to respond with and passes too, so
    // the spell resolves in the cast's own batch; afterwards the active player holds priority (CR 117.3b)
    @Test fun spellResolvesAtOnceWhenNobodyCanRespond() {
        table.put(alice, PlayZone.BATTLEFIELD, "Mountain")
        val (spark) = table.put(alice, PlayZone.HAND, "Storm Spark")
        val rt = RoundTrip(table.state(), table.definitions)
        cast(rt, alice, spark, player(bob))
        assertEquals(1, rt.batches.size, "one batch")
        assertEquals("17", life(rt, bob))
        assertEquals(PlayZone.GRAVEYARD, zone(rt, spark))
        assertTrue(rt.state.board.stackEffects.isEmpty())
        assertFalse(Automatum.flowOpen(rt.state.board))
        assertEquals(MtgPriority(alice), rt.state.board.priority)
        // I-D22: the passes are automatic, folded into the batch's visible event
        assertTrue(rt.batches.single().filter { it.tag?.folded == true }.isNotEmpty())
    }

    // CR 117.3d, 117.4, I-D3: an opponent who could respond holds priority; their "Let it resolve" resolves the spell
    @Test fun opponentWhoCanRespondHoldsPriorityUntilTheyPass() {
        table.put(alice, PlayZone.BATTLEFIELD, "Mountain")
        table.put(bob, PlayZone.BATTLEFIELD, "Mountain")
        val (spark) = table.put(alice, PlayZone.HAND, "Storm Spark")
        table.put(bob, PlayZone.HAND, "Storm Spark")
        val rt = RoundTrip(table.state(), table.definitions)
        cast(rt, alice, spark, player(bob))
        assertEquals(MtgPriority(bob, 1), rt.state.board.priority)
        assertEquals(1, rt.state.board.stackEffects.size)
        val waiting = flow(rt)
        assertEquals(bob, waiting.priority)
        assertNull(waiting.decision)
        rt.apply(waiting.pass(bob), bob)
        assertEquals("17", life(rt, bob))
        assertTrue(rt.state.board.stackEffects.isEmpty())
        assertNull(flow(rt).priority, "nothing on the Stack: no prompt")
    }

    // CR 117.4, 405.5, I-D3: a response goes on top and resolves first; with nothing left to respond with, everyone
    // passes for themselves and both spells resolve in the response's batch
    @Test fun responseResolvesFirst() {
        table.put(alice, PlayZone.BATTLEFIELD, "Mountain")
        table.put(bob, PlayZone.BATTLEFIELD, "Mountain")
        val (spark) = table.put(alice, PlayZone.HAND, "Storm Spark")
        val (response) = table.put(bob, PlayZone.HAND, "Storm Spark")
        val rt = RoundTrip(table.state(), table.definitions)
        cast(rt, alice, spark, player(bob))
        cast(rt, bob, response, player(alice))
        assertEquals("17", life(rt, alice))
        assertEquals("17", life(rt, bob))
        assertEquals(PlayZone.GRAVEYARD, zone(rt, spark))
        assertEquals(PlayZone.GRAVEYARD, zone(rt, response))
        assertTrue(rt.state.board.stackEffects.isEmpty())
        assertEquals(2, rt.batches.size)
    }

    // I-D18: with "Skip when I have no choice" off, a player holding cards only they can see is asked (a priority
    // window) even when nothing in hand could respond; on, they pass at once
    @Test fun skipOffAsksAPlayerHoldingHiddenCards() {
        table.put(alice, PlayZone.BATTLEFIELD, "Mountain", "Mountain")
        val (first, second) = table.put(alice, PlayZone.HAND, "Storm Spark", "Storm Spark")
        table.put(bob, PlayZone.HAND, "Mountain")
        val rt = RoundTrip(table.state(), table.definitions)
        skipOff(rt, bob)
        cast(rt, alice, first, player(bob))
        assertEquals(bob, flow(rt).priority)
        rt.apply(flow(rt).pass(bob), bob)
        assertEquals("17", life(rt, bob))
        rt.state = rt.state.copy(board = rt.state.board.copy(players = rt.state.board.players.map { it.copy(skipWhenNoChoice = true) }))
        cast(rt, alice, second, player(bob))
        assertEquals("14", life(rt, bob), "skip on: resolved at once")
    }

    // CR 603.3, 608.2d, D11, I-D11: a creature dying triggers Fertile Passing; its "may" is the dead creature's
    // controller's, kept in the game as the open decision; anyone may answer it, and the answer draws and closes the flow
    @Test fun triggerAsksItsPlayerAndEitherPlayerMayAnswer() {
        table.put(alice, PlayZone.BATTLEFIELD, "Mountain", "Fertile Passing")
        val (mite) = table.put(bob, PlayZone.BATTLEFIELD, "Tinkered Mite")
        val (drawn) = table.put(bob, PlayZone.LIBRARY, "Mountain")
        val (spark) = table.put(alice, PlayZone.HAND, "Storm Spark")
        val rt = RoundTrip(table.state(), table.definitions)
        val miteObject = rt.obj("Tinkered Mite")
        cast(rt, alice, spark, TargetRef.Object(miteObject))
        assertEquals(PlayZone.GRAVEYARD, zone(rt, mite))
        assertTrue(Automatum.flowOpen(rt.state.board), "the decision is kept in the game")
        val open = flow(rt)
        val may = assertIs<Decision.May>(open.decision)
        assertEquals(PlayerId(bob), may.player)
        assertTrue("draw a card" in may.text, may.text)
        assertNull(open.priority, "no priority prompt while a decision is open")
        // Alice answers for Bob (D11)
        rt.apply(open.answer(Answer.YesNo(true)), alice)
        assertEquals(PlayZone.HAND, zone(rt, drawn))
        assertFalse(Automatum.flowOpen(rt.state.board))
        assertTrue(rt.state.board.stackEffects.isEmpty())
    }

    // CR 603.3b, P5: two different triggers of one player at once are ordered by that player; the order is a shared
    // decision after the cast, and answering it puts them on the Stack
    @Test fun twoTriggersAreOrderedByTheirController() {
        table.put(alice, PlayZone.BATTLEFIELD, "Mountain", "Peak Ascendancy", "Tinkered Mite")
        table.put(alice, PlayZone.LIBRARY, "Mountain", "Mountain")
        val (spark) = table.put(alice, PlayZone.HAND, "Storm Spark")
        val rt = RoundTrip(table.state(), table.definitions)
        cast(rt, alice, spark, player(bob))
        val order = assertIs<Decision.OrderTriggers>(flow(rt).decision)
        assertEquals(PlayerId(alice), order.player)
        assertEquals(2, order.triggers.size)
        assertTrue(order.triggers.all { !it.text.isNullOrEmpty() })
        rt.apply(flow(rt).answer(Answer.Order(listOf(1, 0))), alice)
        // The draw trigger went on first, the pump on top (CR 405.5: it resolves first); with nothing to respond with
        // both resolve in turn, and the draw trigger asks Alice its "may"
        val may = assertIs<Decision.May>(flow(rt).decision)
        assertEquals(PlayerId(alice), may.player)
        assertTrue("may draw a card" in may.text, may.text)
    }

    // I-D19, SYS-060: switching off during a shared flow takes every event since it began back on the server, the
    // cast's batch included, whoever made them
    @Test fun revertFlowTakesASharedFlowBack() {
        table.put(alice, PlayZone.BATTLEFIELD, "Mountain", "Fertile Passing")
        table.put(bob, PlayZone.BATTLEFIELD, "Tinkered Mite")
        table.put(bob, PlayZone.LIBRARY, "Mountain")
        val (spark) = table.put(alice, PlayZone.HAND, "Storm Spark")
        val start = table.state().let { it.copy(board = it.board.copy(automatum = true)) }
        val rt = RoundTrip(start, table.definitions)
        var tree = GameplayTree(start)
        var request = 0
        fun command(action: GameAction) = GameCommand(CommandIds.uuidV5(AtwTable.NAMESPACE, "tree/${request++}"),
            GameScope(start.gameId, playId = start.id), action, CommandIds.uuidV5(AtwTable.NAMESPACE, "stream"), "1")
        fun send(call: EngineCall, actor: String) {
            val command = command(GameAction.PlayBatch(listOf(org.asyncttwiz.protocol.PlayAction.PassAttention)))
            val plan = EngineBatch.plan(call, actor, command)
            val change = PlayBatches.change(tree, actor, command.copy(action = plan.action))
            tree = assertNotNull(change.tree, "batch: ${change.reason}")
            rt.state = tree.stateAt(tree.currentNodeId)!!
        }
        val plays = HandPlays.of(rt.state, table.definitions, alice, emptyList())
        var cast = CastFlow.start(rt.state, table.definitions, alice, emptyList(), plays.of(spark).first(), seeds())
        cast = cast.answer(Answer.Targets(listOf(TargetRef.Object(rt.obj("Tinkered Mite")))))
        cast = cast.answer(Answer.ManaPayment((cast.pending as Decision.ManaPayment).suggestion))
        send(cast.sent(), alice)
        assertTrue(Automatum.flowOpen(rt.state.board))
        val reverted = FlowRevert.change(tree, bob, command(GameAction.RevertFlow))
        assertNull(reverted.reason)
        val back = assertNotNull(reverted.tree).let { it.stateAt(it.currentNodeId)!! }
        assertEquals(start.board, back.board, "back to before the cast")
    }

}
