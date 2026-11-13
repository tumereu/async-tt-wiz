package arcanum.engine.sets.mh3

import arcanum.api.CounterKind
import arcanum.api.Keyword
import arcanum.api.TurnStep
import arcanum.engine.cards.Cards
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.scenario.scenario
import arcanum.engine.sets.blb.resolveAll
import kotlin.test.Test
import kotlin.test.assertFailsWith

/**
 * The MH3 cards of chunk 17, played: the invented names carry the oracle text of Horrid Shadowspinner, Psychic Frog and
 * Gift of the Viper (costs cut where they don't change play).
 */
class Mh3Chunk17DrawLifeCountersScenariosTest {
    private val webspinner = Cards.invent("Gloomy Webspinner", "{1}{U}{B}", "Creature — Horror", """
        Lifelink
        Whenever this creature attacks, you may draw cards equal to its power. If you do, discard that many cards.
    """, "2/3")
    private val toad = Cards.invent("Mindful Toad", "{U}{B}", "Creature — Frog", """
        Whenever this creature deals combat damage to a player or planeswalker, draw a card.
        Discard a card: Put a +1/+1 counter on this creature.
        Exile three cards from your graveyard: This creature gains flying until end of turn.
    """, "1/2")
    private val gift = Cards.invent("Gift of the Adder", "{G}", "Instant",
        "Put a +1/+1 counter, a reach counter, and a deathtouch counter on target creature. Untap it.")
    private val bear = Cards.invent("Reaping Bear", "{1}{G}", "Creature — Bear", "", "2/2")

    // CR 603.5, 121.1, 121.2: "that many" is the number the draw named (the attacker's power, 2)
    @Test
    fun webspinnerDrawsItsPowerThenDiscardsThatMany() = scenario {
        alice.battlefield(webspinner)
        alice.hand(bear)
        alice.library("Plains", "Island", "Swamp")
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(webspinner)
        resolveAll()
        answer(Answer.YesNo(true))
        resolveAll()
        alice.chooses(bear, "Plains")
        resolveAll()
        expect {
            alice.graveyard has bear
            alice.graveyard has "Plains"
            alice.hand has "Island"
            alice.hand.names.size shouldBe 1
            alice.library.names.size shouldBe 1
        }
    }

    // CR 603.5: a "may" declined draws and discards nothing
    @Test
    fun webspinnerMayDeclineTheDraw() = scenario {
        alice.battlefield(webspinner)
        alice.hand(bear)
        alice.library("Plains", "Island")
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(webspinner)
        resolveAll()
        answer(Answer.YesNo(false))
        resolveAll()
        expect {
            alice.hand.names.size shouldBe 1
            alice.library.names.size shouldBe 2
            alice.graveyard.names.size shouldBe 0
        }
    }

    // CR 602.1, 118.12: the cost exiles three cards from your own graveyard
    @Test
    fun toadExilesThreeCardsOfItsControllersGraveyardForFlying() = scenario {
        alice.battlefield(toad)
        alice.graveyard("Plains", "Island", "Swamp")
        bob.graveyard("Forest")
        expect { toad lacksKeyword Keyword.Flying.kind }
        alice.activates(toad, ability = 1, choosing = listOf("Plains", "Island", "Swamp"))
        resolveAll()
        expect {
            toad hasKeyword Keyword.Flying.kind
            alice.exile has "Plains"
            alice.exile.names.size shouldBe 3
            bob.graveyard has "Forest"
        }
    }

    // CR 602.2b: a cost can't be paid with other players' graveyard cards: two own cards and one of the opponent's are not three
    @Test
    fun toadCannotUseTheOpponentsGraveyardForTheCost() = scenario {
        alice.battlefield(toad)
        alice.graveyard("Plains", "Island")
        bob.graveyard("Forest")
        assertFailsWith<IllegalArgumentException> { alice.activates(toad, ability = 1) }
    }

    // CR 122.1b: a reach counter and a deathtouch counter give those keywords; CR 701.26 untap
    @Test
    fun giftPutsThreeCountersAndUntaps() = scenario {
        alice.battlefield("Forest")
        alice.battlefield(bear, tapped = true)
        alice.hand(gift)
        alice.casts(gift, target = bear)
        resolveAll()
        expect {
            bear.counters(CounterKind.PowerToughness(1, 1)) shouldBe 1
            bear.counters(CounterKind.Keyword(Keyword.Reach)) shouldBe 1
            bear.counters(CounterKind.Keyword(Keyword.Deathtouch)) shouldBe 1
            bear hasKeyword Keyword.Reach.kind
            bear hasKeyword Keyword.Deathtouch.kind
            bear.tapped shouldBe false
        }
    }
}
