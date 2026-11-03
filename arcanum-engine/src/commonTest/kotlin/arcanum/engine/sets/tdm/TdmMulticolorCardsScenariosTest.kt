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
 * Tarkir: Dragonstorm cards of parser chunks 14–15 in play: the multicoloured cards and Sagas. Jeskai Brushmaster's invented twin and Dragon Sniper's have only covered keywords and are not tested.
 */
class TdmMulticolorCardsScenariosTest {
    private val totalOnslaught = Cards.invent("Total Onslaught", "{2}{R}{W}{B}", "Enchantment", """
        Creatures you control get +1/+1 and have deathtouch.
        When this enchantment enters, if it's your main phase, there is an additional combat phase after this phase followed by an additional main phase. When you next attack this turn, untap each creature you control.
    """)
    private val rouseTheReveredDead = Cards.invent("Rouse the Revered Dead", "{B}{G}{U}", "Enchantment — Saga", """
        (As this Saga enters and after your draw step, add a lore counter. Sacrifice after III.)
        I — Destroy target nonland permanent.
        II — Mill three cards.
        III — You may discard a card. When you do, return target creature or land card from your graveyard to your hand.
    """)
    private val deathBreedsLife = Cards.invent("Death Breeds Life", "{5}{B}{G}{U}", "Sorcery", "Destroy all creatures and enchantments. Draw a card for each permanent destroyed this way.")
    private val joltingCurrent = Cards.invent("Jolting Current", "{2/R}{2/W}{2/B}", "Sorcery", "Jolting Current deals 4 damage to target creature or planeswalker and you gain 2 life.")
    private val wyrmclawStrike = Cards.invent("Wyrmclaw Strike", "{2/G}{2/U}{2/R}", "Sorcery", "Double the power and toughness of target creature you control until end of turn. Then it fights up to one target creature an opponent controls. (Each deals damage equal to its power to the other.)")
    private val fangwardensFamiliar = Cards.invent("Fangwarden's Familiar", "{1}{B}{G}{U}", "Creature — Snake", """
        Flash
        When this creature enters, choose one —
        • You gain 3 life and surveil 3. (Look at the top three cards of your library, then put any number of them into your graveyard and the rest on top of your library in any order.)
        • Destroy target enchantment.
        • Counter target creature spell.
    """, "3/3")
    private val odrunDawnOfTheHouses = Cards.invent("Odrun, Dawn of the Houses", "{W}{B}{G}", "Legendary Creature — Human Warrior", """
        Trample
        Whenever Odrun enters or attacks, you may sacrifice a nonland permanent. When you do, put a +1/+1 counter on each creature you control.
    """, "3/3")
    private val vanguardRush = Cards.invent("Vanguard Rush", "{R}{W}", "Instant", """
        Choose one —
        • Create two 1/1 red Goblin creature tokens.
        • Target creature gets +X/+X until end of turn, where X is the number of creatures you control.
    """)
    private val fenmireNightwatch = Cards.invent("Fenmire Nightwatch", "{2/B}{2/G}{2/U}", "Creature — Human Ranger", "When this creature enters, look at the top three cards of your library. You may put one of those cards back on top of your library. Put the rest into your graveyard.", "3/3")
    private val hardbittenTactician = Cards.invent("Hardbitten Tactician", "{1}{W}{B}", "Creature — Human Warrior", "{1}, Sacrifice a token: Draw a card.", "2/4")
    private val hostOfTheAfterlife = Cards.invent("Host of the Afterlife", "{2}{B}{G}", "Creature — Zombie Warlock", """
        This creature enters with two +1/+1 counters on it.
        Whenever this creature or another creature you control dies, if it had counters on it, put its counters on up to one target creature you control.
    """, "2/2")
    private val certainDefeat = Cards.invent("Certain Defeat", "{1}{R}{W}{B}", "Instant", """
        This spell can't be countered.
        Exile target nonland permanent. Its controller loses 3 life and you gain 3 life.
    """)
    private val wayfarerRevelation = Cards.invent("Wayfarer Revelation", "{4}{U}{R}{W}", "Instant", "Return target spell or permanent to its owner's hand. Wayfarer Revelation deals 4 damage to any target. Create two 1/1 white Monk creature tokens with prowess. Draw two cards. You gain 4 life.")
    private val kinrootSeverance = Cards.invent("Kinroot Severance", "{2/W}{2/B}{2/G}", "Instant", "Exile target permanent with mana value 3 or greater.")
    private val riverbankSkimmer = Cards.invent("Riverbank Skimmer", "{G}{U}", "Creature — Bird Scout", """
        Flying
        Whenever a card leaves your graveyard during your turn, draw a card. This ability triggers only once each turn.
    """, "2/2")
    private val varrekTheFangwarden = Cards.invent("Varrek, the Fangwarden", "{1}{B}{G}{U}", "Legendary Creature — Zombie Warrior", """
        Indestructible
        Whenever Varrek deals combat damage to a player, exile the top X cards of their library, where X is the amount of damage dealt. You may cast any number of spells with mana value X or less from among them without paying their mana costs.
    """, "2/1")
    private val lurkInAmbush = Cards.invent("Lurk in Ambush", "{B}{G}{U}", "Sorcery", "Return target creature card from your graveyard to your hand. Lurk in Ambush deals damage equal to that card's power to target creature.")
    private val lilyglowDancers = Cards.invent("Lilyglow Dancers", "{2}{B}{G}{U}", "Creature — Zombie Bard", """
        Lifelink
        When this creature enters, search your library for a black card, a green card, and a blue card. Put those cards into your graveyard, then shuffle.
    """, "3/6")
    private val marshalOfTheForsaken = Cards.invent("Marshal of the Forsaken", "{2}{W}{B}", "Creature — Orc Warrior", """
        Deathtouch
        Whenever you attack, target creature gets +X/+X until end of turn, where X is the number of attacking creatures.
    """, "3/3")
    private val cloisterMessenger = Cards.invent("Cloister Messenger", "{2/U}{2/R}{2/W}", "Creature — Bird Scout", """
        Flying, vigilance
        When this creature enters, put up to one target noncreature, nonland card from your graveyard on top of your library.
    """, "2/3")
    private val anotherWayOnward = Cards.invent("Another Way Onward", "{2}{U}{R}{W}", "Instant", "The next time a source of your choice would deal damage to you this turn, prevent that damage. When damage is prevented this way, Another Way Onward deals that much damage to that source's controller and you draw that many cards.")
    private val everbloomReturn = Cards.invent("Everbloom Return", "{3}{W}{B}{G}", "Sorcery", "Return target permanent card from your graveyard to the battlefield with a hexproof counter and an indestructible counter on it.")
    private val rakshasasPact = Cards.invent("Rakshasa's Pact", "{2/B}{2/G}{2/U}", "Instant", "Look at the top four cards of your library. Put two of them into your hand and the rest into your graveyard.")
    private val relearnThePath = Cards.invent("Relearn the Path", "{U}{R}{W}", "Enchantment — Saga", """
        (As this Saga enters and after your draw step, add a lore counter. Sacrifice after III.)
        I, II — Look at the top three cards of your library. Put one of them into your hand and the rest on the bottom of your library in any order.
        III — Whenever you cast a noncreature spell this turn, target creature you control gains double strike until end of turn.
    """)
    private val trustedMerchant = Cards.invent("Trusted Merchant", "{2/W}{2/B}{2/G}", "Creature — Human Citizen", "When this creature enters or dies, put a +1/+1 counter on target creature you control.", "2/2")
    private val returnOfTheForebears = Cards.invent("Return of the Forebears", "{1}{W}{B}{G}", "Enchantment — Saga", """
        (As this Saga enters and after your draw step, add a lore counter. Sacrifice after III.)
        I — Create three 1/1 white Spirit creature tokens.
        II — Distribute three +1/+1 counters among one, two, or three target creatures you control.
        III — Creatures you control gain trample and lifelink until end of turn.
    """)
    private val millwheelSweep = Cards.invent("Millwheel Sweep", "{2/U}{2/R}{2/W}", "Sorcery", """
        Tap target creature. Put three stun counters on it. (If a permanent with a stun counter would become untapped, remove one from it instead.)
        Exile the top two cards of your library. Choose one of them. Until the end of your next turn, you may play that card.
    """)
    private val bellowOfTheEndlessHymn = Cards.invent("Bellow of the Endless Hymn", "{2}{G}{U}{R}", "Enchantment — Saga", """
        (As this Saga enters and after your draw step, add a lore counter. Sacrifice after III.)
        I, II — Create a 5/5 green Elephant creature token.
        III — Double the power and toughness of each creature you control until end of turn.
    """)
    private val sunderingPriest = Cards.invent("Sundering Priest", "{W}{B}{G}", "Creature — Djinn Cleric", """
        Deathtouch
        When this creature enters, target opponent reveals their hand. You may choose a nonland card from it. If you do, exile that card.
        When this creature leaves the battlefield, the exiled card's owner creates an X/X white Spirit creature token, where X is the mana value of the exiled card.
    """, "3/3")
    private val meleeRhino = Cards.invent("Melee Rhino", "{W}{B}{G}", "Creature — Rhino", """
        Trample
        When this creature enters, each opponent loses 2 life and you gain 2 life.
    """, "3/4")
    private val steadfastSuccessor = Cards.invent("Steadfast Successor", "{1}{B}{G}", "Creature — Human Warrior", """
        Menace (This creature can't be blocked except by two or more creatures.)
        Whenever one or more counters are put on a creature you control, if it's the first time counters have been put on that creature this turn, put a +1/+1 counter on that creature.
    """, "3/2")
    private val frontierBattlecrier = Cards.invent("Frontier Battlecrier", "{G}{U}{R}", "Creature — Orc Ranger", "During your turn, spells you cast cost {1} less to cast for each creature you control with power 4 or greater.", "4/3")
    private val rumbleOfUnity = Cards.invent("Rumble of Unity", "{R}{W}{B}", "Enchantment — Saga", """
        (As this Saga enters and after your draw step, add a lore counter. Sacrifice after III.)
        I — You draw two cards and you lose 2 life.
        II, III — Whenever a creature you control enters this turn, each opponent loses 1 life and you gain 1 life.
    """)
    private val barrowRoadwatcher = Cards.invent("Barrow Roadwatcher", "{1}{W}{B}{G}", "Creature — Human Scout", "When this creature enters, if you cast it, mill four cards. When you do, return target creature card with mana value 3 or less from your graveyard to the battlefield.", "3/3")

