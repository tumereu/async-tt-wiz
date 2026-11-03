package arcanum.parser.sets.spm

import arcanum.api.*
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.cardFaces
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

/**
 * Marvel's Spider-Man chunk 12: multicoloured cards: life totals exchanged (CR 701.12), a fight with excess damage (CR 120.4a), the greatest power among an opponent's creatures, the greatest mana value as power (CR 604.3), exiled cards returned when this leaves, a counter for mana at each player's first main phase.
 */
class SpmChunk12MulticoloredTest {

    @Test
    fun starbornWebcrawler() = card(
        name = "Starborn Webcrawler",
        cost = "{W}{U}{B}{R}{G}",
        type = "Legendary Creature — Spider Human Hero",
        pt = "5/5",
        text = """
            Flying, first strike, trample, lifelink, haste
            At the beginning of combat on your turn, other Spiders you control gain flying, first strike, trample, lifelink, and haste until end of turn.
        """,
    ) parsesTo abilities(
        Keyword.Flying,
        Keyword.FirstStrike,
        Keyword.Trample,
        Keyword.Lifelink,
        Keyword.Haste,
        triggered(
            on(EventPattern.StepBegins(step = TurnStep.BeginningOfCombat, whose = PlayerFilter.You)),
            untilEndOfTurn(
                Objects.Each(and(another, sub("Spider"), you)),
                has(Keyword.Flying, Keyword.FirstStrike, Keyword.Trample, Keyword.Lifelink, Keyword.Haste),
            ),
        ),
    )

    @Test
    fun valiantTownsfolk() = card(
        name = "Valiant Townsfolk",
        cost = "{G/W}{G/W}",
        type = "Creature — Human Citizen",
        pt = "1/1",
        text = "When this creature enters, draw a card.",
    ) parsesTo abilities(
        triggered(
            on(EventPattern.ZoneChange(objects = ObjectFilter.Is(Objects.Self), to = Zone.Battlefield)),
            doing(Effect.Draw(players = Players.You, count = n(1))),
        ),
    )

    @Test
    fun verdantFiendReturned() = card(
        name = "Verdant Fiend, Returned",
        cost = "{3}{B}{R}",
        type = "Legendary Creature — Goblin Human Villain",
        pt = "3/3",
        text = """
            Flying, deathtouch
            Whenever Verdant Fiend attacks, discard a card. Then draw a card for each card you've discarded this turn.
        """,
    ) parsesTo abilities(
        Keyword.Flying,
        Keyword.Deathtouch,
        triggered(
            on(EventPattern.Attack(ObjectFilter.Is(Objects.Self))),
            doing(Effect.Discard(players = Players.You, count = n(1), filter = ObjectFilter.Any)),
            doing(Effect.Draw(
                    players = Players.You,
                    count = Value.EventsThisTurn(event = EventPattern.Discard(cards = ObjectFilter.Any, players = PlayerFilter.You), measure = Measure.Objects),
                )),
        ),
    )

    @Test
    fun hunterKrovakApexStalker() = card(
        name = "Hunter Krovak, Apex Stalker",
        cost = "{1}{R}{G}",
        type = "Legendary Creature — Human Warrior Villain",
        pt = "*/4",
        text = """
            Vigilance
            Top of the Food Chain — Hunter Krovak's power is equal to the greatest mana value among permanents you control.
        """,
    ) parsesTo abilities(
        Keyword.Vigilance,
        Ability.Static(
            mechanics = listOf(
                Mechanic.SetPowerToughness(
                    power = Value.Of(
                        property = NumericProperty.ManaValue,
                        objects = Objects.Each(and(ObjectFilter.Category(ObjectCategory.Permanent), you)),
                        how = Combine.Greatest,
                    ),
                    toughness = null,
                ),
            ),
            affects = Affected.Objects(Objects.Self),
            characteristicDefining = true,
            abilityWord = "Top of the Food Chain",
        ),
    )

