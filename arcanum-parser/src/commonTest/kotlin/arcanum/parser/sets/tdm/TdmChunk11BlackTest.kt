package arcanum.parser.sets.tdm

import arcanum.api.*
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.cardFaces
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

/**
 * Tarkir: Dragonstorm chunk 11: black cards: counter kinds among creatures, casting from the graveyard with a finality counter, a delayed trigger on a creature dying, two targets with opposite pumps, a sacrifice of mana value X for a card of X plus 1, an opponent exiling a creature and their graveyard, "if you cast it" of another creature.
 */
class TdmChunk11BlackTest {
    @Test
    fun houselordDevotee() = card(
        name = "Houselord Devotee",
        cost = "{1}{B}",
        type = "Creature — Dog Cleric",
        pt = "2/1",
        text = """
            {1}: Add {W}, {B}, or {G}. Activate only once each turn.
            {2}{B}: Return this card from your graveyard to your hand.
        """,
    ) parsesTo abilities(
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
                                        ColorRef.Literal(color = Color.White),
                                        ColorRef.Literal(color = Color.Black),
                                        ColorRef.Literal(color = Color.Green),
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
        Ability.Activated(
            cost = Cost(parts = listOf(mana("{2}{B}"))),
            instructions = Instructions(steps = listOf(doing(Effect.Move(objects = Objects.Self, to = Destination(zone = Zone.Hand))))),
            rules = anyTime,
            functionsIn = listOf(Zone.Graveyard),
        ),
    )

    @Test
    fun hostileParley() = card(
        name = "Hostile Parley",
        cost = "{2}{B}",
        type = "Sorcery",
        text = "Target opponent reveals their hand. You choose a nonland card from it and exile that card. Put a +1/+1 counter on up to one target creature you control.",
    ) parsesTo abilities(
        Ability.Spell(
            instructions = Instructions(
                targets = listOf(
                    TargetSlot(name = "opponent", what = TargetKind.Players(filter = PlayerFilter.Opponent), min = n(1), max = n(1)),
                    TargetSlot(name = "creature", what = TargetKind.Objects(filter = and(creature, you)), min = n(0), max = n(1)),
                ),
                steps = listOf(
                    doing(
                        Effect.Reveal(
                            objects = Objects.Each(
                                filter = and(
                                    inZone(Zone.Hand),
                                    ObjectFilter.OwnedBy(players = PlayerFilter.Is(players = Players.Target(slot = "opponent"))),
                                ),
                            ),
                            bind = "revealed",
                        ),
                    ),
                    Step.Choose(
                        choice = Choice.Objects(filter = not(land), min = n(1), max = n(1), from = bound("revealed")),
                        bind = "chosen",
                        chooser = Players.You,
                    ),
                    doing(Effect.Move(objects = bound("chosen"), to = Destination(zone = Zone.Exile), postBind = "exiled")),
                    doing(Effect.PutCounters(objects = target("creature"), kind = p1p1, amount = n(1))),
                ),
            ),
        ),
    )

    @Test
    fun vanyasLegacy() = card(
        name = "Vanya's Legacy",
        cost = "{1}{B}",
        type = "Instant",
        text = "Target creature you control gains deathtouch and indestructible until end of turn. (Damage and effects that say \"destroy\" don't destroy it.)",
    ) parsesTo abilities(
        Ability.Spell(
            instructions = Instructions(
                targets = listOf(TargetSlot(name = "creature", what = TargetKind.Objects(filter = and(creature, you)), min = n(1), max = n(1))),
                steps = listOf(
                    doing(
                        Effect.ApplyMechanics(
                            mechanics = listOf(Mechanic.GainAbilities(abilities = listOf(Keyword.Deathtouch, Keyword.Indestructible))),
                            affects = Affected.Objects(objects = target("creature")),
                            duration = Duration.EndOfTurn,
                        ),
                    ),
                ),
            ),
        ),
    )

