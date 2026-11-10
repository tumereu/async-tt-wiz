package arcanum.engine.sets.fdn

import arcanum.api.Keyword
import arcanum.api.TurnStep
import arcanum.engine.cards.Cards
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.scenario.Scenario
import arcanum.engine.scenario.scenario
import kotlin.test.Test
import kotlin.test.assertFailsWith

/**
 * Chunk 23 played: the invented names carry the oracle text of Cephalid Inkmage, Rune-Sealed Wall, Uncharted Voyage, Vanguard Seraph,
 * Crypt Feaster, Dreadwing Scavenger, Kiora, the Rising Tide, An Offer You Can't Refuse, Brass's Bounty, Corsair Captain, Fake Your Own
 * Death, Involuntary Employment, Rapacious Dragon, Seize the Spoils, Cryptic Caves, Cultivator's Caravan, Hedron Archive,
 * Rogue's Passage and Savage Ventmaw.
 */
class FdnChunk23SurveilThresholdTreasureScenariosTest {
    private val seer = Cards.invent("Cephalid Seer", "{2}{U}", "Creature — Octopus Wizard", """
        When this creature enters, surveil 3. (Look at the top three cards of your library, then put any number of them into your graveyard and the rest on top of your library in any order.)
        Threshold — This creature can't be blocked as long as there are seven or more cards in your graveyard.
    """, "2/2")
    private val wall = Cards.invent("Rune-Bound Wall", "{2}{U}", "Artifact Creature — Wall", """
        Defender
        {T}: Surveil 1. (Look at the top card of your library. You may put it into your graveyard.)
    """, "0/6")
    private val voyage = Cards.invent("Unmapped Voyage", "{3}{U}", "Instant", """
        Target creature's owner puts it on their choice of the top or bottom of their library.
        Surveil 1. (Look at the top card of your library. You may put it into your graveyard.)
    """)
    private val vanguard = Cards.invent("Vanguard Seer", "{3}{W}", "Creature — Angel Warrior", """
        Flying
        Whenever you gain life for the first time each turn, surveil 1. (Look at the top card of your library. You may put it into your graveyard.)
    """, "3/3")
    private val eater = Cards.invent("Crypt Eater", "{3}{B}", "Creature — Zombie", """
        Menace (This creature can't be blocked except by two or more creatures.)
        Threshold — Whenever this creature attacks, if there are seven or more cards in your graveyard, this creature gets +2/+0 until end of turn.
    """, "3/4")
    private val forager = Cards.invent("Dreadwing Forager", "{1}{U}{B}", "Creature — Nightmare Bird", """
        Flying
        Whenever this creature enters or attacks, draw a card, then discard a card.
        Threshold — This creature gets +1/+1 and has deathtouch as long as there are seven or more cards in your graveyard.
    """, "2/2")
    private val ciora = Cards.invent("Ciora, the Rising Tide", "{2}{U}", "Legendary Creature — Merfolk Noble", """
        When Ciora enters, draw two cards, then discard two cards.
        Threshold — Whenever Ciora attacks, if there are seven or more cards in your graveyard, you may create Scion of the Deep, a legendary 8/8 blue Octopus creature token.
    """, "3/2")
    private val offer = Cards.invent("An Offer You Must Refuse", "{U}", "Instant", """
        Counter target noncreature spell. Its controller creates two Treasure tokens. (They're artifacts with "{T}, Sacrifice this token: Add one mana of any color.")
    """)
    private val bounty = Cards.invent("Brasses' Bounty", "{6}{R}", "Sorcery", """
        For each land you control, create a Treasure token. (It's an artifact with "{T}, Sacrifice this token: Add one mana of any color.")
    """)
    private val lieutenant = Cards.invent("Corsair Lieutenant", "{2}{U}", "Creature — Human Pirate", """
        When this creature enters, create a Treasure token. (It's an artifact with "{T}, Sacrifice this token: Add one mana of any color.")
        Other Pirates you control get +1/+1.
    """, "2/2")
    private val birth = Cards.invent("Fake Your Own Birth", "{1}{B}", "Instant", """
        Until end of turn, target creature gets +2/+0 and gains "When this creature dies, return it to the battlefield tapped under its owner's control and you create a Treasure token." (It's an artifact with "{T}, Sacrifice this token: Add one mana of any color.")
    """)
    private val servitude = Cards.invent("Involuntary Servitude", "{3}{R}", "Sorcery", """
        Gain control of target creature until end of turn. Untap that creature. It gains haste until end of turn. Create a Treasure token. (It's an artifact with "{T}, Sacrifice this token: Add one mana of any color.")
    """)
    private val wyrm = Cards.invent("Rapacious Wyrm", "{4}{R}", "Creature — Dragon", """
        Flying
        When this creature enters, create two Treasure tokens. (They're artifacts with "{T}, Sacrifice this token: Add one mana of any color.")
    """, "3/3")
    private val loot = Cards.invent("Seize the Loot", "{2}{R}", "Sorcery", """
        As an additional cost to cast this spell, discard a card.
        Draw two cards and create a Treasure token. (It's an artifact with "{T}, Sacrifice this token: Add one mana of any color.")
    """)
    private val grotto = Cards.invent("Cryptic Grotto", null, "Land", """
        {T}: Add {C}.
        {1}, {T}, Sacrifice this land: Draw a card. Activate only if you control five or more lands.
    """)
    private val wagon = Cards.invent("Cultivator's Wagon", "{3}", "Artifact — Vehicle", """
        {T}: Add one mana of any color.
        Crew 3 (Tap any number of creatures you control with total power 3 or more: This Vehicle becomes an artifact creature until end of turn.)
    """, "5/5")
    private val library = Cards.invent("Hedron Library", "{4}", "Artifact", """
        {T}: Add {C}{C}.
        {2}, {T}, Sacrifice this artifact: Draw two cards.
    """)
    private val trail = Cards.invent("Rogue's Trail", null, "Land", """
        {T}: Add {C}.
        {4}, {T}: Target creature can't be blocked this turn.
    """)
    private val ventwing = Cards.invent("Savage Ventwing", "{4}{R}{G}", "Creature — Dragon", """
        Flying
        Whenever this creature attacks, add {R}{R}{R}{G}{G}{G}. Until end of turn, you don't lose this mana as steps and phases end.
    """, "4/4")

