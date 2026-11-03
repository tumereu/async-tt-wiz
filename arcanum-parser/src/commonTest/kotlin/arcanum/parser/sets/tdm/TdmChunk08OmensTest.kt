package arcanum.parser.sets.tdm

import arcanum.api.*
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.cardFaces
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

/**
 * Tarkir: Dragonstorm chunk 08: Omen cards (CR 720): each Dragon with its Omen spell, shuffled into its owner's library as it resolves.
 */
class TdmChunk08OmensTest {
    @Test
    fun thornbloomRegent() = cardFaces(
        Layout.Omen,
        card(
            name = "Thornbloom Regent",
            cost = "{3}{G}{G}",
            type = "Creature — Dragon",
            pt = "4/5",
            text = """
                Flying
                Whenever this creature or another Dragon you control enters, you gain 3 life.
            """,
        ) to abilities(
            Keyword.Flying,
            Ability.Triggered(
                trigger = Trigger.On(
                    events = listOf(EventPattern.ZoneChange(objects = or(isSelf, and(not(isSelf), sub("Dragon"), you)), to = Zone.Battlefield)),
                ),
                instructions = Instructions(steps = listOf(doing(Effect.GainLife(players = Players.You, amount = n(3))))),
            ),
        ),
        card(
            name = "Stake the Wilds",
            cost = "{2}{G}",
            type = "Sorcery — Omen",
            text = "Search your library for up to two basic Forest cards, reveal them, put one onto the battlefield tapped and the other into your hand, then shuffle. (Also shuffle this card.)",
        ) to abilities(
            Ability.Spell(
                instructions = Instructions(
                    steps = listOf(
                        doing(
                            Effect.Search(
                                library = Players.You,
                                filter = and(ObjectFilter.Super(supertype = Supertype.Basic), sub("Forest")),
                                max = n(2),
                                reveal = true,
                                zones = listOf(Zone.Library),
                                bind = "found",
                                searcher = Players.You,
                            ),
                        ),
                        Step.Choose(
                            choice = Choice.Objects(filter = ObjectFilter.Any, min = n(1), max = n(1), from = bound("found")),
                            bind = "chosen",
                            chooser = Players.You,
                        ),
                        doing(Effect.Move(objects = bound("chosen"), to = Destination(zone = Zone.Battlefield, tapped = true))),
                        doing(
                            Effect.Move(
                                objects = Objects.Except(base = bound("found"), minus = bound("chosen")),
                                to = Destination(zone = Zone.Hand),
                            ),
                        ),
                        doing(Effect.Shuffle(library = Players.You)),
                    ),
                ),
            ),
        ),
    )

    @Test
    fun shoalIsleDragon() = cardFaces(
        Layout.Omen,
        card(
            name = "Shoal Isle Dragon",
            cost = "{5}{U}",
            type = "Creature — Dragon",
            pt = "4/4",
            text = """
                Flying
                Ward {2} (Whenever this creature becomes the target of a spell or ability an opponent controls, counter it unless that player pays {2}.)
            """,
        ) to abilities(
            Keyword.Flying,
            Keyword.Ward(cost = Cost(parts = listOf(mana("{2}")))),
        ),
        card(
            name = "Glancing Strike",
            cost = "{1}{U}",
            type = "Instant — Omen",
            text = "Tap up to one target creature. Draw a card. (Then shuffle this card into its owner's library.)",
        ) to abilities(
            Ability.Spell(
                instructions = Instructions(
                    targets = listOf(TargetSlot(name = "creature", what = TargetKind.Objects(filter = creature), min = n(0), max = n(1))),
                    steps = listOf(doing(Effect.Tap(objects = target("creature"))), doing(Effect.Draw(players = Players.You, count = n(1)))),
                ),
            ),
        ),
    )

