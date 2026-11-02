package arcanum.parser.sets.blb

import arcanum.api.Ability
import arcanum.api.Affected
import arcanum.api.CardType
import arcanum.api.Choice
import arcanum.api.Combine
import arcanum.api.Comparison
import arcanum.api.Condition
import arcanum.api.CopyException
import arcanum.api.Cost
import arcanum.api.CostChange
import arcanum.api.CostObjects
import arcanum.api.CostPart
import arcanum.api.CounterKind
import arcanum.api.Destination
import arcanum.api.Duration
import arcanum.api.Each
import arcanum.api.Effect
import arcanum.api.EventPattern
import arcanum.api.EventRole
import arcanum.api.Instructions
import arcanum.api.Keyword
import arcanum.api.Measure
import arcanum.api.Mechanic
import arcanum.api.Modification
import arcanum.api.NumericProperty
import arcanum.api.ObjectCategory
import arcanum.api.ObjectFilter
import arcanum.api.Objects
import arcanum.api.PlayKind
import arcanum.api.PlayerFilter
import arcanum.api.Players
import arcanum.api.Recipients
import arcanum.api.RecipientFilter
import arcanum.api.SharedQuality
import arcanum.api.Step
import arcanum.api.Subtype
import arcanum.api.SubtypeRef
import arcanum.api.Trigger
import arcanum.api.TurnStep
import arcanum.api.Value
import arcanum.api.Zone
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.parsesTo
import arcanum.parser.cube.whenThisEnters
import kotlin.test.Test

/**
 * Bloomburrow chunk 14 (blb gaps): cards whose lines needed new wordings: losing all abilities and base power and
 * toughness until your next turn, a land type for as long as a counter stays, the first instant or sorcery spell each
 * turn costing less, a repeating delayed trigger that blinks a creature, entering as a copy limited by the mana spent,
 * one card of each card type, sacrificing any number of permanents to reduce a cost, a repeated punisher choice, exiled
 * cards played until your next end step, and a conditional threaten.
 */
class BlbChunk14GapsUntilAndCopiesTest {
    private val thisAttacks = EventPattern.Attack(ObjectFilter.Is(Objects.Self))
    private val entersOrAttacks = Trigger.On(listOf(EventPattern.ZoneChange(ObjectFilter.Is(Objects.Self), to = Zone.Battlefield), thisAttacks))
    private fun named(kind: String) = CounterKind.Named(kind)
    private fun until(objects: Objects, duration: Duration, vararg mechanics: Mechanic) =
        Step.Do(Effect.ApplyMechanics(mechanics.toList(), Affected.Objects(objects), duration))

    // CR 613.1f, 613.4b, 611.2c: loses all abilities (layer 6) and has base P/T 2/2 (layer 7b), for the object chosen
    @Test
    fun ceruleanBeastwarden() = card(
        name = "Cerulean Beastwarden",
        cost = "{1}{U}",
        type = "Creature — Rat Rogue",
        pt = "1/3",
        text = """
            Vigilance
            This creature can't be blocked by creatures with power 2 or greater.
            Whenever this creature attacks, up to one target artifact, creature, or planeswalker an opponent controls loses all abilities until your next turn. If it's a creature, it also has base power and toughness 2/2 until your next turn.
        """,
    ) parsesTo abilities(
        Keyword.Vigilance,
        Ability.Static(
            listOf(Mechanic.CantBeBlockedBy(ObjectFilter.And(listOf(creature, ObjectFilter.Power(Comparison.AtLeast, one(2)))))),
            affects = Affected.Objects(Objects.Self),
        ),
        Ability.Triggered(
            trigger = Trigger.On(thisAttacks),
            instructions = Instructions(
                targets = listOf(slot(
                    name = "permanent",
                    filter = ObjectFilter.And(listOf(
                        ObjectFilter.Or(listOf(CardType.Artifact, CardType.Creature, CardType.Planeswalker).map { ObjectFilter.Type(it) }),
                        opponents,
                    )),
                    min = 0,
                )),
                steps = listOf(
                    until(Objects.Target("permanent"), Duration.UntilYourNextTurn, Mechanic.LoseAbilities()),
                    Step.If(
                        Condition.ObjectMatches(Objects.Target("permanent"), creature),
                        then = listOf(until(Objects.Target("permanent"), Duration.UntilYourNextTurn, Mechanic.SetPowerToughness(one(2), one(2)))),
                    ),
                ),
            ),
        ),
    )

