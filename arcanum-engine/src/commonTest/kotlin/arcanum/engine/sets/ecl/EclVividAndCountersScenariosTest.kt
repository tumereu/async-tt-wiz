package arcanum.engine.sets.ecl

import arcanum.api.CounterKind
import arcanum.api.Keyword
import arcanum.api.Mechanic
import arcanum.api.TurnStep
import arcanum.api.Zone
import arcanum.engine.cards.Cards
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.scenario.Scenario
import arcanum.engine.scenario.scenario
import arcanum.engine.sets.ecl.Ecl.bear
import arcanum.engine.sets.ecl.Ecl.elf
import arcanum.engine.sets.ecl.Ecl.jolt
import arcanum.engine.sets.ecl.Ecl.kithkin
import arcanum.engine.sets.ecl.Ecl.m1m1
import arcanum.engine.sets.ecl.Ecl.merfolk
import arcanum.engine.sets.ecl.Ecl.ogre
import arcanum.engine.sets.ecl.Ecl.ponder
import arcanum.engine.sets.ecl.Ecl.redBear
import kotlin.test.Test
import kotlin.test.assertEquals

/** Lorwyn Eclipsed's vivid cards and creatures that enter with -1/-1 counters in play (chunks 3–4): counts of colours among
 * permanents, costs that remove counters, counters removed by triggers, and a mode that removes all counters. */
