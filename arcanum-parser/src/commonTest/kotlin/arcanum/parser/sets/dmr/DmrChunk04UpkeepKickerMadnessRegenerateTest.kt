package arcanum.parser.sets.dmr

import arcanum.api.DamageOccurrence
import arcanum.api.Ability
import arcanum.api.Choice
import arcanum.api.Color
import arcanum.api.ColorRef
import arcanum.api.Combine
import arcanum.api.Comparison
import arcanum.api.Condition
import arcanum.api.Cost
import arcanum.api.CostChange
import arcanum.api.CostObjects
import arcanum.api.CostPart
import arcanum.api.CounterKind
import arcanum.api.Effect
import arcanum.api.EventPattern
import arcanum.api.Instructions
import arcanum.api.Keyword
import arcanum.api.KeywordAction
import arcanum.api.ManaCost
import arcanum.api.ManaProduction
import arcanum.api.Mechanic
import arcanum.api.Modification
import arcanum.api.NumericProperty
import arcanum.api.ObjectFilter
import arcanum.api.Objects
import arcanum.api.PlayerFilter
import arcanum.api.Players
import arcanum.api.RecipientFilter
import arcanum.api.Step
import arcanum.api.Trigger
import arcanum.api.TurnStep
import arcanum.api.Value
import arcanum.api.Zone
import arcanum.parser.cube.abilities
import arcanum.parser.cube.anyOneColor
import arcanum.parser.cube.card
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

/** Dominaria Remastered chunk 04: cumulative upkeep (CR 702.24), kicker (CR 702.33), madness (CR 702.35), regeneration (CR 701.19) and
 * conditions on an ability's activations, counters and damage. */
class DmrChunk04UpkeepKickerMadnessRegenerateTest {
    private val age = CounterKind.Named("age")
    private val kicked = Condition.Paid(Keyword.Kicker.Kind, times = 1, of = Objects.Self)
    private fun entersWith(kind: CounterKind, count: Int, condition: Condition? = null) =
        static(Mechanic.Replacement(EventPattern.ZoneChange(isSelf, to = Zone.Battlefield), modify = listOf(Modification.EntersWithCounters(kind, n(count)))),
            condition = condition)
    private val regenerateSelf = doing(Effect.Action(KeywordAction.Regenerate(Objects.Self)))
    private val phantomShield = static(Mechanic.Prevention(EventPattern.Damage(ObjectFilter.Any, RecipientFilter(objects = isSelf)),
        additional = Instructions(steps = listOf(Step.Do(Effect.RemoveCounters(Objects.Self, p1p1, n(1)))))))

    // CR 702.24a: a cumulative upkeep cost that isn't mana; CR 603.10a: the dies trigger counts the counters it had as it left
    @Test
    fun tauntingSphinx() = card(
        name = "Taunting Sphinx",
        cost = "{1}{U}{U}",
        type = "Creature — Sphinx",
        pt = "4/4",
        text = """
            Flying
            Cumulative upkeep—Discard a card. (At the beginning of your upkeep, put an age counter on this permanent, then sacrifice it unless you pay its upkeep cost for each age counter on it.)
            When this creature dies, draw a card for each age counter on it.
        """,
    ) parsesTo abilities(
        Keyword.Flying,
        Keyword.CumulativeUpkeep(cost(discardACard)),
        triggered(diesSelf, doing(Effect.Draw(Players.You, Value.Of(NumericProperty.Counters(age), Objects.Self, Combine.Single)))),
    )

    // CR 702.24a; CR 118.12a: that player chooses whether to pay first, then you may draw
    @Test
    fun hungeringRemora() = card(
        name = "Hungering Remora",
        cost = "{U}",
        type = "Enchantment",
        text = """
            Cumulative upkeep {1} (At the beginning of your upkeep, put an age counter on this permanent, then sacrifice it unless you pay its upkeep cost for each age counter on it.)
            Whenever an opponent casts a noncreature spell, you may draw a card unless that player pays {4}.
        """,
    ) parsesTo abilities(
        Keyword.CumulativeUpkeep(manaCost("{1}")),
        triggered(on(EventPattern.Cast(not(creature), by = PlayerFilter.Opponent)),
            Step.Pay(manaCost("{4}"), payer = Players.EventPlayer, bind = "paid"),
            ifThen(Condition.Not(Condition.Did("paid")), Step.May(listOf(draw(1)), player = Players.You))),
    )

    // CR 702.33d, 614.1c: any number of counters it enters with when kicked
    @Test
    fun kavuMatriarch() = card(
        name = "Kavu Matriarch",
        cost = "{3}{G}",
        type = "Creature — Kavu",
        pt = "3/3",
        text = """
            Kicker {4} (You may pay an additional {4} as you cast this spell.)
            Convoke (Your creatures can help cast this spell. Each creature you tap while casting this spell pays for {1} or one mana of that creature's color.)
            If this creature was kicked, it enters with four +1/+1 counters on it.
        """,
    ) parsesTo abilities(
        Keyword.Kicker(listOf(manaCost("{4}"))),
        Keyword.Convoke,
        entersWith(p1p1, 4, kicked),
    )