    /**
     * The land is an Island while it has a flood counter (CR 611.2b, 305.7: in addition, it keeps its other types and
     * abilities and has the Island's mana ability, 305.6). "The first instant or sorcery spell you cast each turn": the
     * reduction applies while you have cast none yet this turn, since a spell becomes cast only after its cost is
     * determined (CR 601.2f, 601.2i); a {U} reduction the cost can't use reduces generic mana (CR 118.7b, c).
     */
    @Test
    fun morrowTheEndlessTide() = card(
        name = "Morrow, the Endless Tide",
        cost = "{1}{U}{U}{U}",
        type = "Legendary Creature — Elemental Fish",
        pt = "*/*",
        text = """
            Morrow's power and toughness are each equal to the number of Islands you control.
            Whenever Morrow enters or attacks, put a flood counter on target land. It's an Island in addition to its other types for as long as it has a flood counter on it.
            The first instant or sorcery spell you cast each turn costs {U} (or {1}) less to cast for each land you control with a flood counter on it.
        """,
    ) parsesTo abilities(
        Ability.Static(
            listOf(Mechanic.SetPowerToughness(
                power = Value.Count(Objects.Each(ObjectFilter.And(listOf(sub("Island"), you)))),
                toughness = Value.Count(Objects.Each(ObjectFilter.And(listOf(sub("Island"), you)))),
            )),
            affects = Affected.Objects(Objects.Self),
            characteristicDefining = true,
        ),
        Ability.Triggered(
            trigger = entersOrAttacks,
            instructions = Instructions(
                targets = listOf(slot("land", land)),
                steps = listOf(
                    Step.Do(Effect.PutCounters(Objects.Target("land"), named("flood"), one())),
                    until(
                        Objects.Target("land"),
                        Duration.AsLongAs(Condition.ObjectMatches(Objects.Target("land"), ObjectFilter.HasCounter(named("flood"), atLeast = 1))),
                        Mechanic.SetTypes(addSubtypes = listOf(SubtypeRef.Literal(Subtype("Island")))),
                    ),
                ),
            ),
        ),
        Ability.Static(
            listOf(Mechanic.CostChange(
                what = ObjectFilter.And(listOf(instantOrSorcery, ObjectFilter.Category(ObjectCategory.Spell), you)),
                change = CostChange.Less(
                    mana = arcanum.api.ManaCost.parse("{U}")!!,
                    times = Value.Count(Objects.Each(ObjectFilter.And(listOf(land, you, ObjectFilter.HasCounter(named("flood"), atLeast = 1))))),
                ),
            )),
            affects = Affected.Objects(Objects.Self),
            condition = Condition.Not(Condition.HappenedThisTurn(
                EventPattern.Cast(instantOrSorcery, by = PlayerFilter.You),
                atLeast = 1,
                measure = Measure.Events,
            )),
        ),
    )

    /**
     * "Whenever that creature deals combat damage this turn": a delayed trigger that triggers each time until the turn
     * ends (CR 603.7b), about the creature chosen as the target (CR 603.7c). Exiling it is a cost paid on resolution
     * (CR 118.12); it returns as a new object (CR 400.7).
     */
    @Test
    fun wideCreekSkulker() = card(
        name = "Wide Creek Skulker",
        cost = "{2}{U}",
        type = "Creature — Frog Scout",
        pt = "2/3",
        text = """
            Ward {1}
            Other Frogs you control have ward {1}.
            When this creature enters, target creature you control can't be blocked this turn. Whenever that creature deals combat damage this turn, you may exile it. If you do, return it to the battlefield under its owner's control.
        """,
    ) parsesTo abilities(
        Keyword.Ward(arcanum.parser.cube.manaCost("{1}")),
        Ability.Static(
            listOf(Mechanic.GainAbilities(listOf(Keyword.Ward(arcanum.parser.cube.manaCost("{1}"))))),
            affects = Affected.Objects(Objects.Each(ObjectFilter.And(listOf(ObjectFilter.Not(ObjectFilter.Is(Objects.Self)), sub("Frog"), you)))),
        ),
        whenThisEnters(
            until(Objects.Target("creature"), Duration.EndOfTurn, Mechanic.CantBeBlocked),
            Step.Do(Effect.CreateDelayedTrigger(
                trigger = Trigger.On(EventPattern.Damage(
                    source = ObjectFilter.Is(Objects.Target("creature")),
                    to = RecipientFilter(),
                    combat = true,
                )),
                instructions = steps(
                    Step.Pay(
                        Cost(listOf(CostPart.Exile(CostObjects.These(Objects.EventObject(EventRole.Source)), from = Zone.Battlefield, postBind = "exiled"))),
                        payer = Players.You,
                        bind = "paid",
                    ),
                    Step.If(Condition.Did("paid"), then = listOf(Step.Do(Effect.Move(
                        Objects.Bound("exiled"),
                        Destination(Zone.Battlefield, controller = Players.OwnerOf(Objects.Bound("exiled"))),
                        postBind = "returned",
                    )))),
                ),
                duration = Duration.EndOfTurn,
                repeats = true,
            )),
            targets = listOf(slot("creature", creatureYouControl)),
        ),
    )

