package arcanum.parser.sets.spm

import arcanum.api.*
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.cardFaces
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

/**
 * Marvel's Spider-Man chunk 10: red cards: suspect (CR 701.60), damage to a creature and its controller, a creature dealing damage to itself, spells and abilities that can't be countered (CR 701.6), unspent red mana kept (CR 500.4), a Dinosaur until end of turn.
 */
class SpmChunk10RedTest {

    @Test
    fun furiousThrong() = card(
        name = "Furious Throng",
        cost = "{1}{R}",
        type = "Creature — Human Citizen",
        pt = "2/2",
        text = """
            Trample
            Whenever you cast a spell with mana value 4 or greater, this creature deals 1 damage to each opponent.
            {5}{R}: Put two +1/+1 counters on this creature. Activate only as a sorcery.
        """,
    ) parsesTo abilities(
        Keyword.Trample,
        triggered(
            on(EventPattern.Cast(
                    spell = and(
                        ObjectFilter.Category(ObjectCategory.Spell),
                        ObjectFilter.ManaValue(comparison = Comparison.AtLeast, value = n(4)),
                    ),
                    by = PlayerFilter.You,
                )),
            doing(Effect.DealDamage(to = Recipients(players = Players.Each(PlayerFilter.Opponent)), amount = n(1), source = Objects.Self)),
        ),
        activated(
            cost(mana("{5}{R}")),
            doing(Effect.PutCounters(objects = Objects.Self, kind = p1p1, amount = n(2))),
            rules = asSorcery,
        ),
    )

    @Test
    fun voltrixChargedAssailant() = card(
        name = "Voltrix, Charged Assailant",
        cost = "{1}{R}{R}",
        type = "Legendary Creature — Human Villain",
        pt = "2/3",
        text = """
            Flying
            You don't lose unspent red mana as steps and phases end.
            Whenever you cast an instant or sorcery spell, add {R}.
            When Voltrix leaves the battlefield, you may pay {X}. When you do, he deals X damage to target player.
        """,
    ) parsesTo abilities(
        Keyword.Flying,
        playerStatic(Mechanic.KeepsUnspentMana(listOf(Color.Red))),
        triggered(
            on(EventPattern.Cast(spell = or(instant, sorcery), by = PlayerFilter.You)),
            doing(Effect.AddMana(mana = ManaProduction(ManaKind.Fixed(ManaCost.parse("{R}")!!)), players = Players.You)),
        ),
        triggered(
            on(EventPattern.ZoneChange(objects = ObjectFilter.Is(Objects.Self), from = Zone.Battlefield)),
            Step.Choose(choice = Choice.Number(n(0)), bind = "X", chooser = Players.You),
            Step.Pay(
                cost = cost(CostPart.Mana(cost = ManaCost.parse("{X}")!!, x = Value.Bound("X"))),
                payer = Players.You,
                bind = "paid",
            ),
            Step.If(
                condition = Condition.Did("paid"),
                then = listOf(
                    doing(Effect.ReflexiveTrigger(
                            Instructions(
                                targets = listOf(TargetSlot(name = "player", what = TargetKind.Players(PlayerFilter.Any), min = n(1), max = n(1))),
                                steps = listOf(
                                    doing(Effect.DealDamage(to = Recipients(players = Players.Target("player")), amount = Value.Bound("X"), source = Objects.Self)),
                                ),
                            ),
                        )),
                ),
            ),
        ),
    )

    @Test
    fun hobgremlinCloakedRaider() = card(
        name = "Hobgremlin, Cloaked Raider",
        cost = "{1}{R}",
        type = "Legendary Creature — Goblin Human Villain",
        pt = "1/2",
        text = """
            Flying, haste
            Whenever you discard a card, Hobgremlin gets +2/+0 until end of turn.
        """,
    ) parsesTo abilities(
        Keyword.Flying,
        Keyword.Haste,
        triggered(
            on(EventPattern.Discard(cards = ObjectFilter.Any, players = PlayerFilter.You)),
            untilEndOfTurn(Objects.Self, pt(2, 0)),
        ),
    )

    @Test
    fun editorBarnabyStroud() = card(
        name = "Editor Barnaby Stroud",
        cost = "{2}{R}",
        type = "Legendary Creature — Human Citizen",
        pt = "2/2",
        text = """
            When Editor Barnaby Stroud enters, suspect up to one target creature. (A suspected creature has menace and can't block.)
            Whenever a creature you control with menace attacks, create a Treasure token.
        """,
    ) parsesTo abilities(
        triggered(
            on(EventPattern.ZoneChange(objects = ObjectFilter.Is(Objects.Self), to = Zone.Battlefield)),
            doing(Effect.Mark(objects = target("creature"), mark = ObjectMark.Suspected)),
            targets = listOf(slot("creature", creature, min = 0)),
        ),
        triggered(
            on(EventPattern.Attack(and(creature, you, ObjectFilter.HasKeyword(Keyword.Menace)))),
            doing(Effect.CreateToken(token = TokenSpec.Predefined("Treasure"), count = n(1), controller = Players.You)),
        ),
    )

