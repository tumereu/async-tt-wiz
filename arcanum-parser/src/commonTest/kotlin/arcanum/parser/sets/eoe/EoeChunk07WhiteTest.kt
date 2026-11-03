package arcanum.parser.sets.eoe

import arcanum.api.*
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

/**
 * Edge of Eternities chunk 07: white cards: Equipment that makes and carries a token, exile until it leaves, two spells cast this turn, the second spell each turn, tapped creatures at end step, "If it has a +1/+1 counter on it", Auras on artifacts.
 */
class EoeChunk07WhiteTest {

    @Test
    fun backupThrusters() = card(
        name = "Backup Thrusters",
        cost = "{4}{W}",
        type = "Artifact — Equipment",
        text = """
            When this Equipment enters, create a 2/2 colorless Robot artifact creature token and attach this Equipment to it.
            Equipped creature gets +1/+2 and has flying.
            Equip {3} ({3}: Attach to target creature you control. Equip only as a sorcery.)
        """,
    ) parsesTo abilities(
        triggered(
            entersSelf,
            doing(Effect.CreateToken(token = robot, count = n(1), controller = Players.You, bind = "token")),
            doing(Effect.Attach(objects = Objects.Self, to = Recipients(objects = bound("token")))),
        ),
        static(pt(1, 2), has(Keyword.Flying), affects = Objects.AttachedTo(of = Objects.Self)),
        Keyword.Equip(cost = cost(mana("{3}"))),
    )

    @Test
    fun exilingRadiance() = card(
        name = "Exiling Radiance",
        cost = "{2}{W}",
        type = "Enchantment",
        text = "When this enchantment enters, exile target nonland permanent an opponent controls until this enchantment leaves the battlefield.",
    ) parsesTo abilities(
        triggered(
            entersSelf,
            doing(Effect.Move(objects = target("permanent"), to = Destination(zone = Zone.Exile), untilLeaves = Objects.Self)),
            targets = listOf(slot("permanent", and(not(land), permanentFilter, opponents))),
        ),
    )

    @Test
    fun pastTheSilence() = card(
        name = "Past the Silence",
        cost = "{3}{W}{W}",
        type = "Sorcery",
        text = "Exile all creatures and Spacecraft.",
    ) parsesTo abilities(
        spell(doing(Effect.Move(objects = Objects.Each(filter = or(creature, sub("Spacecraft"))), to = Destination(zone = Zone.Exile)))),
    )

    @Test
    fun glowlanceDevotee() = card(
        name = "Glowlance Devotee",
        cost = "{2}{W}",
        type = "Creature — Human Soldier",
        pt = "2/4",
        text = """
            Vigilance
            This creature gets +2/+0 as long as you've cast two or more spells this turn.
        """,
    ) parsesTo abilities(
        Keyword.Vigilance,
        static(
            pt(2, 0),
            condition = Condition.HappenedThisTurn(
                event = EventPattern.Cast(spell = ObjectFilter.Any, by = PlayerFilter.You),
                atLeast = 2,
                measure = Measure.Events,
            ),
        ),
    )

    @Test
    fun starhighApex() = card(
        name = "Starhigh Apex",
        cost = "{2}{W}",
        type = "Creature — Human Soldier",
        pt = "2/4",
        text = """
            Whenever you cast your second spell each turn, choose one —
            • Create two 1/1 white Human Soldier creature tokens.
            • Put a +1/+1 counter on each creature you control.
        """,
    ) parsesTo abilities(
        Ability.Triggered(
            trigger = Trigger.On(events = listOf(EventPattern.Cast(spell = ObjectFilter.Any, by = PlayerFilter.You)), nthThisTurn = 2),
            instructions = Instructions(
                modes = Modes(
                    options = listOf(
                        Mode(
                            instructions = Instructions(
                                steps = listOf(doing(Effect.CreateToken(token = soldier, count = n(2), controller = Players.You))),
                            ),
                        ),
                        Mode(
                            instructions = Instructions(
                                steps = listOf(
                                    doing(
                                        Effect.PutCounters(objects = Objects.Each(filter = and(creature, you)), kind = p1p1, amount = n(1)),
                                    ),
                                ),
                            ),
                        ),
                    ),
                    min = 1,
                    max = 1,
                    chooser = Players.You,
                ),
            ),
        ),
    )

