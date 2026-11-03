package arcanum.parser.sets.tdm

import arcanum.api.*
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.cardFaces
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

/**
 * Tarkir: Dragonstorm chunk 13: green cards: a named token of all colors, power-based blocking restrictions, a counter then damage equal to power, a creature or creature spell becoming a target, a land kept from the top or put into the graveyard.
 */
class TdmChunk13GreenTest {
    @Test
    fun keenSensedHunter() = card(
        name = "Keen-Sensed Hunter",
        cost = "{2}{G}",
        type = "Creature — Human Ranger",
        pt = "3/3",
        text = """
            Trample
            Whenever one or more cards leave your graveyard during your turn, put a +1/+1 counter on this creature.
        """,
    ) parsesTo abilities(
        Keyword.Trample,
        Ability.Triggered(
            trigger = Trigger.On(
                events = listOf(EventPattern.ZoneChange(objects = and(cardFilter, ownedByYou), from = Zone.Graveyard)),
                oneOrMore = true,
            ),
            instructions = Instructions(steps = listOf(doing(Effect.PutCounters(objects = Objects.Self, kind = p1p1, amount = n(1))))),
            triggersOnlyIf = Condition.YourTurn,
        ),
    )

    @Test
    fun wyrmbroodsHeirloom() = card(
        name = "Wyrmbroods' Heirloom",
        cost = "{1}{G}",
        type = "Artifact",
        text = """
            {T}, Tap an untapped creature you control: Add one mana of any color.
            {3}{W}{U}{B}{R}{G}, Sacrifice this artifact: Create a 4/4 Dragon creature token named Reliquary Dragon that's all colors. It has flying, lifelink, and "When this token enters, it deals 3 damage to any target." Activate only as a sorcery.
        """,
    ) parsesTo abilities(
        Ability.Activated(
            cost = Cost(parts = listOf(tap, CostPart.TapObjects(what = CostObjects.Choose(filter = creature, count = n(1))))),
            instructions = Instructions(
                steps = listOf(doing(Effect.AddMana(mana = ManaProduction(kind = ManaKind.Choose(amount = n(1))), players = Players.You))),
            ),
            rules = anyTime,
        ),
        Ability.Activated(
            cost = Cost(parts = listOf(mana("{3}{W}{U}{B}{R}{G}"), CostPart.Sacrifice(what = CostObjects.Self))),
            instructions = Instructions(
                steps = listOf(
                    doing(
                        Effect.CreateToken(
                            token = TokenSpec.Defined(
                                face = FaceDefinition(
                                    name = "Reliquary Dragon",
                                    colorIndicator = listOf(Color.White, Color.Blue, Color.Black, Color.Red, Color.Green),
                                    types = listOf(CardType.Creature),
                                    subtypes = listOf(Subtype(name = "Dragon")),
                                    power = PrintedNumber.Fixed(value = 4),
                                    toughness = PrintedNumber.Fixed(value = 4),
                                    abilities = listOf(
                                        Keyword.Flying,
                                        Keyword.Lifelink,
                                        Ability.Triggered(
                                            trigger = entersSelf,
                                            instructions = Instructions(
                                                targets = listOf(
                                                    TargetSlot(
                                                        name = "any",
                                                        what = TargetKind.Either(
                                                            objects = or(
                                                                creature,
                                                                planeswalker,
                                                                ObjectFilter.Type(
                                                                    cardType = CardTypeRef.Literal(cardType = CardType.Battle),
                                                                ),
                                                            ),
                                                            players = PlayerFilter.Any,
                                                        ),
                                                        min = n(1),
                                                        max = n(1),
                                                    ),
                                                ),
                                                steps = listOf(
                                                    doing(
                                                        Effect.DealDamage(
                                                            to = Recipients(target = "any"),
                                                            amount = n(3),
                                                            source = Objects.Self,
                                                        ),
                                                    ),
                                                ),
                                            ),
                                        ),
                                    ),
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
    fun phalanxBreaker() = card(
        name = "Phalanx Breaker",
        cost = "{1}{G}",
        type = "Creature — Beast",
        pt = "2/1",
        text = """
            Creatures with power less than this creature's power can't block it.
            As long as you control a creature with a counter on it, this creature gets +1/+2.
        """,
    ) parsesTo abilities(
        Ability.Static(
            mechanics = listOf(
                Mechanic.CantBeBlockedBy(
                    blockers = and(
                        creature,
                        ObjectFilter.Power(
                            comparison = Comparison.Less,
                            value = Value.Of(property = NumericProperty.Power, objects = Objects.Self, how = Combine.Single),
                        ),
                    ),
                ),
            ),
            affects = Affected.Objects(objects = Objects.Self),
        ),
        Ability.Static(
            mechanics = listOf(Mechanic.ModifyPowerToughness(power = n(1), toughness = n(2))),
            affects = Affected.Objects(objects = Objects.Self),
            condition = Condition.Exists(filter = and(creature, ObjectFilter.HasCounter(kind = null, atLeast = 1), you), atLeast = 1),
        ),
    )

    @Test
    fun herdKeepsake() = card(
        name = "Herd Keepsake",
        cost = "{1}{G}",
        type = "Artifact",
        text = """
            {T}: Add one mana of any color. Spend this mana only to cast a creature spell.
            {T}: Until end of turn, target creature you control with power 4 or greater gains trample and "Whenever this creature deals combat damage to a player, draw a card."
        """,
    ) parsesTo abilities(
        Ability.Activated(
            cost = Cost(parts = listOf(tap)),
            instructions = Instructions(
                steps = listOf(
                    doing(
                        Effect.AddMana(
                            mana = ManaProduction(kind = ManaKind.Choose(amount = n(1)), restriction = and(spellFilter, creature)),
                            players = Players.You,
                        ),
                    ),
                ),
            ),
            rules = anyTime,
        ),
        Ability.Activated(
            cost = Cost(parts = listOf(tap)),
            instructions = Instructions(
                targets = listOf(
                    TargetSlot(
                        name = "creature",
                        what = TargetKind.Objects(filter = and(creature, you, ObjectFilter.Power(comparison = Comparison.AtLeast, value = n(4)))),
                        min = n(1),
                        max = n(1),
                    ),
                ),
                steps = listOf(
                    doing(
                        Effect.ApplyMechanics(
                            mechanics = listOf(
                                Mechanic.GainAbilities(
                                    abilities = listOf(
                                        Keyword.Trample,
                                        Ability.Triggered(
                                            trigger = Trigger.On(
                                                events = listOf(
                                                    EventPattern.Damage(
                                                        source = isSelf,
                                                        to = RecipientFilter(players = PlayerFilter.Any),
                                                        combat = true,
                                                    ),
                                                ),
                                            ),
                                            instructions = Instructions(steps = listOf(doing(Effect.Draw(players = Players.You, count = n(1))))),
                                        ),
                                    ),
                                ),
                            ),
                            affects = Affected.Objects(objects = target("creature")),
                            duration = Duration.EndOfTurn,
                        ),
                    ),
                ),
            ),
            rules = anyTime,
        ),
    )

    @Test
    fun legacyReclamation() = card(
        name = "Legacy Reclamation",
        cost = "{1}{G}",
        type = "Instant",
        text = """
            Choose one —
            • Destroy target artifact.
            • Destroy target enchantment.
            • Exile up to one target card from a graveyard. Draw a card.
        """,
    ) parsesTo abilities(
        Ability.Spell(
            instructions = Instructions(
                modes = Modes(
                    options = listOf(
                        Mode(
                            instructions = Instructions(
                                targets = listOf(TargetSlot(name = "artifact", what = TargetKind.Objects(filter = artifact), min = n(1), max = n(1))),
                                steps = listOf(doing(Effect.Destroy(objects = target("artifact")))),
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
                        Mode(
                            instructions = Instructions(
                                targets = listOf(
                                    TargetSlot(
                                        name = "card",
                                        what = TargetKind.Objects(filter = and(cardFilter, inZone(Zone.Graveyard))),
                                        min = n(0),
                                        max = n(1),
                                    ),
                                ),
                                steps = listOf(
                                    doing(Effect.Move(objects = target("card"), to = Destination(zone = Zone.Exile), postBind = "exiled")),
                                    doing(Effect.Draw(players = Players.You, count = n(1))),
                                ),
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
    fun haymakerManeuver() = card(
        name = "Haymaker Maneuver",
        cost = "{2}{G}",
        type = "Sorcery",
        text = "Put a +1/+1 counter on target creature you control, then it deals damage equal to its power to target creature an opponent controls.",
    ) parsesTo abilities(
        Ability.Spell(
            instructions = Instructions(
                targets = listOf(
                    TargetSlot(name = "creature", what = TargetKind.Objects(filter = and(creature, you)), min = n(1), max = n(1)),
                    TargetSlot(
                        name = "second creature",
                        what = TargetKind.Objects(filter = and(creature, opponents)),
                        min = n(1),
                        max = n(1),
                    ),
                ),
                steps = listOf(
                    doing(Effect.PutCounters(objects = target("creature"), kind = p1p1, amount = n(1))),
                    doing(
                        Effect.DealDamage(
                            to = Recipients(objects = target("second creature")),
                            amount = Value.Of(property = NumericProperty.Power, objects = target("creature"), how = Combine.Single),
                            source = target("creature"),
                        ),
                    ),
                ),
            ),
        ),
    )

    @Test
    fun chitinNestguard() = card(
        name = "Chitin Nestguard",
        cost = "{2}{G}",
        type = "Creature — Insect",
        pt = "4/4",
        text = """
            Defender
            {2}{G}: This creature can attack this turn as though it didn't have defender.
        """,
    ) parsesTo abilities(
        Keyword.Defender,
        Ability.Activated(
            cost = Cost(parts = listOf(mana("{2}{G}"))),
            instructions = Instructions(
                steps = listOf(
                    doing(
                        Effect.ApplyMechanics(
                            mechanics = listOf(Mechanic.CanAttackDespiteDefender),
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
    fun mistveilRejuvenator() = card(
        name = "Mistveil Rejuvenator",
        cost = "{3}{G}",
        type = "Creature — Elephant Druid",
        pt = "2/4",
        text = """
            When this creature enters, you may mill three cards. (You may put the top three cards of your library into your graveyard.)
            {T}: Add an amount of {G} equal to this creature's power.
        """,
    ) parsesTo abilities(
        Ability.Triggered(
            trigger = entersSelf,
            instructions = Instructions(
                steps = listOf(Step.May(steps = listOf(doing(Effect.Mill(players = Players.You, count = n(3)))), player = Players.You)),
            ),
        ),
        Ability.Activated(
            cost = Cost(parts = listOf(tap)),
            instructions = Instructions(
                steps = listOf(
                    doing(
                        Effect.AddMana(
                            mana = ManaProduction(
                                kind = ManaKind.Fixed(
                                    mana = ManaCost.parse("{G}")!!,
                                    times = Value.Of(property = NumericProperty.Power, objects = Objects.Self, how = Combine.Single),
                                ),
                            ),
                            players = Players.You,
                        ),
                    ),
                ),
            ),
            rules = anyTime,
        ),
    )

    @Test
    fun riteOfReturn() = card(
        name = "Rite of Return",
        cost = "{3}{G}",
        type = "Sorcery",
        text = "Return up to two target permanent cards from your graveyard to your hand. Target player shuffles up to four target cards from their graveyard into their library. Exile Rite of Return.",
    ) parsesTo abilities(
        Ability.Spell(
            instructions = Instructions(
                targets = listOf(
                    TargetSlot(
                        name = "cards",
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
                        min = n(0),
                        max = n(2),
                    ),
                    TargetSlot(name = "player", what = TargetKind.Players(filter = PlayerFilter.Any), min = n(1), max = n(1)),
                    TargetSlot(
                        name = "card",
                        what = TargetKind.Objects(
                            filter = and(
                                cardFilter,
                                inZone(Zone.Graveyard),
                                ObjectFilter.OwnedBy(players = PlayerFilter.Is(players = Players.Target(slot = "player"))),
                            ),
                        ),
                        min = n(0),
                        max = n(4),
                    ),
                ),
                steps = listOf(
                    doing(Effect.Move(objects = target("cards"), to = Destination(zone = Zone.Hand))),
                    doing(Effect.Move(objects = target("card"), to = Destination(zone = Zone.Library))),
                    doing(Effect.Shuffle(library = Players.Target(slot = "player"))),
                    doing(Effect.Move(objects = Objects.Self, to = Destination(zone = Zone.Exile))),
                ),
            ),
        ),
    )

    @Test
    fun rhazirsResolve() = card(
        name = "Rhazir's Resolve",
        cost = "{1}{G}",
        type = "Instant",
        text = """
            Choose one —
            • Target creature gets +3/+3 until end of turn.
            • Destroy target creature with flying.
        """,
    ) parsesTo abilities(
        Ability.Spell(
            instructions = Instructions(
                modes = Modes(
                    options = listOf(
                        Mode(
                            instructions = Instructions(
                                targets = listOf(TargetSlot(name = "creature", what = TargetKind.Objects(filter = creature), min = n(1), max = n(1))),
                                steps = listOf(
                                    doing(
                                        Effect.ApplyMechanics(
                                            mechanics = listOf(Mechanic.ModifyPowerToughness(power = n(3), toughness = n(3))),
                                            affects = Affected.Objects(objects = target("creature")),
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
                                        what = TargetKind.Objects(filter = and(creature, ObjectFilter.HasKeyword(keyword = Keyword.Flying))),
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
    fun scaleskinVeil() = card(
        name = "Scaleskin Veil",
        cost = "{G}",
        type = "Instant",
        text = "Put a +1/+1 counter on target creature you control. It gains hexproof until end of turn. (It can't be the target of spells or abilities your opponents control.)",
    ) parsesTo abilities(
        Ability.Spell(
            instructions = Instructions(
                targets = listOf(TargetSlot(name = "creature", what = TargetKind.Objects(filter = and(creature, you)), min = n(1), max = n(1))),
                steps = listOf(
                    doing(Effect.PutCounters(objects = target("creature"), kind = p1p1, amount = n(1))),
                    doing(
                        Effect.ApplyMechanics(
                            mechanics = listOf(Mechanic.GainAbilities(abilities = listOf(Keyword.Hexproof))),
                            affects = Affected.Objects(objects = target("creature")),
                            duration = Duration.EndOfTurn,
                        ),
                    ),
                ),
            ),
        ),
    )

    @Test
    fun serpentCourtDevotee() = card(
        name = "Serpent Court Devotee",
        cost = "{1}{G}",
        type = "Creature — Zombie Snake Druid",
        pt = "2/1",
        text = """
            Deathtouch
            {1}: Add {B}, {G}, or {U}. Activate only once each turn.
        """,
    ) parsesTo abilities(
        Keyword.Deathtouch,
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
                                        ColorRef.Literal(color = Color.Black),
                                        ColorRef.Literal(color = Color.Green),
                                        ColorRef.Literal(color = Color.Blue),
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
    fun torvaldElusiveHunter() = card(
        name = "Torvald, Elusive Hunter",
        cost = "{2}{G}",
        type = "Legendary Creature — Human Warrior",
        pt = "4/3",
        text = """
            This spell can't be countered.
            Trample
            Whenever a creature you control or a creature spell you control becomes the target of a spell or ability an opponent controls, draw a card.
        """,
    ) parsesTo abilities(
        Ability.Static(mechanics = listOf(Mechanic.CantBeCountered), affects = Affected.Objects(objects = Objects.Self)),
        Keyword.Trample,
        Ability.Triggered(
            trigger = Trigger.On(
                events = listOf(EventPattern.BecomesTarget(objects = or(and(creature, you), and(creature, spellFilter, you)), by = opponents)),
            ),
            instructions = Instructions(steps = listOf(doing(Effect.Draw(players = Players.You, count = n(1))))),
        ),
    )

    @Test
    fun tradeRoadEnvoy() = card(
        name = "Trade Road Envoy",
        cost = "{3}{G}",
        type = "Creature — Dog Soldier",
        pt = "4/3",
        text = "When this creature enters, draw a card if you control a creature with a counter on it. If you don't draw a card this way, put a +1/+1 counter on this creature.",
    ) parsesTo abilities(
        Ability.Triggered(
            trigger = entersSelf,
            instructions = Instructions(
                steps = listOf(
                    Step.If(
                        condition = Condition.Exists(filter = and(creature, ObjectFilter.HasCounter(kind = null, atLeast = 1), you), atLeast = 1),
                        then = listOf(doing(Effect.Draw(players = Players.You, count = n(1)))),
                        otherwise = listOf(doing(Effect.PutCounters(objects = Objects.Self, kind = p1p1, amount = n(1)))),
                    ),
                ),
            ),
        ),
    )

    @Test
    fun wanderingBotanist() = card(
        name = "Wandering Botanist",
        cost = "{1}{G}",
        type = "Creature — Dog Scout",
        pt = "2/3",
        text = "Whenever this creature becomes tapped, look at the top card of your library. If it's a land card, you may reveal it and put it into your hand. If you don't put the card into your hand, you may put it into your graveyard.",
    ) parsesTo abilities(
        Ability.Triggered(
            trigger = Trigger.On(events = listOf(EventPattern.Tap(objects = isSelf))),
            instructions = Instructions(
                steps = listOf(
                    doing(
                        Effect.LookAt(
                            objects = Objects.LibraryCards(of = Players.You, count = n(1), fromTop = true),
                            players = Players.You,
                            bind = "top",
                        ),
                    ),
                    Step.If(
                        condition = Condition.ObjectMatches(objects = bound("top"), filter = land),
                        then = listOf(
                            Step.Pay(
                                steps = listOf(
                                    doing(Effect.Reveal(objects = bound("top"))),
                                    doing(Effect.Move(objects = bound("top"), to = Destination(zone = Zone.Hand))),
                                ),
                                payer = Players.You,
                                bind = "kept",
                            ),
                        ),
                    ),
                    Step.If(
                        condition = Condition.Not(condition = Condition.Did(name = "kept")),
                        then = listOf(
                            Step.May(
                                steps = listOf(doing(Effect.Move(objects = bound("top"), to = Destination(zone = Zone.Graveyard)))),
                                player = Players.You,
                            ),
                        ),
                    ),
                ),
            ),
        ),
    )

    @Test
    fun underbrushLeopard() = card(
        name = "Underbrush Leopard",
        cost = "{1}{G}",
        type = "Creature — Cat",
        pt = "2/2",
        text = """
            Vigilance
            {1}, Sacrifice this creature: Destroy target artifact or enchantment.
        """,
    ) parsesTo abilities(
        Keyword.Vigilance,
        Ability.Activated(
            cost = Cost(parts = listOf(mana("{1}"), CostPart.Sacrifice(what = CostObjects.Self))),
            instructions = Instructions(
                targets = listOf(
                    TargetSlot(name = "permanent", what = TargetKind.Objects(filter = or(artifact, enchantment)), min = n(1), max = n(1)),
                ),
                steps = listOf(doing(Effect.Destroy(objects = target("permanent")))),
            ),
            rules = anyTime,
        ),
    )
}
