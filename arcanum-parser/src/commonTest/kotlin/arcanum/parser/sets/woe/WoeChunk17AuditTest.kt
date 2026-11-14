package arcanum.parser.sets.woe

import arcanum.api.*
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

/** Wilds of Eldraine chunk 17: audit of cards read in earlier chunks. */
class WoeChunk17AuditTest {
    // CR 608.2h, 111.2: "its controller" is the destroyed permanent's controller as it last was on the battlefield
    // (the object-based player reference resolves through last known information once the target has left)
    @Test
    fun strokeOfTwilight() = card(
        name = "Stroke of Twilight",
        cost = "{2}{W}",
        type = "Instant",
        text = "Destroy target nonland permanent. Its controller creates a 1/1 white Human creature token.",
    ) parsesTo abilities(
        spell(
            destroy(target("permanent")),
            Step.Do(Effect.CreateToken(creatureToken("Human", listOf(Color.White), 1, 1), n(1),
                Players.ControllerOf(target("permanent")))),
            targets = listOf(slot("permanent", and(nonland, permanentFilter))),
        ),
    )
}
