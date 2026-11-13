package arcanum.parser.sets.rvr

import arcanum.api.Choice
import arcanum.api.Effect
import arcanum.api.ObjectFilter
import arcanum.api.Objects
import arcanum.api.PlayerFilter
import arcanum.api.Players
import arcanum.api.Step
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

/** Ravnica Remastered fix step: general parser fixes found during the set's chunks. */
class RvrFixesTest {
    // CR 608.2d: "you may" is the controller's choice; CR 701.21a: once they choose to, the opponent sacrifices and can't decline
    // (the zero-or-one reading of "you may put counters on a creature you control" is only for a choice you make)
    @Test
    fun predatoryNightstalkerLookalike() = card(
        name = "Duskprowl Stalker", cost = "{3}{B}{B}", type = "Creature — Cat Horror", pt = "3/2",
        text = "When this creature enters, you may have target opponent sacrifice a creature of their choice.",
    ) parsesTo abilities(
        triggered(
            entersSelf,
            Step.May(listOf(
                Step.Choose(Choice.Objects(and(creature, ObjectFilter.ControlledBy(PlayerFilter.Is(Players.Target("opponent")))), n(1), n(1)),
                    bind = "sacrificed", chooser = Players.Target("opponent")),
                doing(Effect.Sacrifice(Objects.Bound("sacrificed"))),
            ), Players.You),
            targets = listOf(targetOpponent),
        ),
    )
}
