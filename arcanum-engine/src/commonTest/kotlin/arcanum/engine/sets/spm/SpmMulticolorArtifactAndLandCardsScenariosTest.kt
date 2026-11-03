package arcanum.engine.sets.spm

import arcanum.api.CardType
import arcanum.api.Keyword
import arcanum.api.TurnStep
import arcanum.api.Zone
import arcanum.engine.cards.Cards
import arcanum.engine.scenario.scenario
import kotlin.test.Test
import kotlin.test.assertFailsWith

/**
 * Marvel's Spider-Man cards of parser chunks 12–13 in play (the backfill of 2026-11-03): the multicoloured, artifact and
 * land cards.
 */
class SpmMulticolorArtifactAndLandCardsScenariosTest {
    private val starbornWebcrawler = Cards.invent("Starborn Webcrawler", "{W}{U}{B}{R}{G}", "Legendary Creature — Spider Human Hero", """
        Flying, first strike, trample, lifelink, haste
        At the beginning of combat on your turn, other Spiders you control gain flying, first strike, trample, lifelink, and haste until end of turn.
    """, "5/5")
    private val verdantFiend = Cards.invent("Verdant Fiend, Returned", "{3}{B}{R}", "Legendary Creature — Goblin Human Villain", """
        Flying, deathtouch
        Whenever Verdant Fiend attacks, discard a card. Then draw a card for each card you've discarded this turn.
    """, "3/3")
    private val valiantTownsfolk = Cards.invent("Valiant Townsfolk", "{G/W}{G/W}", "Creature — Human Citizen", "When this creature enters, draw a card.", "1/1")
    private val trafficReportChopper = Cards.invent("Traffic Report Chopper", "{3}", "Artifact Creature — Construct", """
        Flying
        When this creature enters, create a 1/1 green and white Human Citizen creature token.
    """, "1/1")
    private val rosalindFairweather = Cards.invent("Rosalind Fairweather", "{1}{G/W}", "Legendary Creature — Human Performer", "Whenever a Spider you control enters, draw a card. This ability triggers only once each turn.", "2/2")
    private val morvane = Cards.invent("Morvane the Breathing Vampire", "{2}{U}{B}", "Legendary Creature — Vampire Scientist Villain", """
        Flying, vigilance, lifelink
        {U}{B}, Exile this card from your graveyard: Look at the top three cards of your library. Put one of them into your hand and the rest on the bottom of your library in any order.
    """, "3/1")
    private val gourdBarrage = Cards.invent("Gourd Barrage", "{B/R}", "Sorcery", """
        As an additional cost to cast this spell, discard a card or pay {2}.
        Gourd Barrage deals 3 damage to target creature.
    """)
    private val rhinox = Cards.invent("Rhinox, Charging Hulk", "{3}{R}{R}{G}{G}", "Legendary Creature — Human Villain", """
        Vigilance, trample, haste
        Whenever Rhinox attacks, if you've cast a spell with mana value 4 or greater this turn, draw a card.
    """, "6/7")
    private val webspiritApparition = Cards.invent("Webspirit Apparition", "{1}{R/G}", "Creature — Spider Avatar", """
        Reach
        {T}: Add {R} or {G}.
        Whenever you cast a spell with mana value 4 or greater, untap this creature.
    """, "2/2")
    private val weaveDaughter = Cards.invent("Weave Daughter, Heir Hero", "{G}{W}", "Legendary Creature — Spider Human Hero", """
        During your turn, Weave Daughter has flying.
        When Weave Daughter leaves the battlefield, create a 1/1 green and white Human Citizen creature token.
    """, "2/2")
    private val webmatron = Cards.invent("Webmatron, Dazzling Rescuer", "{1}{W/U}", "Legendary Creature — Spider Human Hero", """
        Flying
        Venom Blast — Artifacts and creatures your opponents control enter tapped.
    """, "2/2")
    private val sunweaver = Cards.invent("Sunweaver, Agile Spinner", "{3}{W/U}", "Legendary Creature — Spider Human Hero", """
        During your turn, Sunweaver has flying.
        When Sunweaver enters, search your library for an Aura or Equipment card, reveal it, put it into your hand, then shuffle.
    """, "3/2")
    private val strandSentinels = Cards.invent("Strand Sentinels", "{4}{G/W}", "Creature — Spider Hero", "When this creature enters, put a +1/+1 counter on each other creature you control.", "4/3")
    private val specter = Cards.invent("Specter, Ruthless Avenger", "{1}{W}{U}", "Legendary Creature — Human Detective Hero", """
        Double strike
        Fear Gas — Specter can't be blocked.
    """, "1/1")
    private val pretzelAndMustard = Cards.invent("Pretzel and Mustard", "{1}", "Artifact — Food", """
        Share — {W}, {T}, Sacrifice this artifact: Put a +1/+1 counter on up to one target creature. Draw a card. Activate only as a sorcery.
        Nosh — {2}, {T}, Sacrifice this artifact: You gain 3 life and draw a card.
    """)
    private val uncannyHeadstone = Cards.invent("Uncanny Headstone", "{2}", "Artifact", """
        When this artifact enters, draw a card.
        {1}{B}, Sacrifice this artifact: Mill four cards. You may put a creature card from among them into your hand. (To mill four cards, put the top four cards of your library into your graveyard.)
    """)
    private val streetVendorWagon = Cards.invent("Street Vendor Wagon", "{3}", "Artifact", """
        When this artifact enters, create a Food token. (It's an artifact with "{2}, {T}, Sacrifice this token: You gain 3 life.")
        {T}: Add one mana of any color.
    """)
    private val commuterBarge = Cards.invent("Commuter Barge", "{3}", "Artifact — Vehicle", """
        Whenever this Vehicle attacks, you may pay {U}. When you do, another target attacking creature can't be blocked this turn.
        Crew 2 (Tap any number of creatures you control with total power 2 or more: This Vehicle becomes an artifact creature until end of turn.)
    """, "4/3")
    private val crawlerDrone = Cards.invent("Crawler Drone", "{2}", "Artifact Creature — Spider Robot Scout", """
        Reach
        When this creature enters, you may search your library for a basic land card, reveal it, then shuffle and put that card on top.
    """, "2/1")
    private val webwheelRoadster = Cards.invent("Webwheel Roadster", "{3}", "Artifact — Vehicle", """
        Trample
        Whenever this Vehicle attacks or blocks, it gets +1/+1 until end of turn for each Spider you control.
        Crew 2
    """, "3/3")
    private val ironDemolitionBall = Cards.invent("Iron Demolition Ball", "{5}", "Artifact", """
        When this artifact enters, it deals 5 damage to target creature.
        {1}{R}, Discard this card: Destroy target artifact.
    """)
    private val undergroundRailcar = Cards.invent("Underground Railcar", "{2}", "Artifact — Vehicle", """
        When this Vehicle enters, you may pay {G}. If you do, search your library for a basic land card, reveal it, put it into your hand, then shuffle.
        Crew 2 (Tap any number of creatures you control with total power 2 or more: This Vehicle becomes an artifact creature until end of turn.)
    """, "3/1")
    private val morningHeraldTower = Cards.invent("Morning Herald Tower", null, "Land", """
        {T}: Add {C}.
        {1}, {T}: Add one mana of any color.
        Smear Campaign — {1}, {T}: Target legendary creature gains menace until end of turn. Activate only as a sorcery.
    """)
    private val forebodingSanitarium = Cards.invent("Foreboding Sanitarium", null, "Land", """
        This land enters tapped.
        {T}: Add {B} or {R}.
        {4}, {T}: Surveil 1. (Look at the top card of your library. You may put it into your graveyard.)
    """)
    private val bustlingSkyline = Cards.invent("Bustling Skyline", null, "Land", "{T}, Sacrifice this land: Search your library for a basic land card, put it onto the battlefield tapped, then shuffle.")
    private val bear = spmBear
    private val ogre = spmOgre
    private val giant = spmGiant
    private val spider = spmSpider

