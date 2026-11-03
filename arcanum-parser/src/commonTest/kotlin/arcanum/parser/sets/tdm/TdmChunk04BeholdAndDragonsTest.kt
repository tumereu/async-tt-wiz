package arcanum.parser.sets.tdm

import arcanum.api.*
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.cardFaces
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

/**
 * Tarkir: Dragonstorm chunk 04: beholding a Dragon as an optional additional cost and "if a Dragon was beheld" (CR 701.4), flash by beholding (CR 702.8a), spells that cost more if they target a Dragon, Dragon tutors and mana, activated abilities that can't be activated, winning by counters on five Dragons.
 */
class TdmChunk04BeholdAndDragonsTest {
    @Test
    fun rockhewnDragon() = card(
        name = "Rockhewn Dragon",
        cost = "{5}",
        type = "Artifact Creature — Dragon",
        pt = "3/3",
        text = """
            Flying, vigilance
            Whenever this creature attacks, surveil 1. (Look at the top card of your library. You may put it into your graveyard.)
        """,
    ) parsesTo abilities(
        Keyword.Flying,
        Keyword.Vigilance,
        Ability.Triggered(
            trigger = attacksSelf,
            instructions = Instructions(steps = listOf(doing(Effect.Surveil(players = Players.You, count = n(1))))),
        ),
    )

    @Test
    fun summonTheElderWyrms() = card(
        name = "Summon the Elder Wyrms",
        cost = "{W}{U}{B}{R}{G}",
        type = "Enchantment",
        text = """
            Dragons you control have indestructible.
            At the beginning of your upkeep, for each color, put a +1/+1 counter on a Dragon you control of that color. If you put +1/+1 counters on five Dragons this way, you win the game.
        """,
    ) parsesTo abilities(
        Ability.Static(
            mechanics = listOf(Mechanic.GainAbilities(abilities = listOf(Keyword.Indestructible))),
            affects = Affected.Objects(objects = Objects.Each(filter = and(sub("Dragon"), you))),
        ),
        Ability.Triggered(
            trigger = Trigger.On(events = listOf(EventPattern.StepBegins(step = TurnStep.Upkeep, whose = PlayerFilter.You))),
            instructions = Instructions(
                steps = listOf(
                    Step.Choose(
                        choice = Choice.Objects(
                            filter = and(sub("Dragon"), you, ObjectFilter.OfColor(color = ColorRef.Literal(color = Color.White))),
                            min = n(1),
                            max = n(1),
                        ),
                        bind = "dragon-white",
                        chooser = Players.You,
                    ),
                    doing(Effect.PutCounters(objects = bound("dragon-white"), kind = p1p1, amount = n(1))),
                    Step.Choose(
                        choice = Choice.Objects(
                            filter = and(sub("Dragon"), you, ObjectFilter.OfColor(color = ColorRef.Literal(color = Color.Blue))),
                            min = n(1),
                            max = n(1),
                        ),
                        bind = "dragon-blue",
                        chooser = Players.You,
                    ),
                    doing(Effect.PutCounters(objects = bound("dragon-blue"), kind = p1p1, amount = n(1))),
                    Step.Choose(
                        choice = Choice.Objects(
                            filter = and(sub("Dragon"), you, ObjectFilter.OfColor(color = ColorRef.Literal(color = Color.Black))),
                            min = n(1),
                            max = n(1),
                        ),
                        bind = "dragon-black",
                        chooser = Players.You,
                    ),
                    doing(Effect.PutCounters(objects = bound("dragon-black"), kind = p1p1, amount = n(1))),
                    Step.Choose(
                        choice = Choice.Objects(
                            filter = and(sub("Dragon"), you, ObjectFilter.OfColor(color = ColorRef.Literal(color = Color.Red))),
                            min = n(1),
                            max = n(1),
                        ),
                        bind = "dragon-red",
                        chooser = Players.You,
                    ),
                    doing(Effect.PutCounters(objects = bound("dragon-red"), kind = p1p1, amount = n(1))),
                    Step.Choose(
                        choice = Choice.Objects(
                            filter = and(sub("Dragon"), you, ObjectFilter.OfColor(color = ColorRef.Literal(color = Color.Green))),
                            min = n(1),
                            max = n(1),
                        ),
                        bind = "dragon-green",
                        chooser = Players.You,
                    ),
                    doing(Effect.PutCounters(objects = bound("dragon-green"), kind = p1p1, amount = n(1))),
                    Step.If(
                        condition = Condition.Compare(
                            left = Value.Count(
                                objects = Objects.Union(
                                    parts = listOf(
                                        bound("dragon-white"),
                                        bound("dragon-blue"),
                                        bound("dragon-black"),
                                        bound("dragon-red"),
                                        bound("dragon-green"),
                                    ),
                                ),
                            ),
                            comparison = Comparison.AtLeast,
                            right = n(5),
                        ),
                        then = listOf(doing(Effect.WinGame(players = Players.You))),
                    ),
                ),
            ),
        ),
    )

