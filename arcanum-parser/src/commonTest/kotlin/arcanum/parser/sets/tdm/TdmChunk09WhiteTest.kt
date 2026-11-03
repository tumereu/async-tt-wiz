package arcanum.parser.sets.tdm

import arcanum.api.*
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.cardFaces
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

/**
 * Tarkir: Dragonstorm chunk 09: white cards: two cards from a single graveyard, double tokens, counters on returned creatures, a returned token that draws, a card named like itself, permanents onto the battlefield from the top seven.
 */
class TdmChunk09WhiteTest {
    @Test
    fun dawnwallSunshield() = card(
        name = "Dawnwall Sunshield",
        cost = "{3}{W}",
        type = "Creature — Human Warrior",
        pt = "3/4",
        text = """
            When this creature enters, exile up to two target cards from a single graveyard.
            {W}, {T}: Tap target creature.
        """,
    ) parsesTo abilities(
        Ability.Triggered(
            trigger = entersSelf,
            instructions = Instructions(
                targets = listOf(
                    TargetSlot(
                        name = "card",
                        what = TargetKind.Objects(filter = and(cardFilter, inZone(Zone.Graveyard))),
                        min = n(0),
                        max = n(2),
                        sameOwner = true,
                    ),
                ),
                steps = listOf(doing(Effect.Move(objects = target("card"), to = Destination(zone = Zone.Exile)))),
            ),
        ),
        Ability.Activated(
            cost = Cost(parts = listOf(mana("{W}"), tap)),
            instructions = Instructions(
                targets = listOf(TargetSlot(name = "creature", what = TargetKind.Objects(filter = creature), min = n(1), max = n(1))),
                steps = listOf(doing(Effect.Tap(objects = target("creature")))),
            ),
            rules = anyTime,
        ),
    )

    @Test
    fun bearerOfHonor() = card(
        name = "Bearer of Honor",
        cost = "{1}{W}",
        type = "Creature — Human Soldier",
        pt = "2/1",
        text = """
            During your turn, this creature has first strike.
            {4}{W}: Creatures you control get +1/+1 until end of turn.
        """,
    ) parsesTo abilities(
        Ability.Static(
            mechanics = listOf(Mechanic.GainAbilities(abilities = listOf(Keyword.FirstStrike))),
            affects = Affected.Objects(objects = Objects.Self),
            condition = Condition.YourTurn,
        ),
        Ability.Activated(
            cost = Cost(parts = listOf(mana("{4}{W}"))),
            instructions = Instructions(
                steps = listOf(
                    doing(
                        Effect.ApplyMechanics(
                            mechanics = listOf(Mechanic.ModifyPowerToughness(power = n(1), toughness = n(1))),
                            affects = Affected.Objects(objects = Objects.Each(filter = and(creature, you))),
                            duration = Duration.EndOfTurn,
                        ),
                    ),
                ),
            ),
            rules = anyTime,
        ),
    )

