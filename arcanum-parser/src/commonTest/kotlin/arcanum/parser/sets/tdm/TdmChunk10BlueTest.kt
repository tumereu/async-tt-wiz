package arcanum.parser.sets.tdm

import arcanum.api.*
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.cardFaces
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

/**
 * Tarkir: Dragonstorm chunk 10: blue cards: base power and toughness during your turn, milling equal to power, a mill that refills the library, activation only during your turn and only if a card left the graveyard.
 */
class TdmChunk10BlueTest {
    @Test
    fun wardSculptor() = card(
        name = "Ward Sculptor",
        cost = "{3}{U}",
        type = "Creature — Bird Wizard",
        pt = "2/3",
        text = """
            Flying
            Ward {2} (Whenever this creature becomes the target of a spell or ability an opponent controls, counter it unless that player pays {2}.)
            At the beginning of your upkeep, you may exile two cards from your graveyard. If you do, put a +1/+1 counter on this creature.
        """,
    ) parsesTo abilities(
        Keyword.Flying,
        Keyword.Ward(cost = Cost(parts = listOf(mana("{2}")))),
        Ability.Triggered(
            trigger = Trigger.On(events = listOf(EventPattern.StepBegins(step = TurnStep.Upkeep, whose = PlayerFilter.You))),
            instructions = Instructions(
                steps = listOf(
                    Step.Pay(
                        cost = Cost(
                            parts = listOf(CostPart.Exile(what = CostObjects.Choose(filter = cardFilter, count = n(2)), from = Zone.Graveyard)),
                        ),
                        payer = Players.You,
                        bind = "paid",
                    ),
                    Step.If(
                        condition = Condition.Did(name = "paid"),
                        then = listOf(doing(Effect.PutCounters(objects = Objects.Self, kind = p1p1, amount = n(1)))),
                    ),
                ),
            ),
        ),
    )

    @Test
    fun lumberingStormshell() = card(
        name = "Lumbering Stormshell",
        cost = "{3}{U}{U}",
        type = "Creature — Turtle",
        pt = "5/9",
        text = """
            Ward {2}
            Whenever this creature attacks, put three stun counters on it and draw three cards. (If a permanent with a stun counter would become untapped, remove one from it instead.)
            Whenever you cast a Turtle spell, untap this creature.
        """,
    ) parsesTo abilities(
        Keyword.Ward(cost = Cost(parts = listOf(mana("{2}")))),
        Ability.Triggered(
            trigger = attacksSelf,
            instructions = Instructions(
                steps = listOf(
                    doing(Effect.PutCounters(objects = Objects.Self, kind = CounterKind.Stun, amount = n(3))),
                    doing(Effect.Draw(players = Players.You, count = n(3))),
                ),
            ),
        ),
        Ability.Triggered(
            trigger = Trigger.On(events = listOf(EventPattern.Cast(spell = sub("Turtle"), by = PlayerFilter.You))),
            instructions = Instructions(steps = listOf(doing(Effect.Untap(objects = Objects.Self)))),
        ),
    )

    @Test
    fun bafflingBlizzard() = card(
        name = "Baffling Blizzard",
        cost = "{4}{U}{U}",
        type = "Instant",
        text = "Draw three cards. Creatures your opponents control get -3/-0 until end of turn.",
    ) parsesTo abilities(
        Ability.Spell(
            instructions = Instructions(
                steps = listOf(
                    doing(Effect.Draw(players = Players.You, count = n(3))),
                    doing(
                        Effect.ApplyMechanics(
                            mechanics = listOf(Mechanic.ModifyPowerToughness(power = n(-3), toughness = n(0))),
                            affects = Affected.Objects(objects = Objects.Each(filter = and(creature, opponents))),
                            duration = Duration.EndOfTurn,
                        ),
                    ),
                ),
            ),
        ),
    )

