package arcanum.engine.sets.msh

import arcanum.engine.cards.Cards
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.scenario.Scenario
import arcanum.engine.scenario.scenario
import kotlin.test.Test
import kotlin.test.assertFailsWith

/** Marvel Super Heroes' cards that copy an ability on the Stack (parser chunks 10, 18): the copy resolves too (CR 707.10). */
class MshAbilityCopyCardsScenariosTest {
    private val mirrorPerceptiveProdigy = Cards.invent("Mirror, Perceptive Prodigy", "{2}{U}", "Legendary Creature — Human Hero", """
        Vigilance
        {1}, {T}: Copy target activated or triggered ability you control from a creature source. You may choose new targets for the copy. (Mana abilities can't be targeted.)
    """, "1/4")
    private val scientistSupremeOfTheSyndicate = Cards.invent("Scientist Supreme of the Syndicate", "{U}{B}", "Legendary Creature — Human Scientist Villain", "Pay 2 life: Copy target activated or triggered ability you control from an artifact source. You may choose new targets for the copy. Activate only during your turn and only once each turn. (Mana abilities can't be targeted.)", "2/2")

    private val pinger = Cards.invent("Avenue Pinger", "{1}{R}", "Creature — Human Rogue", "{T}: This creature deals 1 damage to each opponent.", "1/1")
    private val turret = Cards.invent("Avenue Turret", "{1}", "Artifact", "{T}: This artifact deals 1 damage to each opponent.")

    /** Answers the ability-copy target with the ability on the Stack, pays, and resolves everything. */
    private fun Scenario.copyTheAbility() {
        var guard = 30
        while (guard-- > 0) {
            when (val d = pending) {
                is Decision.Targets -> answer(Answer.Targets(listOf(d.options.first())))
                is Decision.ManaPayment -> answer(Answer.ManaPayment(d.suggestion))
                is Decision.May -> answer(Answer.YesNo(false))
                null -> if (state.stack.isEmpty()) return else allPass()
                else -> return
            }
        }
    }

    // CR 707.10
    @Test
    fun mirrorCopiesACreaturesAbility() = scenario {
        alice.battlefield(mirrorPerceptiveProdigy, pinger, "Island")
        alice.activates(pinger)
        alice.activates(mirrorPerceptiveProdigy)
        copyTheAbility()
        expect {
            bob.life shouldBe 18
            mirrorPerceptiveProdigy.tapped shouldBe true
        }
    }

    @Test
    fun mirrorCantCopyAnArtifactsAbility() = scenario {
        alice.battlefield(mirrorPerceptiveProdigy, turret, "Island")
        alice.activates(turret)
        assertFailsWith<IllegalArgumentException> { alice.activates(mirrorPerceptiveProdigy) }
    }

    @Test
    fun scientistSupremeCopiesAnArtifactsAbilityForLife() = scenario {
        alice.battlefield(scientistSupremeOfTheSyndicate, turret)
        alice.activates(turret)
        alice.activates(scientistSupremeOfTheSyndicate)
        copyTheAbility()
        expect {
            bob.life shouldBe 18
            alice.life shouldBe 18
        }
    }
}