    @Test
    fun meddlingStormbrood() = cardFaces(
        Layout.Omen,
        card(
            name = "Meddling Stormbrood",
            cost = "{4}{G}",
            type = "Creature — Dragon",
            pt = "3/3",
            text = """
                Flying
                When this creature enters, destroy up to one target artifact or enchantment.
            """,
        ) to abilities(
            Keyword.Flying,
            Ability.Triggered(
                trigger = entersSelf,
                instructions = Instructions(
                    targets = listOf(
                        TargetSlot(
                            name = "permanent",
                            what = TargetKind.Objects(filter = or(artifact, enchantment)),
                            min = n(0),
                            max = n(1),
                        ),
                    ),
                    steps = listOf(doing(Effect.Destroy(objects = target("permanent")))),
                ),
            ),
        ),
        card(
            name = "Spiteful Payback",
            cost = "{1}{B}",
            type = "Sorcery — Omen",
            text = "Destroy target creature with power 3 or less. (Then shuffle this card into its owner's library.)",
        ) to abilities(
            Ability.Spell(
                instructions = Instructions(
                    targets = listOf(
                        TargetSlot(
                            name = "creature",
                            what = TargetKind.Objects(filter = and(creature, ObjectFilter.Power(comparison = Comparison.AtMost, value = n(3)))),
                            min = n(1),
                            max = n(1),
                        ),
                    ),
                    steps = listOf(doing(Effect.Destroy(objects = target("creature")))),
                ),
            ),
        ),
    )

    @Test
    fun savageGravegorger() = cardFaces(
        Layout.Omen,
        card(
            name = "Savage Gravegorger",
            cost = "{5}{B}",
            type = "Creature — Dragon",
            pt = "3/5",
            text = """
                Flying, deathtouch
                When this creature enters, exile up to two target cards from a single graveyard.
            """,
        ) to abilities(
            Keyword.Flying,
            Keyword.Deathtouch,
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
        ),
        card(
            name = "Gloam Sight",
            cost = "{1}{B}",
            type = "Sorcery — Omen",
            text = "Put a +1/+1 counter on up to one target creature. Draw a card. (Then shuffle this card into its owner's library.)",
        ) to abilities(
            Ability.Spell(
                instructions = Instructions(
                    targets = listOf(TargetSlot(name = "creature", what = TargetKind.Objects(filter = creature), min = n(0), max = n(1))),
                    steps = listOf(
                        doing(Effect.PutCounters(objects = target("creature"), kind = p1p1, amount = n(1))),
                        doing(Effect.Draw(players = Players.You, count = n(1))),
                    ),
                ),
            ),
        ),
    )

    @Test
    fun deltaRiverRegent() = cardFaces(
        Layout.Omen,
        card(
            name = "Delta River Regent",
            cost = "{4}{U}{U}",
            type = "Creature — Dragon",
            pt = "6/7",
            text = """
                Flying
                When this creature enters, return up to two other target nonland permanents to their owners' hands.
            """,
        ) to abilities(
            Keyword.Flying,
            Ability.Triggered(
                trigger = entersSelf,
                instructions = Instructions(
                    targets = listOf(
                        TargetSlot(
                            name = "permanents",
                            what = TargetKind.Objects(filter = and(not(isSelf), not(land), permanentFilter)),
                            min = n(0),
                            max = n(2),
                        ),
                    ),
                    steps = listOf(doing(Effect.Move(objects = target("permanents"), to = Destination(zone = Zone.Hand)))),
                ),
            ),
        ),
        card(
            name = "Wrap and Snare",
            cost = "{3}{U}",
            type = "Instant — Omen",
            text = "Draw three cards, then discard a card. (Then shuffle this card into its owner's library.)",
        ) to abilities(
            Ability.Spell(
                instructions = Instructions(
                    steps = listOf(
                        doing(Effect.Draw(players = Players.You, count = n(3))),
                        doing(Effect.Discard(players = Players.You, count = n(1), filter = ObjectFilter.Any)),
                    ),
                ),
            ),
        ),
    )

