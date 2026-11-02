package org.asyncttwiz.automatum

import arcanum.api.Affected
import arcanum.api.Duration
import arcanum.api.Mechanic
import arcanum.api.Objects
import arcanum.api.Value
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.state.EffectId
import arcanum.engine.state.EffectObject
import arcanum.engine.state.ObjectId
import arcanum.engine.state.PlayerId
import arcanum.engine.state.TargetRef
import kotlinx.serialization.json.Json
import org.asyncttwiz.protocol.EngineRecord
import org.asyncttwiz.protocol.EngineRecordKind
import org.asyncttwiz.protocol.MtgAction
import org.asyncttwiz.protocol.MtgPriority
import org.asyncttwiz.protocol.PlayAction
import org.asyncttwiz.protocol.PlayZone
import org.asyncttwiz.protocol.StackEffect
import org.asyncttwiz.protocol.TurnPosition
import org.asyncttwiz.protocol.TurnState
import kotlin.random.Random
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Adversarial review of phases I1 and I2a (report `arcanum-i12-review.md`, outside the repo): each test pins a problem
 * found; ignored until it is fixed. Invented cube cards only. */
class I12ReviewTest {
    private val table = AtwTable("i12review")
    private val alice get() = table.alice
    private val bob get() = table.bob
    private val random = Random(12)
    private fun seeds() = CastFlow.Seeds { org.asyncttwiz.protocol.RandomV1.encodeSeed(random.nextBytes(32)) }

    // i12review #4 (CR 514.2, TBL-112): while turns still change by hand (I1–I3) nothing ends an until-end-of-turn
    // effect: the computed view keeps showing a pump from two turns ago, with its ⌛
    @Ignore("i12review #4: until-end-of-turn effects never end while the turn changes by hand")
    @Test fun untilEndOfTurnEffectEndsWhenTheTurnChangesByHand() {
        val (gremlin) = table.put(alice, PlayZone.BATTLEFIELD, "Festering Gremlin")
        val base = table.state()
        val engineId = CardIds.of(base.board).engine(gremlin)
        val pump = EngineRecord("effect:pump", EngineRecordKind.Effect, 5, Json.encodeToString(EffectObject.serializer(), EffectObject(
            EffectId("pump"), listOf(Mechanic.ModifyPowerToughness(Value.Fixed(3), Value.Fixed(3))), Affected.Objects(Objects.Self),
            locked = listOf(ObjectId.Card(engineId, 0)), source = ObjectId.Card(engineId, 0), controller = PlayerId(alice),
            timestamp = 5, createdTurn = 1, duration = Duration.EndOfTurn)))
        val turn1 = base.copy(board = base.board.copy(automatum = true, engineRecords = listOf(pump),
            cards = base.board.cards.map { if (it.id == gremlin) it.copy(timestamp = 1) else it }))
        assertEquals("5/5", ComputedBoard.of(turn1, table.definitions, alice).cards.getValue(gremlin).corner, "pumped in its own turn")
        // Two turns later, changed with the step button (TRN-007): the cleanup of turn 1 (CR 514.2) has passed
        val turn3 = turn1.copy(board = turn1.board.copy(turn = TurnState(alice, "3", TurnPosition.PRECOMBAT_MAIN.phase,
            TurnPosition.PRECOMBAT_MAIN.step)))
        val computed = ComputedBoard.of(turn3, table.definitions, alice).cards.getValue(gremlin)
        assertEquals("2/2", computed.corner, "the until-end-of-turn pump of turn 1 has ended")
        assertFalse(computed.untilEndOfTurn)
    }

    // i12review #11 (I-D22, HIS-092): the host's priority grant (HostPriority) leads a cast's batch, so the batch's first
    // visible event (its history line and the timeline's stop) is "Priority → alice" instead of the cast
    @Ignore("i12review #11: a cast batch shows the host's priority grant and mana pool steps as events")
    @Test fun aCastsFirstVisibleEventIsTheSpellNotThePriorityGrant() {
        // After a turn changed by hand Bob, the previous turn's player, still holds priority on the empty Stack
        table.priority = MtgPriority(bob)
        table.put(alice, PlayZone.BATTLEFIELD, "Mountain")
        val (spark) = table.put(alice, PlayZone.HAND, "Storm Spark")
        val rt = RoundTrip(table.state(), table.definitions)
        var flow = CastFlow.start(rt.state, table.definitions, alice, emptyList(),
            HandPlays.of(rt.state, table.definitions, alice, emptyList()).of(spark).first(), seeds())
        flow = flow.answer(Answer.Targets(listOf(TargetRef.Player(PlayerId(bob)))))
        flow = flow.answer(Answer.ManaPayment((flow.pending as Decision.ManaPayment).suggestion))
        // What the app sends (CastFlow.plan): the cast with the passes and resolution that follow it (I2b)
        rt.apply(flow.sent(), alice)
        val visible = rt.batches.last().filter { it.grouping?.folded != true }
        val shown = visible.map { m -> (m.action as? PlayAction.Mtg)?.action?.let { it::class.simpleName } ?: m.action::class.simpleName }
        assertFalse((visible.first().action as? PlayAction.Mtg)?.action is MtgAction.SetPriority, "the cast's first visible event: $shown")
        // Mana made and spent inside the cast (CR 601.2g-h) is no point a player could act at
        assertTrue(visible.none { (it.action as? PlayAction.Mtg)?.action is MtgAction.SetManaPool }, "visible events: $shown")
    }

    // i12review #12 (TRN-055, STK-023): the reasons Automatum gives players for not taking over a board (hold panel, cast
    // refusal "Automatum can't take over this board: …") are plain sentences with card names, never internal ids
    @Test fun refusalReasonsNameNoInternalIds() {
        val (spark, held) = table.put(alice, PlayZone.HAND, "Storm Spark", "Storm Spark")
        val state = table.state()
        val entry = CardIds.of(state.board).engine(spark).value.reversed()
        val stackId = state.board.containers.single { it.zone == PlayZone.STACK }.id
        val manual = state.copy(board = state.board.copy(stackEffects = listOf(StackEffect(entry, spark)),
            cards = state.board.cards.map { if (it.id == held) it.copy(damage = "x") else it },
            containers = state.board.containers.map { if (it.id == stackId) it.copy(cards = listOf(entry)) else it }))
        val reasons = EngineState.unsupported(manual, table.definitions)
        assertTrue(reasons.isNotEmpty())
        val uuid = Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")
        assertTrue(reasons.none(uuid::containsMatchIn), "plain words: $reasons")
        // Named only for a viewer who may see the card (I-D27): the Stack entry is public; the other Storm Spark is in
        // Alice's hand, hidden from Bob
        val shownToAlice = EngineState.unsupported(manual, table.definitions, alice)
        assertTrue("Storm Spark on the Stack is a manual entry" in shownToAlice &&
            shownToAlice.any { "Storm Spark has damage \"x\"" in it }, "$shownToAlice")
        val shownToBob = EngineState.unsupported(manual, table.definitions, bob)
        assertTrue(shownToBob.none { "Storm Spark has damage" in it } && shownToBob.any { "A hidden card has damage" in it }, "$shownToBob")
    }
}
