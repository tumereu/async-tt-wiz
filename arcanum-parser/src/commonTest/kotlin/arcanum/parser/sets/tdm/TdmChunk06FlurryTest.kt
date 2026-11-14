package arcanum.parser.sets.tdm

import arcanum.api.*
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.cardFaces
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

/**
 * Tarkir: Dragonstorm chunk 06: flurry (an ability word, CR 207.2c) and second-spell wordings: copies and suspend from exile (CR 702.62), cheaper second spells, a spell that copies itself, a copy of the next spell, discarding the hand to draw for each spell cast.
 */
class TdmChunk06FlurryTest {
    @Test
    fun summitStalwart() = card(
        name = "Summit Stalwart",
        cost = "{1}{R}{W}",
        type = "Creature — Human Monk",
        pt = "3/3",
        text = "Flurry — Whenever you cast your second spell each turn, this creature deals 2 damage to each opponent and you gain 2 life.",
    ) parsesTo abilities(
        Ability.Triggered(
            trigger = Trigger.On(events = listOf(EventPattern.Cast(spell = ObjectFilter.Any, by = PlayerFilter.You)), nthThisTurn = 2),
            instructions = Instructions(
                steps = listOf(
                    doing(
                        Effect.DealDamage(
                            to = Recipients(players = Players.Each(filter = PlayerFilter.Opponent)),
                            amount = n(2),
                            source = Objects.Self,
                        ),
                    ),
                    doing(Effect.GainLife(players = Players.You, amount = n(2))),
                ),
            ),
            abilityWord = "Flurry",
        ),
    )

    @Test
    fun peakforgedCutter() = card(
        name = "Peakforged Cutter",
        cost = "{1}{R}",
        type = "Artifact — Equipment",
        text = """
            Equipped creature gets +1/+1 and has trample and haste.
            Flurry — Whenever you cast your second spell each turn, create a 1/1 white Monk creature token with prowess. You may attach this Equipment to it. (Whenever you cast a noncreature spell, the token gets +1/+1 until end of turn.)
            Equip {1}{R}
        """,
    ) parsesTo abilities(
        Ability.Static(
            mechanics = listOf(
                Mechanic.ModifyPowerToughness(power = n(1), toughness = n(1)),
                Mechanic.GainAbilities(abilities = listOf(Keyword.Trample, Keyword.Haste)),
            ),
            affects = Affected.Objects(objects = Objects.AttachedTo(of = Objects.Self)),
        ),
        Ability.Triggered(
            trigger = Trigger.On(events = listOf(EventPattern.Cast(spell = ObjectFilter.Any, by = PlayerFilter.You)), nthThisTurn = 2),
            instructions = Instructions(
                steps = listOf(
                    doing(
                        Effect.CreateToken(
                            token = TokenSpec.Defined(
                                face = FaceDefinition(
                                    name = "Monk Token",
                                    colorIndicator = listOf(Color.White),
                                    types = listOf(CardType.Creature),
                                    subtypes = listOf(Subtype(name = "Monk")),
                                    power = PrintedNumber.Fixed(value = 1),
                                    toughness = PrintedNumber.Fixed(value = 1),
                                    abilities = listOf(Keyword.Prowess),
                                ),
                            ),
                            count = n(1),
                            controller = Players.You,
                            bind = "token",
                        ),
                    ),
                    Step.May(
                        steps = listOf(doing(Effect.Attach(objects = Objects.Self, to = Recipients(objects = bound("token"))))),
                        player = Players.You,
                    ),
                ),
            ),
            abilityWord = "Flurry",
        ),
        Keyword.Equip(cost = Cost(parts = listOf(mana("{1}{R}")))),
    )

    @Test
    fun ardentDuelist() = card(
        name = "Ardent Duelist",
        cost = "{1}{R}",
        type = "Creature — Goblin Monk",
        pt = "2/1",
        text = """
            Haste
            Flurry — Whenever you cast your second spell each turn, this creature deals 1 damage to each opponent.
        """,
    ) parsesTo abilities(
        Keyword.Haste,
        Ability.Triggered(
            trigger = Trigger.On(events = listOf(EventPattern.Cast(spell = ObjectFilter.Any, by = PlayerFilter.You)), nthThisTurn = 2),
            instructions = Instructions(
                steps = listOf(
                    doing(
                        Effect.DealDamage(
                            to = Recipients(players = Players.Each(filter = PlayerFilter.Opponent)),
                            amount = n(1),
                            source = Objects.Self,
                        ),
                    ),
                ),
            ),
            abilityWord = "Flurry",
        ),
    )

