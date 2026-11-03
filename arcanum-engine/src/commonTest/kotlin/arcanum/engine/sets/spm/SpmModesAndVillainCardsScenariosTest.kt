package arcanum.engine.sets.spm

import arcanum.api.Keyword
import arcanum.api.TurnStep
import arcanum.api.Zone
import arcanum.engine.cards.Cards
import arcanum.engine.cards.face
import arcanum.engine.scenario.scenario
import kotlin.test.Test

/**
 * Marvel's Spider-Man cards of parser chunks 04–06 in play (the backfill of 2026-11-03): a Saga, modal spells and
 * creatures, Villains that connive, cost less and pump each other, and a hero who plays an exiled card.
 */
class SpmModesAndVillainCardsScenariosTest {
    private val theStalkersFinalPursuit = Cards.invent("The Stalker's Final Pursuit", "{3}{G}", "Enchantment — Saga", """
        (As this Saga enters and after your draw step, add a lore counter. Sacrifice after III.)
        I — Mill five cards. When you do, this Saga deals damage equal to the greatest power among creature cards in your graveyard to target creature.
        II — Target creature you control gets +2/+2 until end of turn.
        III — Return target creature card from your graveyard to your hand.
    """)
    private val morningHeraldNewshounds = Cards.invent("Morning Herald Newshounds", "{3}{W}", "Creature — Human Citizen", """
        When this creature enters, choose one —
        • Puff Piece — Put a +1/+1 counter on each of up to two target creatures.
        • Investigative Journalism — Return target creature card with mana value 2 or less from your graveyard to your hand.
    """, "2/3")
    private val dazzlingManeuvers = Cards.invent("Dazzling Maneuvers", "{1}{W}", "Instant", """
        Choose one —
        • Put a +1/+1 counter on target creature you control. It gains hexproof until end of turn.
        • Destroy target creature with power 4 or greater.
    """)
    private val astoundingTumble = Cards.invent("Astounding Tumble", "{1}{U}{U}", "Instant", """
        Choose one or both —
        • Counter target spell.
        • Tap one or two target creatures.
    """)
    private val classroomReverie = Cards.invent("Classroom Reverie", "{3}{U}{U}", "Instant", """
        Choose one —
        • Do Homework — Draw three cards.
        • Fight Crime — Counter target spell. Draw a card.
    """)
    private val surveyTheSkyline = Cards.invent("Survey the Skyline", "{1}{G}", "Sorcery", """
        Choose one —
        • Look Around — Mill three cards. You may put a permanent card from among them into your hand. You gain 3 life. (To mill three cards, put the top three cards of your library into your graveyard.)
        • Bring Down — Destroy target creature with flying.
    """)
    private val rubbleRepairSquad = Cards.invent("Rubble Repair Squad", "{3}{G}", "Creature — Human Citizen", """
        When this creature enters, choose one —
        • Repair — Return target card with mana value 4 or greater from your graveyard to your hand.
        • Impound — Exile target artifact or enchantment.
    """, "3/3")
    private val carapax = Cards.invent("Carapax, Inherited Outlaw", "{3}{U}", "Legendary Creature — Human Rogue Villain", """
        Flying
        {1}{U}, Exile this card from your graveyard: Put a +1/+1 counter on target creature. It gains flying until end of turn. Activate only as a sorcery.
    """, "3/3")
    private val grapplesLackeys = Cards.invent("Grapple's Lackeys", "{2}{U}", "Creature — Human Villain", """
        Flash
        Whenever this creature attacks, it connives. (Draw a card, then discard a card. If you discarded a nonland card, put a +1/+1 counter on this creature.)
    """, "2/1")
    private val hoveringClawbot = Cards.invent("Hovering Clawbot", "{1}{U}", "Artifact Creature — Robot Villain", """
        Flying
        Whenever another Villain you control enters, put a +1/+1 counter on this creature. This ability triggers only once each turn.
    """, "1/1")
    private val phantasmosApparition = Cards.invent("Phantasmo's Apparition", "{1}{U}", "Creature — Illusion Villain", """
        Flying, vigilance
        Whenever this creature attacks, mill a card. (Put the top card of your library into your graveyard.)
    """, "1/3")
    private val stinger = Cards.invent("Stinger, Venomous Brawler", "{3}{B}", "Legendary Creature — Scorpion Human Villain", """
        Deathtouch
        At the beginning of your end step, if a creature died this turn, target creature you control connives. (Draw a card, then discard a card. If you discarded a nonland card, put a +1/+1 counter on that creature.)
    """, "3/3")
    private val gravelord = Cards.invent("Gravelord, Lifelong Felon", "{2}{B}", "Legendary Creature — Human Villain", """
        When Gravelord enters, return target Villain card from your graveyard to your hand.
        Villain spells you cast cost {1} less to cast.
    """, "2/2")
    private val ravenousOoze = Cards.invent("Ravenous Ooze", "{4}{B}", "Sorcery", """
        This spell costs {2} less to cast if you control a Villain.
        Destroy target creature. You gain 2 life.
    """)
    private val syndicateSentry = Cards.invent("Syndicate Sentry", "{1}{U/B}", "Creature — Human Rogue Villain", "When this creature enters, target creature you control connives. (Draw a card, then discard a card. If you discarded a nonland card, put a +1/+1 counter on that creature.)", "0/3")
    private val nightprowl = Cards.invent("Nightprowl, Gauntleted Burglar", "{1}{U}{B}", "Legendary Creature — Human Rogue Villain", """
        Menace (This creature can't be blocked except by two or more creatures.)
        Whenever another Villain you control enters, Nightprowl connives. (Draw a card, then discard a card. If you discarded a nonland card, put a +1/+1 counter on this creature.)
    """, "2/3")
    private val carrionWing = Cards.invent("Carrion Wing, Plotting Scrapper", "{5}{U/B}", "Legendary Creature — Human Artificer Villain", """
        Flying
        Whenever Carrion Wing attacks, other Villains you control gain flying until end of turn.
    """, "4/6")
    private val volatileTrial = Cards.invent("Volatile Trial", "{1}{U}", "Instant", "Target player draws a card, then up to one target creature you control connives. (Draw a card, then discard a card. If you discarded a nonland card, put a +1/+1 counter on that creature.)")
    private val clockworkGoon = Cards.invent("Clockwork Goon", "{3}", "Artifact Creature — Human Robot Villain", "When this creature enters, exile up to one target card from a graveyard. Target creature you control connives. (Draw a card, then discard a card. If you discarded a nonland card, put a +1/+1 counter on that creature.)", "2/1")
    private val professorGrapple = Cards.invent("Professor Grapple, Grand Schemer", "{5}{U}{B}", "Legendary Creature — Human Scientist Villain", """
        Other Villains you control get +2/+2.
        Your maximum hand size is eight.
        At the beginning of your end step, if you have fewer than eight cards in hand, draw cards equal to the difference.
    """, "4/8")
    private val covertOozeOperative = Cards.invent("Covert Ooze Operative", "{2}{B}", "Legendary Creature — Symbiote Soldier Hero", """
        Flash
        Menace
        Whenever another nontoken creature you control dies, you draw a card and lose 1 life.
    """, "2/3")
    private val wrenAshby = Cards.inventFaces("ModalDoubleFaced",
        face("Wren Ashby", "{1}{R}", "Legendary Creature — Human Performer Hero", """
            When Wren Ashby enters, exile the top card of your library. You may play that card for as long as you control this creature.
            {2}{U}{R}{W}: Transform Wren Ashby. Activate only as a sorcery.
        """, "2/1", null, null),
        face("Phantom Webdancer", "{2}{U}{R}{W}", "Legendary Creature — Spider Human Hero", """
            Flying, vigilance, haste
            Whenever you play a land from exile or cast a spell from exile, put a +1/+1 counter on Phantom Webdancer.
            Remove two counters from Phantom Webdancer: Exile the top card of your library. You may play that card this turn.
        """, "4/4", null, null),
    )
    private val bear = spmBear
    private val ogre = spmOgre
    private val giant = spmGiant
    private val villain = spmVillain

