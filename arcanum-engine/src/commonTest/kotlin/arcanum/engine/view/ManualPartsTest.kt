package arcanum.engine.view

import arcanum.api.Ability
import arcanum.api.CardDefinition
import arcanum.api.CardType
import arcanum.api.Cost
import arcanum.api.CostPart
import arcanum.api.FaceDefinition
import arcanum.api.Instructions
import arcanum.api.Layout
import arcanum.api.ManaCost
import arcanum.api.Players
import arcanum.api.Step
import arcanum.api.Value
import arcanum.engine.Engine
import arcanum.parser.OracleCard
import arcanum.parser.OracleParser
import kotlin.test.Test
import kotlin.test.assertEquals

/** Engine.manualParts: what the engine leaves to the players in a card definition; invented cards, oracle-style wording. */
class ManualPartsTest {
    private fun card(text: String) = CardDefinition(Layout.Normal,
        listOf(OracleParser.parse(OracleCard("Mossback Cub", "{1}{G}", "Creature — Bear", text, power = "2", toughness = "2"))))

    private fun spell(vararg steps: Step) = CardDefinition(Layout.Normal, listOf(FaceDefinition("Odd Rite", ManaCost.parse("{X}{U}"),
        types = listOf(CardType.Instant), abilities = listOf(Ability.Spell(Instructions(steps = steps.toList(), announced = listOf("X")))))))

    @Test fun aFullyReadCardHasNoManualParts() {
        assertEquals(emptyList(), Engine.manualParts(card("Trample\nWhen this creature enters, you gain 2 life.")))
    }

    @Test fun unreadTextAndManualStepsAreLeftToThePlayers() {
        assertEquals(listOf("unread text: Whenever the moon is full, this creature dances a slow jig."),
            Engine.manualParts(card("Whenever the moon is full, this creature dances a slow jig.")))
        assertEquals(listOf("by hand: Do the odd thing"), Engine.manualParts(spell(Step.Manual("Do the odd thing"))))
    }

    // CR 701.48a: the engine plays learn by its expansion, the host bringing the cards outside the game
    @Test fun learnIsNotLeftToThePlayers() {
        assertEquals(emptyList(), Engine.manualParts(spell(Step.Do(arcanum.api.Effect.Action(arcanum.api.KeywordAction.Learn)))))
    }

    // CR 107.3: a cost paid on resolution with an X the definition doesn't set is left to the players; set, it isn't
    @Test fun aResolutionCostNeedsItsX() {
        val x = ManaCost.parse("{X}")!!
        assertEquals(listOf("by hand: pay {X}"), Engine.manualParts(spell(Step.Pay(Cost(listOf(CostPart.Mana(x))), payer = Players.You))))
        assertEquals(emptyList(), Engine.manualParts(spell(Step.Pay(Cost(listOf(CostPart.Mana(x, x = Value.Announced("X")))), payer = Players.You))))
    }
}
