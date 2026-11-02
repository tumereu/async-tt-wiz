package org.asyncttwiz.automatum

import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.input.Input
import arcanum.engine.state.PlayerId
import arcanum.engine.state.TargetRef
import org.asyncttwiz.protocol.MtgAction
import org.asyncttwiz.protocol.MtgPriority
import org.asyncttwiz.protocol.MtgStackKind
import org.asyncttwiz.protocol.PlayAction
import org.asyncttwiz.protocol.PlayZone
import org.asyncttwiz.protocol.TurnPosition
import org.asyncttwiz.protocol.TurnState
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Casting and land plays as private flows (integration spec I-D11, §3 I2): the flow stays local until it is complete,
 * then goes out as one batch that ATW's reducer takes and that rebuilds to the engine's own result ([RoundTrip]). */
class CastFlowTest {
    private val table = AtwTable("cast")
    private val alice get() = table.alice
    private val bob get() = table.bob
    private val random = Random(7)
    private fun seeds() = CastFlow.Seeds { org.asyncttwiz.protocol.RandomV1.encodeSeed(random.nextBytes(32)) }

    private fun plays(rt: RoundTrip, player: String = alice) = HandPlays.of(rt.state, table.definitions, player, emptyList())
    private fun start(rt: RoundTrip, card: String, player: String = alice): CastFlow =
        CastFlow.start(rt.state, table.definitions, player, emptyList(), plays(rt, player).of(card).first(), seeds())

    // CR 601.2c, 601.2g–h, I-D6: targets, then the suggested payment; the flow is sent once, as one batch, when complete
    @Test fun castWithTargetThenSuggestedPayment() {
        table.put(alice, PlayZone.BATTLEFIELD, "Mountain")
        val (spark) = table.put(alice, PlayZone.HAND, "Storm Spark")
        val rt = RoundTrip(table.state(), table.definitions)
        var flow = start(rt, spark)
        val targets = assertIs<Decision.Targets>(flow.pending)
        assertTrue(TargetRef.Player(PlayerId(bob)) in targets.options)
        flow = flow.answer(Answer.Targets(listOf(TargetRef.Player(PlayerId(bob)))))
        val payment = assertIs<Decision.ManaPayment>(flow.pending)
        assertTrue(payment.suggestion.isNotEmpty())
        assertFalse(flow.pays(emptyList()), "nothing tapped doesn't pay {R}")
        assertTrue(flow.pays(payment.suggestion))
        flow = flow.answer(Answer.ManaPayment(payment.suggestion))
        assertNull(flow.pending)
        rt.apply(flow.call, alice)
        val board = rt.state.board
        // STK-001, STK-003: the card stays in the hand; its Stack entry carries the spell
        assertEquals(PlayZone.HAND, board.containers.single { spark in it.cards }.zone)
        val entry = board.stackEffects.single()
        assertEquals(spark, entry.sourceId)
        assertEquals(MtgStackKind.Spell, entry.item?.kind)
        assertTrue(board.cards.single { table.names[it.id] == "Mountain" }.tapped)
        // D57, CR 117.3c: the caster holds priority after casting
        assertEquals(MtgPriority(alice), board.priority)
    }

    // I-D6: a toggle above (kicker) changes the cost the payment asks for and keeps the target chosen after it
    @Test fun changingKickerKeepsTheTargetAndChangesTheCost() {
        table.put(alice, PlayZone.BATTLEFIELD, "Mountain", "Mountain", "Mountain", "Mountain", "Mountain")
        val (surge) = table.put(alice, PlayZone.HAND, "Flash Surge")
        val rt = RoundTrip(table.state(), table.definitions)
        var flow = start(rt, surge)
        assertIs<Decision.May>(flow.pending)
        flow = flow.answer(Answer.YesNo(true))
        flow = flow.answer(Answer.Targets(listOf(TargetRef.Player(PlayerId(bob)))))
        val kicked = assertIs<Decision.ManaPayment>(flow.pending)
        assertEquals("{4}{R}", kicked.cost.toString())
        flow = flow.change(0, Answer.YesNo(false))
        assertEquals(listOf<Answer>(Answer.YesNo(false), Answer.Targets(listOf(TargetRef.Player(PlayerId(bob))))), flow.answers)
        val plain = assertIs<Decision.ManaPayment>(flow.pending)
        assertEquals("{R}", plain.cost.toString())
        assertEquals(1, plain.suggestion.size, "the suggestion follows the choices above it")
        flow = flow.answer(Answer.ManaPayment(plain.suggestion))
        rt.apply(flow.call, alice)
        assertEquals(1, rt.state.board.cards.count { it.tapped })
    }

