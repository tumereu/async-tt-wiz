package arcanum.engine.casting

import arcanum.api.Keyword
import arcanum.engine.Engine
import arcanum.engine.cards.Cards
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.input.CastWay
import arcanum.engine.input.Input
import arcanum.engine.scenario.Scenario
import arcanum.engine.scenario.scenario
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Turning face-down permanents face up and down again (CR 702.37, 702.168, 708): the X a cost was turned up for, what stays. */
class FaceDownTurnsTest {
    private val trumpeter = Cards.invent("Hidden Horn Trumpeter", "{R}", "Creature — Goblin", """
        Morph {X}{X}{R} (You may cast this card face down as a 2/2 creature for {3}. Turn it face up any time for its morph cost.)
        When this creature is turned face up, create X 1/1 red Goblin creature tokens.
    """, "1/1")
    private val witness = Cards.invent("Veiled Tally Witness", "{3}{W}", "Creature — Human Detective", """
        Disguise {X}{3}{W} (You may cast this card face down for {3} as a 2/2 creature with ward {2}. Turn it face up any time for its disguise cost.)
        When this creature is turned face up, you gain X life.
    """, "3/4")

    private fun Scenario.alternativeWay(name: String): CastWay =
        Engine.options(state, alice.id).filterIsInstance<Input.Cast>().first { it.card == card(name) && it.way.alternative != null }.way

    private fun Scenario.pay() {
        while (pending is Decision.ManaPayment) answer(Answer.ManaPayment(decision<Decision.ManaPayment>().suggestion))
    }

    /** Casts [name] face down for {3}, then turns it face up for its cost with X = [x]. */
    private fun Scenario.castDownTurnUp(name: String, x: Int) {
        alice.casts(name, way = alternativeWay(name))
        pay()
        allPass()
        val id = card(name)
        assertTrue(state.objOrThrow(id).faceDown)
        perform(Engine.options(state, alice.id).filterIsInstance<Input.Activate>().single { it.source == id })
        if (pending is Decision.Announce) answer(Answer.Number(x))
        pay()
    }

    // CR 702.37f, 107.3d: X in another ability of a permanent with morph is the X chosen as it was turned face up
    @Test
    fun morphsXIsTheXItWasTurnedUpFor() = scenario {
        alice.battlefield(*Array(8) { "Mountain" })
        alice.hand(trumpeter)
        castDownTurnUp(trumpeter, x = 2)
        allPass()
        val tokens = state.battlefield.filter { state.objOrThrow(it).token }
        assertEquals(2, tokens.size)
    }

    // CR 702.168e: the same for disguise
    @Test
    fun disguisesXIsTheXItWasTurnedUpFor() = scenario {
        alice.battlefield(*Array(10) { "Plains" })
        alice.hand(witness)
        castDownTurnUp(witness, x = 3)
        allPass()
        expect { alice.life shouldBe 23 }
    }
}
