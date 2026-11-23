package arcanum.engine.sets.snc

import arcanum.api.CardDefinition
import arcanum.api.Color
import arcanum.api.CounterKind
import arcanum.api.Layout
import arcanum.api.Mechanic
import arcanum.api.PredefinedTokens
import arcanum.api.TurnStep
import arcanum.api.Zone
import arcanum.engine.Engine
import arcanum.engine.cards.Cards
import arcanum.engine.decision.Decision
import arcanum.engine.input.Input
import arcanum.engine.scenario.scenario
import arcanum.engine.sets.blb.targetsIfAsked
import arcanum.engine.sets.dft.choose
import arcanum.engine.sets.fin.resolveAll
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** Streets of New Capenna chunk 07 in play: what was spent to activate an ability, cost reductions by life, by a graveyard and by an
 * opponent's draws, flash while a condition holds, a rest that happens only if a card was exiled, one card played from two, three
 * additional costs and three effects for each opponent, casting from the top by removing a counter, and three-colour casts and mana. */
class SncChunk07ConditionsAndCostsScenariosTest {
    private val exemplar = Cards.invent("Snc07 Novelty Exemplar", "{4}", "Artifact Creature — Angel Warrior", """
        Flying
        {3}: This creature gets +1/+1 until end of turn. If exactly three colors of mana were spent to activate this ability, put a +1/+1 counter on it instead.
    """, "2/2")
    private val bruiser = Cards.invent("Snc07 Backroom Bruiser", "{R}{G}", "Creature — Cat Warrior",
        "{R}{G}: This creature gets +1/+1 until end of turn. If mana from a Treasure was spent to activate this ability, put a +1/+1 counter on this creature instead.", "2/2")
    private val treasure = Cards.define("Snc07 Treasure Proxy", CardDefinition(Layout.Normal, listOf(PredefinedTokens.face("Treasure")!!.copy(name = "Snc07 Treasure Proxy"))))
    private val specter = Cards.invent("Snc07 Frailty Specter", "{13}{B}{B}", "Creature — Avatar",
        "If your life total is less than your starting life total, this spell costs {X} less to cast, where X is the difference.", "7/7")
    private val overtime = Cards.invent("Snc07 Graveside Overtime", "{4}{B}", "Sorcery", """
        This spell has flash as long as there are five or more mana values among cards in your graveyard.
        Return target creature card from your graveyard to the battlefield.
    """)
    private val gator = Cards.invent("Snc07 Drainpipe Gator", "{5}{U}", "Creature — Crocodile",
        "{3}{U}: This creature can't be blocked this turn. This ability costs {3} less to activate if there are five or more mana values among cards in your graveyard.", "4/6")
    private val assessor = Cards.invent("Snc07 Cadaver Assessor", "{U}{B}{R}", "Creature — Vampire Rogue",
        "When this creature enters, exile up to one target creature card from a graveyard. If a card is put into exile this way, look at the top three cards of your library, then put one of those cards into your hand and the rest into your graveyard.", "3/3")
    private val bystander = Cards.invent("Snc07 Hapless Bystander", "{R}", "Creature — Human Citizen",
        "When this creature dies, exile the top two cards of your library. Until your next end step, you may play one of those cards.", "1/1")
    private val ravager = Cards.invent("Snc07 Twilight Ravager", "{5}{B}{B}", "Creature — Horror", """
        As an additional cost to cast this spell, sacrifice a creature, discard a card, or pay 4 life.
        When this creature enters, each opponent sacrifices a creature of their choice, discards a card, and loses 4 life.
    """, "5/4")
    private val tab = Cards.invent("Snc07 Settle the Tab", "{X}{U}{U}{U}", "Instant", """
        This spell costs {U}{U}{U} less to cast if an opponent has drawn four or more cards this turn.
        Draw X cards.
    """)
    private val corvo = Cards.invent("Snc07 Corvo Venn, Dealbinder", "{1}{G}{W}{U}", "Legendary Creature — Bird Demon", """
        Flying, trample
        Snc07 Corvo Venn enters with a shield counter on it.
        You may look at the top card of your library any time.
        You may cast spells from the top of your library by removing a counter from a creature you control in addition to paying their other costs.
    """, "3/3")
    private val summit = Cards.invent("Snc07 Summit of the Families", "{3}{W}{U}{B}{R}{G}", "Sorcery",
        "Exile the top ten cards of your library. You may cast spells with exactly three colors from among them this turn. Add {W}{W}{U}{U}{B}{B}{R}{R}{G}{G}. Spend this mana only to cast spells with exactly three colors.")

