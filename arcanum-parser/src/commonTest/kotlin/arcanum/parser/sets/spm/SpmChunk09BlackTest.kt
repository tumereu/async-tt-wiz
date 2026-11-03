package arcanum.parser.sets.spm

import arcanum.api.*
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.cardFaces
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

/**
 * Marvel's Spider-Man chunk 09: black cards: different names among targets (CR 601.2c), each player revealing and losing life by the other's card, surveil by counters, X counters as it enters (CR 107.3m), life equal to creatures controlled.
 */
class SpmChunk09BlackTest {

    @Test
    fun witnessTheWickedHalfDozen() = card(
        name = "Witness the Wicked Half-Dozen!",
        cost = "{6}{B}",
        type = "Sorcery",
        text = "Return up to six target creature cards with different names from your graveyard to the battlefield.",
    ) parsesTo abilities(
        spell(
            doing(Effect.Move(objects = target("card"), to = Destination(Zone.Battlefield))),
            targets = listOf(
                TargetSlot(
                    name = "card",
                    what = TargetKind.Objects(and(creature, inZone(Zone.Graveyard), ObjectFilter.OwnedBy(PlayerFilter.You))),
                    min = n(0),
                    max = n(6),
                    distinct = SharedQuality.Name,
                ),
            ),
        ),
    )

    @Test
    fun backAlleyPickpocket() = card(
        name = "Back-Alley Pickpocket",
        cost = "{1}{B}",
        type = "Creature — Human Rogue Villain",
        pt = "2/2",
        text = "When this creature dies, create a Treasure token. (It's an artifact with \"{T}, Sacrifice this token: Add one mana of any color.\")",
    ) parsesTo abilities(
        triggered(
            on(EventPattern.ZoneChange(objects = ObjectFilter.Is(Objects.Self), from = Zone.Battlefield, to = Zone.Graveyard)),
            doing(Effect.CreateToken(token = TokenSpec.Predefined("Treasure"), count = n(1), controller = Players.You)),
        ),
    )

    @Test
    fun edwinBrask() = cardFaces(
        Layout.ModalDoubleFaced,
        card(
            name = "Edwin Brask",
            cost = "{2}{B}",
            type = "Legendary Creature — Human Hero Villain",
            pt = "3/3",
            text = """
                When Edwin Brask enters, return target creature card with mana value 1 or less from your graveyard to the battlefield.
                {3}{B}{R}{G}: Transform Edwin Brask. Activate only as a sorcery.
            """,
        ) to abilities(
            triggered(
                on(EventPattern.ZoneChange(objects = ObjectFilter.Is(Objects.Self), to = Zone.Battlefield)),
                doing(Effect.Move(objects = target("card"), to = Destination(Zone.Battlefield))),
                targets = listOf(
                    slot(
                        "card",
                        and(
                            creature,
                            ObjectFilter.ManaValue(comparison = Comparison.AtMost, value = n(1)),
                            inZone(Zone.Graveyard),
                            ObjectFilter.OwnedBy(PlayerFilter.You),
                        ),
                    ),
                ),
            ),
            activated(cost(mana("{3}{B}{R}{G}")), doing(Effect.Transform(Objects.Self)), rules = asSorcery),
        ),
        card(
            name = "Ink Fiend, Deadly Guardian",
            cost = "{3}{B}{R}{G}",
            type = "Legendary Creature — Symbiote Hero Villain",
            pt = "5/5",
            text = """
                Menace, trample, haste
                Whenever Ink Fiend attacks, you may sacrifice another creature. If you do, draw X cards, then you may put a permanent card with mana value X or less from your hand onto the battlefield, where X is the sacrificed creature's mana value.
            """,
        ) to abilities(
            Keyword.Menace,
            Keyword.Trample,
            Keyword.Haste,
            triggered(
                on(EventPattern.Attack(ObjectFilter.Is(Objects.Self))),
                Step.Pay(
                    cost = cost(
                        CostPart.Sacrifice(what = CostObjects.Choose(filter = and(creature, you, another), count = n(1)), preBind = "sacrificed"),
                    ),
                    payer = Players.You,
                    bind = "paid",
                ),
                Step.If(
                    condition = Condition.Did("paid"),
                    then = listOf(
                        doing(Effect.Draw(
                                players = Players.You,
                                count = Value.Of(property = NumericProperty.ManaValue, objects = Objects.Bound("sacrificed"), how = Combine.Single),
                            )),
                        Step.Choose(
                            choice = Choice.Objects(
                                filter = and(
                                    or(
                                        artifact,
                                        ObjectFilter.Type(CardType.Battle),
                                        creature,
                                        enchantment,
                                        land,
                                        ObjectFilter.Type(CardType.Planeswalker),
                                    ),
                                    inZone(Zone.Hand),
                                    ObjectFilter.OwnedBy(PlayerFilter.You),
                                    ObjectFilter.ManaValue(
                                        comparison = Comparison.AtMost,
                                        value = Value.Of(property = NumericProperty.ManaValue, objects = Objects.Bound("sacrificed"), how = Combine.Single),
                                    ),
                                ),
                                min = n(0),
                                max = n(1),
                            ),
                            bind = "chosen",
                            chooser = Players.You,
                        ),
                        doing(Effect.Move(objects = Objects.Bound("chosen"), to = Destination(Zone.Battlefield))),
                    ),
                ),
            ),
        ),
    )