    // ---- Chunk 12: multicoloured

    /** At the beginning of combat, the other Spiders gain its five keywords. */
    // CR 507.1, 611.2c
    @Test
    fun starbornWebcrawlerSharesItsKeywords() = scenario {
        alice.battlefield(starbornWebcrawler, spider, bear)
        advanceTo(TurnStep.BeginningOfCombat)
        playOut()
        expect {
            spider hasKeyword Keyword.Flying
            spider hasKeyword Keyword.FirstStrike
            spider hasKeyword Keyword.Trample
            spider hasKeyword Keyword.Lifelink
            spider hasKeyword Keyword.Haste
            bear lacksKeyword Keyword.Flying
        }
    }

    /** Attacking: a discard, then a card for each discarded this turn (two, after an earlier discard). */
    // CR 701.9a
    @Test
    fun verdantFiendDrawsForDiscards() = scenario {
        alice.battlefield(verdantFiend, "Swamp")
        alice.hand(gourdBarrage, bear, ogre)
        alice.library(giant, giant, giant)
        bob.battlefield(spider)
        alice.casts(gourdBarrage, target = spider, option = "Discard", choosing = listOf(bear))
        playOut()
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(verdantFiend)
        playOut(ogre)
        expect {
            alice.graveyard has ogre
            alice.hand.size shouldBe 2
        }
    }

