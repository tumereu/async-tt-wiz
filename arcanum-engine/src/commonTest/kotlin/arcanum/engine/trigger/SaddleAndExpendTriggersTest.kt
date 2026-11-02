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
import arcanum.api.ObjectMark
import arcanum.api.Objects
import arcanum.api.PlayerFilter
import arcanum.api.Players
import arcanum.api.PrintedNumber
import arcanum.api.Step
import arcanum.api.Subtype
import arcanum.api.Trigger
import arcanum.api.TurnStep
import arcanum.api.Value
import arcanum.engine.cards.Cards
import arcanum.engine.scenario.scenario
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Saddling (CR 702.171) and expending (CR 700.14). */
class SaddleAndExpendTriggersTest {
    private fun gainOnEvent(event: EventPattern, amount: Int) = Ability.Triggered(Trigger.On(listOf(event)),
        Instructions(steps = listOf(Step.Do(Effect.GainLife(Players.You, Value.Fixed(amount))))))

    /** "Saddle 1. Whenever this creature becomes saddled, you gain 2 life." */
    private val mount = Cards.define("Dunerunner Mount", CardDefinition(Layout.Normal, listOf(FaceDefinition(
        "Dunerunner Mount", ManaCost.parse("{1}{R}"), types = listOf(CardType.Creature), subtypes = listOf(Subtype("Mount")),
        power = PrintedNumber.Fixed(3), toughness = PrintedNumber.Fixed(2),
        abilities = listOf(Keyword.Saddle(1), gainOnEvent(EventPattern.Saddled(ObjectFilter.Is(Objects.Self)), 2)),
    ))))

    /** "Whenever you expend 4, you gain 1 life." */
    private val boxer = Cards.define("Fourfold Brawler", CardDefinition(Layout.Normal, listOf(FaceDefinition(
        "Fourfold Brawler", ManaCost.parse("{1}{G}"), types = listOf(CardType.Creature), power = PrintedNumber.Fixed(2),
        toughness = PrintedNumber.Fixed(2), abilities = listOf(gainOnEvent(EventPattern.Expend(4, PlayerFilter.You), 1)),
    ))))

    /** Saddling taps a creature, the Mount becomes saddled as the ability resolves, which triggers; it stays saddled
     * until the turn ends. */
    // CR 702.171a, 702.171b
    @Test
    fun saddlingMarksAndTriggers() = scenario {
        alice.battlefield(mount, "Elvish Seer")
        alice.activates(mount, choosing = listOf("Elvish Seer"))
        expect { "Elvish Seer".tapped shouldBe true }
        allPass()
        assertTrue(ObjectMark.Saddled in state.objOrThrow(card(mount)).marks)
        allPass()
        expect { alice.life shouldBe 22 }
        advanceTo(TurnStep.Upkeep, turn = 2)
        assertFalse(ObjectMark.Saddled in state.objOrThrow(card(mount)).marks)
    }

    /** Two {2} artifacts: the first payment brings the mana spent on spells this turn to 2, the second to 4, which
     * expends 4 (the trigger goes on the Stack above the second artifact). */
    // CR 700.14
    @Test
    fun expendTriggersAsTheTotalReachesIt() = scenario {
        alice.battlefield(boxer, "Island", "Island", "Island", "Island")
        alice.hand("Shrine of Oblivion", "Shrine of Oblivion")
        alice.casts("Shrine of Oblivion", paying = listOf("Island", "Island#1"))
        expect { stack.size shouldBe 1 }
        allPass()
        alice.casts("Shrine of Oblivion", paying = listOf("Island#2", "Island#3"))
        expect { stack.size shouldBe 2 }
        repeat(2) { allPass() }
        expect {
            alice.life shouldBe 21
            stack.isEmpty()
        }
    }
}