    private val bear = Plain.bear
    private val ogre = Plain.ogre
    private val giant = Plain.giant
    private val dragon = Plain.dragon
    private val p1p1 = Plain.p1p1

    private val muster = Cards.invent("Steppe Muster", "{W}", "Sorcery", "Create a 1/1 white Soldier creature token.")
    private val renewer = Cards.invent("Steppe Renewer", "{1}{G}", "Creature — Human", "{G}, Exile this card from your graveyard: Put a +1/+1 counter on target creature.", "1/1")

    // ---- Chunk 14: multicoloured

    /** Cast in the main phase: an extra combat and main phase; the next attack untaps the team; +1/+1 and deathtouch. */
    // CR 500.8, 505.1
    @Test
    fun totalOnslaughtAddsACombat() = scenario {
        alice.battlefield("Mountain", "Plains", "Swamp", "Swamp", "Swamp", bear)
        alice.hand(totalOnslaught)
        bob.library(ogre)
        alice.casts(totalOnslaught)
        playOut()
        expect {
            bear.power shouldBe 3
            bear hasKeyword Keyword.Deathtouch
        }
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(bear)
        playOut()
        expect { bear.tapped shouldBe false }
        advanceTo(TurnStep.PostcombatMain)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(bear)
    }

    /** Chapter I destroys a nonland permanent; II mills three; III trades a discard for a creature card. */
    // CR 714.2b
    @Test
    fun rouseTheReveredDeadChapterOne() = scenario {
        bob.battlefield(giant)
        loreChapter(rouseTheReveredDead, 1)
        playOut(giant)
        expect { bob.graveyard has giant }
    }

