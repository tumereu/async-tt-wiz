package arcanum.parser.sets.tdm

import arcanum.api.*
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.cardFaces
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

/**
 * Tarkir: Dragonstorm chunk 03: renew abilities (an ability word, CR 207.2c) exiling the card from the graveyard: counters of several kinds, decayed counters on X targets, doubling counters, copies of a creature until end of turn, an optional copy as it enters.
 */
class TdmChunk03RenewTest {
    @Test
    fun bejeweledCrocodile() = card(
        name = "Bejeweled Crocodile",
        cost = "{4}{B}",
        type = "Creature — Crocodile",
        pt = "5/3",
        text = """
            When this creature dies, create a 2/2 black Zombie Druid creature token.
            Renew — {B}, Exile this card from your graveyard: Put a +1/+1 counter on target creature. Activate only as a sorcery.
        """,
    ) parsesTo abilities(
        Ability.Triggered(
            trigger = diesSelf,
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
                            count = n(1),
                            controller = Players.You,
                        ),
                    ),
                ),
            ),
        ),
        Ability.Activated(
            cost = Cost(parts = listOf(mana("{B}"), CostPart.Exile(what = CostObjects.Self, from = Zone.Graveyard))),
            instructions = Instructions(
                targets = listOf(TargetSlot(name = "creature", what = TargetKind.Objects(filter = creature), min = n(1), max = n(1))),
                steps = listOf(doing(Effect.PutCounters(objects = target("creature"), kind = p1p1, amount = n(1)))),
            ),
            rules = asSorcery,
            abilityWord = "Renew",
        ),
    )

    @Test
    fun envoyOfTheFangs() = card(
        name = "Envoy of the Fangs",
        cost = "{1}{U}",
        type = "Creature — Human Rogue",
        pt = "2/1",
        text = "Renew — {3}{U}, Exile this card from your graveyard: Put two +1/+1 counters on target creature. Activate only as a sorcery.",
    ) parsesTo abilities(
        Ability.Activated(
            cost = Cost(parts = listOf(mana("{3}{U}"), CostPart.Exile(what = CostObjects.Self, from = Zone.Graveyard))),
            instructions = Instructions(
                targets = listOf(TargetSlot(name = "creature", what = TargetKind.Objects(filter = creature), min = n(1), max = n(1))),
                steps = listOf(doing(Effect.PutCounters(objects = target("creature"), kind = p1p1, amount = n(2)))),
            ),
            rules = asSorcery,
            abilityWord = "Renew",
        ),
    )

    @Test
    fun apothecarysHelper() = card(
        name = "Apothecary's Helper",
        cost = "{1}{B}",
        type = "Creature — Monkey",
        pt = "2/1",
        text = """
            Lifelink
            Renew — {1}{B}, Exile this card from your graveyard: Put a lifelink counter on target creature. Activate only as a sorcery.
        """,
    ) parsesTo abilities(
        Keyword.Lifelink,
        Ability.Activated(
            cost = Cost(parts = listOf(mana("{1}{B}"), CostPart.Exile(what = CostObjects.Self, from = Zone.Graveyard))),
            instructions = Instructions(
                targets = listOf(TargetSlot(name = "creature", what = TargetKind.Objects(filter = creature), min = n(1), max = n(1))),
                steps = listOf(
                    doing(
                        Effect.PutCounters(
                            objects = target("creature"),
                            kind = CounterKind.Keyword(keyword = Keyword.Lifelink),
                            amount = n(1),
                        ),
                    ),
                ),
            ),
            rules = asSorcery,
            abilityWord = "Renew",
        ),
    )

    @Test
    fun championOfTheTusks() = card(
        name = "Champion of the Tusks",
        cost = "{2}{G}",
        type = "Creature — Human Warrior",
        pt = "4/2",
        text = """
            Trample
            Renew — {1}{G}, Exile this card from your graveyard: Put a +1/+1 counter and a trample counter on target creature. Activate only as a sorcery.
        """,
    ) parsesTo abilities(
        Keyword.Trample,
        Ability.Activated(
            cost = Cost(parts = listOf(mana("{1}{G}"), CostPart.Exile(what = CostObjects.Self, from = Zone.Graveyard))),
            instructions = Instructions(
                targets = listOf(TargetSlot(name = "creature", what = TargetKind.Objects(filter = creature), min = n(1), max = n(1))),
                steps = listOf(
                    doing(Effect.PutCounters(objects = target("creature"), kind = p1p1, amount = n(1))),
                    doing(
                        Effect.PutCounters(
                            objects = target("creature"),
                            kind = CounterKind.Keyword(keyword = Keyword.Trample),
                            amount = n(1),
                        ),
                    ),
                ),
            ),
            rules = asSorcery,
            abilityWord = "Renew",
        ),
    )

    @Test
    fun coilingSage() = card(
        name = "Coiling Sage",
        cost = "{4}{U}",
        type = "Creature — Snake Wizard",
        pt = "4/4",
        text = """
            When this creature enters, tap target creature an opponent controls and put a stun counter on it. (If a permanent with a stun counter would become untapped, remove one from it instead.)
            Renew — {2}{U}, Exile this card from your graveyard: Tap target creature an opponent controls and put a stun counter on it. Activate only as a sorcery.
        """,
    ) parsesTo abilities(
        Ability.Triggered(
            trigger = entersSelf,
            instructions = Instructions(
                targets = listOf(TargetSlot(name = "creature", what = TargetKind.Objects(filter = and(creature, opponents)), min = n(1), max = n(1))),
                steps = listOf(
                    doing(Effect.Tap(objects = target("creature"))),
                    doing(Effect.PutCounters(objects = target("creature"), kind = CounterKind.Stun, amount = n(1))),
                ),
            ),
        ),
        Ability.Activated(
            cost = Cost(parts = listOf(mana("{2}{U}"), CostPart.Exile(what = CostObjects.Self, from = Zone.Graveyard))),
            instructions = Instructions(
                targets = listOf(TargetSlot(name = "creature", what = TargetKind.Objects(filter = and(creature, opponents)), min = n(1), max = n(1))),
                steps = listOf(
                    doing(Effect.Tap(objects = target("creature"))),
                    doing(Effect.PutCounters(objects = target("creature"), kind = CounterKind.Stun, amount = n(1))),
                ),
            ),
            rules = asSorcery,
            abilityWord = "Renew",
        ),
    )

    @Test
    fun hoardscaleKeeper() = card(
        name = "Hoardscale Keeper",
        cost = "{1}{B}{G}{U}",
        type = "Creature — Dragon",
        pt = "3/3",
        text = """
            Flying
            Whenever one or more cards leave your graveyard during your turn, create a Treasure token. (It's an artifact with "{T}, Sacrifice this token: Add one mana of any color.")
            Renew — {2}{B}{G}{U}, Exile this card from your graveyard: Put two +1/+1 counters and a flying counter on target creature. Activate only as a sorcery.
        """,
    ) parsesTo abilities(
        Keyword.Flying,
        Ability.Triggered(
            trigger = Trigger.On(
                events = listOf(EventPattern.ZoneChange(objects = and(cardFilter, ownedByYou), from = Zone.Graveyard)),
                oneOrMore = true,
            ),
            instructions = Instructions(
                steps = listOf(doing(Effect.CreateToken(token = TokenSpec.Predefined(name = "Treasure"), count = n(1), controller = Players.You))),
            ),
            triggersOnlyIf = Condition.YourTurn,
        ),
        Ability.Activated(
            cost = Cost(parts = listOf(mana("{2}{B}{G}{U}"), CostPart.Exile(what = CostObjects.Self, from = Zone.Graveyard))),
            instructions = Instructions(
                targets = listOf(TargetSlot(name = "creature", what = TargetKind.Objects(filter = creature), min = n(1), max = n(1))),
                steps = listOf(
                    doing(Effect.PutCounters(objects = target("creature"), kind = p1p1, amount = n(2))),
                    doing(
                        Effect.PutCounters(
                            objects = target("creature"),
                            kind = CounterKind.Keyword(keyword = Keyword.Flying),
                            amount = n(1),
                        ),
                    ),
                ),
            ),
            rules = asSorcery,
            abilityWord = "Renew",
        ),
    )

    @Test
    fun mirefenProwler() = card(
        name = "Mirefen Prowler",
        cost = "{2}{G}{G}",
        type = "Creature — Snake Ranger",
        pt = "5/5",
        text = """
            When this creature enters, you may mill cards equal to the number of lands you control.
            Renew — {1}{G}, Exile this card from your graveyard: Put X +1/+1 counters on target creature, where X is the number of land cards in your graveyard. Activate only as a sorcery.
        """,
    ) parsesTo abilities(
        Ability.Triggered(
            trigger = entersSelf,
            instructions = Instructions(
                steps = listOf(
                    Step.May(
                        steps = listOf(
                            doing(Effect.Mill(players = Players.You, count = Value.Count(objects = Objects.Each(filter = and(land, you))))),
                        ),
                        player = Players.You,
                    ),
                ),
            ),
        ),
        Ability.Activated(
            cost = Cost(parts = listOf(mana("{1}{G}"), CostPart.Exile(what = CostObjects.Self, from = Zone.Graveyard))),
            instructions = Instructions(
                targets = listOf(TargetSlot(name = "creature", what = TargetKind.Objects(filter = creature), min = n(1), max = n(1))),
                steps = listOf(
                    doing(
                        Effect.PutCounters(
                            objects = target("creature"),
                            kind = p1p1,
                            amount = Value.Count(objects = Objects.Each(filter = and(land, inZone(Zone.Graveyard), ownedByYou))),
                        ),
                    ),
                ),
            ),
            rules = asSorcery,
            abilityWord = "Renew",
        ),
    )

    @Test
    fun nagaSkinshaper() = card(
        name = "Naga Skinshaper",
        cost = "{3}{U}",
        type = "Creature — Snake Shapeshifter",
        pt = "0/0",
        text = """
            You may have this creature enter as a copy of any creature on the battlefield.
            Renew — {2}{U}, Exile this card from your graveyard: Put a +1/+1 counter on target nonlegendary creature you control. Each other creature you control becomes a copy of that creature until end of turn. Activate only as a sorcery.
        """,
    ) parsesTo abilities(
        Ability.Static(
            mechanics = listOf(
                Mechanic.Replacement(
                    event = EventPattern.ZoneChange(objects = isSelf, to = Zone.Battlefield),
                    modify = listOf(Modification.EntersAsCopy(of = creature, optional = true)),
                ),
            ),
            affects = Affected.Objects(objects = Objects.Self),
        ),
        Ability.Activated(
            cost = Cost(parts = listOf(mana("{2}{U}"), CostPart.Exile(what = CostObjects.Self, from = Zone.Graveyard))),
            instructions = Instructions(
                targets = listOf(
                    TargetSlot(
                        name = "creature",
                        what = TargetKind.Objects(filter = and(not(ObjectFilter.Super(supertype = Supertype.Legendary)), creature, you)),
                        min = n(1),
                        max = n(1),
                    ),
                ),
                steps = listOf(
                    doing(Effect.PutCounters(objects = target("creature"), kind = p1p1, amount = n(1))),
                    doing(
                        Effect.ApplyMechanics(
                            mechanics = listOf(Mechanic.CopyOf(objects = target("creature"))),
                            affects = Affected.Objects(
                                objects = Objects.Each(filter = and(creature, you, not(ObjectFilter.Is(objects = target("creature"))))),
                            ),
                            duration = Duration.EndOfTurn,
                        ),
                    ),
                ),
            ),
            rules = asSorcery,
            abilityWord = "Renew",
        ),
    )

    @Test
    fun fenmarshRevenant() = card(
        name = "Fenmarsh Revenant",
        cost = "{1}{B}{B}",
        type = "Creature — Vampire",
        pt = "3/3",
        text = """
            Flying, deathtouch, lifelink
            Renew — {2}{B}, Exile this card from your graveyard: Put a flying counter, a deathtouch counter, and a lifelink counter on target creature. Activate only as a sorcery.
        """,
    ) parsesTo abilities(
        Keyword.Flying,
        Keyword.Deathtouch,
        Keyword.Lifelink,
        Ability.Activated(
            cost = Cost(parts = listOf(mana("{2}{B}"), CostPart.Exile(what = CostObjects.Self, from = Zone.Graveyard))),
            instructions = Instructions(
                targets = listOf(TargetSlot(name = "creature", what = TargetKind.Objects(filter = creature), min = n(1), max = n(1))),
                steps = listOf(
                    doing(
                        Effect.PutCounters(
                            objects = target("creature"),
                            kind = CounterKind.Keyword(keyword = Keyword.Flying),
                            amount = n(1),
                        ),
                    ),
                    doing(
                        Effect.PutCounters(
                            objects = target("creature"),
                            kind = CounterKind.Keyword(keyword = Keyword.Deathtouch),
                            amount = n(1),
                        ),
                    ),
                    doing(
                        Effect.PutCounters(
                            objects = target("creature"),
                            kind = CounterKind.Keyword(keyword = Keyword.Lifelink),
                            amount = n(1),
                        ),
                    ),
                ),
            ),
            rules = asSorcery,
            abilityWord = "Renew",
        ),
    )

    @Test
    fun blightHexRakshasa() = card(
        name = "Blight-Hex Rakshasa",
        cost = "{1}{B}",
        type = "Creature — Demon",
        pt = "5/5",
        text = """
            Trample
            Decayed (This creature can't block. When it attacks, sacrifice it at end of combat.)
            Renew — {X}{B}{B}, Exile this card from your graveyard: Put a decayed counter on each of X target creatures. Activate only as a sorcery.
        """,
    ) parsesTo abilities(
        Keyword.Trample,
        Keyword.Decayed,
        Ability.Activated(
            cost = Cost(parts = listOf(mana("{X}{B}{B}"), CostPart.Exile(what = CostObjects.Self, from = Zone.Graveyard))),
            instructions = Instructions(
                targets = listOf(
                    TargetSlot(
                        name = "creatures",
                        what = TargetKind.Objects(filter = creature),
                        min = Value.Announced(name = "X"),
                        max = Value.Announced(name = "X"),
                    ),
                ),
                steps = listOf(
                    doing(
                        Effect.PutCounters(
                            objects = target("creatures"),
                            kind = CounterKind.Keyword(keyword = Keyword.Decayed),
                            amount = n(1),
                        ),
                    ),
                ),
                announced = listOf("X"),
            ),
            rules = asSorcery,
            abilityWord = "Renew",
        ),
    )

    @Test
    fun sageOfTheCoil() = card(
        name = "Sage of the Coil",
        cost = "{2}{G}",
        type = "Creature — Human Druid",
        pt = "2/2",
        text = """
            When this creature enters, put a +1/+1 counter on target creature.
            Renew — {3}{G}, Exile this card from your graveyard: Put a +1/+1 counter on target creature, then double the number of +1/+1 counters on that creature. Activate only as a sorcery.
        """,
    ) parsesTo abilities(
        Ability.Triggered(
            trigger = entersSelf,
            instructions = Instructions(
                targets = listOf(TargetSlot(name = "creature", what = TargetKind.Objects(filter = creature), min = n(1), max = n(1))),
                steps = listOf(doing(Effect.PutCounters(objects = target("creature"), kind = p1p1, amount = n(1)))),
            ),
        ),
        Ability.Activated(
            cost = Cost(parts = listOf(mana("{3}{G}"), CostPart.Exile(what = CostObjects.Self, from = Zone.Graveyard))),
            instructions = Instructions(
                targets = listOf(TargetSlot(name = "creature", what = TargetKind.Objects(filter = creature), min = n(1), max = n(1))),
                steps = listOf(
                    doing(Effect.PutCounters(objects = target("creature"), kind = p1p1, amount = n(1))),
                    doing(Effect.Action(action = KeywordAction.Double(what = Doubled.Counters(objects = target("creature"), kind = p1p1)))),
                ),
            ),
            rules = asSorcery,
            abilityWord = "Renew",
        ),
    )

    @Test
    fun junglePummeler() = card(
        name = "Jungle Pummeler",
        cost = "{3}{G}",
        type = "Creature — Beast",
        pt = "4/4",
        text = """
            Reach
            Renew — {4}{G}, Exile this card from your graveyard: Put two +1/+1 counters and a reach counter on target creature. Activate only as a sorcery.
        """,
    ) parsesTo abilities(
        Keyword.Reach,
        Ability.Activated(
            cost = Cost(parts = listOf(mana("{4}{G}"), CostPart.Exile(what = CostObjects.Self, from = Zone.Graveyard))),
            instructions = Instructions(
                targets = listOf(TargetSlot(name = "creature", what = TargetKind.Objects(filter = creature), min = n(1), max = n(1))),
                steps = listOf(
                    doing(Effect.PutCounters(objects = target("creature"), kind = p1p1, amount = n(2))),
                    doing(
                        Effect.PutCounters(objects = target("creature"), kind = CounterKind.Keyword(keyword = Keyword.Reach), amount = n(1)),
                    ),
                ),
            ),
            rules = asSorcery,
            abilityWord = "Renew",
        ),
    )
}
