package arcanum.parser.sets.vow

import arcanum.api.*
import arcanum.parser.cube.abilities
import arcanum.parser.cube.anyOneColor
import arcanum.parser.cube.card
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

/*
 * VOW chunk 14: abilities gained by a tribe or under a condition, life gain (a spell cast trigger, the greatest toughness), looking at the top
 * of the library, life loss with Blood tokens, power and toughness changes by condition or turn, and moving cards between zones.
 */
class VowChunk14GainLifeLookPtMoveTest {
    private val zombie = sub("Zombie")
    private val spirit = sub("Spirit")
    private val kindOfZombie = Objects.Bound("exiled")

    // CR 613.1f (abilities granted to a tribe), 702.9 (flying); CR 702.9 area: X is the sacrificed creature's toughness
    @Test
    fun geralfVisionaryStitcher() = card(
        name = "Gerhard, Grisly Stitcher", cost = "{2}{U}", type = "Legendary Creature — Human Wizard", pt = "1/4",
        text = """
            Zombies you control have flying.
            {U}, {T}, Sacrifice another nontoken creature: Create an X/X blue Zombie creature token, where X is the sacrificed creature's toughness.
        """,
    ) parsesTo abilities(
        static(has(Keyword.Flying), affects = Objects.Each(and(zombie, you))),
        activated(
            cost(mana("{U}"), tap, CostPart.Sacrifice(CostObjects.Choose(and(another, not(token), creature), count = n(1)), preBind = "sacrificed")),
            doing(Effect.CreateToken(
                TokenSpec.Defined(FaceDefinition("Zombie Token", colorIndicator = listOf(Color.Blue), types = listOf(CardType.Creature),
                    subtypes = listOf(Subtype("Zombie")), power = PrintedNumber.X, toughness = PrintedNumber.X),
                    power = toughness(Objects.Bound("sacrificed")), toughness = toughness(Objects.Bound("sacrificed"))),
                n(1), Players.You)),
        ),
    )

    // CR 611.3a (a condition on a count), 613.1f; CR 111.4: the token's characteristic-defining power and toughness count Spirits
    @Test
    fun hallowedHaunting() = card(
        name = "Hallowed Vigil", cost = "{2}{W}{W}", type = "Enchantment",
        text = """
            As long as you control seven or more enchantments, creatures you control have flying and vigilance.
            Whenever you cast an enchantment spell, create a white Spirit Cleric creature token with "This token's power and toughness are each equal to the number of Spirits you control."
        """,
    ) parsesTo abilities(
        static(has(Keyword.Flying, Keyword.Vigilance), affects = Objects.Each(and(creature, you)),
            condition = Condition.Exists(and(enchantment, you), atLeast = 7)),
        triggered(youCast(enchantment), create(TokenSpec.Defined(FaceDefinition("Spirit Cleric Token", colorIndicator = listOf(Color.White),
            types = listOf(CardType.Creature), subtypes = listOf(Subtype("Spirit"), Subtype("Cleric")),
            power = PrintedNumber.Star(), toughness = PrintedNumber.Star(),
            abilities = listOf(static(Mechanic.SetPowerToughness(count(and(spirit, you)), count(and(spirit, you))), characteristicDefining = true)))))),
    )

    // CR 601.2i, 603.2 (cast triggers), 119.1
    @Test
    fun dawnhartGeist() = card(
        name = "Dawnmoor Phantom", cost = "{1}{W}", type = "Creature — Spirit Warlock", pt = "1/3",
        text = "Whenever you cast an enchantment spell, you gain 2 life.",
    ) parsesTo abilities(
        triggered(youCast(enchantment), gainLife(2)),
    )

    // CR 119.1, 208.1: the greatest toughness among the other creatures you control
    @Test
    fun flourishingHunter() = card(
        name = "Thriving Stalker", cost = "{4}{G}{G}", type = "Creature — Wolf Spirit", pt = "6/6",
        text = "When this creature enters, you gain life equal to the greatest toughness among other creatures you control.",
    ) parsesTo abilities(
        triggered(entersSelf, doing(Effect.GainLife(Players.You, Value.Of(NumericProperty.Toughness, Objects.Each(and(another, creature, you)), Combine.Greatest)))),
    )

