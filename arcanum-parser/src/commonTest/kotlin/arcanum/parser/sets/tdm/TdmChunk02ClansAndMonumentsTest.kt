package arcanum.parser.sets.tdm

import arcanum.api.*
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.cardFaces
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

/**
 * Tarkir: Dragonstorm chunk 02: a clan chosen as the enchantment enters with each clan's ability working only when chosen (CR 614.12, 607.2a), triggers that trigger an additional time, the monuments' basic land searches, a delayed attack trigger for the turn, a creature exiled until this leaves and token copies of it attacking each opponent.
 */
class TdmChunk02ClansAndMonumentsTest {
    @Test
    fun houselordMonument() = card(
        name = "Houselord Monument",
        cost = "{2}",
        type = "Artifact",
        text = """
            When this artifact enters, search your library for a basic Plains, Swamp, or Forest card, reveal it, put it into your hand, then shuffle.
            {1}{W}{B}{G}, {T}, Sacrifice this artifact: Create an X/X white Spirit creature token, where X is the greatest toughness among creatures you control. Activate only as a sorcery.
        """,
    ) parsesTo abilities(
        Ability.Triggered(
            trigger = entersSelf,
            instructions = Instructions(
                steps = listOf(
                    doing(
                        Effect.Search(
                            library = Players.You,
                            filter = and(ObjectFilter.Super(supertype = Supertype.Basic), or(sub("Plains"), sub("Swamp"), sub("Forest"))),
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
        ),
        Ability.Activated(
            cost = Cost(parts = listOf(mana("{1}{W}{B}{G}"), tap, CostPart.Sacrifice(what = CostObjects.Self))),
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
                                    property = NumericProperty.Toughness,
                                    objects = Objects.Each(filter = and(creature, you)),
                                    how = Combine.Greatest,
                                ),
                                toughness = Value.Of(
                                    property = NumericProperty.Toughness,
                                    objects = Objects.Each(filter = and(creature, you)),
                                    how = Combine.Greatest,
                                ),
                            ),
                            count = n(1),
                            controller = Players.You,
                        ),
                    ),
                ),
            ),
            rules = asSorcery,
        ),
    )

    @Test
    fun saltplainSiege() = card(
        name = "Saltplain Siege",
        cost = "{2}{W}{B}",
        type = "Enchantment",
        text = """
            As this enchantment enters, choose Abzan or Mardu.
            • Abzan — At the beginning of your end step, put a +1/+1 counter on each creature you control.
            • Mardu — At the beginning of your end step, if a creature died under your control this turn, each opponent sacrifices a creature of their choice.
        """,
    ) parsesTo abilities(
        Ability.Static(
            mechanics = listOf(
                Mechanic.Replacement(
                    event = EventPattern.ZoneChange(objects = isSelf, to = Zone.Battlefield),
                    modify = listOf(
                        Modification.AsEnters(
                            steps = listOf(
                                Step.Choose(
                                    choice = Choice.Option(options = listOf("Abzan", "Mardu")),
                                    bind = "option",
                                    chooser = Players.You,
                                    remember = true,
                                ),
                            ),
                        ),
                    ),
                ),
            ),
            affects = Affected.Objects(objects = Objects.Self),
        ),
        Ability.Triggered(
            trigger = Trigger.On(events = listOf(EventPattern.StepBegins(step = TurnStep.End, whose = PlayerFilter.You))),
            instructions = Instructions(
                steps = listOf(doing(Effect.PutCounters(objects = Objects.Each(filter = and(creature, you)), kind = p1p1, amount = n(1)))),
            ),
            triggersOnlyIf = Condition.RememberedOption(key = "option", option = "Abzan"),
        ),
        Ability.Triggered(
            trigger = Trigger.On(events = listOf(EventPattern.StepBegins(step = TurnStep.End, whose = PlayerFilter.You))),
            instructions = Instructions(
                steps = listOf(
                    Step.ForEach(
                        each = Each.Players(players = Players.Each(filter = PlayerFilter.Opponent)),
                        bind = "opponent",
                        steps = listOf(
                            Step.Choose(
                                choice = Choice.Objects(
                                    filter = and(
                                        creature,
                                        ObjectFilter.ControlledBy(players = PlayerFilter.Is(players = Players.Bound(name = "opponent"))),
                                    ),
                                    min = n(1),
                                    max = n(1),
                                ),
                                bind = "sacrificed",
                                chooser = Players.Bound(name = "opponent"),
                            ),
                            doing(Effect.Sacrifice(objects = bound("sacrificed"))),
                        ),
                        together = true,
                    ),
                ),
            ),
            interveningIf = Condition.HappenedThisTurn(
                event = EventPattern.ZoneChange(objects = and(creature, you), from = Zone.Battlefield, to = Zone.Graveyard),
                atLeast = 1,
                measure = Measure.Events,
            ),
            triggersOnlyIf = Condition.RememberedOption(key = "option", option = "Mardu"),
        ),
    )

    @Test
    fun steppeEncampment() = card(
        name = "Steppe Encampment",
        cost = null,
        type = "Land",
        text = """
            This land enters tapped unless you control a Swamp or a Mountain.
            {T}: Add {W}.
            {2}{W}, {T}: Whenever you attack this turn, create two 1/1 red Warrior creature tokens that are tapped and attacking. Sacrifice them at the beginning of the next end step.
        """,
    ) parsesTo abilities(
        Ability.Static(
            mechanics = listOf(
                Mechanic.Replacement(
                    event = EventPattern.ZoneChange(objects = isSelf, to = Zone.Battlefield),
                    modify = listOf(Modification.EntersTapped),
                ),
            ),
            affects = Affected.Objects(objects = Objects.Self),
            condition = Condition.Not(condition = Condition.Exists(filter = and(or(sub("Swamp"), sub("Mountain")), you), atLeast = 1)),
        ),
        Ability.Activated(
            cost = Cost(parts = listOf(tap)),
            instructions = Instructions(
                steps = listOf(
                    doing(
                        Effect.AddMana(mana = ManaProduction(kind = ManaKind.Fixed(mana = ManaCost.parse("{W}")!!)), players = Players.You),
                    ),
                ),
            ),
            rules = anyTime,
        ),
        Ability.Activated(
            cost = Cost(parts = listOf(mana("{2}{W}"), tap)),
            instructions = Instructions(
                steps = listOf(
                    doing(
                        Effect.CreateDelayedTrigger(
                            trigger = Trigger.On(events = listOf(EventPattern.Attack(attacker = you, withOthers = 0)), oneOrMore = true),
                            instructions = Instructions(
                                steps = listOf(
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
                                            tapped = true,
                                            attacking = AttackTarget.Chosen,
                                            bind = "tokens",
                                        ),
                                    ),
                                    doing(
                                        Effect.CreateDelayedTrigger(
                                            trigger = Trigger.On(
                                                events = listOf(EventPattern.StepBegins(step = TurnStep.End, whose = PlayerFilter.Any)),
                                            ),
                                            instructions = Instructions(steps = listOf(doing(Effect.Sacrifice(objects = bound("tokens"))))),
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
            rules = anyTime,
        ),
    )

    @Test
    fun rimecliffSiege() = card(
        name = "Rimecliff Siege",
        cost = "{1}{U}{R}",
        type = "Enchantment",
        text = """
            As this enchantment enters, choose Jeskai or Temur.
            • Jeskai — Whenever one or more creatures you control deal combat damage to a player, draw a card.
            • Temur — Creatures you control get +1/+0 and have trample and haste.
        """,
    ) parsesTo abilities(
        Ability.Static(
            mechanics = listOf(
                Mechanic.Replacement(
                    event = EventPattern.ZoneChange(objects = isSelf, to = Zone.Battlefield),
                    modify = listOf(
                        Modification.AsEnters(
                            steps = listOf(
                                Step.Choose(
                                    choice = Choice.Option(options = listOf("Jeskai", "Temur")),
                                    bind = "option",
                                    chooser = Players.You,
                                    remember = true,
                                ),
                            ),
                        ),
                    ),
                ),
            ),
            affects = Affected.Objects(objects = Objects.Self),
        ),
        Ability.Triggered(
            trigger = Trigger.On(
                events = listOf(EventPattern.Damage(source = and(creature, you), to = RecipientFilter(players = PlayerFilter.Any), combat = true)),
                oneOrMore = true,
                batchPerPlayer = true,
            ),
            instructions = Instructions(steps = listOf(doing(Effect.Draw(players = Players.You, count = n(1))))),
            triggersOnlyIf = Condition.RememberedOption(key = "option", option = "Jeskai"),
        ),
        Ability.Static(
            mechanics = listOf(
                Mechanic.ModifyPowerToughness(power = n(1), toughness = n(0)),
                Mechanic.GainAbilities(abilities = listOf(Keyword.Trample, Keyword.Haste)),
            ),
            affects = Affected.Objects(objects = Objects.Each(filter = and(creature, you))),
            condition = Condition.RememberedOption(key = "option", option = "Temur"),
        ),
    )

    @Test
    fun icebarkSiege() = card(
        name = "Icebark Siege",
        cost = "{1}{G}{U}",
        type = "Enchantment",
        text = """
            As this enchantment enters, choose Temur or Sultai.
            • Temur — Whenever you cast an instant or sorcery spell, target player mills four cards.
            • Sultai — You may play lands from your graveyard.
        """,
    ) parsesTo abilities(
        Ability.Static(
            mechanics = listOf(
                Mechanic.Replacement(
                    event = EventPattern.ZoneChange(objects = isSelf, to = Zone.Battlefield),
                    modify = listOf(
                        Modification.AsEnters(
                            steps = listOf(
                                Step.Choose(
                                    choice = Choice.Option(options = listOf("Temur", "Sultai")),
                                    bind = "option",
                                    chooser = Players.You,
                                    remember = true,
                                ),
                            ),
                        ),
                    ),
                ),
            ),
            affects = Affected.Objects(objects = Objects.Self),
        ),
        Ability.Triggered(
            trigger = Trigger.On(events = listOf(EventPattern.Cast(spell = or(instant, sorcery), by = PlayerFilter.You))),
            instructions = Instructions(
                targets = listOf(TargetSlot(name = "player", what = TargetKind.Players(filter = PlayerFilter.Any), min = n(1), max = n(1))),
                steps = listOf(doing(Effect.Mill(players = Players.Target(slot = "player"), count = n(4)))),
            ),
            triggersOnlyIf = Condition.RememberedOption(key = "option", option = "Temur"),
        ),
        Ability.Static(
            mechanics = listOf(Mechanic.MayPlay(from = Zone.Graveyard, how = PlayKind.Play, holder = PlayerFilter.You)),
            affects = Affected.Objects(objects = Objects.Each(filter = and(land, inZone(Zone.Graveyard), ownedByYou))),
            condition = Condition.RememberedOption(key = "option", option = "Sultai"),
        ),
    )

    @Test
    fun fenhollowSiege() = card(
        name = "Fenhollow Siege",
        cost = "{B}{G}",
        type = "Enchantment",
        text = """
            As this enchantment enters, choose Sultai or Abzan.
            • Sultai — Whenever a counter is put on a creature you control, draw a card. This ability triggers only once each turn.
            • Abzan — Whenever you attack, put a +1/+1 counter on target attacking creature. It gains menace until end of turn.
        """,
    ) parsesTo abilities(
        Ability.Static(
            mechanics = listOf(
                Mechanic.Replacement(
                    event = EventPattern.ZoneChange(objects = isSelf, to = Zone.Battlefield),
                    modify = listOf(
                        Modification.AsEnters(
                            steps = listOf(
                                Step.Choose(
                                    choice = Choice.Option(options = listOf("Sultai", "Abzan")),
                                    bind = "option",
                                    chooser = Players.You,
                                    remember = true,
                                ),
                            ),
                        ),
                    ),
                ),
            ),
            affects = Affected.Objects(objects = Objects.Self),
        ),
        Ability.Triggered(
            trigger = Trigger.On(events = listOf(EventPattern.CountersPut(objects = and(creature, you)))),
            instructions = Instructions(steps = listOf(doing(Effect.Draw(players = Players.You, count = n(1))))),
            perTurn = 1,
            triggersOnlyIf = Condition.RememberedOption(key = "option", option = "Sultai"),
        ),
        Ability.Triggered(
            trigger = Trigger.On(events = listOf(EventPattern.Attack(attacker = you, withOthers = 0)), oneOrMore = true),
            instructions = Instructions(
                targets = listOf(TargetSlot(name = "creature", what = TargetKind.Objects(filter = and(attacking, creature)), min = n(1), max = n(1))),
                steps = listOf(
                    doing(Effect.PutCounters(objects = target("creature"), kind = p1p1, amount = n(1))),
                    doing(
                        Effect.ApplyMechanics(
                            mechanics = listOf(Mechanic.GainAbilities(abilities = listOf(Keyword.Menace))),
                            affects = Affected.Objects(objects = target("creature")),
                            duration = Duration.EndOfTurn,
                        ),
                    ),
                ),
            ),
            triggersOnlyIf = Condition.RememberedOption(key = "option", option = "Abzan"),
        ),
    )

    @Test
    fun wayfarerMonument() = card(
        name = "Wayfarer Monument",
        cost = "{2}",
        type = "Artifact",
        text = """
            When this artifact enters, search your library for a basic Island, Mountain, or Plains card, reveal it, put it into your hand, then shuffle.
            {1}{U}{R}{W}, {T}, Sacrifice this artifact: Create two 1/1 white Bird creature tokens with flying. Activate only as a sorcery.
        """,
    ) parsesTo abilities(
        Ability.Triggered(
            trigger = entersSelf,
            instructions = Instructions(
                steps = listOf(
                    doing(
                        Effect.Search(
                            library = Players.You,
                            filter = and(ObjectFilter.Super(supertype = Supertype.Basic), or(sub("Island"), sub("Mountain"), sub("Plains"))),
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
        ),
        Ability.Activated(
            cost = Cost(parts = listOf(mana("{1}{U}{R}{W}"), tap, CostPart.Sacrifice(what = CostObjects.Self))),
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
                            count = n(2),
                            controller = Players.You,
                        ),
                    ),
                ),
            ),
            rules = asSorcery,
        ),
    )

    @Test
    fun hordeMonument() = card(
        name = "Horde Monument",
        cost = "{2}",
        type = "Artifact",
        text = """
            When this artifact enters, search your library for a basic Mountain, Plains, or Swamp card, reveal it, put it into your hand, then shuffle.
            {2}{R}{W}{B}, {T}, Sacrifice this artifact: Create three 1/1 red Warrior creature tokens. They gain menace and haste until end of turn. Activate only as a sorcery. (A creature with menace can't be blocked except by two or more creatures.)
        """,
    ) parsesTo abilities(
        Ability.Triggered(
            trigger = entersSelf,
            instructions = Instructions(
                steps = listOf(
                    doing(
                        Effect.Search(
                            library = Players.You,
                            filter = and(ObjectFilter.Super(supertype = Supertype.Basic), or(sub("Mountain"), sub("Plains"), sub("Swamp"))),
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
        ),
        Ability.Activated(
            cost = Cost(parts = listOf(mana("{2}{R}{W}{B}"), tap, CostPart.Sacrifice(what = CostObjects.Self))),
            instructions = Instructions(
                steps = listOf(
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
                            count = n(3),
                            controller = Players.You,
                            bind = "tokens",
                        ),
                    ),
                    doing(
                        Effect.ApplyMechanics(
                            mechanics = listOf(Mechanic.GainAbilities(abilities = listOf(Keyword.Menace, Keyword.Haste))),
                            affects = Affected.Objects(objects = bound("tokens")),
                            duration = Duration.EndOfTurn,
                        ),
                    ),
                ),
            ),
            rules = asSorcery,
        ),
    )

    @Test
    fun hordeWallbreaker() = card(
        name = "Horde Wallbreaker",
        cost = "{1}{R}{W}{B}",
        type = "Creature — Human Warrior",
        pt = "4/4",
        text = """
            Deathtouch, haste
            When this creature enters, exile up to one other target creature you control until this creature leaves the battlefield.
            Whenever this creature attacks, for each opponent, create a tapped token that's a copy of the exiled card attacking that opponent. At the beginning of your next end step, sacrifice those tokens.
        """,
    ) parsesTo abilities(
        Keyword.Deathtouch,
        Keyword.Haste,
        Ability.Triggered(
            trigger = entersSelf,
            instructions = Instructions(
                targets = listOf(
                    TargetSlot(
                        name = "creature",
                        what = TargetKind.Objects(filter = and(not(isSelf), creature, you)),
                        min = n(0),
                        max = n(1),
                    ),
                ),
                steps = listOf(
                    doing(
                        Effect.Move(
                            objects = target("creature"),
                            to = Destination(zone = Zone.Exile),
                            untilLeaves = Objects.Self,
                            postBind = "exiled",
                        ),
                    ),
                    doing(Effect.Remember(key = "exiled", objects = bound("exiled"))),
                ),
            ),
        ),
        Ability.Triggered(
            trigger = attacksSelf,
            instructions = Instructions(
                steps = listOf(
                    Step.ForEach(
                        each = Each.Players(players = Players.Each(filter = PlayerFilter.Opponent)),
                        bind = "opponent",
                        steps = listOf(
                            doing(
                                Effect.CreateToken(
                                    token = TokenSpec.CopyOf(objects = Objects.Remembered(key = "exiled")),
                                    count = n(1),
                                    controller = Players.You,
                                    tapped = true,
                                    attacking = AttackTarget.These(recipients = Recipients(players = Players.Bound(name = "opponent"))),
                                    bind = "tokens",
                                ),
                            ),
                        ),
                        together = true,
                    ),
                    doing(
                        Effect.CreateDelayedTrigger(
                            trigger = Trigger.On(events = listOf(EventPattern.StepBegins(step = TurnStep.End, whose = PlayerFilter.You))),
                            instructions = Instructions(steps = listOf(doing(Effect.Sacrifice(objects = bound("tokens"))))),
                        ),
                    ),
                ),
            ),
        ),
    )

    @Test
    fun serpentCourtMonument() = card(
        name = "Serpent Court Monument",
        cost = "{2}",
        type = "Artifact",
        text = """
            When this artifact enters, search your library for a basic Swamp, Forest, or Island card, reveal it, put it into your hand, then shuffle.
            {2}{B}{G}{U}, {T}, Sacrifice this artifact: Create two 2/2 black Zombie Druid creature tokens. Activate only as a sorcery.
        """,
    ) parsesTo abilities(
        Ability.Triggered(
            trigger = entersSelf,
            instructions = Instructions(
                steps = listOf(
                    doing(
                        Effect.Search(
                            library = Players.You,
                            filter = and(ObjectFilter.Super(supertype = Supertype.Basic), or(sub("Swamp"), sub("Forest"), sub("Island"))),
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
        ),
        Ability.Activated(
            cost = Cost(parts = listOf(mana("{2}{B}{G}{U}"), tap, CostPart.Sacrifice(what = CostObjects.Self))),
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
                            count = n(2),
                            controller = Players.You,
                        ),
                    ),
                ),
            ),
            rules = asSorcery,
        ),
    )

    @Test
    fun frontierMonument() = card(
        name = "Frontier Monument",
        cost = "{2}",
        type = "Artifact",
        text = """
            When this artifact enters, search your library for a basic Forest, Island, or Mountain card, reveal it, put it into your hand, then shuffle.
            {3}{G}{U}{R}, {T}, Sacrifice this artifact: Create a 5/5 green Elephant creature token. Activate only as a sorcery.
        """,
    ) parsesTo abilities(
        Ability.Triggered(
            trigger = entersSelf,
            instructions = Instructions(
                steps = listOf(
                    doing(
                        Effect.Search(
                            library = Players.You,
                            filter = and(ObjectFilter.Super(supertype = Supertype.Basic), or(sub("Forest"), sub("Island"), sub("Mountain"))),
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
        ),
        Ability.Activated(
            cost = Cost(parts = listOf(mana("{3}{G}{U}{R}"), tap, CostPart.Sacrifice(what = CostObjects.Self))),
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
            rules = asSorcery,
        ),
    )

    @Test
    fun galecragSiege() = card(
        name = "Galecrag Siege",
        cost = "{1}{R}{W}",
        type = "Enchantment",
        text = """
            As this enchantment enters, choose Mardu or Jeskai.
            • Mardu — If a creature attacking causes a triggered ability of a permanent you control to trigger, that ability triggers an additional time.
            • Jeskai — At the beginning of your upkeep, create a 1/1 red Goblin creature token. It gains lifelink and haste until end of turn.
        """,
    ) parsesTo abilities(
        Ability.Static(
            mechanics = listOf(
                Mechanic.Replacement(
                    event = EventPattern.ZoneChange(objects = isSelf, to = Zone.Battlefield),
                    modify = listOf(
                        Modification.AsEnters(
                            steps = listOf(
                                Step.Choose(
                                    choice = Choice.Option(options = listOf("Mardu", "Jeskai")),
                                    bind = "option",
                                    chooser = Players.You,
                                    remember = true,
                                ),
                            ),
                        ),
                    ),
                ),
            ),
            affects = Affected.Objects(objects = Objects.Self),
        ),
        Ability.Static(
            mechanics = listOf(
                Mechanic.TriggersAdditionalTime(
                    event = EventPattern.Attack(attacker = creature, withOthers = 0),
                    sources = and(permanentFilter, you),
                ),
            ),
            affects = Affected.Objects(objects = Objects.Self),
            condition = Condition.RememberedOption(key = "option", option = "Mardu"),
        ),
        Ability.Triggered(
            trigger = Trigger.On(events = listOf(EventPattern.StepBegins(step = TurnStep.Upkeep, whose = PlayerFilter.You))),
            instructions = Instructions(
                steps = listOf(
                    doing(
                        Effect.CreateToken(
                            token = TokenSpec.Defined(
                                face = FaceDefinition(
                                    name = "Goblin Token",
                                    colorIndicator = listOf(Color.Red),
                                    types = listOf(CardType.Creature),
                                    subtypes = listOf(Subtype(name = "Goblin")),
                                    power = PrintedNumber.Fixed(value = 1),
                                    toughness = PrintedNumber.Fixed(value = 1),
                                ),
                            ),
                            count = n(1),
                            controller = Players.You,
                            bind = "token",
                        ),
                    ),
                    doing(
                        Effect.ApplyMechanics(
                            mechanics = listOf(Mechanic.GainAbilities(abilities = listOf(Keyword.Lifelink, Keyword.Haste))),
                            affects = Affected.Objects(objects = bound("token")),
                            duration = Duration.EndOfTurn,
                        ),
                    ),
                ),
            ),
            triggersOnlyIf = Condition.RememberedOption(key = "option", option = "Jeskai"),
        ),
    )
}