class EclVividAndCountersScenariosTest {
    private val hoarfrostGrip = Cards.invent("Hoarfrost Grip", "{6}{U}", "Instant", """
        Vivid — This spell costs {1} less to cast for each color among permanents you control.
        Tap up to two target creatures. Put a stun counter on each of them. (If a permanent with a stun counter would become untapped, remove one from it instead.)
        Draw a card.
    """)
    private val glimmerslink = Cards.invent("Glimmerslink", "{4}{B}", "Creature — Elemental", """
        Menace (This creature can't be blocked except by two or more creatures.)
        Vivid — When this creature enters, each opponent loses X life and you gain X life, where X is the number of colors among permanents you control.
    """, "3/5")
    private val volatileWhiz = Cards.invent("Volatile Whiz", "{1}{R}", "Creature — Elemental Sorcerer", "Vivid — When this creature enters, it deals X damage to target creature an opponent controls, where X is the number of colors among permanents you control.", "1/1")
    private val dawnglowRouser = Cards.invent("Dawnglow Rouser", "{6}{G}", "Creature — Giant Druid", """
        Trample
        Vivid — When this creature enters, reveal cards from the top of your library until you reveal X permanent cards, where X is the number of colors among permanents you control. Put any number of those permanent cards onto the battlefield, then put the rest of the revealed cards on the bottom of your library in a random order.
    """, "7/7")
    private val glowshellSlug = Cards.invent("Glowshell Slug", "{3}{G}", "Creature — Elemental", """
        Deathtouch
        Vivid — When this creature enters, you gain life equal to the number of colors among permanents you control.
    """, "2/4")
    private val prismsmasher = Cards.invent("Prismsmasher", "{4}{G}{G}", "Creature — Elemental", """
        Trample
        Vivid — When this creature enters, up to X target creatures you control get +X/+X until end of turn, where X is the number of colors among permanents you control.
    """, "6/6")
    private val spectralRiptides = Cards.invent("Spectral Riptides", "{3}{G}", "Enchantment", """
        Vivid — When this enchantment enters, search your library for up to X basic land cards, where X is the number of colors among permanents you control. Reveal those cards, put them into your hand, then shuffle.
        You may play an additional land on each of your turns.
    """)
    private val glimmerWean = Cards.invent("Glimmer Wean", "{2}{G/U}{G/U}{G/U}", "Creature — Ouphe", "Vivid — At the beginning of combat on your turn, another target creature you control gets +X/+X until end of turn, where X is the number of colors among permanents you control.", "1/4")
    private val pell = Cards.invent("Pell, Inventive Freshling", "{2}{U/R}{U/R}", "Legendary Creature — Goblin Sorcerer", "Vivid — At the beginning of your first main phase, reveal cards from the top of your library until you reveal X nonland cards, where X is the number of colors among permanents you control. For each of those colors, you may exile a card of that color from among the revealed cards. Then shuffle. You may cast the exiled cards this turn.", "2/4")
    private val ladenShalehide = Cards.invent("Laden Shalehide", "{1}{W}", "Creature — Giant Warrior", """
        This creature enters with two -1/-1 counters on it.
        {1}{W}, Remove a counter from this creature: Target creature gains indestructible until end of turn. Activate only as a sorcery. (Damage and effects that say "destroy" don't destroy it. If its toughness is 0 or less, it still dies.)
    """, "4/4")
    private val moonglowMourner = Cards.invent("Moonglow Mourner", "{2}{W}", "Creature — Treefolk Cleric", """
        This creature enters with a -1/-1 counter on it.
        {1}{W}, Remove a counter from this creature: Draw a card. Activate only as a sorcery.
    """, "2/5")
    private val hesitantHillwarden = Cards.invent("Hesitant Hillwarden", "{2}{W}", "Creature — Kithkin Soldier", """
        This creature enters with two -1/-1 counters on it.
        Whenever another creature you control enters while this creature has a -1/-1 counter on it, remove a -1/-1 counter from this creature.
    """, "4/4")
    private val drowsingStrider = Cards.invent("Drowsing Strider", "{3}{W}{W}", "Creature — Giant Warrior", """
        This creature enters with two -1/-1 counters on it.
        At the beginning of your end step, you may remove a counter from this creature. When you do, return target creature card with power 2 or less from your graveyard to the battlefield.
    """, "4/7")
    private val fluttermothPest = Cards.invent("Fluttermoth Pest", "{U}", "Creature — Faerie Rogue", """
        Flying
        This creature enters with a -1/-1 counter on it.
        {2}{U}, Remove a counter from this creature: Whenever a creature you control deals combat damage to a player or planeswalker this turn, draw a card.
    """, "2/2")
    private val valeSylvaneWarden = Cards.invent("Vale Sylvane Warden", "{2}{U}", "Creature — Faerie Wizard", """
        Flash
        Flying
        This creature enters with a -1/-1 counter on it.
        {1}{U}, Remove a counter from this creature: Counter target noncreature spell. Its controller draws a card.
    """, "3/4")
    private val tarnSteed = Cards.invent("Tarn Steed", "{1}{U}", "Creature — Horse Serpent", """
        This creature enters with three -1/-1 counters on it.
        {1}{U}, Remove a counter from this creature: Draw a card.
        {2}{U}, Remove two counters from this creature: Tap target creature. Put a stun counter on it. (If a permanent with a stun counter would become untapped, remove one from it instead.)
    """, "4/5")
    private val groanbarkKeeper = Cards.invent("Groanbark Keeper", "{1}{B}", "Creature — Elf Warrior", """
        This creature enters with three -1/-1 counters on it.
        At the beginning of your end step, if there is an Elf card in your graveyard and this creature has a -1/-1 counter on it, remove a -1/-1 counter from this creature.
    """, "5/5")
    private val knotbarkOak = Cards.invent("Knotbark Oak", "{2}{B}", "Creature — Treefolk Warlock", """
        This creature enters with two -1/-1 counters on it.
        {2}{B}, Remove two counters from this creature: Target creature gets -2/-2 until end of turn. Activate only as a sorcery.
    """, "3/4")
    private val keepsakeGranny = Cards.invent("Keepsake Granny", "{2}{B}", "Creature — Goblin Warlock", """
        This creature enters with two -1/-1 counters on it.
        Whenever another creature you control dies, surveil 1, then remove a -1/-1 counter from this creature. (To surveil 1, look at the top card of your library. You may put it into your graveyard.)
    """, "4/4")
    private val duskshade = Cards.invent("Duskshade", "{B}", "Creature — Elemental", """
        Menace
        This creature enters with six -1/-1 counters on it.
        Whenever one or more permanent cards are put into your graveyard from anywhere while this creature has a -1/-1 counter on it, remove a -1/-1 counter from this creature.
    """, "7/7")
    private val flawlessMenace = Cards.invent("Flawless Menace", "{3}{B}", "Sorcery", """
        Choose one or both —
        • Target opponent exiles two cards from their hand.
        • Remove all counters from target creature.
    """)
    private val thornhideBruiser = Cards.invent("Thornhide Bruiser", "{2}{R}", "Creature — Giant Warrior", """
        This creature enters with two -1/-1 counters on it.
        {1}{R}, Remove a counter from this creature: Target creature can't block this turn. Activate only as a sorcery.
    """, "4/5")
    private val shantySlinger = Cards.invent("Shanty Slinger", "{3}{R/W}{R/W}", "Creature — Giant Warrior", """
        This creature enters with two -1/-1 counters on it.
        {R/W}{R/W}, Remove a counter from this creature: Another target creature you control gets +1/+0 and gains flying until end of turn. Activate only as a sorcery.
    """, "6/7")
    private val harvestingBirch = Cards.invent("Harvesting Birch", "{1}{W/B}{W/B}{W/B}", "Creature — Treefolk Cleric", """
        Lifelink
        This creature enters with two -1/-1 counters on it.
        {1}{W/B}, Remove two counters from this creature: Return target creature card with mana value 3 or less from your graveyard to the battlefield. Activate only as a sorcery.
    """, "3/6")

