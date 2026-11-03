package arcanum.parser.sets.spm

import arcanum.api.*
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.cardFaces
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

/**
 * Marvel's Spider-Man chunk 13: artifacts and lands: an artifact becoming a 3/3 creature, Equipment making a Spider Hero, damage to a Spider destroying it, a land of the chosen basic land type (CR 305.7), Foods and Vehicles.
 */
class SpmChunk13ArtifactsAndLandsTest {

    @Test
    fun pretzelAndMustard() = card(
        name = "Pretzel and Mustard",
        cost = "{1}",
        type = "Artifact — Food",
        text = """
            Share — {W}, {T}, Sacrifice this artifact: Put a +1/+1 counter on up to one target creature. Draw a card. Activate only as a sorcery.
            Nosh — {2}, {T}, Sacrifice this artifact: You gain 3 life and draw a card.
        """,
    ) parsesTo abilities(
        Ability.Activated(
            cost = cost(mana("{W}"), tap, CostPart.Sacrifice(CostObjects.Self)),
            instructions = Instructions(
                targets = listOf(slot("creature", creature, min = 0)),
                steps = listOf(
                    doing(Effect.PutCounters(objects = target("creature"), kind = p1p1, amount = n(1))),
                    doing(Effect.Draw(players = Players.You, count = n(1))),
                ),
            ),
            rules = asSorcery,
            abilityWord = "Share",
        ),
        Ability.Activated(
            cost = cost(mana("{2}"), tap, CostPart.Sacrifice(CostObjects.Self)),
            instructions = Instructions(
                steps = listOf(
                    doing(Effect.GainLife(players = Players.You, amount = n(3))),
                    doing(Effect.Draw(players = Players.You, count = n(1))),
                ),
            ),
            rules = anyTime,
            abilityWord = "Nosh",
        ),
    )

    @Test
    fun grappleSSteelLimbs() = card(
        name = "Grapple's Steel Limbs",
        cost = "{1}",
        type = "Artifact — Equipment",
        text = """
            Whenever a creature you control with mana value 5 or greater enters, you may attach this Equipment to it.
            Equipped creature gets +4/+4.
            Equip {5}
        """,
    ) parsesTo abilities(
        triggered(
            on(EventPattern.ZoneChange(
                    objects = and(creature, you, ObjectFilter.ManaValue(comparison = Comparison.AtLeast, value = n(5))),
                    to = Zone.Battlefield,
                )),
            Step.May(
                steps = listOf(doing(Effect.Attach(objects = Objects.Self, to = Recipients(Objects.EventObject(EventRole.After))))),
                player = Players.You,
            ),
        ),
        static(pt(4, 4), affects = Objects.AttachedTo(Objects.Self)),
        Keyword.Equip(cost(mana("{5}"))),
    )

    @Test
    fun uncannyHeadstone() = card(
        name = "Uncanny Headstone",
        cost = "{2}",
        type = "Artifact",
        text = """
            When this artifact enters, draw a card.
            {1}{B}, Sacrifice this artifact: Mill four cards. You may put a creature card from among them into your hand. (To mill four cards, put the top four cards of your library into your graveyard.)
        """,
    ) parsesTo abilities(
        triggered(
            on(EventPattern.ZoneChange(objects = ObjectFilter.Is(Objects.Self), to = Zone.Battlefield)),
            doing(Effect.Draw(players = Players.You, count = n(1))),
        ),
        activated(
            cost(mana("{1}{B}"), CostPart.Sacrifice(CostObjects.Self)),
            doing(Effect.Mill(players = Players.You, count = n(4), postBind = "milled")),
            Step.Choose(
                choice = Choice.Objects(filter = creature, min = n(0), max = n(1), from = Objects.Bound("milled")),
                bind = "chosen",
                chooser = Players.You,
            ),
            doing(Effect.Move(objects = Objects.Bound("chosen"), to = Destination(Zone.Hand))),
        ),
    )

