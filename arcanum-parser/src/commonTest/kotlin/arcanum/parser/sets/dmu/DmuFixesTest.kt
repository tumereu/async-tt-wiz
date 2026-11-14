package arcanum.parser.sets.dmu

import arcanum.api.Ability
import arcanum.api.Affected
import arcanum.api.Comparison
import arcanum.api.CostObjects
import arcanum.api.CostPart
import arcanum.api.Effect
import arcanum.api.EventPattern
import arcanum.api.Instructions
import arcanum.api.Mechanic
import arcanum.api.Modification
import arcanum.api.ObjectFilter
import arcanum.api.Objects
import arcanum.api.PlayerFilter
import arcanum.api.Players
import arcanum.api.Step
import arcanum.api.Value
import arcanum.api.Zone
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

/** Dominaria United fixes: wordings the set's fix step reads (the oracle text of Karn's Sylex). */
class DmuFixesTest {
    // CR 614.1c: enters tapped; CR 119.4, 601.2h, 602.2b: no player pays life to cast a spell or activate a non-mana ability (mana abilities
    // excepted, CR 605.1a); CR 107.3, 602.2b: X announced as the ability is activated; CR 307.5: only as a sorcery
    @Test
    fun karnsSylex() = card(
        name = "Sylex of Ending Echoes",
        cost = "{3}",
        type = "Legendary Artifact",
        text = """
            Sylex of Ending Echoes enters tapped.
            Players can't pay life to cast spells or to activate abilities that aren't mana abilities.
            {X}, {T}, Exile Sylex of Ending Echoes: Destroy each nonland permanent with mana value X or less. Activate only as a sorcery.
        """,
    ) parsesTo abilities(
        static(Mechanic.Replacement(EventPattern.ZoneChange(ObjectFilter.Is(Objects.Self), to = Zone.Battlefield), modify = listOf(Modification.EntersTapped))),
        Ability.Static(listOf(Mechanic.CantPayLifeToCastOrActivate(exceptManaAbilities = true)), affects = Affected.Players(Players.Each(PlayerFilter.Any))),
        Ability.Activated(cost(mana("{X}"), tap, CostPart.Exile(CostObjects.Self, from = Zone.Battlefield)), Instructions(
            steps = listOf(Step.Do(Effect.Destroy(Objects.Each(and(nonland, permanentFilter, ObjectFilter.ManaValue(Comparison.AtMost, Value.Announced("X"))))))),
            announced = listOf("X")), asSorcery),
    )
}
