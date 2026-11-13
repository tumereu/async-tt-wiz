package arcanum.parser.sets.mom

import arcanum.api.Choice
import arcanum.api.Objects
import arcanum.api.Players
import arcanum.api.Step
import arcanum.api.Zone
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

/** MOM fix chunk: parser fixes found while working the set. */
class MomFixesTest {
    // CR 608.2c: chosen as it resolves, not targeted; CR 401.4: several cards put on top at once are ordered by their owner as they
    // go (the engine asks), so the wording reads for more than one card
    @Test
    fun momentideExhumer() = card(
        name = "Momentide Exhumer",
        cost = "{3}{B}",
        type = "Creature — Zombie",
        pt = "3/3",
        text = "When this creature enters, put up to two creature cards from your graveyard on top of your library.",
    ) parsesTo abilities(
        triggered(entersSelf,
            Step.Choose(Choice.Objects(inYourGraveyard(creature), n(0), n(2)), bind = "chosen", chooser = Players.You),
            move(Objects.Bound("chosen"), Zone.Library, position = 0)),
    )
}