    /** A card as it enters, paid with hybrid mana. */
    // CR 107.4e
    @Test
    fun valiantTownsfolkDraw() = scenario {
        alice.battlefield("Forest", "Plains")
        alice.hand(valiantTownsfolk)
        alice.library(ogre)
        alice.casts(valiantTownsfolk)
        playOut()
        expect { alice.hand has ogre }
    }

    /** The first Spider entering each turn draws a card; the second doesn't. */
    // CR 603.2i
    @Test
    fun rosalindFairweatherDrawsForTheFirstSpider() = scenario {
        alice.battlefield(rosalindFairweather, "Forest", "Forest", "Forest", "Forest")
        alice.hand(spider, spider)
        alice.library(ogre, giant)
        alice.casts(spider, paying = listOf("Forest", "Forest#1"))
        playOut()
        alice.casts(spider, paying = listOf("Forest#2", "Forest#3"))
        playOut()
        expect {
            alice.hand has ogre
            alice.library has giant
        }
    }

    /** From the graveyard: one of the top three into the hand, the rest to the bottom. */
    // CR 401.4
    @Test
    fun morvaneDigsFromTheGraveyard() = scenario {
        alice.battlefield("Island", "Swamp")
        alice.graveyard(morvane)
        alice.library(bear, giant, ogre, Spm.shock)
        alice.activates(morvane)
        playOut(giant, bear, ogre)
        expect {
            alice.hand has giant
            alice.exile has morvane
            alice.hand.size shouldBe 1
        }
    }

    /** Paid with {2} instead of a discard: 3 damage. */
    // CR 601.2b, 118.8
    @Test
    fun gourdBarragePaysTwo() = scenario {
        alice.battlefield("Swamp", "Mountain", "Mountain")
        alice.hand(gourdBarrage, bear)
        bob.battlefield(ogre)
        alice.casts(gourdBarrage, target = ogre, option = "Pay")
        playOut()
        expect {
            bob.graveyard has ogre
            alice.hand has bear
        }
    }

    @Test
    fun gourdBarrageDiscards() = scenario {
        alice.battlefield("Swamp")
        alice.hand(gourdBarrage, bear)
        bob.battlefield(ogre)
        alice.casts(gourdBarrage, target = ogre, option = "Discard", choosing = listOf(bear))
        playOut()
        expect {
            bob.graveyard has ogre
            alice.graveyard has bear
        }
    }

    /** Attacking after a spell of mana value 4: a card. */
    // CR 603.4
    @Test
    fun rhinoxDrawsAfterABigSpell() = scenario {
        alice.battlefield(rhinox, *Array(4) { "Forest" })
        alice.hand(Spm.bigSpell)
        alice.library(ogre)
        alice.casts(Spm.bigSpell)
        playOut()
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(rhinox)
        playOut()
        expect { alice.hand has ogre }
    }

    @Test
    fun rhinoxQuietWithoutABigSpell() = scenario {
        alice.battlefield(rhinox)
        alice.library(ogre)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(rhinox)
        playOut()
        expect { alice.library has ogre }
    }

    /** Tapped for mana toward a big spell, it untaps. */
    // CR 701.26b
    @Test
    fun webspiritApparitionUntapsForBigSpells() = scenario {
        alice.battlefield(webspiritApparition, "Forest", "Forest", "Forest")
        alice.hand(Spm.bigSpell)
        alice.casts(Spm.bigSpell, paying = listOf("Forest", "Forest#1", "Forest#2", webspiritApparition + ":G"))
        playOut()
        expect {
            webspiritApparition.tapped shouldBe false
            alice.life shouldBe 21
        }
    }

    /** Flying on her own turn only; leaving, she leaves a Citizen. */
    // CR 611.3a, 603.6c
    @Test
    fun weaveDaughterFliesOnHerTurnAndLeavesACitizen() = scenario {
        alice.battlefield(weaveDaughter, "Swamp")
        alice.hand(Spm.doom)
        expect { weaveDaughter hasKeyword Keyword.Flying }
        alice.casts(Spm.doom, target = weaveDaughter)
        playOut()
        expect { alice.battlefield has "Human Citizen Token" }
    }