    @Test
    fun unhurriedMaster() = card(
        name = "Unhurried Master",
        cost = "{2}{U}{R}",
        type = "Creature — Orc Monk",
        pt = "4/3",
        text = """
            Vigilance
            Menace (This creature can't be blocked except by two or more creatures.)
            This creature enters with two +1/+1 counters on it if you've cast two or more spells this turn.
        """,
    ) parsesTo abilities(
        Keyword.Vigilance,
        Keyword.Menace,
        Ability.Static(
            mechanics = listOf(
                Mechanic.Replacement(
                    event = EventPattern.ZoneChange(objects = isSelf, to = Zone.Battlefield),
                    modify = listOf(Modification.EntersWithCounters(kind = p1p1, amount = n(2))),
                ),
            ),
            affects = Affected.Objects(objects = Objects.Self),
            condition = Condition.HappenedThisTurn(
                event = EventPattern.Cast(spell = ObjectFilter.Any, by = PlayerFilter.You),
                atLeast = 2,
                measure = Measure.Events,
            ),
        ),
    )

    @Test
    fun equipoiseAdept() = card(
        name = "Equipoise Adept",
        cost = "{3}{R}",
        type = "Creature — Dog Monk",
        pt = "2/4",
        text = """
            When this creature enters, exile the top card of your library. Until the end of your next turn, you may play that card.
            Flurry — Whenever you cast your second spell each turn, this creature gains double strike until end of turn.
        """,
    ) parsesTo abilities(
        Ability.Triggered(
            trigger = entersSelf,
            instructions = Instructions(
                steps = listOf(
                    doing(
                        Effect.Move(
                            objects = Objects.LibraryCards(of = Players.You, count = n(1), fromTop = true),
                            to = Destination(zone = Zone.Exile),
                            postBind = "exiled",
                        ),
                    ),
                    doing(
                        Effect.ApplyMechanics(
                            mechanics = listOf(Mechanic.MayPlay(from = Zone.Exile, how = PlayKind.Play, holder = PlayerFilter.You)),
                            affects = Affected.Objects(objects = bound("exiled")),
                            duration = Duration.UntilEndOfYourNextTurn,
                        ),
                    ),
                ),
            ),
        ),
        Ability.Triggered(
            trigger = Trigger.On(events = listOf(EventPattern.Cast(spell = ObjectFilter.Any, by = PlayerFilter.You)), nthThisTurn = 2),
            instructions = Instructions(
                steps = listOf(
                    doing(
                        Effect.ApplyMechanics(
                            mechanics = listOf(Mechanic.GainAbilities(abilities = listOf(Keyword.DoubleStrike))),
                            affects = Affected.Objects(objects = Objects.Self),
                            duration = Duration.EndOfTurn,
                        ),
                    ),
                ),
            ),
            abilityWord = "Flurry",
        ),
    )

    @Test
    fun vargaWyrmclaw() = card(
        name = "Varga Wyrmclaw",
        cost = "{1}{G}{U}{R}",
        type = "Legendary Creature — Human Warrior",
        pt = "4/4",
        text = """
            Vigilance, trample, ward {1}
            At the beginning of combat on your turn, if you've cast both a creature spell and a noncreature spell this turn, draw a card and put two +1/+1 counters on Varga Wyrmclaw.
        """,
    ) parsesTo abilities(
        Keyword.Vigilance,
        Keyword.Trample,
        Keyword.Ward(cost = Cost(parts = listOf(mana("{1}")))),
        Ability.Triggered(
            trigger = Trigger.On(events = listOf(EventPattern.StepBegins(step = TurnStep.BeginningOfCombat, whose = PlayerFilter.You))),
            instructions = Instructions(
                steps = listOf(
                    doing(Effect.Draw(players = Players.You, count = n(1))),
                    doing(Effect.PutCounters(objects = Objects.Self, kind = p1p1, amount = n(2))),
                ),
            ),
            interveningIf = Condition.And(
                all = listOf(
                    Condition.HappenedThisTurn(
                        event = EventPattern.Cast(spell = and(creature, spellFilter), by = PlayerFilter.You),
                        atLeast = 1,
                        measure = Measure.Events,
                    ),
                    Condition.HappenedThisTurn(
                        event = EventPattern.Cast(spell = and(not(creature), spellFilter), by = PlayerFilter.You),
                        atLeast = 1,
                        measure = Measure.Events,
                    ),
                ),
            ),
        ),
    )

