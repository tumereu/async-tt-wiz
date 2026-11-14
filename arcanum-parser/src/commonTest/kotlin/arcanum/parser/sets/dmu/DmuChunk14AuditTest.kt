package arcanum.parser.sets.dmu

import arcanum.api.*
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

/** Dominaria United chunk 14: a card the audit doubted. */
class DmuChunk14AuditTest {
    // CR 106.1b, 605.1a: "one mana of any color" is ManaKind.Choose with no colour list (all five colours); CR 603.1: enters trigger
    @Test
    fun skyfallCache() = card(
        name = "Skyfall Cache", cost = "{5}", type = "Artifact",
        text = """
            When this artifact enters, it deals 2 damage to any target.
            {T}: Add one mana of any color.
        """,
    ) parsesTo abilities(
        triggered(entersSelf, damage(2, toAny), targets = listOf(anyTarget)),
        activated(cost(tap), Step.Do(Effect.AddMana(ManaProduction(ManaKind.Choose(Value.Fixed(1), colors = null)), Players.You))),
    )
}
