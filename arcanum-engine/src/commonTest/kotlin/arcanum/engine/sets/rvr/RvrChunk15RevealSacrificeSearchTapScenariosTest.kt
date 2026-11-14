package arcanum.engine.sets.rvr

import arcanum.api.CardType
import arcanum.api.CounterKind
import arcanum.api.TurnStep
import arcanum.engine.cards.Cards
import arcanum.engine.decision.Answer
import arcanum.engine.scenario.scenario
import arcanum.engine.sets.blb.resolveAll
import kotlin.test.Test
import kotlin.test.assertEquals

/** Ravnica Remastered chunk 15 in play: reveal-and-split on combat damage, a Rat count, five coin flips for extra turns,
 * the sacrifice with a power condition (three players), the chosen-type tap or untap, and untapping all creatures or lands. */
class RvrChunk15RevealSacrificeSearchTapScenariosTest {
    private val cyclops = Cards.invent("Rvr15 Bellow Cyclops", "{4}{R}{R}{G}{G}", "Legendary Creature — Cyclops", """
        Trample
        Whenever this creature deals combat damage to a player, reveal the top three cards of your library. Put all land cards revealed this way into your hand and the rest into your graveyard.
        Discard a land card: This creature deals 3 damage to any target.
    """, "7/6")
    private val packRat = Cards.invent("Rvr15 Gutter Rat", "{1}{B}", "Creature — Rat", """
        This creature's power and toughness are each equal to the number of Rats you control.
        {2}{B}, Discard a card: Create a token that's a copy of this creature.
    """, "*/*")
    private val otherRat = Cards.invent("Rvr15 Sewer Rat", "{B}", "Creature — Rat", "", "1/1")
    private val ral = Cards.invent("Rvr15 Rall Zarek", "{2}{U}{R}", "Legendary Planeswalker — Ral", """
        [+1]: Tap target permanent, then untap another target permanent.
        [−2]: This planeswalker deals 3 damage to any target.
        [−7]: Flip five coins. Take an extra turn after this one for each coin that comes up heads.
    """, loyalty = "4")
    private val embrace = Cards.invent("Rvr15 Undertow Embrace", "{2}{B}", "Instant",
        "Target opponent sacrifices a creature of their choice. If you control a creature with power 4 or greater, you gain 4 life.")
    private val turnabout = Cards.invent("Rvr15 Flip About", "{2}{U}{U}", "Instant",
        "Choose artifact, creature, or land. Tap all untapped permanents of the chosen type target player controls, or untap all tapped permanents of that type that player controls.")
    private val muster = Cards.invent("Rvr15 Sound the Muster", "{1}{W}", "Instant", "Untap all creatures you control.\nDraw a card.")
    private val reclamation = Cards.invent("Rvr15 Frontier Reclamation", "{3}{G}", "Enchantment", "At the beginning of your end step, untap all lands you control.")
    private val hussar = Cards.invent("Rvr15 Dawn Hussar", "{3}{W}{U}", "Creature — Human Knight", """
        Flying
        When this creature enters, untap all creatures you control.
        Forecast — Tap two untapped white and/or blue creatures you control, Reveal this card from your hand: Draw a card. (Activate only during your upkeep and only once each turn.)
    """, "4/3")
    private val bear = Cards.invent("Rvr15 Grizzly", "{1}{G}", "Creature — Bear", "", "2/2")
    private val giant = Cards.invent("Rvr15 Hill Giant", "{3}{R}", "Creature — Giant", "", "4/4")
    private val knight = Cards.invent("Rvr15 White Knight", "{1}{W}", "Creature — Knight", "", "2/2")
    private val mage = Cards.invent("Rvr15 Blue Mage", "{1}{U}", "Creature — Wizard", "", "2/2")

    // CR 701.20a (reveal), 603.2: lands to hand, the rest to the graveyard; the card below the top three stays
    @Test
    fun cyclopsRevealsThreeAndKeepsOnlyTheLands() = scenario {
        alice.battlefield(cyclops)
        alice.library("Forest", bear, "Island", "Swamp")
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(cyclops)
        advanceTo(TurnStep.CombatDamage)
        resolveAll()
        expect {
            alice.hand.names shouldBe listOf("Forest", "Island")
            alice.graveyard has bear
            alice.library.size shouldBe 1
        }
    }

    // CR 208.2a (characteristic-defining P/T), 205.3 (only your own Rats count), 707.2 (a token copy)
    @Test
    fun gutterRatCountsOnlyYourRats() = scenario {
        alice.battlefield(packRat, otherRat, "Swamp", "Swamp", "Swamp")
        bob.battlefield(Cards.invent("Rvr15 Foe Rat", "{B}", "Creature — Rat", "", "1/1"))
        alice.hand("Forest")
        expect { packRat.power shouldBe 2 }
        alice.activates(packRat, choosing = listOf("Forest"))
        resolveAll()
        expect {
            alice.battlefield.names.count { it == packRat } shouldBe 2
            packRat.power shouldBe 3
        }
    }