    @Test
    fun marshBloodflies() = card(
        name = "Marsh Bloodflies",
        cost = "{1}{B}",
        type = "Creature — Insect",
        pt = "1/2",
        text = """
            Flying
            Whenever this creature attacks, if you control a creature with a counter on it, each opponent loses 1 life.
        """,
    ) parsesTo abilities(
        Keyword.Flying,
        Ability.Triggered(
            trigger = attacksSelf,
            instructions = Instructions(steps = listOf(doing(Effect.LoseLife(players = Players.Each(filter = PlayerFilter.Opponent), amount = n(1))))),
            interveningIf = Condition.Exists(filter = and(creature, ObjectFilter.HasCounter(kind = null, atLeast = 1), you), atLeast = 1),
        ),
    )

    @Test
    fun direRecourse() = card(
        name = "Dire Recourse",
        cost = "{B}",
        type = "Instant",
        text = "Target creature gets +1/-1 until end of turn. When it dies under your control this turn, draw two cards.",
    ) parsesTo abilities(
        Ability.Spell(
            instructions = Instructions(
                targets = listOf(TargetSlot(name = "creature", what = TargetKind.Objects(filter = creature), min = n(1), max = n(1))),
                steps = listOf(
                    doing(
                        Effect.ApplyMechanics(
                            mechanics = listOf(Mechanic.ModifyPowerToughness(power = n(1), toughness = n(-1))),
                            affects = Affected.Objects(objects = target("creature")),
                            duration = Duration.EndOfTurn,
                        ),
                    ),
                    doing(
                        Effect.CreateDelayedTrigger(
                            trigger = Trigger.On(
                                events = listOf(
                                    EventPattern.ZoneChange(
                                        objects = and(ObjectFilter.Is(objects = target("creature")), you),
                                        from = Zone.Battlefield,
                                        to = Zone.Graveyard,
                                    ),
                                ),
                            ),
                            instructions = Instructions(steps = listOf(doing(Effect.Draw(players = Players.You, count = n(2))))),
                            duration = Duration.EndOfTurn,
                        ),
                    ),
                ),
            ),
        ),
    )

    @Test
    fun fenmireRakshasa() = card(
        name = "Fenmire Rakshasa",
        cost = "{4}{B}{B}",
        type = "Creature — Demon",
        pt = "5/5",
        text = """
            Menace (This creature can't be blocked except by two or more creatures.)
            When this creature enters, target creature an opponent controls gets -2/-2 until end of turn and target creature you control gets +2/+2 until end of turn.
        """,
    ) parsesTo abilities(
        Keyword.Menace,
        Ability.Triggered(
            trigger = entersSelf,
            instructions = Instructions(
                targets = listOf(
                    TargetSlot(name = "creature", what = TargetKind.Objects(filter = and(creature, opponents)), min = n(1), max = n(1)),
                    TargetSlot(name = "second creature", what = TargetKind.Objects(filter = and(creature, you)), min = n(1), max = n(1)),
                ),
                steps = listOf(
                    doing(
                        Effect.ApplyMechanics(
                            mechanics = listOf(Mechanic.ModifyPowerToughness(power = n(-2), toughness = n(-2))),
                            affects = Affected.Objects(objects = target("creature")),
                            duration = Duration.EndOfTurn,
                        ),
                    ),
                    doing(
                        Effect.ApplyMechanics(
                            mechanics = listOf(Mechanic.ModifyPowerToughness(power = n(2), toughness = n(2))),
                            affects = Affected.Objects(objects = target("second creature")),
                            duration = Duration.EndOfTurn,
                        ),
                    ),
                ),
            ),
        ),
    )

