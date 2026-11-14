package arcanum.engine.sets.otj

import arcanum.api.CardType
import arcanum.api.CounterKind
import arcanum.api.Keyword
import arcanum.api.TurnStep
import arcanum.api.Zone
import arcanum.engine.cards.Cards
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.scenario.scenario
import arcanum.engine.sets.blb.resolveAll
import arcanum.engine.sets.dft.hits
import arcanum.engine.sets.ltr.playOut
import arcanum.engine.sets.ltr.tokens
import kotlin.test.Test
import kotlin.test.assertFailsWith

/** OTJ chunk 08: crew once each turn, doubling counters, Equipment that copies and casts, milling and picking from the milled cards. */
class OtjChunk08CrewEquipMillScenariosTest {
    private val parlorEngine = Cards.invent("Gilded Parlor Engine", "{5}", "Artifact — Vehicle", """
        Whenever this Vehicle attacks, create a Treasure token for each creature that crewed it this turn. (They're artifacts with "{T}, Sacrifice this token: Add one mana of any color.")
        Crew 1. Activate only once each turn. (Tap any number of creatures you control with total power 1 or more: This Vehicle becomes an artifact creature until end of turn.)
    """, "6/5")
    private val tilly = Cards.invent("Thornback Tilly, Burr Planter", "{1}{G}", "Legendary Creature — Plant Druid", """
        Landfall — Whenever a land you control enters, put a +1/+1 counter on target creature.
        {3}{G}{G}: Double the number of +1/+1 counters on each creature you control.
    """, "2/2")
    private val bulwark = Cards.invent("Mimicry Bulwark", "{1}{W}{U}", "Artifact — Equipment", """
        When this Equipment enters, exile up to one target creature until this Equipment leaves the battlefield.
        Whenever this Equipment becomes attached to a creature, for as long as this Equipment remains attached to it, that creature becomes a copy of a creature card exiled with this Equipment.
        Equip {2}
    """)
    private val key = Cards.invent("The Strongbox Skeleton Key", "{1}{U}", "Legendary Artifact — Equipment", """
        Whenever equipped creature deals combat damage to a player, look at that many cards from the top of your library. You may exile a nonland card from among them. Put the rest on the bottom of your library in a random order. You may cast the exiled card without paying its mana cost.
        Equip {2}{U}
    """)
    private val herbalist = Cards.invent("Unhurried Herbalist", "{2}{G}", "Creature — Human Scout",
        "When this creature enters, mill three cards. Put a land card from among the milled cards into your hand. If you can't, create a Treasure token.", "2/3")
    private val gambit = Cards.invent("Bootlegger's Gambit", "{G}", "Instant", """
        Spree (Choose one or more additional costs.)
        + {2} — Mill four cards. You may put up to two creature and/or land cards from among the milled cards into your hand.
        + {4}{G} — You may put up to two creature cards from your hand onto the battlefield.
        + {1} — Creatures you control with power 4 or greater gain hexproof and indestructible until end of turn.
    """)
    private val littlebones = Cards.invent("Littlebones Signs On", "{B}", "Legendary Enchantment", """
        When Littlebones Signs On enters, any number of target players each discard a card.
        Whenever a legendary creature you control enters, any number of target players each mill a card and lose 1 life.
    """)
    private val brigand = Cards.invent("Silt-Bog Brigand", "{2}{U}", "Creature — Homarid Mercenary",
        "Whenever you commit a crime, each opponent mills three cards. This ability triggers only once each turn.", "2/4")
    private val tunnelbrute = Cards.invent("Mulish Tunnelbrute", "{1}{G}", "Creature — Badger Beast Mount", """
        Whenever this creature becomes saddled for the first time each turn, mill two cards, then this creature gets +X/+X until end of turn, where X is the number of creature cards in your graveyard.
        Saddle 2
    """, "2/2")
    private val pianist = Cards.invent("Saloon Pianist", "{W}", "Creature — Human Bard", "", "1/1")
    private val steer = Cards.invent("Ironhide Steer", "{1}{G}", "Creature — Ox", "", "2/2")
    private val shambler = Cards.invent("Mesa Shambler", "{4}{G}", "Creature — Plant Horror", "", "3/3")
    private val behemoth = Cards.invent("Canyon Behemoth", "{5}{G}", "Creature — Beast", "", "5/5")
    private val marshal = Cards.invent("Frontier Marshal", "{2}{W}", "Legendary Creature — Human Soldier", "", "2/2")
    private val jab = Cards.invent("Bounty Poster Jab", "{R}", "Instant", "Bounty Poster Jab deals 1 damage to target player.")
    private val p1p1 = CounterKind.PowerToughness(1, 1)
    private infix fun <T> T.shouldBe(expected: T) = kotlin.test.assertEquals(expected, this)