    @Test
    fun weaveDaughterGroundedOnTheOpponentsTurn() = scenario {
        alice.battlefield(weaveDaughter)
        turn(active = bob)
        expect { weaveDaughter lacksKeyword Keyword.Flying }
    }

    /** The opponent's creatures enter tapped; Alice's don't. */
    // CR 614.1c
    @Test
    fun webmatronTapsTheOpponentsArrivals() = scenario {
        alice.battlefield(webmatron)
        bob.battlefield("Forest", "Forest")
        bob.hand(bear)
        turn(active = bob)
        bob.casts(bear)
        playOut()
        expect { bear.tapped shouldBe true }
    }

    /** As it enters, an Equipment card from the library. */
    // CR 701.23a
    @Test
    fun sunweaverFindsAnEquipment() = scenario {
        alice.battlefield("Plains", "Plains", "Plains", "Plains")
        alice.hand(sunweaver)
        alice.library(ogre, Spm.sword)
        alice.casts(sunweaver)
        playOut(Spm.sword)
        expect {
            alice.hand has Spm.sword
            sunweaver hasKeyword Keyword.Flying
        }
    }

    /** A counter on each other creature as it enters. */
    // CR 122.1
    @Test
    fun strandSentinelsCounterTheTeam() = scenario {
        alice.battlefield(*Array(5) { "Forest" }, bear, ogre)
        alice.hand(strandSentinels)
        alice.casts(strandSentinels)
        playOut()
        expect {
            bear.counters(p1p1) shouldBe 1
            ogre.counters(p1p1) shouldBe 1
            strandSentinels.counters(p1p1) shouldBe 0
        }
    }

    /** Unblockable double strike: 2 damage. */
    // CR 702.4b
    @Test
    fun specterCantBeBlocked() = scenario {
        alice.battlefield(specter)
        bob.battlefield(giant)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(specter)
        advanceTo(TurnStep.DeclareBlockers)
        assertFailsWith<Throwable> { bob.blocks(giant to specter) }
    }

    // ---- Chunk 13: artifacts and lands

    /** Share: a counter and a card; Nosh: 3 life and a card. */
    // CR 602.5d
    @Test
    fun pretzelAndMustardShares() = scenario {
        alice.battlefield(pretzelAndMustard, bear, "Plains")
        alice.library(ogre)
        alice.activates(pretzelAndMustard, target = bear)
        playOut()
        expect {
            bear.counters(p1p1) shouldBe 1
            alice.hand has ogre
        }
    }

    @Test
    fun pretzelAndMustardNoshes() = scenario {
        alice.battlefield(pretzelAndMustard, "Plains", "Plains")
        alice.library(ogre)
        alice.activates(pretzelAndMustard, ability = 1)
        playOut()
        expect {
            alice.life shouldBe 23
            alice.hand has ogre
        }
    }

    /** A card as it enters; sacrificed, four milled and a creature card kept. */
    // CR 701.17a
    @Test
    fun uncannyHeadstoneDrawsThenDigs() = scenario {
        alice.battlefield("Swamp", "Swamp", "Swamp", "Swamp")
        alice.hand(uncannyHeadstone)
        alice.library(bear, Spm.shock, giant, Spm.study, "Forest")
        alice.casts(uncannyHeadstone, paying = listOf("Swamp", "Swamp#1"))
        playOut()
        expect { alice.hand has bear }
        alice.activates(uncannyHeadstone)
        playOut(giant)
        expect {
            alice.hand has giant
            alice.graveyard has Spm.shock
            alice.graveyard has "Forest"
        }
    }

    /** A Food as it enters, and mana of any colour. */
    // CR 111.10b
    @Test
    fun streetVendorWagonFeeds() = scenario {
        alice.battlefield("Plains", "Plains", "Plains")
        alice.hand(streetVendorWagon, Spm.shock)
        alice.casts(streetVendorWagon)
        playOut()
        expect { alice.battlefield has "Food Token" }
        alice.casts(Spm.shock, target = "bob", paying = listOf(streetVendorWagon + ":R"))
        playOut()
        expect { bob.life shouldBe 18 }
    }

    /** A Citizen token as it enters. */
    // CR 111.1
    @Test
    fun trafficReportChopperBringsACitizen() = scenario {
        alice.battlefield("Plains", "Plains", "Plains")
        alice.hand(trafficReportChopper)
        alice.casts(trafficReportChopper)
        playOut()
        expect {
            alice.battlefield has "Human Citizen Token"
            trafficReportChopper hasKeyword Keyword.Flying
        }
    }

