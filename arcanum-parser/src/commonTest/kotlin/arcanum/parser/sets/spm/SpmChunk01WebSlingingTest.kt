package arcanum.parser.sets.spm

import arcanum.api.*
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.cardFaces
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

/**
 * Marvel's Spider-Man chunk 01: web-slinging (CR 702.188): an alternative cost that also returns a tapped creature you control, "if it was cast using web-slinging", the returned creature's mana value, web-slinging given to legendary spells, and an ability that puts its land from the hand onto the battlefield by returning a tapped creature.
 */
class SpmChunk01WebSlingingTest {

    @Test
    fun thessalyMindSpinner() = card(
        name = "Thessaly, Mind Spinner",
        cost = "{2}{W}",
        type = "Legendary Creature — Spider Human Hero",
        pt = "3/3",
        text = """
            Web-slinging {W} (You may cast this spell for {W} if you also return a tapped creature you control to its owner's hand.)
            As Thessaly enters, look at an opponent's hand, then choose a card type other than creature.
            Spells of the chosen type cost {1} more to cast.
        """,
    ) parsesTo abilities(
        Keyword.WebSlinging(cost(mana("{W}"))),
        static(
            Mechanic.Replacement(
                event = EventPattern.ZoneChange(objects = ObjectFilter.Is(Objects.Self), to = Zone.Battlefield),
                modify = listOf(
                    Modification.AsEnters(
                        listOf(
                            Step.Choose(choice = Choice.Player(PlayerFilter.Opponent), bind = "opponent", chooser = Players.You),
                            doing(Effect.LookAt(
                                    objects = Objects.Each(and(inZone(Zone.Hand), ObjectFilter.OwnedBy(PlayerFilter.Is(Players.Bound("opponent"))))),
                                    players = Players.You,
                                )),
                            Step.Choose(
                                choice = Choice.CardType(
                                    listOf(
                                        CardType.Artifact,
                                        CardType.Battle,
                                        CardType.Enchantment,
                                        CardType.Instant,
                                        CardType.Kindred,
                                        CardType.Land,
                                        CardType.Planeswalker,
                                        CardType.Sorcery,
                                    ),
                                ),
                                bind = "type",
                                chooser = Players.You,
                                remember = true,
                            ),
                        ),
                    ),
                ),
            ),
        ),
        static(
            Mechanic.CostChange(
                what = and(ObjectFilter.Category(ObjectCategory.Spell), ObjectFilter.Type(CardTypeRef.Remembered("type"))),
                change = CostChange.More(mana = ManaCost.parse("{1}")!!, times = n(1)),
            ),
        ),
    )

    @Test
    fun rooftopDasherSilkSailer() = card(
        name = "Rooftop Dasher, Silk Sailer",
        cost = "{2}{W}",
        type = "Legendary Creature — Spider Human Hero",
        pt = "3/3",
        text = "Web-slinging {W} (You may cast this spell for {W} if you also return a tapped creature you control to its owner's hand.)",
    ) parsesTo abilities(
        Keyword.WebSlinging(cost(mana("{W}"))),
    )

    @Test
    fun thamesbridgeWarden() = card(
        name = "Thamesbridge Warden",
        cost = "{3}{W}",
        type = "Legendary Creature — Spider Human Hero",
        pt = "3/4",
        text = """
            Web-slinging {2}{W} (You may cast this spell for {2}{W} if you also return a tapped creature you control to its owner's hand.)
            At the beginning of your end step, if two or more creatures entered the battlefield under your control this turn, you draw a card and gain 2 life.
        """,
    ) parsesTo abilities(
        Keyword.WebSlinging(cost(mana("{2}{W}"))),
        triggered(
            on(EventPattern.StepBegins(step = TurnStep.End, whose = PlayerFilter.You)),
            doing(Effect.Draw(players = Players.You, count = n(1))),
            doing(Effect.GainLife(players = Players.You, amount = n(2))),
            interveningIf = Condition.HappenedThisTurn(
                event = EventPattern.ZoneChange(objects = and(creature, you), to = Zone.Battlefield),
                atLeast = 2,
                measure = Measure.Objects,
            ),
        ),
    )

    @Test
    fun tinglingPremonition() = card(
        name = "Tingling Premonition",
        cost = "{1}{U}",
        type = "Instant",
        text = """
            Web-slinging {U} (You may cast this spell for {U} if you also return a tapped creature you control to its owner's hand.)
            Counter target instant spell, sorcery spell, or triggered ability.
        """,
    ) parsesTo abilities(
        Keyword.WebSlinging(cost(mana("{U}"))),
        spell(
            doing(Effect.Counter(target("spell or ability"))),
            targets = listOf(
                slot(
                    "spell or ability",
                    or(
                        and(instant, ObjectFilter.Category(ObjectCategory.Spell)),
                        and(sorcery, ObjectFilter.Category(ObjectCategory.Spell)),
                        ObjectFilter.AbilityOf(AbilityKind.Triggered),
                    ),
                ),
            ),
        ),
    )

