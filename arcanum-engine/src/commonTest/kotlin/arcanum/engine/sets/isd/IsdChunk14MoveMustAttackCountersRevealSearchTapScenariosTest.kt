package arcanum.engine.sets.isd

import arcanum.api.CounterKind
import arcanum.api.Keyword
import arcanum.api.TurnStep
import arcanum.api.Zone
import arcanum.engine.Engine
import arcanum.engine.cards.Cards
import arcanum.engine.input.Input
import arcanum.engine.scenario.scenario
import arcanum.engine.sets.dft.choose
import arcanum.engine.sets.rtr.resolveAll
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** ISD chunk 14: must-attack with a combat-damage counter, an untap that waits for a death, death-counter creatures, a search artifact,
 * creature-count power/toughness, and tap and untap effects. Invented names carry the oracle text of the real cards. */
class IsdChunk14MoveMustAttackCountersRevealSearchTapScenariosTest {
    private val whelp = Cards.invent("Bloodthirst Whelp", "{1}{R}", "Creature — Vampire", """
        This creature attacks each combat if able.
        Whenever this creature deals combat damage to a player, put a +1/+1 counter on it.
    """, "2/1")
    private val brute = Cards.invent("Anvil Brute", "{4}", "Artifact Creature — Juggernaut", """
        This creature attacks each combat if able.
        This creature doesn't untap during your untap step.
        Whenever another creature dies, untap this creature.
    """, "5/5")
    private val elder = Cards.invent("Mossback Elder", "{2}{G}{G}", "Creature — Treefolk", """
        Hexproof (This creature can't be the target of spells or abilities your opponents control.)
        Whenever a creature dies, put a +1/+1 counter on this creature.
    """, "1/1")
    private val heir = Cards.invent("Ruddy Kin", "{2}{R}", "Creature — Vampire", "Whenever a Vampire you control deals combat damage to a player, put a +1/+1 counter on it.", "2/2")
    private val fang = Cards.invent("Smokeheart Vampire", "{1}{B}", "Creature — Vampire", "", "2/2")
    private val compass = Cards.invent("Pilgrim's Compass", "{1}", "Artifact",
        "{1}, Sacrifice this artifact: Search your library for a basic land card, reveal it, put it into your hand, then shuffle.")
    private val wurm = Cards.invent("Gravel Wurm", "{1}{G}", "Creature — Wurm", "Gravel Wurm's power and toughness are each equal to the number of creature cards in your graveyard.", "*/*")
    private val monk = Cards.invent("Choir Monk", "{3}{W}{W}", "Creature — Human Monk", """
        Vigilance
        Choir Monk's power and toughness are each equal to the number of creatures you control.
        When this creature enters, create two 1/1 white Spirit creature tokens with flying.
    """, "*/*")
    private val priest = Cards.invent("Dusk Priest", "{1}{W}", "Creature — Human Cleric", "{1}, {T}: Tap target non-Human creature.", "1/2")
    private val grasp = Cards.invent("Stirring Grasp", "{2}{G}", "Instant", "Untap target creature. It gets +2/+4 and gains reach until end of turn.")
    private val lash = Cards.invent("Ember Whip", "{R}", "Instant", "Ember Whip deals 2 damage to target creature.")
    private val bear = Cards.invent("Brindle Cub", "{1}{G}", "Creature — Bear", "", "2/2")
    private val townsman = Cards.invent("Parish Hand", "{1}{W}", "Creature — Human", "", "2/2")

    // CR 508.1d: a creature that attacks each combat if able can't be left out of the declaration; CR 510.2, 122.1: it grows on hitting a player
    @Test
    fun whelpMustAttackAndGrows() = scenario {
        alice.battlefield(whelp)
        advanceTo(TurnStep.DeclareAttackers)
        assertTrue(Engine.options(state, alice.id).filterIsInstance<Input.DeclareAttackers>().none { it.attacks.isEmpty() })
        alice.attacks(whelp)
        advanceTo(TurnStep.EndOfCombat)
        resolveAll()
        expect {
            bob.life shouldBe 18
            whelp.power shouldBe 3
            whelp.counters(CounterKind.PowerToughness(1, 1)) shouldBe 1
        }
    }