    private val bear = Cards.invent("Mossback Bear", "{1}{G}", "Creature — Bear", "", "2/2")
    private val blocker = Cards.invent("Stonewall Guard", "{1}{W}", "Creature — Human Soldier", "", "1/4")
    private val pirate = Cards.invent("Dockside Pirate", "{1}{U}", "Creature — Human Pirate", "", "2/1")
    private val bolt = Cards.invent("Sunder Bolt", "{R}", "Instant", "Sunder Bolt deals 3 damage to target creature.")
    private val aid = Cards.invent("Soothing Balm", "{W}", "Instant", "You gain 3 life.")

    private fun Scenario.resolveAll() {
        var guard = 40
        while (state.stack.isNotEmpty() && pending == null && guard-- > 0) allPass()
    }

    private fun Scenario.stock() {
        alice.library("Island", "Forest", "Plains", "Swamp", "Mountain", "Island", "Forest", "Plains")
        bob.library("Island", "Forest", "Plains", "Swamp", "Mountain", "Island", "Forest", "Plains")
    }

    private fun Scenario.treasureCount(owner: arcanum.engine.scenario.ScenarioPlayer): Int =
        owner.battlefield.names.count { it == "Treasure Token" }

    private val sevenCards = arrayOf("Island", "Forest", "Plains", "Swamp", "Mountain", "Island", "Forest")

