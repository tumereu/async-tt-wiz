package arcanum.parser.sets.eoe

import arcanum.api.*
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

/**
 * Edge of Eternities chunk 15: artifacts and lands: shock lands, a sacrifice-unless land, the planeswalker with an emblem, cost reductions by lands and Spacecraft attacks, controlling a turn from an Equipment, life set to half the starting total, poison on combat damage.
 */
class EoeChunk15ArtifactsAndLandsTest {

    @Test
    fun everyPathCodex() = card(
        name = "Every-Path Codex",
        cost = "{3}",
        type = "Artifact",
        text = """
            {T}: Add one mana of any color.
            {7}, {T}, Sacrifice this artifact: Draw X cards, where X is the number of differently named lands you control.
        """,
    ) parsesTo abilities(
        activated(cost(tap), doing(Effect.AddMana(mana = ManaProduction(kind = ManaKind.Choose(amount = n(1))), players = Players.You))),
        activated(
            cost(mana("{7}"), tap, CostPart.Sacrifice(what = CostObjects.Self)),
            doing(
                Effect.Draw(
                    players = Players.You,
                    count = Value.CountDistinct(objects = Objects.Each(filter = and(land, you)), quality = SharedQuality.Name),
                ),
            ),
        ),
    )

    @Test
    fun spawningLagoon() = card(
        name = "Spawning Lagoon",
        cost = null,
        type = "Land — Forest Island",
        text = """
            ({T}: Add {G} or {U}.)
            As this land enters, you may pay 2 life. If you don't, it enters tapped.
        """,
    ) parsesTo abilities(
        static(
            Mechanic.Replacement(
                event = EventPattern.ZoneChange(objects = isSelf, to = Zone.Battlefield),
                modify = listOf(
                    Modification.AsEnters(
                        steps = listOf(
                            Step.Pay(cost = cost(CostPart.PayLife(amount = n(2))), payer = Players.You, bind = "paid"),
                            Step.If(
                                condition = Condition.Not(condition = Condition.Did(name = "paid")),
                                then = listOf(Step.Modify(modification = Modification.EntersTapped)),
                            ),
                        ),
                    ),
                ),
            ),
        ),
    )

    @Test
    fun silversteelPal() = card(
        name = "Silversteel Pal",
        cost = "{2}",
        type = "Artifact Creature — Dog",
        pt = "2/1",
        text = """
            Whenever this creature becomes tapped, you gain 1 life.
            {2}, {T}: Put target card from a graveyard on the bottom of its owner's library.
        """,
    ) parsesTo abilities(
        triggered(on(EventPattern.Tap(objects = isSelf)), doing(Effect.GainLife(players = Players.You, amount = n(1)))),
        activated(
            cost(mana("{2}"), tap),
            doing(Effect.Move(objects = target("card"), to = Destination(zone = Zone.Library, libraryPosition = -1))),
            targets = listOf(slot("card", and(cardFilter, inZone(Zone.Graveyard)))),
        ),
    )

    @Test
    fun helmDeck() = card(
        name = "Helm Deck",
        cost = null,
        type = "Land",
        text = """
            This land enters tapped.
            When this land enters, sacrifice it unless you tap an untapped permanent you control.
            {T}: Add one mana of any color.
        """,
    ) parsesTo abilities(
        static(
            Mechanic.Replacement(
                event = EventPattern.ZoneChange(objects = isSelf, to = Zone.Battlefield),
                modify = listOf(Modification.EntersTapped),
            ),
        ),
        triggered(
            entersSelf,
            Step.Pay(
                cost = cost(CostPart.TapObjects(what = CostObjects.Choose(filter = permanentFilter, count = n(1)))),
                payer = Players.You,
                bind = "paid",
            ),
            Step.If(
                condition = Condition.Not(condition = Condition.Did(name = "paid")),
                then = listOf(doing(Effect.Sacrifice(objects = Objects.Self))),
            ),
        ),
        activated(cost(tap), doing(Effect.AddMana(mana = ManaProduction(kind = ManaKind.Choose(amount = n(1))), players = Players.You))),
    )

