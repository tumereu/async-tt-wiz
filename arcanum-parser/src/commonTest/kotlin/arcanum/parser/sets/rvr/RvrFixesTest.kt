package arcanum.parser.sets.rvr

import arcanum.api.Choice
import arcanum.api.Duration
import arcanum.api.Effect
import arcanum.api.ObjectFilter
import arcanum.api.Objects
import arcanum.api.PlayerFilter
import arcanum.api.Players
import arcanum.api.Recipients
import arcanum.api.Step
import arcanum.api.Zone
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

    // CR 601.2c, CR 801.5a's example: an opponent chooses the target ("of an opponent's choice"), the controller picking which opponent
    @Test
    fun mausoleumTurnkeyLookalike() = card(
        name = "Cryptwarden Turnkey", cost = "{3}{B}", type = "Creature — Ogre Rogue", pt = "3/2",
        text = "When this creature enters, return target creature card of an opponent's choice from your graveyard to your hand.",
    ) parsesTo abilities(
        triggered(entersSelf, move(target("card"), Zone.Hand),
            targets = listOf(slot("card", inYourGraveyard(creature)).copy(chosenBy = PlayerFilter.Opponent))),
    )

    // CR 614.9: a redirection shield for the next 1 damage; "another target creature" is a different object from the first (CR 115.3)
    @Test
    fun caromLookalike() = card(
        name = "Glancing Caroms", cost = "{1}{W}", type = "Instant",
        text = """
            The next 1 damage that would be dealt to target creature this turn is dealt to another target creature instead.
            Draw a card.
        """,
    ) parsesTo abilities(
        spell(
            doing(Effect.PreventDamage(Recipients(objects = target("creature")), n(1), ObjectFilter.Any, Duration.EndOfTurn,
                redirectTo = Recipients(objects = target("second creature")))),
            draw(1),
            targets = listOf(slot("creature", creature), slot("second creature", creature).copy(distinctFrom = listOf("creature"))),
        ),
    )
}