    @Test
    fun streetVendorWagon() = card(
        name = "Street Vendor Wagon",
        cost = "{3}",
        type = "Artifact",
        text = """
            When this artifact enters, create a Food token. (It's an artifact with "{2}, {T}, Sacrifice this token: You gain 3 life.")
            {T}: Add one mana of any color.
        """,
    ) parsesTo abilities(
        triggered(
            on(EventPattern.ZoneChange(objects = ObjectFilter.Is(Objects.Self), to = Zone.Battlefield)),
            doing(Effect.CreateToken(token = TokenSpec.Predefined("Food"), count = n(1), controller = Players.You)),
        ),
        activated(
            cost(tap),
            doing(Effect.AddMana(mana = ManaProduction(ManaKind.Choose(n(1))), players = Players.You)),
        ),
    )

    @Test
    fun cogitoThinkingEngine() = card(
        name = "Cogito, Thinking Engine",
        cost = "{4}",
        type = "Legendary Artifact Creature — Robot Villain",
        pt = "3/3",
        text = "At the beginning of combat on your turn, target non-Equipment artifact you control becomes an artifact creature with base power and toughness 3/3 until end of turn. Untap it.",
    ) parsesTo abilities(
        triggered(
            on(EventPattern.StepBegins(step = TurnStep.BeginningOfCombat, whose = PlayerFilter.You)),
            untilEndOfTurn(
                target("artifact"),
                Mechanic.SetTypes(listOf(CardType.Artifact, CardType.Creature)),
                Mechanic.SetPowerToughness(power = n(3), toughness = n(3)),
            ),
            doing(Effect.Untap(target("artifact"))),
            targets = listOf(slot("artifact", and(not(sub("Equipment")), artifact, you))),
        ),
    )

