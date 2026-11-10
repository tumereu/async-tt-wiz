package arcanum.engine.sets.fdn

import arcanum.api.CounterKind
import arcanum.api.Keyword
import arcanum.api.TurnStep
import arcanum.api.Zone
import arcanum.engine.cards.Cards
import arcanum.engine.decision.Answer
import arcanum.engine.scenario.Scenario
import arcanum.engine.scenario.scenario
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * Chunk 26 played: the invented names carry the oracle text of Time Stop, Aggressive Mammoth, Crossway Troublemakers, Inspiring Paladin,
 * Kargan Dragonrider, Kitesail Corsair, Ancestor Dragon, Archway Angel, Ayli, Eternal Pilgrim, Cloudblazer, Inspiring Overseer, Linden,
 * the Steadfast Queen, Vampiric Rites, Squad Rallier and Vivien Reid.
 */
class FdnChunk26EndTurnGainAbilitiesLifeLookScenariosTest {
    private val halt = Cards.invent("Hour Halt", "{4}{U}{U}", "Instant", "End the turn.")
    private val elephant = Cards.invent("Mighty Elephant", "{3}{G}{G}{G}", "Creature — Elephant", """
        Trample
        Other creatures you control have trample.
    """, "8/8")
    private val troublemakers = Cards.invent("Bloodline Troublemakers", "{5}{B}", "Creature — Vampire", """
        Attacking Vampires you control have deathtouch and lifelink.
        Whenever a Vampire you control dies, you may pay 2 life. If you do, draw a card.
    """, "5/5")
    private val paladin = Cards.invent("Zealous Paladin", "{2}{W}", "Creature — Human Knight", """
        During your turn, this creature has first strike.
        During your turn, creatures you control with +1/+1 counters on them have first strike.
    """, "3/3")
    private val rider = Cards.invent("Drake Rider", "{1}{R}", "Creature — Human Warrior", "As long as you control a Dragon, this creature has flying.", "2/2")
    private val corsair = Cards.invent("Skysail Corsair", "{1}{U}", "Creature — Human Pirate", "This creature has flying as long as it's attacking.", "2/1")
    private val elder = Cards.invent("Elder Dragon", "{4}{W}{W}", "Creature — Dragon", """
        Flying
        Whenever one or more creatures you control attack, you gain 1 life for each attacking creature.
    """, "5/6")
    private val gatekeeper = Cards.invent("Gatekeeper Angel", "{5}{W}", "Creature — Angel", """
        Flying
        When this creature enters, you gain 2 life for each Gate you control.
    """, "3/4")
    private val pilgrim = Cards.invent("Kor Pilgrim", "{W}{B}", "Legendary Creature — Kor Cleric", """
        Deathtouch
        {1}, Sacrifice another creature: You gain life equal to the sacrificed creature's toughness.
        {1}{W}{B}, Sacrifice another creature: Exile target nonland permanent. Activate only if you have at least 10 life more than your starting life total.
    """, "2/3")
    private val cloudmender = Cards.invent("Cloudmender", "{3}{W}{U}", "Creature — Human Scout", """
        Flying
        When this creature enters, you gain 2 life and draw two cards.
    """, "2/2")
    private val warden = Cards.invent("Inspiring Warden", "{2}{W}", "Creature — Angel Cleric", """
        Flying
        When this creature enters, you gain 1 life and draw a card.
    """, "2/1")
    private val linden = Cards.invent("Linden, the Bright Queen", "{W}{W}{W}", "Legendary Creature — Human Noble", """
        Vigilance
        Whenever a white creature you control attacks, you gain 1 life.
    """, "3/3")
    private val rite = Cards.invent("Vampiric Rite", "{B}", "Enchantment", "{1}{B}, Sacrifice a creature: You gain 1 life and draw a card.")
    private val marshal = Cards.invent("Squad Marshal", "{3}{W}", "Creature — Human Scout", """
        {2}{W}: Look at the top four cards of your library. You may reveal a creature card with power 2 or less from among them and put it into your hand. Put the rest on the bottom of your library in a random order.
    """, "3/4")
    private val vivien = Cards.invent("Vivien Reed", "{3}{G}{G}", "Legendary Planeswalker — Vivien", """
        [+1]: Look at the top four cards of your library. You may reveal a creature or land card from among them and put it into your hand. Put the rest on the bottom of your library in a random order.
        [−3]: Destroy target artifact, enchantment, or creature with flying.
        [−8]: You get an emblem with "Creatures you control get +2/+2 and have vigilance, trample, and indestructible."
    """, loyalty = "5")