    @Test
    fun cleansingStormbrood() = cardFaces(
        Layout.Omen,
        card(
            name = "Cleansing Stormbrood",
            cost = "{4}{B}",
            type = "Creature — Dragon",
            pt = "4/4",
            text = """
                Flying
                Ward—Pay 2 life.
                When this creature enters, remove all counters from up to one target creature.
            """,
        ) to abilities(
            Keyword.Flying,
            Keyword.Ward(cost = Cost(parts = listOf(CostPart.PayLife(amount = n(2))))),
            Ability.Triggered(
                trigger = entersSelf,
                instructions = Instructions(
                    targets = listOf(TargetSlot(name = "creature", what = TargetKind.Objects(filter = creature), min = n(0), max = n(1))),
                    steps = listOf(
                        doing(
                            Effect.RemoveCounters(
                                objects = target("creature"),
                                kind = null,
                                amount = Value.Of(
                                    property = NumericProperty.Counters(kind = null),
                                    objects = target("creature"),
                                    how = Combine.Single,
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        ),
        card(
            name = "Drink Vigor",
            cost = "{1}{W}",
            type = "Instant — Omen",
            text = "Target creature gets +2/+2 and gains lifelink and hexproof until end of turn. (Then shuffle this card into its owner's library.)",
        ) to abilities(
            Ability.Spell(
                instructions = Instructions(
                    targets = listOf(TargetSlot(name = "creature", what = TargetKind.Objects(filter = creature), min = n(1), max = n(1))),
                    steps = listOf(
                        doing(
                            Effect.ApplyMechanics(
                                mechanics = listOf(
                                    Mechanic.ModifyPowerToughness(power = n(2), toughness = n(2)),
                                    Mechanic.GainAbilities(abilities = listOf(Keyword.Lifelink, Keyword.Hexproof)),
                                ),
                                affects = Affected.Objects(objects = target("creature")),
                                duration = Duration.EndOfTurn,
                            ),
                        ),
                    ),
                ),
            ),
        ),
    )

    @Test
    fun dawnrilerWyrm() = cardFaces(
        Layout.Omen,
        card(
            name = "Dawnriler Wyrm",
            cost = "{4}{W}",
            type = "Creature — Dragon",
            pt = "3/4",
            text = """
                Flying, vigilance
                At the beginning of combat on your turn, another target creature you control gets +1/+0 until end of turn.
            """,
        ) to abilities(
            Keyword.Flying,
            Keyword.Vigilance,
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
                    steps = listOf(
                        doing(
                            Effect.ApplyMechanics(
                                mechanics = listOf(Mechanic.ModifyPowerToughness(power = n(1), toughness = n(0))),
                                affects = Affected.Objects(objects = target("creature")),
                                duration = Duration.EndOfTurn,
                            ),
                        ),
                    ),
                ),
            ),
        ),
        card(
            name = "Muster Roar",
            cost = "{1}{W}",
            type = "Sorcery — Omen",
            text = "Create a 2/2 white Soldier creature token. (Then shuffle this card into its owner's library.)",
        ) to abilities(
            Ability.Spell(
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
            ),
        ),
    )

    @Test
    fun glyphscaleStormbrood() = cardFaces(
        Layout.Omen,
        card(
            name = "Glyphscale Stormbrood",
            cost = "{3}{R}",
            type = "Creature — Dragon",
            pt = "2/4",
            text = """
                Flying
                Whenever you cast a noncreature spell or a Dragon spell, this creature gets +2/+0 until end of turn.
            """,
        ) to abilities(
            Keyword.Flying,
            Ability.Triggered(
                trigger = Trigger.On(
                    events = listOf(EventPattern.Cast(spell = or(and(not(creature), spellFilter), sub("Dragon")), by = PlayerFilter.You)),
                ),
                instructions = Instructions(
                    steps = listOf(
                        doing(
                            Effect.ApplyMechanics(
                                mechanics = listOf(Mechanic.ModifyPowerToughness(power = n(2), toughness = n(0))),
                                affects = Affected.Objects(objects = Objects.Self),
                                duration = Duration.EndOfTurn,
                            ),
                        ),
                    ),
                ),
            ),
        ),
        card(
            name = "Freezing Shriek",
            cost = "{1}{U}",
            type = "Instant — Omen",
            text = "Counter target spell with mana value 2 or less. (Then shuffle this card into its owner's library.)",
        ) to abilities(
            Ability.Spell(
                instructions = Instructions(
                    targets = listOf(
                        TargetSlot(
                            name = "spell",
                            what = TargetKind.Objects(
                                filter = and(spellFilter, ObjectFilter.ManaValue(comparison = Comparison.AtMost, value = n(2))),
                            ),
                            min = n(1),
                            max = n(1),
                        ),
                    ),
                    steps = listOf(doing(Effect.Counter(objects = target("spell")))),
                ),
            ),
        ),
    )

    @Test
    fun jungleWildling() = cardFaces(
        Layout.Omen,
        card(
            name = "Jungle Wildling",
            cost = "{4}{G}",
            type = "Creature — Dragon",
            pt = "3/3",
            text = """
                Flying
                When this creature enters, you gain 3 life.
            """,
        ) to abilities(
            Keyword.Flying,
            Ability.Triggered(
                trigger = entersSelf,
                instructions = Instructions(steps = listOf(doing(Effect.GainLife(players = Players.You, amount = n(3))))),
            ),
        ),
        card(
            name = "Nest Seek",
            cost = "{G}",
            type = "Sorcery — Omen",
            text = "Search your library for a basic land card, reveal it, put it into your hand, then shuffle. (Also shuffle this card.)",
        ) to abilities(
            Ability.Spell(
                instructions = Instructions(
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
                        doing(Effect.Move(objects = bound("found"), to = Destination(zone = Zone.Hand))),
                        doing(Effect.Shuffle(library = Players.You)),
                    ),
                ),
            ),
        ),
    )

    @Test
    fun carrionRegent() = cardFaces(
        Layout.Omen,
        card(
            name = "Carrion Regent",
            cost = "{3}{B}",
            type = "Creature — Dragon",
            pt = "4/4",
            text = """
                Flying
                Ward—Discard a card.
            """,
        ) to abilities(
            Keyword.Flying,
            Keyword.Ward(cost = Cost(parts = listOf(CostPart.Discard(what = CostObjects.Choose(filter = cardFilter, count = n(1)))))),
        ),
        card(
            name = "Seep Venom",
            cost = "{X}{B}{B}",
            type = "Sorcery — Omen",
            text = "Each non-Dragon creature gets -X/-X until end of turn. (Then shuffle this card into its owner's library.)",
        ) to abilities(
            Ability.Spell(
                instructions = Instructions(
                    steps = listOf(
                        doing(
                            Effect.ApplyMechanics(
                                mechanics = listOf(
                                    Mechanic.ModifyPowerToughness(
                                        power = Value.Times(value = Value.Announced(name = "X"), factor = -1),
                                        toughness = Value.Times(value = Value.Announced(name = "X"), factor = -1),
                                    ),
                                ),
                                affects = Affected.Objects(objects = Objects.Each(filter = and(not(sub("Dragon")), creature))),
                                duration = Duration.EndOfTurn,
                            ),
                        ),
                    ),
                    announced = listOf("X"),
                ),
            ),
        ),
    )

    @Test
    fun galeshriekFeral() = cardFaces(
        Layout.Omen,
        card(
            name = "Galeshriek Feral",
            cost = "{4}{R}",
            type = "Creature — Dragon",
            pt = "3/3",
            text = """
                Flying, haste
                {1}{R}: This creature gets +1/+0 until end of turn.
            """,
        ) to abilities(
            Keyword.Flying,
            Keyword.Haste,
            Ability.Activated(
                cost = Cost(parts = listOf(mana("{1}{R}"))),
                instructions = Instructions(
                    steps = listOf(
                        doing(
                            Effect.ApplyMechanics(
                                mechanics = listOf(Mechanic.ModifyPowerToughness(power = n(1), toughness = n(0))),
                                affects = Affected.Objects(objects = Objects.Self),
                                duration = Duration.EndOfTurn,
                            ),
                        ),
                    ),
                ),
                rules = anyTime,
            ),
        ),
        card(
            name = "Roust Out",
            cost = "{1}{R}",
            type = "Sorcery — Omen",
            text = "Discard a card. If you do, draw two cards. (Then shuffle this card into its owner's library.)",
        ) to abilities(
            Ability.Spell(
                instructions = Instructions(
                    steps = listOf(
                        Step.Pay(
                            cost = Cost(parts = listOf(CostPart.Discard(what = CostObjects.Choose(filter = cardFilter, count = n(1))))),
                            payer = Players.You,
                            mandatory = true,
                            bind = "paid",
                        ),
                        Step.If(
                            condition = Condition.Did(name = "paid"),
                            then = listOf(doing(Effect.Draw(players = Players.You, count = n(2)))),
                        ),
                    ),
                ),
            ),
        ),
    )

    @Test
    fun doublemawStormbrood() = cardFaces(
        Layout.Omen,
        card(
            name = "Doublemaw Stormbrood",
            cost = "{5}{W}",
            type = "Creature — Dragon",
            pt = "5/4",
            text = """
                Flying
                When this creature enters, you gain 5 life.
            """,
        ) to abilities(
            Keyword.Flying,
            Ability.Triggered(
                trigger = entersSelf,
                instructions = Instructions(steps = listOf(doing(Effect.GainLife(players = Players.You, amount = n(5))))),
            ),
        ),
        card(
            name = "Scorching Bite",
            cost = "{1}{R}",
            type = "Sorcery — Omen",
            text = "Scorching Bite deals 5 damage to target creature without flying. (Then shuffle this card into its owner's library.)",
        ) to abilities(
            Ability.Spell(
                instructions = Instructions(
                    targets = listOf(
                        TargetSlot(
                            name = "creature",
                            what = TargetKind.Objects(filter = and(creature, not(ObjectFilter.HasKeyword(keyword = Keyword.Flying)))),
                            min = n(1),
                            max = n(1),
                        ),
                    ),
                    steps = listOf(doing(Effect.DealDamage(to = Recipients(objects = target("creature")), amount = n(5), source = Objects.Self))),
                ),
            ),
        ),
    )

    @Test
    fun spinwingStormbrood() = cardFaces(
        Layout.Omen,
        card(
            name = "Spinwing Stormbrood",
            cost = "{4}{U}",
            type = "Creature — Dragon",
            pt = "4/3",
            text = """
                Flash
                Flying
                You may cast sorcery spells and Dragon spells as though they had flash.
            """,
        ) to abilities(
            Keyword.Flash,
            Keyword.Flying,
            Ability.Static(
                mechanics = listOf(Mechanic.CastAsThoughFlash(spells = or(and(sorcery, spellFilter), and(sub("Dragon"), spellFilter)))),
                affects = Affected.Players(players = Players.You),
            ),
        ),
        card(
            name = "Soaring Surge",
            cost = "{2}{G}",
            type = "Sorcery — Omen",
            text = "Put three +1/+1 counters on target creature you control. (Then shuffle this card into its owner's library.)",
        ) to abilities(
            Ability.Spell(
                instructions = Instructions(
                    targets = listOf(TargetSlot(name = "creature", what = TargetKind.Objects(filter = and(creature, you)), min = n(1), max = n(1))),
                    steps = listOf(doing(Effect.PutCounters(objects = target("creature"), kind = p1p1, amount = n(3)))),
                ),
            ),
        ),
    )
}
