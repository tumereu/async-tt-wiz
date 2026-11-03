package arcanum.parser.sets.tdm

import arcanum.api.*
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.cardFaces
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

/**
 * Tarkir: Dragonstorm chunk 07: harmonize (CR 702.180), harmonize given to a card in the graveyard with its mana cost as the cost, a reflexive trigger after discarding a nonland card.
 */
class TdmChunk07HarmonizeTest {
    @Test
    fun focusedWyrmfire() = card(
        name = "Focused Wyrmfire",
        cost = "{R}",
        type = "Sorcery",
        text = """
            Focused Wyrmfire deals 2 damage to any target.
            Harmonize {5}{R}{R} (You may cast this card from your graveyard for its harmonize cost. You may tap a creature you control to reduce that cost by {X}, where X is its power. Then exile this spell.)
        """,
    ) parsesTo abilities(
        Ability.Spell(
            instructions = Instructions(
                targets = listOf(
                    TargetSlot(
                        name = "any",
                        what = TargetKind.Either(
                            objects = or(creature, planeswalker, ObjectFilter.Type(cardType = CardTypeRef.Literal(cardType = CardType.Battle))),
                            players = PlayerFilter.Any,
                        ),
                        min = n(1),
                        max = n(1),
                    ),
                ),
                steps = listOf(doing(Effect.DealDamage(to = Recipients(target = "any"), amount = n(2), source = Objects.Self))),
            ),
        ),
        Keyword.Harmonize(cost = Cost(parts = listOf(mana("{5}{R}{R}")))),
    )