    // ---- Chunk 04: Sagas and modes

    /** Chapter I: five cards milled, then the greatest power among the creature cards milled (5) hits a creature. */
    // CR 714.2b, 603.12
    @Test
    fun theStalkersFinalPursuitMillsAndStrikes() = scenario {
        bob.battlefield(giant)
        loreChapter(theStalkersFinalPursuit, 1, giant, bear, bear, bear, bear)
        playOut(giant)
        expect {
            alice.graveyard.size shouldBe 5
            bob.graveyard has giant
        }
    }

    /** Chapter III: a creature card back to the hand. */
    // CR 714.2b
    @Test
    fun theStalkersFinalPursuitReturnsACreatureCard() = scenario {
        alice.graveyard(ogre)
        loreChapter(theStalkersFinalPursuit, 3)
        playOut(ogre)
        expect {
            alice.hand has ogre
            alice.graveyard has theStalkersFinalPursuit
        }
    }

    /** The second mode: a creature card of mana value 2 or less back to the hand. */
    // CR 700.2a
    @Test
    fun morningHeraldNewshoundsReturnACheapCreature() = scenario {
        alice.battlefield("Plains", "Plains", "Plains", "Plains")
        alice.graveyard(bear)
        alice.hand(morningHeraldNewshounds)
        alice.casts(morningHeraldNewshounds)
        playOut()
        modes(1)
        playOut(bear)
        expect { alice.hand has bear }
    }

