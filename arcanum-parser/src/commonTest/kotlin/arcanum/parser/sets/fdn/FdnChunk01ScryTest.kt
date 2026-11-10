package arcanum.parser.sets.fdn

import arcanum.api.Ability
import arcanum.api.CardType
import arcanum.api.CostObjects
import arcanum.api.CostPart
import arcanum.api.Color
import arcanum.api.ColorRef
import arcanum.api.Comparison
import arcanum.api.Condition
import arcanum.api.Destination
import arcanum.api.Effect
import arcanum.api.EventPattern
import arcanum.api.Instructions
import arcanum.api.ObjectFilter
import arcanum.api.Objects
import arcanum.api.PlayerFilter
import arcanum.api.Players
import arcanum.api.Step
import arcanum.api.Trigger
import arcanum.api.TurnStep
import arcanum.api.Value
import arcanum.api.Zone
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

/** The FDN cards that scry: the invented names carry the oracle text of Charming Prince, Mazemind Tome, Devout Decree, Opt and the Temples. */
class FdnChunk01ScryTest {
    private val pageCounter = arcanum.api.CounterKind.Named("page")

    // CR 700.2 (modes), CR 603.7 (the delayed return), CR 400.7 (it comes back a new object)
    @Test
    fun gallantViscount() = card(
        name = "Gallant Viscount",
        cost = "{1}{W}",
        type = "Creature — Human Noble",
        pt = "2/2",
        text = """
            When this creature enters, choose one —
            • Scry 2.
            • You gain 3 life.
            • Exile another target creature you own. Return it to the battlefield under your control at the beginning of the next end step.
        """,
    ) parsesTo abilities(
        Ability.Triggered(entersSelf, Instructions(modes = modes(
            mode(scry(2)),
            mode(gainLife(3)),
            mode(
                doing(Effect.Move(target("creature"), Destination(Zone.Exile), postBind = "exiled")),
                doing(Effect.CreateDelayedTrigger(Trigger.On(EventPattern.StepBegins(TurnStep.End, PlayerFilter.Any)),
                    Instructions(steps = listOf(doing(Effect.Move(Objects.Bound("exiled"), Destination(Zone.Battlefield, controller = Players.You))))))),
                targets = listOf(slot("creature", and(not(ObjectFilter.Is(Objects.Self)), creature, ObjectFilter.OwnedBy(PlayerFilter.You)))),
            ),
        ))),
    )

    // CR 118.3 (a cost that adds counters), CR 603.8 (state trigger), CR 400.7 (gone before it resolves, it isn't exiled and "if you do" fails)
    @Test
    fun recollectionCodex() = card(
        name = "Recollection Codex",
        cost = "{2}",
        type = "Artifact — Book",
        text = """
            {T}, Put a page counter on this artifact: Scry 1. (Look at the top card of your library. You may put that card on the bottom.)
            {2}, {T}, Put a page counter on this artifact: Draw a card.
            When there are four or more page counters on this artifact, exile it. If you do, you gain 4 life.
        """,
    ) parsesTo abilities(
        activated(cost(tap, CostPart.PutCounters(pageCounter, n(1), CostObjects.Self)), scry(1)),
        activated(cost(mana("{2}"), tap, CostPart.PutCounters(pageCounter, n(1), CostObjects.Self)), draw(1)),
        Ability.Triggered(
            Trigger.State(Condition.ObjectMatches(Objects.Self, ObjectFilter.HasCounter(pageCounter, atLeast = 4))),
            Instructions(steps = listOf(
                doing(Effect.Move(Objects.Self, Destination(Zone.Exile), postBind = "exiled")),
                Step.If(Condition.Compare(Value.Count(Objects.Bound("exiled")), Comparison.AtLeast, n(1)), then = listOf(gainLife(4))),
            )),
        ),
    )

    // CR 115.1 (the target), CR 701.22 (scry)
    @Test
    fun austereMandate() = card(
        name = "Austere Mandate",
        cost = "{1}{W}",
        type = "Sorcery",
        text = "Exile target creature or planeswalker that's black or red. Scry 1. (Look at the top card of your library. You may put that card on the bottom.)",
    ) parsesTo abilities(
        spell(
            doing(Effect.Move(target("permanent"), Destination(Zone.Exile))),
            scry(1),
            targets = listOf(slot("permanent", and(
                or(creature, ObjectFilter.Type(CardType.Planeswalker)),
                or(ObjectFilter.OfColor(ColorRef.Literal(Color.Black)), ObjectFilter.OfColor(ColorRef.Literal(Color.Red))),
            ))),
        ),
    )

    // CR 701.22 (scry), CR 608.2c (the spell's lines are followed in order)
    @Test
    fun peekForward() = card(
        name = "Peek Forward",
        cost = "{U}",
        type = "Instant",
        text = """
            Scry 1. (Look at the top card of your library. You may put that card on the bottom.)
            Draw a card.
        """,
    ) parsesTo abilities(
        spell(scry(1), draw(1)),
    )

    // CR 614.1c (enters tapped), CR 603.6a (the trigger), CR 106.1 (one color of the two)
    @Test
    fun sanctumOfAbandon() = card(
        name = "Sanctum of Abandon",
        cost = null,
        type = "Land",
        text = """
            This land enters tapped.
            When this land enters, scry 1. (Look at the top card of your library. You may put that card on the bottom.)
            {T}: Add {R} or {G}.
        """,
    ) parsesTo abilities(
        entersTapped,
        triggered(entersSelf, scry(1)),
        addEither("R", "G"),
    )

