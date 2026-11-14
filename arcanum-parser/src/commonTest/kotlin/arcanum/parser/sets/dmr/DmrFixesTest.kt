package arcanum.parser.sets.dmr

import arcanum.api.CardType
import arcanum.api.Comparison
import arcanum.api.Condition
import arcanum.api.CostChange
import arcanum.api.EventPattern
import arcanum.api.ManaCost
import arcanum.api.Measure
import arcanum.api.Mechanic
import arcanum.api.Modification
import arcanum.api.ObjectFilter
import arcanum.api.Objects
import arcanum.api.PlayerFilter
import arcanum.api.Players
import arcanum.api.Value
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

/** DMR fix step: wordings the set's chunks left unread. */
class DmrFixesTest {
    // CR 106.12, 614.1a: a replacement of the mana a land tapped for two or more mana produces; CR 601.2f: a cost increase counting the
    // other spells the spell's own caster cast this turn (the spell being cast is the tested object)
    @Test
    fun mufflingOrb() = card(
        name = "Muffling Orb",
        cost = "{2}",
        type = "Artifact",
        text = """
            If a land is tapped for two or more mana, it produces {C} instead of any other type and amount.
            Each spell a player casts costs {1} more to cast for each other spell that player has cast this turn.
        """,
    ) parsesTo abilities(
        static(Mechanic.Replacement(EventPattern.Tap(ObjectFilter.Type(CardType.Land), forMana = true),
            modify = listOf(Modification.ProducesInstead(ManaCost.parse("{C}")!!)),
            condition = Condition.Compare(Value.EventAmount, Comparison.AtLeast, Value.Fixed(2)))),
        static(Mechanic.CostChange(spellFilter, CostChange.More(ManaCost.parse("{1}")!!,
            Value.EventsThisTurn(EventPattern.Cast(ObjectFilter.Not(ObjectFilter.Is(Objects.Tested)),
                PlayerFilter.Is(Players.ControllerOf(Objects.Tested))), Measure.Events)))),
    )
}
