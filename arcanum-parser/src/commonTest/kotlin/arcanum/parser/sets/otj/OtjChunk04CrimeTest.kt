package arcanum.parser.sets.otj

import arcanum.api.*
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

/*
 * OTJ chunk 04: "Whenever you commit a crime" (CR 700.13: you cast a spell, activate an ability or put a triggered ability on the Stack
 * that targets an opponent, something an opponent controls, or a card in an opponent's graveyard).
 */
class OtjChunk04CrimeTest {
    private val loot = CounterKind.Named("loot")
    private val paid = Condition.Did("paid")
    private fun mayPay(cost: String) = Step.Pay(manaCost(cost), payer = Players.You, bind = "paid")
    private val eachOpponentLoses1 = loseLife(1, Players.Each(PlayerFilter.Opponent))

    // CR 700.13, 603.2h
    @Test
    fun rustlersStrongbox() = card(
        name = "Rustler's Strongbox",
        cost = "{3}",
        type = "Artifact",
        text = """
            Whenever you commit a crime, put a loot counter on this artifact. This ability triggers only once each turn. (Targeting opponents, anything they control, and/or cards in their graveyards is a crime.)
            {T}: Add one mana of any color.
            {2}, {T}, Remove two loot counters from this artifact: Draw a card.
        """,
    ) parsesTo abilities(
        crime(counters(Objects.Self, kind = loot), perTurn = 1),
        tapForAnyColor,
        activated(cost(mana("{2}"), tap, CostPart.RemoveCounters(loot, n(2), CostObjects.Self)), draw(1)),
    )

    // CR 700.13, 603.2h
    @Test
    fun bloodcoinGrifter() = card(
        name = "Bloodcoin Grifter",
        cost = "{1}{B}",
        type = "Creature — Vampire Rogue",
        text = """
            Whenever you commit a crime, put a +1/+1 counter on this creature. This ability triggers only once each turn. (Targeting opponents, anything they control, and/or cards in their graveyards is a crime.)
            {3}{B}: Target opponent loses 1 life and you gain 1 life.
        """,
        pt = "1/1",
    ) parsesTo abilities(
        crime(counters(Objects.Self), perTurn = 1),
        activated(manaCost("{3}{B}"), loseLife(1, Players.Target("opponent")), gainLife(1), targets = listOf(targetOpponent)),
    )

    // CR 604.3 (its power is a characteristic-defining ability), 700.13, 608.2c
    @Test
    fun quickdrawSavant() = card(
        name = "Quickdraw Savant",
        cost = "{1}{U}",
        type = "Creature — Human Advisor",
        text = """
            Flying, vigilance
            Quickdraw Savant's power is equal to the number of cards you've drawn this turn.
            Whenever you commit a crime, you may draw a card. If you do, discard a card. This ability triggers only once each turn.
        """,
        pt = "*/3",
    ) parsesTo abilities(
        Keyword.Flying,
        Keyword.Vigilance,
        static(Mechanic.SetPowerToughness(Value.EventsThisTurn(EventPattern.Draw(PlayerFilter.You), Measure.Events), null), characteristicDefining = true),
        crime(Step.Pay(steps = listOf(draw(1)), payer = Players.You, bind = "paid"), ifThen(paid, discard(1)), perTurn = 1),
    )

    // CR 113.6m (it triggers only from the graveyard), 700.13, 118.12
    @Test
    fun desertedProspector() = card(
        name = "Deserted Prospector",
        cost = "{B}",
        type = "Creature — Skeleton Rogue",
        text = """
            This creature can't block.
            Whenever you commit a crime, you may pay {B}. If you do, return this card from your graveyard to the battlefield. (Targeting opponents, anything they control, and/or cards in their graveyards is a crime.)
        """,
        pt = "2/2",
    ) parsesTo abilities(
        static(Mechanic.CantBlock(attackers = ObjectFilter.Any)),
        crime(mayPay("{B}"), ifThen(paid, move(Objects.Self, Zone.Battlefield, postBind = "returned")), functionsIn = listOf(Zone.Graveyard)),
    )