    // CR 701.20a (look at), 701.20 (put some onto the battlefield tapped), 401.4 (bottom in a random order)
    @Test
    fun cartographersSurvey() = card(
        name = "Surveyor's Reckoning", cost = "{3}{G}", type = "Sorcery",
        text = "Look at the top seven cards of your library. Put up to two land cards from among them onto the battlefield tapped. Put the rest on the bottom of your library in a random order.",
    ) parsesTo abilities(
        spell(
            doing(Effect.LookAt(Objects.LibraryCards(Players.You, n(7), fromTop = true), Players.You, bind = "seen")),
            Step.Choose(Choice.Objects(land, n(0), n(2), from = Objects.Bound("seen")), bind = "chosen", chooser = Players.You),
            move(Objects.Bound("chosen"), Zone.Battlefield, tapped = true),
            doing(Effect.Move(Objects.Except(Objects.Bound("seen"), Objects.Bound("chosen")), Destination(Zone.Library, libraryPosition = -1, randomOrder = true))),
        ),
    )

    // CR 119.3 (life loss), 119.1, 111.10g (Blood tokens)
    @Test
    fun vampiresKiss() = card(
        name = "Crimson Embrace", cost = "{1}{B}", type = "Sorcery",
        text = """Target player loses 2 life and you gain 2 life. Create two Blood tokens. (They're artifacts with "{1}, {T}, Discard a card, Sacrifice this token: Draw a card.")""",
    ) parsesTo abilities(
        spell(
            doing(Effect.LoseLife(Players.Target("player"), n(2))),
            gainLife(2),
            create(blood, n(2)),
            targets = listOf(TargetSlot("player", TargetKind.Players(PlayerFilter.Any), min = n(1), max = n(1))),
        ),
    )

    // CR 611.3a (a condition on a graveyard count), 613.4c, 605.1a (a mana ability), 106.1b (any one color)
    @Test
    fun reclusiveTaxidermist() = card(
        name = "Hermit Preserver", cost = "{1}{G}", type = "Creature — Human Druid", pt = "1/2",
        text = """
            This creature gets +3/+2 as long as there are four or more creature cards in your graveyard.
            {T}: Add one mana of any color.
        """,
    ) parsesTo abilities(
        static(pt(3, 2), condition = Condition.Exists(inYourGraveyard(creature), atLeast = 4)),
        activated(cost(tap), doing(Effect.AddMana(ManaProduction(anyOneColor(1)), Players.You))),
    )

    // CR 613.4c, 611.3a: only during its controller's turn
    @Test
    fun sporebackWolf() = card(
        name = "Mossmane Wolf", cost = "{1}{G}", type = "Creature — Wolf", pt = "2/2",
        text = "During your turn, this creature gets +0/+2.",
    ) parsesTo abilities(
        static(pt(0, 2), condition = Condition.YourTurn),
    )

    // CR 700.2 (modes), 406.2 (exile), 701.13 (the opponent chooses the cards they exile)
    @Test
    fun aimForTheHead() = card(
        name = "Aim for the Skull", cost = "{2}{B}", type = "Sorcery",
        text = """
            Choose one —
            • Exile target Zombie.
            • Target opponent exiles two cards from their hand.
        """,
    ) parsesTo abilities(
        Ability.Spell(Instructions(modes = modes(
            mode(move(target("Zombie"), Zone.Exile), targets = listOf(slot("Zombie", zombie))),
            mode(
                Step.Choose(Choice.Objects(and(inZone(Zone.Hand), ObjectFilter.OwnedBy(PlayerFilter.Is(Players.Target("opponent")))), n(2), n(2)),
                    bind = "chosen", chooser = Players.Target("opponent")),
                move(Objects.Bound("chosen"), Zone.Exile), targets = listOf(targetOpponent)),
        ))),
    )