    @Test
    fun thousandScarVeteran() = card(
        name = "Thousand-Scar Veteran",
        cost = "{3}{B}",
        type = "Creature — Zombie Warrior",
        pt = "4/2",
        text = """
            As long as there are three or more different kinds of counters among creatures you control, this creature gets +2/+4.
            You may cast this card from your graveyard. If you do, it enters with a finality counter on it. (If a creature with a finality counter on it would die, exile it instead.)
        """,
    ) parsesTo abilities(
        Ability.Static(
            mechanics = listOf(Mechanic.ModifyPowerToughness(power = n(2), toughness = n(4))),
            affects = Affected.Objects(objects = Objects.Self),
            condition = Condition.Compare(
                left = Value.CountDistinct(objects = Objects.Each(filter = and(creature, you)), quality = SharedQuality.CounterKind),
                comparison = Comparison.AtLeast,
                right = n(3),
            ),
        ),
        Ability.Static(
            mechanics = listOf(Mechanic.MayPlay(from = Zone.Graveyard, how = PlayKind.Cast, holder = PlayerFilter.You)),
            affects = Affected.Objects(objects = Objects.Self),
        ),
        Ability.Static(
            mechanics = listOf(
                Mechanic.Replacement(
                    event = EventPattern.ZoneChange(objects = isSelf, to = Zone.Battlefield),
                    modify = listOf(Modification.EntersWithCounters(kind = CounterKind.Finality, amount = n(1))),
                ),
            ),
            affects = Affected.Objects(objects = Objects.Self),
            condition = Condition.CastFrom(zone = Zone.Graveyard, of = Objects.Self),
        ),
    )

    @Test
    fun caravanSkirmish() = card(
        name = "Caravan Skirmish",
        cost = "{3}{B}",
        type = "Sorcery",
        text = "Destroy target creature. Create two 1/1 red Warrior creature tokens. They gain haste until end of turn. Sacrifice them at the beginning of the next end step.",
    ) parsesTo abilities(
        Ability.Spell(
            instructions = Instructions(
                targets = listOf(TargetSlot(name = "creature", what = TargetKind.Objects(filter = creature), min = n(1), max = n(1))),
                steps = listOf(
                    doing(Effect.Destroy(objects = target("creature"))),
                    doing(
                        Effect.CreateToken(
                            token = TokenSpec.Defined(
                                face = FaceDefinition(
                                    name = "Warrior Token",
                                    colorIndicator = listOf(Color.Red),
                                    types = listOf(CardType.Creature),
                                    subtypes = listOf(Subtype(name = "Warrior")),
                                    power = PrintedNumber.Fixed(value = 1),
                                    toughness = PrintedNumber.Fixed(value = 1),
                                ),
                            ),
                            count = n(2),
                            controller = Players.You,
                            bind = "tokens",
                        ),
                    ),
                    doing(
                        Effect.ApplyMechanics(
                            mechanics = listOf(Mechanic.GainAbilities(abilities = listOf(Keyword.Haste))),
                            affects = Affected.Objects(objects = bound("tokens")),
                            duration = Duration.EndOfTurn,
                        ),
                    ),
                    doing(
                        Effect.CreateDelayedTrigger(
                            trigger = Trigger.On(events = listOf(EventPattern.StepBegins(step = TurnStep.End, whose = PlayerFilter.Any))),
                            instructions = Instructions(steps = listOf(doing(Effect.Sacrifice(objects = bound("tokens"))))),
                        ),
                    ),
                ),
            ),
        ),
    )

    @Test
    fun ssessaRegentOfTheFen() = card(
        name = "Ssessa, Regent of the Fen",
        cost = "{1}{B}",
        type = "Legendary Creature — Zombie Snake Warlock",
        pt = "1/3",
        text = "{T}, Sacrifice a creature you control with mana value X other than Ssessa: Return target creature card with mana value X plus 1 from your graveyard to the battlefield. Activate only as a sorcery.",
    ) parsesTo abilities(
        Ability.Activated(
            cost = Cost(
                parts = listOf(
                    tap,
                    CostPart.Sacrifice(
                        what = CostObjects.Choose(
                            filter = and(
                                creature,
                                you,
                                ObjectFilter.ManaValue(comparison = Comparison.Equal, value = Value.Announced(name = "X")),
                                not(isSelf),
                            ),
                            count = n(1),
                        ),
                    ),
                ),
            ),
            instructions = Instructions(
                targets = listOf(
                    TargetSlot(
                        name = "card",
                        what = TargetKind.Objects(
                            filter = and(
                                creature,
                                ObjectFilter.ManaValue(
                                    comparison = Comparison.Equal,
                                    value = Value.Sum(parts = listOf(Value.Announced(name = "X"), n(1))),
                                ),
                                inZone(Zone.Graveyard),
                                ownedByYou,
                            ),
                        ),
                        min = n(1),
                        max = n(1),
                    ),
                ),
                steps = listOf(doing(Effect.Move(objects = target("card"), to = Destination(zone = Zone.Battlefield)))),
                announced = listOf("X"),
            ),
            rules = asSorcery,
        ),
    )