    // CR 601.2c: the creature is a target only if the spell was kicked; CR 608.2h: the destroyed permanent's mana value as it last existed
    @Test
    fun sistersThunder() = card(
        name = "Sister's Thunder",
        cost = "{2}{W}",
        type = "Instant",
        text = """
            Kicker {R} (You may pay an additional {R} as you cast this spell.)
            Destroy target artifact or enchantment. If this spell was kicked, it deals damage equal to that permanent's mana value to target creature.
        """,
    ) parsesTo abilities(
        Keyword.Kicker(listOf(manaCost("{R}"))),
        spell(
            destroy(target("permanent")),
            ifThen(kicked, damage(Value.Of(NumericProperty.ManaValue, target("permanent"), Combine.Single), toObjects(target("creature")))),
            targets = listOf(slot("permanent", or(artifact, enchantment)), slot("creature", creature).copy(onlyIf = kicked))),
    )

    // CR 702.33a: a kicker cost of life
    @Test
    fun phyrexianCarapace() = card(
        name = "Phyrexian Carapace",
        cost = "{3}{B}",
        type = "Creature — Phyrexian Zombie",
        pt = "3/3",
        text = """
            Kicker—Pay 3 life. (You may pay 3 life in addition to any other costs as you cast this spell.)
            If this creature was kicked, it enters with two +1/+1 counters on it.
        """,
    ) parsesTo abilities(
        Keyword.Kicker(listOf(cost(CostPart.PayLife(n(3))))),
        entersWith(p1p1, 2, kicked),
    )

    // CR 118.12a: {1} for each card in your graveyard; CR 702.35a
    @Test
    fun loopingLogic() = card(
        name = "Looping Logic",
        cost = "{2}{U}",
        type = "Instant",
        text = """
            Counter target spell unless its controller pays {1} for each card in your graveyard.
            Madness {U} (If you discard this card, discard it into exile. When you do, cast it for its madness cost or put it into your graveyard.)
        """,
    ) parsesTo abilities(
        spell(
            Step.Pay(Cost(listOf(CostPart.Mana(ManaCost.parse("{1}")!!, times = count(inYourGraveyard(cardFilter))))),
                payer = Players.ControllerOf(target("spell")), bind = "paid"),
            ifThen(Condition.Not(Condition.Did("paid")), doing(Effect.Counter(target("spell")))),
            targets = listOf(slot("spell", spellFilter))),
        Keyword.Madness(manaCost("{U}")),
    )

    // CR 105.2: a black creature is not a legal target
    @Test
    fun darkWilting() = card(
        name = "Dark Wilting",
        cost = "{4}{B}{B}",
        type = "Instant",
        text = """
            Destroy target nonblack creature.
            Madness {B} (If you discard this card, discard it into exile. When you do, cast it for its madness cost or put it into your graveyard.)
        """,
    ) parsesTo abilities(
        spell(destroy(target("creature")), targets = listOf(slot("creature", and(not(ObjectFilter.OfColor(ColorRef.Literal(Color.Black))), creature)))),
        Keyword.Madness(manaCost("{B}")),
    )

    // CR 702.35a
    @Test
    fun obsessiveHunt() = card(
        name = "Obsessive Hunt",
        cost = "{U}",
        type = "Instant",
        text = """
            Draw a card.
            Madness {U} (If you discard this card, discard it into exile. When you do, cast it for its madness cost or put it into your graveyard.)
        """,
    ) parsesTo abilities(
        spell(draw(1)),
        Keyword.Madness(manaCost("{U}")),
    )

    // CR 120.1 (any damage to a creature, combat or not), 701.19a, 105.3 (the chosen colour replaces its colours, layer 5)
    @Test
    fun spiritgorger() = card(
        name = "Spiritgorger",
        cost = "{3}{B}{G}",
        type = "Creature — Beast",
        pt = "6/6",
        text = """
            Whenever this creature deals damage to a creature, put a +1/+1 counter on this creature.
            {B}: Regenerate this creature.
            {G}: This creature becomes the color of your choice until end of turn.
        """,
    ) parsesTo abilities(
        triggered(on(EventPattern.Damage(isSelf, RecipientFilter(objects = creature))), counters(Objects.Self)),
        activated(manaCost("{B}"), regenerateSelf),
        activated(manaCost("{G}"), Step.Choose(Choice.Color(), bind = "color", chooser = Players.You),
            untilEndOfTurn(Objects.Self, Mechanic.SetColors(listOf(ColorRef.Chosen("color"))))),
    )

