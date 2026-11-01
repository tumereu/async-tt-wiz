package arcanum.engine.resolution

import arcanum.api.Color
import arcanum.engine.cards.Cards
import arcanum.engine.decision.Answer
import arcanum.engine.scenario.scenario
import kotlin.test.Test
import kotlin.test.assertFailsWith

/** Values a resolving effect fixes or reads as it happens: a chosen colour, the amount of an event it replaces. */
class ChosenValuesTest {
    private val bear = Cards.invent("Mossback Cub", "{1}{G}", "Creature — Bear", "", "2/2")

    // CR 608.2h, 702.16b: "protection from the color of your choice" keeps the colour chosen on resolution
    @Test
    fun protectionFromTheChosenColor() = scenario {
        alice.battlefield("Bestower of Sigils", bear)
        bob.battlefield("Mountain")
        bob.hand("Storm Spark")

        alice.activatesWithoutCosts("Bestower of Sigils", target = bear)
        resolve()
        answer(Answer.Option("a color"))
        answer(Answer.ColorChoice(Color.Red))
        alice.passes()

        assertFailsWith<IllegalArgumentException> { bob.casts("Storm Spark", target = bear) }
    }

    // CR 614.1a: "those tokens plus that many 1/1 Squirrels": as many as the tokens being created
    @Test
    fun thatManyMoreTokens() = scenario {
        alice.battlefield("Nutcracker, Squirrel Marshal", "Tombcracker")

        alice.activatesWithoutCosts("Tombcracker")
        resolve()

        expect {
            alice.battlefield has "Zombie Token"
            alice.battlefield.names.count { it == "Squirrel Token" } shouldBe 1
        }
    }
}