    private val one = Cards.invent("Snc07 One-Penny Lookout", "{B}", "Creature — Human Rogue", "", "1/1")
    private val two = Cards.invent("Snc07 Two-Bit Fence", "{1}{B}", "Creature — Human Rogue", "", "2/2")
    private val three = Cards.invent("Snc07 Three-Card Dealer", "{2}{B}", "Creature — Human Rogue", "", "3/3")
    private val four = Cards.invent("Snc07 Four-Corner Capo", "{3}{B}", "Creature — Human Rogue", "", "4/4")
    private val spark = Cards.invent("Snc07 Alley Spark", "{R}", "Instant", "Snc07 Alley Spark deals 3 damage to any target.")
    private val flare = Cards.invent("Snc07 Street Flare", "{R}", "Instant", "Snc07 Street Flare deals 3 damage to any target.")
    private val windfall = Cards.invent("Snc07 Deep Pockets", "{U}", "Sorcery", "Target player draws four cards.")
    private val handout = Cards.invent("Snc07 Spread the Wealth", "{U}", "Sorcery", "Each opponent draws two cards.")
    private val pact = Cards.invent("Snc07 Three-Family Pact", "{W}{U}{B}", "Sorcery", "Draw a card.")
    private val accord = Cards.invent("Snc07 Two-Family Accord", "{W}{U}", "Sorcery", "Draw a card.")

    private fun castable(s: arcanum.engine.scenario.Scenario, name: String) =
        Engine.options(s.state, s.alice.id).filterIsInstance<Input.Cast>().any { it.card == s.card(name) }

    // CR 602.2b, 601.2h: generic mana paid with three colours is three colours spent to activate it; the counter replaces the +1/+1
    @Test
    fun exemplarGetsACounterForThreeColors() = scenario {
        alice.battlefield(exemplar, "Plains", "Island", "Swamp")
        alice.activates(exemplar, paying = listOf("Plains", "Island", "Swamp"))
        allPass()
        expect {
            exemplar.counters(CounterKind.PowerToughness(1, 1)) shouldBe 1
            exemplar.power shouldBe 3
        }
    }

    // CR 602.2b: two colours aren't exactly three: it gets +1/+1 until end of turn instead
    @Test
    fun exemplarIsPumpedForTwoColors() = scenario {
        alice.battlefield(exemplar, "Plains", "Plains", "Island")
        alice.activates(exemplar, paying = listOf("Plains", "Plains#1", "Island"))
        allPass()
        expect {
            exemplar.counters(CounterKind.PowerToughness(1, 1)) shouldBe 0
            exemplar.power shouldBe 3
        }
    }

    // CR 106.3, 602.2b: mana from a sacrificed Treasure (its source as it last existed) was spent to activate it
    @Test
    fun bruiserGetsACounterForTreasureMana() = scenario {
        alice.battlefield(bruiser, treasure, "Forest")
        alice.activates(treasure, color = Color.Red)
        alice.activates(bruiser)
        allPass()
        expect {
            bruiser.counters(CounterKind.PowerToughness(1, 1)) shouldBe 1
            bruiser.power shouldBe 3
        }
    }