    // CR 700.13, 401.4 (the rest in a random order)
    @Test
    fun ridgelineSpotter() = card(
        name = "Ridgeline Spotter",
        cost = "{2}{G}",
        type = "Creature — Human Rogue",
        text = """
            Reach
            Whenever you commit a crime, look at the top five cards of your library. You may put a land card from among them onto the battlefield tapped. Put the rest on the bottom of your library in a random order. This ability triggers only once each turn. (Targeting opponents, anything they control, and/or cards in their graveyards is a crime.)
        """,
        pt = "3/3",
    ) parsesTo abilities(
        Keyword.Reach,
        crime(
            doing(Effect.LookAt(Objects.LibraryCards(Players.You, n(5), fromTop = true), bind = "looked", players = Players.You)),
            Step.Choose(Choice.Objects(land, n(0), n(1), from = Objects.Bound("looked")), bind = "chosen", chooser = Players.You),
            move(Objects.Bound("chosen"), Zone.Battlefield, tapped = true),
            doing(Effect.Move(Objects.Except(Objects.Bound("looked"), Objects.Bound("chosen")), Destination(Zone.Library, libraryPosition = -1, randomOrder = true))),
            perTurn = 1,
        ),
    )

    // CR 700.13, 603.2h
    @Test
    fun thornbackRustler() = card(
        name = "Thornback Rustler",
        cost = "{1}{G}",
        type = "Creature — Plant Rogue",
        text = """
            {T}: Add one mana of any color.
            Whenever you commit a crime, untap this creature. This ability triggers only once each turn. (Targeting opponents, anything they control, and/or cards in their graveyards is a crime.)
        """,
        pt = "1/1",
    ) parsesTo abilities(
        tapForAnyColor,
        crime(untapIt(Objects.Self), perTurn = 1),
    )

    // CR 700.13, 603.6a
    @Test
    fun shakedownRacket() = card(
        name = "Shakedown Racket",
        cost = "{1}{U}{B}",
        type = "Enchantment",
        text = """
            When this enchantment enters, each opponent loses 1 life, you gain 1 life, and you draw a card.
            Whenever you commit a crime, you may return this enchantment to its owner's hand. (It returns only from the battlefield. Targeting opponents, anything they control, and/or cards in their graveyards is a crime.)
        """,
    ) parsesTo abilities(
        triggered(entersSelf, eachOpponentLoses1, gainLife(1), draw(1)),
        crime(Step.May(listOf(move(Objects.Self, Zone.Hand)), Players.You)),
    )

    // CR 700.13, 707.12 (a copy of a card in exile, cast paying its costs), 608.2c
    @Test
    fun varrikThePenitent() = card(
        name = "Varrik, the Penitent",
        cost = "{1}{B}{B}",
        type = "Legendary Creature — Human Warlock",
        text = """
            Whenever you commit a crime, exile up to one target black card from your graveyard and copy it. You may cast the copy. If you do, you lose 2 life. (Targeting opponents, anything they control, and/or cards in their graveyards is a crime. Copies of permanent spells become tokens.)
        """,
        pt = "3/3",
    ) parsesTo abilities(
        crime(
            move(target("card"), Zone.Exile, postBind = "exiled"),
            doing(Effect.CopyCard(Objects.Bound("exiled"), owner = Players.You, bind = "copies")),
            Step.ForEach(Each.Objects(Objects.Bound("copies")), bind = "copy", steps = listOf(
                Step.Pay(steps = listOf(doing(Effect.Cast(Objects.Bound("copy"), PlayKind.Cast, caster = Players.You))), payer = Players.You, bind = "cast"),
                ifThen(Condition.Did("cast"), loseLife(2)))),
            targets = listOf(slot("card", inYourGraveyard(ObjectFilter.OfColor(ColorRef.Literal(Color.Black))), min = 0)),
        ),
    )

    // CR 700.13, 707.2, 613.1a (a copy until end of turn), 608.2c
    @Test
    fun veskBorrowedFace() = card(
        name = "Vesk, Borrowed Face",
        cost = "{1}{U}{B}",
        type = "Legendary Creature — Shapeshifter",
        text = """
            Whenever you commit a crime, put a +1/+1 counter on Vesk. Then you may exile a card from a graveyard. If a creature card was exiled this way, you may have Vesk become a copy of that card until end of turn. This ability triggers only once each turn. (Targeting opponents, anything they control, and/or cards in their graveyards is a crime.)
        """,
        pt = "1/4",
    ) parsesTo abilities(
        crime(
            counters(Objects.Self),
            Step.Choose(Choice.Objects(and(cardFilter, inZone(Zone.Graveyard)), n(0), n(1)), bind = "chosen", chooser = Players.You),
            move(Objects.Bound("chosen"), Zone.Exile, postBind = "exiled"),
            ifThen(Condition.ObjectMatches(Objects.Bound("exiled"), creature),
                Step.May(listOf(doing(Effect.ApplyMechanics(listOf(Mechanic.CopyOf(Objects.Bound("exiled"))), Affected.Objects(Objects.Self), Duration.EndOfTurn))),
                    Players.You)),
            perTurn = 1,
        ),
    )

