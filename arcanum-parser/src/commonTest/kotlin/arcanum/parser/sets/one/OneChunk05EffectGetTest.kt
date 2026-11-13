package arcanum.parser.sets.one

import arcanum.api.Ability
import arcanum.api.CardType
import arcanum.api.Choice
import arcanum.api.Combine
import arcanum.api.Comparison
import arcanum.api.Condition
import arcanum.api.CounterKind
import arcanum.api.Destination
import arcanum.api.Duration
import arcanum.api.Each
import arcanum.api.Effect
import arcanum.api.EventPattern
import arcanum.api.EventRole
import arcanum.api.Instructions
import arcanum.api.Keyword
import arcanum.api.Mechanic
import arcanum.api.Modification
import arcanum.api.NumericProperty
import arcanum.api.ObjectFilter
import arcanum.api.Objects
import arcanum.api.PlayerFilter
import arcanum.api.Players
import arcanum.api.Recipients
import arcanum.api.Step
import arcanum.api.Trigger
import arcanum.api.Value
import arcanum.api.Zone
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

/**
 * Phyrexia: All Will Be One chunk 05: "gets" effects: poison counters given by effects to each opponent and to a blocking creature's
 * controller (CR 122.1f), creatures that get +1/+1 for each oil counter on themselves (layer 7c, CR 613.4c), "up to one target creature
 * gets -X/-X" and an untargeted return from the graveyard (CR 115.10a, 608.2d).
 */
class OneChunk05EffectGetTest {
    private val oil = CounterKind.Named("oil")
    private val poisonEachOpponent = doing(Effect.GivePlayerCounters(Players.Each(PlayerFilter.Opponent), CounterKind.Poison, n(1)))
    private val x = Value.Announced(name = "X")
    private val oilOnSelf = Value.Of(NumericProperty.Counters(oil), Objects.Self, Combine.Single)

    /** "This creature enters with [n] oil counter(s) on it." (CR 614.1c) */
    private fun entersWithOil(count: Int) = Ability.Static(listOf(Mechanic.Replacement(
        EventPattern.ZoneChange(ObjectFilter.Is(Objects.Self), to = Zone.Battlefield),
        modify = listOf(Modification.EntersWithCounters(oil, n(count))))), affects = arcanum.api.Affected.Objects(Objects.Self))

    /** "This creature gets +[p]/+[t] for each oil counter on it." (layer 7c, CR 613.4c) */
    private fun getsPerOil(p: Int, t: Int) = static(Mechanic.ModifyPowerToughness(if (p == 1) oilOnSelf else n(p), if (t == 1) oilOnSelf else n(t)))

    private val dies: (ObjectFilter) -> EventPattern = { EventPattern.ZoneChange(it, from = Zone.Battlefield, to = Zone.Graveyard) }

    // CR 107.3a (X announced as it's cast); CR 115.10a, 608.2d: the returned card is no target, chosen as it resolves; CR 110.2a
    @Test
    fun umbralDuskRite() = card(
        name = "Umbral Dusk Rite",
        cost = "{X}{B}",
        type = "Instant",
        text = "Up to one target creature gets -X/-X until end of turn. If X is 5 or more, return a creature card with mana value X or less from your graveyard to the battlefield tapped.",
    ) parsesTo abilities(
        spell(
            untilEndOfTurn(target("creature"), Mechanic.ModifyPowerToughness(Value.Times(x, -1), Value.Times(x, -1))),
            Step.If(Condition.Compare(x, Comparison.AtLeast, n(5)), then = listOf(
                Step.Choose(Choice.Objects(inYourGraveyard(creature, ObjectFilter.ManaValue(Comparison.AtMost, x)), n(1), n(1)), bind = "chosen", chooser = Players.You),
                doing(Effect.Move(Objects.Bound("chosen"), Destination(Zone.Battlefield, tapped = true))))),
            targets = listOf(slot("creature", creature, min = 0)),
            announced = listOf("X"),
        ),
    )