    private val jab = Cards.invent("Cinder Jab", "{R}", "Instant", "Cinder Jab deals 9 damage to target creature.")
    private val gate = Cards.invent("Lowland Gate", null, "Land — Gate", "")
    private val foeGate = Cards.invent("Foe Gate", null, "Land — Gate", "")
    private val gate2 = Cards.invent("Hillock Gate", null, "Land — Gate", "")
    private val bear = Cards.invent("Gray Wolf", "{1}{G}", "Creature — Wolf", "", "2/2")
    private val soldier = Cards.invent("Plain Soldier", "{W}", "Creature — Human Soldier", "", "1/1")
    private val knight = Cards.invent("Pale Knight", "{1}{W}", "Creature — Human Knight", "", "2/2")
    private val dragon = Cards.invent("Ash Dragon", "{3}{R}", "Creature — Dragon", "", "3/3")
    private val bat = Cards.invent("Frost Bat", "{1}{U}", "Creature — Bat", "Flying", "1/1")
    private val wall = Cards.invent("Wall of Reeds", "{1}{G}", "Creature — Wall", "", "0/5")
    private val vampire = Cards.invent("Pale Vampire", "{1}{B}", "Creature — Vampire", "", "2/2")
    private val bigWolf = Cards.invent("Brass Wurm", "{4}{G}", "Creature — Wurm", "", "5/5")
    private val relic = Cards.invent("Dusty Idol", "{1}", "Artifact", "")

    private fun Scenario.resolveAll() {
        var guard = 40
        while (state.stack.isNotEmpty() && pending == null && guard-- > 0) allPass()
    }

    private fun Scenario.stock() {
        alice.library("Island", "Forest", "Plains", "Swamp", "Mountain", "Island", "Forest", "Plains")
        bob.library("Island", "Forest", "Plains", "Swamp", "Mountain", "Island", "Forest", "Plains")
    }

    // CR 723.1, 723.1a: the spell itself is exiled and the turn ends: the other player's turn follows (CR 500.1)
    @Test
    fun timeStopEndsTheTurn() = scenario {
        stock()
        alice.battlefield("Island", "Island", "Island", "Island", "Island", "Island")
        alice.hand(halt)
        alice.casts(halt)
        resolveAll()
        expect { alice.exile has halt }
        kotlin.test.assertEquals(2, state.turn.number)
        kotlin.test.assertEquals(bob.id, state.turn.active)
    }

    // CR 613.1f: every other creature you control has trample, the opponent's do not
    @Test
    fun elephantGivesOthersTrample() = scenario {
        alice.battlefield(elephant, bear)
        bob.battlefield(soldier)
        expect {
            bear hasKeyword Keyword.Trample.kind
            elephant hasKeyword Keyword.Trample.kind
            soldier lacksKeyword Keyword.Trample.kind
        }
    }

    // CR 613.1f, 508.1k: only attacking Vampires get deathtouch and lifelink
    @Test
    fun troublemakersGiveAttackingVampiresDeathtouchAndLifelink() = scenario {
        alice.battlefield(troublemakers, vampire, bear)
        expect { vampire lacksKeyword Keyword.Deathtouch.kind }
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(vampire, bear)
        expect {
            vampire hasKeyword Keyword.Deathtouch.kind
            vampire hasKeyword Keyword.Lifelink.kind
            bear lacksKeyword Keyword.Deathtouch.kind
            troublemakers lacksKeyword Keyword.Deathtouch.kind
        }
    }

    // CR 603.10a, 119.4, 608.2d: a Vampire dies: pay 2 life, then draw
    @Test
    fun troublemakersPayLifeToDraw() = scenario {
        stock()
        alice.battlefield(troublemakers, vampire, "Mountain")
        alice.hand(jab)
        alice.casts(jab, target = vampire)
        resolveAll()
        alice.answers(Answer.YesNo(true))
        resolveAll()
        expect {
            alice.life shouldBe 18
            alice.hand.size shouldBe 1
        }
    }