    // CR 701.25a, 602.2: the tapped wall looks at one card and may put it into the graveyard
    @Test
    fun wallSurveilsOneAndBinsIt() = scenario {
        alice.battlefield(wall)
        alice.library("Forest", "Island", "Plains")
        alice.activates(wall)
        resolveAll()
        alice.chooses("Forest")
        expect {
            alice.graveyard has "Forest"
            wall.tapped shouldBe true
            alice.library.size shouldBe 2
        }
    }

    // CR 701.25a: choosing none keeps the card on top
    @Test
    fun wallSurveilsOneAndKeepsIt() = scenario {
        alice.battlefield(wall)
        alice.library("Forest", "Island", "Plains")
        alice.activates(wall)
        resolveAll()
        alice.chooses()
        expect {
            alice.graveyard.size shouldBe 0
            alice.library.size shouldBe 3
        }
    }

    // CR 603.6a, 701.25a: surveil 3 on entering puts any number into the graveyard
    @Test
    fun seerSurveilsThreeOnEntering() = scenario {
        alice.battlefield("Island", "Island", "Island")
        alice.hand(seer)
        alice.library("Forest", "Island", "Plains", "Swamp")
        alice.casts(seer)
        resolveAll()
        alice.chooses("Forest", "Island")
        expect { alice.graveyard.size shouldBe 2 }
    }

    // CR 509.1b, 604.1: with seven cards in the graveyard nothing may block it; with six a creature may
    @Test
    fun seerCantBeBlockedAtSevenCards() = scenario {
        alice.battlefield(seer)
        alice.graveyard(*sevenCards)
        bob.battlefield(blocker)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(seer)
        advanceTo(TurnStep.DeclareBlockers)
        assertFailsWith<Throwable> { bob.blocks(blocker to seer) }
    }

    @Test
    fun seerCanBeBlockedAtSixCards() = scenario {
        alice.battlefield(seer)
        alice.graveyard(*sevenCards.take(6).toTypedArray())
        bob.battlefield(blocker)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(seer)
        advanceTo(TurnStep.DeclareBlockers)
        bob.blocks(blocker to seer)
        advanceTo(TurnStep.EndOfCombat)
        expect { bob.life shouldBe 20 }
    }

    // CR 400.3, 701.25a: the owner picks the top, then surveils 1
    @Test
    fun voyagePutsTheCreatureOnTopThenSurveils() = scenario {
        alice.battlefield("Island", "Island", "Island", "Island")
        alice.hand(voyage)
        bob.battlefield(bear)
        alice.library("Forest", "Island")
        alice.casts(voyage, target = bear)
        resolveAll()
        bob.answers(Answer.Option("top"))
        alice.chooses("Forest")
        expect {
            bear.zone shouldBe arcanum.api.Zone.Library
            alice.graveyard has "Forest"
        }
    }

    // CR 603.2: only the first life gain each turn triggers
    @Test
    fun vanguardSurveilsOnlyOnTheFirstGain() = scenario {
        alice.battlefield(vanguard, "Plains", "Plains")
        alice.hand(aid, aid)
        alice.library("Forest", "Island", "Plains")
        alice.casts(aid)
        resolveAll()
        alice.chooses("Forest")
        alice.casts(aid)
        resolveAll()
        expect {
            alice.life shouldBe 26
            alice.graveyard.names.count { it == "Forest" } shouldBe 1
            alice.library.size shouldBe 2
        }
    }

    // CR 207.2c, 603.4: seven cards in the graveyard as it attacks
    @Test
    fun eaterGrowsAtSevenCards() = scenario {
        alice.battlefield(eater)
        alice.graveyard(*sevenCards)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(eater)
        resolveAll()
        expect { eater.power shouldBe 5 }
    }

    @Test
    fun eaterStaysSmallBelowSeven() = scenario {
        alice.battlefield(eater)
        alice.graveyard(*sevenCards.take(6).toTypedArray())
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(eater)
        resolveAll()
        expect { eater.power shouldBe 3 }
    }