    @Test
    fun faithlessChapel() = card(
        name = "Faithless Chapel",
        cost = null,
        type = "Land — Plains Swamp",
        text = """
            ({T}: Add {W} or {B}.)
            As this land enters, you may pay 2 life. If you don't, it enters tapped.
        """,
    ) parsesTo abilities(
        static(
            Mechanic.Replacement(
                event = EventPattern.ZoneChange(objects = isSelf, to = Zone.Battlefield),
                modify = listOf(
                    Modification.AsEnters(
                        steps = listOf(
                            Step.Pay(cost = cost(CostPart.PayLife(amount = n(2))), payer = Players.You, bind = "paid"),
                            Step.If(
                                condition = Condition.Not(condition = Condition.Did(name = "paid")),
                                then = listOf(Step.Modify(modification = Modification.EntersTapped)),
                            ),
                        ),
                    ),
                ),
            ),
        ),
    )

    @Test
    fun rationBrick() = card(
        name = "Ration Brick",
        cost = "{1}",
        type = "Artifact — Food",
        text = """
            Indestructible (Effects that say "destroy" don't destroy this artifact.)
            {2}, {T}, Sacrifice this artifact: You gain 3 life.
            When this artifact is put into a graveyard from the battlefield, draw a card.
        """,
    ) parsesTo abilities(
        Keyword.Indestructible,
        activated(
            cost(mana("{2}"), tap, CostPart.Sacrifice(what = CostObjects.Self)),
            doing(Effect.GainLife(players = Players.You, amount = n(3))),
        ),
        triggered(diesSelf, doing(Effect.Draw(players = Players.You, count = n(1)))),
    )

    @Test
    fun hallowedSmeltery() = card(
        name = "Hallowed Smeltery",
        cost = null,
        type = "Land — Mountain Plains",
        text = """
            ({T}: Add {R} or {W}.)
            As this land enters, you may pay 2 life. If you don't, it enters tapped.
        """,
    ) parsesTo abilities(
        static(
            Mechanic.Replacement(
                event = EventPattern.ZoneChange(objects = isSelf, to = Zone.Battlefield),
                modify = listOf(
                    Modification.AsEnters(
                        steps = listOf(
                            Step.Pay(cost = cost(CostPart.PayLife(amount = n(2))), payer = Players.You, bind = "paid"),
                            Step.If(
                                condition = Condition.Not(condition = Condition.Did(name = "paid")),
                                then = listOf(Step.Modify(modification = Modification.EntersTapped)),
                            ),
                        ),
                    ),
                ),
            ),
        ),
    )

    @Test
    fun remoteStarsmithy() = card(
        name = "Remote Starsmithy",
        cost = null,
        type = "Land",
        text = """
            {T}: Add {C}.
            {2}, {T}, Tap X untapped artifacts you control: Target creature gets +X/+0 until end of turn. Activate only as a sorcery.
            {5}, {T}: Create a 2/2 colorless Robot artifact creature token.
        """,
    ) parsesTo abilities(
        activated(
            cost(tap),
            doing(Effect.AddMana(mana = ManaProduction(kind = ManaKind.Fixed(mana = ManaCost.parse("{C}")!!)), players = Players.You)),
        ),
        Ability.Activated(
            cost = cost(
                mana("{2}"),
                tap,
                CostPart.TapObjects(what = CostObjects.Choose(filter = artifact, count = Value.Announced(name = "X"))),
            ),
            instructions = Instructions(
                targets = listOf(slot("creature", creature)),
                steps = listOf(
                    untilEndOfTurn(target("creature"), Mechanic.ModifyPowerToughness(power = Value.Announced(name = "X"), toughness = n(0))),
                ),
                announced = listOf("X"),
            ),
            rules = asSorcery,
        ),
        activated(cost(mana("{5}"), tap), doing(Effect.CreateToken(token = robot, count = n(1), controller = Players.You))),
    )

