package arcanum.parser.sets.snc

import arcanum.api.*
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

/** SNC fixes: casualty X with the copy's exceptions. */
class SncFixesTest {
    // CR 702.153a, 107.3 (X is the power of the creature sacrificed), 707.9b (the copy isn't legendary), 306.5b (starting loyalty)
    @Test
    fun obNixilisTheAdversary() = card(
        name = "Ob Vexilis, the Contrarian",
        cost = "{1}{B}{R}",
        type = "Legendary Planeswalker — Nixilis",
        loyalty = "3",
        text = """
            Casualty X. The copy isn't legendary and has starting loyalty X. (As you cast this spell, you may sacrifice a creature with power X. When you do, copy this spell. The copy becomes a token.)
            [+1]: You gain 2 life.
        """,
    ) parsesTo abilities(
        Keyword.CasualtyX(listOf(
            CopyException.Types(removeSupertypes = listOf(Supertype.Legendary)),
            CopyException.Loyalty(Value.Of(NumericProperty.Power, Objects.Bound(KeywordExpansions.CASUALTY_SACRIFICED), Combine.Single)))),
        activated(cost(CostPart.Loyalty(n(1))), Step.Do(Effect.GainLife(Players.You, n(2)))),
    )
}
