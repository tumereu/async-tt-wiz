package arcanum.parser.sets.fdn

import arcanum.api.Ability
import arcanum.api.ActivationRules
import arcanum.api.Affected
import arcanum.api.Choice
import arcanum.api.Color
import arcanum.api.ColorRef
import arcanum.api.Combine
import arcanum.api.Comparison
import arcanum.api.Condition
import arcanum.api.CostObjects
import arcanum.api.CostPart
import arcanum.api.CounterKind
import arcanum.api.Destination
import arcanum.api.Duration
import arcanum.api.Effect
import arcanum.api.EventPattern
import arcanum.api.ManaCost
import arcanum.api.ManaKind
import arcanum.api.ManaProduction
import arcanum.api.Mechanic
import arcanum.api.Modification
import arcanum.api.NumericProperty
import arcanum.api.ObjectFilter
import arcanum.api.Objects
import arcanum.api.PlayerFilter
import arcanum.api.Players
import arcanum.api.SharedQuality
import arcanum.api.Step
import arcanum.api.Subtype
import arcanum.api.Timing
import arcanum.api.Value
import arcanum.api.Zone
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

/** Chunk 15 of FDN: replacement effects, part 2. Enters-tapped Gates and gain lands, as-enters choices, enters with counters. Invented names, the real oracle text. */
class FdnChunk15ReplacementTest {
    private fun gate(name: String, a: String, b: String) = card(
        name = name, cost = null, type = "Land — Gate",
        text = "This land enters tapped.\n{T}: Add {$a} or {$b}.",
    ) parsesTo abilities(entersTapped, addEither(a, b))

    private fun gainLand(name: String, a: String, b: String) = card(
        name = name, cost = null, type = "Land",
        text = "This land enters tapped.\nWhen this land enters, you gain 1 life.\n{T}: Add {$a} or {$b}.",
    ) parsesTo abilities(entersTapped, triggered(entersSelf, gainLife(1)), addEither(a, b))

    // CR 614.1c (enters tapped), 605.1a
    @Test fun blackWhiteGate() = gate("Ashen Gate", "W", "B")
    @Test fun blackRedGate() = gate("Cinder Gate", "B", "R")
    @Test fun greenWhiteGate() = gate("Meadow Gate", "G", "W")
    @Test fun greenBlueGate() = gate("Tidepool Gate", "G", "U")

    // CR 614.1c (enters tapped), 603.6a, 119.1, 605.1a
    @Test fun redGreenGainLand() = gainLand("Rugged Hollows", "R", "G")
    @Test fun whiteBlackGainLand() = gainLand("Scoured Basin", "W", "B")
    @Test fun blueRedGainLand() = gainLand("Swiftwater Rocks", "U", "R")
    @Test fun greenBlueGainLand() = gainLand("Thornwood Pools", "G", "U")
    @Test fun whiteBlueGainLand() = gainLand("Tranquil Bay", "W", "U")
    @Test fun redWhiteGainLand() = gainLand("Wind-Scarred Ridge", "R", "W")

    // CR 614.1c, 614.12 (as it enters), 106.1b (mana of the chosen color), 607.2d
    @Test
    fun unchartedHarbor() = card(
        name = "Uncharted Harbor",
        cost = null,
        type = "Land",
        text = """
            This land enters tapped. As it enters, choose a color.
            {T}: Add one mana of the chosen color.
        """,
    ) parsesTo abilities(
        entersTapped,
        static(Mechanic.Replacement(EventPattern.ZoneChange(ObjectFilter.Is(Objects.Self), to = Zone.Battlefield),
            modify = listOf(Modification.AsEnters(listOf(Step.Choose(Choice.Color(), bind = "color", chooser = Players.You, remember = true)))))),
        activated(cost(tap), doing(Effect.AddMana(ManaProduction(ManaKind.Choose(n(1), listOf(ColorRef.Remembered("color")))), Players.You))),
    )