    private fun Scenario.targets(vararg names: String) {
        if (pending is Decision.Targets) answer(Answer.Targets(names.map { target(it) }))
    }

    // CR 601.2f: four colours: {2}{U} left to pay; two creatures tapped and stunned; a card drawn
    @Test
    fun hoarfrostGripCostsLessAndStuns() = scenario {
        alice.battlefield("Island", "Island", "Island", merfolk, redBear, bear, kithkin)
        alice.hand(hoarfrostGrip)
        alice.library(elf)
        bob.battlefield(ogre, Ecl.giant)
        alice.casts(hoarfrostGrip, targets = listOf(ogre, Ecl.giant))
        resolveAll()
        expect {
            ogre.tapped shouldBe true
            Ecl.giant.counters(CounterKind.Stun) shouldBe 1
            alice.hand has elf
        }
    }

    // CR 105.4: black and red: each opponent loses 2, you gain 2
    @Test
    fun glimmerslinkDrains() = scenario {
        alice.battlefield("Swamp", "Swamp", "Swamp", "Swamp", "Swamp", redBear)
        alice.hand(glimmerslink)
        alice.casts(glimmerslink)
        resolveAll()
        assertEquals(18, bob.life)
        assertEquals(22, alice.life)
    }

    @Test
    fun volatileWhizDealsTheColorCount() = scenario {
        alice.battlefield("Mountain", "Mountain", bear)
        alice.hand(volatileWhiz)
        bob.battlefield(redBear)
        alice.casts(volatileWhiz)
        resolveAll()
        targets(redBear)
        resolveAll()
        expect { bob.graveyard has redBear }
    }

    // CR 701.20: until two permanent cards are revealed; the rest to the bottom
    @Test
    fun dawnglowRouserPutsPermanentsOntoTheBattlefield() = scenario {
        alice.battlefield("Forest", "Forest", "Forest", "Forest", "Forest", "Forest", "Forest", redBear)
        alice.hand(dawnglowRouser)
        alice.library(ponder, bear, ogre, elf)
        alice.casts(dawnglowRouser)
        resolveAll()
        if (pending is Decision.ChooseObjects) alice.chooses(bear, ogre)
        resolveAll()
        expect {
            alice.battlefield has bear
            alice.battlefield has ogre
            alice.library has ponder
        }
        // the unrevealed card stays on top; the revealed sorcery went to the bottom
        assertEquals(elf, state.obj(state.player(alice.id).library.first())?.definition?.faces?.get(0)?.name)
    }