    // CR 119.4: declining the payment draws nothing
    @Test
    fun troublemakersDecliningDrawsNothing() = scenario {
        stock()
        alice.battlefield(troublemakers, vampire, "Mountain")
        alice.hand(jab)
        alice.casts(jab, target = vampire)
        resolveAll()
        alice.answers(Answer.YesNo(false))
        resolveAll()
        expect {
            alice.life shouldBe 20
            alice.hand.size shouldBe 0
        }
    }

    // CR 611.3a, 505.1: first strike in your own turn only, and the counter group needs a +1/+1 counter
    @Test
    fun paladinHasFirstStrikeDuringYourTurn() = scenario {
        alice.battlefield(paladin, bear)
        alice.battlefield(knight, counters = listOf(CounterKind.PowerToughness(1, 1) to 1))
        expect {
            paladin hasKeyword Keyword.FirstStrike.kind
            knight hasKeyword Keyword.FirstStrike.kind
            bear lacksKeyword Keyword.FirstStrike.kind
        }
    }

    @Test
    fun paladinHasNoFirstStrikeInTheOpponentsTurn() = scenario {
        alice.battlefield(paladin)
        alice.battlefield(knight, counters = listOf(CounterKind.PowerToughness(1, 1) to 1))
        turn(number = 2, active = bob, step = TurnStep.PrecombatMain, priority = bob)
        expect {
            paladin lacksKeyword Keyword.FirstStrike.kind
            knight lacksKeyword Keyword.FirstStrike.kind
        }
    }

    // CR 611.3a: flying only while you control a Dragon
    @Test
    fun riderFliesWithADragon() = scenario {
        alice.battlefield(rider, dragon)
        bob.battlefield(bear)
        expect { rider hasKeyword Keyword.Flying.kind }
    }

    @Test
    fun riderDoesNotFlyWithoutADragon() = scenario {
        alice.battlefield(rider, bear)
        bob.battlefield(dragon)
        expect { rider lacksKeyword Keyword.Flying.kind }
    }

    // CR 611.3a, 508.1k: flying only once attacking
    @Test
    fun corsairFliesWhileAttacking() = scenario {
        alice.battlefield(corsair)
        expect { corsair lacksKeyword Keyword.Flying.kind }
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(corsair)
        expect { corsair hasKeyword Keyword.Flying.kind }
    }

    // CR 603.2c, 119.1: one trigger for two attackers gains 2 life
    @Test
    fun elderGainsOneLifePerAttacker() = scenario {
        alice.battlefield(elder, bear, soldier)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(elder, bear)
        resolveAll()
        expect { alice.life shouldBe 22 }
    }

    // CR 119.1: two life for each Gate you control
    @Test
    fun gatekeeperGainsForEachGate() = scenario {
        alice.battlefield(gate, gate2, "Plains", "Plains", "Plains", "Plains", "Plains", "Plains")
        bob.battlefield(foeGate)
        alice.hand(gatekeeper)
        alice.casts(gatekeeper)
        resolveAll()
        expect { alice.life shouldBe 24 }
    }

    // CR 118.12 / 608.2c: the sacrificed creature's toughness is the life gained
    @Test
    fun pilgrimGainsLifeEqualToSacrificedToughness() = scenario {
        alice.battlefield(pilgrim, wall, "Plains")
        alice.activates(pilgrim, ability = 0)
        resolveAll()
        expect {
            alice.life shouldBe 25
            alice.graveyard has wall
        }
    }

    // CR 602.5b: with fewer than 10 more life than the starting life total the second ability can't be activated
    @Test
    fun pilgrimExilesOnlyWithTenLifeMore() = scenario {
        alice.battlefield(pilgrim, bear, relic, "Plains", "Swamp", "Plains")
        alice.life = 29
        assertFailsWith<Throwable> { alice.activates(pilgrim, ability = 1, target = relic) }
    }

    @Test
    fun pilgrimExilesAtTenLifeMore() = scenario {
        alice.battlefield(pilgrim, bear, relic, "Plains", "Swamp", "Plains")
        alice.life = 30
        alice.activates(pilgrim, ability = 1, target = relic)
        resolveAll()
        expect {
            relic.zone shouldBe Zone.Exile
            alice.graveyard has bear
        }
    }

    // CR 608.2c: gain 2, draw 2
    @Test
    fun cloudmenderGainsAndDraws() = scenario {
        stock()
        alice.battlefield("Plains", "Plains", "Plains", "Island", "Island")
        alice.hand(cloudmender)
        alice.casts(cloudmender)
        resolveAll()
        expect {
            alice.life shouldBe 22
            alice.hand.size shouldBe 2
        }
    }