    @Test
    fun innerFiendsRacketeers() = card(
        name = "Inner Fiends Racketeers",
        cost = "{3}{B}",
        type = "Creature — Human Rogue Villain",
        pt = "3/4",
        text = "Discard a card: This creature gets +1/+0 and gains menace until end of turn. Activate only as a sorcery. (It can't be blocked except by two or more creatures.)",
    ) parsesTo abilities(
        activated(
            cost(
                CostPart.Discard(CostObjects.Choose(filter = ObjectFilter.Category(ObjectCategory.Card), count = n(1))),
            ),
            untilEndOfTurn(Objects.Self, pt(1, 0), has(Keyword.Menace)),
            rules = asSorcery,
        ),
    )

    @Test
    fun pitilessMuscle() = card(
        name = "Pitiless Muscle",
        cost = "{1}{B}",
        type = "Creature — Human Mercenary Villain",
        pt = "2/1",
        text = """
            Lifelink
            {3}{B}: This creature deals 1 damage to each opponent.
        """,
    ) parsesTo abilities(
        Keyword.Lifelink,
        activated(
            cost(mana("{3}{B}")),
            doing(Effect.DealDamage(to = Recipients(players = Players.Each(PlayerFilter.Opponent)), amount = n(1), source = Objects.Self)),
        ),
    )

    @Test
    fun mordrakeEaterOfWeavers() = card(
        name = "Mordrake, Eater of Weavers",
        cost = "{X}{B}{B}",
        type = "Legendary Creature — Vampire Villain",
        pt = "2/1",
        text = """
            Lifelink
            Mordrake enters with X +1/+1 counters on him.
            When Mordrake enters, he deals X damage to target opponent.
        """,
    ) parsesTo abilities(
        Keyword.Lifelink,
        static(
            Mechanic.Replacement(
                event = EventPattern.ZoneChange(objects = ObjectFilter.Is(Objects.Self), to = Zone.Battlefield),
                modify = listOf(Modification.EntersWithCounters(kind = p1p1, amount = Value.CastX(Objects.Self))),
            ),
        ),
        triggered(
            on(EventPattern.ZoneChange(objects = ObjectFilter.Is(Objects.Self), to = Zone.Battlefield)),
            doing(Effect.DealDamage(
                    to = Recipients(players = Players.Target("opponent")),
                    amount = Value.CastX(Objects.Self),
                    source = Objects.Self,
                )),
            targets = listOf(
                TargetSlot(name = "opponent", what = TargetKind.Players(PlayerFilter.Opponent), min = n(1), max = n(1)),
            ),
        ),
    )