    /**
     * "Enter as a copy of" is a replacement applied as it enters (CR 614.1c, 707.2); the exceptions become part of the
     * copy (CR 707.9b). The mana spent to cast it is kept by the permanent (CR 400.7d); 0 when it wasn't cast.
     */
    @Test
    fun echoThrush() = card(
        name = "Echo Thrush",
        cost = "{X}{U}",
        type = "Creature — Bird Bard",
        pt = "1/1",
        text = """
            Flying
            You may have this creature enter as a copy of any creature on the battlefield with mana value less than or equal to the amount of mana spent to cast this creature, except it's a Bird in addition to its other types and it has flying.
        """,
    ) parsesTo abilities(
        Keyword.Flying,
        Ability.Static(
            listOf(Mechanic.Replacement(
                EventPattern.ZoneChange(ObjectFilter.Is(Objects.Self), to = Zone.Battlefield),
                modify = listOf(Modification.EntersAsCopy(
                    of = ObjectFilter.And(listOf(
                        creature,
                        ObjectFilter.ManaValue(Comparison.AtMost, Value.ManaSpent(of = Objects.Self)),
                    )),
                    except = listOf(
                        CopyException.Types(addSubtypes = listOf(SubtypeRef.Literal(Subtype("Bird")))),
                        CopyException.AddAbilities(listOf(Keyword.Flying)),
                    ),
                    optional = true,
                )),
            )),
            affects = Affected.Objects(Objects.Self),
        ),
    )

    /**
     * One card of each card type (an artifact creature card stands for one of its types, CR 205.2a); "this way" counts
     * the cards exiled. The spell is cast while this one resolves (CR 608.2g) and the other exiled cards go to the hand.
     */
    @Test
    fun omenOfRuin() = card(
        name = "Omen of Ruin",
        cost = "{X}{U}",
        type = "Sorcery",
        text = "Reveal the top X cards of your library. For each card type, you may exile a card of that type from among them. Put the rest into your graveyard. You may cast a spell from among the exiled cards without paying its mana cost if you exiled four or more cards this way. Then put the rest of the exiled cards into your hand.",
    ) parsesTo abilities(
        Ability.Spell(Instructions(
            announced = listOf("X"),
            steps = listOf(
                Step.Do(Effect.Reveal(Objects.LibraryCards(Players.You, Value.Announced("X"), fromTop = true), bind = "seen")),
                Step.Choose(
                    Choice.Objects(
                        filter = ObjectFilter.Any,
                        min = one(0),
                        max = null,
                        from = Objects.Bound("seen"),
                        onePer = SharedQuality.CardType,
                    ),
                    bind = "chosen",
                    chooser = Players.You,
                ),
                Step.Do(Effect.Move(Objects.Bound("chosen"), Destination(Zone.Exile), postBind = "exiled")),
                Step.Do(Effect.Move(Objects.Except(Objects.Bound("seen"), Objects.Bound("chosen")), Destination(Zone.Graveyard))),
                Step.If(
                    Condition.Compare(Value.Count(Objects.Bound("exiled")), Comparison.AtLeast, one(4)),
                    then = listOf(
                        Step.Choose(
                            Choice.Objects(ObjectFilter.Not(land), min = one(0), max = one(1), from = Objects.Bound("exiled")),
                            bind = "cast",
                            chooser = Players.You,
                        ),
                        Step.Do(Effect.Cast(Objects.Bound("cast"), PlayKind.Cast, withoutPaying = true, caster = Players.You)),
                    ),
                ),
                Step.Do(Effect.Move(Objects.Except(Objects.Bound("exiled"), Objects.Bound("cast")), Destination(Zone.Hand))),
            ),
        )),
    )