    // CR 604.1, 613.4c: the bonus and the keyword both depend on the count, so they follow it as cards come and go
    @Test
    fun foragerGetsBonusAtSevenCards() = scenario {
        alice.battlefield(forager)
        alice.graveyard(*sevenCards)
        expect {
            forager.power shouldBe 3
            forager.toughness shouldBe 3
            forager hasKeyword Keyword.Deathtouch.kind
        }
    }

    @Test
    fun foragerIsPlainBelowSeven() = scenario {
        alice.battlefield(forager)
        alice.graveyard(*sevenCards.take(6).toTypedArray())
        expect {
            forager.power shouldBe 2
            forager lacksKeyword Keyword.Deathtouch.kind
        }
    }

    // CR 603.5, 111.4: the legendary 8/8 token comes only if the player says yes
    @Test
    fun cioraMakesTheScionAtSevenCards() = scenario {
        alice.battlefield(ciora)
        alice.graveyard(*sevenCards)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(ciora)
        resolveAll()
        alice.answers(Answer.YesNo(true))
        expect {
            alice.battlefield has "Scion of the Deep"
            "Scion of the Deep".power shouldBe 8
        }
    }

    @Test
    fun cioraMakesNoScionBelowSeven() = scenario {
        alice.battlefield(ciora)
        alice.graveyard(*sevenCards.take(6).toTypedArray())
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(ciora)
        resolveAll()
        expect { alice.battlefield lacks "Scion of the Deep" }
    }

    // CR 701.6a, 111.10b: the countered spell's controller gets the Treasures, not the counterer
    @Test
    fun offerGivesTheSpellsControllerTwoTreasures() = scenario {
        alice.battlefield("Plains")
        alice.hand(aid)
        bob.battlefield("Island")
        bob.hand(offer)
        alice.casts(aid)
        alice.passes()
        bob.casts(offer, target = aid)
        resolveAll()
        expect {
            alice.life shouldBe 20
            treasureCount(alice) shouldBe 2
            treasureCount(bob) shouldBe 0
        }
    }

    // CR 107.3: one Treasure per land, counted as it resolves
    @Test
    fun bountyMakesATreasurePerLand() = scenario {
        alice.battlefield("Mountain", "Mountain", "Mountain", "Mountain", "Mountain", "Mountain", "Mountain")
        alice.hand(bounty)
        alice.casts(bounty)
        resolveAll()
        expect { treasureCount(alice) shouldBe 7 }
    }

    // CR 613.4c: Other Pirates only
    @Test
    fun lieutenantMakesATreasureAndPumpsOtherPirates() = scenario {
        alice.battlefield("Island", "Island", "Island", pirate, bear)
        alice.hand(lieutenant)
        alice.casts(lieutenant)
        resolveAll()
        expect {
            treasureCount(alice) shouldBe 1
            pirate.power shouldBe 3
            lieutenant.power shouldBe 2
            bear.power shouldBe 2
        }
    }

    // CR 700.4, 400.7, 111.10b: the granted trigger returns the dead creature tapped and makes the Treasure
    @Test
    fun birthReturnsTheCreatureAndMakesATreasure() = scenario {
        alice.battlefield("Swamp", "Swamp", bear)
        alice.hand(birth)
        bob.battlefield("Mountain")
        bob.hand(bolt)
        alice.casts(birth, target = bear)
        resolveAll()
        alice.passes()
        bob.casts(bolt, target = bear)
        resolveAll()
        expect {
            bear.zone shouldBe arcanum.api.Zone.Battlefield
            bear.tapped shouldBe true
            treasureCount(alice) shouldBe 1
        }
    }