    @Test
    fun corinVaelBoroughDreamer() = card(
        name = "Corin Vael, Borough Dreamer",
        cost = "{4}{G}",
        type = "Legendary Creature — Spider Human Hero",
        pt = "4/3",
        text = """
            Web-slinging {2}{G} (You may cast this spell for {2}{G} if you also return a tapped creature you control to its owner's hand.)
            When Corin Vael enters, search your library for a basic land card, put it onto the battlefield tapped, then shuffle.
        """,
    ) parsesTo abilities(
        Keyword.WebSlinging(cost(mana("{2}{G}"))),
        triggered(
            on(EventPattern.ZoneChange(objects = ObjectFilter.Is(Objects.Self), to = Zone.Battlefield)),
            doing(Effect.Search(
                    library = Players.You,
                    filter = and(ObjectFilter.Super(Supertype.Basic), land),
                    max = n(1),
                    zones = listOf(Zone.Library),
                    bind = "found",
                    searcher = Players.You,
                )),
            doing(Effect.Move(objects = Objects.Bound("found"), to = Destination(zone = Zone.Battlefield, tapped = true))),
            doing(Effect.Shuffle(Players.You)),
        ),
    )

    @Test
    fun thousandLegLegionManyAsOne() = card(
        name = "Thousand-Leg Legion, Many as One",
        cost = "{1}{G}",
        type = "Legendary Creature — Spider Hero",
        pt = "2/3",
        text = """
            Web-slinging {4}{G}{G} (You may cast this spell for {4}{G}{G} if you also return a tapped creature you control to its owner's hand.)
            When Thousand-Leg Legion enters, if they were cast using web-slinging, you gain 3 life and create two 2/1 green Spider creature tokens with reach.
        """,
    ) parsesTo abilities(
        Keyword.WebSlinging(cost(mana("{4}{G}{G}"))),
        triggered(
            on(EventPattern.ZoneChange(objects = ObjectFilter.Is(Objects.Self), to = Zone.Battlefield)),
            doing(Effect.GainLife(players = Players.You, amount = n(3))),
            doing(Effect.CreateToken(
                    token = TokenSpec.Defined(
                        FaceDefinition(
                            name = "Spider Token",
                            colorIndicator = listOf(Color.Green),
                            types = listOf(CardType.Creature),
                            subtypes = listOf(Subtype("Spider")),
                            power = PrintedNumber.Fixed(2),
                            toughness = PrintedNumber.Fixed(1),
                            abilities = listOf(Keyword.Reach),
                        ),
                    ),
                    count = n(2),
                    controller = Players.You,
                )),
            interveningIf = Condition.Paid(keyword = Keyword.WebSlinging.Kind, times = 1, of = Objects.Self),
        ),
    )

    @Test
    fun crimsonWeaverTwinOfValor() = card(
        name = "Crimson Weaver, Twin of Valor",
        cost = "{1}{R}{G}",
        type = "Legendary Creature — Spider Human Hero",
        pt = "4/3",
        text = """
            Web-slinging {R}{G} (You may cast this spell for {R}{G} if you also return a tapped creature you control to its owner's hand.)
            Trample
            Sensational Save — If Crimson Weaver was cast using web-slinging, he enters with X +1/+1 counters on him, where X is the mana value of the returned creature.
        """,
    ) parsesTo abilities(
        Keyword.WebSlinging(cost(mana("{R}{G}"))),
        Keyword.Trample,
        Ability.Static(
            mechanics = listOf(
                Mechanic.Replacement(
                    event = EventPattern.ZoneChange(objects = ObjectFilter.Is(Objects.Self), to = Zone.Battlefield),
                    modify = listOf(
                        Modification.EntersWithCounters(
                            kind = p1p1,
                            amount = Value.Of(property = NumericProperty.ManaValue, objects = Objects.Bound("web-slinging-returned"), how = Combine.Single),
                        ),
                    ),
                ),
            ),
            affects = Affected.Objects(Objects.Self),
            condition = Condition.Paid(keyword = Keyword.WebSlinging.Kind, times = 1, of = Objects.Self),
            abilityWord = "Sensational Save",
        ),
    )

    @Test
    fun lyrraStrandSpinner() = card(
        name = "Lyrra, Strand Spinner",
        cost = "{2}{G}{W}",
        type = "Legendary Creature — Spider Human Hero",
        pt = "3/5",
        text = """
            Web-slinging {1}{G}{W} (You may cast this spell for {1}{G}{W} if you also return a tapped creature you control to its owner's hand.)
            Whenever you cast a creature spell, create a 1/1 green and white Human Citizen creature token.
            {3}{G}{W}: Creatures you control get +2/+2 and gain vigilance until end of turn.
        """,
    ) parsesTo abilities(
        Keyword.WebSlinging(cost(mana("{1}{G}{W}"))),
        triggered(
            on(EventPattern.Cast(spell = creature, by = PlayerFilter.You)),
            doing(Effect.CreateToken(
                    token = TokenSpec.Defined(
                        FaceDefinition(
                            name = "Human Citizen Token",
                            colorIndicator = listOf(Color.Green, Color.White),
                            types = listOf(CardType.Creature),
                            subtypes = listOf(Subtype("Human"), Subtype("Citizen")),
                            power = PrintedNumber.Fixed(1),
                            toughness = PrintedNumber.Fixed(1),
                        ),
                    ),
                    count = n(1),
                    controller = Players.You,
                )),
        ),
        activated(
            cost(mana("{3}{G}{W}")),
            untilEndOfTurn(Objects.Each(and(creature, you)), pt(2, 2), has(Keyword.Vigilance)),
        ),
    )