    @Test
    fun essenceTether() = card(
        name = "Essence Tether",
        cost = "{2}{U}",
        type = "Artifact",
        text = """
            At the beginning of your upkeep, surveil 1. (Look at the top card of your library. You may put it into your graveyard.)
            {T}: Create a 2/2 black Zombie Druid creature token. Activate only during your turn and only if a card left your graveyard this turn.
        """,
    ) parsesTo abilities(
        Ability.Triggered(
            trigger = Trigger.On(events = listOf(EventPattern.StepBegins(step = TurnStep.Upkeep, whose = PlayerFilter.You))),
            instructions = Instructions(steps = listOf(doing(Effect.Surveil(players = Players.You, count = n(1))))),
        ),
        Ability.Activated(
            cost = Cost(parts = listOf(tap)),
            instructions = Instructions(
                steps = listOf(
                    doing(
                        Effect.CreateToken(
                            token = TokenSpec.Defined(
                                face = FaceDefinition(
                                    name = "Zombie Druid Token",
                                    colorIndicator = listOf(Color.Black),
                                    types = listOf(CardType.Creature),
                                    subtypes = listOf(Subtype(name = "Zombie"), Subtype(name = "Druid")),
                                    power = PrintedNumber.Fixed(value = 2),
                                    toughness = PrintedNumber.Fixed(value = 2),
                                ),
                            ),
                            count = n(1),
                            controller = Players.You,
                        ),
                    ),
                ),
            ),
            rules = ActivationRules(
                timing = Timing.Any,
                activatedBy = PlayerFilter.You,
                condition = Condition.And(
                    all = listOf(
                        Condition.YourTurn,
                        Condition.HappenedThisTurn(
                            event = EventPattern.ZoneChange(objects = and(cardFilter, ownedByYou), from = Zone.Graveyard),
                            atLeast = 1,
                            measure = Measure.Events,
                        ),
                    ),
                ),
            ),
        ),
    )

    @Test
    fun cleanSlateHex() = card(
        name = "Clean Slate Hex",
        cost = "{1}{U}",
        type = "Enchantment — Aura",
        text = """
            Flash
            Enchant creature
            Enchanted creature gets -5/-0 and loses all abilities.
        """,
    ) parsesTo abilities(
        Keyword.Flash,
        Keyword.Enchant(what = TargetKind.Objects(filter = creature)),
        Ability.Static(
            mechanics = listOf(Mechanic.ModifyPowerToughness(power = n(-5), toughness = n(0)), Mechanic.LoseAbilities()),
            affects = Affected.Objects(objects = Objects.AttachedTo(of = Objects.Self)),
        ),
    )

    @Test
    fun chasteningElder() = card(
        name = "Chastening Elder",
        cost = "{U}",
        type = "Creature — Human Monk",
        pt = "1/2",
        text = """
            Flash
            When this creature enters, target creature an opponent controls gets -2/-0 until end of turn.
        """,
    ) parsesTo abilities(
        Keyword.Flash,
        Ability.Triggered(
            trigger = entersSelf,
            instructions = Instructions(
                targets = listOf(TargetSlot(name = "creature", what = TargetKind.Objects(filter = and(creature, opponents)), min = n(1), max = n(1))),
                steps = listOf(
                    doing(
                        Effect.ApplyMechanics(
                            mechanics = listOf(Mechanic.ModifyPowerToughness(power = n(-2), toughness = n(0))),
                            affects = Affected.Objects(objects = target("creature")),
                            duration = Duration.EndOfTurn,
                        ),
                    ),
                ),
            ),
        ),
    )

    @Test
    fun frostridgeSerpent() = card(
        name = "Frostridge Serpent",
        cost = "{4}{U}",
        type = "Creature — Serpent",
        pt = "3/3",
        text = "When this creature enters, return target creature an opponent controls to its owner's hand.",
    ) parsesTo abilities(
        Ability.Triggered(
            trigger = entersSelf,
            instructions = Instructions(
                targets = listOf(TargetSlot(name = "creature", what = TargetKind.Objects(filter = and(creature, opponents)), min = n(1), max = n(1))),
                steps = listOf(doing(Effect.Move(objects = target("creature"), to = Destination(zone = Zone.Hand)))),
            ),
        ),
    )