    @Test
    fun wardenGainsAndDraws() = scenario {
        stock()
        alice.battlefield("Plains", "Plains", "Plains")
        alice.hand(warden)
        alice.casts(warden)
        resolveAll()
        expect {
            alice.life shouldBe 21
            alice.hand.size shouldBe 1
        }
    }

    // CR 508.1m, 105.1: only white attackers trigger it
    @Test
    fun lindenGainsForAWhiteAttackerOnly() = scenario {
        alice.battlefield(linden, bear)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(bear)
        resolveAll()
        expect { alice.life shouldBe 20 }
    }

    @Test
    fun lindenGainsLifePerWhiteAttacker() = scenario {
        alice.battlefield(linden, soldier, knight)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(linden, soldier)
        resolveAll()
        expect { alice.life shouldBe 22 }
    }

    // CR 118.1, 701.21a: sacrifice a creature (even another one's the only one), gain 1 and draw 1
    @Test
    fun riteSacrificesForLifeAndACard() = scenario {
        stock()
        alice.battlefield(rite, bear, "Swamp", "Swamp")
        alice.activates(rite)
        resolveAll()
        expect {
            alice.graveyard has bear
            alice.life shouldBe 21
            alice.hand.size shouldBe 1
        }
    }

    // CR 701.20a, 401.4: of the four cards only the small creature may be taken, the rest go to the bottom
    @Test
    fun marshalTakesASmallCreatureAndBottomsTheRest() = scenario {
        alice.battlefield(marshal, "Plains", "Plains", "Plains")
        alice.library(bigWolf, "Forest", bear, "Swamp", "Island", "Mountain")
        alice.activates(marshal)
        resolveAll()
        alice.chooses(bear)
        resolveAll()
        expect {
            alice.hand has bear
            alice.library.size shouldBe 5
        }
        val names = alice.library.names
        assertEquals("Island", names.first())
        assertEquals(setOf(bigWolf, "Forest", "Swamp"), names.takeLast(3).toSet())
    }

    // CR 701.20a: choosing nothing puts all four on the bottom
    @Test
    fun marshalMayTakeNothing() = scenario {
        alice.battlefield(marshal, "Plains", "Plains", "Plains")
        alice.library(bear, "Forest", "Swamp", "Island", "Mountain")
        alice.activates(marshal)
        resolveAll()
        alice.chooses()
        resolveAll()
        expect { alice.hand.size shouldBe 0 }
        assertEquals("Mountain", alice.library.names.first())
    }

    // CR 606.4, 701.20a: +1 raises loyalty, takes a land from the top four
    @Test
    fun vivienLooksAndTakesALand() = scenario {
        alice.battlefield(vivien, counters = listOf(CounterKind.Loyalty to 5))
        alice.library(bear, "Forest", "Swamp", "Island", "Mountain")
        alice.activates(vivien, ability = 0)
        resolveAll()
        alice.chooses("Forest")
        resolveAll()
        expect {
            vivien.counters(CounterKind.Loyalty) shouldBe 6
            alice.hand has "Forest"
        }
        assertEquals("Mountain", alice.library.names.first())
    }

    // CR 701.8a: -3 destroys a flier
    @Test
    fun vivienDestroysAFlyingCreature() = scenario {
        alice.battlefield(vivien, counters = listOf(CounterKind.Loyalty to 5))
        bob.battlefield(bat)
        alice.activates(vivien, ability = 1, target = bat)
        resolveAll()
        expect {
            vivien.counters(CounterKind.Loyalty) shouldBe 2
            bob.graveyard has bat
        }
    }

    // CR 114.1, 114.4, 613.1f: the emblem pumps and grants keywords to your creatures only
    @Test
    fun vivienEmblemPumpsYourCreatures() = scenario {
        alice.battlefield(vivien, counters = listOf(CounterKind.Loyalty to 8))
        alice.battlefield(bear)
        bob.battlefield(soldier)
        alice.activates(vivien, ability = 2)
        resolveAll()
        expect {
            bear.power shouldBe 4
            bear hasKeyword Keyword.Vigilance.kind
            bear hasKeyword Keyword.Trample.kind
            bear hasKeyword Keyword.Indestructible.kind
            soldier.power shouldBe 1
        }
    }
}
