package arcanum.engine.sets.one

import arcanum.api.CounterKind
import arcanum.api.TurnStep
import arcanum.api.Zone
import arcanum.engine.cards.Cards
import arcanum.engine.scenario.scenario
import arcanum.engine.sets.blb.resolveAll
import kotlin.test.Test

/**
 * Phyrexia: All Will Be One chunk 16 in play: a static bonus that counts artifacts (CR 613.4c), a power defined by the number of
 * artifacts (CR 604.3, 208.2a), an entering creature that already has an oil counter getting another (CR 603.4, 122.1) and the whole
 * hand discarded as a sacrifice ability resolves (CR 701.9, 601.2f).
 */
class OneChunk16OilStaticsAndHandScenariosTest {
    private val oil = CounterKind.Named("oil")
    private val enforcer = Cards.invent("Rusted Enforcer", "{3}{W}", "Creature — Phyrexian Cleric", """
        Vigilance
        This creature gets +2/+0 as long as you control two or more artifacts.
    """, "2/4")
    private val warden = Cards.invent("Cephalopod Warden", "{2}{W}{U}", "Artifact Creature — Phyrexian Squid", """
        Flying
        Cephalopod Warden's power is equal to the number of artifacts you control.
    """, "*/5")
    private val golem = Cards.invent("Ichorcrown Golem", "{3}", "Artifact Creature — Phyrexian Golem", """
        Whenever a creature you control enters, if it has one or more oil counters on it, put an oil counter on it.
        Creatures you control with oil counters on them get +1/+1.
    """, "2/3")
    private val flamestoker = Cards.invent("Vengeful Flamestoker", "{R}", "Creature — Phyrexian Wizard", """
        Whenever you cast a noncreature spell, put an oil counter on this creature.
        {6}{R}, Sacrifice this creature: Discard your hand, then draw four cards. This ability costs {1} less to activate for each oil counter on this creature.
    """, "1/2")
    private val oiled = Cards.invent("Oiled Squire", "{1}{W}", "Creature — Phyrexian Soldier",
        "This creature enters with an oil counter on it.", "1/1")
    private val plain = Cards.invent("Plain Squire", "{1}{W}", "Creature — Human Soldier", "", "1/1")
    private val trinket = Cards.invent("Brass Trinket", "{1}", "Artifact", "")
    private val trinketTwo = Cards.invent("Brass Trinket Two", "{1}", "Artifact", "")

    // CR 613.4c: one artifact is not enough, the second one gives +2/+0, and losing it takes the bonus away again
    @Test
    fun enforcerNeedsTwoArtifacts() = scenario {
        alice.battlefield(enforcer, trinket)
        expect { enforcer.power shouldBe 2 }
        alice.battlefield(trinketTwo)
        expect { enforcer.power shouldBe 4 }
    }

    // CR 604.3: the power counts every artifact its controller has, itself included; the toughness stays printed
    @Test
    fun wardenPowerCountsMyArtifacts() = scenario {
        alice.battlefield(warden, trinket)
        bob.battlefield(trinketTwo)
        expect {
            warden.power shouldBe 2
            warden.toughness shouldBe 5
        }
    }

    // CR 603.4, 122.1: the creature entering with an oil counter gets a second and the +1/+1 applies; a creature without one is untouched
    @Test
    fun golemAddsOilToAnEnteringCreatureThatHasOil() = scenario {
        alice.battlefield(golem, "Plains", "Plains")
        alice.hand(oiled)
        alice.casts(oiled)
        resolveAll()
        expect {
            oiled.counters(oil) shouldBe 2
            oiled.power shouldBe 2
            golem.counters(oil) shouldBe 0
        }
    }

    @Test
    fun golemLeavesAnEnteringCreatureWithoutOilAlone() = scenario {
        alice.battlefield(golem, "Plains", "Plains")
        alice.hand(plain)
        alice.casts(plain)
        resolveAll()
        expect {
            plain.counters(oil) shouldBe 0
            plain.power shouldBe 1
        }
    }

    // CR 603.4: the oil counter on the Golem itself is not what is checked
    @Test
    fun golemWithOilItselfDoesNotAddOilToACreatureWithout() = scenario {
        alice.battlefield(golem, "Plains", "Plains", counters = listOf(oil to 1))
        alice.hand(plain)
        alice.casts(plain)
        resolveAll()
        expect { plain.counters(oil) shouldBe 0 }
    }

    // CR 701.9, 601.2f: six oil counters make the ability cost {R}; the whole hand (two cards) goes, then four are drawn
    @Test
    fun flamestokerDiscardsTheWholeHandThenDraws() = scenario {
        alice.battlefield(flamestoker, "Mountain", counters = listOf(oil to 6))
        alice.hand(trinket, trinketTwo)
        alice.library("Forest", "Forest", "Forest", "Forest", "Forest")
        alice.activates(flamestoker)
        resolveAll()
        expect {
            flamestoker.zone shouldBe Zone.Graveyard
            trinket.zone shouldBe Zone.Graveyard
            trinketTwo.zone shouldBe Zone.Graveyard
            alice.hand.size shouldBe 4
        }
    }
}