    @Test
    fun rouseTheReveredDeadChapterThree() = scenario {
        alice.hand(bear)
        alice.graveyard(giant)
        loreChapter(rouseTheReveredDead, 3)
        playOut(giant)
        expect { alice.graveyard has bear }
        expect { alice.hand has giant }
    }

    /** Creatures and enchantments destroyed; a card for each. */
    @Test
    fun deathBreedsLifeDraws() = scenario {
        alice.battlefield(*Array(8) { "Swamp" }, "Forest", "Island", bear)
        alice.hand(deathBreedsLife)
        alice.library(ogre, giant, ogre)
        bob.battlefield(giant, Plain.charm)
        alice.casts(deathBreedsLife)
        playOut()
        expect {
            alice.hand.size shouldBe 3
            bob.graveyard has Plain.charm
        }
    }

    /** 4 damage and 2 life. */
    @Test
    fun joltingCurrentShocks() = scenario {
        alice.battlefield("Mountain", "Plains", "Swamp")
        alice.hand(joltingCurrent)
        bob.battlefield(ogre)
        alice.casts(joltingCurrent, target = ogre)
        playOut()
        expect {
            bob.graveyard has ogre
            alice.life shouldBe 22
        }
    }

    /** Power and toughness doubled, then a fight. */
    // CR 701.10e, 701.14a
    @Test
    fun wyrmclawStrikeDoublesAndFights() = scenario {
        alice.battlefield("Forest", "Island", "Mountain", ogre)
        alice.hand(wyrmclawStrike)
        bob.battlefield(giant)
        alice.casts(wyrmclawStrike, targets = listOf(ogre, giant))
        playOut()
        expect {
            bob.graveyard has giant
            ogre.power shouldBe 6
        }
    }

