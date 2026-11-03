package arcanum.engine.sets.tdm

import arcanum.api.CardType
import arcanum.api.Keyword
import arcanum.api.TurnStep
import arcanum.api.Zone
import arcanum.engine.cards.Cards
import arcanum.engine.cards.face
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.scenario.scenario
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Tarkir: Dragonstorm cards of parser chunks 05–06 in play: the Dragonstorm enchantments and elder Dragons, flurry (an ability word, CR 207.2c) and second-spell cards.
 */
class TdmDragonstormAndFlurryCardsScenariosTest {
    private val arsenalDragon = Cards.invent("Arsenal Dragon", "{3}{W}{B}{G}", "Creature — Dragon", """
        Flying
        When this creature enters, distribute three +1/+1 counters among one, two, or three target creatures you control.
    """, "3/4")
    private val ovaroKinOfMany = Cards.invent("Ovaro, Kin of Many", "{2}{W}{B}{G}", "Legendary Creature — Spirit Dragon", """
        Flying
        At the beginning of your end step, if creatures you control have total toughness 10 or greater, draw a card. Then if creatures you control have total toughness 20 or greater, untap each creature you control. Then if creatures you control have total toughness 40 or greater, each opponent loses half their life, rounded up.
    """, "5/7")
    private val rendingWyrmstorm = Cards.invent("Rending Wyrmstorm", "{4}{R}", "Enchantment", """
        When this enchantment enters, exile cards from the top of your library until you exile a nonland card. You may cast it without paying its mana cost if that spell's mana value is 8 or less. If you don't, put that card into your hand.
        When a Dragon you control enters, return this enchantment to its owner's hand.
    """)
    private val wastingWyrmstorm = Cards.invent("Wasting Wyrmstorm", "{1}{B}", "Enchantment", """
        When this enchantment enters, each opponent loses 2 life and you gain 2 life. Surveil 2. (Look at the top two cards of your library, then put any number of them into your graveyard and the rest on top of your library in any order.)
        When a Dragon you control enters, return this enchantment to its owner's hand.
    """)
    private val wyrmbackOnslaught = Cards.invent("Wyrmback Onslaught", "{3}{G}{U}{R}", "Enchantment", """
        When this enchantment enters, it deals 3 damage to each creature and each planeswalker.
        Landfall — Whenever a land you control enters, create a 4/4 red Dragon creature token with flying.
    """)
    private val creepingWyrmstorm = Cards.invent("Creeping Wyrmstorm", "{3}{G}", "Enchantment", """
        When this enchantment enters, search your library for up to two basic land cards, put them onto the battlefield tapped, then shuffle.
        When a Dragon you control enters, return this enchantment to its owner's hand.
    """)
    private val monasteryShrinewarden = Cards.invent("Monastery Shrinewarden", "{2}{U}{R}{W}", "Creature — Dragon", """
        Flying, haste
        Whenever this creature deals combat damage to a player, you gain 1 life and draw a card.
    """, "3/3")
    private val highpeakGuardian = Cards.invent("Highpeak Guardian", "{3}{G}{U}{R}", "Creature — Dragon", """
        Flying, vigilance, trample
        This creature has hexproof as long as it hasn't dealt damage yet. (It can't be the target of spells or abilities your opponents control.)
    """, "6/5")
    private val keshHeartOfTheGale = Cards.invent("Kesh, Heart of the Gale", "{1}{R}{W}{B}", "Legendary Creature — Spirit Dragon", """
        Flying
        If a creature you control that entered this turn would deal damage, it deals twice that much damage instead.
    """, "4/5")
    private val churningWyrmstorm = Cards.invent("Churning Wyrmstorm", "{1}{U}", "Enchantment", """
        When this enchantment enters, draw two cards, then discard a card.
        When a Dragon you control enters, return this enchantment to its owner's hand.
    """)
    private val taviParagonOfThePath = Cards.invent("Tavi, Paragon of the Path", "{2}{U}{R}{W}", "Legendary Creature — Spirit Dragon", """
        Flying, vigilance
        When Tavi enters, exile target nonland card with mana value 3 or less from your graveyard. Copy it, then you may cast the copy without paying its mana cost. (A copy of a permanent spell becomes a token.)
    """, "4/5")
    private val sonicScreecher = Cards.invent("Sonic Screecher", "{2}{R}{W}{B}", "Creature — Dragon", """
        Flying
        When this creature enters, it deals 2 damage to any target and you gain 2 life. If a player is dealt damage this way, they discard a card.
    """, "4/4")
    private val swarmingWyrmstorm = Cards.invent("Swarming Wyrmstorm", "{3}{W}", "Enchantment", """
        When this enchantment enters, create two 2/2 white Soldier creature tokens.
        When a Dragon you control enters, return this enchantment to its owner's hand.
    """)
    private val ilmarJudgeOfVirtue = Cards.invent("Ilmar, Judge of Virtue", "{2}{B}{G}{U}", "Legendary Creature — Spirit Dragon", """
        Flying, lifelink
        Spells you cast have delve. (Each card you exile from your graveyard while casting those spells pays for {1}.)
        Whenever you cast a spell, you lose life equal to its mana value.
    """, "6/6")
    private val sorrelTheEndlessHymn = Cards.invent("Sorrel, the Endless Hymn", "{5}{G}{U}{R}", "Legendary Creature — Spirit Dragon", """
        Flying, protection from white and from black
        When Sorrel enters, it deals X damage divided as you choose among any number of target creatures and/or planeswalkers your opponents control, where X is the number of lands you control.
    """, "10/10")
    private val summitStalwart = Cards.invent("Summit Stalwart", "{1}{R}{W}", "Creature — Human Monk", "Flurry — Whenever you cast your second spell each turn, this creature deals 2 damage to each opponent and you gain 2 life.", "3/3")
    private val peakforgedCutter = Cards.invent("Peakforged Cutter", "{1}{R}", "Artifact — Equipment", """
        Equipped creature gets +1/+1 and has trample and haste.
        Flurry — Whenever you cast your second spell each turn, create a 1/1 white Monk creature token with prowess. You may attach this Equipment to it. (Whenever you cast a noncreature spell, the token gets +1/+1 until end of turn.)
        Equip {1}{R}
    """)
    private val ardentDuelist = Cards.invent("Ardent Duelist", "{1}{R}", "Creature — Goblin Monk", """
        Haste
        Flurry — Whenever you cast your second spell each turn, this creature deals 1 damage to each opponent.
    """, "2/1")
    private val unhurriedMaster = Cards.invent("Unhurried Master", "{2}{U}{R}", "Creature — Orc Monk", """
        Vigilance
        Menace (This creature can't be blocked except by two or more creatures.)
        This creature enters with two +1/+1 counters on it if you've cast two or more spells this turn.
    """, "4/3")
    private val equipoiseAdept = Cards.invent("Equipoise Adept", "{3}{R}", "Creature — Dog Monk", """
        When this creature enters, exile the top card of your library. Until the end of your next turn, you may play that card.
        Flurry — Whenever you cast your second spell each turn, this creature gains double strike until end of turn.
    """, "2/4")
    private val vargaWyrmclaw = Cards.invent("Varga Wyrmclaw", "{1}{G}{U}{R}", "Legendary Creature — Human Warrior", """
        Vigilance, trample, ward {1}
        At the beginning of combat on your turn, if you've cast both a creature spell and a noncreature spell this turn, draw a card and put two +1/+1 counters on Varga Wyrmclaw.
    """, "4/4")
    private val embergripGrappler = Cards.invent("Embergrip Grappler", "{U}{R}{W}", "Creature — Human Monk", """
        First strike
        When this creature enters, copy the next spell you cast this turn when you cast it. You may choose new targets for the copy. (A copy of a permanent spell becomes a token.)
    """, "3/3")
    private val centerTheThoughts = Cards.invent("Center the Thoughts", "{4}{U}", "Instant", """
        This spell costs {2} less to cast if you've cast another spell this turn.
        Draw three cards, then discard a card.
    """)
    private val tallspireBellkeeper = Cards.invent("Tallspire Bellkeeper", "{2}{U}", "Creature — Djinn Monk", """
        Flying
        The second spell you cast each turn costs {1} less to cast.
    """, "1/4")
    private val wayfarerDevotee = Cards.invent("Wayfarer Devotee", "{1}{R}", "Creature — Orc Monk", """
        Flurry — Whenever you cast your second spell each turn, this creature gets +1/+1 until end of turn.
        {1}: Add {U}, {R}, or {W}. Activate only once each turn.
    """, "2/2")
    private val lirienWaymasterAbbess = Cards.invent("Lirien, Waymaster Abbess", "{U}{R}{W}", "Legendary Creature — Human Monk", "At the beginning of your end step, you may discard your hand. If you do, draw cards equal to the number of spells you've cast this turn.", "3/4")
    private val balancedAdept = Cards.invent("Balanced Adept", "{2}{W}", "Creature — Human Monk", "Flurry — Whenever you cast your second spell each turn, put a +1/+1 counter on this creature. Scry 1. (Look at the top card of your library. You may put that card on the bottom.)", "2/3")
    private val rouseTheMonastery = Cards.invent("Rouse the Monastery", "{3}{W}", "Instant", """
        This spell costs {2} less to cast if you've cast another spell this turn.
        Choose one —
        • Create two 1/1 white Monk creature tokens with prowess.
        • Up to two target creatures you control each get +2/+2 until end of turn.
        • Destroy target creature with power 4 or greater.
    """)
    private val echoingSummons = Cards.invent("Echoing Summons", "{1}{R}", "Enchantment", """
        At the beginning of each combat, if you've cast two or more spells this turn, this enchantment becomes a 3/3 Monk creature with haste in addition to its other types until end of turn.
        {1}{R}, Discard your hand, Sacrifice this enchantment: Draw two cards.
    """)
    private val sageOfTheHeavens = Cards.invent("Sage of the Heavens", "{2}{W}", "Creature — Human Monk", """
        When you cast this spell, if you've cast another spell this turn, copy this spell. (The copy becomes a token.)
        Flying, lifelink
    """, "2/3")
    private val rhenuMasterSchemer = Cards.invent("Rhenu, Master Schemer", "{1}{U}", "Legendary Creature — Human Monk", "Flurry — Whenever you cast your second spell each turn, copy it, then exile the spell you cast with four time counters on it. If it doesn't have suspend, it gains suspend. (At the beginning of its owner's upkeep, they remove a time counter. When the last is removed, they may play it without paying its mana cost. If it's a creature, it has haste.)", "2/2")
    private val pathspeakerGuard = Cards.invent("Pathspeaker Guard", "{3}{W}", "Creature — Orc Monk", """
        When this creature enters, return target nonland permanent card with mana value 2 or less from your graveyard to your hand.
        Flurry — Whenever you cast your second spell each turn, tap target creature an opponent controls.
    """, "3/4")
    private val featherbladeDisciple = Cards.invent("Featherblade Disciple", "{2}{U}", "Creature — Human Monk", """
        Flying
        Flurry — Whenever you cast your second spell each turn, create a 1/1 white Bird creature token with flying.
    """, "2/2")

