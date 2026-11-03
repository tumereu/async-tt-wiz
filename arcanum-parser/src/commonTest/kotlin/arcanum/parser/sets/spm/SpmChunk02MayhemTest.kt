package arcanum.parser.sets.spm

import arcanum.api.*
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.cardFaces
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

/**
 * Marvel's Spider-Man chunk 02: mayhem (CR 702.187): casting a card discarded this turn from the graveyard for its mayhem cost, a land's mayhem without a cost (CR 702.187c), "if this spell's mayhem cost was paid", "if it was cast from your graveyard", mayhem given to the cards in a graveyard at their mana costs, spells cast from a graveyard costing less, and a copy with another name (CR 707.9b).
 */
class SpmChunk02MayhemTest {

    @Test
    fun varrikThousandFaces() = card(
        name = "Varrik, Thousand Faces",
        cost = "{3}{U}",
        type = "Legendary Creature — Human Shapeshifter Villain",
        pt = "2/3",
        text = """
            You may have Varrik enter as a copy of a creature you control, except his name is Varrik, Thousand Faces.
            Mayhem {2}{U} (You may cast this card from your graveyard for {2}{U} if you discarded it this turn. Timing rules still apply.)
        """,
    ) parsesTo abilities(
        static(
            Mechanic.Replacement(
                event = EventPattern.ZoneChange(objects = ObjectFilter.Is(Objects.Self), to = Zone.Battlefield),
                modify = listOf(
                    Modification.EntersAsCopy(of = and(creature, you), except = listOf(CopyException.Name("Varrik, Thousand Faces")), optional = true),
                ),
            ),
        ),
        Keyword.Mayhem(cost(mana("{2}{U}"))),
    )

    @Test
    fun haldenGrimsby() = cardFaces(
        Layout.ModalDoubleFaced,
        card(
            name = "Halden Grimsby",
            cost = "{1}{U}",
            type = "Legendary Creature — Human Scientist Villain",
            pt = "1/1",
            text = """
                Halden Grimsby can't be blocked.
                Whenever Halden Grimsby deals combat damage to a player, he connives. (Draw a card, then discard a card. If you discarded a nonland card, put a +1/+1 counter on this creature.)
                {1}{U}{B}{R}: Transform Halden Grimsby. Activate only as a sorcery.
            """,
        ) to abilities(
            static(Mechanic.CantBeBlocked),
            triggered(
                on(EventPattern.Damage(source = ObjectFilter.Is(Objects.Self), to = RecipientFilter(players = PlayerFilter.Any), combat = true)),
                doing(Effect.Action(KeywordAction.Connive(objects = Objects.Self, count = n(1)))),
            ),
            activated(cost(mana("{1}{U}{B}{R}")), doing(Effect.Transform(Objects.Self)), rules = asSorcery),
        ),
        card(
            name = "Verdant Fiend",
            cost = "{1}{U}{B}{R}",
            type = "Legendary Creature — Goblin Human Villain",
            pt = "3/3",
            text = """
                Flying, menace
                Spells you cast from your graveyard cost {2} less to cast.
                Goblin Formula — Each nonland card in your graveyard has mayhem. The mayhem cost is equal to its mana cost. (You may cast a card from your graveyard for its mayhem cost if you discarded it this turn. Timing rules still apply.)
            """,
        ) to abilities(
            Keyword.Flying,
            Keyword.Menace,
            static(
                Mechanic.CostChange(
                    what = and(ObjectFilter.Category(ObjectCategory.Spell), you, ObjectFilter.WasCast(Zone.Graveyard)),
                    change = CostChange.Less(mana = ManaCost.parse("{2}")!!, times = n(1)),
                ),
            ),
            Ability.Static(
                mechanics = listOf(has(Keyword.Mayhem(cost(CostPart.ItsManaCost)))),
                affects = Affected.Objects(Objects.Each(and(not(land), inZone(Zone.Graveyard), ObjectFilter.OwnedBy(PlayerFilter.You)))),
                abilityWord = "Goblin Formula",
            ),
        ),
    )