    /** Modes: 3 life and surveil 3; an enchantment destroyed; a creature spell countered. */
    @Test
    fun fangwardensFamiliarCountersACreature() = scenario {
        alice.battlefield("Swamp", "Forest", "Island", "Island")
        alice.hand(fangwardensFamiliar)
        bob.battlefield("Forest", "Forest")
        bob.hand(bear)
        turn(active = bob)
        bob.casts(bear)
        bob.passes()
        alice.casts(fangwardensFamiliar)
        playOut()
        modes(2)
        playOut(bear)
        expect { bob.graveyard has bear }
    }

    /** A nonland permanent sacrificed: a counter on each creature. */
    // CR 603.12
    @Test
    fun odrunSacrificesForCounters() = scenario {
        alice.battlefield("Plains", "Swamp", "Forest", bear, Plain.idol)
        alice.hand(odrunDawnOfTheHouses)
        alice.casts(odrunDawnOfTheHouses)
        playOut(Plain.idol)
        expect {
            bear.counters(p1p1) shouldBe 1
            odrunDawnOfTheHouses.counters(p1p1) shouldBe 1
        }
    }

    /** Two Goblins; or +X/+X for the creatures you control. */
    @Test
    fun vanguardRushPumps() = scenario {
        alice.battlefield("Mountain", "Plains", bear, ogre)
        alice.hand(vanguardRush)
        alice.casts(vanguardRush, modes = listOf(1), target = bear)
        playOut()
        expect { bear.power shouldBe 4 }
    }

    /** One of the top three kept on top, the rest to the graveyard. */
    @Test
    fun fenmireNightwatchSorts() = scenario {
        alice.battlefield("Swamp", "Forest", "Island")
        alice.hand(fenmireNightwatch)
        alice.library(ogre, giant, bear)
        alice.casts(fenmireNightwatch)
        playOut(giant)
        expect {
            alice.library has giant
            alice.graveyard.size shouldBe 2
        }
    }

    /** A token sacrificed: a card. */
    @Test
    fun hardbittenTacticianDraws() = scenario {
        alice.battlefield(hardbittenTactician, "Plains", "Plains")
        alice.hand(muster)
        alice.library(ogre)
        alice.casts(muster, paying = listOf("Plains"))
        playOut()
        alice.activates(hardbittenTactician, choosing = listOf("Soldier Token"))
        playOut()
        expect { alice.hand has ogre }
    }