    // CR 502.3: it stays tapped through the untap step; CR 700.4, 701.26b: another creature dying untaps it
    @Test
    fun bruteStaysTappedUntilAnotherCreatureDies() = scenario {
        alice.battlefield(brute, "Mountain")
        alice.library("Forest", "Forest", "Forest", "Forest")
        bob.library("Forest", "Forest", "Forest", "Forest")
        alice.hand(lash)
        bob.battlefield(bear)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(brute)
        advanceTo(TurnStep.EndOfCombat)
        expect { brute.tapped shouldBe true }
        advanceTo(TurnStep.Upkeep, turn = 3)
        expect { brute.tapped shouldBe true }
        alice.casts(lash, target = bear)
        resolveAll()
        expect {
            bear.zone shouldBe Zone.Graveyard
            brute.tapped shouldBe false
        }
    }

    // CR 700.4: any creature dying counts, an opponent's included
    @Test
    fun elderGrowsWhenAnOpponentsCreatureDies() = scenario {
        alice.battlefield(elder, "Mountain")
        alice.hand(lash)
        bob.battlefield(bear)
        alice.casts(lash, target = bear)
        resolveAll()
        expect { elder.power shouldBe 2 }
    }

    // CR 510.2, 603.2: the counter goes on the Vampire that dealt the damage, not on this card
    @Test
    fun heirPutsTheCounterOnTheVampireThatHit() = scenario {
        alice.battlefield(heir, fang)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(fang)
        advanceTo(TurnStep.EndOfCombat)
        resolveAll()
        expect {
            fang.power shouldBe 3
            heir.power shouldBe 2
            bob.life shouldBe 18
        }
    }

    // CR 701.23a, 701.20a, 701.24: the sacrificed artifact fetches a basic land, which is revealed and put into hand
    @Test
    fun compassFindsABasicLand() = scenario {
        alice.library("Forest", bear)
        alice.battlefield(compass, "Plains")
        alice.activates(compass)
        resolveAll()
        choose("Forest")
        expect {
            alice.hand has "Forest"
            alice.graveyard has compass
        }
    }

    // CR 604.3, 208.2a: counts only creature cards in the owner's graveyard
    @Test
    fun wurmCountsCreatureCardsInYourGraveyard() = scenario {
        alice.battlefield(wurm)
        alice.graveyard(bear, townsman, lash)
        bob.graveyard(bear)
        expect {
            wurm.power shouldBe 2
            wurm.toughness shouldBe 2
        }
    }

    // CR 604.3, 111.1: the monk counts itself and the tokens it makes
    @Test
    fun monkCountsItselfAndItsSpirits() = scenario {
        alice.battlefield("Plains", "Plains", "Plains", "Plains", "Plains", bear)
        alice.hand(monk)
        alice.casts(monk)
        resolveAll()
        expect {
            monk.power shouldBe 4
            monk.toughness shouldBe 4
            monk hasKeyword Keyword.Vigilance
        }
    }

    // CR 701.26a: taps a creature that isn't a Human
    @Test
    fun priestTapsANonHuman() = scenario {
        alice.battlefield(priest, "Plains")
        bob.battlefield(bear)
        alice.activates(priest, target = bear)
        resolveAll()
        expect { bear.tapped shouldBe true }
    }

    // CR 115.2: a Human isn't a legal target
    @Test
    fun priestCantTargetAHuman() = scenario {
        alice.battlefield(priest, "Plains")
        bob.battlefield(townsman)
        assertFailsWith<IllegalArgumentException> { alice.activates(priest, target = townsman) }
    }

    // CR 701.26b, 611.2a: untaps, then +2/+4 and reach until end of turn
    @Test
    fun graspUntapsAndPumps() = scenario {
        alice.battlefield(bear, "Forest", "Forest", "Forest", tapped = false)
        alice.hand(grasp)
        bob.battlefield(townsman)
        expect { bear.tapped shouldBe false }
        alice.casts(grasp, target = bear)
        resolveAll()
        expect {
            bear.power shouldBe 4
            bear.toughness shouldBe 6
            bear hasKeyword Keyword.Reach
        }
    }
}