    /** The first mode: counters on two creatures. */
    // CR 700.2a
    @Test
    fun morningHeraldNewshoundsPuffPiece() = scenario {
        alice.battlefield("Plains", "Plains", "Plains", "Plains", bear, ogre)
        alice.hand(morningHeraldNewshounds)
        alice.casts(morningHeraldNewshounds)
        playOut()
        modes(0)
        playOut(bear, ogre)
        expect {
            bear.counters(p1p1) shouldBe 1
            ogre.counters(p1p1) shouldBe 1
        }
    }

    /** The second mode destroys a creature with power 4 or greater. */
    // CR 700.2a
    @Test
    fun dazzlingManeuversDestroysABigCreature() = scenario {
        alice.battlefield("Plains", "Plains")
        alice.hand(dazzlingManeuvers)
        bob.battlefield(giant)
        alice.casts(dazzlingManeuvers, modes = listOf(1), target = giant)
        playOut()
        expect { bob.graveyard has giant }
    }

    /** The first mode: a counter and hexproof. */
    // CR 702.11a
    @Test
    fun dazzlingManeuversProtects() = scenario {
        alice.battlefield("Plains", "Plains", bear)
        alice.hand(dazzlingManeuvers)
        alice.casts(dazzlingManeuvers, modes = listOf(0), target = bear)
        playOut()
        expect {
            bear.counters(p1p1) shouldBe 1
            bear hasKeyword Keyword.Hexproof
        }
    }

    /** Both modes: the opponent's spell countered and two of their creatures tapped. */
    // CR 700.2d
    @Test
    fun astoundingTumbleCountersAndTapsTwo() = scenario {
        alice.battlefield("Island", "Island", "Island")
        alice.hand(astoundingTumble)
        bob.battlefield("Island", ogre, giant)
        bob.hand(Spm.study)
        bob.library(bear)
        turn(active = bob)
        bob.casts(Spm.study)
        bob.passes()
        alice.casts(astoundingTumble, modes = listOf(0, 1), targets = listOf(Spm.study, ogre, giant))
        playOut()
        expect {
            bob.graveyard has Spm.study
            bob.library has bear
            ogre.tapped shouldBe true
            giant.tapped shouldBe true
        }
    }

    /** Fight Crime: a spell countered and a card drawn. */
    // CR 700.2a, 701.6a
    @Test
    fun classroomReverieFightsCrime() = scenario {
        alice.battlefield("Island", "Island", "Island", "Island", "Island")
        alice.hand(classroomReverie)
        alice.library(ogre)
        bob.battlefield("Mountain")
        bob.hand(Spm.shock)
        turn(active = bob)
        bob.casts(Spm.shock, target = "alice")
        bob.passes()
        alice.casts(classroomReverie, modes = listOf(1), target = Spm.shock)
        playOut()
        expect {
            alice.life shouldBe 20
            alice.hand has ogre
        }
    }

    /** Look Around: three cards milled, a permanent card among them kept, 3 life. */
    // CR 701.17a
    @Test
    fun surveyTheSkylineLooksAround() = scenario {
        alice.battlefield("Forest", "Forest")
        alice.hand(surveyTheSkyline)
        alice.library(Spm.shock, ogre, Spm.study)
        alice.casts(surveyTheSkyline, modes = listOf(0))
        playOut(ogre)
        expect {
            alice.hand has ogre
            alice.graveyard has Spm.shock
            alice.graveyard has Spm.study
            alice.life shouldBe 23
        }
    }

    /** Bring Down destroys a flyer. */
    // CR 700.2a
    @Test
    fun surveyTheSkylineBringsDownAFlyer() = scenario {
        alice.battlefield("Forest", "Forest")
        alice.hand(surveyTheSkyline)
        bob.battlefield(Spm.flyer)
        alice.casts(surveyTheSkyline, modes = listOf(1), target = Spm.flyer)
        playOut()
        expect { bob.graveyard has Spm.flyer }
    }