    // CR 603.4: intervening "if" checked as it triggers and as it resolves; "that creature" is the creature that entered
    @Test
    fun seepwingAdaptor() = card(
        name = "Seepwing Adaptor",
        cost = "{G}",
        type = "Creature — Phyrexian Warrior",
        pt = "0/0",
        text = """
            This creature enters with an oil counter on it.
            This creature gets +1/+1 for each oil counter on it.
            Whenever another creature you control enters, if that creature has greater power or toughness than this creature, put an oil counter on this creature.
        """,
    ) parsesTo abilities(
        entersWithOil(1),
        getsPerOil(1, 1),
        triggered(enters(and(another, creatureYouControl)), counters(Objects.Self, kind = oil), interveningIf = Condition.Or(listOf(
            Condition.Compare(power(Objects.EventObject(EventRole.After)), Comparison.Greater, power(Objects.Self)),
            Condition.Compare(toughness(Objects.EventObject(EventRole.After)), Comparison.Greater, toughness(Objects.Self))))),
    )

    // CR 613.4c; CR 603.2: one ability with two trigger events
    @Test
    fun sparkoilGrublet() = card(
        name = "Sparkoil Grublet",
        cost = "{R}",
        type = "Creature — Phyrexian Goblin Warrior",
        pt = "0/1",
        text = """
            Trample
            This creature gets +1/+0 for each oil counter on it.
            When this creature enters and whenever another creature or artifact you control is put into a graveyard from the battlefield, put an oil counter on this creature.
        """,
    ) parsesTo abilities(
        Keyword.Trample,
        getsPerOil(1, 0),
        triggered(Trigger.On(listOf(EventPattern.ZoneChange(ObjectFilter.Is(Objects.Self), to = Zone.Battlefield), dies(and(another, or(creature, artifact), you)))),
            counters(Objects.Self, kind = oil)),
    )

    // CR 122.1f: an effect gives each opponent a poison counter
    @Test
    fun venomousNip() = card(
        name = "Venomous Nip",
        cost = "{1}{G}",
        type = "Instant",
        text = "Target creature you control deals damage equal to its power to target creature you don't control. Each opponent gets a poison counter.",
    ) parsesTo abilities(
        spell(
            damage(power(target("creature")), toObjects(target("creature you don't control")), source = target("creature")),
            poisonEachOpponent,
            targets = listOf(slot("creature", creatureYouControl), slot("creature you don't control", and(creature, ObjectFilter.ControlledBy(PlayerFilter.Not(PlayerFilter.You))))),
        ),
    )

    // CR 122.1f
    @Test
    fun taintedQuery() = card(
        name = "Tainted Query",
        cost = "{2}{B}",
        type = "Sorcery",
        text = "You draw two cards and you lose 2 life. Each opponent gets a poison counter.",
    ) parsesTo abilities(
        spell(draw(2), loseLife(2), poisonEachOpponent),
    )

    // CR 613.4c; CR 614.1c (enters with two oil counters)
    @Test
    fun oilwingGnat() = card(
        name = "Oilwing Gnat",
        cost = "{3}{B}",
        type = "Creature — Phyrexian Insect",
        pt = "0/0",
        text = """
            Flying
            This creature enters with two oil counters on it.
            This creature gets +1/+1 for each oil counter on it.
            Whenever another creature or artifact you control is put into a graveyard from the battlefield, put an oil counter on this creature.
        """,
    ) parsesTo abilities(
        Keyword.Flying,
        entersWithOil(2),
        getsPerOil(1, 1),
        triggered(on(dies(and(another, or(creature, artifact), you))), counters(Objects.Self, kind = oil)),
    )

    // CR 603.7c: a delayed trigger that triggers each time a creature blocks this turn; CR 509.3a; CR 122.1f: the blocker's controller
    @Test
    fun foulingOnslaught() = card(
        name = "Fouling Onslaught",
        cost = "{3}{G}{G}",
        type = "Sorcery",
        text = "Creatures you control get +2/+2 until end of turn. Whenever a creature blocks this turn, its controller gets a poison counter.",
    ) parsesTo abilities(
        spell(
            pump(Objects.Each(creatureYouControl), 2, 2),
            doing(Effect.CreateDelayedTrigger(
                trigger = on(EventPattern.Block(creature, ObjectFilter.Any)),
                instructions = Instructions(steps = listOf(
                    doing(Effect.GivePlayerCounters(Players.ControllerOf(Objects.EventObject(EventRole.Object)), CounterKind.Poison, n(1))))),
                duration = Duration.EndOfTurn,
                repeats = true,
            )),
        ),
    )