    private val bear = Plain.bear
    private val ogre = Plain.ogre
    private val giant = Plain.giant
    private val dragon = Plain.dragon
    private val p1p1 = Plain.p1p1

    // ---- Chunk 05: Dragonstorms and the elder Dragons

    /** Three counters divided among up to three creatures. */
    // CR 601.2d
    @Test
    fun arsenalDragonDistributesCounters() = scenario {
        alice.battlefield("Plains", "Swamp", "Forest", "Forest", "Forest", "Forest", bear, ogre)
        alice.hand(arsenalDragon)
        alice.casts(arsenalDragon)
        playOut(bear, ogre)
        expect { (card(bear).counters(p1p1) + card(ogre).counters(p1p1)) shouldBe 3 }
    }

    /** At the end step: 22 total toughness draws a card and untaps the team. */
    // CR 603.4
    @Test
    fun ovaroDrawsAndUntaps() = scenario {
        alice.battlefield(ovaroKinOfMany)
        alice.battlefield(giant, giant, giant, tapped = true)
        alice.library(ogre)
        advanceTo(TurnStep.End)
        playOut()
        expect {
            alice.hand has ogre
            card(giant, 0).tapped shouldBe false
        }
    }

    @Test
    fun ovaroQuietBelowTen() = scenario {
        alice.battlefield(ovaroKinOfMany)
        alice.library(ogre)
        advanceTo(TurnStep.End)
        playOut()
        expect { alice.library has ogre }
    }