    @Test
    fun cellblockRiot() = card(
        name = "Cellblock Riot",
        cost = "{4}{B}",
        type = "Sorcery",
        text = """
            Return target creature card from your graveyard to the battlefield with an additional +1/+1 counter on it.
            Mayhem {3}{B} (You may cast this card from your graveyard for {3}{B} if you discarded it this turn. Timing rules still apply.)
        """,
    ) parsesTo abilities(
        spell(
            doing(Effect.Move(
                    objects = target("card"),
                    to = Destination(zone = Zone.Battlefield, counters = listOf(CountersToPut(kind = p1p1, amount = n(1)))),
                )),
            targets = listOf(slot("card", and(creature, inZone(Zone.Graveyard), ObjectFilter.OwnedBy(PlayerFilter.You)))),
        ),
        Keyword.Mayhem(cost(mana("{3}{B}"))),
    )

    @Test
    fun duneSinkhole() = card(
        name = "Dune Sinkhole",
        cost = "{1}{B}{B}",
        type = "Sorcery",
        text = """
            Mayhem {3}{B} (You may cast this card from your graveyard for {3}{B} if you discarded it this turn. Timing rules still apply.)
            All creatures get -2/-2 until end of turn. If this spell's mayhem cost was paid, creatures your opponents control get -2/-2 until end of turn instead.
        """,
    ) parsesTo abilities(
        Keyword.Mayhem(cost(mana("{3}{B}"))),
        spell(
            Step.If(
                condition = Condition.Paid(keyword = Keyword.Mayhem.Kind, times = 1, of = Objects.Self),
                then = listOf(untilEndOfTurn(Objects.Each(and(creature, opponents)), pt(-2, -2))),
                otherwise = listOf(untilEndOfTurn(Objects.Each(creature), pt(-2, -2))),
            ),
        ),
    )

    @Test
    fun thrumHiveIncarnate() = card(
        name = "Thrum, Hive Incarnate",
        cost = "{2}{B}",
        type = "Legendary Creature — Insect Villain",
        pt = "2/2",
        text = """
            Flash
            Flying
            Mayhem {B} (You may cast this card from your graveyard for {B} if you discarded it this turn. Timing rules still apply.)
        """,
    ) parsesTo abilities(
        Keyword.Flash,
        Keyword.Flying,
        Keyword.Mayhem(cost(mana("{B}"))),
    )

    @Test
    fun galvanicLash() = card(
        name = "Galvanic Lash",
        cost = "{2}{R}",
        type = "Sorcery",
        text = """
            Galvanic Lash deals 4 damage to target creature.
            Mayhem {1}{R} (You may cast this card from your graveyard for {1}{R} if you discarded it this turn. Timing rules still apply.)
        """,
    ) parsesTo abilities(
        spell(
            doing(Effect.DealDamage(to = Recipients(target("creature")), amount = n(4), source = Objects.Self)),
            targets = listOf(slot("creature", creature)),
        ),
        Keyword.Mayhem(cost(mana("{1}{R}"))),
    )

    @Test
    fun seethingGoblinMob() = card(
        name = "Seething Goblin Mob",
        cost = "{4}{R}",
        type = "Creature — Goblin Berserker Villain",
        pt = "5/4",
        text = """
            Haste
            Mayhem {2}{R} (You may cast this card from your graveyard for {2}{R} if you discarded it this turn. Timing rules still apply.)
        """,
    ) parsesTo abilities(
        Keyword.Haste,
        Keyword.Mayhem(cost(mana("{2}{R}"))),
    )

