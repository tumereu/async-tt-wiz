package org.asyncttwiz.automatum

import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.input.Input
import org.asyncttwiz.protocol.MtgPriority
import org.asyncttwiz.protocol.PlayZone
import org.asyncttwiz.protocol.PoolMana
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** What the engine offers for each card (integration spec §3 I4, I-D28) and activating abilities as private flows
 * through the cast panel's flow ([CastFlow]), sent as one batch that ATW's reducer takes ([RoundTrip]). */
class CardPlaysTest {
    private val table = AtwTable("plays")
    private val alice get() = table.alice
    private val bob get() = table.bob
    private val random = Random(11)
    private fun seeds() = CastFlow.Seeds { org.asyncttwiz.protocol.RandomV1.encodeSeed(random.nextBytes(32)) }
    private fun plays(rt: RoundTrip, player: String = alice) = CardPlays.of(rt.state, table.definitions, player, emptyList())
    private fun start(rt: RoundTrip, input: Input): CastFlow =
        CastFlow.start(rt.state, table.definitions, alice, emptyList(), input, seeds())

    // CR 602.2, 605.3a, I-D28: every activated ability is listed with its text, legal ones with the engine's input, the
    // others with why not; a hand card's cast and a land play come with the engine's ways
    @Test fun abilitiesListedLegalAndRefused() {
        table.put(alice, PlayZone.BATTLEFIELD, "Mountain", "Mountain")
        val (seer, workshop) = table.put(alice, PlayZone.BATTLEFIELD, "Elvish Seer", "Refitting Workshop")
        val (bobSeer) = table.put(bob, PlayZone.BATTLEFIELD, "Elvish Seer")
        val (land) = table.put(alice, PlayZone.HAND, "Mountain")
        val rt = RoundTrip(table.state(), table.definitions)
        val plays = plays(rt)
        val mana = plays.of(seer).single()
        assertEquals(CardPlay.Kind.Mana, mana.kind)
        assertEquals("{T}: Add {G}.", mana.text)
        assertIs<Input.Activate>(mana.input)
        val tools = plays.of(workshop)
        assertEquals(4, tools.size, "$tools")
        assertTrue(tools.first { it.text.startsWith("{2}, {T}") }.legal, "two Mountains pay {2}")
        val thopter = tools.first { it.text.startsWith("{1}, {T}, Sacrifice a Servo") }
        assertNull(thopter.input, "no Servo to sacrifice")
        assertEquals(CardPlay.Refusal.Other, thopter.refusal)
        assertEquals(CardPlay.Refusal.NotYours, plays.of(bobSeer).single().refusal)
        assertEquals(CardPlay.Kind.Play, plays.of(land).single().kind)
        // Bob doesn't hold priority in Alice's main phase
        val bobs = plays(rt, bob)
        assertEquals(CardPlay.Refusal.Priority(alice), bobs.of(bobSeer).single().refusal)
    }

    // CR 602.2, 117.3c, STK-026: an activated ability goes on the Stack paid with the suggested mana; the activating
    // player passes, nobody can respond and it resolves within the same batch (and the game goes on, STK-026)
    @Test fun activatingPaysAndResolves() {
        table.put(alice, PlayZone.BATTLEFIELD, "Mountain", "Mountain")
        val (workshop) = table.put(alice, PlayZone.BATTLEFIELD, "Refitting Workshop")
        val rt = RoundTrip(table.state(), table.definitions)
        val servo = plays(rt).of(workshop).first { it.text.startsWith("{2}, {T}") }
        var flow = start(rt, assertNotNull(servo.input))
        assertEquals(workshop, flow.cardId)
        assertTrue(flow.activation)
        val payment = assertIs<Decision.ManaPayment>(flow.pending)
        flow = flow.answer(Answer.ManaPayment(payment.suggestion))
        assertNull(flow.pending)
        rt.apply(flow.sent(), alice)
        val board = rt.state.board
        assertTrue(board.cards.single { it.id == workshop }.tapped)
        assertEquals(1, board.cards.count { it.token }, "the Servo was made")
        assertTrue(board.stackEffects.isEmpty())
    }

    // CR 605.3a, 106.4: a mana ability outside a payment adds its mana at once and keeps priority with its player
    @Test fun manaAbilityOutsidePayment() {
        val (seer) = table.put(alice, PlayZone.BATTLEFIELD, "Elvish Seer")
        val rt = RoundTrip(table.state(), table.definitions)
        val flow = start(rt, assertNotNull(plays(rt).of(seer).single().input))
        assertNull(flow.pending)
        rt.apply(flow.sent(), alice)
        val board = rt.state.board
        assertTrue(board.cards.single { it.id == seer }.tapped)
        assertEquals(listOf("G"), board.players.single { it.accountId == alice }.manaPool.map(PoolMana::color))
        assertEquals(MtgPriority(alice), board.priority)
    }

    // CR 305.2 (engine-responsiveness R3): a second land in hand after this turn's land play is greyed as used, the
    // engine's reason asked without performing it (Engine.refusal)
    @Test fun aSecondLandIsGreyedAsUsed() {
        val (first, second) = table.put(alice, PlayZone.HAND, "Mountain", "Mountain")
        val rt = RoundTrip(table.state(), table.definitions)
        rt.apply(start(rt, assertNotNull(plays(rt).of(first).single().input)).sent(), alice)
        val greyed = plays(rt).of(second).single()
        assertNull(greyed.input)
        assertEquals(CardPlay.Refusal.Used, greyed.refusal)
    }

    // CR 702.6a: equip targets a creature its controller controls, as a sorcery
    @Test fun equipTargetsThenPays() {
        table.put(alice, PlayZone.BATTLEFIELD, "Mountain")
        val (cloak, seer) = table.put(alice, PlayZone.BATTLEFIELD, "Eden Cloak", "Elvish Seer")
        val rt = RoundTrip(table.state(), table.definitions)
        val equip = plays(rt).of(cloak).single { it.text.startsWith("Equip") }
        var flow = start(rt, assertNotNull(equip.input))
        val targets = assertIs<Decision.Targets>(flow.pending)
        flow = flow.answer(Answer.Targets(listOf(targets.options.single { flow.ref(it).let { r -> r is org.asyncttwiz.protocol.MtgRef.Card && r.cardId == seer } })))
        flow = flow.answer(Answer.ManaPayment(assertIs<Decision.ManaPayment>(flow.pending).suggestion))
        rt.apply(flow.sent(), alice)
        assertEquals(seer, (rt.state.board.cards.single { it.id == cloak }.attachedTo as? org.asyncttwiz.protocol.MtgRef.Card)?.cardId)
    }
}