    // CR 602.5b: "Activate only once each turn" after crew limits the crew ability; CR 702.122c: one creature crewed it: one Treasure
    @Test
    fun crewsOnlyOnceEachTurnAndMakesATreasurePerCrewer() = scenario {
        alice.battlefield(parlorEngine, pianist, steer)
        alice.activates(parlorEngine, choosing = listOf(pianist))
        resolveAll()
        assertFailsWith<IllegalArgumentException> { alice.activates(parlorEngine, choosing = listOf(steer)) }
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(parlorEngine)
        resolveAll()
        tokens("Treasure Token").size shouldBe 1
        expect { steer.tapped shouldBe false }
    }

    // CR 701.10e: each creature you control gets as many +1/+1 counters as it has; an opponent's creature doesn't
    @Test
    fun doublesCountersOnlyOnCreaturesYouControl() = scenario {
        alice.battlefield(tilly, "Forest", "Forest", "Forest", "Forest", "Forest")
        alice.battlefield(steer, counters = listOf(p1p1 to 2))
        bob.battlefield(behemoth, counters = listOf(p1p1 to 3))
        alice.activates(tilly, ability = 0)
        resolveAll()
        expect {
            steer.counters(p1p1) shouldBe 4
            tilly.counters(p1p1) shouldBe 0
            behemoth.counters(p1p1) shouldBe 3
        }
    }

    // CR 607.2a, 707.2: attached, the creature is a copy of the card exiled with the Equipment; CR 611.2b: moved to another creature,
    // the first stops being a copy and the second becomes one
    @Test
    fun equippedCreatureIsACopyOfTheExiledCardWhileAttached() = scenario {
        alice.battlefield(pianist, steer, "Plains", "Island", "Plains", "Plains", "Plains", "Plains", "Plains")
        alice.hand(bulwark)
        bob.battlefield(behemoth)
        alice.casts(bulwark)
        resolveAll()
        alice.answers(Answer.Targets(listOf(target(behemoth))))
        resolveAll()
        expect { behemoth.zone shouldBe Zone.Exile }
        alice.activates(bulwark, target = pianist)
        resolveAll()
        expect { pianist.power shouldBe 5; pianist.toughness shouldBe 5 }
        alice.activates(bulwark, target = steer)
        resolveAll()
        expect {
            pianist.power shouldBe 1
            steer.power shouldBe 5
            steer.subtypes.map { it.name } shouldBe listOf("Beast")
        }
    }

    // CR 510.2, 120.3: two combat damage: look at two; CR 608.2g, 118.9: the exiled creature card is cast without paying its mana cost
    // and resolves; the land goes to the bottom (CR 401.4)
    @Test
    fun combatDamageLooksAtThatManyAndCastsTheExiledCardFree() = scenario {
        alice.battlefield(key, steer)
        attach(key, steer)
        alice.library(shambler, "Forest", "Island", "Island")
        bob.library("Island", "Island", "Island")
        hits(steer)
        alice.chooses(shambler)
        if (pending is Decision.May) alice.answers(Answer.YesNo(true))
        resolveAll()
        expect {
            bob.life shouldBe 18
            shambler.zone shouldBe Zone.Battlefield
            alice.library.names.last() shouldBe "Forest"
            alice.library.size shouldBe 3
        }
    }

