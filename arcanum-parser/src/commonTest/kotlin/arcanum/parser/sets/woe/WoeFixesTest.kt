package arcanum.parser.sets.woe

import arcanum.api.*
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

/** Wilds of Eldraine fix step: general parser fixes found while working the set. */
class WoeFixesTest {
    private fun elemental(n: String = "Elemental") = SubtypeRef.Literal(Subtype(n))

    // CR 303.4: an Aura can enchant a noncreature permanent, so "enchanted permanents" isn't read as "enchanted creatures" (only creatures);
    // nothing reads the wording as written, so it stays unread
    @Test
    fun enchantedPermanentsArentEnchantedCreatures() = card(
        name = "Undo the Bindings",
        cost = "{2}{W}",
        type = "Sorcery",
        text = "Destroy all enchanted permanents.",
    ) parsesTo abilities(Ability.Unparsed("Destroy all enchanted permanents."))

    // CR 205.1b: "It's still a land." keeps all its prior card types and subtypes, its creature types too: nothing is replaced
    @Test
    fun stillALandKeepsItsSubtypes() = card(
        name = "Tidecall Surge",
        cost = "{2}{U}",
        type = "Sorcery",
        text = "Target land becomes a 3/3 Elemental creature with flying until end of turn. It's still a land.",
    ) parsesTo abilities(
        spell(
            doing(Effect.ApplyMechanics(listOf(
                Mechanic.SetTypes(add = listOf(CardType.Creature), addSubtypes = listOf(elemental())),
                Mechanic.SetPowerToughness(n(3), n(3)),
                Mechanic.GainAbilities(listOf(Keyword.Flying))), Affected.Objects(target("land")), Duration.EndOfTurn)),
            targets = listOf(slot("land", land)),
        ),
    )

    // CR 205.1a: without "still a land", "in addition to its other types" or "artifact creature", becoming a creature sets its card type and
    // its creature types
    @Test
    fun becomingACreatureSetsItsType() = card(
        name = "Wakened Furrow",
        cost = "{1}{G}",
        type = "Instant",
        text = "Until end of turn, target land becomes a 4/4 Elemental creature.",
    ) parsesTo abilities(
        spell(
            doing(Effect.ApplyMechanics(listOf(
                Mechanic.SetTypes(add = listOf(CardType.Creature), addSubtypes = listOf(elemental()), replaceTypes = true, replaceSubtypes = true),
                Mechanic.SetPowerToughness(n(4), n(4))), Affected.Objects(target("land")), Duration.EndOfTurn)),
            targets = listOf(slot("land", land)),
        ),
    )

    // CR 205.1b: a "[creature type] artifact creature" keeps its other card types and subtypes but replaces its creature types
    @Test
    fun creatureTypeArtifactCreatureReplacesCreatureTypes() = card(
        name = "Gearsoul Infusion",
        cost = "{2}{U}",
        type = "Sorcery",
        text = "Put three +1/+1 counters on target artifact you control. If it's not a creature, it becomes a 0/0 Robot artifact creature.",
    ) parsesTo abilities(
        spell(
            doing(Effect.PutCounters(target("artifact"), CounterKind.PowerToughness(1, 1), n(3))),
            Step.If(Condition.Not(Condition.ObjectMatches(target("artifact"), creature)), then = listOf(doing(Effect.ApplyMechanics(listOf(
                Mechanic.SetTypes(add = listOf(CardType.Artifact, CardType.Creature), addSubtypes = listOf(elemental("Robot")), replaceSubtypes = true),
                Mechanic.SetPowerToughness(n(0), n(0))), Affected.Objects(target("artifact")), Duration.Indefinitely)))),
            targets = listOf(slot("artifact", and(artifact, you))),
        ),
    )