    @Test
    fun trafficReportChopper() = card(
        name = "Traffic Report Chopper",
        cost = "{3}",
        type = "Artifact Creature — Construct",
        pt = "1/1",
        text = """
            Flying
            When this creature enters, create a 1/1 green and white Human Citizen creature token.
        """,
    ) parsesTo abilities(
        Keyword.Flying,
        triggered(
            on(EventPattern.ZoneChange(objects = ObjectFilter.Is(Objects.Self), to = Zone.Battlefield)),
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
    fun commuterBarge() = card(
        name = "Commuter Barge",
        cost = "{3}",
        type = "Artifact — Vehicle",
        pt = "4/3",
        text = """
            Whenever this Vehicle attacks, you may pay {U}. When you do, another target attacking creature can't be blocked this turn.
            Crew 2 (Tap any number of creatures you control with total power 2 or more: This Vehicle becomes an artifact creature until end of turn.)
        """,
    ) parsesTo abilities(
        triggered(
            on(EventPattern.Attack(ObjectFilter.Is(Objects.Self))),
            Step.Pay(cost = cost(mana("{U}")), payer = Players.You, bind = "paid"),
            Step.If(
                condition = Condition.Did("paid"),
                then = listOf(
                    doing(Effect.ReflexiveTrigger(
                            Instructions(
                                targets = listOf(slot("creature", and(another, ObjectFilter.HasStatus(Status.Attacking), creature))),
                                steps = listOf(untilEndOfTurn(target("creature"), Mechanic.CantBeBlocked)),
                            ),
                        )),
                ),
            ),
        ),
        Keyword.Crew(2),
    )

    @Test
    fun crawlerDrone() = card(
        name = "Crawler Drone",
        cost = "{2}",
        type = "Artifact Creature — Spider Robot Scout",
        pt = "2/1",
        text = """
            Reach
            When this creature enters, you may search your library for a basic land card, reveal it, then shuffle and put that card on top.
        """,
    ) parsesTo abilities(
        Keyword.Reach,
        triggered(
            on(EventPattern.ZoneChange(objects = ObjectFilter.Is(Objects.Self), to = Zone.Battlefield)),
            Step.May(
                steps = listOf(
                    doing(Effect.Search(
                            library = Players.You,
                            filter = and(ObjectFilter.Super(Supertype.Basic), land),
                            max = n(1),
                            reveal = true,
                            zones = listOf(Zone.Library),
                            bind = "found",
                            searcher = Players.You,
                        )),
                    doing(Effect.Shuffle(library = Players.You, except = Objects.Bound("found"))),
                    doing(Effect.Move(objects = Objects.Bound("found"), to = Destination(zone = Zone.Library, libraryPosition = 0))),
                ),
                player = Players.You,
            ),
        ),
    )

    @Test
    fun webwheelRoadster() = card(
        name = "Webwheel Roadster",
        cost = "{3}",
        type = "Artifact — Vehicle",
        pt = "3/3",
        text = """
            Trample
            Whenever this Vehicle attacks or blocks, it gets +1/+1 until end of turn for each Spider you control.
            Crew 2
        """,
    ) parsesTo abilities(
        Keyword.Trample,
        triggered(
            Trigger.On(
                listOf(
                    EventPattern.Attack(ObjectFilter.Is(Objects.Self)),
                    EventPattern.Block(blocker = ObjectFilter.Is(Objects.Self), attacker = ObjectFilter.Any),
                ),
            ),
            untilEndOfTurn(
                Objects.Self,
                Mechanic.ModifyPowerToughness(
                    power = Value.Count(Objects.Each(and(sub("Spider"), you))),
                    toughness = Value.Count(Objects.Each(and(sub("Spider"), you))),
                ),
            ),
        ),
        Keyword.Crew(2),
    )

    @Test
    fun arachnicideSpiteEngine() = card(
        name = "Arachnicide, Spite Engine",
        cost = "{2}",
        type = "Legendary Artifact Creature — Human Villain",
        pt = "2/1",
        text = """
            Whenever Arachnicide deals damage to a Spider, destroy that creature.
            {6}, Exile this card from your graveyard: Create two tapped 1/1 colorless Robot artifact creature tokens with flying.
        """,
    ) parsesTo abilities(
        triggered(
            on(EventPattern.Damage(source = ObjectFilter.Is(Objects.Self), to = RecipientFilter(sub("Spider")))),
            doing(Effect.Destroy(Objects.EventObject(EventRole.Object))),
        ),
        activated(
            cost(mana("{6}"), CostPart.Exile(what = CostObjects.Self, from = Zone.Graveyard)),
            doing(Effect.CreateToken(
                    token = TokenSpec.Defined(
                        FaceDefinition(
                            name = "Robot Token",
                            colorIndicator = emptyList(),
                            types = listOf(CardType.Artifact, CardType.Creature),
                            subtypes = listOf(Subtype("Robot")),
                            power = PrintedNumber.Fixed(1),
                            toughness = PrintedNumber.Fixed(1),
                            abilities = listOf(Keyword.Flying),
                        ),
                    ),
                    count = n(2),
                    controller = Players.You,
                    tapped = true,
                )),
        ),
    )

    @Test
    fun webbedBodysuit() = card(
        name = "Webbed Bodysuit",
        cost = "{1}",
        type = "Artifact — Equipment",
        text = """
            Equipped creature gets +2/+2 and is a Spider Hero in addition to its other types.
            Equip {3} ({3}: Attach to target creature you control. Equip only as a sorcery.)
        """,
    ) parsesTo abilities(
        static(
            pt(2, 2),
            Mechanic.SetTypes(addSubtypes = listOf(SubtypeRef.Literal(Subtype("Spider")), SubtypeRef.Literal(Subtype("Hero")))),
            affects = Objects.AttachedTo(Objects.Self),
        ),
        Keyword.Equip(cost(mana("{3}"))),
    )

    @Test
    fun ironDemolitionBall() = card(
        name = "Iron Demolition Ball",
        cost = "{5}",
        type = "Artifact",
        text = """
            When this artifact enters, it deals 5 damage to target creature.
            {1}{R}, Discard this card: Destroy target artifact.
        """,
    ) parsesTo abilities(
        triggered(
            on(EventPattern.ZoneChange(objects = ObjectFilter.Is(Objects.Self), to = Zone.Battlefield)),
            doing(Effect.DealDamage(to = Recipients(target("creature")), amount = n(5), source = Objects.Self)),
            targets = listOf(slot("creature", creature)),
        ),
        activated(
            cost(mana("{1}{R}"), CostPart.Discard(CostObjects.Self)),
            doing(Effect.Destroy(target("artifact"))),
            targets = listOf(slot("artifact", artifact)),
        ),
    )

    @Test
    fun undergroundRailcar() = card(
        name = "Underground Railcar",
        cost = "{2}",
        type = "Artifact — Vehicle",
        pt = "3/1",
        text = """
            When this Vehicle enters, you may pay {G}. If you do, search your library for a basic land card, reveal it, put it into your hand, then shuffle.
            Crew 2 (Tap any number of creatures you control with total power 2 or more: This Vehicle becomes an artifact creature until end of turn.)
        """,
    ) parsesTo abilities(
        triggered(
            on(EventPattern.ZoneChange(objects = ObjectFilter.Is(Objects.Self), to = Zone.Battlefield)),
            Step.Pay(cost = cost(mana("{G}")), payer = Players.You, bind = "paid"),
            Step.If(
                condition = Condition.Did("paid"),
                then = listOf(
                    doing(Effect.Search(
                            library = Players.You,
                            filter = and(ObjectFilter.Super(Supertype.Basic), land),
                            max = n(1),
                            reveal = true,
                            zones = listOf(Zone.Library),
                            bind = "found",
                            searcher = Players.You,
                        )),
                    doing(Effect.Move(objects = Objects.Bound("found"), to = Destination(Zone.Hand))),
                    doing(Effect.Shuffle(Players.You)),
                ),
            ),
        ),
        Keyword.Crew(2),
    )

    @Test
    fun morningHeraldTower() = card(
        name = "Morning Herald Tower",
        cost = null,
        type = "Land",
        text = """
            {T}: Add {C}.
            {1}, {T}: Add one mana of any color.
            Smear Campaign — {1}, {T}: Target legendary creature gains menace until end of turn. Activate only as a sorcery.
        """,
    ) parsesTo abilities(
        activated(
            cost(tap),
            doing(Effect.AddMana(mana = ManaProduction(ManaKind.Fixed(ManaCost.parse("{C}")!!)), players = Players.You)),
        ),
        activated(
            cost(mana("{1}"), tap),
            doing(Effect.AddMana(mana = ManaProduction(ManaKind.Choose(n(1))), players = Players.You)),
        ),
        Ability.Activated(
            cost = cost(mana("{1}"), tap),
            instructions = Instructions(
                targets = listOf(slot("creature", and(legendary, creature))),
                steps = listOf(untilEndOfTurn(target("creature"), has(Keyword.Menace))),
            ),
            rules = asSorcery,
            abilityWord = "Smear Campaign",
        ),
    )

    @Test
    fun crossworldArchway() = card(
        name = "Crossworld Archway",
        cost = null,
        type = "Land",
        text = """
            As this land enters, choose a basic land type. Then you may pay 2 life. If you don't, it enters tapped.
            This land is the chosen type.
        """,
    ) parsesTo abilities(
        static(
            Mechanic.Replacement(
                event = EventPattern.ZoneChange(objects = ObjectFilter.Is(Objects.Self), to = Zone.Battlefield),
                modify = listOf(
                    Modification.AsEnters(
                        listOf(
                            Step.Choose(choice = Choice.BasicLandType, bind = "type", chooser = Players.You, remember = true),
                            Step.Pay(cost = cost(CostPart.PayLife(n(2))), payer = Players.You, bind = "paid"),
                            Step.If(condition = Condition.Not(Condition.Did("paid")), then = listOf(Step.Modify(Modification.EntersTapped))),
                        ),
                    ),
                ),
            ),
        ),
        static(Mechanic.SetTypes(addSubtypes = listOf(SubtypeRef.Remembered("type")))),
    )

    @Test
    fun forebodingSanitarium() = card(
        name = "Foreboding Sanitarium",
        cost = null,
        type = "Land",
        text = """
            This land enters tapped.
            {T}: Add {B} or {R}.
            {4}, {T}: Surveil 1. (Look at the top card of your library. You may put it into your graveyard.)
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
                    mana = ManaProduction(ManaKind.Choose(amount = n(1), colors = listOf(ColorRef.Literal(Color.Black), ColorRef.Literal(Color.Red)))),
                    players = Players.You,
                )),
        ),
        activated(cost(mana("{4}"), tap), doing(Effect.Surveil(players = Players.You, count = n(1)))),
    )

    @Test
    fun feralManor() = card(
        name = "Feral Manor",
        cost = null,
        type = "Land",
        text = """
            This land enters tapped.
            {T}: Add {R} or {G}.
            {4}, {T}: Surveil 1. (Look at the top card of your library. You may put it into your graveyard.)
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
                    mana = ManaProduction(ManaKind.Choose(amount = n(1), colors = listOf(ColorRef.Literal(Color.Red), ColorRef.Literal(Color.Green)))),
                    players = Players.You,
                )),
        ),
        activated(cost(mana("{4}"), tap), doing(Effect.Surveil(players = Players.You, count = n(1)))),
    )

    @Test
    fun menacingLair() = card(
        name = "Menacing Lair",
        cost = null,
        type = "Land",
        text = """
            This land enters tapped.
            {T}: Add {U} or {B}.
            {4}, {T}: Surveil 1. (Look at the top card of your library. You may put it into your graveyard.)
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
                    mana = ManaProduction(ManaKind.Choose(amount = n(1), colors = listOf(ColorRef.Literal(Color.Blue), ColorRef.Literal(Color.Black)))),
                    players = Players.You,
                )),
        ),
        activated(cost(mana("{4}"), tap), doing(Effect.Surveil(players = Players.You, count = n(1)))),
    )

    @Test
    fun culDeSacRefuge() = card(
        name = "Cul-de-sac Refuge",
        cost = null,
        type = "Land",
        text = """
            This land enters tapped.
            {T}: Add {G} or {W}.
            {4}, {T}: Surveil 1. (Look at the top card of your library. You may put it into your graveyard.)
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
                    mana = ManaProduction(ManaKind.Choose(amount = n(1), colors = listOf(ColorRef.Literal(Color.Green), ColorRef.Literal(Color.White)))),
                    players = Players.You,
                )),
        ),
        activated(cost(mana("{4}"), tap), doing(Effect.Surveil(players = Players.You, count = n(1)))),
    )

    @Test
    fun collegeQuadrangle() = card(
        name = "College Quadrangle",
        cost = null,
        type = "Land",
        text = """
            This land enters tapped.
            {T}: Add {W} or {U}.
            {4}, {T}: Surveil 1. (Look at the top card of your library. You may put it into your graveyard.)
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
                    mana = ManaProduction(ManaKind.Choose(amount = n(1), colors = listOf(ColorRef.Literal(Color.White), ColorRef.Literal(Color.Blue)))),
                    players = Players.You,
                )),
        ),
        activated(cost(mana("{4}"), tap), doing(Effect.Surveil(players = Players.You, count = n(1)))),
    )

    @Test
    fun bustlingSkyline() = card(
        name = "Bustling Skyline",
        cost = null,
        type = "Land",
        text = "{T}, Sacrifice this land: Search your library for a basic land card, put it onto the battlefield tapped, then shuffle.",
    ) parsesTo abilities(
        activated(
            cost(tap, CostPart.Sacrifice(CostObjects.Self)),
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
}
