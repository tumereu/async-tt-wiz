package arcanum.parser.sets.tdm

import arcanum.api.*
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.cardFaces
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

/**
 * Tarkir: Dragonstorm chunk 05: Dragonstorm enchantments returning to hand when a Dragon enters, total toughness thresholds, double damage from creatures that entered this turn, delve for every spell, a copy of a graveyard card cast free, divided damage among any number of targets, hexproof until it has dealt damage.
 */
class TdmChunk05DragonstormsAndEldersTest {
    @Test
    fun arsenalDragon() = card(
        name = "Arsenal Dragon",
        cost = "{3}{W}{B}{G}",
        type = "Creature — Dragon",
        pt = "3/4",
        text = """
            Flying
            When this creature enters, distribute three +1/+1 counters among one, two, or three target creatures you control.
        """,
    ) parsesTo abilities(
        Keyword.Flying,
        Ability.Triggered(
            trigger = entersSelf,
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
    )

    @Test
    fun ovaroKinOfMany() = card(
        name = "Ovaro, Kin of Many",
        cost = "{2}{W}{B}{G}",
        type = "Legendary Creature — Spirit Dragon",
        pt = "5/7",
        text = """
            Flying
            At the beginning of your end step, if creatures you control have total toughness 10 or greater, draw a card. Then if creatures you control have total toughness 20 or greater, untap each creature you control. Then if creatures you control have total toughness 40 or greater, each opponent loses half their life, rounded up.
        """,
    ) parsesTo abilities(
        Keyword.Flying,
        Ability.Triggered(
            trigger = Trigger.On(events = listOf(EventPattern.StepBegins(step = TurnStep.End, whose = PlayerFilter.You))),
            instructions = Instructions(
                steps = listOf(
                    doing(Effect.Draw(players = Players.You, count = n(1))),
                    Step.If(
                        condition = Condition.Compare(
                            left = Value.Of(
                                property = NumericProperty.Toughness,
                                objects = Objects.Each(filter = and(creature, you)),
                                how = Combine.Total,
                            ),
                            comparison = Comparison.AtLeast,
                            right = n(20),
                        ),
                        then = listOf(doing(Effect.Untap(objects = Objects.Each(filter = and(creature, you))))),
                    ),
                    Step.If(
                        condition = Condition.Compare(
                            left = Value.Of(
                                property = NumericProperty.Toughness,
                                objects = Objects.Each(filter = and(creature, you)),
                                how = Combine.Total,
                            ),
                            comparison = Comparison.AtLeast,
                            right = n(40),
                        ),
                        then = listOf(
                            Step.ForEach(
                                each = Each.Players(players = Players.Each(filter = PlayerFilter.Opponent)),
                                bind = "opponent",
                                steps = listOf(
                                    doing(
                                        Effect.LoseLife(
                                            players = Players.Bound(name = "opponent"),
                                            amount = Value.Half(value = Value.LifeOf(players = Players.Bound(name = "opponent")), roundUp = true),
                                        ),
                                    ),
                                ),
                                together = true,
                            ),
                        ),
                    ),
                ),
            ),
            interveningIf = Condition.Compare(
                left = Value.Of(property = NumericProperty.Toughness, objects = Objects.Each(filter = and(creature, you)), how = Combine.Total),
                comparison = Comparison.AtLeast,
                right = n(10),
            ),
        ),
    )

    @Test
    fun rendingWyrmstorm() = card(
        name = "Rending Wyrmstorm",
        cost = "{4}{R}",
        type = "Enchantment",
        text = """
            When this enchantment enters, exile cards from the top of your library until you exile a nonland card. You may cast it without paying its mana cost if that spell's mana value is 8 or less. If you don't, put that card into your hand.
            When a Dragon you control enters, return this enchantment to its owner's hand.
        """,
    ) parsesTo abilities(
        Ability.Triggered(
            trigger = entersSelf,
            instructions = Instructions(
                steps = listOf(
                    doing(
                        Effect.RevealUntil(
                            filter = not(land),
                            library = Players.You,
                            count = n(1),
                            reveal = false,
                            into = Zone.Exile,
                            foundBind = "found",
                        ),
                    ),
                    Step.Pay(
                        steps = listOf(
                            doing(
                                Effect.Cast(
                                    objects = bound("found"),
                                    how = PlayKind.Cast,
                                    withoutPaying = true,
                                    caster = Players.You,
                                    resultingSpell = ObjectFilter.ManaValue(comparison = Comparison.AtMost, value = n(8)),
                                ),
                            ),
                        ),
                        payer = Players.You,
                        bind = "cast",
                    ),
                    Step.If(
                        condition = Condition.Not(condition = Condition.Did(name = "cast")),
                        then = listOf(doing(Effect.Move(objects = bound("found"), to = Destination(zone = Zone.Hand)))),
                    ),
                ),
            ),
        ),
        Ability.Triggered(
            trigger = Trigger.On(events = listOf(EventPattern.ZoneChange(objects = and(sub("Dragon"), you), to = Zone.Battlefield))),
            instructions = Instructions(steps = listOf(doing(Effect.Move(objects = Objects.Self, to = Destination(zone = Zone.Hand))))),
        ),
    )

    @Test
    fun wastingWyrmstorm() = card(
        name = "Wasting Wyrmstorm",
        cost = "{1}{B}",
        type = "Enchantment",
        text = """
            When this enchantment enters, each opponent loses 2 life and you gain 2 life. Surveil 2. (Look at the top two cards of your library, then put any number of them into your graveyard and the rest on top of your library in any order.)
            When a Dragon you control enters, return this enchantment to its owner's hand.
        """,
    ) parsesTo abilities(
        Ability.Triggered(
            trigger = entersSelf,
            instructions = Instructions(
                steps = listOf(
                    doing(Effect.LoseLife(players = Players.Each(filter = PlayerFilter.Opponent), amount = n(2))),
                    doing(Effect.GainLife(players = Players.You, amount = n(2))),
                    doing(Effect.Surveil(players = Players.You, count = n(2))),
                ),
            ),
        ),
        Ability.Triggered(
            trigger = Trigger.On(events = listOf(EventPattern.ZoneChange(objects = and(sub("Dragon"), you), to = Zone.Battlefield))),
            instructions = Instructions(steps = listOf(doing(Effect.Move(objects = Objects.Self, to = Destination(zone = Zone.Hand))))),
        ),
    )

    @Test
    fun wyrmbackOnslaught() = card(
        name = "Wyrmback Onslaught",
        cost = "{3}{G}{U}{R}",
        type = "Enchantment",
        text = """
            When this enchantment enters, it deals 3 damage to each creature and each planeswalker.
            Landfall — Whenever a land you control enters, create a 4/4 red Dragon creature token with flying.
        """,
    ) parsesTo abilities(
        Ability.Triggered(
            trigger = entersSelf,
            instructions = Instructions(
                steps = listOf(
                    doing(
                        Effect.DealDamage(
                            to = Recipients(objects = Objects.Each(filter = or(creature, planeswalker))),
                            amount = n(3),
                            source = Objects.Self,
                        ),
                    ),
                ),
            ),
        ),
        Ability.Triggered(
            trigger = Trigger.On(events = listOf(EventPattern.ZoneChange(objects = and(land, you), to = Zone.Battlefield))),
            instructions = Instructions(
                steps = listOf(
                    doing(
                        Effect.CreateToken(
                            token = TokenSpec.Defined(
                                face = FaceDefinition(
                                    name = "Dragon Token",
                                    colorIndicator = listOf(Color.Red),
                                    types = listOf(CardType.Creature),
                                    subtypes = listOf(Subtype(name = "Dragon")),
                                    power = PrintedNumber.Fixed(value = 4),
                                    toughness = PrintedNumber.Fixed(value = 4),
                                    abilities = listOf(Keyword.Flying),
                                ),
                            ),
                            count = n(1),
                            controller = Players.You,
                        ),
                    ),
                ),
            ),
            abilityWord = "Landfall",
        ),
    )

    @Test
    fun creepingWyrmstorm() = card(
        name = "Creeping Wyrmstorm",
        cost = "{3}{G}",
        type = "Enchantment",
        text = """
            When this enchantment enters, search your library for up to two basic land cards, put them onto the battlefield tapped, then shuffle.
            When a Dragon you control enters, return this enchantment to its owner's hand.
        """,
    ) parsesTo abilities(
        Ability.Triggered(
            trigger = entersSelf,
            instructions = Instructions(
                steps = listOf(
                    doing(
                        Effect.Search(
                            library = Players.You,
                            filter = and(ObjectFilter.Super(supertype = Supertype.Basic), land),
                            max = n(2),
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
        Ability.Triggered(
            trigger = Trigger.On(events = listOf(EventPattern.ZoneChange(objects = and(sub("Dragon"), you), to = Zone.Battlefield))),
            instructions = Instructions(steps = listOf(doing(Effect.Move(objects = Objects.Self, to = Destination(zone = Zone.Hand))))),
        ),
    )

    @Test
    fun monasteryShrinewarden() = card(
        name = "Monastery Shrinewarden",
        cost = "{2}{U}{R}{W}",
        type = "Creature — Dragon",
        pt = "3/3",
        text = """
            Flying, haste
            Whenever this creature deals combat damage to a player, you gain 1 life and draw a card.
        """,
    ) parsesTo abilities(
        Keyword.Flying,
        Keyword.Haste,
        Ability.Triggered(
            trigger = Trigger.On(
                events = listOf(EventPattern.Damage(source = isSelf, to = RecipientFilter(players = PlayerFilter.Any), combat = true)),
            ),
            instructions = Instructions(
                steps = listOf(
                    doing(Effect.GainLife(players = Players.You, amount = n(1))),
                    doing(Effect.Draw(players = Players.You, count = n(1))),
                ),
            ),
        ),
    )

    @Test
    fun highpeakGuardian() = card(
        name = "Highpeak Guardian",
        cost = "{3}{G}{U}{R}",
        type = "Creature — Dragon",
        pt = "6/5",
        text = """
            Flying, vigilance, trample
            This creature has hexproof as long as it hasn't dealt damage yet. (It can't be the target of spells or abilities your opponents control.)
        """,
    ) parsesTo abilities(
        Keyword.Flying,
        Keyword.Vigilance,
        Keyword.Trample,
        Ability.Static(
            mechanics = listOf(Mechanic.GainAbilities(abilities = listOf(Keyword.Hexproof))),
            affects = Affected.Objects(objects = Objects.Self),
            condition = Condition.Not(condition = Condition.ObjectMatches(objects = Objects.Self, filter = ObjectFilter.HasDealtDamage)),
        ),
    )

    @Test
    fun keshHeartOfTheGale() = card(
        name = "Kesh, Heart of the Gale",
        cost = "{1}{R}{W}{B}",
        type = "Legendary Creature — Spirit Dragon",
        pt = "4/5",
        text = """
            Flying
            If a creature you control that entered this turn would deal damage, it deals twice that much damage instead.
        """,
    ) parsesTo abilities(
        Keyword.Flying,
        Ability.Static(
            mechanics = listOf(
                Mechanic.Replacement(
                    event = EventPattern.Damage(
                        source = and(
                            creature,
                            you,
                            ObjectFilter.ThisTurn(event = EventPattern.ZoneChange(objects = ObjectFilter.Any, to = Zone.Battlefield)),
                        ),
                        to = RecipientFilter(),
                    ),
                    modify = listOf(Modification.MultiplyAmount(factor = 2)),
                ),
            ),
            affects = Affected.Objects(objects = Objects.Self),
        ),
    )

    @Test
    fun churningWyrmstorm() = card(
        name = "Churning Wyrmstorm",
        cost = "{1}{U}",
        type = "Enchantment",
        text = """
            When this enchantment enters, draw two cards, then discard a card.
            When a Dragon you control enters, return this enchantment to its owner's hand.
        """,
    ) parsesTo abilities(
        Ability.Triggered(
            trigger = entersSelf,
            instructions = Instructions(
                steps = listOf(
                    doing(Effect.Draw(players = Players.You, count = n(2))),
                    doing(Effect.Discard(players = Players.You, count = n(1), filter = ObjectFilter.Any)),
                ),
            ),
        ),
        Ability.Triggered(
            trigger = Trigger.On(events = listOf(EventPattern.ZoneChange(objects = and(sub("Dragon"), you), to = Zone.Battlefield))),
            instructions = Instructions(steps = listOf(doing(Effect.Move(objects = Objects.Self, to = Destination(zone = Zone.Hand))))),
        ),
    )

    @Test
    fun taviParagonOfThePath() = card(
        name = "Tavi, Paragon of the Path",
        cost = "{2}{U}{R}{W}",
        type = "Legendary Creature — Spirit Dragon",
        pt = "4/5",
        text = """
            Flying, vigilance
            When Tavi enters, exile target nonland card with mana value 3 or less from your graveyard. Copy it, then you may cast the copy without paying its mana cost. (A copy of a permanent spell becomes a token.)
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
                        what = TargetKind.Objects(
                            filter = and(
                                not(land),
                                ObjectFilter.ManaValue(comparison = Comparison.AtMost, value = n(3)),
                                inZone(Zone.Graveyard),
                                ownedByYou,
                            ),
                        ),
                        min = n(1),
                        max = n(1),
                    ),
                ),
                steps = listOf(
                    doing(Effect.Move(objects = target("card"), to = Destination(zone = Zone.Exile), postBind = "exiled")),
                    doing(Effect.CopyCard(objects = bound("exiled"), owner = Players.You, bind = "copy")),
                    Step.May(
                        steps = listOf(doing(Effect.Cast(objects = bound("copy"), how = PlayKind.Cast, withoutPaying = true, caster = Players.You))),
                        player = Players.You,
                    ),
                ),
            ),
        ),
    )

    @Test
    fun sonicScreecher() = card(
        name = "Sonic Screecher",
        cost = "{2}{R}{W}{B}",
        type = "Creature — Dragon",
        pt = "4/4",
        text = """
            Flying
            When this creature enters, it deals 2 damage to any target and you gain 2 life. If a player is dealt damage this way, they discard a card.
        """,
    ) parsesTo abilities(
        Keyword.Flying,
        Ability.Triggered(
            trigger = entersSelf,
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
                steps = listOf(
                    doing(Effect.DealDamage(to = Recipients(target = "any"), amount = n(2), source = Objects.Self, bind = "dealt")),
                    doing(Effect.GainLife(players = Players.You, amount = n(2))),
                    Step.If(
                        condition = Condition.Compare(left = Value.Bound(name = "dealt"), comparison = Comparison.AtLeast, right = n(1)),
                        then = listOf(doing(Effect.Discard(players = Players.Target(slot = "any"), count = n(1), filter = ObjectFilter.Any))),
                    ),
                ),
            ),
        ),
    )

    @Test
    fun swarmingWyrmstorm() = card(
        name = "Swarming Wyrmstorm",
        cost = "{3}{W}",
        type = "Enchantment",
        text = """
            When this enchantment enters, create two 2/2 white Soldier creature tokens.
            When a Dragon you control enters, return this enchantment to its owner's hand.
        """,
    ) parsesTo abilities(
        Ability.Triggered(
            trigger = entersSelf,
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
                            count = n(2),
                            controller = Players.You,
                        ),
                    ),
                ),
            ),
        ),
        Ability.Triggered(
            trigger = Trigger.On(events = listOf(EventPattern.ZoneChange(objects = and(sub("Dragon"), you), to = Zone.Battlefield))),
            instructions = Instructions(steps = listOf(doing(Effect.Move(objects = Objects.Self, to = Destination(zone = Zone.Hand))))),
        ),
    )

    @Test
    fun ilmarJudgeOfVirtue() = card(
        name = "Ilmar, Judge of Virtue",
        cost = "{2}{B}{G}{U}",
        type = "Legendary Creature — Spirit Dragon",
        pt = "6/6",
        text = """
            Flying, lifelink
            Spells you cast have delve. (Each card you exile from your graveyard while casting those spells pays for {1}.)
            Whenever you cast a spell, you lose life equal to its mana value.
        """,
    ) parsesTo abilities(
        Keyword.Flying,
        Keyword.Lifelink,
        Ability.Static(
            mechanics = listOf(Mechanic.GainAbilities(abilities = listOf(Keyword.Delve))),
            affects = Affected.Objects(objects = Objects.Each(filter = and(spellFilter, you))),
        ),
        Ability.Triggered(
            trigger = Trigger.On(events = listOf(EventPattern.Cast(spell = ObjectFilter.Any, by = PlayerFilter.You))),
            instructions = Instructions(
                steps = listOf(
                    doing(
                        Effect.LoseLife(
                            players = Players.You,
                            amount = Value.Of(
                                property = NumericProperty.ManaValue,
                                objects = Objects.EventObject(role = EventRole.Object),
                                how = Combine.Single,
                            ),
                        ),
                    ),
                ),
            ),
        ),
    )

    @Test
    fun sorrelTheEndlessHymn() = card(
        name = "Sorrel, the Endless Hymn",
        cost = "{5}{G}{U}{R}",
        type = "Legendary Creature — Spirit Dragon",
        pt = "10/10",
        text = """
            Flying, protection from white and from black
            When Sorrel enters, it deals X damage divided as you choose among any number of target creatures and/or planeswalkers your opponents control, where X is the number of lands you control.
        """,
    ) parsesTo abilities(
        Keyword.Flying,
        Keyword.Protection(from = ObjectFilter.OfColor(color = ColorRef.Literal(color = Color.White))),
        Keyword.Protection(from = ObjectFilter.OfColor(color = ColorRef.Literal(color = Color.Black))),
        Ability.Triggered(
            trigger = entersSelf,
            instructions = Instructions(
                targets = listOf(
                    TargetSlot(
                        name = "planeswalkers",
                        what = TargetKind.Objects(filter = and(or(creature, planeswalker), opponents)),
                        min = n(1),
                        max = null,
                        divided = Value.Count(objects = Objects.Each(filter = and(land, you))),
                    ),
                ),
                steps = listOf(
                    doing(
                        Effect.DealDamage(
                            to = Recipients(objects = target("planeswalkers")),
                            amount = Value.DividedShare,
                            source = Objects.Self,
                        ),
                    ),
                ),
            ),
        ),
    )
}
