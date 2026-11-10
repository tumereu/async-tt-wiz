package arcanum.engine.sets.inr

import arcanum.api.TurnStep
import arcanum.engine.cards.Cards
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.scenario.scenario
import arcanum.engine.sets.rtr.resolveAll
import kotlin.test.Test
import kotlin.test.assertFailsWith

/** INR chunk 13: cost reductions for creature spells and for creatures on the battlefield (CR 601.2f), a mana ability limited to creature spells
 * (CR 106.6), a creature that can't block and can be cast from the graveyard (CR 509.1b, 601.2a). Invented names carry the oracle text. */
class InrChunk13CostChangeScenariosTest {
    private val summoning = Cards.invent("Hollow Summoning", "{1}{B}", "Enchantment", """
        Creature spells you cast cost {2} less to cast.
        Creatures you control get -1/-1.
    """)
    private val hulk = Cards.invent("Mire Hulk", "{2}{B}{B}", "Creature — Zombie", "", "4/4")
    private val routTheHorde = Cards.invent("Rout the Horde", "{6}{W}{W}", "Sorcery", """
        This spell costs {1} less to cast for each creature on the battlefield.
        Destroy all creatures.
    """)
    private val seer = Cards.invent("Woodland Seer", "{2}{G}", "Creature — Human Druid", """
        {T}: Add three mana of any one color. Spend this mana only to cast creature spells.
    """, "0/1")
    private val behemoth = Cards.invent("Mossback Behemoth", "{2}{G}{G}", "Creature — Beast", "", "4/4")
    private val fizzle = Cards.invent("Mossback Fizzle", "{1}{G}{G}", "Sorcery", "Draw a card.")
    private val walker = Cards.invent("Gravewalker", "{B}", "Creature — Zombie", """
        This creature can't block.
        You may cast this card from your graveyard as long as you control a Zombie.
    """, "2/1")
    private val grunt = Cards.invent("Barrow Grunt", "{1}{R}", "Creature — Goblin", "", "2/2")

    // CR 601.2f, 613.4c: the Hulk costs {2}{B}{B}, minus {2}, so {B}{B}; the 4/4 is a 3/3 under the enchantment
    @Test
    fun creatureSpellsCostTwoLess() = scenario {
        alice.battlefield(summoning, "Swamp", "Swamp")
        alice.hand(hulk)
        alice.casts(hulk)
        resolveAll()
        expect {
            alice.battlefield has hulk
            hulk.power shouldBe 3
        }
    }

    // CR 601.2f: the discount is for creature spells you cast, so a creature spell of the opponent costs full price
    @Test
    fun opponentsCreatureSpellsAreNotCheaper() = scenario(players = 3) {
        alice.battlefield(summoning)
        bob.battlefield("Swamp", "Swamp")
        bob.hand(hulk)
        turn(number = 2, active = bob)
        bob.casts(hulk)
        expect { decision<Decision.ManaPayment>().suggestion shouldBe emptyList() }
    }

    // CR 601.2f: two creatures on the battlefield (any controller) make it {4}{W}{W}
    @Test
    fun costsLessForEachCreatureOnTheBattlefield() = scenario(players = 3) {
        alice.battlefield(*Array(6) { "Plains" }, hulk)
        bob.battlefield(grunt)
        alice.hand(routTheHorde)
        alice.casts(routTheHorde)
        resolveAll()
        expect {
            alice.graveyard has hulk
            bob.graveyard has grunt
        }
    }

    // CR 106.6: three mana from the Seer pay for a creature spell
    @Test
    fun seerManaPaysForACreatureSpell() = scenario {
        alice.battlefield(seer, "Forest")
        alice.hand(behemoth)
        alice.activates(seer)
        answer(Answer.ColorChoice(arcanum.api.Color.Green))
        alice.casts(behemoth)
        resolveAll()
        expect { alice.battlefield has behemoth }
    }

    // CR 106.6: the Seer's mana can't be spent on a sorcery, so the Forest alone can't pay {1}{G}{G}
    @Test
    fun seerManaCantPayForASorcery() = scenario {
        alice.battlefield(seer, "Forest")
        alice.hand(fizzle)
        alice.activates(seer)
        answer(Answer.ColorChoice(arcanum.api.Color.Green))
        alice.casts(fizzle)
        expect { decision<Decision.ManaPayment>().suggestion shouldBe emptyList() }
    }

    // CR 601.2a: with a Zombie in play the card can be cast from the graveyard; CR 509.1b: it can't block
    @Test
    fun walkerIsCastFromTheGraveyardWithAZombieAndCantBlock() = scenario {
        alice.battlefield(hulk, "Swamp")
        alice.graveyard(walker)
        alice.casts(walker)
        resolveAll()
        expect { alice.battlefield has walker }
    }

    // CR 509.1b: the Gravewalker can't be declared as a blocker, though it is untapped
    @Test
    fun walkerCantBlock() = scenario {
        alice.battlefield(walker)
        bob.battlefield(grunt)
        turn(number = 2, active = bob)
        advanceTo(TurnStep.DeclareAttackers)
        bob.attacks(grunt)
        advanceTo(TurnStep.DeclareBlockers)
        assertFailsWith<IllegalArgumentException> { alice.blocks(walker to grunt) }
    }
}