    @Test
    fun isleOfWebbedFolk() = card(
        name = "Isle of Webbed Folk",
        cost = "{3}{R}",
        type = "Creature — Spider Horror Citizen",
        pt = "4/3",
        text = "Mayhem {1}{R} (You may cast this card from your graveyard for {1}{R} if you discarded it this turn. Timing rules still apply.)",
    ) parsesTo abilities(
        Keyword.Mayhem(cost(mana("{1}{R}"))),
    )

    @Test
    fun ruinmawScarletFrenzy() = card(
        name = "Ruinmaw, Scarlet Frenzy",
        cost = "{2}{B}{R}",
        type = "Legendary Creature — Symbiote Villain",
        pt = "4/3",
        text = """
            Trample
            When Ruinmaw enters, return target creature card with mana value 3 or less from your graveyard to the battlefield. It gains "This creature attacks each combat if able" and "When this creature deals combat damage to a player, sacrifice it."
            Mayhem {B}{R}
        """,
    ) parsesTo abilities(
        Keyword.Trample,
        triggered(
            on(EventPattern.ZoneChange(objects = ObjectFilter.Is(Objects.Self), to = Zone.Battlefield)),
            doing(Effect.Move(objects = target("card"), to = Destination(Zone.Battlefield), postBind = "returned")),
            doing(Effect.ApplyMechanics(
                    mechanics = listOf(
                        has(
                            static(Mechanic.MustAttack()),
                            triggered(
                                on(EventPattern.Damage(source = ObjectFilter.Is(Objects.Self), to = RecipientFilter(players = PlayerFilter.Any), combat = true)),
                                doing(Effect.Sacrifice(Objects.Self)),
                            ),
                        ),
                    ),
                    affects = Affected.Objects(Objects.Bound("returned")),
                    duration = Duration.Indefinitely,
                )),
            targets = listOf(
                slot(
                    "card",
                    and(
                        creature,
                        ObjectFilter.ManaValue(comparison = Comparison.AtMost, value = n(3)),
                        inZone(Zone.Graveyard),
                        ObjectFilter.OwnedBy(PlayerFilter.You),
                    ),
                ),
            ),
        ),
        Keyword.Mayhem(cost(mana("{B}{R}"))),
    )

    @Test
    fun duskWeaverBitterTwin() = card(
        name = "Dusk Weaver, Bitter Twin",
        cost = "{B}{R}",
        type = "Legendary Creature — Spider Human Hero",
        pt = "2/1",
        text = """
            Menace (This creature can't be blocked except by two or more creatures.)
            When Dusk Weaver enters, you may discard a card. If you do, put a +1/+1 counter on him.
            Mayhem {B/R} (You may cast this card from your graveyard for {B/R} if you discarded it this turn. Timing rules still apply.)
        """,
    ) parsesTo abilities(
        Keyword.Menace,
        triggered(
            on(EventPattern.ZoneChange(objects = ObjectFilter.Is(Objects.Self), to = Zone.Battlefield)),
            Step.Pay(
                cost = cost(
                    CostPart.Discard(CostObjects.Choose(filter = ObjectFilter.Category(ObjectCategory.Card), count = n(1))),
                ),
                payer = Players.You,
                bind = "paid",
            ),
            Step.If(
                condition = Condition.Did("paid"),
                then = listOf(
                    doing(Effect.PutCounters(objects = Objects.EventObject(EventRole.After), kind = p1p1, amount = n(1))),
                ),
            ),
        ),
        Keyword.Mayhem(cost(mana("{B/R}"))),
    )

    @Test
    fun paramountGremlinTyrant() = card(
        name = "Paramount Gremlin Tyrant",
        cost = "{1}{B/R}{B/R}",
        type = "Legendary Creature — Goblin Villain",
        pt = "5/4",
        text = """
            At the beginning of your upkeep, discard a card, then create a Treasure token.
            Mayhem {2}{B/R} (You may cast this card from your graveyard for {2}{B/R} if you discarded it this turn. Timing rules still apply.)
        """,
    ) parsesTo abilities(
        triggered(
            on(EventPattern.StepBegins(step = TurnStep.Upkeep, whose = PlayerFilter.You)),
            doing(Effect.Discard(players = Players.You, count = n(1), filter = ObjectFilter.Any)),
            doing(Effect.CreateToken(token = TokenSpec.Predefined("Treasure"), count = n(1), controller = Players.You)),
        ),
        Keyword.Mayhem(cost(mana("{2}{B/R}"))),
    )