    /** Exile until a nonland card, cast free; a Dragon entering returns it to the hand. */
    // CR 118.9
    @Test
    fun rendingWyrmstormCastsTheExiledCard() = scenario {
        alice.battlefield(*Array(5) { "Mountain" })
        alice.hand(rendingWyrmstorm)
        alice.library("Forest", dragon, ogre)
        alice.casts(rendingWyrmstorm)
        playOut()
        expect {
            dragon.zone shouldBe Zone.Battlefield
            alice.hand has rendingWyrmstorm
        }
    }

    @Test
    fun rendingWyrmstormDeclinedGoesToHand() = scenario {
        alice.battlefield(*Array(5) { "Mountain" })
        alice.hand(rendingWyrmstorm)
        alice.library("Forest", ogre)
        alice.casts(rendingWyrmstorm)
        playOut(may = false)
        expect { alice.hand has ogre }
    }

    /** 2 life drained, surveil 2. */
    // CR 701.25a
    @Test
    fun wastingWyrmstormDrains() = scenario {
        alice.battlefield("Swamp", "Swamp")
        alice.hand(wastingWyrmstorm)
        alice.library(ogre, giant)
        alice.casts(wastingWyrmstorm)
        playOut(ogre)
        expect {
            bob.life shouldBe 18
            alice.life shouldBe 22
            alice.graveyard has ogre
        }
    }

