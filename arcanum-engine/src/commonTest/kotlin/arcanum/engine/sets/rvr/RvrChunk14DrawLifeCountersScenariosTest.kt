package arcanum.engine.sets.rvr

import arcanum.api.CounterKind
import arcanum.api.Keyword
import arcanum.api.TurnStep
import arcanum.engine.cards.Cards
import arcanum.engine.scenario.scenario
import arcanum.engine.sets.blb.resolveAll
import kotlin.test.Test
import kotlin.test.assertFailsWith

/** Ravnica Remastered chunk 14 in play: a sorcery-speed draw, a planeswalker that draws on every death, keywords granted by a
 * condition (a Gate, your turn), blocking limits, mana of any color, anthem lines by color and a life-gain counter engine.
 * The cards that only differ in wording from tested ones (coin flip, drain, bloodthirst, counters on death) get parser tests. */
class RvrChunk14DrawLifeCountersScenariosTest {
    private val guildmage = Cards.invent("Rvr14 Dusk Guildmage", "{U/B}{U/B}", "Creature — Human Wizard", """
        {3}{U}: Target player draws a card. Activate only as a sorcery.
        {3}{B}: Target player discards a card. Activate only as a sorcery.
    """, "2/2")
    private val liliana = Cards.invent("Rvr14 Gloom General", "{4}{B}{B}", "Legendary Planeswalker — Liliana", """
        Whenever a creature you control dies, draw a card.
        [+1]: Create a 2/2 black Zombie creature token.
        [−4]: Each player sacrifices two creatures of their choice.
        [−9]: Each opponent chooses a permanent they control of each permanent type and sacrifices the rest.
    """, loyalty = "6")
    private val armoryGuard = Cards.invent("Rvr14 Gatehouse Giant", "{3}{W}", "Creature — Giant Soldier",
        "This creature has vigilance as long as you control a Gate.", "2/5")
    private val gate = Cards.invent("Rvr14 Stone Gate", null, "Land — Gate", """
        This land enters tapped.
        {T}: Add {W} or {U}.
    """)
    private val lantern = Cards.invent("Rvr14 Prism Lantern", "{3}", "Artifact", """
        Lands you control have "{T}: Add one mana of any color."
        {T}: Add one mana of any color.
    """)
    private val peek = Cards.invent("Rvr14 Twin Peek", "{U}{R}", "Instant", "You gain 2 life.")
    private val recruit = Cards.invent("Rvr14 Eager Recruit", "{1}{R/W}", "Creature — Human Soldier",
        "During your turn, this creature has first strike.", "2/1")
    private val shaman = Cards.invent("Rvr14 Giant Wrecker", "{R}{R}{G}{G}", "Creature — Giant Shaman", """
        This creature can't be blocked by more than one creature.
        Whenever this creature deals combat damage to a player, destroy target artifact or enchantment that player controls.
    """, "5/5")
    private val tolsimir = Cards.invent("Rvr14 Wolfblood Elder", "{4}{G}{W}", "Legendary Creature — Elf Warrior", """
        Other green creatures you control get +1/+1.
        Other white creatures you control get +1/+1.
        {T}: Create Voja, a legendary 2/2 green and white Wolf creature token.
    """, "3/4")
    private val karlov = Cards.invent("Rvr14 Ghost Council Chair", "{W}{B}", "Legendary Creature — Spirit Advisor", """
        Whenever you gain life, put two +1/+1 counters on this creature.
        {W}{B}, Remove six +1/+1 counters from this creature: Exile target creature.
    """, "2/2")
    private val greenBear = Cards.invent("Rvr14 Green Bear", "{1}{G}", "Creature — Bear", "", "2/2")
    private val whiteKnight = Cards.invent("Rvr14 White Knight", "{1}{W}", "Creature — Knight", "", "2/2")
    private val dualElf = Cards.invent("Rvr14 Dual Elf", "{G}{W}", "Creature — Elf", "", "2/2")
    private val redOgre = Cards.invent("Rvr14 Red Ogre", "{2}{R}", "Creature — Ogre", "", "3/3")
    private val bear = Cards.invent("Rvr14 Plain Bear", "{1}{G}", "Creature — Bear", "", "2/2")
    private val sip = Cards.invent("Rvr14 Sip", "{W}", "Instant", "You gain 3 life.")

    // CR 602.5d, 307.5: sorcery-speed only; CR 121.1: the targeted player (an opponent of three) draws, not the activator
    @Test
    fun guildmageMakesAnOpponentDrawOnlyAtSorcerySpeed() = scenario(players = 3) {
        alice.battlefield(guildmage, "Island", "Island", "Island", "Island", "Island", "Island", "Island", "Island")
        carol.library("Forest", "Forest")
        alice.activates(guildmage, target = "carol")
        resolveAll()
        expect {
            carol.hand.size shouldBe 1
            alice.hand.size shouldBe 0
            bob.hand.size shouldBe 0
        }
        advanceTo(TurnStep.DeclareAttackers)
        assertFailsWith<Throwable> { alice.activates(guildmage, target = "carol") }
    }