    @Test
    fun riverbankTrawlers() = card(
        name = "Riverbank Trawlers",
        cost = "{2}{U}",
        type = "Creature — Human Citizen",
        pt = "3/2",
        text = "When this creature enters, you may exile a creature card from your graveyard. When you do, return target instant or sorcery card from your graveyard to your hand.",
    ) parsesTo abilities(
        Ability.Triggered(
            trigger = entersSelf,
            instructions = Instructions(
                steps = listOf(
                    Step.Pay(
                        cost = Cost(
                            parts = listOf(CostPart.Exile(what = CostObjects.Choose(filter = creature, count = n(1)), from = Zone.Graveyard)),
                        ),
                        payer = Players.You,
                        bind = "paid",
                    ),
                    Step.If(
                        condition = Condition.Did(name = "paid"),
                        then = listOf(
                            doing(
                                Effect.ReflexiveTrigger(
                                    instructions = Instructions(
                                        targets = listOf(
                                            TargetSlot(
                                                name = "card",
                                                what = TargetKind.Objects(filter = and(or(instant, sorcery), inZone(Zone.Graveyard), ownedByYou)),
                                                min = n(1),
                                                max = n(1),
                                            ),
                                        ),
                                        steps = listOf(doing(Effect.Move(objects = target("card"), to = Destination(zone = Zone.Hand)))),
                                    ),
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        ),
    )

    @Test
    fun resoundingStrikeMastery() = card(
        name = "Resounding Strike Mastery",
        cost = "{U}",
        type = "Enchantment — Aura",
        text = """
            Enchant creature
            When this Aura enters, tap enchanted creature.
            Enchanted creature doesn't untap during its controller's untap step.
            Enchanted creature has "{5}: Untap this creature."
        """,
    ) parsesTo abilities(
        Keyword.Enchant(what = TargetKind.Objects(filter = creature)),
        Ability.Triggered(
            trigger = entersSelf,
            instructions = Instructions(steps = listOf(doing(Effect.Tap(objects = Objects.AttachedTo(of = Objects.Self))))),
        ),
        Ability.Static(
            mechanics = listOf(Mechanic.DoesntUntap),
            affects = Affected.Objects(objects = Objects.AttachedTo(of = Objects.Self)),
        ),
        Ability.Static(
            mechanics = listOf(
                Mechanic.GainAbilities(
                    abilities = listOf(
                        Ability.Activated(
                            cost = Cost(parts = listOf(mana("{5}"))),
                            instructions = Instructions(steps = listOf(doing(Effect.Untap(objects = Objects.Self)))),
                            rules = anyTime,
                        ),
                    ),
                ),
            ),
            affects = Affected.Objects(objects = Objects.AttachedTo(of = Objects.Self)),
        ),
    )

    @Test
    fun streamstepTechnique() = card(
        name = "Streamstep Technique",
        cost = "{3}{U}",
        type = "Instant",
        text = """
            Choose one —
            • The owner of target nonland permanent puts it on their choice of the top or bottom of their library.
            • Counter target noncreature spell.
        """,
    ) parsesTo abilities(
        Ability.Spell(
            instructions = Instructions(
                modes = Modes(
                    options = listOf(
                        Mode(
                            instructions = Instructions(
                                targets = listOf(
                                    TargetSlot(
                                        name = "permanent",
                                        what = TargetKind.Objects(filter = and(not(land), permanentFilter)),
                                        min = n(1),
                                        max = n(1),
                                    ),
                                ),
                                steps = listOf(
                                    Step.Choose(
                                        choice = Choice.Option(options = listOf("top", "bottom")),
                                        bind = "position",
                                        chooser = Players.OwnerOf(objects = target("permanent")),
                                    ),
                                    Step.If(
                                        condition = Condition.Chose(name = "position", option = "top"),
                                        then = listOf(
                                            doing(
                                                Effect.Move(
                                                    objects = target("permanent"),
                                                    to = Destination(zone = Zone.Library, libraryPosition = 0),
                                                ),
                                            ),
                                        ),
                                        otherwise = listOf(
                                            doing(
                                                Effect.Move(
                                                    objects = target("permanent"),
                                                    to = Destination(zone = Zone.Library, libraryPosition = -1),
                                                ),
                                            ),
                                        ),
                                    ),
                                ),
                            ),
                            pawprints = 0,
                        ),
                        Mode(
                            instructions = Instructions(
                                targets = listOf(
                                    TargetSlot(
                                        name = "spell",
                                        what = TargetKind.Objects(filter = and(not(creature), spellFilter)),
                                        min = n(1),
                                        max = n(1),
                                    ),
                                ),
                                steps = listOf(doing(Effect.Counter(objects = target("spell")))),
                            ),
                            pawprints = 0,
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
    fun gravecourtAppraiser() = card(
        name = "Gravecourt Appraiser",
        cost = "{2}{U}",
        type = "Creature — Zombie Advisor",
        pt = "2/1",
        text = "When this creature enters, look at the top two cards of your library. Put one of them into your hand and the other into your graveyard.",
    ) parsesTo abilities(
        Ability.Triggered(
            trigger = entersSelf,
            instructions = Instructions(
                steps = listOf(
                    doing(
                        Effect.LookAt(
                            objects = Objects.LibraryCards(of = Players.You, count = n(2), fromTop = true),
                            players = Players.You,
                            bind = "looked",
                        ),
                    ),
                    Step.Choose(
                        choice = Choice.Objects(filter = ObjectFilter.Any, min = n(1), max = n(1), from = bound("looked")),
                        bind = "chosen",
                        chooser = Players.You,
                    ),
                    doing(Effect.Move(objects = bound("chosen"), to = Destination(zone = Zone.Hand))),
                    doing(
                        Effect.Move(
                            objects = Objects.Except(base = bound("looked"), minus = bound("chosen")),
                            to = Destination(zone = Zone.Graveyard),
                        ),
                    ),
                ),
            ),
        ),
    )

    @Test
    fun thawwaterStag() = card(
        name = "Thawwater Stag",
        cost = "{3}{U}",
        type = "Creature — Elemental Elk",
        pt = "2/5",
        text = """
            Vigilance
            During your turn, this creature has base power and toughness 5/2.
            {5}{U}{U}: This creature can't be blocked this turn.
        """,
    ) parsesTo abilities(
        Keyword.Vigilance,
        Ability.Static(
            mechanics = listOf(Mechanic.SetPowerToughness(power = n(5), toughness = n(2))),
            affects = Affected.Objects(objects = Objects.Self),
            condition = Condition.YourTurn,
        ),
        Ability.Activated(
            cost = Cost(parts = listOf(mana("{5}{U}{U}"))),
            instructions = Instructions(
                steps = listOf(
                    doing(
                        Effect.ApplyMechanics(
                            mechanics = listOf(Mechanic.CantBeBlocked),
                            affects = Affected.Objects(objects = Objects.Self),
                            duration = Duration.EndOfTurn,
                        ),
                    ),
                ),
            ),
            rules = anyTime,
        ),
    )

    @Test
    fun phantomRefusal() = card(
        name = "Phantom Refusal",
        cost = "{X}{U}",
        type = "Instant",
        text = """
            This spell costs {1} less to cast for each creature you control with power 4 or greater.
            Counter target spell unless its controller pays {X}.
        """,
    ) parsesTo abilities(
        Ability.Static(
            mechanics = listOf(
                Mechanic.CostChange(
                    what = isSelf,
                    change = CostChange.Less(
                        mana = ManaCost.parse("{1}")!!,
                        times = Value.Count(
                            objects = Objects.Each(filter = and(creature, you, ObjectFilter.Power(comparison = Comparison.AtLeast, value = n(4)))),
                        ),
                    ),
                    minimum = 0,
                ),
            ),
            affects = Affected.Objects(objects = Objects.Self),
        ),
        Ability.Spell(
            instructions = Instructions(
                targets = listOf(TargetSlot(name = "spell", what = TargetKind.Objects(filter = spellFilter), min = n(1), max = n(1))),
                steps = listOf(
                    Step.Pay(
                        // CR 107.3a (set check): the X announced as the spell was cast
                        cost = Cost(parts = listOf(CostPart.Mana(cost = ManaCost.parse("{X}")!!, x = Value.Announced(name = "X")))),
                        payer = Players.ControllerOf(objects = target("spell")),
                        bind = "paid",
                    ),
                    Step.If(
                        condition = Condition.Not(condition = Condition.Did(name = "paid")),
                        then = listOf(doing(Effect.Counter(objects = target("spell")))),
                    ),
                ),
                announced = listOf("X"),
            ),
        ),
    )

    @Test
    fun calmWithinMotion() = card(
        name = "Calm Within Motion",
        cost = "{1}{U}",
        type = "Enchantment",
        text = "At the beginning of your upkeep, mill three cards. Then if your library has no cards in it, exile this enchantment and put five cards from your graveyard on top of your library in any order.",
    ) parsesTo abilities(
        Ability.Triggered(
            trigger = Trigger.On(events = listOf(EventPattern.StepBegins(step = TurnStep.Upkeep, whose = PlayerFilter.You))),
            instructions = Instructions(
                steps = listOf(
                    doing(Effect.Mill(players = Players.You, count = n(3))),
                    Step.If(
                        condition = Condition.Compare(
                            left = Value.Count(objects = Objects.Each(filter = and(inZone(Zone.Library), ownedByYou))),
                            comparison = Comparison.Equal,
                            right = n(0),
                        ),
                        then = listOf(
                            doing(Effect.Move(objects = Objects.Self, to = Destination(zone = Zone.Exile))),
                            Step.Choose(
                                choice = Choice.Objects(filter = and(cardFilter, inZone(Zone.Graveyard), ownedByYou), min = n(5), max = n(5)),
                                bind = "chosen",
                                chooser = Players.You,
                            ),
                            doing(Effect.Move(objects = bound("chosen"), to = Destination(zone = Zone.Library, libraryPosition = 0))),
                        ),
                    ),
                ),
            ),
        ),
    )

    @Test
    fun frontierDevotee() = card(
        name = "Frontier Devotee",
        cost = "{1}{U}",
        type = "Creature — Human Druid",
        pt = "3/3",
        text = """
            Defender
            {1}: Add {G}, {U}, or {R}. Activate only once each turn.
        """,
    ) parsesTo abilities(
        Keyword.Defender,
        Ability.Activated(
            cost = Cost(parts = listOf(mana("{1}"))),
            instructions = Instructions(
                steps = listOf(
                    doing(
                        Effect.AddMana(
                            mana = ManaProduction(
                                kind = ManaKind.Choose(
                                    amount = n(1),
                                    colors = listOf(
                                        ColorRef.Literal(color = Color.Green),
                                        ColorRef.Literal(color = Color.Blue),
                                        ColorRef.Literal(color = Color.Red),
                                    ),
                                ),
                            ),
                            players = Players.You,
                        ),
                    ),
                ),
            ),
            rules = ActivationRules(timing = Timing.Any, activatedBy = PlayerFilter.You, perTurn = 1),
        ),
    )

    @Test
    fun seasonedIceScaler() = card(
        name = "Seasoned Ice Scaler",
        cost = "{1}{U}",
        type = "Creature — Human Scout",
        pt = "1/3",
        text = """
            Vigilance
            This creature can't be blocked.
            Whenever this creature attacks, up to one target player mills cards equal to this creature's power. (They put that many cards from the top of their library into their graveyard.)
        """,
    ) parsesTo abilities(
        Keyword.Vigilance,
        Ability.Static(mechanics = listOf(Mechanic.CantBeBlocked), affects = Affected.Objects(objects = Objects.Self)),
        Ability.Triggered(
            trigger = attacksSelf,
            instructions = Instructions(
                targets = listOf(TargetSlot(name = "player", what = TargetKind.Players(filter = PlayerFilter.Any), min = n(0), max = n(1))),
                steps = listOf(
                    doing(
                        Effect.Mill(
                            players = Players.Target(slot = "player"),
                            count = Value.Of(property = NumericProperty.Power, objects = Objects.Self, how = Combine.Single),
                        ),
                    ),
                ),
            ),
        ),
    )

    @Test
    fun broadwingStride() = card(
        name = "Broadwing Stride",
        cost = "{U}",
        type = "Enchantment — Aura",
        text = """
            Enchant creature
            Enchanted creature gets +1/+1 and has flying.
            {2}{U}: Return this Aura to its owner's hand.
        """,
    ) parsesTo abilities(
        Keyword.Enchant(what = TargetKind.Objects(filter = creature)),
        Ability.Static(
            mechanics = listOf(
                Mechanic.ModifyPowerToughness(power = n(1), toughness = n(1)),
                Mechanic.GainAbilities(abilities = listOf(Keyword.Flying)),
            ),
            affects = Affected.Objects(objects = Objects.AttachedTo(of = Objects.Self)),
        ),
        Ability.Activated(
            cost = Cost(parts = listOf(mana("{2}{U}"))),
            instructions = Instructions(steps = listOf(doing(Effect.Move(objects = Objects.Self, to = Destination(zone = Zone.Hand))))),
            rules = anyTime,
        ),
    )
}