    /** 3 damage to each creature; a land entering makes a 4/4 flying Dragon. */
    // CR 207.2c
    @Test
    fun wyrmbackOnslaughtSweepsAndMakesDragons() = scenario {
        alice.battlefield("Island", "Mountain", "Forest", "Forest", "Forest", "Forest")
        alice.hand(wyrmbackOnslaught, "Mountain")
        bob.battlefield(ogre, giant)
        alice.casts(wyrmbackOnslaught)
        playOut()
        expect {
            bob.graveyard has ogre
            giant.zone shouldBe Zone.Battlefield
        }
        alice.plays("Mountain")
        playOut()
        expect { "Dragon Token" hasKeyword Keyword.Flying }
    }

    /** Two basic lands onto the battlefield tapped. */
    // CR 701.23a
    @Test
    fun creepingWyrmstormFetchesTwo() = scenario {
        alice.battlefield(*Array(4) { "Forest" })
        alice.hand(creepingWyrmstorm)
        alice.library("Island", "Swamp", ogre)
        alice.casts(creepingWyrmstorm)
        playOut("Island", "Swamp")
        expect {
            card("Island").tapped shouldBe true
            card("Swamp").zone shouldBe Zone.Battlefield
        }
    }

    /** Hitting a player: 1 life and a card. */
    // CR 510.3a
    @Test
    fun monasteryShrinewardenHits() = scenario {
        alice.battlefield(monasteryShrinewarden)
        alice.library(ogre)
        hits(monasteryShrinewarden)
        expect {
            alice.life shouldBe 21
            alice.hand has ogre
        }
    }