    @Test
    fun frostboundWyrmhunt() = card(
        name = "Frostbound Wyrmhunt",
        cost = "{U}{R}",
        type = "Sorcery",
        text = """
            Draw a card, then you may discard a card. When you discard a nonland card this way, Frostbound Wyrmhunt deals 3 damage to target creature.
            Harmonize {4}{U}{R} (You may cast this card from your graveyard for its harmonize cost. You may tap a creature you control to reduce that cost by {X}, where X is its power. Then exile this spell.)
        """,
    ) parsesTo abilities(
        Ability.Spell(
            instructions = Instructions(
                steps = listOf(
                    doing(Effect.Draw(players = Players.You, count = n(1))),
                    Step.Pay(
                        cost = Cost(
                            parts = listOf(
                                CostPart.Discard(what = CostObjects.Choose(filter = ObjectFilter.Any, count = n(1)), postBind = "discarded"),
                            ),
                        ),
                        payer = Players.You,
                        bind = "paid",
                    ),
                    Step.If(
                        condition = Condition.And(
                            all = listOf(Condition.Did(name = "paid"), Condition.ObjectMatches(objects = bound("discarded"), filter = not(land))),
                        ),
                        then = listOf(
                            doing(
                                Effect.ReflexiveTrigger(
                                    instructions = Instructions(
                                        targets = listOf(
                                            TargetSlot(
                                                name = "creature",
                                                what = TargetKind.Objects(filter = creature),
                                                min = n(1),
                                                max = n(1),
                                            ),
                                        ),
                                        steps = listOf(
                                            doing(
                                                Effect.DealDamage(
                                                    to = Recipients(objects = target("creature")),
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
                ),
            ),
        ),
        Keyword.Harmonize(cost = Cost(parts = listOf(mana("{4}{U}{R}")))),
    )

    @Test
    fun mastodonBellow() = card(
        name = "Mastodon Bellow",
        cost = "{2}{G}{U}{R}",
        type = "Sorcery",
        text = """
            Create a 5/5 green Elephant creature token.
            Harmonize {5}{G}{U}{R} (You may cast this card from your graveyard for its harmonize cost. You may tap a creature you control to reduce that cost by {X}, where X is its power. Then exile this spell.)
        """,
    ) parsesTo abilities(
        Ability.Spell(
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
        Keyword.Harmonize(cost = Cost(parts = listOf(mana("{5}{G}{U}{R}")))),
    )

    @Test
    fun wildCadence() = card(
        name = "Wild Cadence",
        cost = "{X}{G}{G}",
        type = "Sorcery",
        text = """
            Search your library for a creature card with mana value X or less, put it onto the battlefield, then shuffle.
            Harmonize {X}{G}{G}{G}{G} (You may cast this card from your graveyard for its harmonize cost. You may tap a creature you control to reduce that cost by an amount of generic mana equal to its power. Then exile this spell.)
        """,
    ) parsesTo abilities(
        Ability.Spell(
            instructions = Instructions(
                steps = listOf(
                    doing(
                        Effect.Search(
                            library = Players.You,
                            filter = and(creature, ObjectFilter.ManaValue(comparison = Comparison.AtMost, value = Value.Announced(name = "X"))),
                            max = n(1),
                            zones = listOf(Zone.Library),
                            bind = "found",
                            searcher = Players.You,
                        ),
                    ),
                    doing(Effect.Move(objects = bound("found"), to = Destination(zone = Zone.Battlefield))),
                    doing(Effect.Shuffle(library = Players.You)),
                ),
                announced = listOf("X"),
            ),
        ),
        Keyword.Harmonize(cost = Cost(parts = listOf(mana("{X}{G}{G}{G}{G}")))),
    )

    @Test
    fun wanderersHabit() = card(
        name = "Wanderer's Habit",
        cost = "{2}{G}",
        type = "Sorcery",
        text = """
            Search your library for a basic land card, put it onto the battlefield tapped, then shuffle.
            Harmonize {4}{G} (You may cast this card from your graveyard for its harmonize cost. You may tap a creature you control to reduce that cost by {X}, where X is its power. Then exile this spell.)
        """,
    ) parsesTo abilities(
        Ability.Spell(
            instructions = Instructions(
                steps = listOf(
                    doing(
                        Effect.Search(
                            library = Players.You,
                            filter = and(ObjectFilter.Super(supertype = Supertype.Basic), land),
                            max = n(1),
                            zones = listOf(Zone.Library),
                            bind = "found",
                            searcher = Players.You,
                        ),
                    ),
                    doing(Effect.Move(objects = bound("found"), to = Destination(zone = Zone.Battlefield, tapped = true))),
                    doing(Effect.Shuffle(library = Players.You)),
                ),
            ),
        ),
        Keyword.Harmonize(cost = Cost(parts = listOf(mana("{4}{G}")))),
    )

    @Test
    fun hymnweaverMage() = card(
        name = "Hymnweaver Mage",
        cost = "{G}{U}{R}",
        type = "Creature — Human Bard",
        pt = "3/2",
        text = """
            Flash
            When this creature enters, target instant or sorcery card in your graveyard gains harmonize until end of turn. Its harmonize cost is equal to its mana cost. (You may cast that card from your graveyard for its harmonize cost. You may tap a creature you control to reduce that cost by {X}, where X is its power. Then exile the spell.)
        """,
    ) parsesTo abilities(
        Keyword.Flash,
        Ability.Triggered(
            trigger = entersSelf,
            instructions = Instructions(
                targets = listOf(
                    TargetSlot(
                        name = "card",
                        what = TargetKind.Objects(filter = and(or(instant, sorcery), inZone(Zone.Graveyard), ownedByYou)),
                        min = n(1),
                        max = n(1),
                    ),
                ),
                steps = listOf(
                    doing(
                        Effect.ApplyMechanics(
                            mechanics = listOf(
                                Mechanic.GainAbilities(
                                    abilities = listOf(Keyword.Harmonize(cost = Cost(parts = listOf(CostPart.ItsManaCost)))),
                                ),
                            ),
                            affects = Affected.Objects(objects = target("card")),
                            duration = Duration.EndOfTurn,
                        ),
                    ),
                ),
            ),
        ),
    )

    @Test
    fun unisonCharge() = card(
        name = "Unison Charge",
        cost = "{1}{G}",
        type = "Sorcery",
        text = """
            Distribute two +1/+1 counters among one or two target creatures you control. Creatures you control with counters on them gain vigilance and trample until end of turn.
            Harmonize {4}{G} (You may cast this card from your graveyard for its harmonize cost. You may tap a creature you control to reduce that cost by {X}, where X is its power. Then exile this spell.)
        """,
    ) parsesTo abilities(
        Ability.Spell(
            instructions = Instructions(
                targets = listOf(
                    TargetSlot(
                        name = "creatures",
                        what = TargetKind.Objects(filter = and(creature, you)),
                        min = n(1),
                        max = n(2),
                        divided = n(2),
                    ),
                ),
                steps = listOf(
                    doing(Effect.PutCounters(objects = target("creatures"), kind = p1p1, amount = Value.DividedShare)),
                    doing(
                        Effect.ApplyMechanics(
                            mechanics = listOf(Mechanic.GainAbilities(abilities = listOf(Keyword.Vigilance, Keyword.Trample))),
                            affects = Affected.Objects(
                                objects = Objects.Each(filter = and(creature, you, ObjectFilter.HasCounter(kind = null, atLeast = 1))),
                            ),
                            duration = Duration.EndOfTurn,
                        ),
                    ),
                ),
            ),
        ),
        Keyword.Harmonize(cost = Cost(parts = listOf(mana("{4}{G}")))),
    )

    @Test
    fun endlessMurmur() = card(
        name = "Endless Murmur",
        cost = "{U}",
        type = "Sorcery",
        text = """
            Draw a card.
            Harmonize {5}{U} (You may cast this card from your graveyard for its harmonize cost. You may tap a creature you control to reduce that cost by {X}, where X is its power. Then exile this spell.)
        """,
    ) parsesTo abilities(
        Ability.Spell(instructions = Instructions(steps = listOf(doing(Effect.Draw(players = Players.You, count = n(1)))))),
        Keyword.Harmonize(cost = Cost(parts = listOf(mana("{5}{U}")))),
    )

    @Test
    fun sorrelsRebuke() = card(
        name = "Sorrel's Rebuke",
        cost = "{1}{U}",
        type = "Sorcery",
        text = """
            Return target creature to its owner's hand.
            Harmonize {5}{U} (You may cast this card from your graveyard for its harmonize cost. You may tap a creature you control to reduce that cost by {X}, where X is its power. Then exile this spell.)
        """,
    ) parsesTo abilities(
        Ability.Spell(
            instructions = Instructions(
                targets = listOf(TargetSlot(name = "creature", what = TargetKind.Objects(filter = creature), min = n(1), max = n(1))),
                steps = listOf(doing(Effect.Move(objects = target("creature"), to = Destination(zone = Zone.Hand)))),
            ),
        ),
        Keyword.Harmonize(cost = Cost(parts = listOf(mana("{5}{U}")))),
    )

    @Test
    fun recklessGallop() = card(
        name = "Reckless Gallop",
        cost = "{R}",
        type = "Sorcery",
        text = """
            Target creature gets +3/+0 and gains haste until end of turn.
            Harmonize {4}{R} (You may cast this card from your graveyard for its harmonize cost. You may tap a creature you control to reduce that cost by {X}, where X is its power. Then exile this spell.)
        """,
    ) parsesTo abilities(
        Ability.Spell(
            instructions = Instructions(
                targets = listOf(TargetSlot(name = "creature", what = TargetKind.Objects(filter = creature), min = n(1), max = n(1))),
                steps = listOf(
                    doing(
                        Effect.ApplyMechanics(
                            mechanics = listOf(
                                Mechanic.ModifyPowerToughness(power = n(3), toughness = n(0)),
                                Mechanic.GainAbilities(abilities = listOf(Keyword.Haste)),
                            ),
                            affects = Affected.Objects(objects = target("creature")),
                            duration = Duration.EndOfTurn,
                        ),
                    ),
                ),
            ),
        ),
        Keyword.Harmonize(cost = Cost(parts = listOf(mana("{4}{R}")))),
    )

    @Test
    fun longnightTales() = card(
        name = "Longnight Tales",
        cost = "{2}{U}",
        type = "Sorcery",
        text = """
            Draw three cards. Then discard two cards unless you discard a creature card.
            Harmonize {4}{U} (You may cast this card from your graveyard for its harmonize cost. You may tap a creature you control to reduce that cost by {X}, where X is its power. Then exile this spell.)
        """,
    ) parsesTo abilities(
        Ability.Spell(
            instructions = Instructions(
                steps = listOf(
                    doing(Effect.Draw(players = Players.You, count = n(3))),
                    Step.Pay(
                        cost = Cost(parts = listOf(CostPart.Discard(what = CostObjects.Choose(filter = creature, count = n(1))))),
                        payer = Players.You,
                        bind = "paid",
                    ),
                    Step.If(
                        condition = Condition.Not(condition = Condition.Did(name = "paid")),
                        then = listOf(doing(Effect.Discard(players = Players.You, count = n(2), filter = ObjectFilter.Any))),
                    ),
                ),
            ),
        ),
        Keyword.Harmonize(cost = Cost(parts = listOf(mana("{4}{U}")))),
    )
}