    @Test
    fun krovakTheTracker() = card(
        name = "Krovak the Tracker",
        cost = "{1}{B}{G}",
        type = "Legendary Creature — Human Warrior Villain",
        pt = "4/3",
        text = """
            Trample
            Whenever a creature an opponent controls with the greatest power among creatures that player controls dies, draw a card and put a +1/+1 counter on Krovak the Tracker.
        """,
    ) parsesTo abilities(
        Keyword.Trample,
        triggered(
            on(EventPattern.ZoneChange(
                    objects = and(
                        creature,
                        opponents,
                        ObjectFilter.Power(
                            comparison = Comparison.AtLeast,
                            value = Value.Of(property = NumericProperty.Power, objects = Objects.Each(and(creature, opponents)), how = Combine.Greatest),
                        ),
                    ),
                    from = Zone.Battlefield,
                    to = Zone.Graveyard,
                )),
            doing(Effect.Draw(players = Players.You, count = n(1))),
            doing(Effect.PutCounters(objects = Objects.Self, kind = p1p1, amount = n(1))),
        ),
    )

    @Test
    fun rosalindFairweather() = card(
        name = "Rosalind Fairweather",
        cost = "{1}{G/W}",
        type = "Legendary Creature — Human Performer",
        pt = "2/2",
        text = "Whenever a Spider you control enters, draw a card. This ability triggers only once each turn.",
    ) parsesTo abilities(
        triggered(
            on(EventPattern.ZoneChange(objects = and(sub("Spider"), you), to = Zone.Battlefield)),
            doing(Effect.Draw(players = Players.You, count = n(1))),
            perTurn = 1,
        ),
    )

    @Test
    fun monsieurInverso() = card(
        name = "Monsieur Inverso",
        cost = "{5}{W}{B}",
        type = "Legendary Creature — Human Villain",
        pt = "5/5",
        text = """
            Vigilance, lifelink
            Darkforce Inversion — When Monsieur Inverso enters, you may exchange life totals with target opponent. If you lost life this way, draw that many cards.
        """,
    ) parsesTo abilities(
        Keyword.Vigilance,
        Keyword.Lifelink,
        triggered(
            on(EventPattern.ZoneChange(objects = ObjectFilter.Is(Objects.Self), to = Zone.Battlefield)),
            Step.Pay(
                steps = listOf(doing(Effect.ExchangeLife(first = Players.You, second = Players.Target("opponent")))),
                payer = Players.You,
                bind = "paid",
            ),
            Step.If(
                condition = Condition.And(
                    listOf(
                        Condition.Did("paid"),
                        Condition.Compare(
                            left = Value.Difference(from = Value.LifeOf(Players.Target("opponent")), minus = Value.LifeOf(Players.You)),
                            comparison = Comparison.Greater,
                            right = n(0),
                        ),
                    ),
                ),
                then = listOf(
                    doing(Effect.Draw(
                            players = Players.You,
                            count = Value.Difference(from = Value.LifeOf(Players.Target("opponent")), minus = Value.LifeOf(Players.You)),
                        )),
                ),
            ),
            targets = listOf(
                TargetSlot(name = "opponent", what = TargetKind.Players(PlayerFilter.Opponent), min = n(1), max = n(1)),
            ),
            abilityWord = "Darkforce Inversion",
        ),
    )

    @Test
    fun morvaneTheBreathingVampire() = card(
        name = "Morvane the Breathing Vampire",
        cost = "{2}{U}{B}",
        type = "Legendary Creature — Vampire Scientist Villain",
        pt = "3/1",
        text = """
            Flying, vigilance, lifelink
            {U}{B}, Exile this card from your graveyard: Look at the top three cards of your library. Put one of them into your hand and the rest on the bottom of your library in any order.
        """,
    ) parsesTo abilities(
        Keyword.Flying,
        Keyword.Vigilance,
        Keyword.Lifelink,
        activated(
            cost(mana("{U}{B}"), CostPart.Exile(what = CostObjects.Self, from = Zone.Graveyard)),
            doing(Effect.LookAt(
                    objects = Objects.LibraryCards(of = Players.You, count = n(3), fromTop = true),
                    players = Players.You,
                    bind = "seen",
                )),
            Step.Choose(
                choice = Choice.Objects(filter = ObjectFilter.Any, min = n(1), max = n(1), from = Objects.Bound("seen")),
                bind = "kept",
                chooser = Players.You,
            ),
            doing(Effect.Move(objects = Objects.Bound("kept"), to = Destination(Zone.Hand))),
            doing(Effect.Move(
                    objects = Objects.Except(base = Objects.Bound("seen"), minus = Objects.Bound("kept")),
                    to = Destination(zone = Zone.Library, libraryPosition = -1),
                )),
        ),
    )