    @Test
    fun glowshellSlugGainsLife() = scenario {
        alice.battlefield("Forest", "Forest", "Forest", "Forest", redBear)
        alice.hand(glowshellSlug)
        alice.casts(glowshellSlug)
        resolveAll()
        assertEquals(22, alice.life)
    }

    // CR 115.1: up to X targets, each +X/+X
    @Test
    fun prismsmasherPumpsUpToXCreatures() = scenario {
        alice.battlefield("Forest", "Forest", "Forest", "Forest", "Forest", "Forest", redBear)
        alice.hand(prismsmasher)
        alice.casts(prismsmasher)
        resolveAll()
        targets(redBear, prismsmasher)
        resolveAll()
        expect {
            redBear.power shouldBe 4
            prismsmasher.toughness shouldBe 8
        }
    }

    // CR 305.2: two basic lands found; an additional land play
    @Test
    fun spectralRiptidesFindsLandsAndPlaysTwo() = scenario {
        alice.battlefield("Forest", "Forest", "Forest", "Forest", redBear)
        alice.hand(spectralRiptides)
        alice.library("Forest", "Mountain", bear)
        alice.casts(spectralRiptides)
        resolveAll()
        if (pending is Decision.ChooseObjects) alice.chooses("Forest", "Mountain")
        resolveAll()
        alice.plays("Forest")
        alice.plays("Mountain")
        expect { alice.battlefield.names.count { it == "Forest" } shouldBe 5 }
    }

    // green, blue and red: +3/+3 at the beginning of combat
    @Test
    fun glimmerWeanPumpsAtCombat() = scenario {
        alice.battlefield(glimmerWean, redBear)
        advanceTo(TurnStep.BeginningOfCombat)
        resolveAll()
        targets(redBear)
        resolveAll()
        expect { redBear.power shouldBe 5 }
    }

    // CR 701.20: two nonland cards revealed (blue and red); one of each colour exiled and castable this turn
    @Test
    fun pellExilesCardsToCast() = scenario {
        alice.battlefield("Mountain", pell)
        // the ogre is drawn; blue and red: the first two nonland cards revealed are the red Spark Jolt and the blue Quiet Study
        alice.library(ogre, jolt, "Mountain", ponder, bear)
        bob.library(bear)
        advanceTo(TurnStep.PrecombatMain, turn = 3)
        resolveAll()
        // one card of each of those colours, chosen together
        if (pending is Decision.ChooseObjects) alice.chooses(jolt, ponder)
        resolveAll()
        expect {
            alice.exile has jolt
            alice.exile has ponder
        }
        alice.casts(jolt, target = "bob")
        resolveAll()
        assertEquals(18, bob.life)
    }

    // CR 122.6: enters with two counters; removing one as a cost gives indestructible
    @Test
    fun ladenShalehideGivesIndestructible() = scenario {
        alice.battlefield("Plains", "Plains", "Plains", "Plains")
        alice.hand(ladenShalehide)
        alice.casts(ladenShalehide)
        resolveAll()
        expect { ladenShalehide.power shouldBe 2 }
        alice.activates(ladenShalehide, target = ladenShalehide)
        resolveAll()
        expect {
            ladenShalehide.counters(m1m1) shouldBe 1
            ladenShalehide hasKeyword Keyword.Indestructible
        }
    }

    @Test
    fun moonglowMournerDrawsForACounter() = scenario {
        alice.battlefield("Plains", "Plains", "Plains", "Plains", "Plains")
        alice.hand(moonglowMourner)
        alice.library(bear)
        alice.casts(moonglowMourner)
        resolveAll()
        expect { moonglowMourner.counters(m1m1) shouldBe 1 }
        alice.activates(moonglowMourner)
        resolveAll()
        expect {
            moonglowMourner.counters(m1m1) shouldBe 0
            alice.hand has bear
        }
    }