    @Test
    fun embergripGrappler() = card(
        name = "Embergrip Grappler",
        cost = "{U}{R}{W}",
        type = "Creature — Human Monk",
        pt = "3/3",
        text = """
            First strike
            When this creature enters, copy the next spell you cast this turn when you cast it. You may choose new targets for the copy. (A copy of a permanent spell becomes a token.)
        """,
    ) parsesTo abilities(
        Keyword.FirstStrike,
        Ability.Triggered(
            trigger = entersSelf,
            instructions = Instructions(
                steps = listOf(
                    doing(
                        Effect.CreateDelayedTrigger(
                            trigger = Trigger.On(events = listOf(EventPattern.Cast(spell = ObjectFilter.Any, by = PlayerFilter.You))),
                            instructions = Instructions(
                                steps = listOf(
                                    doing(
                                        Effect.CopySpell(
                                            objects = Objects.EventObject(role = EventRole.Object),
                                            controller = Players.You,
                                            newTargets = true,
                                        ),
                                    ),
                                ),
                            ),
                            duration = Duration.EndOfTurn,
                        ),
                    ),
                ),
            ),
        ),
    )

    @Test
    fun centerTheThoughts() = card(
        name = "Center the Thoughts",
        cost = "{4}{U}",
        type = "Instant",
        text = """
            This spell costs {2} less to cast if you've cast another spell this turn.
            Draw three cards, then discard a card.
        """,
    ) parsesTo abilities(
        Ability.Static(
            mechanics = listOf(Mechanic.CostChange(what = isSelf, change = CostChange.Less(mana = ManaCost.parse("{2}")!!, times = n(1)), minimum = 0)),
            affects = Affected.Objects(objects = Objects.Self),
            condition = Condition.HappenedThisTurn(
                event = EventPattern.Cast(spell = not(isSelf), by = PlayerFilter.You),
                atLeast = 1,
                measure = Measure.Events,
            ),
        ),
        Ability.Spell(
            instructions = Instructions(
                steps = listOf(
                    doing(Effect.Draw(players = Players.You, count = n(3))),
                    doing(Effect.Discard(players = Players.You, count = n(1), filter = ObjectFilter.Any)),
                ),
            ),
        ),
    )

    @Test
    fun tallspireBellkeeper() = card(
        name = "Tallspire Bellkeeper",
        cost = "{2}{U}",
        type = "Creature — Djinn Monk",
        pt = "1/4",
        text = """
            Flying
            The second spell you cast each turn costs {1} less to cast.
        """,
    ) parsesTo abilities(
        Keyword.Flying,
        Ability.Static(
            mechanics = listOf(
                Mechanic.CostChange(
                    what = and(ObjectFilter.NthSpellThisTurn(spells = ObjectFilter.Any, n = 2), you),
                    change = CostChange.Less(mana = ManaCost.parse("{1}")!!, times = n(1)),
                    minimum = 0,
                ),
            ),
            affects = Affected.Objects(objects = Objects.Self),
        ),
    )