    /** Hexproof until it has dealt damage. */
    // CR 702.11a
    @Test
    fun highpeakGuardianHexproofUntilItDeals() = scenario {
        alice.battlefield(highpeakGuardian)
        bob.battlefield("Mountain")
        bob.hand(Plain.shock)
        turn(active = bob)
        assertFailsWith<Throwable> { bob.casts(Plain.shock, target = highpeakGuardian) }
    }

    @Test
    fun highpeakGuardianLosesHexproofAfterDamage() = scenario {
        alice.battlefield(highpeakGuardian)
        bob.library(ogre)
        hits(highpeakGuardian)
        expect { highpeakGuardian lacksKeyword Keyword.Hexproof }
    }

    /** A creature that entered this turn deals double damage. */
    // CR 614.1a
    @Test
    fun keshDoublesNewcomersDamage() = scenario {
        alice.battlefield(keshHeartOfTheGale, "Mountain", "Mountain", "Plains", "Swamp", "Swamp", "Swamp")
        alice.hand(sonicScreecher)
        bob.hand(ogre)
        alice.casts(sonicScreecher)
        playOut("bob", ogre)
        expect { bob.life shouldBe 16 }
    }

    /** Two cards, then a discard; a Dragon entering returns it. */
    // CR 701.9a
    @Test
    fun churningWyrmstormLoots() = scenario {
        alice.battlefield("Island", "Island")
        alice.hand(churningWyrmstorm, bear)
        alice.library(ogre, giant)
        alice.casts(churningWyrmstorm)
        playOut(bear)
        expect {
            alice.graveyard has bear
            alice.hand.size shouldBe 2
        }
    }

    /** A cheap card exiled from the graveyard, copied and cast free. */
    // CR 707.12
    @Test
    fun taviCastsACopy() = scenario {
        alice.battlefield("Island", "Island", "Island", "Mountain", "Plains")
        alice.hand(taviParagonOfThePath)
        alice.graveyard(Plain.study)
        alice.library(ogre)
        alice.casts(taviParagonOfThePath)
        playOut(Plain.study)
        expect {
            alice.exile has Plain.study
            alice.hand has ogre
        }
    }

    /** 2 damage to a player and 2 life; the player dealt damage discards. */
    // CR 120.3
    @Test
    fun sonicScreecherHitsAndDiscards() = scenario {
        alice.battlefield("Mountain", "Plains", "Swamp", "Swamp", "Swamp", "Swamp")
        alice.hand(sonicScreecher)
        bob.hand(ogre)
        alice.casts(sonicScreecher)
        playOut("bob", ogre)
        expect {
            bob.life shouldBe 18
            alice.life shouldBe 22
            bob.graveyard has ogre
        }
    }

    /** Two Soldiers; returned to the hand when a Dragon enters. */
    // CR 603.2
    @Test
    fun swarmingWyrmstormMakesSoldiersAndReturns() = scenario {
        alice.battlefield(*Array(8) { "Plains" })
        alice.hand(swarmingWyrmstorm, Plain.whiteDragon)
        alice.casts(swarmingWyrmstorm, paying = listOf("Plains", "Plains#1", "Plains#2", "Plains#3"))
        playOut()
        expect { card("Soldier Token", 1).zone shouldBe Zone.Battlefield }
        alice.casts(Plain.whiteDragon)
        playOut()
        expect { alice.hand has swarmingWyrmstorm }
    }

    /** Spells cost life equal to their mana value. */
    // CR 202.3
    @Test
    fun ilmarLosesLifeForSpells() = scenario {
        alice.battlefield(ilmarJudgeOfVirtue, "Mountain", "Mountain", "Mountain")
        alice.hand(ogre)
        alice.casts(ogre)
        playOut()
        expect { alice.life shouldBe 17 }
    }

