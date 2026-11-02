package arcanum.engine.trigger

import arcanum.api.Ability
import arcanum.api.CardDefinition
import arcanum.api.CardType
import arcanum.api.Effect
import arcanum.api.EventPattern
import arcanum.api.FaceDefinition
import arcanum.api.Instructions
import arcanum.api.Keyword
import arcanum.api.Layout
import arcanum.api.ManaCost
import arcanum.api.ObjectFilter
import arcanum.api.Objects
import arcanum.api.PlayerFilter
import arcanum.api.Players
import arcanum.api.PrintedNumber
import arcanum.api.Step
import arcanum.api.Subtype
import arcanum.api.Trigger
import arcanum.api.Value
import arcanum.engine.cards.Cards
import arcanum.engine.scenario.scenario
import kotlin.test.Test
import kotlin.test.assertIs

/** Events of keyword abilities: cycling a card (CR 702.29c, d) and a Vehicle becoming crewed (CR 702.122e). */
class CyclingAndCrewTriggersTest {
    private fun gainOnEvent(event: EventPattern, amount: Int) = Ability.Triggered(Trigger.On(listOf(event)),
        Instructions(steps = listOf(Step.Do(Effect.GainLife(Players.You, Value.Fixed(amount))))))

    private fun arcanum.engine.scenario.Scenario.passUntilEmpty() {
        var guard = 10
        do {
            (pending as? arcanum.engine.decision.Decision.OrderTriggers)?.let { answer(arcanum.engine.decision.Answer.Order(it.triggers.indices.toList())) }
            allPass()
        } while ((state.stack.isNotEmpty() || pending != null) && guard-- > 0)
    }

    /** "Whenever you cycle a card, you gain 2 life." */
    private val sage = Cards.define("Cyclewatch Sage", CardDefinition(Layout.Normal, listOf(FaceDefinition(
        "Cyclewatch Sage", ManaCost.parse("{1}{U}"), types = listOf(CardType.Creature), power = PrintedNumber.Fixed(1),
        toughness = PrintedNumber.Fixed(1), abilities = listOf(gainOnEvent(EventPattern.Cycle(ObjectFilter.Any, PlayerFilter.You), 2)),
    ))))

    /** "Cycling {1}. When you cycle this card, you gain 3 life." */
    private val shard = Cards.define("Recycled Shard", CardDefinition(Layout.Normal, listOf(FaceDefinition(
        "Recycled Shard", ManaCost.parse("{3}{W}"), types = listOf(CardType.Sorcery),
        abilities = listOf(Keyword.Cycling(arcanum.api.Cost.mana(ManaCost.parse("{1}")!!)),
            gainOnEvent(EventPattern.Cycle(ObjectFilter.Is(Objects.Self), PlayerFilter.You), 3)),
    ))))

    /** "Whenever a player discards a card, you gain 1 life." */
    private val ledger = Cards.define("Discard Ledger", CardDefinition(Layout.Normal, listOf(FaceDefinition(
        "Discard Ledger", ManaCost.parse("{1}"), types = listOf(CardType.Artifact),
        abilities = listOf(gainOnEvent(EventPattern.Discard(ObjectFilter.Any, PlayerFilter.Any), 1)),
    ))))

    /** "Crew 1. Whenever this Vehicle becomes crewed, you gain 1 life." */
    private val wagon = Cards.define("Crewbound Wagon", CardDefinition(Layout.Normal, listOf(FaceDefinition(
        "Crewbound Wagon", ManaCost.parse("{2}"), types = listOf(CardType.Artifact), subtypes = listOf(Subtype("Vehicle")),
        power = PrintedNumber.Fixed(3), toughness = PrintedNumber.Fixed(3),
        abilities = listOf(Keyword.Crew(1), gainOnEvent(EventPattern.Crewed(ObjectFilter.Is(Objects.Self)), 1)),
    ))))

    /** Cycling a triland triggers "whenever you cycle a card"; cycling is also discarding (CR 702.29d). */
    // CR 702.29c, 702.29d
    @Test
    fun cyclingACardTriggers() = scenario {
        alice.battlefield(sage, ledger, "Forest", "Forest", "Forest")
        alice.hand("Hallowed Mire Triad")
        alice.library("Island")
        alice.activates("Hallowed Mire Triad")
        // Both trigger: the Sage's and the Ledger's, to be ordered
        assertIs<arcanum.engine.decision.Decision.OrderTriggers>(pending)
        passUntilEmpty()
        expect {
            alice.life shouldBe 23
            alice.hand.names shouldBe listOf("Island")
        }
    }

    /** "When you cycle this card" triggers from the graveyard the card went to (CR 702.29c). */
    // CR 702.29c
    @Test
    fun whenYouCycleThisCard() = scenario {
        alice.battlefield("Plains")
        alice.hand(shard)
        alice.library("Island")
        alice.activates(shard)
        passUntilEmpty()
        expect {
            alice.life shouldBe 23
            alice.graveyard has shard
            alice.hand.names shouldBe listOf("Island")
        }
    }

    /** Discarding a card another way isn't cycling it. */
    // CR 702.29c
    @Test
    fun aPlainDiscardIsntCycling() = scenario {
        alice.battlefield(sage, ledger)
        alice.hand("Hallowed Mire Triad")
        drive { r ->
            arcanum.engine.action.Actions.discard(arcanum.engine.action.Act(r, arcanum.engine.action.Asker.NONE), null, listOf(card("Hallowed Mire Triad")))
            arcanum.engine.Progress.Done
        }
        resolveStack()
        expect { alice.life shouldBe 21 }
    }

    /** The Vehicle's "becomes crewed" ability triggers as its crew ability resolves, not when it's activated. */
    // CR 702.122e
    @Test
    fun becomingCrewedTriggersAsCrewResolves() = scenario {
        alice.battlefield(wagon, "Elvish Seer")
        alice.activates(wagon, choosing = listOf("Elvish Seer"))
        expect { stack.size shouldBe 1 }
        resolve()
        expect {
            stack.size shouldBe 1
            wagon.types.contains(CardType.Creature) shouldBe true
        }
        resolve()
        expect { alice.life shouldBe 21 }
    }
}