    @Test
    fun calculatedBetrayal() = card(
        name = "Calculated Betrayal",
        cost = "{1}{B}",
        type = "Sorcery",
        text = "Target opponent exiles a creature they control and their graveyard.",
    ) parsesTo abilities(
        Ability.Spell(
            instructions = Instructions(
                targets = listOf(TargetSlot(name = "opponent", what = TargetKind.Players(filter = PlayerFilter.Opponent), min = n(1), max = n(1))),
                steps = listOf(
                    Step.Choose(
                        choice = Choice.Objects(
                            filter = and(creature, ObjectFilter.ControlledBy(players = PlayerFilter.Is(players = Players.Target(slot = "opponent")))),
                            min = n(1),
                            max = n(1),
                        ),
                        bind = "chosen",
                        chooser = Players.Target(slot = "opponent"),
                    ),
                    doing(Effect.Move(objects = bound("chosen"), to = Destination(zone = Zone.Exile))),
                    doing(
                        Effect.Move(
                            objects = Objects.Each(
                                filter = and(
                                    cardFilter,
                                    inZone(Zone.Graveyard),
                                    ObjectFilter.OwnedBy(players = PlayerFilter.Is(players = Players.Target(slot = "opponent"))),
                                ),
                            ),
                            to = Destination(zone = Zone.Exile),
                        ),
                    ),
                ),
            ),
        ),
    )

