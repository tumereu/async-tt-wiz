package arcanum.parser.sets.isd

import arcanum.api.*
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

/*
 * ISD fixes: cards the set's chunks left for concepts the API lacked.
 */
class IsdFixesTest {
    private val slime = CounterKind.Named("slime")

    // CR 601.2b, 601.2f: the card exiled for the cost is chosen before the total cost is known; "its mana cost" is that card's;
    // "that card" in the effect is the card exiled (CR 607.2, 707.2: the copy's values are the exiled card's)
    @Test
    fun returnFromTheLedge() = card(
        name = "Return from the Ledge", cost = "{4}{U}{U}", type = "Enchantment",
        text = "Exile a creature card from your graveyard and pay its mana cost: Create a token that's a copy of that card. Activate only as a sorcery.",
    ) parsesTo abilities(
        activated(
            cost(
                CostPart.Exile(CostObjects.Choose(creature, count = n(1)), from = Zone.Graveyard, postBind = "exiled"),
                CostPart.ManaCostOf(Objects.Bound("exiled")),
            ),
            create(TokenSpec.CopyOf(Objects.Bound("exiled"), emptyList())),
            rules = asSorcery,
        ),
    )

    // CR 201.5a: the token's ability names the object that created it (its counters, not the token's); CR 604.3: a characteristic-
    // defining ability; CR 111.4: the token has the ability as its text
    @Test
    fun oozeGutter() = card(
        name = "Ooze Gutter", cost = "{4}{G}", type = "Enchantment",
        text = "Whenever a nontoken creature you control dies, put a slime counter on this enchantment, then create a green Ooze creature token with \"This token's power and toughness are each equal to the number of slime counters on Ooze Gutter.\"",
    ) parsesTo abilities(
        triggered(dies(and(not(token), creature, you)),
            counters(Objects.Self, 1, slime),
            create(TokenSpec.Defined(FaceDefinition("Ooze Token", colorIndicator = listOf(Color.Green), types = listOf(CardType.Creature),
                subtypes = listOf(Subtype("Ooze")), power = PrintedNumber.Star(), toughness = PrintedNumber.Star(),
                abilities = listOf(Value.Of(NumericProperty.Counters(slime), Objects.Creator, Combine.Single).let { v ->
                    static(Mechanic.SetPowerToughness(v, v), characteristicDefining = true)
                })))),
        ),
    )
}
