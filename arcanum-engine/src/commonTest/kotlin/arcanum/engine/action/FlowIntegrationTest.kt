package arcanum.engine.action

import arcanum.api.Ability
import arcanum.api.Affected
import arcanum.api.CardDefinition
import arcanum.api.CardType
import arcanum.api.EventPattern
import arcanum.api.FaceDefinition
import arcanum.api.Layout
import arcanum.api.ManaCost
import arcanum.api.Mechanic
import arcanum.api.Modification
import arcanum.api.ObjectFilter
import arcanum.api.Objects
import arcanum.api.PlayerFilter
import arcanum.api.RecipientFilter
import arcanum.api.TurnStep
import arcanum.engine.cards.Cards
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.scenario.scenario
import kotlin.test.Test
import kotlin.test.assertTrue

/** The turn flow (casting, priority, steps, combat) and the event pipeline together: replacements apply to land plays,
 * the draw step's draw and combat damage; real casts resolve with their triggers. */
class FlowIntegrationTest {
    private fun enchantment(name: String, mechanic: Mechanic) = Cards.define(name, CardDefinition(Layout.Normal, listOf(
        FaceDefinition(name, ManaCost.parse("{2}"), types = listOf(CardType.Enchantment),
            abilities = listOf(Ability.Static(listOf(mechanic), Affected.Objects(Objects.Self)))))))

    // CR 614.12, 305.1: playing a land that asks as it enters
    @Test
    fun playingAShockLandAsksToPayLife() = scenario {
        alice.hand("Gore Vault")

        alice.plays("Gore Vault")
        assertTrue(decision<Decision.May>().cost != null)
        answer(Answer.YesNo(false))

        expect {
            alice.battlefield has "Gore Vault"
            "Gore Vault".tapped shouldBe true
            alice.life shouldBe 20
        }
    }

    // CR 510.2, 614.1a: combat damage goes through replacement effects
    @Test
    fun combatDamageIsDoubled() = scenario {
        /** "If a source you control would deal damage to a permanent or player, it deals double that damage instead." */
        alice.battlefield(enchantment("Test Fire Within", Mechanic.Replacement(EventPattern.Damage(ObjectFilter.ControlledBy(PlayerFilter.You),
            RecipientFilter()), modify = listOf(Modification.MultiplyAmount(2)))))
        alice.battlefield("Elvish Hierophant")
        turn(1, step = TurnStep.BeginningOfCombat)

        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks("Elvish Hierophant")
        advanceTo(TurnStep.CombatDamage)
        advanceTo(TurnStep.EndOfCombat)

        expect { bob.life shouldBe 16 }
    }

    // CR 121.6, 504.1: the draw step's draw goes through replacement effects
    @Test
    fun drawStepDrawCanBeSkipped() = scenario {
        /** "If you would draw a card, skip that draw instead." */
        alice.battlefield(enchantment("Test Stalled Mind", Mechanic.Replacement(EventPattern.Draw(PlayerFilter.You),
            modify = listOf(Modification.Skip))))
        alice.library("Forest")
        turn(2, step = TurnStep.Upkeep)

        advanceTo(TurnStep.PrecombatMain)

        expect { alice.hand.size shouldBe 0 }
    }

    // CR 601.2, 608.3, 603.3: a real cast resolves when all pass; its enters trigger goes on the Stack
    @Test
    fun aCastCreatureResolvesAndTriggers() = scenario {
        alice.battlefield("Forest", "Forest", "Forest")
        alice.hand("Canopy Visionary")
        alice.library("Island")

        alice.casts("Canopy Visionary")
        allPass()
        expect { stack.names shouldBe listOf("ability of Canopy Visionary") }
        allPass()

        expect { alice.hand has "Island" }
    }

    // CR 702.34a: flashback cast through the casting flow is exiled as it leaves the Stack
    @Test
    fun flashbackThroughTheCastingFlow() = scenario {
        alice.battlefield("Mountain", "Mountain", "Mountain", "Mountain", "Mountain")
        alice.graveyard("Cinder Bolt")

        alice.casts("Cinder Bolt", target = "bob")
        allPass()

        expect {
            bob.life shouldBe 18
            alice.exile has "Cinder Bolt"
        }
    }
}