    /** Enters with two counters; dying, they move to another creature. */
    // CR 603.10a
    @Test
    fun hostOfTheAfterlifePassesCounters() = scenario {
        alice.battlefield("Swamp", "Swamp", "Forest", "Forest", "Swamp", bear)
        alice.hand(hostOfTheAfterlife, Plain.doom)
        alice.casts(hostOfTheAfterlife, paying = listOf("Swamp", "Swamp#1", "Forest", "Forest#1"))
        playOut()
        expect { hostOfTheAfterlife.counters(p1p1) shouldBe 2 }
        alice.casts(Plain.doom, target = hostOfTheAfterlife)
        playOut(bear)
        expect { bear.counters(p1p1) shouldBe 2 }
    }

    /** Can't be countered: a permanent exiled, its controller loses 3, you gain 3. */
    @Test
    fun certainDefeatExiles() = scenario {
        alice.battlefield("Mountain", "Plains", "Swamp", "Swamp")
        alice.hand(certainDefeat)
        bob.battlefield(giant)
        alice.casts(certainDefeat, target = giant)
        playOut()
        expect {
            bob.exile has giant
            bob.life shouldBe 17
            alice.life shouldBe 23
        }
    }

    /** Bounce, 4 damage, two Monks, two cards and 4 life. */
    @Test
    fun wayfarerRevelationDoesItAll() = scenario {
        alice.battlefield("Island", "Mountain", "Plains", "Island", "Island", "Island", "Island")
        alice.hand(wayfarerRevelation)
        alice.library(ogre, giant)
        bob.battlefield(giant)
        alice.casts(wayfarerRevelation, targets = listOf(giant, "bob"))
        playOut()
        expect {
            bob.hand has giant
            bob.life shouldBe 16
            card("Monk Token", 1).zone shouldBe Zone.Battlefield
            alice.life shouldBe 24
        }
    }

    /** A permanent of mana value 3 or more exiled. */
    @Test
    fun kinrootSeveranceExiles() = scenario {
        alice.battlefield("Plains", "Swamp", "Forest")
        alice.hand(kinrootSeverance)
        bob.battlefield(ogre)
        alice.casts(kinrootSeverance, target = ogre)
        playOut()
        expect { bob.exile has ogre }
    }

    /** A card leaving the graveyard on its controller's turn: a card, once each turn. */
    @Test
    fun riverbankSkimmerDraws() = scenario {
        alice.battlefield(riverbankSkimmer, bear, "Forest")
        alice.graveyard(renewer)
        alice.library(ogre)
        alice.activates(renewer, target = bear)
        playOut()
        expect { alice.hand has ogre }
    }

    /** Hitting for 4: the top four exiled, a spell of mana value 4 or less among them cast free. */
    // CR 601.2a
    @Test
    fun varrekCastsTheSpoils() = scenario {
        alice.battlefield(varrekTheFangwarden)
        alice.battlefield(Plain.idol)
        bob.library(Plain.heal, ogre)
        hits(varrekTheFangwarden, picks = listOf(Plain.heal))
        expect { alice.life shouldBe 22 }
    }

    /** A creature card back to the hand, and damage equal to its power to a creature. */
    @Test
    fun lurkInAmbushStrikes() = scenario {
        alice.battlefield("Swamp", "Forest", "Island")
        alice.hand(lurkInAmbush)
        alice.graveyard(giant)
        bob.battlefield(ogre)
        alice.casts(lurkInAmbush, targets = listOf(giant, ogre))
        playOut()
        expect {
            alice.hand has giant
            bob.graveyard has ogre
        }
    }

    /** A black, a green and a blue card into the graveyard. */
    @Test
    fun lilyglowDancersFillTheGraveyard() = scenario {
        alice.battlefield("Swamp", "Forest", "Island", "Island", "Island", "Island")
        alice.hand(lilyglowDancers)
        alice.library(Plain.doom, bear, Plain.study, ogre)
        alice.casts(lilyglowDancers)
        playOut(Plain.doom, bear, Plain.study)
        expect { alice.graveyard.size shouldBe 3 }
    }

    // ---- Chunk 15: multicoloured

    /** Attacking: a creature gets +X/+X for the attackers. */
    @Test
    fun marshalOfTheForsakenPumps() = scenario {
        alice.battlefield(marshalOfTheForsaken, bear)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(marshalOfTheForsaken, bear)
        playOut(bear)
        expect { bear.power shouldBe 4 }
    }