    @Test
    fun concertedManeuver() = card(
        name = "Concerted Maneuver",
        cost = "{1}{W}",
        type = "Instant",
        text = """
            Choose one —
            • Concerted Maneuver deals damage equal to the number of creatures you control to target creature or planeswalker.
            • Destroy target enchantment.
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
                                        what = TargetKind.Objects(filter = or(creature, planeswalker)),
                                        min = n(1),
                                        max = n(1),
                                    ),
                                ),
                                steps = listOf(
                                    doing(
                                        Effect.DealDamage(
                                            to = Recipients(objects = target("permanent")),
                                            amount = Value.Count(objects = Objects.Each(filter = and(creature, you))),
                                            source = Objects.Self,
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
                                        name = "enchantment",
                                        what = TargetKind.Objects(filter = enchantment),
                                        min = n(1),
                                        max = n(1),
                                    ),
                                ),
                                steps = listOf(doing(Effect.Destroy(objects = target("enchantment")))),
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
    fun oathPastDeath() = card(
        name = "Oath Past Death",
        cost = "{1}{W}",
        type = "Instant",
        text = """
            As an additional cost to cast this spell, sacrifice a creature.
            Creatures you control gain indestructible until end of turn. Put a +1/+1 counter on each creature you control. (Damage and effects that say "destroy" don't destroy those creatures.)
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
                steps = listOf(
                    doing(
                        Effect.ApplyMechanics(
                            mechanics = listOf(Mechanic.GainAbilities(abilities = listOf(Keyword.Indestructible))),
                            affects = Affected.Objects(objects = Objects.Each(filter = and(creature, you))),
                            duration = Duration.EndOfTurn,
                        ),
                    ),
                    doing(Effect.PutCounters(objects = Objects.Each(filter = and(creature, you)), kind = p1p1, amount = n(1))),
                ),
            ),
        ),
    )

    @Test
    fun aurelleStormBreaker() = card(
        name = "Aurelle, Storm Breaker",
        cost = "{3}{W}{W}",
        type = "Legendary Planeswalker — Elspeth",
        loyalty = "5",
        text = """
            If one or more tokens would be created under your control, twice that many of those tokens are created instead.
            [+1]: Create a 1/1 white Soldier creature token.
            [0]: Put a +1/+1 counter on each creature you control. Those creatures gain flying until your next turn.
            [−3]: Destroy target creature an opponent controls with mana value 3 or greater.
        """,
    ) parsesTo abilities(
        Ability.Static(
            mechanics = listOf(
                Mechanic.Replacement(
                    event = EventPattern.CreateToken(tokens = you),
                    modify = listOf(Modification.MultiplyAmount(factor = 2)),
                ),
            ),
            affects = Affected.Objects(objects = Objects.Self),
        ),
        Ability.Activated(
            cost = Cost(parts = listOf(CostPart.Loyalty(amount = n(1)))),
            instructions = Instructions(
                steps = listOf(
                    doing(
                        Effect.CreateToken(
                            token = TokenSpec.Defined(
                                face = FaceDefinition(
                                    name = "Soldier Token",
                                    colorIndicator = listOf(Color.White),
                                    types = listOf(CardType.Creature),
                                    subtypes = listOf(Subtype(name = "Soldier")),
                                    power = PrintedNumber.Fixed(value = 1),
                                    toughness = PrintedNumber.Fixed(value = 1),
                                ),
                            ),
                            count = n(1),
                            controller = Players.You,
                        ),
                    ),
                ),
            ),
            rules = anyTime,
        ),
        Ability.Activated(
            cost = Cost(parts = listOf(CostPart.Loyalty(amount = n(0)))),
            instructions = Instructions(
                steps = listOf(
                    doing(Effect.PutCounters(objects = Objects.Each(filter = and(creature, you)), kind = p1p1, amount = n(1))),
                    doing(
                        Effect.ApplyMechanics(
                            mechanics = listOf(Mechanic.GainAbilities(abilities = listOf(Keyword.Flying))),
                            affects = Affected.Objects(objects = Objects.Each(filter = and(creature, you))),
                            duration = Duration.UntilYourNextTurn,
                        ),
                    ),
                ),
            ),
            rules = anyTime,
        ),
        Ability.Activated(
            cost = Cost(parts = listOf(CostPart.Loyalty(amount = n(-3)))),
            instructions = Instructions(
                targets = listOf(
                    TargetSlot(
                        name = "creature",
                        what = TargetKind.Objects(
                            filter = and(creature, opponents, ObjectFilter.ManaValue(comparison = Comparison.AtLeast, value = n(3))),
                        ),
                        min = n(1),
                        max = n(1),
                    ),
                ),
                steps = listOf(doing(Effect.Destroy(objects = target("creature")))),
            ),
            rules = anyTime,
        ),
    )

    @Test
    fun wrathfulForebear() = card(
        name = "Wrathful Forebear",
        cost = "{1}{W}",
        type = "Creature — Spirit Warrior",
        pt = "3/1",
        text = "Whenever a creature you control dies while this card is in your graveyard, you may pay {1}{W}. If you do, return this card from your graveyard to your hand.",
    ) parsesTo abilities(
        Ability.Triggered(
            trigger = Trigger.On(events = listOf(EventPattern.ZoneChange(objects = and(creature, you), from = Zone.Battlefield, to = Zone.Graveyard))),
            instructions = Instructions(
                steps = listOf(
                    Step.Pay(cost = Cost(parts = listOf(mana("{1}{W}"))), payer = Players.You, bind = "paid"),
                    Step.If(
                        condition = Condition.Did(name = "paid"),
                        then = listOf(doing(Effect.Move(objects = Objects.Self, to = Destination(zone = Zone.Hand)))),
                    ),
                ),
            ),
            triggersOnlyIf = Condition.ObjectMatches(objects = Objects.Self, filter = inZone(Zone.Graveyard)),
            functionsIn = listOf(Zone.Graveyard),
        ),
    )

    @Test
    fun swiftstepTechnique() = card(
        name = "Swiftstep Technique",
        cost = "{1}{W}",
        type = "Instant",
        text = "Put a +1/+1 counter on target creature. It gains flying and indestructible until end of turn. (Damage and effects that say \"destroy\" don't destroy it.)",
    ) parsesTo abilities(
        Ability.Spell(
            instructions = Instructions(
                targets = listOf(TargetSlot(name = "creature", what = TargetKind.Objects(filter = creature), min = n(1), max = n(1))),
                steps = listOf(
                    doing(Effect.PutCounters(objects = target("creature"), kind = p1p1, amount = n(1))),
                    doing(
                        Effect.ApplyMechanics(
                            mechanics = listOf(Mechanic.GainAbilities(abilities = listOf(Keyword.Flying, Keyword.Indestructible))),
                            affects = Affected.Objects(objects = target("creature")),
                            duration = Duration.EndOfTurn,
                        ),
                    ),
                ),
            ),
        ),
    )

    @Test
    fun pachydermWarPriest() = card(
        name = "Pachyderm War Priest",
        cost = "{4}{W}",
        type = "Creature — Elephant Cleric",
        pt = "3/5",
        text = "At the beginning of combat on your turn, put a +1/+1 counter on another target creature you control.",
    ) parsesTo abilities(
        Ability.Triggered(
            trigger = Trigger.On(events = listOf(EventPattern.StepBegins(step = TurnStep.BeginningOfCombat, whose = PlayerFilter.You))),
            instructions = Instructions(
                targets = listOf(
                    TargetSlot(
                        name = "creature",
                        what = TargetKind.Objects(filter = and(not(isSelf), creature, you)),
                        min = n(1),
                        max = n(1),
                    ),
                ),
                steps = listOf(doing(Effect.PutCounters(objects = target("creature"), kind = p1p1, amount = n(1)))),
            ),
        ),
    )

    @Test
    fun hordeDevotee() = card(
        name = "Horde Devotee",
        cost = "{W}",
        type = "Creature — Human Scout",
        pt = "1/2",
        text = """
            When this creature enters, scry 2. (Look at the top two cards of your library, then put any number of them on the bottom and the rest on top in any order.)
            {1}: Add {R}, {W}, or {B}. Activate only once each turn.
        """,
    ) parsesTo abilities(
        Ability.Triggered(
            trigger = entersSelf,
            instructions = Instructions(steps = listOf(doing(Effect.Scry(players = Players.You, count = n(2))))),
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
                                        ColorRef.Literal(color = Color.Red),
                                        ColorRef.Literal(color = Color.White),
                                        ColorRef.Literal(color = Color.Black),
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
    fun rebelsStrike() = card(
        name = "Rebel's Strike",
        cost = "{1}{W}",
        type = "Instant",
        text = """
            Target creature gets +3/+0 until end of turn.
            Draw a card.
        """,
    ) parsesTo abilities(
        Ability.Spell(
            instructions = Instructions(
                targets = listOf(TargetSlot(name = "creature", what = TargetKind.Objects(filter = creature), min = n(1), max = n(1))),
                steps = listOf(
                    doing(
                        Effect.ApplyMechanics(
                            mechanics = listOf(Mechanic.ModifyPowerToughness(power = n(3), toughness = n(0))),
                            affects = Affected.Objects(objects = target("creature")),
                            duration = Duration.EndOfTurn,
                        ),
                    ),
                    doing(Effect.Draw(players = Players.You, count = n(1))),
                ),
            ),
        ),
    )

    @Test
    fun caravanPackbeast() = card(
        name = "Caravan Packbeast",
        cost = "{5}{W}",
        type = "Creature — Beast",
        pt = "4/3",
        text = """
            Affinity for creatures (This spell costs {1} less to cast for each creature you control.)
            When this creature enters, draw a card.
        """,
    ) parsesTo abilities(
        Keyword.Affinity(forWhat = creature, printed = "creatures"),
        Ability.Triggered(
            trigger = entersSelf,
            instructions = Instructions(steps = listOf(doing(Effect.Draw(players = Players.You, count = n(1))))),
        ),
    )

    @Test
    fun laughAtTheGrave() = card(
        name = "Laugh at the Grave",
        cost = "{3}{W}{W}",
        type = "Enchantment",
        text = "At the beginning of your upkeep, return up to two target creature cards with power 2 or less from your graveyard to the battlefield. Put a +1/+1 counter on each of those creatures.",
    ) parsesTo abilities(
        Ability.Triggered(
            trigger = Trigger.On(events = listOf(EventPattern.StepBegins(step = TurnStep.Upkeep, whose = PlayerFilter.You))),
            instructions = Instructions(
                targets = listOf(
                    TargetSlot(
                        name = "card",
                        what = TargetKind.Objects(
                            filter = and(
                                creature,
                                ObjectFilter.Power(comparison = Comparison.AtMost, value = n(2)),
                                inZone(Zone.Graveyard),
                                ownedByYou,
                            ),
                        ),
                        min = n(0),
                        max = n(2),
                    ),
                ),
                steps = listOf(
                    doing(Effect.Move(objects = target("card"), to = Destination(zone = Zone.Battlefield), postBind = "returned")),
                    doing(Effect.PutCounters(objects = bound("returned"), kind = p1p1, amount = n(1))),
                ),
            ),
        ),
    )

    @Test
    fun wideEyedSkyrider() = card(
        name = "Wide-Eyed Skyrider",
        cost = "{2}{W}",
        type = "Creature — Human Scout",
        pt = "1/3",
        text = """
            Flying
            Whenever this creature attacks, another target creature you control gains flying until end of turn.
            Attacking tokens you control have flying.
        """,
    ) parsesTo abilities(
        Keyword.Flying,
        Ability.Triggered(
            trigger = attacksSelf,
            instructions = Instructions(
                targets = listOf(
                    TargetSlot(
                        name = "creature",
                        what = TargetKind.Objects(filter = and(not(isSelf), creature, you)),
                        min = n(1),
                        max = n(1),
                    ),
                ),
                steps = listOf(
                    doing(
                        Effect.ApplyMechanics(
                            mechanics = listOf(Mechanic.GainAbilities(abilities = listOf(Keyword.Flying))),
                            affects = Affected.Objects(objects = target("creature")),
                            duration = Duration.EndOfTurn,
                        ),
                    ),
                ),
            ),
        ),
        Ability.Static(
            mechanics = listOf(Mechanic.GainAbilities(abilities = listOf(Keyword.Flying))),
            affects = Affected.Objects(objects = Objects.Each(filter = and(attacking, token, you))),
        ),
    )

    @Test
    fun cracklingSnare() = card(
        name = "Crackling Snare",
        cost = "{4}{W}",
        type = "Enchantment",
        text = """
            Flash
            This spell costs {1} less to cast for each attacking creature.
            When this enchantment enters, exile target artifact or creature an opponent controls until this enchantment leaves the battlefield.
        """,
    ) parsesTo abilities(
        Keyword.Flash,
        Ability.Static(
            mechanics = listOf(
                Mechanic.CostChange(
                    what = isSelf,
                    change = CostChange.Less(
                        mana = ManaCost.parse("{1}")!!,
                        times = Value.Count(objects = Objects.Each(filter = and(attacking, creature))),
                    ),
                    minimum = 0,
                ),
            ),
            affects = Affected.Objects(objects = Objects.Self),
        ),
        Ability.Triggered(
            trigger = entersSelf,
            instructions = Instructions(
                targets = listOf(
                    TargetSlot(
                        name = "permanent",
                        what = TargetKind.Objects(filter = and(or(artifact, creature), opponents)),
                        min = n(1),
                        max = n(1),
                    ),
                ),
                steps = listOf(doing(Effect.Move(objects = target("permanent"), to = Destination(zone = Zone.Exile), untilLeaves = Objects.Self))),
            ),
        ),
    )

    @Test
    fun stormsignalBlade() = card(
        name = "Stormsignal Blade",
        cost = "{1}{W}",
        type = "Artifact — Equipment",
        text = """
            Equipped creature gets +3/+0.
            Whenever equipped creature attacks, draw a card if you control three or more attacking creatures.
            Equip {2} ({2}: Attach to target creature you control. Equip only as a sorcery.)
        """,
    ) parsesTo abilities(
        Ability.Static(
            mechanics = listOf(Mechanic.ModifyPowerToughness(power = n(3), toughness = n(0))),
            affects = Affected.Objects(objects = Objects.AttachedTo(of = Objects.Self)),
        ),
        Ability.Triggered(
            trigger = Trigger.On(
                events = listOf(EventPattern.Attack(attacker = ObjectFilter.Is(objects = Objects.AttachedTo(of = Objects.Self)), withOthers = 0)),
            ),
            instructions = Instructions(
                steps = listOf(
                    Step.If(
                        condition = Condition.Exists(filter = and(attacking, creature, you), atLeast = 3),
                        then = listOf(doing(Effect.Draw(players = Players.You, count = n(1)))),
                    ),
                ),
            ),
        ),
        Keyword.Equip(cost = Cost(parts = listOf(mana("{2}")))),
    )

    @Test
    fun dawnpearlKirin() = card(
        name = "Dawnpearl Kirin",
        cost = "{1}{W}",
        type = "Creature — Kirin",
        pt = "2/1",
        text = """
            Flash
            Flying
            When this creature enters, return up to one other target nonland permanent you control to its owner's hand. If it was a token, draw a card.
        """,
    ) parsesTo abilities(
        Keyword.Flash,
        Keyword.Flying,
        Ability.Triggered(
            trigger = entersSelf,
            instructions = Instructions(
                targets = listOf(
                    TargetSlot(
                        name = "permanent",
                        what = TargetKind.Objects(filter = and(not(isSelf), not(land), permanentFilter, you)),
                        min = n(0),
                        max = n(1),
                    ),
                ),
                steps = listOf(
                    doing(Effect.Move(objects = target("permanent"), to = Destination(zone = Zone.Hand), preBind = "returned")),
                    Step.If(
                        condition = Condition.ObjectMatches(objects = bound("returned"), filter = token),
                        then = listOf(doing(Effect.Draw(players = Players.You, count = n(1)))),
                    ),
                ),
            ),
        ),
    )

    @Test
    fun galeHawk() = card(
        name = "Gale Hawk",
        cost = "{2}{W}",
        type = "Creature — Bird",
        pt = "2/2",
        text = """
            Flying
            Whenever this creature deals combat damage to a player, you may search your library for a card named Gale Hawk, reveal it, put it into your hand, then shuffle.
            A deck can have any number of cards named Gale Hawk.
        """,
    ) parsesTo abilities(
        Keyword.Flying,
        Ability.Triggered(
            trigger = Trigger.On(
                events = listOf(EventPattern.Damage(source = isSelf, to = RecipientFilter(players = PlayerFilter.Any), combat = true)),
            ),
            instructions = Instructions(
                steps = listOf(
                    Step.May(
                        steps = listOf(
                            doing(
                                Effect.Search(
                                    library = Players.You,
                                    filter = ObjectFilter.Named(name = "Gale Hawk"),
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
                        player = Players.You,
                    ),
                ),
            ),
        ),
        Ability.Static(mechanics = listOf(Mechanic.AnyNumberInDeck), affects = Affected.Objects(objects = Objects.Self)),
    )

    @Test
    fun joinedBattlefront() = card(
        name = "Joined Battlefront",
        cost = "{3}{W}",
        type = "Sorcery",
        text = "Look at the top seven cards of your library. Put up to two noncreature, nonland permanent cards with mana value 3 or less from among them onto the battlefield. Put the rest on the bottom of your library in a random order.",
    ) parsesTo abilities(
        Ability.Spell(
            instructions = Instructions(
                steps = listOf(
                    doing(
                        Effect.LookAt(
                            objects = Objects.LibraryCards(of = Players.You, count = n(7), fromTop = true),
                            players = Players.You,
                            bind = "looked",
                        ),
                    ),
                    Step.Choose(
                        choice = Choice.Objects(
                            filter = and(
                                not(creature),
                                not(land),
                                or(
                                    artifact,
                                    ObjectFilter.Type(cardType = CardTypeRef.Literal(cardType = CardType.Battle)),
                                    creature,
                                    enchantment,
                                    land,
                                    planeswalker,
                                ),
                                ObjectFilter.ManaValue(comparison = Comparison.AtMost, value = n(3)),
                            ),
                            min = n(0),
                            max = n(2),
                            from = bound("looked"),
                        ),
                        bind = "picked",
                        chooser = Players.You,
                    ),
                    doing(Effect.Move(objects = bound("picked"), to = Destination(zone = Zone.Battlefield))),
                    doing(
                        Effect.Move(
                            objects = Objects.Except(base = bound("looked"), minus = bound("picked")),
                            to = Destination(zone = Zone.Library, libraryPosition = -1, randomOrder = true),
                        ),
                    ),
                ),
            ),
        ),
    )
}
