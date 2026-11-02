package arcanum.engine.sets.blb

import arcanum.api.CounterKind
import arcanum.api.TurnStep
import arcanum.api.Zone
import arcanum.engine.cards.Cards
import arcanum.engine.scenario.scenario
import kotlin.test.Test

/** Bloomburrow's creature wordings, played with invented cards of the parser's chunk 11. */
class BlbCreaturesScenariosTest {
    private val harborMage = Cards.invent("Glum Harbor-Mage", "{1}{U}", "Creature — Frog Wizard", """
        Whenever one or more other creatures you control leave the battlefield without dying, draw a card.
        {1}{U}, {T}: Return another target creature you control to its owner's hand.
    """, "1/3")
    private val lynx = Cards.invent("Sunbristle Lynx", "{2}{R}{R}", "Creature — Elemental Cat", """
        Players can't gain life.
        Damage can't be prevented.
        When this creature enters, it deals damage to each player equal to the number of nonbasic lands that player controls.
    """, "5/4")
    private val hargen = Cards.invent("Hargen, Long Ear of the Watch", "{4}{G}{W}", "Legendary Creature — Rabbit Soldier", """
        Vigilance
        When Hargen enters, put a +1/+1 counter on each of up to two target creatures.
        Whenever a creature you control with a +1/+1 counter on it attacks, double the number of +1/+1 counters on it.
    """, "4/4")
    private val vole = Cards.invent("Mossback Vole", "{2}{G}", "Creature — Mouse", "", "3/3")
    private val bolt = Cards.invent("Twin Spark", "{1}{R}", "Instant", "Twin Spark deals 1 damage to any target.")
    private val salve = Cards.invent("Small Salve", "{W}", "Instant", "You gain 2 life.")
    private val grove = Cards.invent("Mossy Hollow", null, "Land", "{T}: Add {G}.")

    private val p1p1 = CounterKind.PowerToughness(1, 1)

    /** Returning a creature to its owner's hand is leaving without dying: a card is drawn. */
    // CR 700.4
    @Test
    fun leavingWithoutDyingDraws() = scenario {
        alice.battlefield(harborMage, vole, "Island", "Island")
        alice.library("Plains")

        alice.activates(harborMage, target = vole)
        allPass()
        expect { stack.size shouldBe 1 }
        allPass()
        expect { alice.hand.names shouldBe listOf(vole, "Plains") }
    }

    /** Dying isn't leaving without dying: nothing is drawn. */
    // CR 700.4
    @Test
    fun dyingDoesNotDraw() = scenario {
        alice.battlefield(harborMage, "Mountain", "Mountain")
        alice.battlefield(Cards.invent("Tiny Shrew", "{W}", "Creature — Mouse", "", "1/1"))
        alice.hand(bolt)
        alice.library("Plains")

        alice.casts(bolt, target = "Tiny Shrew")
        allPass()
        expect {
            card("Tiny Shrew").zone shouldBe Zone.Graveyard
            stack.isEmpty()
            alice.hand.size shouldBe 0
        }
    }

    /** Each player is dealt damage for their own nonbasic lands; nobody gains life while it is there. */
    // CR 119.7
    @Test
    fun damagePerPlayerAndNoLifeGain() = scenario {
        alice.battlefield(grove, grove, "Mountain", "Mountain", "Mountain", "Mountain", "Plains")
        alice.hand(lynx, salve)
        bob.battlefield(grove, grove, grove)

        alice.casts(lynx, paying = listOf("Mountain", "Mountain", "Mountain", "Mountain"))
        allPass()
        allPass()
        expect {
            alice.life shouldBe 18
            bob.life shouldBe 17
        }
        alice.casts(salve)
        allPass()
        expect { alice.life shouldBe 18 }
    }

    /** A creature with two +1/+1 counters attacks and has four. */
    // CR 701.10e
    @Test
    fun attackDoublesCounters() = scenario {
        alice.battlefield(hargen)
        alice.battlefield(vole, counters = listOf(p1p1 to 2))
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(vole)
        allPass()
        expect { card(vole).counters(p1p1) shouldBe 4 }
    }
}