    // CR 614.1c (enters tapped), CR 603.6a (the trigger), CR 106.1 (one color of the two)
    @Test
    fun sanctumOfDeceit() = card(
        name = "Sanctum of Deceit",
        cost = null,
        type = "Land",
        text = """
            This land enters tapped.
            When this land enters, scry 1. (Look at the top card of your library. You may put that card on the bottom.)
            {T}: Add {U} or {B}.
        """,
    ) parsesTo abilities(
        entersTapped,
        triggered(entersSelf, scry(1)),
        addEither("U", "B"),
    )

    // CR 614.1c (enters tapped), CR 603.6a (the trigger), CR 106.1 (one color of the two)
    @Test
    fun sanctumOfEnlightenment() = card(
        name = "Sanctum of Enlightenment",
        cost = null,
        type = "Land",
        text = """
            This land enters tapped.
            When this land enters, scry 1. (Look at the top card of your library. You may put that card on the bottom.)
            {T}: Add {W} or {U}.
        """,
    ) parsesTo abilities(
        entersTapped,
        triggered(entersSelf, scry(1)),
        addEither("W", "U"),
    )

    // CR 614.1c (enters tapped), CR 603.6a (the trigger), CR 106.1 (one color of the two)
    @Test
    fun sanctumOfEpiphany() = card(
        name = "Sanctum of Epiphany",
        cost = null,
        type = "Land",
        text = """
            This land enters tapped.
            When this land enters, scry 1. (Look at the top card of your library. You may put that card on the bottom.)
            {T}: Add {U} or {R}.
        """,
    ) parsesTo abilities(
        entersTapped,
        triggered(entersSelf, scry(1)),
        addEither("U", "R"),
    )

    // CR 614.1c (enters tapped), CR 603.6a (the trigger), CR 106.1 (one color of the two)
    @Test
    fun sanctumOfMalady() = card(
        name = "Sanctum of Malady",
        cost = null,
        type = "Land",
        text = """
            This land enters tapped.
            When this land enters, scry 1. (Look at the top card of your library. You may put that card on the bottom.)
            {T}: Add {B} or {G}.
        """,
    ) parsesTo abilities(
        entersTapped,
        triggered(entersSelf, scry(1)),
        addEither("B", "G"),
    )

    // CR 614.1c (enters tapped), CR 603.6a (the trigger), CR 106.1 (one color of the two)
    @Test
    fun sanctumOfMalice() = card(
        name = "Sanctum of Malice",
        cost = null,
        type = "Land",
        text = """
            This land enters tapped.
            When this land enters, scry 1. (Look at the top card of your library. You may put that card on the bottom.)
            {T}: Add {B} or {R}.
        """,
    ) parsesTo abilities(
        entersTapped,
        triggered(entersSelf, scry(1)),
        addEither("B", "R"),
    )

    // CR 614.1c (enters tapped), CR 603.6a (the trigger), CR 106.1 (one color of the two)
    @Test
    fun sanctumOfMystery() = card(
        name = "Sanctum of Mystery",
        cost = null,
        type = "Land",
        text = """
            This land enters tapped.
            When this land enters, scry 1. (Look at the top card of your library. You may put that card on the bottom.)
            {T}: Add {G} or {U}.
        """,
    ) parsesTo abilities(
        entersTapped,
        triggered(entersSelf, scry(1)),
        addEither("G", "U"),
    )

    // CR 614.1c (enters tapped), CR 603.6a (the trigger), CR 106.1 (one color of the two)
    @Test
    fun sanctumOfPlenty() = card(
        name = "Sanctum of Plenty",
        cost = null,
        type = "Land",
        text = """
            This land enters tapped.
            When this land enters, scry 1. (Look at the top card of your library. You may put that card on the bottom.)
            {T}: Add {G} or {W}.
        """,
    ) parsesTo abilities(
        entersTapped,
        triggered(entersSelf, scry(1)),
        addEither("G", "W"),
    )

    // CR 614.1c (enters tapped), CR 603.6a (the trigger), CR 106.1 (one color of the two)
    @Test
    fun sanctumOfSilence() = card(
        name = "Sanctum of Silence",
        cost = null,
        type = "Land",
        text = """
            This land enters tapped.
            When this land enters, scry 1. (Look at the top card of your library. You may put that card on the bottom.)
            {T}: Add {W} or {B}.
        """,
    ) parsesTo abilities(
        entersTapped,
        triggered(entersSelf, scry(1)),
        addEither("W", "B"),
    )

    // CR 614.1c (enters tapped), CR 603.6a (the trigger), CR 106.1 (one color of the two)
    @Test
    fun sanctumOfTriumph() = card(
        name = "Sanctum of Triumph",
        cost = null,
        type = "Land",
        text = """
            This land enters tapped.
            When this land enters, scry 1. (Look at the top card of your library. You may put that card on the bottom.)
            {T}: Add {R} or {W}.
        """,
    ) parsesTo abilities(
        entersTapped,
        triggered(entersSelf, scry(1)),
        addEither("R", "W"),
    )
}