    // CR 701.19a; a Goblin to sacrifice may be this creature itself
    @Test
    fun goblinDefector() = card(
        name = "Goblin Defector",
        cost = "{1}{B}",
        type = "Creature — Goblin Mercenary",
        pt = "2/1",
        text = "Sacrifice a Goblin: Regenerate this creature.",
    ) parsesTo abilities(
        activated(cost(sacrificeA(sub("Goblin"))), regenerateSelf),
    )

    // CR 601.2f: a blue or red spell costs {1} less (a spell that is both, once)
    @Test
    fun nightshadeFamiliar() = card(
        name = "Nightshade Familiar",
        cost = "{1}{B}",
        type = "Creature — Zombie",
        pt = "1/1",
        text = """
            Blue spells and red spells you cast cost {1} less to cast.
            {1}{B}: Regenerate this creature.
        """,
    ) parsesTo abilities(
        static(Mechanic.CostChange(or(and(ObjectFilter.OfColor(ColorRef.Literal(Color.Blue)), spellFilter, you), and(ObjectFilter.OfColor(ColorRef.Literal(Color.Red)), spellFilter, you)),
            CostChange.Less(ManaCost.parse("{1}")!!, n(1)))),
        activated(manaCost("{1}{B}"), regenerateSelf),
    )

    // CR 602.1 (counting this activation), 603.7: a delayed trigger made as the ability resolves
    @Test
    fun emberWhelp() = card(
        name = "Ember Whelp",
        cost = "{2}{R}{R}",
        type = "Creature — Dragon",
        pt = "2/3",
        text = """
            Flying
            {R}: This creature gets +1/+0 until end of turn. If this ability has been activated four or more times this turn, sacrifice this creature at the beginning of the next end step.
        """,
    ) parsesTo abilities(
        Keyword.Flying,
        activated(manaCost("{R}"), pump(Objects.Self, 1, 0),
            ifThen(Condition.Compare(Value.AbilityUsesThisTurn(resolved = false), Comparison.AtLeast, n(4)),
                doing(Effect.CreateDelayedTrigger(Trigger.On(EventPattern.StepBegins(TurnStep.End, PlayerFilter.Any)),
                    Instructions(steps = listOf(doing(Effect.Sacrifice(Objects.Self, by = Players.You)))))))),
    )

    // CR 605.1a (a mana ability), 122.1: the land is sacrificed once its last counter is gone
    @Test
    fun gemstoneQuarry() = card(
        name = "Gemstone Quarry",
        cost = null,
        type = "Land",
        text = """
            This land enters with three mining counters on it.
            {T}, Remove a mining counter from this land: Add one mana of any color. If there are no mining counters on this land, sacrifice it.
        """,
    ) parsesTo abilities(
        entersWith(CounterKind.Named("mining"), 3),
        activated(cost(tap, CostPart.RemoveCounters(CounterKind.Named("mining"), n(1), CostObjects.Self)),
            doing(Effect.AddMana(ManaProduction(anyOneColor(1)), Players.You)),
            ifThen(Condition.Not(Condition.ObjectMatches(Objects.Self, ObjectFilter.HasCounter(CounterKind.Named("mining"), atLeast = 1))),
                doing(Effect.Sacrifice(Objects.Self, by = Players.You)))),
    )

    // CR 615.5: the damage is prevented, then a counter removed
    @Test
    fun phantomCovey() = card(
        name = "Phantom Covey",
        cost = "{3}{W}{W}",
        type = "Creature — Bird Soldier Spirit",
        pt = "0/0",
        text = """
            Flying
            This creature enters with three +1/+1 counters on it.
            If damage would be dealt to this creature, prevent that damage. Remove a +1/+1 counter from this creature.
        """,
    ) parsesTo abilities(
        Keyword.Flying,
        entersWith(p1p1, 3),
        phantomShield,
    )

    // CR 615.5; CR 120.3: the damage it deals, of any kind, is what you gain; CR 603.2c: once for all it deals at once
    @Test
    fun phantomNightcat() = card(
        name = "Phantom Nightcat",
        cost = "{5}{G}{W}",
        type = "Creature — Cat Beast Spirit",
        pt = "0/0",
        text = """
            Trample
            This creature enters with seven +1/+1 counters on it.
            Whenever this creature deals damage, you gain that much life.
            If damage would be dealt to this creature, prevent that damage. Remove a +1/+1 counter from this creature.
        """,
    ) parsesTo abilities(
        Keyword.Trample,
        entersWith(p1p1, 7),
        triggered(on(EventPattern.Damage(isSelf, RecipientFilter(), per = DamageOccurrence.Source)), doing(Effect.GainLife(Players.You, Value.EventAmount))),
        phantomShield,
    )
}
