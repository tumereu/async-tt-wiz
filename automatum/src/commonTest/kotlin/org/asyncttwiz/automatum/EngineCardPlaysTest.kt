package org.asyncttwiz.automatum

import arcanum.api.Zone
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.input.Input
import arcanum.engine.state.PlayerId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** What a card's menu offers on a game's own engine state ([CardPlays], I-D28) and activating through the cast panel's
 * flow ([CastFlow]). */
class EngineCardPlaysTest {
    private val table = EngineTable()
    private val alice get() = table.alice
    private val bob get() = table.bob
    private fun start(state: arcanum.engine.state.GameState, input: Input) =
        CastFlow.start(table.source(state), alice, input, CastFlow.Seeds(table::seed))

    // TBL-115 (CR 602.2, 605.3a): each activated ability with its printed text, the legal ones with the engine's input,
    // the others greyed with why; a land's Play; the other player's abilities greyed as theirs; out of priority the
    // holder is named
    @Test fun abilitiesListedLegalAndRefused() {
        table.put(alice, Zone.Battlefield, "Mountain", "Mountain")
        val (seer, workshop) = table.put(alice, Zone.Battlefield, "Elvish Seer", "Refitting Workshop")
        val (bobSeer) = table.put(bob, Zone.Battlefield, "Elvish Seer")
        val (land) = table.put(alice, Zone.Hand, "Mountain")
        val plays = table.plays()
        val mana = plays.of(seer).single()
        assertEquals(CardPlay.Kind.Mana, mana.kind)
        assertEquals("{T}: Add {G}.", mana.text)
        assertIs<Input.Activate>(mana.input)
        val tools = plays.of(workshop)
        assertTrue(tools.first { it.text.startsWith("{2}, {T}") }.legal, "two Mountains pay {2}")
        val thopter = tools.first { it.text.startsWith("{1}, {T}, Sacrifice a Servo") }
        assertNull(thopter.input, "no Servo to sacrifice")
        assertEquals(CardPlay.Refusal.Other, thopter.refusal)
        assertEquals(CardPlay.Refusal.NotYours, plays.of(bobSeer).single().refusal)
        assertEquals(CardPlay.Kind.Play, plays.of(land).single().kind)
        assertEquals(CardPlay.Refusal.Priority(alice), table.plays(viewer = bob).of(bobSeer).single().refusal)
    }

    // SYS-067, TBL-115 (CR 605.3a, 117.3c): a mana ability outside a payment goes out at once and its player keeps priority
    @Test fun aManaAbilityKeepsPriority() {
        val (seer) = table.put(alice, Zone.Battlefield, "Elvish Seer")
        val state = table.state()
        val flow = start(state, assertNotNull(table.plays(state).of(seer).single().input))
        assertNull(flow.pending)
        assertEquals(PlayerId(alice), flow.state.priority.holder)
        assertEquals(1, flow.state.player(PlayerId(alice)).manaPool.size)
    }

    // SYS-067 (CR 305.2): lands played through Automatum count one per turn: after the first, a second is greyed as used
    @Test fun aSecondLandIsGreyedAsUsed() {
        val (first, second) = table.put(alice, Zone.Hand, "Mountain", "Mountain")
        val state = table.state()
        val played = start(state, assertNotNull(table.plays(state).of(first).single().input)).state
        val greyed = table.plays(played).of(second).single()
        assertNull(greyed.input)
        assertEquals(CardPlay.Refusal.Used, greyed.refusal)
    }

    // TBL-115 (CR 602.2): an ability with a cost to pay opens the cast panel's payment, and the activation goes out
    // with the suggested payment
    @Test fun activatingAsksThePayment() {
        table.put(alice, Zone.Battlefield, "Mountain", "Mountain")
        val (workshop) = table.put(alice, Zone.Battlefield, "Refitting Workshop")
        val state = table.state()
        val servo = table.plays(state).of(workshop).first { it.text.startsWith("{2}, {T}") }
        var flow = start(state, assertNotNull(servo.input))
        assertEquals(workshop, flow.cardId)
        assertTrue(flow.activation)
        val payment = assertIs<Decision.ManaPayment>(flow.pending)
        flow = flow.answer(Answer.ManaPayment(payment.suggestion))
        assertNull(flow.pending)
        assertEquals(1, flow.state.stack.size, "the ability is on the Stack")
    }
}
