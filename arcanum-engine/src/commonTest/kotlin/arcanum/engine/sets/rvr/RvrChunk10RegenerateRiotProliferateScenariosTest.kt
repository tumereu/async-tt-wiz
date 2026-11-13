package arcanum.engine.sets.rvr

import arcanum.engine.decision.Answer
import arcanum.api.CounterKind
import arcanum.api.Keyword
import arcanum.api.TurnStep
import arcanum.api.Zone
import arcanum.engine.cards.Cards
import arcanum.engine.scenario.scenario
import arcanum.engine.sets.blb.may
import arcanum.engine.sets.blb.option
import arcanum.engine.sets.blb.resolveAll
import arcanum.engine.sets.fra.chooseIfOffered
import arcanum.engine.sets.ecl.paySuggested
import arcanum.engine.sets.ltr.playOut
import kotlin.test.Test
import kotlin.test.assertEquals

/** Ravnica Remastered chunk 10 in play: regeneration from counters, a hellbent granted regeneration, jump-start copying,
 * proliferate with a choice among three players, riot, extort with several opponents and haunt with a damaged-creature
 * target. Evolve, fear and the plain keyword cards are mechanically the same as tested ones. */
class RvrChunk10RegenerateRiotProliferateScenariosTest {
    private val p1p1 = CounterKind.PowerToughness(1, 1)
    private val specimen = Cards.invent("Rvr10 Experiment Uno", "{G}", "Creature — Human Ooze", """
        Evolve (Whenever a creature you control enters, if that creature has greater power or toughness than this creature, put a +1/+1 counter on this creature.)
        Remove two +1/+1 counters from this creature: Regenerate it. (The next time this creature would be destroyed this turn, instead tap it, remove it from combat, and heal all damage on it.)
    """, "1/1")
    private val big = Cards.invent("Rvr10 Big Frog", "{1}{G}", "Creature — Frog", "", "4/4")
    private val smite = Cards.invent("Rvr10 Smite", "{R}", "Instant", "Destroy target creature.")
    private val spark = Cards.invent("Rvr10 Spark", "{R}", "Instant", "Rvr10 Spark deals 1 damage to target creature.")
    private val mice = Cards.invent("Rvr10 Gobhobbler Mice", "{B}{R}", "Creature — Rat", """
        Hellbent — As long as you have no cards in hand, this creature gets +1/+0 and has "{B}: Regenerate this creature." (The next time it would be destroyed this turn, instead tap it, remove it from combat, and heal all damage on it.)
    """, "2/2")
    private val bear = Cards.invent("Rvr10 Bear", "{1}{G}", "Creature — Bear", "", "2/2")
    private val ogre = Cards.invent("Rvr10 Ogre", "{2}{R}", "Creature — Ogre", "", "3/3")
    private val copier = Cards.invent("Rvr10 Quasimultiply", "{1}{U}{U}", "Sorcery", """
        Create a token that's a copy of target creature you control.
        Jump-start (You may cast this card from your graveyard by discarding a card in addition to paying its other costs. Then exile this card.)
    """)
    private val wavebreaker = Cards.invent("Rvr10 Wavebreaker", "{5}{U}", "Creature — Leviathan", """
        When this creature enters, proliferate. (Choose any number of permanents and/or players, then give each another counter of each kind already there.)
    """, "5/6")
    private val vandal = Cards.invent("Rvr10 Burning-Bush Vandal", "{2}{R}", "Creature — Human Rogue", """
        Riot (This creature enters with your choice of a +1/+1 counter or haste.)
        Whenever this creature attacks, you may discard a card. If you do, draw a card.
    """, "2/1")
    private val rendbeast = Cards.invent("Rvr10 Rendbeast", "{4}{G}", "Creature — Beast", "Riot (This creature enters with your choice of a +1/+1 counter or haste.)", "4/4")
    private val guards = Cards.invent("Rvr10 Basilica Wardens", "{2}{W}", "Creature — Human Soldier", """
        Defender
        Extort (Whenever you cast a spell, you may pay {W/B}. If you do, each opponent loses 1 life and you gain that much life.)
    """, "1/4")
    private val executioner = Cards.invent("Rvr10 Orzhov Executioner", "{2}{B}", "Creature — Human Assassin", """
        Haunt (When this creature dies, exile it haunting target creature.)
        When this creature enters or the creature it haunts dies, destroy target creature that was dealt damage this turn.
    """, "2/2")
    private val gatekeeper = Cards.invent("Rvr10 Dimir Gatekeeper", "{3}{B}", "Creature — Skeleton", """
        Fear (This creature can't be blocked except by artifact creatures and/or black creatures.)
        Sacrifice a creature: Regenerate this creature.
    """, "2/3")

    /** Two counters paid as the cost: the destroy spell only taps it instead (CR 701.19a); evolve gave the counters. */
    // CR 702.100a, 701.19a, 614.8
    @Test
    fun evolveCountersPayForRegeneration() = scenario {
        alice.battlefield(specimen, "Forest", "Forest", "Forest", counters = listOf(p1p1 to 2))
        bob.battlefield("Mountain")
        bob.hand(smite)
        alice.hand(big)
        alice.casts(big)
        playOut()
        expect { specimen.counters(p1p1) shouldBe 3 }
        alice.activates(specimen)
        resolveAll()
        expect { specimen.counters(p1p1) shouldBe 1 }
        alice.passes()
        bob.casts(smite, target = specimen)
        resolveAll()
        expect {
            specimen.zone shouldBe Zone.Battlefield
            specimen.tapped shouldBe true
        }
    }