    /** A noncreature, nonland card from the graveyard on top of the library. */
    @Test
    fun cloisterMessengerStacks() = scenario {
        alice.battlefield("Island", "Mountain", "Plains")
        alice.hand(cloisterMessenger)
        alice.graveyard(Plain.study)
        alice.library(ogre)
        alice.casts(cloisterMessenger)
        playOut(Plain.study)
        expect { alice.library has Plain.study }
    }

    /** The next damage from a chosen source prevented, dealt back to its controller, and as many cards drawn. */
    // CR 615.5, 603.12
    @Test
    fun anotherWayOnwardTurnsDamageBack() = scenario {
        alice.battlefield("Island", "Mountain", "Plains", "Island", "Island")
        alice.hand(anotherWayOnward)
        alice.library(ogre, giant)
        bob.battlefield(giant)
        turn(active = bob)
        advanceTo(TurnStep.DeclareAttackers)
        bob.attacks(giant)
        bob.passes()
        alice.casts(anotherWayOnward)
        playOut(giant)
        advanceTo(TurnStep.DeclareBlockers)
        if (state.priority.holder == null) alice.blocks()
        advanceTo(TurnStep.CombatDamage)
        playOut()
        expect {
            alice.life shouldBe 20
            bob.life shouldBe 15
            alice.hand.size shouldBe 2
        }
    }

    /** A permanent card back with hexproof and indestructible counters. */
    @Test
    fun everbloomReturnRevives() = scenario {
        alice.battlefield("Plains", "Swamp", "Forest", "Forest", "Forest", "Forest")
        alice.hand(everbloomReturn)
        alice.graveyard(giant)
        alice.casts(everbloomReturn, target = giant)
        playOut()
        expect {
            giant hasKeyword Keyword.Hexproof
            giant hasKeyword Keyword.Indestructible
        }
    }

    /** Two of the top four to the hand, the rest to the graveyard. */
    @Test
    fun rakshasasPactPicksTwo() = scenario {
        alice.battlefield("Swamp", "Forest", "Island")
        alice.hand(rakshasasPact)
        alice.library(ogre, giant, bear, Plain.elf)
        alice.casts(rakshasasPact)
        playOut(ogre, giant)
        expect {
            alice.hand.size shouldBe 2
            alice.graveyard.size shouldBe 3
        }
    }

    /** Chapter I: one of the top three to the hand; III: noncreature spells give double strike. */
    @Test
    fun relearnThePathChapterOne() = scenario {
        loreChapter(relearnThePath, 1, giant, bear, Plain.elf)
        playOut(giant)
        expect { alice.hand has giant }
    }

    @Test
    fun relearnThePathChapterThree() = scenario {
        alice.battlefield(bear, "Island")
        alice.hand(Plain.study)
        loreChapter(relearnThePath, 3)
        playOut()
        alice.casts(Plain.study)
        playOut(bear)
        expect { bear hasKeyword Keyword.DoubleStrike }
    }

    /** Entering and dying: a counter on a creature. */
    @Test
    fun trustedMerchantCounters() = scenario {
        alice.battlefield("Plains", "Swamp", "Forest", bear)
        alice.hand(trustedMerchant)
        alice.casts(trustedMerchant)
        playOut(bear)
        expect { bear.counters(p1p1) shouldBe 1 }
    }

    /** Chapter I: three Spirits; II: three counters divided; III: trample and lifelink. */
    @Test
    fun returnOfTheForebearsChapterOne() = scenario {
        loreChapter(returnOfTheForebears, 1)
        playOut()
        expect { card("Spirit Token", 2).zone shouldBe Zone.Battlefield }
    }

    @Test
    fun returnOfTheForebearsChapterThree() = scenario {
        alice.battlefield(bear)
        loreChapter(returnOfTheForebears, 3)
        playOut()
        expect {
            bear hasKeyword Keyword.Trample
            bear hasKeyword Keyword.Lifelink
        }
    }