    // CR 201.2 (Gates with different names), 104.2b (winning the game), 701.23 (search), 400.7
    @Test
    fun mazesEndLookalike() = card(
        name = "Winding Maze",
        cost = null,
        type = "Land",
        text = """
            This land enters tapped.
            {T}: Add {C}.
            {3}, {T}, Return this land to its owner's hand: Search your library for a Gate card, put it onto the battlefield, then shuffle. If you control ten or more Gates with different names, you win the game.
        """,
    ) parsesTo abilities(
        entersTapped,
        activated(cost(tap), doing(Effect.AddMana(ManaProduction(ManaKind.Fixed(ManaCost.parse("{C}")!!)), Players.You))),
        activated(cost(mana("{3}"), tap, CostPart.ReturnToHand(CostObjects.Self)),
            doing(Effect.Search(Players.You, sub("Gate"), max = n(1), zones = listOf(Zone.Library), bind = "found", searcher = Players.You)),
            doing(Effect.Move(Objects.Bound("found"), Destination(Zone.Battlefield))),
            doing(Effect.Shuffle(Players.You)),
            Step.If(Condition.Compare(Value.CountDistinct(Objects.Each(and(sub("Gate"), you)), SharedQuality.Name), Comparison.AtLeast, n(10)),
                then = listOf(doing(Effect.WinGame(Players.You))))),
    )

    // CR 614.1c: counted as it enters, its power then, counters included; 603.6a
    @Test
    fun primeSpeakerLookalike() = card(
        name = "Prime Speaker Zagana",
        cost = "{2}{G}{G}{U}{U}",
        type = "Legendary Creature — Merfolk Wizard",
        pt = "1/1",
        text = """
            Prime Speaker Zagana enters with X +1/+1 counters on it, where X is the greatest power among other creatures you control.
            When Prime Speaker Zagana enters, draw cards equal to its power.
        """,
    ) parsesTo abilities(
        static(Mechanic.Replacement(EventPattern.ZoneChange(ObjectFilter.Is(Objects.Self), to = Zone.Battlefield),
            modify = listOf(Modification.EntersWithCounters(p1p1,
                Value.Of(NumericProperty.Power, Objects.Each(and(another, creature, you)), Combine.Greatest))))),
        triggered(entersSelf, Step.Do(Effect.Draw(Players.You, power(Objects.Self)))),
    )

    // CR 614.1c (enters with counters), 122.1, 701.23, 108.3 (control changes), 602.5b
    @Test
    fun wishclawLookalike() = card(
        name = "Wishhook Talisman",
        cost = "{1}{B}",
        type = "Artifact",
        text = """
            This artifact enters with three wish counters on it.
            {1}, {T}, Remove a wish counter from this artifact: Search your library for a card, put it into your hand, then shuffle. An opponent gains control of this artifact. Activate only during your turn.
        """,
    ) parsesTo abilities(
        static(Mechanic.Replacement(EventPattern.ZoneChange(ObjectFilter.Is(Objects.Self), to = Zone.Battlefield),
            modify = listOf(Modification.EntersWithCounters(CounterKind.Named("wish"), n(3))))),
        activated(cost(mana("{1}"), tap, CostPart.RemoveCounters(CounterKind.Named("wish"), amount = n(1), from = CostObjects.Self)),
            doing(Effect.Search(Players.You, cardFilter, max = n(1), zones = listOf(Zone.Library), bind = "found", searcher = Players.You)),
            doing(Effect.Move(Objects.Bound("found"), Destination(Zone.Hand))),
            doing(Effect.Shuffle(Players.You)),
            Step.Choose(Choice.Player(PlayerFilter.Opponent), bind = "opponent", chooser = Players.You),
            doing(Effect.ApplyMechanics(listOf(Mechanic.ControlledBy(Players.Bound("opponent"))), Affected.Objects(Objects.Self), Duration.Indefinitely)),
            rules = ActivationRules(condition = Condition.YourTurn, timing = Timing.Any, activatedBy = PlayerFilter.You)),
    )
}