    // CR 603.2: "while this creature has a -1/-1 counter on it"
    @Test
    fun hesitantHillwardenLosesACounterWhenACreatureEnters() = scenario {
        alice.battlefield("Forest")
        alice.battlefield(hesitantHillwarden, counters = listOf(m1m1 to 2))
        alice.hand(elf)
        alice.casts(elf)
        resolveAll()
        expect { hesitantHillwarden.counters(m1m1) shouldBe 1 }
    }

    // CR 603.12: removing the counter makes the reflexive trigger that returns a small creature
    @Test
    fun drowsingStriderReturnsASmallCreature() = scenario {
        alice.library(bear)
        bob.library(bear)
        alice.battlefield(drowsingStrider, counters = listOf(m1m1 to 2))
        alice.graveyard(redBear, ogre)
        advanceTo(TurnStep.End)
        resolveAll()
        say(true)
        resolveAll()
        targets(redBear)
        resolveAll()
        expect {
            alice.battlefield has redBear
            drowsingStrider.counters(m1m1) shouldBe 1
        }
    }

    // CR 603.7: a delayed trigger for the rest of the turn: combat damage to a player draws a card
    @Test
    fun fluttermothPestDrawsOnCombatDamage() = scenario {
        alice.library(elf, elf)
        bob.library(bear)
        alice.battlefield("Island", "Island", "Island", bear)
        alice.battlefield(fluttermothPest, counters = listOf(m1m1 to 1))
        alice.activates(fluttermothPest)
        resolveAll()
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(bear)
        advanceTo(TurnStep.EndOfCombat)
        resolveAll()
        expect { alice.hand has elf }
        assertEquals(18, bob.life)
    }

    // CR 701.6a: the noncreature spell is countered and its controller draws
    @Test
    fun valeSylvaneWardenCounters() = scenario {
        alice.battlefield("Island", "Island")
        alice.battlefield(valeSylvaneWarden, counters = listOf(m1m1 to 1))
        bob.battlefield("Mountain")
        bob.hand(jolt)
        bob.library(bear)
        turn(active = bob)
        bob.casts(jolt, target = "alice")
        bob.passes()
        alice.activates(valeSylvaneWarden, target = jolt)
        resolveAll()
        expect {
            bob.graveyard has jolt
            bob.hand has bear
        }
        assertEquals(20, alice.life)
    }

    // removing two counters: tap and stun
    @Test
    fun tarnSteedTapsAndStuns() = scenario {
        alice.battlefield("Island", "Island", "Island")
        alice.battlefield(tarnSteed, counters = listOf(m1m1 to 3))
        bob.battlefield(ogre)
        alice.activates(tarnSteed, ability = 1, target = ogre)
        resolveAll()
        expect {
            tarnSteed.counters(m1m1) shouldBe 1
            ogre.tapped shouldBe true
            ogre.counters(CounterKind.Stun) shouldBe 1
        }
    }

    @Test
    fun tarnSteedDraws() = scenario {
        alice.battlefield("Island", "Island")
        alice.battlefield(tarnSteed, counters = listOf(m1m1 to 3))
        alice.library(bear)
        alice.activates(tarnSteed)
        resolveAll()
        expect {
            tarnSteed.counters(m1m1) shouldBe 2
            alice.hand has bear
        }
    }

    // CR 603.4: the intervening if: an Elf card in the graveyard
    @Test
    fun groanbarkKeeperRecoversWithAnElfInTheGraveyard() = scenario {
        alice.library(bear)
        bob.library(bear)
        alice.battlefield(groanbarkKeeper, counters = listOf(m1m1 to 3))
        alice.graveyard(elf)
        advanceTo(TurnStep.End)
        resolveAll()
        expect { groanbarkKeeper.counters(m1m1) shouldBe 2 }
    }

    @Test
    fun groanbarkKeeperNeedsAnElfCard() = scenario {
        alice.library(bear)
        bob.library(bear)
        alice.battlefield(groanbarkKeeper, counters = listOf(m1m1 to 3))
        alice.graveyard(bear)
        advanceTo(TurnStep.End)
        resolveAll()
        expect { groanbarkKeeper.counters(m1m1) shouldBe 3 }
    }

