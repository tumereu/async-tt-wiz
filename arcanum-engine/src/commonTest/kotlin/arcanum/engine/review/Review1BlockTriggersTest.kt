package arcanum.engine.review

import arcanum.api.Ability
import arcanum.api.CardDefinition
import arcanum.api.CardType
import arcanum.api.Effect
import arcanum.api.EventPattern
import arcanum.api.FaceDefinition
import arcanum.api.Instructions
import arcanum.api.Layout
import arcanum.api.ManaCost
import arcanum.api.ObjectFilter
import arcanum.api.Objects
import arcanum.api.Players
import arcanum.api.PrintedNumber
import arcanum.api.Step
import arcanum.api.Trigger
import arcanum.api.TurnStep
import arcanum.api.Value
import arcanum.engine.cards.Cards
import arcanum.engine.scenario.scenario
import kotlin.test.Ignore
import kotlin.test.Test

/** Review 1: triggers on blocking (CR 509.3) and attack requirements (CR 508.1d). */
class Review1BlockTriggersTest {
    /** A 2/2 creature with one triggered ability: when [event] happens, you gain 2 life. */
    private fun creature(name: String, event: EventPattern) = Cards.define(name, CardDefinition(Layout.Normal, listOf(FaceDefinition(
        name, ManaCost.parse("{1}{W}"), types = listOf(CardType.Creature), power = PrintedNumber.Fixed(2), toughness = PrintedNumber.Fixed(2),
        abilities = listOf(Ability.Triggered(Trigger.On(listOf(event)),
            Instructions(steps = listOf(Step.Do(Effect.GainLife(Players.You, Value.Fixed(2))))))),
    ))))

    /** "Whenever this creature becomes blocked, you gain 2 life." */
    private val blockedOne = creature("Review Lure Hound", EventPattern.BecomesBlocked(ObjectFilter.Is(Objects.Self), ObjectFilter.Any))

    /** "Whenever this creature attacks and isn't blocked, you gain 2 life." */
    private val unblockedOne = creature("Review Slipping Fox", EventPattern.AttacksUnblocked(ObjectFilter.Is(Objects.Self)))

    /** "Becomes blocked" triggers when a blocker is declared for it. */
    // CR 509.3c
    @Test
    fun becomesBlockedTriggers() = scenario {
        alice.battlefield(blockedOne)
        bob.battlefield("Thicketwood Elves")
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(blockedOne)
        allPass()
        bob.blocks("Thicketwood Elves" to blockedOne)
        allPass()
        expect { alice.life shouldBe 22 }
    }

    /** "Attacks and isn't blocked" doesn't trigger for a blocked attacker. */
    // CR 509.3g
    @Test
    fun blockedAttackerIsntUnblocked() = scenario {
        alice.battlefield(unblockedOne)
        bob.battlefield("Thicketwood Elves")
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(unblockedOne)
        allPass()
        bob.blocks("Thicketwood Elves" to unblockedOne)
        allPass()
        expect { alice.life shouldBe 20 }
    }

    /** "Attacks and isn't blocked" triggers for an unblocked attacker. */
    // CR 509.3g
    @Test
    fun unblockedAttackerTriggers() = scenario {
        alice.battlefield(unblockedOne)
        bob.battlefield("Thicketwood Elves")
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(unblockedOne)
        allPass()
        bob.blocks()
        allPass()
        expect { alice.life shouldBe 22 }
    }

    /** Requirements are obeyed as far as restrictions allow: two creatures that must attack under "no more than one
     * creature can attack" — attacking with one of them is legal. */
    // CR 508.1d
    @Test
    fun mustAttackUnderAMaximumOfOne() = scenario {
        alice.battlefield("Kael's Marauder", "Kael's Marauder")
        effect(bob, arcanum.api.Mechanic.MaximumAttackers(1), affects = arcanum.api.Affected.Objects(Objects.Each(ObjectFilter.Type(CardType.Creature))),
            duration = arcanum.api.Duration.Indefinitely)
        alice.library("Island", "Island", "Island")
        advanceTo(TurnStep.DeclareAttackers)
        perform(arcanum.engine.input.Input.DeclareAttackers(alice.id, listOf(
            arcanum.engine.input.Attack(card("Kael's Marauder"), arcanum.engine.state.TargetRef.Player(bob.id)))))
    }

    /** A spell cast by an effect (cascade) needs legal targets like any other: with none, it isn't cast and goes to
     * the bottom with the other exiled cards. */
    // CR 601.2c, 702.85a
    @Ignore("review1 #22: a spell cast by an effect without paying is put on the Stack even with no legal target (TriggerTest.cascade encodes it)")
    @Test
    fun cascadeIntoASpellWithoutTargets() = scenario {
        alice.battlefield("Forest", "Island", "Forest")
        alice.hand("Splintered Envoy")
        alice.library("Island", "Plowshare Decree", "Forest")
        alice.casts("Splintered Envoy")
        allPass()
        if (pending is arcanum.engine.decision.Decision.May) answer(arcanum.engine.decision.Answer.YesNo(true))
        expect {
            stack.names shouldBe listOf("Splintered Envoy")
            alice.library has "Plowshare Decree"
        }
    }
}