    @Test
    fun jetPropelledGlidewing() = card(
        name = "Jet-Propelled Glidewing",
        cost = "{3}",
        type = "Artifact — Equipment",
        text = """
            When this Equipment enters, if it was cast from your graveyard, attach it to target creature you control.
            Equipped creature gets +2/+0 and has flying and haste.
            Equip {2}
            Mayhem {2}
        """,
    ) parsesTo abilities(
        triggered(
            on(EventPattern.ZoneChange(objects = ObjectFilter.Is(Objects.Self), to = Zone.Battlefield)),
            doing(Effect.Attach(objects = Objects.Self, to = Recipients(target("creature")))),
            targets = listOf(slot("creature", and(creature, you))),
            interveningIf = Condition.CastFrom(zone = Zone.Graveyard, of = Objects.Self),
        ),
        static(pt(2, 0), has(Keyword.Flying, Keyword.Haste), affects = Objects.AttachedTo(Objects.Self)),
        Keyword.Equip(cost(mana("{2}"))),
        Keyword.Mayhem(cost(mana("{2}"))),
    )

    @Test
    fun grimsbyFoundry() = card(
        name = "Grimsby Foundry",
        cost = null,
        type = "Land",
        text = """
            This land enters tapped.
            When this land enters from a graveyard, you lose 2 life.
            {T}: Add {U}, {B}, or {R}.
            Mayhem (You may play this card from your graveyard if you discarded it this turn. Timing rules still apply.)
        """,
    ) parsesTo abilities(
        static(
            Mechanic.Replacement(
                event = EventPattern.ZoneChange(objects = ObjectFilter.Is(Objects.Self), to = Zone.Battlefield),
                modify = listOf(Modification.EntersTapped),
            ),
        ),
        triggered(
            on(EventPattern.ZoneChange(objects = ObjectFilter.Is(Objects.Self), from = Zone.Graveyard, to = Zone.Battlefield)),
            doing(Effect.LoseLife(players = Players.You, amount = n(2))),
        ),
        activated(
            cost(tap),
            doing(Effect.AddMana(
                    mana = ManaProduction(
                        ManaKind.Choose(
                            amount = n(1),
                            colors = listOf(ColorRef.Literal(Color.Blue), ColorRef.Literal(Color.Black), ColorRef.Literal(Color.Red)),
                        ),
                    ),
                    players = Players.You,
                )),
        ),
        Keyword.Mayhem(null),
    )

    @Test
    fun parasitePact() = card(
        name = "Parasite Pact",
        cost = "{1}{B}",
        type = "Enchantment — Aura",
        text = """
            Enchant creature
            Enchanted creature gets +1/+1, has menace, and is a Symbiote in addition to its other types.
            You may cast this card from your graveyard by discarding a card in addition to paying its other costs.
        """,
    ) parsesTo abilities(
        Keyword.Enchant(TargetKind.Objects(creature)),
        static(
            pt(1, 1),
            has(Keyword.Menace),
            Mechanic.SetTypes(addSubtypes = listOf(SubtypeRef.Literal(Subtype("Symbiote")))),
            affects = Objects.AttachedTo(Objects.Self),
        ),
        static(
            Mechanic.MayPlay(
                from = Zone.Graveyard,
                how = PlayKind.Cast,
                additionalCost = cost(
                    CostPart.Discard(CostObjects.Choose(filter = ObjectFilter.Category(ObjectCategory.Card), count = n(1))),
                ),
                holder = PlayerFilter.You,
            ),
        ),
    )
}