    // "that many" is the nearest amount before it: the counters removed by the same ability, not the triggering event's amount
    // (CR 608.2c: the instructions are followed in the order written)
    @Test
    fun thatManyIsTheNearestAmount() = card(
        name = "Tallying Wyrm",
        cost = "{3}{U}",
        type = "Creature — Dragon",
        pt = "3/3",
        text = "When this creature enters, remove all +1/+1 counters from target creature you control. Discard that many cards.",
    ) parsesTo abilities(
        triggered(entersSelf,
            doing(Effect.RemoveCounters(target("creature"), CounterKind.PowerToughness(1, 1),
                Value.Of(NumericProperty.Counters(CounterKind.PowerToughness(1, 1)), target("creature"), Combine.Single), bind = "removed")),
            doing(Effect.Discard(Players.You, Value.Bound("removed"), filter = ObjectFilter.Any)),
            targets = listOf(slot("creature", and(creature, you)))),
    )

    // CR 614.1a, 119.4: paying life is replaced by exiling that many cards while the library has at least that many (the event's amount);
    // CR 406.1: "a card was put into exile this turn" from anywhere; CR 107.3c: X defined by "where X is"
    @Test
    fun ashiokWickedManipulator() = card(
        name = "Vashka, Wicked Schemer",
        cost = "{3}{B}{B}",
        type = "Legendary Planeswalker — Ashiok",
        loyalty = "5",
        text = """
            If you would pay life while your library has at least that many cards in it, exile that many cards from the top of your library instead.
            [+1]: Look at the top two cards of your library. Exile one of them and put the other into your hand.
            [−2]: Create two 1/1 black Nightmare creature tokens with "At the beginning of combat on your turn, if a card was put into exile this turn, put a +1/+1 counter on this token."
            [−7]: Target player exiles the top X cards of their library, where X is the total mana value of cards you own in exile.
        """,
    ) parsesTo abilities(
        static(Mechanic.Replacement(EventPattern.PayLife(PlayerFilter.You),
            instead = Instructions(steps = listOf(Step.Do(Effect.Move(Objects.LibraryCards(Players.You, Value.EventAmount, fromTop = true), Destination(Zone.Exile),
                postBind = "exiled")))),
            condition = Condition.Compare(Value.Count(Objects.Each(and(inZone(Zone.Library), ObjectFilter.OwnedBy(PlayerFilter.You)))), Comparison.AtLeast,
                Value.EventAmount))),
        activated(cost(CostPart.Loyalty(n(1))),
            doing(Effect.LookAt(Objects.LibraryCards(Players.You, n(2), fromTop = true), Players.You, bind = "looked")),
            Step.Choose(Choice.Objects(ObjectFilter.Any, n(1), n(1), from = Objects.Bound("looked")), bind = "chosen", chooser = Players.You),
            doing(Effect.Move(Objects.Bound("chosen"), Destination(Zone.Exile))),
            doing(Effect.Move(Objects.Except(Objects.Bound("looked"), Objects.Bound("chosen")), Destination(Zone.Hand)))),
        activated(cost(CostPart.Loyalty(n(-2))),
            doing(Effect.CreateToken(creatureToken("Nightmare", listOf(Color.Black), 1, 1, abilities = listOf(
                triggered(stepBegins(TurnStep.BeginningOfCombat, PlayerFilter.You), doing(Effect.PutCounters(Objects.Self, p1p1, n(1))),
                    interveningIf = Condition.HappenedThisTurn(EventPattern.ZoneChange(cardFilter, to = Zone.Exile), 1, Measure.Events)))),
                n(2), Players.You))),
        activated(cost(CostPart.Loyalty(n(-7))),
            doing(Effect.Move(Objects.LibraryCards(Players.Target("player"), Value.Of(NumericProperty.ManaValue,
                Objects.Each(and(cardFilter, inZone(Zone.Exile), ObjectFilter.OwnedBy(PlayerFilter.You))), Combine.Total), fromTop = true), Destination(Zone.Exile),
                postBind = "exiled")),
            targets = listOf(TargetSlot("player", TargetKind.Players(PlayerFilter.Any), min = n(1), max = n(1)))),
    )
}
