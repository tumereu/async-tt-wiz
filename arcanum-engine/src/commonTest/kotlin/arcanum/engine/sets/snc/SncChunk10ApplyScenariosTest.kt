package arcanum.engine.sets.snc

import arcanum.api.Keyword
import arcanum.api.TurnStep
import arcanum.engine.cards.Cards
import arcanum.engine.scenario.scenario
import arcanum.engine.sets.blb.resolveAll
import arcanum.engine.sets.blb.targets
import kotlin.test.Test
import kotlin.test.assertFailsWith

/** Streets of New Capenna chunk 10 in play: a charm whose first mode pumps a creature that then deals damage by its new power,
 * two different targets given base power and toughness before damage to each creature, a creature that can't attack or block until
 * its opponent's next turn, and a count of multicolored permanents (you control only). */
class SncChunk10ApplyScenariosTest {
    private val charm = Cards.invent("Snc10 Guild Accord", "{G}{W}{U}", "Instant", """
        Choose one —
        • Target creature you control gets +1/+0 until end of turn. It deals damage equal to its power to target creature or planeswalker an opponent controls.
        • Destroy target enchantment.
        • Draw two cards.
    """)
    private val takeover = Cards.invent("Snc10 Corporate Raid", "{2}{U}{B}{R}", "Sorcery",
        "Up to one target creature has base power and toughness 1/1 until end of turn. Up to one other target creature has base power and toughness 4/4 until end of turn. Then Snc10 Corporate Raid deals 3 damage to each creature.")
    private val adjudicators = Cards.invent("Snc10 Tribunal of Three", "{2}{G}{W}{U}", "Creature — Cat Citizen",
        "When this creature enters, target creature an opponent controls can't attack or block until your next turn.", "4/4")
    private val steward = Cards.invent("Snc10 Skittering Steward", "{3}", "Artifact Creature — Construct",
        "At the beginning of combat on your turn, if you control two or more multicolored permanents, this creature gains double strike until end of turn.", "4/1")
    private val gold = Cards.invent("Snc10 Gold Cat", "{G}{W}", "Creature — Cat", "", "2/2")
    private val goldTwo = Cards.invent("Snc10 Gold Dog", "{R}{W}", "Creature — Dog", "", "2/2")
    private val kitten = Cards.invent("Snc10 Alley Kitten", "{W}", "Creature — Cat", "", "1/1")
    private val wall = Cards.invent("Snc10 Brick Wall", "{1}{W}", "Creature — Wall", "", "1/5")
    private val brute = Cards.invent("Snc10 Dock Brute", "{3}{R}", "Creature — Ogre", "", "4/4")
    private val tough = Cards.invent("Snc10 Street Tough", "{2}{R}", "Creature — Human Warrior", "", "3/3")

    // CR 608.2c (its power as it resolves, after +1/+0), 120.3 (the pumped creature is the source)
    @Test
    fun charmPumpsThenDealsDamageEqualToPower() = scenario {
        alice.battlefield("Forest", "Plains", "Island", brute)
        alice.hand(charm)
        bob.battlefield(tough)
        alice.casts(charm, modes = listOf(0), targets = listOf(brute, tough))
        resolveAll()
        expect {
            card(brute).power shouldBe 5
            bob.graveyard has tough
        }
    }

    // CR 700.2: the draw mode
    @Test
    fun charmDrawsTwo() = scenario {
        alice.battlefield("Forest", "Plains", "Island")
        alice.hand(charm)
        alice.library("Plains", "Island", "Swamp")
        alice.casts(charm, modes = listOf(2))
        resolveAll()
        expect {
            alice.hand has "Plains"
            alice.hand has "Island"
        }
    }

    // CR 115.3 (two different targets), 613.4b (base 1/1 and 4/4 before the damage), 120.3 (3 damage to each creature)
    @Test
    fun takeoverShrinksOneGrowsAnotherThenDamagesAll() = scenario {
        alice.battlefield("Island", "Swamp", "Mountain", "Plains", "Plains", wall, kitten, brute)
        alice.hand(takeover)
        alice.casts(takeover, targets = listOf(wall, kitten))
        resolveAll()
        expect {
            alice.graveyard has wall
            alice.battlefield has kitten
            card(kitten).power shouldBe 4
            card(kitten).toughness shouldBe 4
            alice.battlefield has brute
        }
    }

    // CR 508.1c, 509.1b, 611.2b: until alice's next turn the opponent's creature can't attack or block, then it can
    @Test
    fun adjudicatorsStopsAnOpposingCreatureUntilYourNextTurn() = scenario(players = 3) {
        alice.battlefield("Forest", "Plains", "Island", "Island", "Island")
        alice.hand(adjudicators)
        bob.battlefield(tough)
        carol.battlefield(brute)
        alice.casts(adjudicators)
        resolveAll()
        targets(tough)
        resolveAll()
        advanceTo(TurnStep.DeclareAttackers, turn = 2)
        assertFailsWith<Throwable> { bob.attacks(tough) }
    }

    // CR 700.8 (multicolored), 603.4 (intervening if): two multicolored permanents you control; an opponent's don't count
    @Test
    fun stewardNeedsTwoMulticoloredPermanentsOfYours() = scenario(players = 3) {
        alice.battlefield(steward, gold)
        bob.battlefield(goldTwo)
        advanceTo(TurnStep.BeginningOfCombat)
        resolveAll()
        expect { steward lacksKeyword Keyword.DoubleStrike.kind }
    }

    // CR 603.4: with two multicolored permanents the trigger gives double strike
    @Test
    fun stewardGainsDoubleStrikeWithTwo() = scenario(players = 3) {
        alice.battlefield(steward, gold, goldTwo)
        advanceTo(TurnStep.BeginningOfCombat)
        resolveAll()
        expect { steward hasKeyword Keyword.DoubleStrike.kind }
    }
}
