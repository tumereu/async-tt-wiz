package arcanum.engine.sets.otj

import arcanum.api.Keyword
import arcanum.api.TurnStep
import arcanum.engine.cards.Cards
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.scenario.scenario
import arcanum.engine.sets.rtr.resolveAll
import kotlin.test.Test
import kotlin.test.assertFailsWith

/** OTJ chunk 15: Auras (enchant, graveyard draw, flash), an additional sacrifice cost, flash for {2} more, and a cost reduction by targets. */
class OtjChunk15EnchantScryCostsScenariosTest {
    private val ruckus = Cards.invent("Furious Haunting", "{1}{R}", "Enchantment — Aura", """
        Enchant creature
        Enchanted creature gets +1/+1 and has menace and trample.
        When this Aura is put into a graveyard from the battlefield, draw a card.
    """)
    private val sky = Cards.invent("Stretch for Clouds", "{3}{G}", "Enchantment — Aura", """
        Flash
        Enchant creature
        Enchanted creature gets +3/+2 and has reach.
        When this Aura is put into a graveyard from the battlefield, draw a card.
    """)
    private val cold = Cards.invent("Frozen Standstill", "{3}{U}", "Enchantment — Aura", """
        Flash
        Enchant artifact or creature
        When this Aura enters, tap enchanted permanent.
        Enchanted permanent loses all abilities and doesn't untap during its controller's untap step.
    """)
    private val bargain = Cards.invent("Grim Bargain", "{B}", "Instant", """
        As an additional cost to cast this spell, sacrifice a creature.
        Draw two cards.
    """)
    private val shackle = Cards.invent("Spectral Shackle", "{2}{W}", "Enchantment", """
        You may cast this spell as though it had flash if you pay {2} more to cast it.
        When this enchantment enters, exile target artifact or creature an opponent controls until this enchantment leaves the battlefield.
    """)
    private val room = Cards.invent("Not Enough Room", "{4}{U}", "Instant", """
        This spell costs {3} less to cast if it targets a permanent you control.
        Return up to two target nonland permanents to their owners' hands.
    """)
    private val dissolve = Cards.invent("Dissolving Light", "{W}", "Instant", "Destroy target enchantment.")
    private val bear = Cards.invent("Reedmarsh Bear", "{1}{G}", "Creature — Bear", "", "2/2")
    private val hawk = Cards.invent("Stormwing Hawk", "{1}{U}", "Creature — Bird", "Flying", "1/1")
    private val ox = Cards.invent("Tallgrass Ox", "{G}", "Creature — Ox", "", "1/1")

    // CR 303.4, 613.4c, 700.4: the Aura pumps and grants; destroyed, it goes to the graveyard from the battlefield and draws
    @Test
    fun auraPumpsGrantsAndDrawsWhenItIsDestroyed() = scenario {
        alice.battlefield("Mountain", "Mountain", "Plains", bear)
        alice.library("Island", "Island")
        alice.hand(ruckus, dissolve)
        alice.casts(ruckus, target = bear)
        allPass()
        expect {
            card(bear).power shouldBe 3
            card(bear).toughness shouldBe 3
            card(bear) hasKeyword Keyword.Menace.kind
            card(bear) hasKeyword Keyword.Trample.kind
        }
        alice.casts(dissolve, target = ruckus)
        resolveAll()
        expect {
            alice.graveyard has ruckus
            card(bear).power shouldBe 2
            alice.hand has "Island"
        }
    }

    // CR 702.8a, 303.4: flash on the opponent's turn; the Aura grants reach and +3/+2
    @Test
    fun flashAuraOnTheOpponentsTurn() = scenario {
        turn(number = 1, active = bob, step = TurnStep.Upkeep)
        alice.battlefield("Forest", "Forest", "Forest", "Forest")
        alice.hand(sky)
        bob.battlefield(bear)
        bob.passes()
        alice.casts(sky, target = bear)
        allPass()
        expect {
            card(bear).power shouldBe 5
            card(bear).toughness shouldBe 4
            card(bear) hasKeyword Keyword.Reach.kind
        }
    }

    // CR 613.1f, 502.3: tapped on entering, loses its flying, stays tapped through its controller's untap step
    @Test
    fun coldTapsStripsAbilitiesAndKeepsItTapped() = scenario {
        alice.battlefield("Island", "Island", "Island", "Island")
        alice.hand(cold)
        bob.battlefield(hawk)
        alice.casts(cold, target = hawk)
        allPass()
        allPass()
        expect {
            card(hawk).tapped shouldBe true
            card(hawk) lacksKeyword Keyword.Flying.kind
        }
        advanceTo(TurnStep.Upkeep, turn = 2)
        expect { card(hawk).tapped shouldBe true }
    }

    // CR 601.2b, 601.2h, 701.21a: only your own creature can be sacrificed to the cost
    @Test
    fun sacrificeCostTakesYourOwnCreature() = scenario {
        alice.battlefield("Swamp", ox)
        alice.library("Island", "Island")
        alice.hand(bargain)
        alice.casts(bargain, choosing = listOf(ox))
        resolveAll()
        expect {
            alice.graveyard has ox
            alice.hand.size shouldBe 2
        }
    }

    @Test
    fun sacrificeCostCannotUseAnOpponentsCreature() = scenario {
        alice.battlefield("Swamp")
        bob.battlefield(ox)
        alice.hand(bargain)
        assertFailsWith<IllegalArgumentException> { alice.casts(bargain) }
    }

    // CR 601.3b, 601.2f: flash by paying {2} more; the exile lasts while the enchantment stays (CR 610.3)
    @Test
    fun shackleFlashedInExilesAnOpponentsCreature() = scenario {
        alice.battlefield("Plains", "Plains", "Plains", "Plains", "Plains")
        alice.hand(shackle)
        bob.battlefield(bear)
        advanceTo(TurnStep.BeginningOfCombat)
        alice.casts(shackle, kicked = 1)
        resolveAll()
        expect {
            alice.battlefield has shackle
            bob.exile has bear
        }
    }

    @Test
    fun shackleNeedsTheExtraCostOutsideItsTiming() = scenario {
        alice.battlefield("Plains", "Plains", "Plains", "Plains", "Plains")
        alice.hand(shackle)
        advanceTo(TurnStep.BeginningOfCombat)
        assertFailsWith<IllegalArgumentException> { alice.casts(shackle, kicked = 0) }
    }

    // CR 601.2f: "if it targets a permanent you control" holds when any chosen target is yours
    @Test
    fun reductionWhenOneOfTwoTargetsIsYours() = scenario {
        alice.battlefield("Island", "Island", bear)
        alice.hand(room)
        bob.battlefield(ox)
        alice.casts(room, targets = listOf(bear, ox))
        resolveAll()
        expect {
            alice.hand has bear
            bob.hand has ox
        }
    }

    @Test
    fun noReductionWhenNoTargetIsYours() = scenario {
        alice.battlefield("Island", "Island")
        alice.hand(room)
        bob.battlefield(ox)
        alice.casts(room, target = ox)
        expect { decision<Decision.ManaPayment>().suggestion.isEmpty() shouldBe true }
    }
}