    // CR 700.13, 118.12
    @Test
    fun corvinaDealerOfRuin() = card(
        name = "Corvina, Dealer of Ruin",
        cost = "{U}{B}{R}",
        type = "Legendary Creature — Human Rogue",
        text = """
            Whenever you commit a crime, you may pay {1}. If you do, look at the top two cards of your library. Put one of them into your hand and the other into your graveyard. (Targeting opponents, anything they control, and/or cards in their graveyards is a crime.)
        """,
        pt = "3/4",
    ) parsesTo abilities(
        crime(mayPay("{1}"), ifThen(paid,
            doing(Effect.LookAt(Objects.LibraryCards(Players.You, n(2), fromTop = true), Players.You, bind = "looked")),
            Step.Choose(Choice.Objects(ObjectFilter.Any, n(1), n(1), from = Objects.Bound("looked")), bind = "chosen", chooser = Players.You),
            move(Objects.Bound("chosen"), Zone.Hand),
            move(Objects.Except(Objects.Bound("looked"), Objects.Bound("chosen")), Zone.Graveyard))),
    )

    // CR 700.13, 603.2 ("during your turn" is checked as it would trigger)
    @Test
    fun overeagerEnforcer() = card(
        name = "Overeager Enforcer",
        cost = "{4}{B}",
        type = "Creature — Ogre Mercenary",
        text = """
            Whenever you commit a crime during your turn, this creature gains indestructible until end of turn. (Targeting opponents, anything they control, and/or cards in their graveyards is a crime. Damage and effects that say "destroy" don't destroy a creature with indestructible.)
        """,
        pt = "5/4",
    ) parsesTo abilities(
        crime(gains(Objects.Self, Keyword.Indestructible), triggersOnlyIf = Condition.YourTurn),
    )

    // CR 700.13, 608.2d (the choice is made as it resolves)
    @Test
    fun rattlecoilChemist() = card(
        name = "Rattlecoil Chemist",
        cost = "{2}{B}",
        type = "Creature — Gorgon Warlock",
        text = """
            Deathtouch
            Whenever you commit a crime, target creature you control gains your choice of menace or lifelink until end of turn. (Targeting opponents, anything they control, and/or cards in their graveyards is a crime.)
        """,
        pt = "3/2",
    ) parsesTo abilities(
        Keyword.Deathtouch,
        crime(Step.Choose(Choice.Option(listOf("menace", "lifelink")), bind = "keyword", chooser = Players.You),
            ifThen(Condition.Chose("keyword", "menace"), gains(target("creature"), Keyword.Menace), otherwise = listOf(gains(target("creature"), Keyword.Lifelink))),
            targets = listOf(slot("creature", creatureYouControl))),
    )

    // CR 700.13, 603.2h
    @Test
    fun crowOfIllTidings() = card(
        name = "Crow of Ill Tidings",
        cost = "{1}{B}",
        type = "Creature — Bird",
        text = """
            Flying
            Whenever you commit a crime, each opponent loses 1 life and you gain 1 life. This ability triggers only once each turn. (Targeting opponents, anything they control, and/or cards in their graveyards is a crime.)
        """,
        pt = "1/2",
    ) parsesTo abilities(
        Keyword.Flying,
        crime(eachOpponentLoses1, gainLife(1), perTurn = 1),
    )

    // CR 700.13, 603.2h, 613.1f
    @Test
    fun ezrinFreshFang() = card(
        name = "Ezrin, Fresh Fang",
        cost = "{1}{B}",
        type = "Legendary Creature — Vampire Rogue",
        text = """
            Whenever you commit a crime, put a +1/+1 counter on Ezrin. This ability triggers only once each turn. (Targeting opponents, anything they control, and/or cards in their graveyards is a crime.)
            As long as Ezrin has four or more +1/+1 counters on it, it has menace and lifelink.
        """,
        pt = "2/2",
    ) parsesTo abilities(
        crime(counters(Objects.Self), perTurn = 1),
        static(has(Keyword.Menace, Keyword.Lifelink), condition = Condition.ObjectMatches(Objects.Self, ObjectFilter.HasCounter(p1p1, atLeast = 4))),
    )
}