    @Test
    fun tramplingHollow() = card(
        name = "Trampling Hollow",
        cost = null,
        type = "Land — Mountain Forest",
        text = """
            ({T}: Add {R} or {G}.)
            As this land enters, you may pay 2 life. If you don't, it enters tapped.
        """,
    ) parsesTo abilities(
        static(
            Mechanic.Replacement(
                event = EventPattern.ZoneChange(objects = isSelf, to = Zone.Battlefield),
                modify = listOf(
                    Modification.AsEnters(
                        steps = listOf(
                            Step.Pay(cost = cost(CostPart.PayLife(amount = n(2))), payer = Players.You, bind = "paid"),
                            Step.If(
                                condition = Condition.Not(condition = Condition.Did(name = "paid")),
                                then = listOf(Step.Modify(modification = Modification.EntersTapped)),
                            ),
                        ),
                    ),
                ),
            ),
        ),
    )

    @Test
    fun scoutingAutomaton() = card(
        name = "Scouting Automaton",
        cost = "{4}",
        type = "Artifact Creature — Robot",
        pt = "1/3",
        text = """
            Flying
            Hexproof (This creature can't be the target of spells or abilities your opponents control.)
            {10}, Sacrifice this creature: It deals 3 damage to any target. Target player draws three cards and gains 3 life. This ability costs {X} less to activate, where X is the number of differently named lands you control.
        """,
    ) parsesTo abilities(
        Keyword.Flying,
        Keyword.Hexproof,
        activated(
            cost(mana("{10}"), CostPart.Sacrifice(what = CostObjects.Self)),
            doing(Effect.DealDamage(to = toAny, amount = n(3), source = Objects.Self)),
            doing(Effect.Draw(players = Players.Target(slot = "player"), count = n(3))),
            doing(Effect.GainLife(players = Players.Target(slot = "player"), amount = n(3))),
            targets = listOf(
                anyTarget,
                TargetSlot(name = "player", what = TargetKind.Players(filter = PlayerFilter.Any), min = n(1), max = n(1)),
            ),
            rules = ActivationRules(
                timing = Timing.Any,
                activatedBy = PlayerFilter.You,
                costChange = CostChange.Less(
                    mana = ManaCost.parse("{1}")!!,
                    times = Value.CountDistinct(objects = Objects.Each(filter = and(land, you)), quality = SharedQuality.Name),
                ),
            ),
        ),
    )

    @Test
    fun vexmorRuthlessCommander() = card(
        name = "Vexmor, Ruthless Commander",
        cost = "{3}",
        type = "Legendary Planeswalker — Tezzeret",
        loyalty = "4",
        text = """
            Whenever an artifact you control enters, put a loyalty counter on Vexmor.
            [0]: Untap target artifact or creature. If it's an artifact creature, put a +1/+1 counter on it.
            [−3]: Search your library for an artifact card with mana value 1 or less, reveal it, put it into your hand, then shuffle.
            [−7]: You get an emblem with "At the beginning of combat on your turn, put three +1/+1 counters on target artifact you control. If it's not a creature, it becomes a 0/0 Robot artifact creature."
        """,
    ) parsesTo abilities(
        triggered(
            on(EventPattern.ZoneChange(objects = and(artifact, you), to = Zone.Battlefield)),
            doing(Effect.PutCounters(objects = Objects.Self, kind = CounterKind.Loyalty, amount = n(1))),
        ),
        activated(
            cost(CostPart.Loyalty(amount = n(0))),
            doing(Effect.Untap(objects = target("permanent"))),
            Step.If(
                condition = Condition.ObjectMatches(objects = target("permanent"), filter = and(artifact, creature)),
                then = listOf(doing(Effect.PutCounters(objects = target("permanent"), kind = p1p1, amount = n(1)))),
            ),
            targets = listOf(slot("permanent", or(artifact, creature))),
        ),
        activated(
            cost(CostPart.Loyalty(amount = n(-3))),
            doing(
                Effect.Search(
                    library = Players.You,
                    filter = and(artifact, ObjectFilter.ManaValue(comparison = Comparison.AtMost, value = n(1))),
                    max = n(1),
                    reveal = true,
                    zones = listOf(Zone.Library),
                    bind = "found",
                    searcher = Players.You,
                ),
            ),
            doing(Effect.Move(objects = bound("found"), to = Destination(zone = Zone.Hand))),
            doing(Effect.Shuffle(library = Players.You)),
        ),
        activated(
            cost(CostPart.Loyalty(amount = n(-7))),
            doing(
                Effect.CreateEmblem(
                    abilities = listOf(
                        triggered(
                            on(EventPattern.StepBegins(step = TurnStep.BeginningOfCombat, whose = PlayerFilter.You)),
                            doing(Effect.PutCounters(objects = target("artifact"), kind = p1p1, amount = n(3))),
                            Step.If(
                                condition = Condition.Not(
                                    condition = Condition.ObjectMatches(objects = target("artifact"), filter = creature),
                                ),
                                then = listOf(
                                    doing(
                                        Effect.ApplyMechanics(
                                            mechanics = listOf(
                                                Mechanic.SetTypes(
                                                    add = listOf(CardType.Artifact, CardType.Creature),
                                                    addSubtypes = listOf(SubtypeRef.Literal(subtype = Subtype("Robot"))),
                                                ),
                                                Mechanic.SetPowerToughness(power = n(0), toughness = n(0)),
                                            ),
                                            affects = Affected.Objects(objects = target("artifact")),
                                            duration = Duration.Indefinitely,
                                        ),
                                    ),
                                ),
                            ),
                            targets = listOf(slot("artifact", and(artifact, you))),
                        ),
                    ),
                    players = Players.You,
                ),
            ),
        ),
    )