    // I-D28: only what the engine allows is offered; an unpayable spell isn't castable now, a land is played
    @Test fun handPlaysListOnlyWhatTheEngineAllows() {
        val (mountain) = table.put(alice, PlayZone.BATTLEFIELD, "Mountain", tapped = true)
        val (spark, land) = table.put(alice, PlayZone.HAND, "Storm Spark", "Mountain")
        val rt = RoundTrip(table.state(), table.definitions)
        val plays = plays(rt)
        assertTrue(plays.of(spark).isEmpty(), "no untapped source pays {R}")
        assertIs<Input.PlayLand>(plays.of(land).single())
        assertTrue(plays.of(mountain).isEmpty(), "only hand cards")
        assertTrue(plays(rt, bob).byCard.isEmpty(), "Bob doesn't hold priority in Alice's main phase")
    }

    // CR 305.1, 305.2: a land played through the engine goes onto the battlefield; a second isn't playable that turn,
    // and a turn changed by hand starts with none played
    @Test fun playingALandCountsUntilTheTurnChanges() {
        val (first, second) = table.put(alice, PlayZone.HAND, "Mountain", "Mountain")
        val rt = RoundTrip(table.state(), table.definitions)
        val flow = start(rt, first)
        assertNull(flow.pending)
        rt.apply(flow.call, alice)
        assertEquals(PlayZone.BATTLEFIELD, rt.state.board.containers.single { first in it.cards }.zone)
        assertTrue(plays(rt).of(second).isEmpty(), "one land per turn")
        // Two turns later by hand (ATW's step button, before Automatum runs the turns)
        rt.state = rt.state.copy(board = rt.state.board.copy(turn = TurnState(alice, "3", TurnPosition.PRECOMBAT_MAIN.phase,
            TurnPosition.PRECOMBAT_MAIN.step)))
        assertIs<Input.PlayLand>(plays(rt).of(second).single())
    }

    // CR 117.3a: with nobody holding priority (Automatum just turned on, steps changed by hand) the active player holds
    // it on an empty Stack; the batch says so first
    @Test fun nobodyHoldingPriorityGivesItToTheActivePlayer() {
        table.priority = null
        table.put(alice, PlayZone.BATTLEFIELD, "Mountain")
        val (spark) = table.put(alice, PlayZone.HAND, "Storm Spark")
        val rt = RoundTrip(table.state(), table.definitions)
        var flow = start(rt, spark)
        flow = flow.answer(Answer.Targets(listOf(TargetRef.Player(PlayerId(bob)))))
        flow = flow.answer(Answer.ManaPayment((flow.pending as Decision.ManaPayment).suggestion))
        rt.apply(flow.call, alice)
        val first = rt.lastPlan!!.actions.first()
        assertEquals(PlayAction.Mtg(MtgAction.SetPriority(null, MtgPriority(alice))), first)
        assertEquals(MtgPriority(alice), rt.state.board.priority)
    }

    // I-D11: an answer the engine refuses changes nothing
    @Test fun refusedAnswerLeavesTheFlow() {
        table.put(alice, PlayZone.BATTLEFIELD, "Mountain")
        val (spark) = table.put(alice, PlayZone.HAND, "Storm Spark")
        val rt = RoundTrip(table.state(), table.definitions)
        val flow = start(rt, spark)
        assertFailsWith<IllegalArgumentException> { flow.answer(Answer.YesNo(true)) }
        assertIs<Decision.Targets>(flow.pending)
        assertTrue(flow.answers.isEmpty())
    }
}
