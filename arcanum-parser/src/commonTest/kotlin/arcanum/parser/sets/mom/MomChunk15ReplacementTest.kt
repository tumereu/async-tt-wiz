package arcanum.parser.sets.mom

import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

/** MOM chunk 15: the ten gain-lands, each "enters tapped" (CR 614.1d), a life-gain enters trigger and a two-colour mana ability. */
class MomChunk15ReplacementTest {
    private fun gainLand(name: String, a: String, b: String) = card(
        name = name,
        cost = null,
        type = "Land",
        text = """
            This land enters tapped.
            When this land enters, you gain 1 life.
            {T}: Add {$a} or {$b}.
        """,
    ) parsesTo abilities(
        entersTapped,
        triggered(entersSelf, gainLife(1)),
        addEither(a, b),
    )

    @Test fun ashmoorHollow() = gainLand("Ashmoor Hollow", "B", "R")
    @Test fun mossgroveSands() = gainLand("Mossgrove Sands", "G", "W")
    @Test fun dimwaterCove() = gainLand("Dimwater Cove", "U", "B")
    @Test fun rootdeepGlen() = gainLand("Rootdeep Glen", "B", "G")
    @Test fun cragwindRise() = gainLand("Cragwind Rise", "R", "G")
    @Test fun tallowBarrens() = gainLand("Tallow Barrens", "W", "B")
    @Test fun brinewashCliffs() = gainLand("Brinewash Cliffs", "U", "R")
    @Test fun emberdeepFalls() = gainLand("Emberdeep Falls", "G", "U")
    @Test fun quietShoal() = gainLand("Quiet Shoal", "W", "U")
    @Test fun sunlitDune() = gainLand("Sunlit Dune", "R", "W")
}