    /** Repair: a card of mana value 4 or more back to the hand; Impound: an artifact exiled. */
    // CR 700.2a
    @Test
    fun rubbleRepairSquadRepairs() = scenario {
        alice.battlefield("Forest", "Forest", "Forest", "Forest")
        alice.graveyard(giant)
        alice.hand(rubbleRepairSquad)
        alice.casts(rubbleRepairSquad)
        playOut()
        modes(0)
        playOut(giant)
        expect { alice.hand has giant }
    }

    @Test
    fun rubbleRepairSquadImpounds() = scenario {
        alice.battlefield("Forest", "Forest", "Forest", "Forest")
        alice.hand(rubbleRepairSquad)
        bob.battlefield(Spm.idol)
        alice.casts(rubbleRepairSquad)
        playOut()
        modes(1)
        playOut(Spm.idol)
        expect { bob.exile has Spm.idol }
    }

    // ---- Chunk 05: Villains

    /** From the graveyard: a counter and flying for a creature, Carapax exiled. */
    // CR 602.2, 113.6
    @Test
    fun carapaxGivesACounterFromTheGraveyard() = scenario {
        alice.battlefield("Island", "Island", bear)
        alice.graveyard(carapax)
        alice.activates(carapax, target = bear)
        playOut()
        expect {
            bear.counters(p1p1) shouldBe 1
            bear hasKeyword Keyword.Flying
            alice.exile has carapax
        }
    }

    /** Attacking, it connives: a nonland card discarded gives it a counter. */
    // CR 701.50a
    @Test
    fun grapplesLackeysConniveOnAttack() = scenario {
        alice.battlefield(grapplesLackeys)
        alice.library(ogre)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(grapplesLackeys)
        playOut(ogre)
        expect {
            alice.graveyard has ogre
            grapplesLackeys.counters(p1p1) shouldBe 1
        }
    }

    /** Two Villains entering in a turn: one counter (once each turn). */
    // CR 603.2i
    @Test
    fun hoveringClawbotGrowsOncePerTurn() = scenario {
        alice.battlefield(hoveringClawbot, "Swamp", "Swamp", "Swamp", "Swamp")
        alice.hand(villain, villain)
        alice.casts(villain, paying = listOf("Swamp", "Swamp#1"))
        playOut()
        alice.casts(villain, paying = listOf("Swamp#2", "Swamp#3"))
        playOut()
        expect { hoveringClawbot.counters(p1p1) shouldBe 1 }
    }

    /** Attacking mills a card. */
    // CR 701.17a
    @Test
    fun phantasmosApparitionMillsOnAttack() = scenario {
        alice.battlefield(phantasmosApparition)
        alice.library(ogre)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(phantasmosApparition)
        playOut()
        expect {
            alice.graveyard has ogre
            phantasmosApparition.tapped shouldBe false
        }
    }

    /** A creature died this turn: at the end step a creature connives. */
    // CR 603.4, 701.50a
    @Test
    fun stingerConnivesAfterADeath() = scenario {
        alice.battlefield(stinger, bear, "Swamp")
        alice.hand(Spm.doom)
        alice.library(ogre)
        bob.battlefield(giant)
        alice.casts(Spm.doom, target = giant)
        playOut()
        advanceTo(TurnStep.End)
        playOut(bear, ogre)
        expect {
            alice.graveyard has ogre
            bear.counters(p1p1) shouldBe 1
        }
    }

    /** No death, no trigger. */
    // CR 603.4
    @Test
    fun stingerQuietWithoutADeath() = scenario {
        alice.battlefield(stinger, bear)
        alice.library(ogre)
        advanceTo(TurnStep.End)
        playOut(bear, ogre)
        expect { alice.library has ogre }
    }

    /** A Villain card returns, then costs {1} less. */
    // CR 601.2f
    @Test
    fun gravelordReturnsAndDiscountsVillains() = scenario {
        alice.battlefield("Swamp", "Swamp", "Swamp", "Swamp")
        alice.graveyard(villain)
        alice.hand(gravelord)
        alice.casts(gravelord, paying = listOf("Swamp", "Swamp#1", "Swamp#2"))
        playOut(villain)
        expect { alice.hand has villain }
        alice.casts(villain, paying = listOf("Swamp#3"))
        playOut()
        expect { villain.zone shouldBe Zone.Battlefield }
    }