    // CR 122.1f; a spell's lines are one spell ability, its instructions followed in order (CR 608.2c)
    @Test
    fun prefaceToCorruption() = card(
        name = "Preface to Corruption",
        cost = "{1}{U}",
        type = "Instant",
        text = """
            Each opponent gets a poison counter.
            Draw a card.
        """,
    ) parsesTo abilities(
        spell(poisonEachOpponent, draw(1)),
    )

    // CR 613.4c; CR 601.2: a noncreature spell cast
    @Test
    fun slickwingTrawler() = card(
        name = "Slickwing Trawler",
        cost = "{2}{U}",
        type = "Creature — Phyrexian Drake",
        pt = "0/0",
        text = """
            Flying
            This creature enters with an oil counter on it.
            This creature gets +1/+1 for each oil counter on it.
            Whenever you cast a noncreature spell, put an oil counter on this creature.
        """,
    ) parsesTo abilities(
        Keyword.Flying,
        entersWithOil(1),
        getsPerOil(1, 1),
        triggered(youCast(noncreatureSpell), counters(Objects.Self, kind = oil)),
    )

    // CR 701.21a: each opponent sacrifices one of their choice (in APNAP order, together); CR 122.1f
    @Test
    fun gorgonsPlunge() = card(
        name = "Gorgon's Plunge",
        cost = "{2}{B}",
        type = "Instant",
        text = "Each opponent sacrifices a creature or planeswalker of their choice and gets a poison counter.",
    ) parsesTo abilities(
        spell(
            Step.ForEach(Each.Players(Players.Each(PlayerFilter.Opponent)), "opponent", listOf(
                Step.Choose(Choice.Objects(and(or(creature, ObjectFilter.Type(CardType.Planeswalker)), ObjectFilter.ControlledBy(PlayerFilter.Is(Players.Bound("opponent")))), n(1), n(1)),
                    bind = "sacrificed", chooser = Players.Bound("opponent")),
                doing(Effect.Sacrifice(Objects.Bound("sacrificed")))), together = true),
            poisonEachOpponent,
        ),
    )

    // "its controller" after destroying a target is the target's controller, whether or not it was destroyed (an indestructible creature's
    // controller still gets the counter, CR 608.2c); CR 122.1f
    @Test
    fun skyrotLance() = card(
        name = "Skyrot Lance",
        cost = "{2}{G}",
        type = "Instant",
        text = "Destroy target creature with flying. Its controller gets a poison counter.",
    ) parsesTo abilities(
        spell(
            destroy(target("creature")),
            doing(Effect.GivePlayerCounters(Players.ControllerOf(target("creature")), CounterKind.Poison, n(1))),
            targets = listOf(slot("creature", and(creature, kw(Keyword.Flying.kind)))),
        ),
    )

    // CR 603.7c (a delayed trigger, this turn); CR 603.10a, 608.2h: "its controller" is who controlled the creature as it last existed on the
    // battlefield, not the card's owner in the graveyard
    @Test
    fun seepingLesion() = card(
        name = "Seeping Lesion",
        cost = "{B}",
        type = "Instant",
        text = "Put a -1/-1 counter on target creature. When that creature dies this turn, its controller gets a poison counter.",
    ) parsesTo abilities(
        spell(
            counters(target("creature"), kind = CounterKind.PowerToughness(-1, -1)),
            doing(Effect.CreateDelayedTrigger(
                trigger = on(dies(ObjectFilter.Is(target("creature")))),
                instructions = Instructions(steps = listOf(
                    doing(Effect.GivePlayerCounters(Players.ControllerOf(Objects.EventObject(EventRole.Before)), CounterKind.Poison, n(1))))),
                duration = Duration.EndOfTurn,
            )),
            targets = listOf(slot("creature", creature)),
        ),
    )
}