    @Test
    fun wondersteelMissile() = card(
        name = "Wondersteel Missile",
        cost = "{1}",
        type = "Artifact",
        text = "{6}, {T}, Sacrifice this artifact: Destroy target nonland permanent. This ability costs {3} less to activate if you attacked with a Spacecraft this turn.",
    ) parsesTo abilities(
        activated(
            cost(mana("{6}"), tap, CostPart.Sacrifice(what = CostObjects.Self)),
            doing(Effect.Destroy(objects = target("permanent"))),
            targets = listOf(slot("permanent", and(not(land), permanentFilter))),
            rules = ActivationRules(
                timing = Timing.Any,
                activatedBy = PlayerFilter.You,
                costChange = CostChange.Less(
                    mana = ManaCost.parse("{3}")!!,
                    times = Value.Min(
                        parts = listOf(
                            Value.EventsThisTurn(
                                event = EventPattern.Attack(attacker = and(sub("Spacecraft"), you)),
                                measure = Measure.Events,
                            ),
                            n(1),
                        ),
                    ),
                ),
            ),
        ),
    )

    @Test
    fun theSovereignBangle() = card(
        name = "The Sovereign Bangle",
        cost = "{2}",
        type = "Legendary Artifact — Equipment",
        text = """
            Equipped creature gets +1/+1 and has "{15}, Exile The Sovereign Bangle: You control target opponent during their next turn. This ability costs {X} less to activate, where X is this creature's power. Activate only as a sorcery." (You see all cards that player could see and make all decisions for them.)
            Equip {1}
        """,
    ) parsesTo abilities(
        static(
            pt(1, 1),
            has(
                activated(
                    cost(mana("{15}"), CostPart.Exile(what = CostObjects.These(objects = Objects.Granter), from = Zone.Battlefield)),
                    doing(Effect.ControlNextTurn(players = Players.Target(slot = "opponent"), controller = Players.You)),
                    targets = listOf(
                        TargetSlot(name = "opponent", what = TargetKind.Players(filter = PlayerFilter.Opponent), min = n(1), max = n(1)),
                    ),
                    rules = ActivationRules(
                        timing = Timing.Sorcery,
                        activatedBy = PlayerFilter.You,
                        costChange = CostChange.Less(
                            mana = ManaCost.parse("{1}")!!,
                            times = Value.Of(property = NumericProperty.Power, objects = Objects.Self, how = Combine.Single),
                        ),
                    ),
                ),
            ),
            affects = Objects.AttachedTo(of = Objects.Self),
        ),
        Keyword.Equip(cost = cost(mana("{1}"))),
    )

