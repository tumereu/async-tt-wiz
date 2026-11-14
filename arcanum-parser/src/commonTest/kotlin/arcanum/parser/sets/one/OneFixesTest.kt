package arcanum.parser.sets.one

import arcanum.api.*
import arcanum.parser.cube.*
import kotlin.test.Test

/** ONE fixes: parser misreads set work found, read generally. */
class OneFixesTest {
    private val thatOpponentControls = ObjectFilter.ControlledBy(PlayerFilter.Is(Players.Target("opponent")))
    private val thatPlayerControls = ObjectFilter.ControlledBy(PlayerFilter.Is(Players.Target("player")))

    // CR 608.2c: in a spell, "that player" after "Choose target opponent" is the chosen opponent (a spell has no event player), so X counts
    // that opponent's artifacts
    @Test
    fun inquisitiveDrove() = card(
        name = "Inquisitive Drove",
        cost = "{3}{G}",
        type = "Instant",
        text = "Choose target opponent. You create X 3/3 green Beast creature tokens, where X is the number of artifacts that player controls.",
    ) parsesTo abilities(
        spell(doing(Effect.CreateToken(creatureToken("Beast", listOf(Color.Green), 3, 3), count(and(artifact, thatOpponentControls)), Players.You)),
            targets = listOf(targetOpponent)),
    )

    // CR 608.2c: the same "that player" read by a sentence rule (not a "where X is"): the target player
    @Test
    fun covetousStampede() = card(
        name = "Covetous Stampede",
        cost = "{2}{R}",
        type = "Sorcery",
        text = "Choose target player. Destroy all artifacts that player controls.",
    ) parsesTo abilities(
        spell(destroy(Objects.Each(and(artifact, thatPlayerControls))), targets = listOf(targetPlayer)),
    )
}
