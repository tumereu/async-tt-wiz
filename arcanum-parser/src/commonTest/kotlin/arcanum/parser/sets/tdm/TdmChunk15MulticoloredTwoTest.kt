package arcanum.parser.sets.tdm

import arcanum.api.*
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.cardFaces
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

/**
 * Tarkir: Dragonstorm chunk 15: multicolored cards and Sagas: a prevention that deals the damage back and draws (CR 615.5), counters of two kinds as a card returns, a hand card exiled for a Spirit token later, the first counters on a creature this turn, cheaper spells for big creatures during your turn.
 */
class TdmChunk15MulticoloredTwoTest {
    @Test
    fun marshalOfTheForsaken() = card(
        name = "Marshal of the Forsaken",
        cost = "{2}{W}{B}",
        type = "Creature — Orc Warrior",
        pt = "3/3",
        text = """
            Deathtouch
            Whenever you attack, target creature gets +X/+X until end of turn, where X is the number of attacking creatures.
        """,
    ) parsesTo abilities(
        Keyword.Deathtouch,
        Ability.Triggered(
            trigger = Trigger.On(events = listOf(EventPattern.Attack(attacker = you, withOthers = 0)), oneOrMore = true),
            instructions = Instructions(
                targets = listOf(TargetSlot(name = "creature", what = TargetKind.Objects(filter = creature), min = n(1), max = n(1))),
                steps = listOf(
                    doing(
                        Effect.ApplyMechanics(
                            mechanics = listOf(
                                Mechanic.ModifyPowerToughness(
                                    power = Value.Count(objects = Objects.Each(filter = and(attacking, creature))),
                                    toughness = Value.Count(objects = Objects.Each(filter = and(attacking, creature))),
                                ),
                            ),
                            affects = Affected.Objects(objects = target("creature")),
                            duration = Duration.EndOfTurn,
                        ),
                    ),
                ),
            ),
        ),
    )

    @Test
    fun cloisterMessenger() = card(
        name = "Cloister Messenger",
        cost = "{2/U}{2/R}{2/W}",
        type = "Creature — Bird Scout",
        pt = "2/3",
        text = """
            Flying, vigilance
            When this creature enters, put up to one target noncreature, nonland card from your graveyard on top of your library.
        """,
    ) parsesTo abilities(
        Keyword.Flying,
        Keyword.Vigilance,
        Ability.Triggered(
            trigger = entersSelf,
            instructions = Instructions(
                targets = listOf(
                    TargetSlot(
                        name = "card",
                        what = TargetKind.Objects(filter = and(not(creature), not(land), inZone(Zone.Graveyard), ownedByYou)),
                        min = n(0),
                        max = n(1),
                    ),
                ),
                steps = listOf(doing(Effect.Move(objects = target("card"), to = Destination(zone = Zone.Library, libraryPosition = 0)))),
            ),
        ),
    )

    @Test
    fun anotherWayOnward() = card(
        name = "Another Way Onward",
        cost = "{2}{U}{R}{W}",
        type = "Instant",
        text = "The next time a source of your choice would deal damage to you this turn, prevent that damage. When damage is prevented this way, Another Way Onward deals that much damage to that source's controller and you draw that many cards.",
    ) parsesTo abilities(
        Ability.Spell(
            instructions = Instructions(
                steps = listOf(
                    Step.Choose(
                        choice = Choice.Objects(filter = ObjectFilter.Any, min = n(1), max = n(1)),
                        bind = "source",
                        chooser = Players.You,
                    ),
                    doing(
                        Effect.ApplyMechanics(
                            mechanics = listOf(
                                Mechanic.Prevention(
                                    event = EventPattern.Damage(
                                        source = ObjectFilter.Is(objects = bound("source")),
                                        to = RecipientFilter(players = PlayerFilter.You),
                                    ),
                                    additional = Instructions(
                                        steps = listOf(
                                            doing(
                                                Effect.ReflexiveTrigger(
                                                    instructions = Instructions(
                                                        steps = listOf(
                                                            doing(
                                                                Effect.DealDamage(
                                                                    to = Recipients(players = Players.ControllerOf(objects = bound("source"))),
                                                                    amount = Value.EventAmount,
                                                                    source = Objects.Self,
                                                                ),
                                                            ),
                                                            doing(Effect.Draw(players = Players.You, count = Value.EventAmount)),
                                                        ),
                                                    ),
                                                ),
                                            ),
                                        ),
                                    ),
                                ),
                            ),
                            affects = null,
                            duration = Duration.EndOfTurn,
                            uses = 1,
                        ),
                    ),
                ),
            ),
        ),
    )

