package arcanum.parser.sets.inr

import arcanum.api.Choice
import arcanum.api.Comparison
import arcanum.api.Condition
import arcanum.api.Effect
import arcanum.api.Objects
import arcanum.api.Players
import arcanum.api.Step
import arcanum.api.Value
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

/** INR fix chunk: wordings found unread or misread while the set's chunks were read. */
class InrFixesTest {
    // CR 701.21a: "If you do" holds only when a creature was sacrificed; CR 608.2h: "that creature" is the sacrificed one as it last existed
    // on the battlefield
    @Test
    fun grimTithe() = card(
        name = "Grim Tithe",
        cost = "{1}{B}",
        type = "Sorcery",
        text = "Sacrifice a creature. If you do, you gain life equal to that creature's toughness.",
    ) parsesTo abilities(
        spell(
            Step.Choose(Choice.Objects(and(creature, you), n(1), n(1)), bind = "sacrificed", chooser = Players.You),
            doing(Effect.Sacrifice(Objects.Bound("sacrificed"), preBind = "sacrificed creature", postBind = "sacrificed")),
            Step.If(Condition.Compare(Value.Count(Objects.Bound("sacrificed")), Comparison.AtLeast, n(1)), then = listOf(
                doing(Effect.GainLife(Players.You, toughness(Objects.Bound("sacrificed creature"))))))),
    )
}