    // CR 705.1 (flip a coin), 705.2: three of five flips won = three extra turns; CR 500.7
    @Test
    fun ralMinusSevenGivesAnExtraTurnPerWonFlip() = scenario {
        alice.battlefield(ral, counters = listOf(CounterKind.Loyalty to 7))
        random(Answer.CoinFlip(true), Answer.CoinFlip(false), Answer.CoinFlip(true), Answer.CoinFlip(true), Answer.CoinFlip(false))
        alice.activates(ral, ability = 2)
        resolveAll()
        assertEquals(listOf(alice.id, alice.id, alice.id), state.turn.extraTurns)
    }

    // CR 701.21a (the chosen player decides), 608.2c: the condition is checked on resolution
    @Test
    fun embraceMakesTheTargetChooseAndGainsLifeWithPowerFour() = scenario(players = 3) {
        alice.battlefield(giant)
        alice.hand(embrace)
        alice.battlefield("Swamp", "Swamp", "Swamp")
        bob.battlefield(bear, knight)
        carol.battlefield(bear)
        alice.casts(embrace, target = "bob")
        resolveAll()
        bob.chooses(knight)
        expect {
            bob.graveyard has knight
            bob.battlefield has bear
            carol.battlefield has bear
            alice.life shouldBe 24
        }
    }

    @Test
    fun embraceGainsNothingWithoutAPowerFourCreature() = scenario(players = 3) {
        alice.battlefield(bear, "Swamp", "Swamp", "Swamp")
        alice.hand(embrace)
        bob.battlefield(knight, mage)
        alice.casts(embrace, target = "bob")
        resolveAll()
        bob.chooses(mage)
        expect {
            bob.graveyard has mage
            alice.life shouldBe 20
        }
    }

    // CR 608.2d: the type and tap-or-untap are chosen on resolution; only the target player's permanents of that type change
    @Test
    fun turnaboutTapsOnlyTheChosenTypeOfTheTarget() = scenario(players = 3) {
        alice.battlefield("Island", "Island", "Island", "Island")
        alice.hand(turnabout)
        bob.battlefield(bear, knight, "Forest")
        carol.battlefield(giant)
        alice.casts(turnabout, target = "bob")
        resolveAll()
        answer(Answer.CardTypeChoice(CardType.Creature))
        answer(Answer.Option("tap"))
        expect {
            bear.tapped shouldBe true
            knight.tapped shouldBe true
            "Forest".tapped shouldBe false
            giant.tapped shouldBe false
        }
    }

    @Test
    fun turnaboutUntapsTheChosenTypeOfTheTarget() = scenario {
        alice.battlefield("Island", "Island", "Island", "Island")
        alice.hand(turnabout)
        bob.battlefield(bear, "Forest", tapped = true)
        bob.battlefield(knight, tapped = true)
        alice.casts(turnabout, target = "bob")
        resolveAll()
        answer(Answer.CardTypeChoice(CardType.Creature))
        answer(Answer.Option("untap"))
        expect {
            bear.tapped shouldBe false
            knight.tapped shouldBe false
            "Forest".tapped shouldBe true
        }
    }

    // CR 701.26b (untap all you control), 121.1: an opponent's creature stays tapped
    @Test
    fun musterUntapsOnlyYourCreaturesAndDraws() = scenario {
        alice.battlefield(bear, tapped = true)
        alice.battlefield("Plains", "Plains")
        bob.battlefield(knight, tapped = true)
        alice.hand(muster)
        alice.library("Forest")
        alice.casts(muster)
        resolveAll()
        expect {
            bear.tapped shouldBe false
            knight.tapped shouldBe true
            alice.hand.names shouldBe listOf("Forest")
        }
    }

    // CR 513.1, 701.26b: only your lands untap, at your end step
    @Test
    fun reclamationUntapsYourLandsAtYourEndStep() = scenario {
        alice.battlefield(reclamation, "Forest", "Forest", tapped = true)
        bob.battlefield("Forest", tapped = true)
        advanceTo(TurnStep.End)
        resolveAll()
        expect {
            alice.battlefield.names.count { it == "Forest" } shouldBe 2
            "Forest".tapped shouldBe false
        }
    }

    // CR 702.57a: Forecast from the hand, during your upkeep, tapping two white and/or blue creatures; once each turn
    @Test
    fun hussarForecastDrawsDuringYourUpkeep() = scenario {
        alice.hand(hussar)
        alice.battlefield(knight, mage, bear)
        alice.library("Forest", "Forest", "Forest")
        bob.library("Forest", "Forest")
        advanceTo(TurnStep.Upkeep, turn = 3)
        alice.activates(hussar, choosing = listOf(knight, mage))
        resolveAll()
        expect {
            knight.tapped shouldBe true
            mage.tapped shouldBe true
            bear.tapped shouldBe false
            alice.hand.names shouldBe listOf(hussar, "Forest")
        }
    }
}