    /** A creature tapped and stunned thrice; one of two exiled cards playable. */
    @Test
    fun millwheelSweepStunsAndExiles() = scenario {
        alice.battlefield("Island", "Mountain", "Plains")
        alice.hand(millwheelSweep)
        alice.library("Forest", ogre)
        bob.battlefield(giant)
        alice.casts(millwheelSweep, target = giant)
        playOut("Forest")
        expect { giant.counters(arcanum.api.CounterKind.Stun) shouldBe 3 }
        alice.plays("Forest")
        expect { card("Forest").zone shouldBe Zone.Battlefield }
    }

    /** Chapters I and II: Elephants; III doubles each creature's power and toughness. */
    @Test
    fun bellowOfTheEndlessHymnElephant() = scenario {
        loreChapter(bellowOfTheEndlessHymn, 1)
        playOut()
        expect { "Elephant Token".power shouldBe 5 }
    }

    @Test
    fun bellowOfTheEndlessHymnDoubles() = scenario {
        alice.battlefield(bear)
        loreChapter(bellowOfTheEndlessHymn, 3)
        playOut()
        expect { bear.power shouldBe 4 }
    }

    /** A nonland card exiled from the revealed hand; leaving, it gives that card's owner an X/X Spirit. */
    // CR 607.2a
    @Test
    fun sunderingPriestExilesAndRepays() = scenario {
        alice.battlefield("Plains", "Swamp", "Forest", "Swamp")
        alice.hand(sunderingPriest, Plain.doom)
        bob.hand(ogre)
        alice.casts(sunderingPriest, paying = listOf("Plains", "Swamp", "Forest"))
        playOut("bob", ogre)
        expect { bob.exile has ogre }
        alice.casts(Plain.doom, target = sunderingPriest)
        playOut()
        expect {
            "Spirit Token".power shouldBe 3
            "Spirit Token".controller shouldBe bob.id
        }
    }

    /** 2 life drained as it enters. */
    @Test
    fun meleeRhinoDrains() = scenario {
        alice.battlefield("Plains", "Swamp", "Forest")
        alice.hand(meleeRhino)
        alice.casts(meleeRhino)
        playOut()
        expect {
            bob.life shouldBe 18
            alice.life shouldBe 22
        }
    }

    /** The first counters on a creature each turn add one more; the second time doesn't. */
    // CR 603.4
    @Test
    fun steadfastSuccessorAddsACounter() = scenario {
        alice.battlefield(steadfastSuccessor, bear, "Forest", "Forest")
        alice.hand(Plain.growth, Plain.growth)
        alice.casts(Plain.growth, target = bear, paying = listOf("Forest"))
        playOut()
        expect { bear.counters(p1p1) shouldBe 2 }
        alice.casts(Plain.growth, target = bear, paying = listOf("Forest#1"))
        playOut()
        expect { bear.counters(p1p1) shouldBe 3 }
    }

    /** On its controller's turn, spells cost {1} less for each 4-power creature. */
    @Test
    fun frontierBattlecrierDiscounts() = scenario {
        alice.battlefield(frontierBattlecrier, giant, "Mountain", "Mountain")
        alice.hand(ogre)
        alice.casts(ogre)
        playOut()
        expect { ogre.zone shouldBe Zone.Battlefield }
    }

    /** Chapter I: two cards and 2 life lost; II: creatures entering drain this turn. */
    @Test
    fun rumbleOfUnityChapterOne() = scenario {
        loreChapter(rumbleOfUnity, 1, giant, bear)
        playOut()
        expect {
            alice.hand.size shouldBe 2
            alice.life shouldBe 18
        }
    }

    @Test
    fun rumbleOfUnityChapterTwo() = scenario {
        alice.battlefield("Forest", "Forest")
        alice.hand(bear)
        loreChapter(rumbleOfUnity, 2)
        playOut()
        alice.casts(bear)
        playOut()
        expect {
            bob.life shouldBe 19
            alice.life shouldBe 21
        }
    }

    /** Cast: four milled, then a cheap creature card from the graveyard onto the battlefield. */
    @Test
    fun barrowRoadwatcherReanimates() = scenario {
        alice.battlefield("Plains", "Swamp", "Forest", "Forest")
        alice.hand(barrowRoadwatcher)
        alice.library(ogre, giant, Plain.study, Plain.heal)
        alice.casts(barrowRoadwatcher)
        playOut(ogre)
        expect { ogre.zone shouldBe Zone.Battlefield }
    }
}