    /** X damage, X the lands, divided among the opponent's creatures. */
    // CR 601.2d
    @Test
    fun sorrelDividesDamage() = scenario {
        alice.battlefield("Island", "Mountain", *Array(6) { "Forest" })
        alice.hand(sorrelTheEndlessHymn)
        bob.battlefield(ogre, bear)
        alice.casts(sorrelTheEndlessHymn)
        playOut(ogre, bear)
        expect {
            bob.graveyard has ogre
            bob.graveyard has bear
        }
    }

    // ---- Chunk 06: flurry and second spells

    /** The second spell each turn: 2 damage to each opponent and 2 life. */
    // CR 601.2i
    @Test
    fun summitStalwartFlurry() = scenario {
        alice.battlefield(summitStalwart, "Island", "Plains")
        alice.hand(Plain.study, Plain.heal)
        alice.library(ogre)
        alice.casts(Plain.study)
        playOut()
        expect { bob.life shouldBe 20 }
        alice.casts(Plain.heal)
        playOut()
        expect {
            bob.life shouldBe 18
            alice.life shouldBe 24
        }
    }

    /** Flurry: a Monk token, and the Equipment may jump onto it. */
    // CR 301.5
    @Test
    fun peakforgedCutterMakesAMonk() = scenario {
        alice.battlefield(peakforgedCutter, "Island", "Plains")
        alice.hand(Plain.study, Plain.heal)
        alice.library(ogre)
        alice.casts(Plain.study)
        playOut()
        alice.casts(Plain.heal)
        playOut()
        expect {
            "Monk Token".power shouldBe 2
            "Monk Token" hasKeyword Keyword.Haste
        }
    }

    /** Flurry: 1 damage to each opponent. */
    @Test
    fun ardentDuelistFlurry() = scenario {
        alice.battlefield(ardentDuelist, "Island", "Plains")
        alice.hand(Plain.study, Plain.heal)
        alice.library(ogre)
        alice.casts(Plain.study)
        playOut()
        alice.casts(Plain.heal)
        playOut()
        expect { bob.life shouldBe 19 }
    }

    /** Two spells cast this turn: it enters with two counters. */
    // CR 614.1c
    @Test
    fun unhurriedMasterEntersBig() = scenario {
        alice.battlefield("Island", "Plains", "Island", "Island", "Mountain", "Mountain")
        alice.hand(Plain.study, Plain.heal, unhurriedMaster)
        alice.library(ogre)
        alice.casts(Plain.study, paying = listOf("Island"))
        playOut()
        alice.casts(Plain.heal, paying = listOf("Plains"))
        playOut()
        alice.casts(unhurriedMaster)
        playOut()
        expect { unhurriedMaster.counters(p1p1) shouldBe 2 }
    }

    /** The exiled top card may be played; flurry gives double strike. */
    // CR 601.2a
    @Test
    fun equipoiseAdeptPlaysTheTop() = scenario {
        alice.battlefield(*Array(4) { "Mountain" })
        alice.hand(equipoiseAdept)
        alice.library("Forest", ogre)
        alice.casts(equipoiseAdept)
        playOut()
        alice.plays("Forest")
        expect { card("Forest").zone shouldBe Zone.Battlefield }
    }

    /** A creature spell and a noncreature spell this turn: at combat, a card and two counters. */
    // CR 603.4
    @Test
    fun vargaWyrmclawRewardsBoth() = scenario {
        alice.battlefield(vargaWyrmclaw, "Forest", "Forest", "Island")
        alice.hand(bear, Plain.study)
        alice.library(ogre, giant)
        alice.casts(bear, paying = listOf("Forest", "Forest#1"))
        playOut()
        alice.casts(Plain.study)
        playOut()
        advanceTo(TurnStep.BeginningOfCombat)
        playOut()
        expect {
            vargaWyrmclaw.counters(p1p1) shouldBe 2
            alice.hand.size shouldBe 2
        }
    }