    // CR 603.4 (an intervening "if" on life gained this turn), 115.1b (up to one target)
    @Test
    fun courierBat() = card(
        name = "Dispatch Bat", cost = "{2}{B}", type = "Creature — Bat", pt = "2/2",
        text = """
            Flying
            When this creature enters, if you gained life this turn, return up to one target creature card from your graveyard to your hand.
        """,
    ) parsesTo abilities(
        Keyword.Flying,
        triggered(entersSelf, move(target("cards"), Zone.Hand), targets = listOf(slot("cards", inYourGraveyard(creature), min = 0)),
            interveningIf = Condition.HappenedThisTurn(EventPattern.GainLife(PlayerFilter.You), atLeast = 1, measure = Measure.Events)),
    )

    // CR 608.2c (a later instruction depends on what was exiled), 119.3, 119.1; the exile is of up to one target card
    @Test
    fun diregrafScavenger() = card(
        name = "Gravemoor Scavenger", cost = "{3}{B}", type = "Creature — Zombie Bear", pt = "2/3",
        text = """
            Deathtouch (Any amount of damage this deals to a creature is enough to destroy it.)
            When this creature enters, exile up to one target card from a graveyard. If a creature card was exiled this way, each opponent loses 2 life and you gain 2 life.
        """,
    ) parsesTo abilities(
        Keyword.Deathtouch,
        triggered(entersSelf,
            doing(Effect.Move(target("card"), Destination(Zone.Exile), postBind = "exiled")),
            ifThen(Condition.ObjectMatches(kindOfZombie, creature),
                loseLife(2, Players.Each(PlayerFilter.Opponent)), gainLife(2)),
            targets = listOf(slot("card", and(ObjectFilter.Category(ObjectCategory.Card), inZone(Zone.Graveyard)), min = 0))),
    )

    // CR 701.9 (discard), 603.12 (reflexive trigger), 603.6 (a card moving to the graveyard from the hand)
    @Test
    fun edgarsAwakening() = card(
        name = "Awakening of Ashmoor", cost = "{3}{B}{B}", type = "Sorcery",
        text = """
            Return target creature card from your graveyard to the battlefield.
            When you discard this card, you may pay {B}. When you do, return target creature card from your graveyard to your hand.
        """,
    ) parsesTo abilities(
        spell(move(target("card"), Zone.Battlefield), targets = listOf(slot("card", inYourGraveyard(creature)))),
        triggered(on(EventPattern.Discard(ObjectFilter.Is(Objects.Self), PlayerFilter.You)),
            Step.Pay(cost(mana("{B}")), payer = Players.You, bind = "paid"),
            ifThen(Condition.Did("paid"),
                reflexive(move(target("card"), Zone.Hand), targets = listOf(slot("card", inYourGraveyard(creature)))))),
    )

    // CR 115.1b (each "up to one" target is chosen independently), 400.3 (to its owner's hand), 406.2 (exile itself)
    @Test
    fun retrieve() = card(
        name = "Reclaim Remains", cost = "{2}{G}", type = "Sorcery",
        text = "Return up to one target creature card and up to one target noncreature permanent card from your graveyard to your hand. Exile Reclaim Remains.",
    ) parsesTo abilities(
        spell(
            doing(Effect.Move(Objects.Union(listOf(target("creature card"), target("noncreature permanent card"))), Destination(Zone.Hand))),
            move(Objects.Self, Zone.Exile),
            targets = listOf(
                slot("creature card", inYourGraveyard(creature), min = 0),
                slot("noncreature permanent card", inYourGraveyard(not(creature), permanentCard), min = 0),
            ),
        ),
    )

    // CR 400.3, 110.4a: all the land cards in the graveyard, entering tapped
    @Test
    fun splendidReclamation() = card(
        name = "Grand Restoration", cost = "{3}{G}", type = "Sorcery",
        text = "Return all land cards from your graveyard to the battlefield tapped.",
    ) parsesTo abilities(
        spell(move(Objects.Each(inYourGraveyard(land)), Zone.Battlefield, tapped = true)),
    )
}
