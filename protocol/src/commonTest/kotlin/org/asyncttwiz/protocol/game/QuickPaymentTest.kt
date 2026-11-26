package org.asyncttwiz.protocol.game

import arcanum.api.TurnStep
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.input.Input
import arcanum.engine.state.ObjectId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * SYS-209, SYS-063 (owner 2026-11-26, after problem report DH9MW5: "it should never make those if there are multiple options"): a mana
 * payment while something resolves is a one-answer question for Quick only when there is one way to pay it (sources alike count as
 * one). With Autotap on the player chose to have their payments made for them, so their device pays as Autotap suggests.
 */
class QuickPaymentTest {
    private val toll = arcanum.engine.cards.Cards.invent("Quiet Toll", "{0}", "Creature — Spirit",
        "When this creature enters, you may pay {1} and 2 life. If you do, draw a card.", "1/1")
    private val rock = arcanum.engine.cards.Cards.invent("Quiet Rock", "{0}", "Artifact", "{T}: Add {C}.")
    private val stone = arcanum.engine.cards.Cards.invent("Quiet Stone", "{0}", "Artifact", "{T}: Add {R}.")

    private fun names(m: GameModel, ids: List<ObjectId>) = ids.map { m.engine.obj(it)!!.definition.faces.first().name }

    /**
     * Alice (on Quick, Autotap [autotap]) casts the mana artifacts [sources] and then a Quiet Toll; its trigger asks her "pay {1} and 2
     * life?" and she says yes on her own: the payment that follows is open or made, as her device leaves it.
     */
    private fun afterYes(sources: List<String>, autotap: Boolean): DeviceSim {
        val deck = List(14) { toll } + List(13) { rock } + List(13) { stone }
        for (seed in 1L..300L) {
            val sim = DeviceSim(EngineGames(seed, deck to List(40) { "Mountain" }))
            sim.start(sim.alice)
            sim.keepWhileAway(sim.alice)
            val alice = sim.device(sim.alice)
            val hand = names(alice.model, alice.model.engine.player(sim.alice).hand)
            if (toll !in hand || sources.groupingBy { it }.eachCount().any { (name, n) -> hand.count { it == name } < n }) continue
            alice.settings(PlaySettings(autotap = autotap))
            fun castFromHand(name: String) {
                val s = alice.model.engine
                val id = s.player(sim.alice).hand.first { s.obj(it)!!.definition.faces.first().name == name }
                assertIs<GameDevice.Acted.Sent>(alice.act(Input.Cast(sim.alice, id)), "cast $name")
                sim.settle(only = listOf(sim.alice))
            }
            sources.forEach(::castFromHand)
            castFromHand(toll)
            val may = assertIs<Decision.May>(alice.model.engine.pending?.decision, "the Toll's question: ${alice.model.engine.turn}")
            assertIs<GameDevice.Acted.Sent>(alice.act(Input.Answer(may.id, Answer.YesNo(true))))
            sim.settle(only = listOf(sim.alice))
            assertEquals(TurnStep.PrecombatMain, alice.model.engine.turn.step)
            return sim
        }
        error("no seed deals what the test wants")
    }

    private fun paid(sim: DeviceSim): Boolean = sim.device(sim.alice).model.engine.player(sim.alice).life == 18

    // SYS-209, SYS-063: Autotap off, two different sources ({C} and {R}) may pay her {1}: Quick leaves the payment to her
    @Test
    fun withAutotapOffAPaymentWithTwoWaysIsLeftToThePlayer() {
        val sim = afterYes(listOf(rock, stone), autotap = false)
        val payment = assertIs<Decision.ManaPayment>(sim.device(sim.alice).model.engine.pending?.decision, "her payment open")
        val judged = WhoActs.quickJudged(sim.device(sim.alice).model, payment)
        assertNull(judged.answer, judged.why)
        assertTrue(!paid(sim), "nothing paid for her")
    }

    // SYS-209, SYS-063: Autotap off, two sources alike (two Quiet Rocks) are one way to pay: Quick pays it
    @Test
    fun withAutotapOffSourcesAlikeAreOneWayToPay() {
        val sim = afterYes(listOf(rock, rock), autotap = false)
        assertNull(sim.device(sim.alice).model.engine.pending?.decision, "paid by itself")
        assertTrue(paid(sim), "{1} and 2 life paid")
    }

    // SYS-209, CR 118.12: Autotap on, the payment of two ways is made as Autotap suggests
    @Test
    fun withAutotapOnThePaymentIsMadeForThePlayer() {
        val sim = afterYes(listOf(rock, stone), autotap = true)
        assertNull(sim.device(sim.alice).model.engine.pending?.decision, "paid by itself")
        assertTrue(paid(sim), "{1} and 2 life paid")
    }

    // SYS-209, SYS-206: the server takes no payment of two ways made for a player by another device (the server's rule never knows Autotap)
    @Test
    fun aPaymentOfTwoWaysHasNoQuickAnswer() {
        val sim = afterYes(listOf(rock, stone), autotap = false)
        val model = sim.device(sim.alice).model
        val payment = assertIs<Decision.ManaPayment>(model.engine.pending?.decision)
        assertNull(WhoActs.quickAnswer(model, payment))
        assertNotNull(payment.suggestion.takeIf { it.isNotEmpty() }, "Autotap has a suggestion")
    }
}