    /** The next spell this turn is copied. */
    // CR 707.10
    @Test
    fun embergripGrapplerCopiesTheNextSpell() = scenario {
        alice.battlefield("Island", "Mountain", "Plains", "Plains")
        alice.hand(embergripGrappler, Plain.heal)
        alice.casts(embergripGrappler, paying = listOf("Island", "Mountain", "Plains"))
        playOut()
        alice.casts(Plain.heal)
        playOut()
        expect { alice.life shouldBe 24 }
    }

    /** {2} less after another spell this turn: three cards, a discard. */
    // CR 601.2f
    @Test
    fun centerTheThoughtsCheaperSecond() = scenario {
        alice.battlefield("Island", "Island", "Island", "Island")
        alice.hand(Plain.study, centerTheThoughts)
        alice.library(ogre, giant, bear, ogre)
        alice.casts(Plain.study, paying = listOf("Island"))
        playOut()
        alice.casts(centerTheThoughts, paying = listOf("Island#1", "Island#2", "Island#3"))
        playOut(ogre)
        expect { alice.hand.size shouldBe 3 }
    }

    /** The second spell each turn costs {1} less. */
    // CR 601.2f
    @Test
    fun tallspireBellkeeperDiscountsTheSecond() = scenario {
        alice.battlefield(tallspireBellkeeper, "Island", "Mountain", "Mountain")
        alice.hand(Plain.study, ogre)
        alice.library(giant)
        alice.casts(Plain.study, paying = listOf("Island"))
        playOut()
        alice.casts(ogre, paying = listOf("Mountain", "Mountain#1"))
        playOut()
        expect { ogre.zone shouldBe Zone.Battlefield }
    }

    /** Flurry: +1/+1; {1}: one mana of three colours once a turn. */
    @Test
    fun wayfarerDevoteeFlurry() = scenario {
        alice.battlefield(wayfarerDevotee, "Island", "Plains")
        alice.hand(Plain.study, Plain.heal)
        alice.library(ogre)
        alice.casts(Plain.study)
        playOut()
        alice.casts(Plain.heal)
        playOut()
        expect { wayfarerDevotee.power shouldBe 3 }
    }

    /** At the end step, the hand discarded for a card per spell cast this turn. */
    // CR 701.9a
    @Test
    fun lirienDiscardsHandForSpells() = scenario {
        alice.battlefield(lirienWaymasterAbbess, "Island", "Plains")
        alice.hand(Plain.study, Plain.heal, bear)
        alice.library(ogre, giant, ogre)
        alice.casts(Plain.study)
        playOut()
        alice.casts(Plain.heal)
        playOut()
        advanceTo(TurnStep.End)
        playOut()
        expect {
            alice.graveyard has bear
            alice.hand.size shouldBe 2
        }
    }

    /** Flurry: a counter and scry 1. */
    @Test
    fun balancedAdeptFlurry() = scenario {
        alice.battlefield(balancedAdept, "Island", "Plains")
        alice.hand(Plain.study, Plain.heal)
        alice.library(ogre, giant)
        alice.casts(Plain.study)
        playOut()
        alice.casts(Plain.heal)
        playOut()
        expect { balancedAdept.counters(p1p1) shouldBe 1 }
    }

    /** Two Monk tokens with prowess; cheaper after another spell; a big creature destroyed. */
    // CR 700.2a
    @Test
    fun rouseTheMonasteryMonks() = scenario {
        alice.battlefield("Plains", "Plains", "Plains", "Plains")
        alice.hand(rouseTheMonastery)
        alice.casts(rouseTheMonastery, modes = listOf(0))
        playOut()
        expect { "Monk Token" hasKeyword Keyword.Prowess }
    }