    // CR 701.17a: the milled land goes to hand; no Treasure
    @Test
    fun milledLandGoesToHand() = scenario {
        alice.hand(herbalist)
        alice.library(steer, "Forest", pianist, "Island")
        alice.castsWithoutCosts(herbalist)
        resolveAll()
        expect {
            alice.hand has "Forest"
            alice.graveyard.size shouldBe 2
        }
        tokens("Treasure Token").size shouldBe 0
    }

    // CR 609.3: no land among the milled cards: it can't, so a Treasure
    @Test
    fun noMilledLandMakesATreasure() = scenario {
        alice.hand(herbalist)
        alice.library(steer, pianist, shambler, "Island")
        alice.castsWithoutCosts(herbalist)
        resolveAll()
        expect { alice.graveyard.size shouldBe 3 }
        tokens("Treasure Token").size shouldBe 1
    }

    // CR 702.172a, 700.2: the chosen modes in printed order: two milled cards to hand, a 5/5 put from hand, then it gains hexproof and
    // indestructible; a 2/2 doesn't
    @Test
    fun spreeMillsPutsCreaturesAndProtectsTheBigOnes() = scenario {
        alice.battlefield(steer)
        alice.hand(gambit, behemoth)
        alice.library(shambler, "Forest", pianist, "Island", "Island")
        alice.castsWithoutCosts(gambit, modes = listOf(0, 1, 2))
        resolveAll()
        alice.chooses(shambler, "Forest")
        resolveAll()
        alice.chooses(behemoth)
        resolveAll()
        expect {
            alice.hand has shambler
            alice.hand has "Forest"
            alice.graveyard has pianist
            behemoth.zone shouldBe Zone.Battlefield
            behemoth hasKeyword Keyword.Hexproof
            behemoth hasKeyword Keyword.Indestructible
            steer lacksKeyword Keyword.Hexproof
        }
    }

    // CR 115.1, 601.2c: any number of target players: Bob and Carol each mill a card and lose 1 life, Alice doesn't
    @Test
    fun legendaryEntersTargetedPlayersMillAndLoseLife() = scenario(players = 3) {
        alice.battlefield(littlebones)
        alice.hand(marshal)
        alice.library("Plains", "Plains")
        bob.library("Island", "Island")
        carol.library("Swamp", "Swamp")
        alice.castsWithoutCosts(marshal)
        playOut("bob", "carol")
        expect {
            bob.graveyard.size shouldBe 1
            carol.graveyard.size shouldBe 1
            alice.graveyard.size shouldBe 0
            bob.life shouldBe 19
            carol.life shouldBe 19
            alice.life shouldBe 20
        }
    }

    // CR 700.13: targeting an opponent is a crime: each opponent mills three; CR 603.2h: only once each turn
    @Test
    fun crimeMillsEachOpponentOnceEachTurn() = scenario(players = 3) {
        alice.battlefield(brigand)
        alice.hand(jab, jab)
        bob.library("Island", "Island", "Island", "Island", "Island", "Island")
        carol.library("Swamp", "Swamp", "Swamp", "Swamp", "Swamp", "Swamp")
        alice.castsWithoutCosts(jab, target = "bob")
        resolveAll()
        alice.castsWithoutCosts(jab, target = "carol")
        resolveAll()
        expect {
            bob.graveyard.size shouldBe 3
            carol.graveyard.size shouldBe 3
            bob.life shouldBe 19
            carol.life shouldBe 19
        }
    }

    // CR 702.171b, 603.2: saddled for the first time this turn: mill two, then +X/+X for the creature cards in the graveyard (CR 608.2h)
    @Test
    fun saddledMillsAndGrowsByCreatureCards() = scenario {
        alice.battlefield(tunnelbrute, steer)
        alice.graveyard(pianist)
        alice.library(shambler, "Forest", "Island")
        alice.activates(tunnelbrute, choosing = listOf(steer))
        resolveAll()
        expect {
            alice.graveyard.size shouldBe 3
            tunnelbrute.power shouldBe 4
            tunnelbrute.toughness shouldBe 4
        }
    }
}