    // CR 106.3: land mana only: +1/+1 until end of turn
    @Test
    fun bruiserIsPumpedWithoutTreasureMana() = scenario {
        alice.battlefield(bruiser, treasure, "Mountain", "Forest")
        alice.activates(bruiser, paying = listOf("Mountain", "Forest"))
        allPass()
        expect {
            bruiser.counters(CounterKind.PowerToughness(1, 1)) shouldBe 0
            bruiser.power shouldBe 3
        }
    }

    // CR 601.2f, 103.4: at 7 life the spell costs 13 less ({B}{B})
    @Test
    fun specterCostsLessByTheLifeLost() = scenario {
        alice.life = 7
        alice.battlefield("Swamp", "Swamp")
        alice.hand(specter)
        alice.casts(specter)
        allPass()
        expect { alice.battlefield has specter }
    }

    // CR 601.2f: at the starting life total there's no reduction
    @Test
    fun specterCostsFullAtStartingLife() = scenario {
        alice.battlefield("Swamp", "Swamp")
        alice.hand(specter)
        alice.casts(specter)
        assertTrue(decision<Decision.ManaPayment>().suggestion.isEmpty())
    }

    // CR 702.8a, 202.3: five mana values (0 to 4) in the graveyard: cast on an opponent's turn
    @Test
    fun overtimeHasFlashWithFiveManaValues() = scenario {
        alice.graveyard("Swamp", one, two, three, four)
        alice.battlefield("Swamp", "Swamp", "Swamp", "Swamp", "Swamp")
        alice.hand(overtime)
        turn(number = 2, active = bob, step = TurnStep.Upkeep, priority = alice)
        alice.casts(overtime, target = four)
        allPass()
        expect { alice.battlefield has four }
    }

    // CR 307.1: four mana values: only as a sorcery
    @Test
    fun overtimeIsASorceryWithFourManaValues() = scenario {
        alice.graveyard(one, two, three, four)
        alice.battlefield("Swamp", "Swamp", "Swamp", "Swamp", "Swamp")
        alice.hand(overtime)
        turn(number = 2, active = bob, step = TurnStep.Upkeep, priority = alice)
        assertFailsWith<IllegalArgumentException> { alice.casts(overtime, target = four) }
    }

    // CR 601.2f, 602.2b: with five mana values the ability costs {U}
    @Test
    fun gatorAbilityCostsLessWithFiveManaValues() = scenario {
        alice.graveyard("Island", one, two, three, four)
        alice.battlefield(gator, "Island")
        alice.activates(gator)
        allPass()
        expect { gator hasMechanic { it is Mechanic.CantBeBlocked } }
    }

    // CR 601.2f: without them it costs {3}{U}
    @Test
    fun gatorAbilityCostsFullWithoutThem() = scenario {
        alice.battlefield(gator, "Island")
        alice.activates(gator)
        assertTrue(decision<Decision.ManaPayment>().suggestion.isEmpty())
    }

    // CR 608.2c: a card exiled from an opponent's graveyard: look at three, one to hand, the rest to the graveyard
    @Test
    fun assessorLooksAfterExilingACard() = scenario {
        alice.battlefield("Island", "Swamp", "Mountain")
        alice.hand(assessor)
        alice.library("Plains", "Forest", "Island")
        bob.graveyard(two)
        alice.casts(assessor)
        resolveAll()
        targetsIfAsked(two)
        resolveAll()
        choose("Forest")
        expect {
            bob.exile has two
            alice.hand has "Forest"
            alice.graveyard has "Plains"
            alice.library.size shouldBe 0
        }
    }

    // CR 115.1 (up to one target: none), 608.2c: nothing exiled, so no look
    @Test
    fun assessorDoesNothingWithoutACard() = scenario {
        alice.battlefield("Island", "Swamp", "Mountain")
        alice.hand(assessor)
        alice.library("Plains", "Forest", "Island")
        alice.casts(assessor)
        resolveAll()
        expect {
            alice.library.size shouldBe 3
            alice.hand.size shouldBe 0
        }
    }