    @Test
    fun rottenFortune() = card(
        name = "Rotten Fortune",
        cost = "{2}{B}",
        type = "Enchantment",
        text = "At the beginning of your end step, two target players each reveal the top card of their library. They each lose life equal to the mana value of the card revealed by the other player. Then they each put the card they revealed into their hand.",
    ) parsesTo abilities(
        triggered(
            on(EventPattern.StepBegins(step = TurnStep.End, whose = PlayerFilter.You)),
            doing(Effect.Reveal(
                    objects = Objects.LibraryCards(of = Players.Target("players"), count = n(1), fromTop = true),
                    bind = "revealed",
                )),
            Step.ForEach(
                each = Each.Players(Players.Target("players")),
                bind = "player",
                steps = listOf(
                    doing(Effect.LoseLife(
                            players = Players.Bound("player"),
                            amount = Value.Of(
                                property = NumericProperty.ManaValue,
                                objects = Objects.Where(
                                    objects = Objects.Bound("revealed"),
                                    filter = not(ObjectFilter.OwnedBy(PlayerFilter.Is(Players.Bound("player")))),
                                ),
                                how = Combine.Total,
                            ),
                        )),
                ),
                together = true,
            ),
            doing(Effect.Move(objects = Objects.Bound("revealed"), to = Destination(Zone.Hand))),
            targets = listOf(TargetSlot(name = "players", what = TargetKind.Players(PlayerFilter.Any), min = n(2), max = n(2))),
        ),
    )

    @Test
    fun recklessInquiry() = card(
        name = "Reckless Inquiry",
        cost = "{2}{B}",
        type = "Sorcery",
        text = "Surveil 2, then draw two cards. You lose 2 life. (To surveil 2, look at the top two cards of your library, then put any number of them into your graveyard and the rest on top of your library in any order.)",
    ) parsesTo abilities(
        spell(
            doing(Effect.Surveil(players = Players.You, count = n(2))),
            doing(Effect.Draw(players = Players.You, count = n(2))),
            doing(Effect.LoseLife(players = Players.You, amount = n(2))),
        ),
    )

    @Test
    fun theSpiritGem() = card(
        name = "The Spirit Gem",
        cost = "{1}{B}",
        type = "Legendary Artifact — Infinity Stone",
        text = """
            Indestructible
            {T}: Add {B}.
            {6}{B}, {T}, Exile a creature you control: Harness The Spirit Gem. (Once harnessed, its ∞ ability is active.)
            ∞ — At the beginning of your upkeep, return target creature card from your graveyard to the battlefield.
        """,
    ) parsesTo abilities(
        Keyword.Indestructible,
        activated(
            cost(tap),
            doing(Effect.AddMana(mana = ManaProduction(ManaKind.Fixed(ManaCost.parse("{B}")!!)), players = Players.You)),
        ),
        activated(
            cost(
                mana("{6}{B}"),
                tap,
                CostPart.Exile(what = CostObjects.Choose(filter = and(creature, you), count = n(1)), from = Zone.Battlefield),
            ),
            doing(Effect.Mark(objects = Objects.Self, mark = ObjectMark.Harnessed)),
        ),
        Ability.Triggered(
            trigger = on(EventPattern.StepBegins(step = TurnStep.Upkeep, whose = PlayerFilter.You)),
            instructions = Instructions(
                targets = listOf(slot("card", and(creature, inZone(Zone.Graveyard), ObjectFilter.OwnedBy(PlayerFilter.You)))),
                steps = listOf(doing(Effect.Move(objects = target("card"), to = Destination(Zone.Battlefield)))),
            ),
            label = AbilityLabel.Infinity,
        ),
    )