    @Test
    fun acridBreath() = card(
        name = "Acrid Breath",
        cost = "{B}",
        type = "Instant",
        text = """
            As an additional cost to cast this spell, behold a Dragon or pay {1}. (To behold a Dragon, choose a Dragon you control or reveal a Dragon card from your hand.)
            Target creature gets -3/-3 until end of turn.
        """,
    ) parsesTo abilities(
        Ability.Static(
            mechanics = listOf(
                Mechanic.AdditionalCost(
                    cost = Cost(
                        parts = listOf(
                            CostPart.OneOf(
                                options = listOf(
                                    Cost(
                                        parts = listOf(
                                            CostPart.Action(
                                                action = KeywordAction.Behold(filter = sub("Dragon"), count = n(1)),
                                                bind = "beheld",
                                            ),
                                        ),
                                    ),
                                    Cost(parts = listOf(mana("{1}"))),
                                ),
                            ),
                        ),
                    ),
                ),
            ),
            affects = Affected.Objects(objects = Objects.Self),
        ),
        Ability.Spell(
            instructions = Instructions(
                targets = listOf(TargetSlot(name = "creature", what = TargetKind.Objects(filter = creature), min = n(1), max = n(1))),
                steps = listOf(
                    doing(
                        Effect.ApplyMechanics(
                            mechanics = listOf(Mechanic.ModifyPowerToughness(power = n(-3), toughness = n(-3))),
                            affects = Affected.Objects(objects = target("creature")),
                            duration = Duration.EndOfTurn,
                        ),
                    ),
                ),
            ),
        ),
    )

    @Test
    fun silencingConqueror() = card(
        name = "Silencing Conqueror",
        cost = "{2}{W}",
        type = "Creature — Dragon",
        pt = "3/3",
        text = """
            Flying
            Activated abilities of artifacts, creatures, and planeswalkers can't be activated.
        """,
    ) parsesTo abilities(
        Keyword.Flying,
        Ability.Static(
            mechanics = listOf(Mechanic.CantActivate(abilities = ObjectFilter.AbilityOf(source = or(artifact, creature, planeswalker)))),
            affects = Affected.Players(players = Players.Each(filter = PlayerFilter.Any)),
        ),
    )