    @Test
    fun everbloomReturn() = card(
        name = "Everbloom Return",
        cost = "{3}{W}{B}{G}",
        type = "Sorcery",
        text = "Return target permanent card from your graveyard to the battlefield with a hexproof counter and an indestructible counter on it.",
    ) parsesTo abilities(
        Ability.Spell(
            instructions = Instructions(
                targets = listOf(
                    TargetSlot(
                        name = "card",
                        what = TargetKind.Objects(
                            filter = and(
                                or(
                                    artifact,
                                    ObjectFilter.Type(cardType = CardTypeRef.Literal(cardType = CardType.Battle)),
                                    creature,
                                    enchantment,
                                    land,
                                    planeswalker,
                                ),
                                inZone(Zone.Graveyard),
                                ownedByYou,
                            ),
                        ),
                        min = n(1),
                        max = n(1),
                    ),
                ),
                steps = listOf(
                    doing(
                        Effect.Move(
                            objects = target("card"),
                            to = Destination(
                                zone = Zone.Battlefield,
                                counters = listOf(
                                    CountersToPut(kind = CounterKind.Keyword(keyword = Keyword.Hexproof), amount = n(1)),
                                    CountersToPut(kind = CounterKind.Keyword(keyword = Keyword.Indestructible), amount = n(1)),
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        ),
    )

    @Test
    fun rakshasasPact() = card(
        name = "Rakshasa's Pact",
        cost = "{2/B}{2/G}{2/U}",
        type = "Instant",
        text = "Look at the top four cards of your library. Put two of them into your hand and the rest into your graveyard.",
    ) parsesTo abilities(
        Ability.Spell(
            instructions = Instructions(
                steps = listOf(
                    doing(
                        Effect.LookAt(
                            objects = Objects.LibraryCards(of = Players.You, count = n(4), fromTop = true),
                            players = Players.You,
                            bind = "looked",
                        ),
                    ),
                    Step.Choose(
                        choice = Choice.Objects(filter = ObjectFilter.Any, min = n(2), max = n(2), from = bound("looked")),
                        bind = "picked",
                        chooser = Players.You,
                    ),
                    doing(Effect.Move(objects = bound("picked"), to = Destination(zone = Zone.Hand))),
                    doing(
                        Effect.Move(
                            objects = Objects.Except(base = bound("looked"), minus = bound("picked")),
                            to = Destination(zone = Zone.Graveyard),
                        ),
                    ),
                ),
            ),
        ),
    )

    @Test
    fun relearnThePath() = card(
        name = "Relearn the Path",
        cost = "{U}{R}{W}",
        type = "Enchantment — Saga",
        text = """
            (As this Saga enters and after your draw step, add a lore counter. Sacrifice after III.)
            I, II — Look at the top three cards of your library. Put one of them into your hand and the rest on the bottom of your library in any order.
            III — Whenever you cast a noncreature spell this turn, target creature you control gains double strike until end of turn.
        """,
    ) parsesTo abilities(
        Ability.Triggered(
            trigger = Trigger.Chapter(chapters = listOf(1, 2)),
            instructions = Instructions(
                steps = listOf(
                    doing(
                        Effect.LookAt(
                            objects = Objects.LibraryCards(of = Players.You, count = n(3), fromTop = true),
                            players = Players.You,
                            bind = "seen",
                        ),
                    ),
                    Step.Choose(
                        choice = Choice.Objects(filter = ObjectFilter.Any, min = n(1), max = n(1), from = bound("seen")),
                        bind = "kept",
                        chooser = Players.You,
                    ),
                    doing(Effect.Move(objects = bound("kept"), to = Destination(zone = Zone.Hand))),
                    doing(
                        Effect.Move(
                            objects = Objects.Except(base = bound("seen"), minus = bound("kept")),
                            to = Destination(zone = Zone.Library, libraryPosition = -1),
                        ),
                    ),
                ),
            ),
        ),
        Ability.Triggered(
            trigger = Trigger.Chapter(chapters = listOf(3)),
            instructions = Instructions(
                steps = listOf(
                    doing(
                        Effect.CreateDelayedTrigger(
                            trigger = Trigger.On(events = listOf(EventPattern.Cast(spell = not(creature), by = PlayerFilter.You))),
                            instructions = Instructions(
                                targets = listOf(
                                    TargetSlot(
                                        name = "creature",
                                        what = TargetKind.Objects(filter = and(creature, you)),
                                        min = n(1),
                                        max = n(1),
                                    ),
                                ),
                                steps = listOf(
                                    doing(
                                        Effect.ApplyMechanics(
                                            mechanics = listOf(Mechanic.GainAbilities(abilities = listOf(Keyword.DoubleStrike))),
                                            affects = Affected.Objects(objects = target("creature")),
                                            duration = Duration.EndOfTurn,
                                        ),
                                    ),
                                ),
                            ),
                            duration = Duration.EndOfTurn,
                            repeats = true,
                        ),
                    ),
                ),
            ),
        ),
    )

    @Test
    fun trustedMerchant() = card(
        name = "Trusted Merchant",
        cost = "{2/W}{2/B}{2/G}",
        type = "Creature — Human Citizen",
        pt = "2/2",
        text = "When this creature enters or dies, put a +1/+1 counter on target creature you control.",
    ) parsesTo abilities(
        Ability.Triggered(
            trigger = Trigger.On(
                events = listOf(
                    EventPattern.ZoneChange(objects = isSelf, to = Zone.Battlefield),
                    EventPattern.ZoneChange(objects = isSelf, from = Zone.Battlefield, to = Zone.Graveyard),
                ),
            ),
            instructions = Instructions(
                targets = listOf(TargetSlot(name = "creature", what = TargetKind.Objects(filter = and(creature, you)), min = n(1), max = n(1))),
                steps = listOf(doing(Effect.PutCounters(objects = target("creature"), kind = p1p1, amount = n(1)))),
            ),
        ),
    )

    @Test
    fun returnOfTheForebears() = card(
        name = "Return of the Forebears",
        cost = "{1}{W}{B}{G}",
        type = "Enchantment — Saga",
        text = """
            (As this Saga enters and after your draw step, add a lore counter. Sacrifice after III.)
            I — Create three 1/1 white Spirit creature tokens.
            II — Distribute three +1/+1 counters among one, two, or three target creatures you control.
            III — Creatures you control gain trample and lifelink until end of turn.
        """,
    ) parsesTo abilities(
        Ability.Triggered(
            trigger = Trigger.Chapter(chapters = listOf(1)),
            instructions = Instructions(
                steps = listOf(
                    doing(
                        Effect.CreateToken(
                            token = TokenSpec.Defined(
                                face = FaceDefinition(
                                    name = "Spirit Token",
                                    colorIndicator = listOf(Color.White),
                                    types = listOf(CardType.Creature),
                                    subtypes = listOf(Subtype(name = "Spirit")),
                                    power = PrintedNumber.Fixed(value = 1),
                                    toughness = PrintedNumber.Fixed(value = 1),
                                ),
                            ),
                            count = n(3),
                            controller = Players.You,
                        ),
                    ),
                ),
            ),
        ),
        Ability.Triggered(
            trigger = Trigger.Chapter(chapters = listOf(2)),
            instructions = Instructions(
                targets = listOf(
                    TargetSlot(
                        name = "creatures",
                        what = TargetKind.Objects(filter = and(creature, you)),
                        min = n(1),
                        max = n(3),
                        divided = n(3),
                    ),
                ),
                steps = listOf(doing(Effect.PutCounters(objects = target("creatures"), kind = p1p1, amount = Value.DividedShare))),
            ),
        ),
        Ability.Triggered(
            trigger = Trigger.Chapter(chapters = listOf(3)),
            instructions = Instructions(
                steps = listOf(
                    doing(
                        Effect.ApplyMechanics(
                            mechanics = listOf(Mechanic.GainAbilities(abilities = listOf(Keyword.Trample, Keyword.Lifelink))),
                            affects = Affected.Objects(objects = Objects.Each(filter = and(creature, you))),
                            duration = Duration.EndOfTurn,
                        ),
                    ),
                ),
            ),
        ),
    )

    @Test
    fun millwheelSweep() = card(
        name = "Millwheel Sweep",
        cost = "{2/U}{2/R}{2/W}",
        type = "Sorcery",
        text = """
            Tap target creature. Put three stun counters on it. (If a permanent with a stun counter would become untapped, remove one from it instead.)
            Exile the top two cards of your library. Choose one of them. Until the end of your next turn, you may play that card.
        """,
    ) parsesTo abilities(
        Ability.Spell(
            instructions = Instructions(
                targets = listOf(TargetSlot(name = "creature", what = TargetKind.Objects(filter = creature), min = n(1), max = n(1))),
                steps = listOf(
                    doing(Effect.Tap(objects = target("creature"))),
                    doing(Effect.PutCounters(objects = target("creature"), kind = CounterKind.Stun, amount = n(3))),
                    doing(
                        Effect.Move(
                            objects = Objects.LibraryCards(of = Players.You, count = n(2), fromTop = true),
                            to = Destination(zone = Zone.Exile),
                            postBind = "exiled",
                        ),
                    ),
                    Step.Choose(
                        choice = Choice.Objects(filter = ObjectFilter.Any, min = n(1), max = n(1), from = bound("exiled")),
                        bind = "chosen",
                        chooser = Players.You,
                    ),
                    doing(
                        Effect.ApplyMechanics(
                            mechanics = listOf(Mechanic.MayPlay(from = Zone.Exile, how = PlayKind.Play, holder = PlayerFilter.You)),
                            affects = Affected.Objects(objects = bound("chosen")),
                            duration = Duration.UntilEndOfYourNextTurn,
                        ),
                    ),
                ),
            ),
        ),
    )

    @Test
    fun bellowOfTheEndlessHymn() = card(
        name = "Bellow of the Endless Hymn",
        cost = "{2}{G}{U}{R}",
        type = "Enchantment — Saga",
        text = """
            (As this Saga enters and after your draw step, add a lore counter. Sacrifice after III.)
            I, II — Create a 5/5 green Elephant creature token.
            III — Double the power and toughness of each creature you control until end of turn.
        """,
    ) parsesTo abilities(
        Ability.Triggered(
            trigger = Trigger.Chapter(chapters = listOf(1, 2)),
            instructions = Instructions(
                steps = listOf(
                    doing(
                        Effect.CreateToken(
                            token = TokenSpec.Defined(
                                face = FaceDefinition(
                                    name = "Elephant Token",
                                    colorIndicator = listOf(Color.Green),
                                    types = listOf(CardType.Creature),
                                    subtypes = listOf(Subtype(name = "Elephant")),
                                    power = PrintedNumber.Fixed(value = 5),
                                    toughness = PrintedNumber.Fixed(value = 5),
                                ),
                            ),
                            count = n(1),
                            controller = Players.You,
                        ),
                    ),
                ),
            ),
        ),
        Ability.Triggered(
            trigger = Trigger.Chapter(chapters = listOf(3)),
            instructions = Instructions(
                steps = listOf(
                    Step.ForEach(
                        each = Each.Objects(objects = Objects.Each(filter = and(creature, you))),
                        bind = "creature",
                        steps = listOf(
                            doing(
                                Effect.ApplyMechanics(
                                    mechanics = listOf(
                                        Mechanic.ModifyPowerToughness(
                                            power = Value.Of(property = NumericProperty.Power, objects = bound("creature"), how = Combine.Single),
                                            toughness = Value.Of(
                                                property = NumericProperty.Toughness,
                                                objects = bound("creature"),
                                                how = Combine.Single,
                                            ),
                                        ),
                                    ),
                                    affects = Affected.Objects(objects = bound("creature")),
                                    duration = Duration.EndOfTurn,
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        ),
    )

    @Test
    fun sunderingPriest() = card(
        name = "Sundering Priest",
        cost = "{W}{B}{G}",
        type = "Creature — Djinn Cleric",
        pt = "3/3",
        text = """
            Deathtouch
            When this creature enters, target opponent reveals their hand. You may choose a nonland card from it. If you do, exile that card.
            When this creature leaves the battlefield, the exiled card's owner creates an X/X white Spirit creature token, where X is the mana value of the exiled card.
        """,
    ) parsesTo abilities(
        Keyword.Deathtouch,
        Ability.Triggered(
            trigger = entersSelf,
            instructions = Instructions(
                targets = listOf(TargetSlot(name = "opponent", what = TargetKind.Players(filter = PlayerFilter.Opponent), min = n(1), max = n(1))),
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
                    Step.Pay(
                        steps = listOf(
                            Step.Choose(
                                choice = Choice.Objects(filter = not(land), min = n(1), max = n(1), from = bound("revealed")),
                                bind = "chosen",
                                chooser = Players.You,
                            ),
                        ),
                        payer = Players.You,
                        bind = "paid",
                    ),
                    Step.If(
                        condition = Condition.Did(name = "paid"),
                        then = listOf(
                            doing(Effect.Move(objects = bound("chosen"), to = Destination(zone = Zone.Exile), postBind = "exiled")),
                            doing(Effect.Remember(key = "exiled", objects = bound("exiled"))),
                        ),
                    ),
                ),
            ),
        ),
        Ability.Triggered(
            trigger = Trigger.On(events = listOf(EventPattern.ZoneChange(objects = isSelf, from = Zone.Battlefield))),
            instructions = Instructions(
                steps = listOf(
                    doing(
                        Effect.CreateToken(
                            token = TokenSpec.Defined(
                                face = FaceDefinition(
                                    name = "Spirit Token",
                                    colorIndicator = listOf(Color.White),
                                    types = listOf(CardType.Creature),
                                    subtypes = listOf(Subtype(name = "Spirit")),
                                    power = PrintedNumber.X,
                                    toughness = PrintedNumber.X,
                                ),
                                power = Value.Of(
                                    property = NumericProperty.ManaValue,
                                    objects = Objects.Remembered(key = "exiled"),
                                    how = Combine.Total,
                                ),
                                toughness = Value.Of(
                                    property = NumericProperty.ManaValue,
                                    objects = Objects.Remembered(key = "exiled"),
                                    how = Combine.Total,
                                ),
                            ),
                            count = n(1),
                            controller = Players.OwnerOf(objects = Objects.Remembered(key = "exiled")),
                        ),
                    ),
                ),
            ),
        ),
    )

    @Test
    fun meleeRhino() = card(
        name = "Melee Rhino",
        cost = "{W}{B}{G}",
        type = "Creature — Rhino",
        pt = "3/4",
        text = """
            Trample
            When this creature enters, each opponent loses 2 life and you gain 2 life.
        """,
    ) parsesTo abilities(
        Keyword.Trample,
        Ability.Triggered(
            trigger = entersSelf,
            instructions = Instructions(
                steps = listOf(
                    doing(Effect.LoseLife(players = Players.Each(filter = PlayerFilter.Opponent), amount = n(2))),
                    doing(Effect.GainLife(players = Players.You, amount = n(2))),
                ),
            ),
        ),
    )

    @Test
    fun steadfastSuccessor() = card(
        name = "Steadfast Successor",
        cost = "{1}{B}{G}",
        type = "Creature — Human Warrior",
        pt = "3/2",
        text = """
            Menace (This creature can't be blocked except by two or more creatures.)
            Whenever one or more counters are put on a creature you control, if it's the first time counters have been put on that creature this turn, put a +1/+1 counter on that creature.
        """,
    ) parsesTo abilities(
        Keyword.Menace,
        Ability.Triggered(
            trigger = Trigger.On(events = listOf(EventPattern.CountersPut(objects = and(creature, you))), oneOrMore = true),
            instructions = Instructions(
                steps = listOf(doing(Effect.PutCounters(objects = Objects.EventObject(role = EventRole.Object), kind = p1p1, amount = n(1)))),
            ),
            interveningIf = Condition.Compare(
                left = Value.EventsThisTurn(
                    event = EventPattern.CountersPut(objects = ObjectFilter.Is(objects = Objects.EventObject(role = EventRole.Object))),
                    measure = Measure.Events,
                ),
                comparison = Comparison.Equal,
                right = n(1),
            ),
        ),
    )

    @Test
    fun frontierBattlecrier() = card(
        name = "Frontier Battlecrier",
        cost = "{G}{U}{R}",
        type = "Creature — Orc Ranger",
        pt = "4/3",
        text = "During your turn, spells you cast cost {1} less to cast for each creature you control with power 4 or greater.",
    ) parsesTo abilities(
        Ability.Static(
            mechanics = listOf(
                Mechanic.CostChange(
                    what = and(spellFilter, you),
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
            condition = Condition.YourTurn,
        ),
    )

    @Test
    fun rumbleOfUnity() = card(
        name = "Rumble of Unity",
        cost = "{R}{W}{B}",
        type = "Enchantment — Saga",
        text = """
            (As this Saga enters and after your draw step, add a lore counter. Sacrifice after III.)
            I — You draw two cards and you lose 2 life.
            II, III — Whenever a creature you control enters this turn, each opponent loses 1 life and you gain 1 life.
        """,
    ) parsesTo abilities(
        Ability.Triggered(
            trigger = Trigger.Chapter(chapters = listOf(1)),
            instructions = Instructions(
                steps = listOf(
                    doing(Effect.Draw(players = Players.You, count = n(2))),
                    doing(Effect.LoseLife(players = Players.You, amount = n(2))),
                ),
            ),
        ),
        Ability.Triggered(
            trigger = Trigger.Chapter(chapters = listOf(2, 3)),
            instructions = Instructions(
                steps = listOf(
                    doing(
                        Effect.CreateDelayedTrigger(
                            trigger = Trigger.On(events = listOf(EventPattern.ZoneChange(objects = and(creature, you), to = Zone.Battlefield))),
                            instructions = Instructions(
                                steps = listOf(
                                    doing(Effect.LoseLife(players = Players.Each(filter = PlayerFilter.Opponent), amount = n(1))),
                                    doing(Effect.GainLife(players = Players.You, amount = n(1))),
                                ),
                            ),
                            duration = Duration.EndOfTurn,
                            repeats = true,
                        ),
                    ),
                ),
            ),
        ),
    )

    @Test
    fun barrowRoadwatcher() = card(
        name = "Barrow Roadwatcher",
        cost = "{1}{W}{B}{G}",
        type = "Creature — Human Scout",
        pt = "3/3",
        text = "When this creature enters, if you cast it, mill four cards. When you do, return target creature card with mana value 3 or less from your graveyard to the battlefield.",
    ) parsesTo abilities(
        Ability.Triggered(
            trigger = entersSelf,
            instructions = Instructions(
                steps = listOf(
                    doing(Effect.Mill(players = Players.You, count = n(4))),
                    doing(
                        Effect.ReflexiveTrigger(
                            instructions = Instructions(
                                targets = listOf(
                                    TargetSlot(
                                        name = "card",
                                        what = TargetKind.Objects(
                                            filter = and(
                                                creature,
                                                ObjectFilter.ManaValue(comparison = Comparison.AtMost, value = n(3)),
                                                inZone(Zone.Graveyard),
                                                ownedByYou,
                                            ),
                                        ),
                                        min = n(1),
                                        max = n(1),
                                    ),
                                ),
                                steps = listOf(doing(Effect.Move(objects = target("card"), to = Destination(zone = Zone.Battlefield)))),
                            ),
                        ),
                    ),
                ),
            ),
            interveningIf = Condition.CastFrom(zone = null, of = Objects.Self),
        ),
    )
}