    @Test
    fun webcrawlerInShadow() = card(
        name = "Webcrawler in Shadow",
        cost = "{4}{B}",
        type = "Legendary Creature — Spider Human Hero",
        pt = "4/4",
        text = """
            Menace
            Whenever a creature you control attacks alone, put a +1/+1 counter on it. Then surveil X, where X is the number of counters on it. (Look at the top X cards of your library, then put any number of them into your graveyard and the rest on top of your library in any order.)
        """,
    ) parsesTo abilities(
        Keyword.Menace,
        triggered(
            on(EventPattern.Attack(attacker = and(creature, you), alone = true)),
            doing(Effect.PutCounters(objects = Objects.EventObject(EventRole.Object), kind = p1p1, amount = n(1))),
            doing(Effect.Surveil(
                    players = Players.You,
                    count = Value.Of(
                        property = NumericProperty.Counters(null),
                        objects = Objects.EventObject(EventRole.Object),
                        how = Combine.Single,
                    ),
                )),
        ),
    )

    @Test
    fun theBlotSGateway() = card(
        name = "The Blot's Gateway",
        cost = "{2}{B}",
        type = "Instant",
        text = "Put target creature on the bottom of its owner's library. You lose 2 life unless you control a Villain.",
    ) parsesTo abilities(
        spell(
            doing(Effect.Move(objects = target("creature"), to = Destination(zone = Zone.Library, libraryPosition = -1))),
            Step.If(
                condition = Condition.Not(Condition.Exists(filter = and(sub("Villain"), you), atLeast = 1)),
                then = listOf(doing(Effect.LoseLife(players = Players.You, amount = n(2)))),
            ),
            targets = listOf(slot("creature", creature)),
        ),
    )

    @Test
    fun inkFiendMaliceLoosed() = card(
        name = "Ink Fiend, Malice Loosed",
        cost = "{4}{B}",
        type = "Legendary Creature — Symbiote Villain",
        pt = "4/5",
        text = """
            Deathtouch
            {2}{B}, Exile this card from your graveyard: Put two +1/+1 counters on target creature. It gains deathtouch until end of turn. Activate only as a sorcery.
        """,
    ) parsesTo abilities(
        Keyword.Deathtouch,
        activated(
            cost(mana("{2}{B}"), CostPart.Exile(what = CostObjects.Self, from = Zone.Graveyard)),
            doing(Effect.PutCounters(objects = target("creature"), kind = p1p1, amount = n(2))),
            untilEndOfTurn(target("creature"), has(Keyword.Deathtouch)),
            targets = listOf(slot("creature", creature)),
            rules = asSorcery,
        ),
    )

    @Test
    fun inkSoakedTabby() = card(
        name = "Ink-Soaked Tabby",
        cost = "{2}{B}",
        type = "Creature — Symbiote Cat Villain",
        pt = "2/3",
        text = """
            Deathtouch
            When this creature enters, mill two cards. (Put the top two cards of your library into your graveyard.)
        """,
    ) parsesTo abilities(
        Keyword.Deathtouch,
        triggered(
            on(EventPattern.ZoneChange(objects = ObjectFilter.Is(Objects.Self), to = Zone.Battlefield)),
            doing(Effect.Mill(players = Players.You, count = n(2))),
        ),
    )

    @Test
    fun feloniousFury() = card(
        name = "Felonious Fury",
        cost = "{3}{B}{B}",
        type = "Sorcery",
        text = "Target opponent loses life equal to the number of creatures they control. Then destroy all creatures.",
    ) parsesTo abilities(
        spell(
            doing(Effect.LoseLife(
                    players = Players.Target("opponent"),
                    amount = Value.Count(Objects.Each(and(creature, ObjectFilter.ControlledBy(PlayerFilter.Is(Players.Target("opponent")))))),
                )),
            doing(Effect.Destroy(Objects.Each(creature))),
            targets = listOf(
                TargetSlot(name = "opponent", what = TargetKind.Players(PlayerFilter.Opponent), min = n(1), max = n(1)),
            ),
        ),
    )

    @Test
    fun stingerSBarb() = card(
        name = "Stinger's Barb",
        cost = "{1}{B}",
        type = "Instant",
        text = "Target creature gets -3/-3 until end of turn.",
    ) parsesTo abilities(
        spell(untilEndOfTurn(target("creature"), pt(-3, -3)), targets = listOf(slot("creature", creature))),
    )
}