    @Test
    fun knotbarkOakShrinks() = scenario {
        alice.battlefield("Swamp", "Swamp", "Swamp")
        alice.battlefield(knotbarkOak, counters = listOf(m1m1 to 2))
        bob.battlefield(redBear)
        alice.activates(knotbarkOak, target = redBear)
        resolveAll()
        expect {
            bob.graveyard has redBear
            knotbarkOak.counters(m1m1) shouldBe 0
        }
    }

    // CR 701.25a: surveil 1, then a counter removed
    @Test
    fun keepsakeGrannyRecoversWhenACreatureDies() = scenario {
        alice.battlefield("Mountain", bear)
        alice.battlefield(keepsakeGranny, counters = listOf(m1m1 to 2))
        alice.hand(jolt)
        alice.library(ogre)
        alice.casts(jolt, target = bear)
        resolveAll()
        if (pending is Decision.ChooseObjects) alice.chooses(ogre)
        resolveAll()
        expect {
            keepsakeGranny.counters(m1m1) shouldBe 1
            alice.graveyard has ogre
        }
    }

    // CR 603.2c: "one or more permanent cards": one trigger for the creature card, none for the instant
    @Test
    fun duskshadeRecoversAsPermanentCardsArePutIntoYourGraveyard() = scenario {
        alice.battlefield("Mountain", bear)
        alice.battlefield(duskshade, counters = listOf(m1m1 to 6))
        alice.hand(jolt)
        alice.casts(jolt, target = bear)
        resolveAll()
        expect { duskshade.counters(m1m1) shouldBe 5 }
    }

    // CR 700.2: both modes: the opponent exiles two cards; all counters removed
    @Test
    fun flawlessMenaceBothModes() = scenario {
        alice.battlefield("Swamp", "Swamp", "Swamp", "Swamp")
        alice.battlefield(ogre, counters = listOf(m1m1 to 2, CounterKind.Stun to 1))
        alice.hand(flawlessMenace)
        bob.hand(bear, redBear, jolt)
        alice.casts(flawlessMenace, modes = listOf(0, 1), targets = listOf("bob", ogre))
        resolveAll()
        if (pending is Decision.ChooseObjects) bob.chooses(bear, redBear)
        resolveAll()
        expect {
            bob.exile has bear
            bob.exile has redBear
            bob.hand has jolt
            ogre.counters(m1m1) shouldBe 0
            ogre.counters(CounterKind.Stun) shouldBe 0
        }
    }

    @Test
    fun thornhideBruiserStopsABlocker() = scenario {
        alice.battlefield("Mountain", "Mountain")
        alice.battlefield(thornhideBruiser, counters = listOf(m1m1 to 2))
        bob.battlefield(ogre)
        alice.activates(thornhideBruiser, target = ogre)
        resolveAll()
        expect {
            ogre hasMechanic { it is Mechanic.CantBlock }
            thornhideBruiser.counters(m1m1) shouldBe 1
        }
    }

    @Test
    fun shantySlingerGivesFlying() = scenario {
        alice.battlefield("Mountain", "Plains", bear)
        alice.battlefield(shantySlinger, counters = listOf(m1m1 to 2))
        alice.activates(shantySlinger, target = bear)
        resolveAll()
        expect {
            bear.power shouldBe 3
            bear hasKeyword Keyword.Flying
        }
    }

    @Test
    fun harvestingBirchReturnsACreature() = scenario {
        alice.battlefield("Plains", "Swamp")
        alice.battlefield(harvestingBirch, counters = listOf(m1m1 to 2))
        alice.graveyard(ogre)
        alice.activates(harvestingBirch, target = ogre)
        resolveAll()
        expect {
            alice.battlefield has ogre
            harvestingBirch.counters(m1m1) shouldBe 0
        }
    }
}