    // CR 611.2a, 305.1: one of the two cards: after the land is played the other can't be cast
    @Test
    fun bystanderLetsOnlyOneCardBePlayed() = scenario {
        alice.battlefield(bystander, "Mountain", "Mountain")
        alice.hand(spark)
        alice.library("Forest", flare, "Island")
        alice.casts(spark, target = bystander)
        resolveAll()
        expect { alice.exile.size shouldBe 2 }
        assertTrue(castable(this, flare))
        alice.plays("Forest")
        expect { alice.battlefield has "Forest" }
        assertTrue(!castable(this, flare))
    }

    // CR 601.2f (one of three additional costs), 608.2c, 101.4: each opponent sacrifices, then discards, then loses 4 life
    @Test
    fun ravagerHitsEachOpponent() = scenario(players = 3) {
        alice.battlefield("Swamp", "Swamp", "Swamp", "Swamp", "Swamp", "Swamp", "Swamp")
        alice.hand(ravager)
        bob.battlefield(two)
        bob.hand("Island")
        carol.battlefield(three)
        carol.hand("Forest")
        alice.casts(ravager, option = "Pay life")
        resolveAll()
        expect {
            alice.life shouldBe 16
            bob.graveyard has two
            bob.graveyard has "Island"
            carol.graveyard has three
            carol.graveyard has "Forest"
            bob.life shouldBe 16
            carol.life shouldBe 16
        }
    }

    // CR 601.2f, 121.2: one opponent drew four cards: {U}{U}{U} less
    @Test
    fun tabCostsLessAfterAnOpponentDrewFour() = scenario(players = 3) {
        alice.battlefield("Island", "Island")
        alice.hand(windfall, tab)
        alice.library("Plains", "Plains")
        bob.library("Swamp", "Swamp", "Swamp", "Swamp", "Swamp")
        alice.casts(windfall, target = "bob")
        resolveAll()
        alice.casts(tab, x = 1)
        resolveAll()
        expect { alice.hand has "Plains" }
    }

    // CR 601.2f: two opponents drawing two each isn't one opponent drawing four
    @Test
    fun tabCostsFullWhenNoOpponentDrewFour() = scenario(players = 3) {
        alice.battlefield("Island", "Island")
        alice.hand(handout, tab)
        bob.library("Swamp", "Swamp", "Swamp")
        carol.library("Forest", "Forest", "Forest")
        alice.casts(handout)
        resolveAll()
        alice.casts(tab, x = 1)
        assertTrue(decision<Decision.ManaPayment>().suggestion.isEmpty())
    }

    // CR 601.2f, 122.1: a spell cast from the top of the library by removing Corvo's shield counter
    @Test
    fun corvoCastsFromTheTopByRemovingACounter() = scenario {
        alice.battlefield(corvo, counters = listOf(CounterKind.Shield to 1))
        alice.battlefield("Mountain")
        alice.library(spark, "Island")
        alice.casts(spark, target = "bob")
        resolveAll()
        expect {
            bob.life shouldBe 17
            corvo.counters(CounterKind.Shield) shouldBe 0
        }
    }

    // CR 105.2, 106.6: a three-colour spell from among the exiled cards cast with the restricted mana; a two-colour one isn't castable
    @Test
    fun summitCastsAThreeColorSpell() = scenario {
        alice.battlefield("Plains", "Island", "Swamp", "Mountain", "Forest", "Plains", "Island", "Swamp")
        alice.hand(summit)
        alice.library(pact, accord, "Plains", "Plains", "Plains", "Plains", "Plains", "Plains", "Plains", "Plains", "Forest")
        alice.casts(summit)
        resolveAll()
        assertTrue(castable(this, pact))
        assertTrue(!castable(this, accord))
        alice.casts(pact)
        resolveAll()
        expect {
            alice.graveyard has pact
            alice.hand has "Forest"
        }
    }
}