    @Test
    fun maskedMouser() = card(
        name = "Masked Mouser",
        cost = "{R}",
        type = "Creature — Spider Cat Hero",
        pt = "1/1",
        text = """
            Haste
            Discard a card, Sacrifice this creature: Draw a card.
        """,
    ) parsesTo abilities(
        Keyword.Haste,
        activated(
            cost(
                CostPart.Discard(CostObjects.Choose(filter = ObjectFilter.Category(ObjectCategory.Card), count = n(1))),
                CostPart.Sacrifice(CostObjects.Self),
            ),
            doing(Effect.Draw(players = Players.You, count = n(1))),
        ),
    )

    @Test
    fun slagformLivingFurnace() = card(
        name = "Slagform, Living Furnace",
        cost = "{2}{R}",
        type = "Legendary Creature — Elemental Villain",
        pt = "0/0",
        text = """
            When Slagform enters, search your library for a basic Mountain card, put it onto the battlefield tapped, then shuffle.
            Slagform gets +1/+1 for each Mountain you control.
            When Slagform leaves the battlefield, sacrifice a land.
        """,
    ) parsesTo abilities(
        triggered(
            on(EventPattern.ZoneChange(objects = ObjectFilter.Is(Objects.Self), to = Zone.Battlefield)),
            doing(Effect.Search(
                    library = Players.You,
                    filter = and(ObjectFilter.Super(Supertype.Basic), sub("Mountain")),
                    max = n(1),
                    zones = listOf(Zone.Library),
                    bind = "found",
                    searcher = Players.You,
                )),
            doing(Effect.Move(objects = Objects.Bound("found"), to = Destination(zone = Zone.Battlefield, tapped = true))),
            doing(Effect.Shuffle(Players.You)),
        ),
        static(
            Mechanic.ModifyPowerToughness(
                power = Value.Count(Objects.Each(and(sub("Mountain"), you))),
                toughness = Value.Count(Objects.Each(and(sub("Mountain"), you))),
            ),
        ),
        triggered(
            on(EventPattern.ZoneChange(objects = ObjectFilter.Is(Objects.Self), from = Zone.Battlefield)),
            Step.Choose(
                choice = Choice.Objects(filter = and(land, you), min = n(1), max = n(1)),
                bind = "sacrificed",
                chooser = Players.You,
            ),
            doing(Effect.Sacrifice(Objects.Bound("sacrificed"))),
        ),
    )

    @Test
    fun moonlitTryst() = card(
        name = "Moonlit Tryst",
        cost = "{1}{R}",
        type = "Sorcery",
        text = "Discard a card, then draw two cards.",
    ) parsesTo abilities(
        spell(
            doing(Effect.Discard(players = Players.You, count = n(1), filter = ObjectFilter.Any)),
            doing(Effect.Draw(players = Players.You, count = n(2))),
        ),
    )

    @Test
    fun staticJolt() = card(
        name = "Static Jolt",
        cost = "{R}",
        type = "Instant",
        text = "Static Jolt deals 2 damage to any target.",
    ) parsesTo abilities(
        spell(
            doing(Effect.DealDamage(to = Recipients(target = "any"), amount = n(2), source = Objects.Self)),
            targets = listOf(
                TargetSlot(
                    name = "any",
                    what = TargetKind.Either(
                        objects = or(creature, ObjectFilter.Type(CardType.Planeswalker), ObjectFilter.Type(CardType.Battle)),
                        players = PlayerFilter.Any,
                    ),
                    min = n(1),
                    max = n(1),
                ),
            ),
        ),
    )

    @Test
    fun tremorfistUnyielding() = card(
        name = "Tremorfist, Unyielding",
        cost = "{4}{R}{R}",
        type = "Legendary Creature — Human Rogue Villain",
        pt = "5/5",
        text = """
            During your turn, Tremorfist has first strike.
            Vibro-Shock Gauntlets — When Tremorfist enters, he deals 2 damage to target creature and 2 damage to that creature's controller.
        """,
    ) parsesTo abilities(
        static(has(Keyword.FirstStrike), condition = Condition.YourTurn),
        triggered(
            on(EventPattern.ZoneChange(objects = ObjectFilter.Is(Objects.Self), to = Zone.Battlefield)),
            doing(Effect.DealDamage(to = Recipients(target("creature")), amount = n(2), source = Objects.Self)),
            doing(Effect.DealDamage(to = Recipients(players = Players.ControllerOf(target("creature"))), amount = n(2), source = Objects.Self)),
            targets = listOf(slot("creature", creature)),
            abilityWord = "Vibro-Shock Gauntlets",
        ),
    )