    @Test
    fun wayfarerDevotee() = card(
        name = "Wayfarer Devotee",
        cost = "{1}{R}",
        type = "Creature — Orc Monk",
        pt = "2/2",
        text = """
            Flurry — Whenever you cast your second spell each turn, this creature gets +1/+1 until end of turn.
            {1}: Add {U}, {R}, or {W}. Activate only once each turn.
        """,
    ) parsesTo abilities(
        Ability.Triggered(
            trigger = Trigger.On(events = listOf(EventPattern.Cast(spell = ObjectFilter.Any, by = PlayerFilter.You)), nthThisTurn = 2),
            instructions = Instructions(
                steps = listOf(
                    doing(
                        Effect.ApplyMechanics(
                            mechanics = listOf(Mechanic.ModifyPowerToughness(power = n(1), toughness = n(1))),
                            affects = Affected.Objects(objects = Objects.Self),
                            duration = Duration.EndOfTurn,
                        ),
                    ),
                ),
            ),
            abilityWord = "Flurry",
        ),
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
                                        ColorRef.Literal(color = Color.Blue),
                                        ColorRef.Literal(color = Color.Red),
                                        ColorRef.Literal(color = Color.White),
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
    fun lirienWaymasterAbbess() = card(
        name = "Lirien, Waymaster Abbess",
        cost = "{U}{R}{W}",
        type = "Legendary Creature — Human Monk",
        pt = "3/4",
        text = "At the beginning of your end step, you may discard your hand. If you do, draw cards equal to the number of spells you've cast this turn.",
    ) parsesTo abilities(
        Ability.Triggered(
            trigger = Trigger.On(events = listOf(EventPattern.StepBegins(step = TurnStep.End, whose = PlayerFilter.You))),
            instructions = Instructions(
                steps = listOf(
                    Step.Pay(
                        steps = listOf(
                            doing(
                                Effect.Discard(
                                    players = Players.You,
                                    count = n(1),
                                    filter = ObjectFilter.Any,
                                    objects = Objects.Each(filter = and(inZone(Zone.Hand), ownedByYou)),
                                ),
                            ),
                        ),
                        payer = Players.You,
                        bind = "discarded",
                    ),
                    Step.If(
                        condition = Condition.Did(name = "discarded"),
                        then = listOf(
                            doing(
                                Effect.Draw(
                                    players = Players.You,
                                    count = Value.EventsThisTurn(
                                        event = EventPattern.Cast(spell = ObjectFilter.Any, by = PlayerFilter.You),
                                        measure = Measure.Events,
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
    fun balancedAdept() = card(
        name = "Balanced Adept",
        cost = "{2}{W}",
        type = "Creature — Human Monk",
        pt = "2/3",
        text = "Flurry — Whenever you cast your second spell each turn, put a +1/+1 counter on this creature. Scry 1. (Look at the top card of your library. You may put that card on the bottom.)",
    ) parsesTo abilities(
        Ability.Triggered(
            trigger = Trigger.On(events = listOf(EventPattern.Cast(spell = ObjectFilter.Any, by = PlayerFilter.You)), nthThisTurn = 2),
            instructions = Instructions(
                steps = listOf(
                    doing(Effect.PutCounters(objects = Objects.Self, kind = p1p1, amount = n(1))),
                    doing(Effect.Scry(players = Players.You, count = n(1))),
                ),
            ),
            abilityWord = "Flurry",
        ),
    )

    @Test
    fun rouseTheMonastery() = card(
        name = "Rouse the Monastery",
        cost = "{3}{W}",
        type = "Instant",
        text = """
            This spell costs {2} less to cast if you've cast another spell this turn.
            Choose one —
            • Create two 1/1 white Monk creature tokens with prowess.
            • Up to two target creatures you control each get +2/+2 until end of turn.
            • Destroy target creature with power 4 or greater.
        """,
    ) parsesTo abilities(
        Ability.Static(
            mechanics = listOf(Mechanic.CostChange(what = isSelf, change = CostChange.Less(mana = ManaCost.parse("{2}")!!, times = n(1)), minimum = 0)),
            affects = Affected.Objects(objects = Objects.Self),
            condition = Condition.HappenedThisTurn(
                event = EventPattern.Cast(spell = not(isSelf), by = PlayerFilter.You),
                atLeast = 1,
                measure = Measure.Events,
            ),
        ),
        Ability.Spell(
            instructions = Instructions(
                modes = Modes(
                    options = listOf(
                        Mode(
                            instructions = Instructions(
                                steps = listOf(
                                    doing(
                                        Effect.CreateToken(
                                            token = TokenSpec.Defined(
                                                face = FaceDefinition(
                                                    name = "Monk Token",
                                                    colorIndicator = listOf(Color.White),
                                                    types = listOf(CardType.Creature),
                                                    subtypes = listOf(Subtype(name = "Monk")),
                                                    power = PrintedNumber.Fixed(value = 1),
                                                    toughness = PrintedNumber.Fixed(value = 1),
                                                    abilities = listOf(Keyword.Prowess),
                                                ),
                                            ),
                                            count = n(2),
                                            controller = Players.You,
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
                                        name = "creatures",
                                        what = TargetKind.Objects(filter = and(creature, you)),
                                        min = n(0),
                                        max = n(2),
                                    ),
                                ),
                                steps = listOf(
                                    doing(
                                        Effect.ApplyMechanics(
                                            mechanics = listOf(Mechanic.ModifyPowerToughness(power = n(2), toughness = n(2))),
                                            affects = Affected.Objects(objects = target("creatures")),
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
                                        name = "creature",
                                        what = TargetKind.Objects(
                                            filter = and(creature, ObjectFilter.Power(comparison = Comparison.AtLeast, value = n(4))),
                                        ),
                                        min = n(1),
                                        max = n(1),
                                    ),
                                ),
                                steps = listOf(doing(Effect.Destroy(objects = target("creature")))),
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
    fun echoingSummons() = card(
        name = "Echoing Summons",
        cost = "{1}{R}",
        type = "Enchantment",
        text = """
            At the beginning of each combat, if you've cast two or more spells this turn, this enchantment becomes a 3/3 Monk creature with haste in addition to its other types until end of turn.
            {1}{R}, Discard your hand, Sacrifice this enchantment: Draw two cards.
        """,
    ) parsesTo abilities(
        Ability.Triggered(
            trigger = Trigger.On(events = listOf(EventPattern.StepBegins(step = TurnStep.BeginningOfCombat, whose = PlayerFilter.Any))),
            instructions = Instructions(
                steps = listOf(
                    doing(
                        Effect.ApplyMechanics(
                            mechanics = listOf(
                                Mechanic.SetTypes(
                                    add = listOf(CardType.Creature),
                                    addSubtypes = listOf(SubtypeRef.Literal(subtype = Subtype(name = "Monk"))),
                                ),
                                Mechanic.SetPowerToughness(power = n(3), toughness = n(3)),
                                Mechanic.GainAbilities(abilities = listOf(Keyword.Haste)),
                            ),
                            affects = Affected.Objects(objects = Objects.Self),
                            duration = Duration.EndOfTurn,
                        ),
                    ),
                ),
            ),
            interveningIf = Condition.HappenedThisTurn(
                event = EventPattern.Cast(spell = ObjectFilter.Any, by = PlayerFilter.You),
                atLeast = 2,
                measure = Measure.Events,
            ),
        ),
        Ability.Activated(
            cost = Cost(
                parts = listOf(
                    mana("{1}{R}"),
                    CostPart.Discard(what = CostObjects.These(objects = Objects.Each(filter = and(inZone(Zone.Hand), ownedByYou)))),
                    CostPart.Sacrifice(what = CostObjects.Self),
                ),
            ),
            instructions = Instructions(steps = listOf(doing(Effect.Draw(players = Players.You, count = n(2))))),
            rules = anyTime,
        ),
    )

    // CR 707.10: the copy keeps the spell's choices (new targets only when the text says so, CR 707.10c)
    @Test
    fun sageOfTheHeavens() = card(
        name = "Sage of the Heavens",
        cost = "{2}{W}",
        type = "Creature — Human Monk",
        pt = "2/3",
        text = """
            When you cast this spell, if you've cast another spell this turn, copy this spell. (The copy becomes a token.)
            Flying, lifelink
        """,
    ) parsesTo abilities(
        Ability.Triggered(
            trigger = Trigger.On(events = listOf(EventPattern.Cast(spell = isSelf, by = PlayerFilter.You))),
            instructions = Instructions(steps = listOf(doing(Effect.CopySpell(objects = Objects.Self, controller = Players.You, newTargets = false)))),
            interveningIf = Condition.HappenedThisTurn(
                event = EventPattern.Cast(spell = not(isSelf), by = PlayerFilter.You),
                atLeast = 1,
                measure = Measure.Events,
            ),
        ),
        Keyword.Flying,
        Keyword.Lifelink,
    )

    // CR 707.10: the copy keeps the spell's choices (new targets only when the text says so, CR 707.10c)
    @Test
    fun rhenuMasterSchemer() = card(
        name = "Rhenu, Master Schemer",
        cost = "{1}{U}",
        type = "Legendary Creature — Human Monk",
        pt = "2/2",
        text = "Flurry — Whenever you cast your second spell each turn, copy it, then exile the spell you cast with four time counters on it. If it doesn't have suspend, it gains suspend. (At the beginning of its owner's upkeep, they remove a time counter. When the last is removed, they may play it without paying its mana cost. If it's a creature, it has haste.)",
    ) parsesTo abilities(
        Ability.Triggered(
            trigger = Trigger.On(events = listOf(EventPattern.Cast(spell = ObjectFilter.Any, by = PlayerFilter.You)), nthThisTurn = 2),
            instructions = Instructions(
                steps = listOf(
                    doing(
                        Effect.CopySpell(
                            objects = Objects.EventObject(role = EventRole.Object),
                            controller = Players.You,
                            newTargets = false,
                        ),
                    ),
                    doing(
                        Effect.Move(
                            objects = Objects.EventObject(role = EventRole.Object),
                            to = Destination(zone = Zone.Exile, counters = listOf(CountersToPut(kind = CounterKind.Time, amount = n(4)))),
                            postBind = "exiled",
                        ),
                    ),
                    Step.If(
                        condition = Condition.Not(
                            condition = Condition.ObjectMatches(
                                objects = bound("exiled"),
                                filter = ObjectFilter.HasKeyword(keyword = Keyword.Suspend.Kind),
                            ),
                        ),
                        then = listOf(
                            doing(
                                Effect.ApplyMechanics(
                                    mechanics = listOf(
                                        Mechanic.GainAbilities(
                                            abilities = listOf(Keyword.Suspend(amount = 4, cost = Cost(parts = listOf()))),
                                        ),
                                    ),
                                    affects = Affected.Objects(objects = bound("exiled")),
                                    duration = Duration.Indefinitely,
                                ),
                            ),
                        ),
                    ),
                ),
            ),
            abilityWord = "Flurry",
        ),
    )

    @Test
    fun pathspeakerGuard() = card(
        name = "Pathspeaker Guard",
        cost = "{3}{W}",
        type = "Creature — Orc Monk",
        pt = "3/4",
        text = """
            When this creature enters, return target nonland permanent card with mana value 2 or less from your graveyard to your hand.
            Flurry — Whenever you cast your second spell each turn, tap target creature an opponent controls.
        """,
    ) parsesTo abilities(
        Ability.Triggered(
            trigger = entersSelf,
            instructions = Instructions(
                targets = listOf(
                    TargetSlot(
                        name = "card",
                        what = TargetKind.Objects(
                            filter = and(
                                not(land),
                                or(
                                    artifact,
                                    ObjectFilter.Type(cardType = CardTypeRef.Literal(cardType = CardType.Battle)),
                                    creature,
                                    enchantment,
                                    land,
                                    planeswalker,
                                ),
                                ObjectFilter.ManaValue(comparison = Comparison.AtMost, value = n(2)),
                                inZone(Zone.Graveyard),
                                ownedByYou,
                            ),
                        ),
                        min = n(1),
                        max = n(1),
                    ),
                ),
                steps = listOf(doing(Effect.Move(objects = target("card"), to = Destination(zone = Zone.Hand)))),
            ),
        ),
        Ability.Triggered(
            trigger = Trigger.On(events = listOf(EventPattern.Cast(spell = ObjectFilter.Any, by = PlayerFilter.You)), nthThisTurn = 2),
            instructions = Instructions(
                targets = listOf(TargetSlot(name = "creature", what = TargetKind.Objects(filter = and(creature, opponents)), min = n(1), max = n(1))),
                steps = listOf(doing(Effect.Tap(objects = target("creature")))),
            ),
            abilityWord = "Flurry",
        ),
    )

    @Test
    fun featherbladeDisciple() = card(
        name = "Featherblade Disciple",
        cost = "{2}{U}",
        type = "Creature — Human Monk",
        pt = "2/2",
        text = """
            Flying
            Flurry — Whenever you cast your second spell each turn, create a 1/1 white Bird creature token with flying.
        """,
    ) parsesTo abilities(
        Keyword.Flying,
        Ability.Triggered(
            trigger = Trigger.On(events = listOf(EventPattern.Cast(spell = ObjectFilter.Any, by = PlayerFilter.You)), nthThisTurn = 2),
            instructions = Instructions(
                steps = listOf(
                    doing(
                        Effect.CreateToken(
                            token = TokenSpec.Defined(
                                face = FaceDefinition(
                                    name = "Bird Token",
                                    colorIndicator = listOf(Color.White),
                                    types = listOf(CardType.Creature),
                                    subtypes = listOf(Subtype(name = "Bird")),
                                    power = PrintedNumber.Fixed(value = 1),
                                    toughness = PrintedNumber.Fixed(value = 1),
                                    abilities = listOf(Keyword.Flying),
                                ),
                            ),
                            count = n(1),
                            controller = Players.You,
                        ),
                    ),
                ),
            ),
            abilityWord = "Flurry",
        ),
    )
}