    @Test
    fun rouseTheMonasteryDestroysCheaply() = scenario {
        alice.battlefield("Plains", "Plains", "Island")
        alice.hand(Plain.study, rouseTheMonastery)
        alice.library(ogre)
        bob.battlefield(giant)
        alice.casts(Plain.study, paying = listOf("Island"))
        playOut()
        alice.casts(rouseTheMonastery, modes = listOf(2), target = giant)
        playOut()
        expect { bob.graveyard has giant }
    }

    /** At combat after two spells, a 3/3 hasty Monk; sacrificed with the hand, two cards. */
    // CR 205.1b
    @Test
    fun echoingSummonsAnimates() = scenario {
        alice.battlefield(echoingSummons, "Island", "Plains")
        alice.hand(Plain.study, Plain.heal)
        alice.library(ogre)
        alice.casts(Plain.study)
        playOut()
        alice.casts(Plain.heal)
        playOut()
        advanceTo(TurnStep.BeginningOfCombat)
        playOut()
        expect {
            echoingSummons.power shouldBe 3
            echoingSummons.types.contains(CardType.Creature) shouldBe true
        }
    }

    @Test
    fun echoingSummonsCashesIn() = scenario {
        alice.battlefield(echoingSummons, "Mountain", "Mountain")
        alice.hand(bear)
        alice.library(ogre, giant)
        alice.activates(echoingSummons)
        playOut()
        expect {
            alice.graveyard has bear
            alice.hand.size shouldBe 2
        }
    }

    /** Cast after another spell, it copies itself: two Sages. */
    // CR 707.10
    @Test
    fun sageOfTheHeavensCopiesItself() = scenario {
        alice.battlefield("Island", "Plains", "Plains", "Plains")
        alice.hand(Plain.study, sageOfTheHeavens)
        alice.library(ogre)
        alice.casts(Plain.study, paying = listOf("Island"))
        playOut()
        alice.casts(sageOfTheHeavens)
        playOut()
        expect { card(sageOfTheHeavens, 1).zone shouldBe Zone.Battlefield }
    }

    /** The second spell is copied, then exiled with four time counters and suspend. */
    // CR 702.62a, b
    @Test
    fun rhenuSuspendsTheSecondSpell() = scenario {
        alice.battlefield(rhenuMasterSchemer, "Island", "Plains")
        alice.hand(Plain.study, Plain.heal)
        alice.library(ogre)
        alice.casts(Plain.study)
        playOut()
        alice.casts(Plain.heal)
        playOut()
        expect {
            alice.life shouldBe 22
            alice.exile has Plain.heal
            card(Plain.heal).counters(arcanum.api.CounterKind.Time) shouldBe 4
        }
    }

    /** A cheap permanent card back to the hand; flurry taps an opponent's creature. */
    // CR 701.26a
    @Test
    fun pathspeakerGuardReturnsAndTaps() = scenario {
        alice.battlefield("Plains", "Plains", "Plains", "Plains", "Island", "Plains")
        alice.hand(pathspeakerGuard, Plain.study)
        alice.graveyard(bear)
        alice.library(ogre)
        bob.battlefield(giant)
        alice.casts(pathspeakerGuard, paying = listOf("Plains", "Plains#1", "Plains#2", "Plains#3"))
        playOut(bear)
        expect { alice.hand has bear }
        alice.casts(Plain.study)
        playOut(giant)
        expect { giant.tapped shouldBe true }
    }

    /** Flurry: a flying Bird. */
    @Test
    fun featherbladeDiscipleFlurry() = scenario {
        alice.battlefield(featherbladeDisciple, "Island", "Plains")
        alice.hand(Plain.study, Plain.heal)
        alice.library(ogre)
        alice.casts(Plain.study)
        playOut()
        alice.casts(Plain.heal)
        playOut()
        expect { "Bird Token" hasKeyword Keyword.Flying }
    }
}