    @Test
    fun daybreakSpearhead() = card(
        name = "Daybreak Spearhead",
        cost = "{5}{W}",
        type = "Creature — Human Knight",
        pt = "4/5",
        text = """
            Lifelink
            At the beginning of your end step, if you control two or more tapped creatures, put a +1/+1 counter on each creature you control other than this creature.
        """,
    ) parsesTo abilities(
        Keyword.Lifelink,
        triggered(
            on(EventPattern.StepBegins(step = TurnStep.End, whose = PlayerFilter.You)),
            doing(Effect.PutCounters(objects = Objects.Each(filter = and(creature, you, not(isSelf))), kind = p1p1, amount = n(1))),
            interveningIf = Condition.Exists(filter = and(tapped, creature, you), atLeast = 2),
        ),
    )

    @Test
    fun hangarLoaderDrone() = card(
        name = "Hangar Loader Drone",
        cost = "{1}{W}",
        type = "Artifact Creature — Robot",
        pt = "1/1",
        text = """
            This creature enters with a +1/+1 counter on it.
            When this creature dies, put its counters on target creature you control.
        """,
    ) parsesTo abilities(
        static(
            Mechanic.Replacement(
                event = EventPattern.ZoneChange(objects = isSelf, to = Zone.Battlefield),
                modify = listOf(Modification.EntersWithCounters(kind = p1p1, amount = n(1))),
            ),
        ),
        triggered(
            diesSelf,
            doing(
                Effect.MoveCounters(
                    from = Objects.Self,
                    to = target("creature"),
                    kind = null,
                    amount = Value.Of(property = NumericProperty.Counters(kind = null), objects = Objects.Self, how = Combine.Single),
                ),
            ),
            targets = listOf(slot("creature", and(creature, you))),
        ),
    )

    @Test
    fun twinStarDisciples() = card(
        name = "Twin-Star Disciples",
        cost = "{2}{W}",
        type = "Creature — Human Soldier",
        pt = "2/2",
        text = """
            Double strike
            {5}: Creatures you control get +1/+1 until end of turn.
        """,
    ) parsesTo abilities(
        Keyword.DoubleStrike,
        activated(cost(mana("{5}")), untilEndOfTurn(Objects.Each(filter = and(creature, you)), pt(1, 1))),
    )

    @Test
    fun twinStarForm() = card(
        name = "Twin-Star Form",
        cost = "{1}{W}",
        type = "Instant",
        text = "Target creature you control gains double strike until end of turn. If it has a +1/+1 counter on it, draw a card.",
    ) parsesTo abilities(
        spell(
            untilEndOfTurn(target("creature"), has(Keyword.DoubleStrike)),
            Step.If(
                condition = Condition.ObjectMatches(
                    objects = target("creature"),
                    filter = ObjectFilter.HasCounter(kind = p1p1, atLeast = 1),
                ),
                then = listOf(doing(Effect.Draw(players = Players.You, count = n(1)))),
            ),
            targets = listOf(slot("creature", and(creature, you))),
        ),
    )

    @Test
    fun powersuitRescuer() = card(
        name = "Powersuit Rescuer",
        cost = "{2}{W}",
        type = "Creature — Human Soldier",
        pt = "2/2",
        text = """
            Flying
            When this creature enters, return up to one other target permanent you control to its owner's hand.
        """,
    ) parsesTo abilities(
        Keyword.Flying,
        triggered(
            entersSelf,
            doing(Effect.Move(objects = target("permanent"), to = Destination(zone = Zone.Hand))),
            targets = listOf(slot("permanent", and(not(isSelf), permanentFilter, you), min = 0)),
        ),
    )