    /** Crewed and attacking, {U} makes another attacker unblockable. */
    // CR 702.122a, 603.12
    @Test
    fun commuterBargeSneaksAnAttackerThrough() = scenario {
        alice.battlefield(commuterBarge, ogre, bear, "Island")
        bob.battlefield(giant)
        advanceTo(TurnStep.BeginningOfCombat)
        alice.activates(commuterBarge, choosing = listOf(ogre))
        playOut()
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(commuterBarge, bear)
        playOut(bear)
        advanceTo(TurnStep.DeclareBlockers)
        assertFailsWith<Throwable> { bob.blocks(giant to bear) }
    }

    /** A basic land found and put on top. */
    // CR 701.23a
    @Test
    fun crawlerDroneSetsUpALand() = scenario {
        alice.battlefield("Plains", "Plains")
        alice.hand(crawlerDrone)
        alice.library(ogre, giant, "Forest")
        alice.casts(crawlerDrone)
        playOut("Forest")
        expect { state.player(alice.id).library.first().let { state.obj(it)!!.definition!!.faces[0].name } shouldBe "Forest" }
    }

    /** Crewed, attacking: +1/+1 for each Spider. */
    // CR 702.122a
    @Test
    fun webwheelRoadsterGrowsWithSpiders() = scenario {
        alice.battlefield(webwheelRoadster, spider, Spm.hero)
        advanceTo(TurnStep.BeginningOfCombat)
        alice.activates(webwheelRoadster, choosing = listOf(Spm.hero))
        playOut()
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(webwheelRoadster)
        playOut()
        expect {
            webwheelRoadster.power shouldBe 5
            webwheelRoadster.types.contains(CardType.Creature) shouldBe true
        }
    }

    /** 5 damage as it enters; from the hand, discarded to destroy an artifact. */
    // CR 702.29a
    @Test
    fun ironDemolitionBallSmashes() = scenario {
        alice.battlefield(*Array(5) { "Mountain" })
        alice.hand(ironDemolitionBall)
        bob.battlefield(giant)
        alice.casts(ironDemolitionBall)
        playOut(giant)
        expect { bob.graveyard has giant }
    }

    @Test
    fun ironDemolitionBallDiscardedDestroysAnArtifact() = scenario {
        alice.battlefield("Mountain", "Mountain")
        alice.hand(ironDemolitionBall)
        bob.battlefield(Spm.idol)
        alice.activates(ironDemolitionBall, target = Spm.idol)
        playOut()
        expect {
            bob.graveyard has Spm.idol
            alice.graveyard has ironDemolitionBall
        }
    }

    /** {G} paid as it enters: a basic land card into the hand. */
    // CR 701.23a
    @Test
    fun undergroundRailcarFindsALand() = scenario {
        alice.battlefield("Forest", "Forest", "Forest")
        alice.hand(undergroundRailcar)
        alice.library(ogre, "Mountain")
        alice.casts(undergroundRailcar, paying = listOf("Forest", "Forest#1"))
        playOut("Mountain")
        expect { alice.hand has "Mountain" }
    }

    /** Smear Campaign gives a legendary creature menace. */
    // CR 702.111a
    @Test
    fun morningHeraldTowerSmears() = scenario {
        alice.battlefield(morningHeraldTower, "Plains")
        bob.battlefield(specter)
        alice.activates(morningHeraldTower, ability = abilityIndex(morningHeraldTower, "Smear"), target = specter)
        playOut()
        expect { specter hasKeyword Keyword.Menace }
    }

    /** Enters tapped; {4}, {T}: surveil 1. */
    // CR 701.25a
    @Test
    fun forebodingSanitariumSurveils() = scenario {
        alice.battlefield(forebodingSanitarium, *Array(4) { "Swamp" })
        alice.library(ogre)
        alice.activates(forebodingSanitarium, ability = abilityIndex(forebodingSanitarium, "Surveil"))
        playOut(ogre)
        expect { alice.graveyard has ogre }
    }

    /** Sacrificed for a basic land, tapped. */
    // CR 701.23a
    @Test
    fun bustlingSkylineFetches() = scenario {
        alice.battlefield(bustlingSkyline)
        alice.library("Island")
        alice.activates(bustlingSkyline)
        playOut("Island")
        expect {
            card("Island").zone shouldBe Zone.Battlefield
            card("Island").tapped shouldBe true
            alice.graveyard has bustlingSkyline
        }
    }
}