    @Test
    fun drumbeatWeaverWildHeart() = card(
        name = "Drumbeat Weaver, Wild Heart",
        cost = "{2}{R}",
        type = "Legendary Creature — Spider Human Hero",
        pt = "2/3",
        text = """
            Reach
            Whenever Drumbeat Weaver becomes tapped, you may discard a card. If you do, draw a card.
        """,
    ) parsesTo abilities(
        Keyword.Reach,
        triggered(
            on(EventPattern.Tap(ObjectFilter.Is(Objects.Self))),
            Step.Pay(
                cost = cost(
                    CostPart.Discard(CostObjects.Choose(filter = ObjectFilter.Category(ObjectCategory.Card), count = n(1))),
                ),
                payer = Players.You,
                bind = "paid",
            ),
            Step.If(condition = Condition.Did("paid"), then = listOf(doing(Effect.Draw(players = Players.You, count = n(1))))),
        ),
    )

    @Test
    fun anarchyWebslinger() = card(
        name = "Anarchy Webslinger",
        cost = "{1}{R}",
        type = "Legendary Creature — Spider Human Hero",
        pt = "2/1",
        text = """
            Riot (This creature enters with your choice of a +1/+1 counter or haste.)
            Other Spiders you control have riot.
            Spells and abilities can't be countered.
            Damage can't be prevented.
        """,
    ) parsesTo abilities(
        Keyword.Riot,
        static(has(Keyword.Riot), affects = Objects.Each(and(another, sub("Spider"), you))),
        static(
            Mechanic.CantBeCountered,
            affects = Objects.Each(or(ObjectFilter.Category(ObjectCategory.Spell), ObjectFilter.Category(ObjectCategory.StackAbility))),
        ),
        static(
            Mechanic.DamageCantBePrevented(EventPattern.Damage(source = ObjectFilter.Any, to = RecipientFilter())),
        ),
    )

    @Test
    fun sauroxTheReptileLord() = card(
        name = "Saurox the Reptile Lord",
        cost = "{4}{R}",
        type = "Legendary Creature — Dinosaur Villain",
        pt = "5/4",
        text = """
            Menace (This creature can't be blocked except by two or more creatures.)
            Dinosaur Formula — {1}{R}, Discard this card: Until end of turn, target creature you control gets +3/+1 and becomes a Dinosaur in addition to its other types.
        """,
    ) parsesTo abilities(
        Keyword.Menace,
        Ability.Activated(
            cost = cost(mana("{1}{R}"), CostPart.Discard(CostObjects.Self)),
            instructions = Instructions(
                targets = listOf(slot("creature", and(creature, you))),
                steps = listOf(
                    untilEndOfTurn(
                        target("creature"),
                        pt(3, 1),
                        Mechanic.SetTypes(addSubtypes = listOf(SubtypeRef.Literal(Subtype("Dinosaur")))),
                    ),
                ),
            ),
            rules = anyTime,
            abilityWord = "Dinosaur Formula",
        ),
    )

    @Test
    fun cabHustler() = card(
        name = "Cab Hustler",
        cost = "{1}{R}",
        type = "Creature — Human Pilot",
        pt = "3/1",
        text = "{1}, {T}: Target creature gains haste until end of turn.",
    ) parsesTo abilities(
        activated(
            cost(mana("{1}"), tap),
            untilEndOfTurn(target("creature"), has(Keyword.Haste)),
            targets = listOf(slot("creature", creature)),
        ),
    )

    @Test
    fun snappyRetort() = card(
        name = "Snappy Retort",
        cost = "{2}{R}",
        type = "Instant",
        text = "Target creature deals damage equal to its power to itself. If that creature is attacking, Snappy Retort deals 2 damage to that creature's controller.",
    ) parsesTo abilities(
        spell(
            doing(Effect.DealDamage(
                    to = Recipients(target("creature")),
                    amount = Value.Of(property = NumericProperty.Power, objects = target("creature"), how = Combine.Single),
                    source = target("creature"),
                )),
            Step.If(
                condition = Condition.ObjectMatches(objects = target("creature"), filter = ObjectFilter.HasStatus(Status.Attacking)),
                then = listOf(
                    doing(Effect.DealDamage(to = Recipients(players = Players.ControllerOf(target("creature"))), amount = n(2), source = Objects.Self)),
                ),
            ),
            targets = listOf(slot("creature", creature)),
        ),
    )
}