    @Test
    fun launchBayMarshal() = card(
        name = "Launch-Bay Marshal",
        cost = "{2}{W}",
        type = "Creature — Human Soldier",
        pt = "3/3",
        text = "At the beginning of your end step, if you control two or more tapped creatures, you gain 2 life.",
    ) parsesTo abilities(
        triggered(
            on(EventPattern.StepBegins(step = TurnStep.End, whose = PlayerFilter.You)),
            doing(Effect.GainLife(players = Players.You, amount = n(2))),
            interveningIf = Condition.Exists(filter = and(tapped, creature, you), atLeast = 2),
        ),
    )

    @Test
    fun concentratedBarrage() = card(
        name = "Concentrated Barrage",
        cost = "{W}",
        type = "Instant",
        text = "Concentrated Barrage deals X damage to target attacking or blocking creature, where X is 2 plus the number of creatures and/or Spacecraft you control.",
    ) parsesTo abilities(
        spell(
            doing(
                Effect.DealDamage(
                    to = Recipients(objects = target("creature")),
                    amount = Value.Sum(
                        parts = listOf(n(2), Value.Count(objects = Objects.Each(filter = and(or(creature, sub("Spacecraft")), you)))),
                    ),
                    source = Objects.Self,
                ),
            ),
            targets = listOf(slot("creature", and(or(attacking, ObjectFilter.HasStatus(status = Status.Blocking)), creature))),
        ),
    )

    @Test
    fun solidLightPrison() = card(
        name = "Solid-Light Prison",
        cost = "{W}",
        type = "Enchantment — Aura",
        text = """
            Enchant artifact you control
            When this Aura enters, exile target creature an opponent controls until this Aura leaves the battlefield.
            Enchanted permanent has ward {1}.
        """,
    ) parsesTo abilities(
        Keyword.Enchant(what = TargetKind.Objects(filter = and(artifact, you))),
        triggered(
            entersSelf,
            doing(Effect.Move(objects = target("creature"), to = Destination(zone = Zone.Exile), untilLeaves = Objects.Self)),
            targets = listOf(slot("creature", and(creature, opponents))),
        ),
        static(has(Keyword.Ward(cost = cost(mana("{1}")))), affects = Objects.AttachedTo(of = Objects.Self)),
    )

    @Test
    fun laurelOfMerit() = card(
        name = "Laurel of Merit",
        cost = "{W}",
        type = "Sorcery",
        text = """
            Put a +1/+1 counter on target creature.
            Draw a card.
        """,
    ) parsesTo abilities(
        spell(
            doing(Effect.PutCounters(objects = target("creature"), kind = p1p1, amount = n(1))),
            doing(Effect.Draw(players = Players.You, count = n(1))),
            targets = listOf(slot("creature", creature)),
        ),
    )

    @Test
    fun decoratedKnightMarshal() = card(
        name = "Decorated Knight-Marshal",
        cost = "{1}{W}",
        type = "Creature — Human Advisor Knight",
        pt = "1/1",
        text = """
            When this creature enters, create a 1/1 white Human Soldier creature token.
            {4}{W}{W}, Sacrifice this creature: Search your library for an Equipment card, put it onto the battlefield, then shuffle.
        """,
    ) parsesTo abilities(
        triggered(entersSelf, doing(Effect.CreateToken(token = soldier, count = n(1), controller = Players.You))),
        activated(
            cost(mana("{4}{W}{W}"), CostPart.Sacrifice(what = CostObjects.Self)),
            doing(
                Effect.Search(
                    library = Players.You,
                    filter = sub("Equipment"),
                    max = n(1),
                    zones = listOf(Zone.Library),
                    bind = "found",
                    searcher = Players.You,
                ),
            ),
            doing(Effect.Move(objects = bound("found"), to = Destination(zone = Zone.Battlefield))),
            doing(Effect.Shuffle(library = Players.You)),
        ),
    )
}
