package arcanum.engine.resolution

import arcanum.api.Ability
import arcanum.api.Condition
import arcanum.api.Cost
import arcanum.api.CostPart
import arcanum.api.Effect
import arcanum.api.Players
import arcanum.api.Step
import arcanum.api.Value
import arcanum.engine.cards.Cards
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.scenario.scenario
import kotlin.test.Test
import kotlin.test.assertEquals

/** Costs paid as a spell or ability resolves (CR 118.12) whose amount the definition doesn't give: an X, "its mana cost". */
class ResolutionCostsTest {
    private val vigil = Cards.invent("Mourning Vigil", "{2}{W}", "Enchantment",
        "Whenever a creature you control dies, you may pay {X}. If you do, you gain X life.")
    private val elf = Cards.invent("Vigil Elf", "{G}", "Creature — Elf", "", "1/1")
    private val doom = Cards.invent("Vigil Doom", "{1}{B}", "Sorcery", "Destroy target creature.")

    // CR 107.3f, 118.12: X nothing defines is chosen by the payer as they pay, at most what they can pay, and the rest reads it
    @Test
    fun anXNothingDefinesIsChosenAsItIsPaid() = scenario {
        alice.battlefield(vigil, elf, "Plains", "Plains", "Plains")
        alice.hand(doom)
        alice.castsWithoutCosts(doom, target = elf)
        resolve()
        resolve()
        answer(Answer.YesNo(true))
        val x = decision<Decision.ChooseNumber>()
        assertEquals(0 to 3, x.min to x.max)
        answer(Answer.Number(2))
        answer(Answer.ManaPayment(decision<Decision.ManaPayment>().suggestion))
        assertEquals(null, pending)
        expect { alice.life shouldBe 22 }
        assertEquals(2, state.battlefield.count { state.objOrThrow(it).tapped })
    }

    // CR 107.3f: with nothing to pay with, X can only be 0: nothing is asked, and X life is none
    @Test
    fun withNothingToPayWithXIsZero() = scenario {
        alice.battlefield(vigil, elf)
        alice.hand(doom)
        alice.castsWithoutCosts(doom, target = elf)
        resolve()
        resolve()
        answer(Answer.YesNo(true))
        assertEquals(null, pending)
        expect { alice.life shouldBe 20 }
    }

    /** [name] with "{0}: You may pay [cost]. If you do, you gain 3 life." */
    private fun payer(name: String, cost: String?, type: String, part: CostPart): String {
        val base = Cards.definition(Cards.invent("$name Base", cost, type, "{0}: You gain 3 life.", if (type.startsWith("Creature")) "1/1" else null))
        val face = base.faces.single()
        val ability = face.abilities.single() as Ability.Activated
        val steps = listOf(
            Step.Pay(Cost(listOf(part)), payer = Players.You, bind = "paid"),
            Step.If(Condition.Did("paid"), listOf(Step.Do(Effect.GainLife(Players.You, Value.Fixed(3))))),
        )
        return Cards.define(name, base.copy(faces = listOf(face.copy(name = name,
            abilities = listOf(ability.copy(instructions = ability.instructions.copy(steps = steps)))))))
    }

    // CR 107.3h: "its mana cost" is the source's, here {1}{G}
    @Test
    fun itsManaCostIsTheSourcesManaCost() = scenario {
        val sprite = payer("Echo Sprite", "{1}{G}", "Creature — Faerie", CostPart.ItsManaCost)
        alice.battlefield(sprite, "Forest", "Forest")
        alice.activates(sprite)
        resolve()
        answer(Answer.YesNo(true))
        val payment = decision<Decision.ManaPayment>()
        assertEquals("{1}{G}", payment.cost.toString())
        answer(Answer.ManaPayment(payment.suggestion))
        expect { alice.life shouldBe 23 }
    }

    // CR 118.6, 202.1b: a source with no mana cost (a land) makes "its mana cost" unpayable: it isn't paid, and "if you do" doesn't happen
    @Test
    fun noManaCostCantBePaid() = scenario {
        val ruin = payer("Echo Ruin", null, "Land", CostPart.ItsManaCost)
        alice.battlefield(ruin, "Forest")
        alice.activates(ruin)
        resolve()
        assertEquals(null, pending)
        expect { alice.life shouldBe 20 }
    }

    // A cost part the definition can't express (Engine.manualParts names it) can't be paid, so nothing is asked by hand
    @Test
    fun aManualCostPartCantBePaid() = scenario {
        val oddity = payer("Echo Oddity", "{1}", "Artifact", CostPart.Manual("Pay the odd price"))
        alice.battlefield(oddity)
        alice.activates(oddity)
        resolve()
        assertEquals(null, pending)
        expect { alice.life shouldBe 20 }
    }
}