    // CR 701.12, 302.6, 702.10, 111.10b
    @Test
    fun servitudeTakesTheCreatureAndMakesATreasure() = scenario {
        alice.battlefield("Mountain", "Mountain", "Mountain", "Mountain")
        alice.hand(servitude)
        bob.battlefield(bear)
        alice.casts(servitude, target = bear)
        resolveAll()
        expect {
            bear.controller shouldBe alice.id
            bear.tapped shouldBe false
            bear hasKeyword Keyword.Haste.kind
            treasureCount(alice) shouldBe 1
        }
    }

    // CR 111.10b: two tokens
    @Test
    fun wyrmMakesTwoTreasures() = scenario {
        alice.battlefield("Mountain", "Mountain", "Mountain", "Mountain", "Mountain")
        alice.hand(wyrm)
        alice.casts(wyrm)
        resolveAll()
        expect { treasureCount(alice) shouldBe 2 }
    }

    // CR 118.8, 601.2b: the discard is paid in casting, before the draw
    @Test
    fun lootDiscardsAsACostThenDrawsTwo() = scenario {
        stock()
        alice.battlefield("Mountain", "Mountain", "Mountain")
        alice.hand(loot, "Swamp")
        alice.casts(loot)
        resolveAll()
        expect {
            alice.graveyard has "Swamp"
            alice.hand.size shouldBe 2
            treasureCount(alice) shouldBe 1
        }
    }

    // CR 602.5b: only with five lands
    @Test
    fun grottoNeedsFiveLands() = scenario {
        stock()
        alice.battlefield(grotto, "Island", "Island", "Island")
        assertFailsWith<Throwable> { alice.activates(grotto, ability = 1) }
    }

    @Test
    fun grottoSacrificesToDrawWithFiveLands() = scenario {
        stock()
        alice.battlefield(grotto, "Island", "Island", "Island", "Island")
        alice.activates(grotto, ability = 1)
        resolveAll()
        expect {
            alice.hand.size shouldBe 1
            grotto.zone shouldBe arcanum.api.Zone.Graveyard
        }
    }

    // CR 106.1b: the tapped Vehicle makes one mana of a colour the player picks
    @Test
    fun wagonMakesOneManaOfAnyColor() = scenario {
        alice.battlefield(wagon)
        alice.activates(wagon, color = arcanum.api.Color.Green)
        expect {
            alice.pool.size shouldBe 1
            wagon.tapped shouldBe true
        }
    }

    // CR 106.1a, 602.2: two colourless mana; the second ability sacrifices it
    @Test
    fun libraryMakesTwoColorlessMana() = scenario {
        alice.battlefield(library)
        alice.activates(library)
        expect { alice.pool.size shouldBe 2 }
    }

    @Test
    fun librarySacrificesToDrawTwo() = scenario {
        stock()
        alice.battlefield(library, "Island", "Island")
        alice.activates(library, ability = 1)
        resolveAll()
        expect {
            alice.hand.size shouldBe 2
            library.zone shouldBe arcanum.api.Zone.Graveyard
        }
    }

    // CR 506.4, 509.1b: the land's two abilities share its tapped state
    @Test
    fun trailTapsForTheAbilityAndNotForMana() = scenario {
        alice.battlefield(trail, "Island", "Island", "Island", "Island", bear)
        bob.battlefield(blocker)
        alice.activates(trail, ability = 1, target = bear)
        resolveAll()
        expect { trail.tapped shouldBe true }
        assertFailsWith<Throwable> { alice.activates(trail, ability = 0) }
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(bear)
        advanceTo(TurnStep.DeclareBlockers)
        assertFailsWith<Throwable> { bob.blocks(blocker to bear) }
    }

    // CR 106.4, 106.4c: the six mana stays in the pool through the combat steps
    @Test
    fun ventwingKeepsItsManaThroughTheTurn() = scenario {
        alice.battlefield(ventwing)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(ventwing)
        resolveAll()
        expect { alice.pool.size shouldBe 6 }
        advanceTo(TurnStep.PostcombatMain)
        expect { alice.pool.size shouldBe 6 }
    }
}
