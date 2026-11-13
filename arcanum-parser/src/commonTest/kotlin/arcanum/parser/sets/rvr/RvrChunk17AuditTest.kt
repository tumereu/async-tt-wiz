package arcanum.parser.sets.rvr

import arcanum.api.*
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

/** Ravnica Remastered chunk 17: the one card the audit doubted. */
class RvrChunk17AuditTest {
    // CR 701.20a (reveal), 701.17a (the revealed cards, found land included, go to the graveyard): the model puts both the
    // matching card ("found") and the other revealed cards ("rest") into the graveyard, and leaves the unrevealed remainder alone
    @Test
    fun balustradeSpyLookalike() = card(
        name = "Cellar Spy", cost = "{3}{B}", type = "Creature — Vampire Rogue", pt = "2/3",
        text = """
            Flying
            When this creature enters, target player reveals cards from the top of their library until they reveal a land card, then puts those cards into their graveyard.
        """,
    ) parsesTo abilities(
        Keyword.Flying,
        triggered(entersSelf,
            Step.Do(Effect.RevealUntil(ObjectFilter.Type(CardType.Land), Players.Target("player"), n(1), reveal = true,
                foundBind = "found", restBind = "rest")),
            Step.Do(Effect.Move(Objects.Union(listOf(Objects.Bound("found"), Objects.Bound("rest"))), Destination(Zone.Graveyard))),
            targets = listOf(targetPlayer)),
    )
}