    @Test
    fun unmakingBreath() = card(
        name = "Unmaking Breath",
        cost = "{1}{U}",
        type = "Instant",
        text = """
            As an additional cost to cast this spell, you may behold a Dragon. (You may choose a Dragon you control or reveal a Dragon card from your hand.)
            Counter target spell unless its controller pays {2}. If a Dragon was beheld, counter that spell unless its controller pays {4} instead.
        """,
    ) parsesTo abilities(
        Ability.Static(
            mechanics = listOf(
                Mechanic.AdditionalCost(
                    cost = Cost(
                        parts = listOf(CostPart.Action(action = KeywordAction.Behold(filter = sub("Dragon"), count = n(1)), bind = "beheld")),
                    ),
                    optional = true,
                ),
            ),
            affects = Affected.Objects(objects = Objects.Self),
        ),
        Ability.Spell(
            instructions = Instructions(
                targets = listOf(TargetSlot(name = "spell", what = TargetKind.Objects(filter = spellFilter), min = n(1), max = n(1))),
                steps = listOf(
                    Step.If(
                        condition = Condition.Paid(times = 1, of = Objects.Self),
                        then = listOf(
                            Step.Pay(
                                cost = Cost(parts = listOf(mana("{4}"))),
                                payer = Players.ControllerOf(objects = target("spell")),
                                bind = "paid",
                            ),
                            Step.If(
                                condition = Condition.Not(condition = Condition.Did(name = "paid")),
                                then = listOf(doing(Effect.Counter(objects = target("spell")))),
                            ),
                        ),
                        otherwise = listOf(
                            Step.Pay(
                                cost = Cost(parts = listOf(mana("{2}"))),
                                payer = Players.ControllerOf(objects = target("spell")),
                                bind = "paid",
                            ),
                            Step.If(
                                condition = Condition.Not(condition = Condition.Did(name = "paid")),
                                then = listOf(doing(Effect.Counter(objects = target("spell")))),
                            ),
                        ),
                    ),
                ),
            ),
        ),
    )

    @Test
    fun wyrmgenesis() = card(
        name = "Wyrmgenesis",
        cost = "{6}{R}{R}",
        type = "Enchantment",
        text = "You may cast Dragon spells without paying their mana costs.",
    ) parsesTo abilities(
        Ability.Static(
            mechanics = listOf(Mechanic.MayPlay(from = Zone.Hand, how = PlayKind.Cast, withoutPaying = true, holder = PlayerFilter.You)),
            affects = Affected.Objects(objects = Objects.Each(filter = and(sub("Dragon"), inZone(Zone.Hand), ownedByYou))),
        ),
    )

    @Test
    fun wyrmsQuarry() = card(
        name = "Wyrm's Quarry",
        cost = "{2}{B}",
        type = "Instant",
        text = """
            This spell costs {2} more to cast if it targets a Dragon.
            Destroy target creature.
        """,
    ) parsesTo abilities(
        Ability.Static(
            mechanics = listOf(Mechanic.CostChange(what = isSelf, change = CostChange.More(mana = ManaCost.parse("{2}")!!, times = n(1)), minimum = 0)),
            affects = Affected.Objects(objects = Objects.Self),
            condition = Condition.ObjectMatches(objects = Objects.Self, filter = ObjectFilter.Targets(objects = Objects.Each(filter = sub("Dragon")))),
        ),
        Ability.Spell(
            instructions = Instructions(
                targets = listOf(TargetSlot(name = "creature", what = TargetKind.Objects(filter = creature), min = n(1), max = n(1))),
                steps = listOf(doing(Effect.Destroy(objects = target("creature")))),
            ),
        ),
    )

    @Test
    fun wyrmScholar() = card(
        name = "Wyrm Scholar",
        cost = "{2}{U}",
        type = "Creature — Human Wizard",
        pt = "1/3",
        text = """
            When this creature enters, look at the top six cards of your library. You may reveal an instant, sorcery, or Dragon card from among them and put it into your hand. Put the rest on the bottom of your library in a random order.
            Untapped Dragons you control have hexproof.
        """,
    ) parsesTo abilities(
        Ability.Triggered(
            trigger = entersSelf,
            instructions = Instructions(
                steps = listOf(
                    doing(
                        Effect.LookAt(
                            objects = Objects.LibraryCards(of = Players.You, count = n(6), fromTop = true),
                            players = Players.You,
                            bind = "seen",
                        ),
                    ),
                    Step.Choose(
                        choice = Choice.Objects(filter = or(instant, sorcery, sub("Dragon")), min = n(0), max = n(1), from = bound("seen")),
                        bind = "chosen",
                        chooser = Players.You,
                    ),
                    doing(Effect.Reveal(objects = bound("chosen"))),
                    doing(Effect.Move(objects = bound("chosen"), to = Destination(zone = Zone.Hand))),
                    doing(
                        Effect.Move(
                            objects = Objects.Except(base = bound("seen"), minus = bound("chosen")),
                            to = Destination(zone = Zone.Library, libraryPosition = -1, randomOrder = true),
                        ),
                    ),
                ),
            ),
        ),
        Ability.Static(
            mechanics = listOf(Mechanic.GainAbilities(abilities = listOf(Keyword.Hexproof))),
            affects = Affected.Objects(objects = Objects.Each(filter = and(not(tapped), sub("Dragon"), you))),
        ),
    )