    /** With no cards in hand it is 3/2 and can regenerate for {B}; one card in hand and it is a plain 2/2. */
    // CR 701.19a, 613.4c, 604.2
    @Test
    fun hellbentMicePumpAndRegenerate() = scenario {
        alice.battlefield(mice, "Swamp")
        bob.battlefield("Mountain")
        bob.hand(smite)
        expect { mice.power shouldBe 3 }
        turn(active = bob)
        bob.casts(smite, target = mice)
        bob.passes()
        alice.activates(mice)
        resolveAll()
        expect {
            mice.zone shouldBe Zone.Battlefield
            mice.tapped shouldBe true
        }
    }

    // CR 207.2c, 604.2: a card in hand ends hellbent
    @Test
    fun micePlainWithACardInHand() = scenario {
        alice.battlefield(mice)
        alice.hand(bear)
        expect { mice.power shouldBe 2 }
    }

    /** Jump-start: cast from the graveyard discarding a card; the token copies the bear and the spell is exiled. */
    // CR 702.133a, 707.2, 111.2
    @Test
    fun jumpStartCopiesAndExiles() = scenario {
        alice.battlefield("Island", "Island", "Island", bear)
        alice.graveyard(copier)
        alice.hand("Forest")
        alice.casts(copier, target = bear, choosing = listOf("Forest"))
        resolveAll()
        expect {
            alice.battlefield.names.count { it == bear } shouldBe 2
            alice.exile has copier
            alice.graveyard has "Forest"
        }
    }

    /** The controller picks any permanents and players: here a creature and one of two opponents, not the other. */
    // CR 701.34a, 701.34b
    @Test
    fun proliferateChoosesAmongThreePlayers() = scenario(players = 3) {
        alice.battlefield("Island", "Island", "Island", "Island", "Island", "Island", bear, counters = listOf(p1p1 to 1))
        alice.hand(wavebreaker)
        bob.counters(CounterKind.Poison, 3)
        carol.counters(CounterKind.Poison, 2)
        alice.casts(wavebreaker)
        resolveAll()
        answer(Answer.Targets(listOf(target(bear), target("carol"))))
        expect { bear.counters(p1p1) shouldBe 2 }
        assertEquals(3, bob.counters(CounterKind.Poison))
        assertEquals(3, carol.counters(CounterKind.Poison))
    }

    // CR 702.136a: the counter choice
    @Test
    fun riotCounter() = scenario {
        alice.battlefield("Forest", "Forest", "Forest", "Forest", "Forest")
        alice.hand(rendbeast)
        alice.casts(rendbeast)
        allPass()
        option("+1/+1")
        expect {
            rendbeast.power shouldBe 5
            rendbeast lacksKeyword Keyword.Haste
        }
    }

    /** Haste chosen: it attacks at once, and the optional discard then draws. */
    // CR 702.136a, 508.1m, 701.9a
    @Test
    fun riotHasteThenRummage() = scenario {
        alice.battlefield("Mountain", "Mountain", "Mountain")
        alice.hand(vandal, ogre)
        alice.library(bear)
        alice.casts(vandal)
        allPass()
        option("haste")
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(vandal)
        resolveAll()
        may(true)
        chooseIfOffered(ogre)
        resolveAll()
        expect {
            vandal.power shouldBe 2
            alice.hand has bear
            alice.graveyard has ogre
        }
    }

    /** Declining the discard draws nothing. */
    // CR 608.2c
    @Test
    fun vandalDeclinesToDiscard() = scenario {
        alice.battlefield(vandal)
        alice.hand(ogre)
        alice.library(bear)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(vandal)
        resolveAll()
        may(false)
        resolveAll()
        expect {
            alice.hand has ogre
            alice.library has bear
        }
    }

    /** Three players: each of the two opponents loses 1 and the extorter gains 2 (CR 702.101a). */
    // CR 702.101a, 119.3
    @Test
    fun extortDrainsEachOpponent() = scenario(players = 3) {
        alice.battlefield(guards, "Forest", "Forest", "Plains")
        alice.hand(bear)
        alice.casts(bear, paying = listOf("Forest", "Forest"))
        allPass()
        answer(Answer.YesNo(true))
        paySuggested()
        resolveAll()
        expect {
            bob.life shouldBe 19
            carol.life shouldBe 19
            alice.life shouldBe 22
        }
    }

    /** The enters trigger can only target a creature dealt damage this turn: the damaged bear dies, the other lives. */
    // CR 702.55b, 700.4
    @Test
    fun executionerDestroysOnlyDamagedCreature() = scenario {
        alice.battlefield("Swamp", "Swamp", "Swamp", "Mountain")
        alice.hand(executioner, spark)
        bob.battlefield(bear, ogre)
        alice.casts(spark, target = bear)
        resolveAll()
        alice.casts(executioner)
        playOut(bear)
        expect {
            bob.graveyard has bear
            bob.battlefield has ogre
        }
    }

    /** A creature sacrificed as the cost (the bear) pays for regeneration; the destroy spell only taps the gatekeeper. */
    // CR 701.19a, 602.2b
    @Test
    fun sacrificeRegeneration() = scenario {
        alice.battlefield(gatekeeper, bear)
        bob.battlefield("Mountain")
        bob.hand(smite)
        turn(active = bob)
        bob.casts(smite, target = gatekeeper)
        bob.passes()
        alice.activates(gatekeeper, choosing = listOf(bear))
        resolveAll()
        expect {
            gatekeeper.zone shouldBe Zone.Battlefield
            gatekeeper.tapped shouldBe true
            alice.graveyard has bear
        }
    }
}