    @Test
    fun gourdBarrage() = card(
        name = "Gourd Barrage",
        cost = "{B/R}",
        type = "Sorcery",
        text = """
            As an additional cost to cast this spell, discard a card or pay {2}.
            Gourd Barrage deals 3 damage to target creature.
        """,
    ) parsesTo abilities(
        static(
            Mechanic.AdditionalCost(
                cost = cost(
                    CostPart.OneOf(
                        listOf(
                            cost(
                                CostPart.Discard(CostObjects.Choose(filter = ObjectFilter.Category(ObjectCategory.Card), count = n(1))),
                            ),
                            cost(mana("{2}")),
                        ),
                    ),
                ),
            ),
        ),
        spell(
            doing(Effect.DealDamage(to = Recipients(target("creature")), amount = n(3), source = Objects.Self)),
            targets = listOf(slot("creature", creature)),
        ),
    )

    @Test
    fun rhinoxChargingHulk() = card(
        name = "Rhinox, Charging Hulk",
        cost = "{3}{R}{R}{G}{G}",
        type = "Legendary Creature — Human Villain",
        pt = "6/7",
        text = """
            Vigilance, trample, haste
            Whenever Rhinox attacks, if you've cast a spell with mana value 4 or greater this turn, draw a card.
        """,
    ) parsesTo abilities(
        Keyword.Vigilance,
        Keyword.Trample,
        Keyword.Haste,
        triggered(
            on(EventPattern.Attack(ObjectFilter.Is(Objects.Self))),
            doing(Effect.Draw(players = Players.You, count = n(1))),
            interveningIf = Condition.HappenedThisTurn(
                event = EventPattern.Cast(
                    spell = and(
                        ObjectFilter.Category(ObjectCategory.Spell),
                        ObjectFilter.ManaValue(comparison = Comparison.AtLeast, value = n(4)),
                    ),
                    by = PlayerFilter.You,
                ),
                atLeast = 1,
                measure = Measure.Events,
            ),
        ),
    )

    @Test
    fun rhinoxSStampede() = card(
        name = "Rhinox's Stampede",
        cost = "{R/G}",
        type = "Sorcery",
        text = "Target creature you control gets +1/+0 until end of turn. It fights target creature an opponent controls. When excess damage is dealt to the creature an opponent controls this way, destroy up to one target noncreature artifact with mana value 3 or less.",
    ) parsesTo abilities(
        spell(
            untilEndOfTurn(target("creature"), pt(1, 0)),
            doing(Effect.Fight(first = target("creature"), second = target("second creature"), bind = "fight")),
            Step.If(
                condition = Condition.Compare(left = Value.Excess("fight"), comparison = Comparison.Greater, right = n(0)),
                then = listOf(
                    doing(Effect.ReflexiveTrigger(
                            Instructions(
                                targets = listOf(
                                    slot(
                                        "artifact",
                                        and(not(creature), artifact, ObjectFilter.ManaValue(comparison = Comparison.AtMost, value = n(3))),
                                        min = 0,
                                    ),
                                ),
                                steps = listOf(doing(Effect.Destroy(target("artifact")))),
                            ),
                        )),
                ),
            ),
            targets = listOf(slot("creature", and(creature, you)), slot("second creature", and(creature, opponents))),
        ),
    )

