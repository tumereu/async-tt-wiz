package arcanum.engine.sets.blb

import arcanum.engine.cards.Cards
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.scenario.scenario
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/** Bloomburrow's Seasons: up to five {P} worth of modes, repeatable (CR 700.2i). */
class BlbSeasonsScenariosTest {
    private val warren = Cards.invent("Time of the Warren", "{3}{W}{W}", "Sorcery", """
        Choose up to five {P} worth of modes. You may choose the same mode more than once.
        {P} — Create a 1/1 white Rabbit creature token.
        {P}{P} — Exile target nonland permanent. Its controller draws a card.
        {P}{P}{P} — Return target permanent card with mana value 3 or less from your graveyard to the battlefield with an indestructible counter on it.
    """)
    private val idol = Cards.invent("Brass Idol", "{2}", "Artifact")
    private val bear = Cards.invent("Mossback Cub", "{1}{G}", "Creature — Bear", "", "2/2")

    /** Five Rabbits: the one-pawprint mode chosen five times. */
    // CR 700.2i, 700.2d: the same mode more than once
    @Test
    fun sameModeFiveTimes() = scenario {
        alice.battlefield("Plains", "Plains", "Plains", "Plains", "Plains")
        alice.hand(warren)

        alice.casts(warren, modes = listOf(0, 0, 0, 0, 0))
        allPass()
        expect { alice.battlefield.names.count { it == "Rabbit Token" } shouldBe 5 }
    }

    /** Two pawprints and three: an exile and a return; more than five pawprints is refused. */
    // CR 700.2i; CR 122.1b: the indestructible counter gives indestructible
    @Test
    fun mixedModesWithinTheLimit() = scenario {
        alice.battlefield("Plains", "Plains", "Plains", "Plains", "Plains")
        alice.hand(warren)
        alice.graveyard(bear)
        bob.battlefield(idol)
        bob.library("Island")

        alice.casts(warren)
        val d = decision<Decision.Modes>()
        assertEquals(5, d.pawprintLimit)
        assertFailsWith<IllegalArgumentException> { answer(Answer.Modes(listOf(2, 2))) }
        answer(Answer.Modes(listOf(1, 2)))
        if (pending is Decision.Targets) answer(Answer.Targets(listOf(target(idol))))
        if (pending is Decision.Targets) answer(Answer.Targets(listOf(target(bear))))
        answer(Answer.ManaPayment(decision<Decision.ManaPayment>().suggestion))
        allPass()
        expect {
            bob.hand.names shouldBe listOf("Island")
            alice.battlefield has bear
            bear hasKeyword arcanum.api.Keyword.Indestructible
        }
    }
}