    // CR 700.4, 603.2: each of your own creatures that dies draws you a card; the others' don't. CR 701.21a: each player sacrifices
    // two creatures (three players)
    @Test
    fun lilianaMinusFourDrawsForEachOfYourCreaturesOnly() = scenario(players = 3) {
        alice.battlefield(liliana, bear, bear, counters = listOf(CounterKind.Loyalty to 6))
        bob.battlefield(bear, bear)
        carol.battlefield(bear, bear)
        alice.library("Forest", "Forest", "Forest")
        alice.activates(liliana, ability = 1)
        resolveAll()
        resolveAll()
        expect {
            alice.battlefield.names shouldBe listOf(liliana)
            bob.graveyard.size shouldBe 2
            carol.graveyard.size shouldBe 2
            alice.hand.size shouldBe 2
            bob.hand.size shouldBe 0
        }
    }

    // CR 611.3a, 702.20: vigilance only while a Gate is controlled; an opponent's Gate doesn't count
    @Test
    fun armoryGuardHasVigilanceOnlyWithAGate() = scenario {
        alice.battlefield(armoryGuard)
        bob.battlefield(gate)
        expect { armoryGuard lacksKeyword Keyword.Vigilance }
    }

    @Test
    fun armoryGuardHasVigilanceWithYourGate() = scenario {
        alice.battlefield(armoryGuard, gate)
        expect { armoryGuard hasKeyword Keyword.Vigilance }
    }

    // CR 605.1a, 106.1b: the Forest and the Lantern each make any color, so a {U}{R} spell is castable
    @Test
    fun lanternMakesLandsAndItselfProduceAnyColor() = scenario {
        alice.battlefield(lantern, "Forest")
        alice.hand(peek)
        alice.casts(peek)
        resolveAll()
        expect { alice.life shouldBe 22 }
    }

    // CR 611.3a: first strike only in your own turn, so the 2/1 beats a 2/2 blocker on offence and loses to it on defence
    @Test
    fun recruitStrikesFirstOnlyDuringItsControllersTurn() = scenario {
        alice.battlefield(recruit)
        bob.battlefield(whiteKnight)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(recruit)
        advanceTo(TurnStep.DeclareBlockers)
        bob.blocks(whiteKnight to recruit)
        advanceTo(TurnStep.EndOfCombat)
        expect {
            recruit hasKeyword Keyword.FirstStrike
            alice.battlefield has recruit
            bob.graveyard has whiteKnight
        }
    }

    @Test
    fun recruitHasNoFirstStrikeInTheOpponentsTurn() = scenario {
        alice.battlefield(recruit)
        bob.battlefield(whiteKnight)
        turn(active = bob)
        advanceTo(TurnStep.DeclareAttackers)
        expect { recruit lacksKeyword Keyword.FirstStrike }
        bob.attacks(whiteKnight)
        advanceTo(TurnStep.DeclareBlockers)
        alice.blocks(recruit to whiteKnight)
        advanceTo(TurnStep.EndOfCombat)
        expect { alice.graveyard has recruit }
    }

    // CR 509.1b: a second blocker is refused, one blocker is fine
    @Test
    fun shamanCannotBeBlockedByTwoCreatures() = scenario {
        alice.battlefield(shaman)
        bob.battlefield(greenBear, whiteKnight)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(shaman)
        advanceTo(TurnStep.DeclareBlockers)
        assertFailsWith<Throwable> { bob.blocks(greenBear to shaman, whiteKnight to shaman) }
        bob.blocks(greenBear to shaman)
        advanceTo(TurnStep.EndOfCombat)
        expect { bob.graveyard has greenBear }
    }

    // CR 613.4c, 205.1: each line counts the creature's colors; "other" excludes the source; a green-white creature gets both
    @Test
    fun tolsimirPumpsOtherGreenAndWhiteCreatures() = scenario {
        alice.battlefield(tolsimir, greenBear, whiteKnight, dualElf, redOgre)
        bob.battlefield(bear)
        expect {
            tolsimir.power shouldBe 3
            greenBear.power shouldBe 3
            whiteKnight.power shouldBe 3
            dualElf.power shouldBe 4
            redOgre.power shouldBe 3
            bear.power shouldBe 2
        }
    }

    // CR 111.1, 613.4c: the Wolf token is green and white, so it gets +2/+2 from the two lines
    @Test
    fun tolsimirsWolfTokenGetsBothBonuses() = scenario {
        alice.battlefield(tolsimir)
        alice.activates(tolsimir)
        resolveAll()
        expect {
            alice.battlefield.names.count { it == "Voja" } shouldBe 1
            card("Voja").power shouldBe 4
            card("Voja").toughness shouldBe 4
        }
    }

    // CR 119.3, 603.2: each life gain gives two counters; CR 602.2b, 701.13a: six counters pay for exile
    @Test
    fun karlovGrowsOnLifeGainAndExilesWithSixCounters() = scenario {
        alice.battlefield(karlov, "Plains", "Plains", "Swamp", "Plains")
        alice.hand(sip)
        bob.battlefield(redOgre)
        alice.casts(sip)
        resolveAll()
        expect {
            karlov.power shouldBe 4
            alice.life shouldBe 23
        }
    }

    @Test
    fun karlovExilesACreatureByRemovingSixCounters() = scenario {
        alice.battlefield(karlov, "Plains", "Swamp", counters = listOf(CounterKind.PowerToughness(1, 1) to 6))
        bob.battlefield(redOgre)
        expect { karlov.power shouldBe 8 }
        alice.activates(karlov, target = redOgre)
        resolveAll()
        expect {
            bob.exile has redOgre
            karlov.power shouldBe 2
        }
    }
}