    @Test
    fun stormbroodAugur() = card(
        name = "Stormbrood Augur",
        cost = "{U}",
        type = "Creature — Human Scout",
        pt = "0/3",
        text = "{2}, {T}: Search your library for a card named Stormbrood Orb or Rockhewn Dragon, reveal it, put it into your hand, then shuffle.",
    ) parsesTo abilities(
        Ability.Activated(
            cost = Cost(parts = listOf(mana("{2}"), tap)),
            instructions = Instructions(
                steps = listOf(
                    doing(
                        Effect.Search(
                            library = Players.You,
                            filter = or(ObjectFilter.Named(name = "Stormbrood Orb"), ObjectFilter.Named(name = "Rockhewn Dragon")),
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
            ),
            rules = anyTime,
        ),
    )

    @Test
    fun stormbroodOrb() = card(
        name = "Stormbrood Orb",
        cost = "{3}",
        type = "Artifact",
        text = """
            Each Dragon you control enters with an additional +1/+1 counter on it.
            {T}: Add one mana of any color.
        """,
    ) parsesTo abilities(
        Ability.Static(
            mechanics = listOf(
                Mechanic.Replacement(
                    event = EventPattern.ZoneChange(objects = and(sub("Dragon"), you), to = Zone.Battlefield),
                    modify = listOf(Modification.EntersWithCounters(kind = p1p1, amount = n(1))),
                ),
            ),
            affects = Affected.Objects(objects = Objects.Self),
        ),
        Ability.Activated(
            cost = Cost(parts = listOf(tap)),
            instructions = Instructions(
                steps = listOf(doing(Effect.AddMana(mana = ManaProduction(kind = ManaKind.Choose(amount = n(1))), players = Players.You))),
            ),
            rules = anyTime,
        ),
    )

    @Test
    fun cindermawSentry() = card(
        name = "Cindermaw Sentry",
        cost = "{2}",
        type = "Artifact Creature — Chimera",
        pt = "2/1",
        text = "When this creature enters, you may search your library for a basic land card, reveal it, then shuffle and put that card on top. If you control a Dragon, put that card onto the battlefield tapped instead.",
    ) parsesTo abilities(
        Ability.Triggered(
            trigger = entersSelf,
            instructions = Instructions(
                steps = listOf(
                    Step.May(
                        steps = listOf(
                            doing(
                                Effect.Search(
                                    library = Players.You,
                                    filter = and(ObjectFilter.Super(supertype = Supertype.Basic), land),
                                    max = n(1),
                                    reveal = true,
                                    zones = listOf(Zone.Library),
                                    bind = "found",
                                    searcher = Players.You,
                                ),
                            ),
                            doing(Effect.Shuffle(library = Players.You, except = bound("found"))),
                            Step.If(
                                condition = Condition.Exists(filter = and(sub("Dragon"), you), atLeast = 1),
                                then = listOf(
                                    doing(Effect.Move(objects = bound("found"), to = Destination(zone = Zone.Battlefield, tapped = true))),
                                ),
                                otherwise = listOf(
                                    doing(Effect.Move(objects = bound("found"), to = Destination(zone = Zone.Library, libraryPosition = 0))),
                                ),
                            ),
                        ),
                        player = Players.You,
                    ),
                ),
            ),
        ),
    )

    @Test
    fun vortexOfTheElderWyrm() = card(
        name = "Vortex of the Elder Wyrm",
        cost = null,
        type = "Land",
        text = """
            {T}: Add {C}.
            {T}: Add one mana of any color. Spend this mana only to cast a Dragon spell or an Omen spell.
            {4}, {T}, Sacrifice this land: Search your library for a Dragon card, reveal it, put it into your hand, then shuffle.
        """,
    ) parsesTo abilities(
        Ability.Activated(
            cost = Cost(parts = listOf(tap)),
            instructions = Instructions(
                steps = listOf(
                    doing(
                        Effect.AddMana(mana = ManaProduction(kind = ManaKind.Fixed(mana = ManaCost.parse("{C}")!!)), players = Players.You),
                    ),
                ),
            ),
            rules = anyTime,
        ),
        Ability.Activated(
            cost = Cost(parts = listOf(tap)),
            instructions = Instructions(
                steps = listOf(
                    doing(
                        Effect.AddMana(
                            mana = ManaProduction(
                                kind = ManaKind.Choose(amount = n(1)),
                                restriction = and(spellFilter, or(and(sub("Dragon"), spellFilter), sub("Omen"))),
                            ),
                            players = Players.You,
                        ),
                    ),
                ),
            ),
            rules = anyTime,
        ),
        Ability.Activated(
            cost = Cost(parts = listOf(mana("{4}"), tap, CostPart.Sacrifice(what = CostObjects.Self))),
            instructions = Instructions(
                steps = listOf(
                    doing(
                        Effect.Search(
                            library = Players.You,
                            filter = sub("Dragon"),
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
            ),
            rules = anyTime,
        ),
    )

    @Test
    fun lavabornHellkite() = card(
        name = "Lavaborn Hellkite",
        cost = "{2}{R}{R}",
        type = "Creature — Dragon",
        pt = "4/5",
        text = """
            Flying
            When this creature enters, destroy target nonbasic land an opponent controls. Its controller searches their library for a basic land card, puts it onto the battlefield tapped with a stun counter on it, then shuffles. (If a permanent with a stun counter would become untapped, remove one from it instead.)
        """,
    ) parsesTo abilities(
        Keyword.Flying,
        Ability.Triggered(
            trigger = entersSelf,
            instructions = Instructions(
                targets = listOf(
                    TargetSlot(
                        name = "land",
                        what = TargetKind.Objects(filter = and(not(ObjectFilter.Super(supertype = Supertype.Basic)), land, opponents)),
                        min = n(1),
                        max = n(1),
                    ),
                ),
                steps = listOf(
                    doing(Effect.Destroy(objects = target("land"))),
                    doing(
                        Effect.Search(
                            library = Players.ControllerOf(objects = target("land")),
                            filter = and(ObjectFilter.Super(supertype = Supertype.Basic), land),
                            max = n(1),
                            zones = listOf(Zone.Library),
                            bind = "found",
                            searcher = Players.ControllerOf(objects = target("land")),
                        ),
                    ),
                    doing(
                        Effect.Move(
                            objects = bound("found"),
                            to = Destination(
                                zone = Zone.Battlefield,
                                tapped = true,
                                counters = listOf(CountersToPut(kind = CounterKind.Stun, amount = n(1))),
                            ),
                        ),
                    ),
                    doing(Effect.Shuffle(library = Players.ControllerOf(objects = target("land")))),
                ),
            ),
        ),
    )

    @Test
    fun searingBreath() = card(
        name = "Searing Breath",
        cost = "{1}{R}",
        type = "Sorcery",
        text = """
            You may cast this spell as though it had flash if you behold a Dragon as an additional cost to cast it. (To behold a Dragon, choose a Dragon you control or reveal a Dragon card from your hand.)
            Searing Breath deals 4 damage to target creature or planeswalker.
        """,
    ) parsesTo abilities(
        Ability.Static(
            mechanics = listOf(
                Mechanic.AdditionalCost(
                    cost = Cost(
                        parts = listOf(CostPart.Action(action = KeywordAction.Behold(filter = sub("Dragon"), count = n(1)), bind = "beheld")),
                    ),
                    optional = true,
                    flash = true,
                ),
            ),
            affects = Affected.Objects(objects = Objects.Self),
        ),
        Ability.Spell(
            instructions = Instructions(
                targets = listOf(
                    TargetSlot(name = "permanent", what = TargetKind.Objects(filter = or(creature, planeswalker)), min = n(1), max = n(1)),
                ),
                steps = listOf(doing(Effect.DealDamage(to = Recipients(objects = target("permanent")), amount = n(4), source = Objects.Self))),
            ),
        ),
    )

    @Test
    fun moxCarnelian() = card(
        name = "Mox Carnelian",
        cost = "{0}",
        type = "Legendary Artifact",
        text = "{T}: Add one mana of any color. Activate only if you control a Dragon.",
    ) parsesTo abilities(
        Ability.Activated(
            cost = Cost(parts = listOf(tap)),
            instructions = Instructions(
                steps = listOf(doing(Effect.AddMana(mana = ManaProduction(kind = ManaKind.Choose(amount = n(1))), players = Players.You))),
            ),
            rules = ActivationRules(
                timing = Timing.Any,
                activatedBy = PlayerFilter.You,
                condition = Condition.Exists(filter = and(sub("Dragon"), you), atLeast = 1),
            ),
        ),
    )

    @Test
    fun marrowBreath() = card(
        name = "Marrow Breath",
        cost = "{1}{W}",
        type = "Instant",
        text = """
            As an additional cost to cast this spell, you may behold a Dragon. (You may choose a Dragon you control or reveal a Dragon card from your hand.)
            Marrow Breath deals 5 damage to target attacking or blocking creature. If a Dragon was beheld, you gain 2 life.
        """,
    ) parsesTo abilities(
        Ability.Static(
            mechanics = listOf(
                Mechanic.AdditionalCost(
                    cost = Cost(
                        parts = listOf(CostPart.Action(action = KeywordAction.Behold(filter = sub("Dragon"), count = n(1)), bind = "beheld")),
                    ),
                    optional = true,
                ),
            ),
            affects = Affected.Objects(objects = Objects.Self),
        ),
        Ability.Spell(
            instructions = Instructions(
                targets = listOf(
                    TargetSlot(
                        name = "creature",
                        what = TargetKind.Objects(filter = and(or(attacking, ObjectFilter.HasStatus(status = Status.Blocking)), creature)),
                        min = n(1),
                        max = n(1),
                    ),
                ),
                steps = listOf(
                    doing(Effect.DealDamage(to = Recipients(objects = target("creature")), amount = n(5), source = Objects.Self)),
                    Step.If(
                        condition = Condition.Paid(times = 1, of = Objects.Self),
                        then = listOf(doing(Effect.GainLife(players = Players.You, amount = n(2)))),
                    ),
                ),
            ),
        ),
    )

    @Test
    fun lancingBreath() = card(
        name = "Lancing Breath",
        cost = "{1}{G}",
        type = "Instant",
        text = """
            As an additional cost to cast this spell, you may behold a Dragon. (You may choose a Dragon you control or reveal a Dragon card from your hand.)
            Target creature you control deals damage equal to its power to target creature or planeswalker. If a Dragon was beheld, surveil 2.
        """,
    ) parsesTo abilities(
        Ability.Static(
            mechanics = listOf(
                Mechanic.AdditionalCost(
                    cost = Cost(
                        parts = listOf(CostPart.Action(action = KeywordAction.Behold(filter = sub("Dragon"), count = n(1)), bind = "beheld")),
                    ),
                    optional = true,
                ),
            ),
            affects = Affected.Objects(objects = Objects.Self),
        ),
        Ability.Spell(
            instructions = Instructions(
                targets = listOf(
                    TargetSlot(name = "creature", what = TargetKind.Objects(filter = and(creature, you)), min = n(1), max = n(1)),
                    TargetSlot(name = "permanent", what = TargetKind.Objects(filter = or(creature, planeswalker)), min = n(1), max = n(1)),
                ),
                steps = listOf(
                    doing(
                        Effect.DealDamage(
                            to = Recipients(objects = target("permanent")),
                            amount = Value.Of(property = NumericProperty.Power, objects = target("creature"), how = Combine.Single),
                            source = target("creature"),
                        ),
                    ),
                    Step.If(
                        condition = Condition.Paid(times = 1, of = Objects.Self),
                        then = listOf(doing(Effect.Surveil(players = Players.You, count = n(2)))),
                    ),
                ),
            ),
        ),
    )

    @Test
    fun rhazirWyrmAscendant() = card(
        name = "Rhazir, Wyrm Ascendant",
        cost = "{1}{R}",
        type = "Legendary Creature — Human Druid",
        pt = "2/2",
        text = """
            When Rhazir enters, you may behold a Dragon. If you do, create a Treasure token. (To behold a Dragon, choose a Dragon you control or reveal a Dragon card from your hand.)
            Whenever a Dragon you control enters, put a +1/+1 counter on Rhazir. Until end of turn, Rhazir becomes a Dragon in addition to his other types and gains flying.
        """,
    ) parsesTo abilities(
        Ability.Triggered(
            trigger = entersSelf,
            instructions = Instructions(
                steps = listOf(
                    Step.Pay(
                        cost = Cost(
                            parts = listOf(CostPart.Action(action = KeywordAction.Behold(filter = sub("Dragon"), count = n(1)), bind = "beheld")),
                        ),
                        payer = Players.You,
                        bind = "paid",
                    ),
                    Step.If(
                        condition = Condition.Did(name = "paid"),
                        then = listOf(
                            doing(
                                Effect.CreateToken(token = TokenSpec.Predefined(name = "Treasure"), count = n(1), controller = Players.You),
                            ),
                        ),
                    ),
                ),
            ),
        ),
        Ability.Triggered(
            trigger = Trigger.On(events = listOf(EventPattern.ZoneChange(objects = and(sub("Dragon"), you), to = Zone.Battlefield))),
            instructions = Instructions(
                steps = listOf(
                    doing(Effect.PutCounters(objects = Objects.Self, kind = p1p1, amount = n(1))),
                    doing(
                        Effect.ApplyMechanics(
                            mechanics = listOf(
                                Mechanic.SetTypes(addSubtypes = listOf(SubtypeRef.Literal(subtype = Subtype(name = "Dragon")))),
                                Mechanic.GainAbilities(abilities = listOf(Keyword.Flying)),
                            ),
                            affects = Affected.Objects(objects = Objects.Self),
                            duration = Duration.EndOfTurn,
                        ),
                    ),
                ),
            ),
        ),
    )

    @Test
    fun thunderscaleHeir() = card(
        name = "Thunderscale Heir",
        cost = "{4}{R}{R}",
        type = "Creature — Dragon",
        pt = "4/4",
        text = """
            Flying
            Other Dragons you control get +1/+1.
            Storm (When you cast this spell, copy it for each spell cast before it this turn. Copies become tokens.)
        """,
    ) parsesTo abilities(
        Keyword.Flying,
        Ability.Static(
            mechanics = listOf(Mechanic.ModifyPowerToughness(power = n(1), toughness = n(1))),
            affects = Affected.Objects(objects = Objects.Each(filter = and(not(isSelf), sub("Dragon"), you))),
        ),
        Keyword.Storm,
    )
}