    @Test
    fun theGravecourtRite() = card(
        name = "The Gravecourt Rite",
        cost = "{B}{B}{B}",
        type = "Legendary Enchantment",
        text = """
            Creature spells you cast cost {2} less to cast.
            Whenever a creature you control enters, if you cast it, destroy that creature, then create a 2/2 black Zombie Druid creature token.
        """,
    ) parsesTo abilities(
        Ability.Static(
            mechanics = listOf(
                Mechanic.CostChange(
                    what = and(creature, spellFilter, you),
                    change = CostChange.Less(mana = ManaCost.parse("{2}")!!, times = n(1)),
                    minimum = 0,
                ),
            ),
            affects = Affected.Objects(objects = Objects.Self),
        ),
        Ability.Triggered(
            trigger = Trigger.On(events = listOf(EventPattern.ZoneChange(objects = and(creature, you), to = Zone.Battlefield))),
            instructions = Instructions(
                steps = listOf(
                    doing(Effect.Destroy(objects = Objects.EventObject(role = EventRole.After))),
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
            interveningIf = Condition.CastFrom(zone = null, of = Objects.EventObject(role = EventRole.After)),
        ),
    )

    @Test
    fun unearthedGravedigger() = card(
        name = "Unearthed Gravedigger",
        cost = "{1}{B}",
        type = "Creature — Human Warrior",
        pt = "2/2",
        text = "{2}, Sacrifice another creature: Put a +1/+1 counter on this creature.",
    ) parsesTo abilities(
        Ability.Activated(
            cost = Cost(
                parts = listOf(mana("{2}"), CostPart.Sacrifice(what = CostObjects.Choose(filter = and(not(isSelf), creature), count = n(1)))),
            ),
            instructions = Instructions(steps = listOf(doing(Effect.PutCounters(objects = Objects.Self, kind = p1p1, amount = n(1))))),
            rules = anyTime,
        ),
    )

    @Test
    fun uprootedAncestor() = card(
        name = "Uprooted Ancestor",
        cost = "{2}{B}",
        type = "Creature — Spirit Cleric",
        pt = "3/2",
        text = """
            Flash
            {1}, Sacrifice another creature: This creature gains indestructible until end of turn. Tap it. (Damage and effects that say "destroy" don't destroy it.)
        """,
    ) parsesTo abilities(
        Keyword.Flash,
        Ability.Activated(
            cost = Cost(
                parts = listOf(mana("{1}"), CostPart.Sacrifice(what = CostObjects.Choose(filter = and(not(isSelf), creature), count = n(1)))),
            ),
            instructions = Instructions(
                steps = listOf(
                    doing(
                        Effect.ApplyMechanics(
                            mechanics = listOf(Mechanic.GainAbilities(abilities = listOf(Keyword.Indestructible))),
                            affects = Affected.Objects(objects = Objects.Self),
                            duration = Duration.EndOfTurn,
                        ),
                    ),
                    doing(Effect.Tap(objects = Objects.Self)),
                ),
            ),
            rules = anyTime,
        ),
    )

    @Test
    fun keeningOfWar() = card(
        name = "Keening of War",
        cost = "{2}{B}",
        type = "Instant",
        text = """
            Choose one —
            • Creatures target opponent controls get -1/-1 until end of turn.
            • Return up to two target creature cards from your graveyard to your hand.
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
                                        name = "opponent",
                                        what = TargetKind.Players(filter = PlayerFilter.Opponent),
                                        min = n(1),
                                        max = n(1),
                                    ),
                                ),
                                steps = listOf(
                                    doing(
                                        Effect.ApplyMechanics(
                                            mechanics = listOf(Mechanic.ModifyPowerToughness(power = n(-1), toughness = n(-1))),
                                            affects = Affected.Objects(
                                                objects = Objects.Each(
                                                    filter = and(
                                                        creature,
                                                        ObjectFilter.ControlledBy(
                                                            players = PlayerFilter.Is(players = Players.Target(slot = "opponent")),
                                                        ),
                                                    ),
                                                ),
                                            ),
                                            duration = Duration.EndOfTurn,
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
                                        name = "cards",
                                        what = TargetKind.Objects(filter = and(creature, inZone(Zone.Graveyard), ownedByYou)),
                                        min = n(0),
                                        max = n(2),
                                    ),
                                ),
                                steps = listOf(doing(Effect.Move(objects = target("cards"), to = Destination(zone = Zone.Hand)))),
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
    fun fittingPrice() = card(
        name = "Fitting Price",
        cost = "{B}",
        type = "Sorcery",
        text = """
            As an additional cost to cast this spell, sacrifice a creature.
            Exile target creature or planeswalker.
        """,
    ) parsesTo abilities(
        Ability.Static(
            mechanics = listOf(
                Mechanic.AdditionalCost(
                    cost = Cost(parts = listOf(CostPart.Sacrifice(what = CostObjects.Choose(filter = creature, count = n(1))))),
                ),
            ),
            affects = Affected.Objects(objects = Objects.Self),
        ),
        Ability.Spell(
            instructions = Instructions(
                targets = listOf(
                    TargetSlot(name = "permanent", what = TargetKind.Objects(filter = or(creature, planeswalker)), min = n(1), max = n(1)),
                ),
                steps = listOf(doing(Effect.Move(objects = target("permanent"), to = Destination(zone = Zone.Exile)))),
            ),
        ),
    )

    @Test
    fun barrowTombguard() = card(
        name = "Barrow Tombguard",
        cost = "{2}{B}",
        type = "Creature — Human Warrior",
        pt = "2/3",
        text = """
            Menace (This creature can't be blocked except by two or more creatures.)
            Whenever a creature you control with a counter on it deals combat damage to a player, you draw a card and you lose 1 life.
        """,
    ) parsesTo abilities(
        Keyword.Menace,
        Ability.Triggered(
            trigger = Trigger.On(
                events = listOf(
                    EventPattern.Damage(
                        source = and(creature, you, ObjectFilter.HasCounter(kind = null, atLeast = 1)),
                        to = RecipientFilter(players = PlayerFilter.Any),
                        combat = true,
                    ),
                ),
            ),
            instructions = Instructions(
                steps = listOf(
                    doing(Effect.Draw(players = Players.You, count = n(1))),
                    doing(Effect.LoseLife(players = Players.You, amount = n(1))),
                ),
            ),
        ),
    )
}