    @Test
    fun theFinalityStone() = card(
        name = "The Finality Stone",
        cost = "{7}",
        type = "Legendary Artifact",
        text = """
            Whenever you play a land or cast a spell, draw a card.
            At the beginning of your end step, your life total becomes half your starting life total, rounded up.
        """,
    ) parsesTo abilities(
        triggered(
            Trigger.On(
                events = listOf(
                    EventPattern.PlayLand(lands = ObjectFilter.Any, players = PlayerFilter.You),
                    EventPattern.Cast(spell = ObjectFilter.Any, by = PlayerFilter.You),
                ),
            ),
            doing(Effect.Draw(players = Players.You, count = n(1))),
        ),
        triggered(
            on(EventPattern.StepBegins(step = TurnStep.End, whose = PlayerFilter.You)),
            doing(
                Effect.SetLife(
                    players = Players.You,
                    amount = Value.Half(value = Value.StartingLife(players = Players.You), roundUp = true),
                ),
            ),
        ),
    )

    @Test
    fun hummingBroodvat() = card(
        name = "Humming Broodvat",
        cost = "{6}",
        type = "Artifact",
        text = """
            Affinity for Slivers (This spell costs {1} less to cast for each Sliver you control.)
            Slivers you control have double strike and haste.
            At the beginning of your upkeep, create two 1/1 colorless Sliver creature tokens.
        """,
    ) parsesTo abilities(
        Keyword.Affinity(forWhat = sub("Sliver"), printed = "Slivers"),
        static(has(Keyword.DoubleStrike, Keyword.Haste), affects = Objects.Each(filter = and(sub("Sliver"), you))),
        triggered(
            on(EventPattern.StepBegins(step = TurnStep.Upkeep, whose = PlayerFilter.You)),
            doing(
                Effect.CreateToken(
                    token = TokenSpec.Defined(
                        face = FaceDefinition(
                            name = "Sliver Token",
                            colorIndicator = listOf(),
                            types = listOf(CardType.Creature),
                            subtypes = listOf(Subtype("Sliver")),
                            power = PrintedNumber.Fixed(value = 1),
                            toughness = PrintedNumber.Fixed(value = 1),
                        ),
                    ),
                    count = n(2),
                    controller = Players.You,
                ),
            ),
        ),
    )

    @Test
    fun toxicHushBot() = card(
        name = "Toxic Hush-Bot",
        cost = "{3}",
        type = "Artifact Creature — Robot Assassin",
        pt = "2/3",
        text = "Whenever a nontoken artifact creature you control deals combat damage to a player, that player gets two poison counters. (A player with ten or more poison counters loses the game.)",
    ) parsesTo abilities(
        triggered(
            on(
                EventPattern.Damage(
                    source = and(not(token), artifact, creature, you),
                    to = RecipientFilter(players = PlayerFilter.Any),
                    combat = true,
                ),
            ),
            doing(Effect.GivePlayerCounters(players = Players.EventPlayer, kind = CounterKind.Poison, amount = n(2))),
        ),
    )

    @Test
    fun sunkenOssuary() = card(
        name = "Sunken Ossuary",
        cost = null,
        type = "Land — Island Swamp",
        text = """
            ({T}: Add {U} or {B}.)
            As this land enters, you may pay 2 life. If you don't, it enters tapped.
        """,
    ) parsesTo abilities(
        static(
            Mechanic.Replacement(
                event = EventPattern.ZoneChange(objects = isSelf, to = Zone.Battlefield),
                modify = listOf(
                    Modification.AsEnters(
                        steps = listOf(
                            Step.Pay(cost = cost(CostPart.PayLife(amount = n(2))), payer = Players.You, bind = "paid"),
                            Step.If(
                                condition = Condition.Not(condition = Condition.Did(name = "paid")),
                                then = listOf(Step.Modify(modification = Modification.EntersTapped)),
                            ),
                        ),
                    ),
                ),
            ),
        ),
    )
}