    /**
     * The sacrifices are chosen as the spell is cast and its total cost is worked out with them (CR 601.2b, 601.2f),
     * then paid (601.2h). "For each blight counter on it": counted as that part begins (CR 608.2h: as it last existed if
     * it left); each time, each opponent in turn order chooses to pay or not, then those who don't lose life together
     * (CR 101.4, 118.12a).
     */
    @Test
    fun rotfangAdder() = card(
        name = "Rotfang Adder",
        cost = "{5}{B}",
        type = "Creature — Elemental Snake",
        pt = "6/6",
        text = """
            As an additional cost to cast this spell, you may sacrifice any number of nonland permanents. This spell costs {1} less to cast for each permanent sacrificed this way.
            Whenever this creature enters or attacks, put a blight counter on it. Then for each blight counter on it, each opponent loses 4 life unless that player sacrifices a nonland permanent of their choice or discards a card.
        """,
    ) parsesTo abilities(
        Ability.Static(
            listOf(
                Mechanic.AdditionalCost(cost = Cost(listOf(CostPart.Sacrifice(CostObjects.Choose(nonlandPermanent, count = null), preBind = "sacrificed")))),
                Mechanic.CostChange(
                    what = ObjectFilter.Is(Objects.Self),
                    change = CostChange.Less(arcanum.api.ManaCost.parse("{1}")!!, times = Value.Count(Objects.Bound("sacrificed"))),
                ),
            ),
            affects = Affected.Objects(Objects.Self),
        ),
        Ability.Triggered(
            trigger = entersOrAttacks,
            instructions = steps(
                Step.Do(Effect.PutCounters(Objects.Self, named("blight"), one())),
                Step.Repeat(
                    times = Value.Of(NumericProperty.Counters(named("blight")), Objects.Self, Combine.Single),
                    steps = listOf(Step.ForEach(
                        Each.Players(Players.Each(PlayerFilter.Opponent)),
                        bind = "opponent",
                        together = true,
                        steps = losesUnlessSacrificesOrDiscards(Players.Bound("opponent"), 4),
                    )),
                ),
            ),
        ),
    )

    /**
     * X is defined by the text (CR 107.3c). The permission ends as your next end step begins; the delayed trigger
     * (CR 603.7) counts the exiled cards that are still the same objects in exile (a card played left, CR 400.7).
     */
    @Test
    fun stormwingDoomsSquall() = card(
        name = "Stormwing, Doom's Squall",
        cost = "{3}{R}{R}",
        type = "Legendary Creature — Bird Dragon",
        pt = "5/5",
        text = """
            Flying
            Whenever Stormwing enters or attacks, exile the top X cards of your library, where X is the number of creatures you control with power 4 or greater. You may play those cards until your next end step. At the beginning of your next end step, Stormwing deals 2 damage to each opponent for each of those cards that are still exiled.
        """,
    ) parsesTo abilities(
        Keyword.Flying,
        Ability.Triggered(
            trigger = entersOrAttacks,
            instructions = steps(
                Step.Do(Effect.Move(
                    Objects.LibraryCards(
                        Players.You,
                        Value.Count(Objects.Each(ObjectFilter.And(listOf(creature, you, ObjectFilter.Power(Comparison.AtLeast, one(4)))))),
                        fromTop = true,
                    ),
                    Destination(Zone.Exile),
                    postBind = "exiled",
                )),
                until(
                    Objects.Bound("exiled"),
                    Duration.UntilStep(TurnStep.End, PlayerFilter.You),
                    Mechanic.MayPlay(Zone.Exile, PlayKind.Play, holder = PlayerFilter.You),
                ),
                Step.Do(Effect.CreateDelayedTrigger(
                    trigger = yourEndStep,
                    instructions = steps(Step.Do(Effect.DealDamage(
                        to = Recipients(players = Players.Each(PlayerFilter.Opponent)),
                        amount = Value.Times(Value.Count(Objects.Where(Objects.Bound("exiled"), ObjectFilter.InZone(Zone.Exile))), 2),
                        source = Objects.Self,
                    ))),
                )),
            ),
        ),
    )

    // CR 613.1b, 611.2c: control until end of turn of the target, if the condition holds as it resolves
    @Test
    fun saurianConscriptor() = card(
        name = "Saurian Conscriptor",
        cost = "{3}{R}{R}",
        type = "Creature — Lizard Warrior",
        pt = "4/2",
        text = """
            Trample
            When this creature enters, choose target creature. If that creature's power is 2 or less or if you control another Lizard, gain control of that creature until end of turn, untap it, and it gains haste until end of turn.
        """,
    ) parsesTo abilities(
        Keyword.Trample,
        whenThisEnters(
            Step.If(
                Condition.Or(listOf(
                    Condition.ObjectMatches(Objects.Target("creature"), ObjectFilter.Power(Comparison.AtMost, one(2))),
                    Condition.Exists(ObjectFilter.And(listOf(ObjectFilter.Not(ObjectFilter.Is(Objects.Self)), sub("Lizard"), you)), atLeast = 1),
                )),
                then = listOf(
                    until(Objects.Target("creature"), Duration.EndOfTurn, Mechanic.ControlledBy(Players.You)),
                    Step.Do(Effect.Untap(Objects.Target("creature"))),
                    until(Objects.Target("creature"), Duration.EndOfTurn, Mechanic.GainAbilities(listOf(Keyword.Haste))),
                ),
            ),
            targets = listOf(slot("creature", creature)),
        ),
    )

    private val instantOrSorcery = ObjectFilter.Or(listOf(ObjectFilter.Type(CardType.Instant), ObjectFilter.Type(CardType.Sorcery)))
}