    /** With a Villain it costs {2}{B}: a creature destroyed and 2 life. */
    // CR 601.2f
    @Test
    fun ravenousOozeCheaperWithAVillain() = scenario {
        alice.battlefield(villain, "Swamp", "Swamp", "Swamp")
        alice.hand(ravenousOoze)
        bob.battlefield(giant)
        alice.casts(ravenousOoze, target = giant)
        playOut()
        expect {
            bob.graveyard has giant
            alice.life shouldBe 22
        }
    }

    /** As it enters, a creature connives. */
    // CR 701.50a
    @Test
    fun syndicateSentryMakesACreatureConnive() = scenario {
        alice.battlefield("Island", "Swamp", ogre)
        alice.hand(syndicateSentry)
        alice.library(giant)
        alice.casts(syndicateSentry)
        playOut(ogre, giant)
        expect {
            ogre.counters(p1p1) shouldBe 1
            alice.graveyard has giant
        }
    }

    /** Another Villain entering makes Nightprowl connive. */
    // CR 701.50a
    @Test
    fun nightprowlConnivesForVillains() = scenario {
        alice.battlefield(nightprowl, "Swamp", "Swamp")
        alice.hand(villain)
        alice.library(giant)
        alice.casts(villain)
        playOut(giant)
        expect { nightprowl.counters(p1p1) shouldBe 1 }
    }

    /** Attacking, it gives the other Villains flying. */
    // CR 611.2c
    @Test
    fun carrionWingGivesVillainsFlying() = scenario {
        alice.battlefield(carrionWing, villain, bear)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(carrionWing)
        playOut()
        expect {
            villain hasKeyword Keyword.Flying
            bear lacksKeyword Keyword.Flying
        }
    }

    /** The opponent draws; a creature of yours connives. */
    // CR 701.50a
    @Test
    fun volatileTrialDrawsAndConnives() = scenario {
        alice.battlefield("Island", "Island", bear)
        alice.hand(volatileTrial)
        alice.library(giant)
        bob.library(ogre)
        alice.casts(volatileTrial, targets = listOf("bob", bear))
        playOut(giant)
        expect {
            bob.hand has ogre
            bear.counters(p1p1) shouldBe 1
        }
    }

    /** A graveyard card exiled and a creature conniving. */
    // CR 701.50a
    @Test
    fun clockworkGoonExilesAndConnives() = scenario {
        alice.battlefield("Plains", "Plains", "Plains")
        alice.hand(clockworkGoon)
        alice.library(giant)
        bob.graveyard(ogre)
        alice.casts(clockworkGoon)
        playOut(ogre, clockworkGoon, giant)
        expect {
            bob.exile has ogre
            clockworkGoon.counters(p1p1) shouldBe 1
        }
    }

    /** Other Villains get +2/+2; at the end step the hand is filled up to eight. */
    // CR 402.2, 603.4
    @Test
    fun professorGrapplePumpsAndRefills() = scenario {
        alice.battlefield(professorGrapple, villain)
        alice.hand(bear)
        alice.library(ogre, ogre, ogre, ogre, ogre, ogre, ogre, ogre)
        expect { villain.power shouldBe 4 }
        advanceTo(TurnStep.End)
        playOut()
        expect { alice.hand.size shouldBe 8 }
    }

    /** Another nontoken creature dying: a card and 1 life lost. */
    // CR 700.4
    @Test
    fun covertOozeOperativeDrawsOnDeaths() = scenario {
        alice.battlefield(covertOozeOperative, bear, "Swamp")
        alice.hand(Spm.doom)
        alice.library(ogre)
        alice.casts(Spm.doom, target = bear)
        playOut()
        expect {
            alice.hand has ogre
            alice.life shouldBe 19
        }
    }

    // ---- Chunk 06: copies and elsewhere

    /** The exiled top card may be played while Wren Ashby stays. */
    // CR 611.2a
    @Test
    fun wrenAshbyPlaysTheExiledCard() = scenario {
        alice.battlefield("Mountain", "Mountain")
        alice.hand(wrenAshby)
        alice.library("Forest", ogre)
        alice.casts(wrenAshby)
        playOut()
        expect { card("Forest").zone shouldBe Zone.Exile }
        alice.plays("Forest")
        expect { card("Forest").zone shouldBe Zone.Battlefield }
    }
}