    @Test
    fun screechDiscordMaker() = card(
        name = "Screech, Discord Maker",
        cost = "{2}{B/R}",
        type = "Legendary Creature — Mutant Villain",
        pt = "2/3",
        text = """
            At the beginning of your first main phase, you may discard a card. When you do, target creature can't block this turn.
            Sonic Blast — Whenever a creature an opponent controls dies, Screech deals 1 damage to that player.
        """,
    ) parsesTo abilities(
        triggered(
            on(EventPattern.StepBegins(step = TurnStep.PrecombatMain, whose = PlayerFilter.You)),
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
                    doing(Effect.ReflexiveTrigger(
                            Instructions(
                                targets = listOf(slot("creature", creature)),
                                steps = listOf(untilEndOfTurn(target("creature"), Mechanic.CantBlock(ObjectFilter.Any))),
                            ),
                        )),
                ),
            ),
        ),
        triggered(
            on(EventPattern.ZoneChange(objects = and(creature, opponents), from = Zone.Battlefield, to = Zone.Graveyard)),
            doing(Effect.DealDamage(to = Recipients(players = Players.EventPlayer), amount = n(1), source = Objects.Self)),
            abilityWord = "Sonic Blast",
        ),
    )

    @Test
    fun webspiritApparition() = card(
        name = "Webspirit Apparition",
        cost = "{1}{R/G}",
        type = "Creature — Spider Avatar",
        pt = "2/2",
        text = """
            Reach
            {T}: Add {R} or {G}.
            Whenever you cast a spell with mana value 4 or greater, untap this creature.
        """,
    ) parsesTo abilities(
        Keyword.Reach,
        activated(
            cost(tap),
            doing(Effect.AddMana(
                    mana = ManaProduction(ManaKind.Choose(amount = n(1), colors = listOf(ColorRef.Literal(Color.Red), ColorRef.Literal(Color.Green)))),
                    players = Players.You,
                )),
        ),
        triggered(
            on(EventPattern.Cast(
                    spell = and(
                        ObjectFilter.Category(ObjectCategory.Spell),
                        ObjectFilter.ManaValue(comparison = Comparison.AtLeast, value = n(4)),
                    ),
                    by = PlayerFilter.You,
                )),
            doing(Effect.Untap(Objects.Self)),
        ),
    )

    @Test
    fun weaveDaughterHeirHero() = card(
        name = "Weave Daughter, Heir Hero",
        cost = "{G}{W}",
        type = "Legendary Creature — Spider Human Hero",
        pt = "2/2",
        text = """
            During your turn, Weave Daughter has flying.
            When Weave Daughter leaves the battlefield, create a 1/1 green and white Human Citizen creature token.
        """,
    ) parsesTo abilities(
        static(has(Keyword.Flying), condition = Condition.YourTurn),
        triggered(
            on(EventPattern.ZoneChange(objects = ObjectFilter.Is(Objects.Self), from = Zone.Battlefield)),
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
    )

    @Test
    fun webmatronDazzlingRescuer() = card(
        name = "Webmatron, Dazzling Rescuer",
        cost = "{1}{W/U}",
        type = "Legendary Creature — Spider Human Hero",
        pt = "2/2",
        text = """
            Flying
            Venom Blast — Artifacts and creatures your opponents control enter tapped.
        """,
    ) parsesTo abilities(
        Keyword.Flying,
        Ability.Static(
            mechanics = listOf(
                Mechanic.Replacement(
                    event = EventPattern.ZoneChange(objects = and(or(artifact, creature), opponents), to = Zone.Battlefield),
                    modify = listOf(Modification.EntersTapped),
                ),
            ),
            affects = Affected.Objects(Objects.Self),
            abilityWord = "Venom Blast",
        ),
    )

    @Test
    fun theBlotWalkingGateway() = card(
        name = "The Blot, Walking Gateway",
        cost = "{3}{W}{B}",
        type = "Legendary Creature — Human Scientist Villain",
        pt = "4/4",
        text = """
            When The Blot enters, exile up to one target nonland permanent and up to one target nonland permanent card from a graveyard.
            When The Blot dies, put him on the bottom of his owner's library. If you do, return the exiled cards to their owners' hands.
        """,
    ) parsesTo abilities(
        triggered(
            on(EventPattern.ZoneChange(objects = ObjectFilter.Is(Objects.Self), to = Zone.Battlefield)),
            doing(Effect.Move(
                    objects = Objects.Union(listOf(target("permanent"), target("card"))),
                    to = Destination(Zone.Exile),
                    postBind = "exiled",
                )),
            doing(Effect.Remember(key = "exiled", objects = Objects.Bound("exiled"))),
            targets = listOf(
                slot("permanent", and(not(land), ObjectFilter.Category(ObjectCategory.Permanent)), min = 0),
                slot(
                    "card",
                    and(
                        not(land),
                        or(
                            artifact,
                            ObjectFilter.Type(CardType.Battle),
                            creature,
                            enchantment,
                            land,
                            ObjectFilter.Type(CardType.Planeswalker),
                        ),
                        inZone(Zone.Graveyard),
                    ),
                    min = 0,
                ),
            ),
        ),
        triggered(
            on(EventPattern.ZoneChange(objects = ObjectFilter.Is(Objects.Self), from = Zone.Battlefield, to = Zone.Graveyard)),
            Step.Pay(
                steps = listOf(
                    doing(Effect.Move(objects = Objects.EventObject(EventRole.After), to = Destination(zone = Zone.Library, libraryPosition = -1))),
                ),
                payer = Players.You,
                mandatory = true,
                bind = "paid",
            ),
            Step.If(
                condition = Condition.Did("paid"),
                then = listOf(doing(Effect.Move(objects = Objects.Remembered("exiled"), to = Destination(Zone.Hand)))),
            ),
        ),
    )

    @Test
    fun sunweaverAgileSpinner() = card(
        name = "Sunweaver, Agile Spinner",
        cost = "{3}{W/U}",
        type = "Legendary Creature — Spider Human Hero",
        pt = "3/2",
        text = """
            During your turn, Sunweaver has flying.
            When Sunweaver enters, search your library for an Aura or Equipment card, reveal it, put it into your hand, then shuffle.
        """,
    ) parsesTo abilities(
        static(has(Keyword.Flying), condition = Condition.YourTurn),
        triggered(
            on(EventPattern.ZoneChange(objects = ObjectFilter.Is(Objects.Self), to = Zone.Battlefield)),
            doing(Effect.Search(
                    library = Players.You,
                    filter = or(sub("Aura"), sub("Equipment")),
                    max = n(1),
                    reveal = true,
                    zones = listOf(Zone.Library),
                    bind = "found",
                    searcher = Players.You,
                )),
            doing(Effect.Move(objects = Objects.Bound("found"), to = Destination(Zone.Hand))),
            doing(Effect.Shuffle(Players.You)),
        ),
    )

    @Test
    fun strandSentinels() = card(
        name = "Strand Sentinels",
        cost = "{4}{G/W}",
        type = "Creature — Spider Hero",
        pt = "4/3",
        text = "When this creature enters, put a +1/+1 counter on each other creature you control.",
    ) parsesTo abilities(
        triggered(
            on(EventPattern.ZoneChange(objects = ObjectFilter.Is(Objects.Self), to = Zone.Battlefield)),
            doing(Effect.PutCounters(objects = Objects.Each(and(another, creature, you)), kind = p1p1, amount = n(1))),
        ),
    )

    @Test
    fun specterRuthlessAvenger() = card(
        name = "Specter, Ruthless Avenger",
        cost = "{1}{W}{U}",
        type = "Legendary Creature — Human Detective Hero",
        pt = "1/1",
        text = """
            Double strike
            Fear Gas — Specter can't be blocked.
        """,
    ) parsesTo abilities(
        Keyword.DoubleStrike,
        Ability.Static(
            mechanics = listOf(Mechanic.CantBeBlocked),
            affects = Affected.Objects(Objects.Self),
            abilityWord = "Fear Gas",
        ),
    )
}