    @Test
    fun monsoonStrider() = card(
        name = "Monsoon Strider",
        cost = "{3}{G}{W}",
        type = "Legendary Creature — Spider Human Hero",
        pt = "4/4",
        text = """
            Web-slinging {1}{G}{W} (You may cast this spell for {1}{G}{W} if you also return a tapped creature you control to its owner's hand.)
            Pavitr's Sevā — Whenever you cast a creature spell, put a +1/+1 counter on target creature you control. It gains flying until end of turn.
        """,
    ) parsesTo abilities(
        Keyword.WebSlinging(cost(mana("{1}{G}{W}"))),
        triggered(
            on(EventPattern.Cast(spell = creature, by = PlayerFilter.You)),
            doing(Effect.PutCounters(objects = target("creature"), kind = p1p1, amount = n(1))),
            untilEndOfTurn(target("creature"), has(Keyword.Flying)),
            targets = listOf(slot("creature", and(creature, you))),
            abilityWord = "Pavitr's Sevā",
        ),
    )

    @Test
    fun tobinAshgrove() = cardFaces(
        Layout.ModalDoubleFaced,
        card(
            name = "Tobin Ashgrove",
            cost = "{1}{W}",
            type = "Legendary Creature — Human Scientist Hero",
            pt = "0/1",
            text = """
                When Tobin Ashgrove enters, create a 2/1 green Spider creature token with reach.
                {1}{G}{W}{U}: Transform Tobin Ashgrove. Activate only as a sorcery.
            """,
        ) to abilities(
            triggered(
                on(EventPattern.ZoneChange(objects = ObjectFilter.Is(Objects.Self), to = Zone.Battlefield)),
                doing(Effect.CreateToken(
                        token = TokenSpec.Defined(
                            FaceDefinition(
                                name = "Spider Token",
                                colorIndicator = listOf(Color.Green),
                                types = listOf(CardType.Creature),
                                subtypes = listOf(Subtype("Spider")),
                                power = PrintedNumber.Fixed(2),
                                toughness = PrintedNumber.Fixed(1),
                                abilities = listOf(Keyword.Reach),
                            ),
                        ),
                        count = n(1),
                        controller = Players.You,
                    )),
            ),
            activated(cost(mana("{1}{G}{W}{U}")), doing(Effect.Transform(Objects.Self)), rules = asSorcery),
        ),
        card(
            name = "Astonishing Webcrawler",
            cost = "{1}{G}{W}{U}",
            type = "Legendary Creature — Spider Human Hero",
            pt = "4/4",
            text = """
                Vigilance, reach
                Each legendary spell you cast that's one or more colors has web-slinging {G}{W}{U}. (You may cast a spell for its web-slinging cost if you also return a tapped creature you control to its owner's hand.)
            """,
        ) to abilities(
            Keyword.Vigilance,
            Keyword.Reach,
            static(
                has(Keyword.WebSlinging(cost(mana("{G}{W}{U}")))),
                affects = Objects.Each(
                    and(
                        ObjectFilter.Category(ObjectCategory.Spell),
                        legendary,
                        not(ObjectFilter.Colorless),
                        you,
                        ObjectFilter.WasCast(),
                    ),
                ),
            ),
        ),
    )

    @Test
    fun sprawlSanctum() = card(
        name = "Sprawl Sanctum",
        cost = null,
        type = "Land",
        text = """
            This land enters tapped.
            {T}: Add {G}, {W}, or {U}.
            {2}, Return a tapped creature you control to its owner's hand: Put this card from your hand onto the battlefield. Activate only as a sorcery.
        """,
    ) parsesTo abilities(
        static(
            Mechanic.Replacement(
                event = EventPattern.ZoneChange(objects = ObjectFilter.Is(Objects.Self), to = Zone.Battlefield),
                modify = listOf(Modification.EntersTapped),
            ),
        ),
        activated(
            cost(tap),
            doing(Effect.AddMana(
                    mana = ManaProduction(
                        ManaKind.Choose(
                            amount = n(1),
                            colors = listOf(ColorRef.Literal(Color.Green), ColorRef.Literal(Color.White), ColorRef.Literal(Color.Blue)),
                        ),
                    ),
                    players = Players.You,
                )),
        ),
        activated(
            cost(
                mana("{2}"),
                CostPart.ReturnToHand(CostObjects.Choose(filter = and(ObjectFilter.HasStatus(Status.Tapped), creature, you), count = n(1))),
            ),
            doing(Effect.Move(objects = Objects.Self, to = Destination(Zone.Battlefield))),
            rules = asSorcery,
            functionsIn = listOf(Zone.Hand),
        ),
    )
}
