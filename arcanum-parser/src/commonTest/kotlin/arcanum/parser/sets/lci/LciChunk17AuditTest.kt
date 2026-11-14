package arcanum.parser.sets.lci

import arcanum.api.*
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

/** LCI chunk 17: audit findings. */
class LciChunk17AuditTest {
    // CR 700.2: "a creature card and/or a land card": up to one of each (not one card of either type), both put into hand
    @Test
    fun beneathTheShadowOfEons() = card(
        name = "Beneath the Shadow of Eons",
        cost = "{2}{G}",
        type = "Instant",
        text = "Reveal the top four cards of your library. You may put a creature card and/or a land card from among them into your hand. Put the rest into your graveyard.",
    ) parsesTo abilities(
        spell(
            Step.Do(Effect.Reveal(Objects.LibraryCards(Players.You, n(4), fromTop = true), bind = "seen")),
            Step.Choose(Choice.Objects(creature, n(0), n(1), from = Objects.Bound("seen")), bind = "chosen", chooser = Players.You),
            Step.Choose(Choice.Objects(land, n(0), n(1), from = Objects.Except(Objects.Bound("seen"), Objects.Bound("chosen"))),
                bind = "chosen second", chooser = Players.You),
            Step.Bind(Objects.Union(listOf(Objects.Bound("chosen"), Objects.Bound("chosen second"))), "picked both"),
            move(Objects.Bound("picked both"), Zone.Hand),
            move(Objects.Except(Objects.Bound("seen"), Objects.Bound("picked both")), Zone.Graveyard),
        ),
    )
}
