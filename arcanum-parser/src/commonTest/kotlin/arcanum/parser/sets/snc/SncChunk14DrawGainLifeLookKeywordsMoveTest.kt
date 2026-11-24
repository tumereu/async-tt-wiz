package arcanum.parser.sets.snc

import arcanum.api.*
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.manaCost
import arcanum.parser.cube.parsesTo
import arcanum.parser.cube.whenThisEnters
import kotlin.test.Test

/** Streets of New Capenna chunk 14: draw, gaining abilities, life, keywords, look, modifying power and toughness, move. */
class SncChunk14DrawGainLifeLookKeywordsMoveTest {
    private fun look(n: Int) = doing(Effect.LookAt(Objects.LibraryCards(Players.You, n(n), fromTop = true), Players.You, bind = "seen"))
    private val bottomRandom = doing(Effect.Move(Objects.Except(Objects.Bound("seen"), Objects.Bound("chosen")),
        Destination(Zone.Library, libraryPosition = -1, randomOrder = true)))
    private val citizen = creatureToken("Citizen", listOf(Color.Green, Color.White), 1, 1)

    // CR 113.6m (works from the graveyard), 602.5d, 701.9 (discard)
    @Test
    fun maestroNovice() = card(
        name = "Maestro Novice",
        cost = "{2}{B}",
        type = "Creature — Human Citizen",
        pt = "3/1",
        text = """
            {4}{U/R}, Exile this card from your graveyard: Draw two cards, then discard a card.
        """,
    ) parsesTo abilities(
        activated(cost(mana("{4}{U/R}"), CostPart.Exile(CostObjects.Self, from = Zone.Graveyard)), draw(2), discard(1)),
    )

    // CR 508.1 (attacks), 122.1 (counters), 702.9 (flying)
    @Test
    fun cityAngel() = card(
        name = "City Angel",
        cost = "{2}{W}{U}",
        type = "Creature — Angel Soldier",
        pt = "3/1",
        text = """
            Flying
            Whenever you attack with one or more creatures with counters on them, draw a card.
        """,
    ) parsesTo abilities(
        Keyword.Flying,
        triggered(Trigger.On(EventPattern.Attack(and(creature, ObjectFilter.HasCounter(null, 1), you)), oneOrMore = true), draw(1)),
    )

    // CR 121.1, 702.15 (lifelink), 702.9 (flying)
    @Test
    fun skyUsher() = card(
        name = "Sky Usher",
        cost = "{1}{W}",
        type = "Creature — Bird Citizen",
        pt = "1/1",
        text = """
            Flying, lifelink
            {3}{W}: You and target opponent each draw a card.
        """,
    ) parsesTo abilities(
        Keyword.Flying,
        Keyword.Lifelink,
        activated(cost(mana("{3}{W}")),
            doing(Effect.Draw(Players.Each(PlayerFilter.Or(listOf(PlayerFilter.You, PlayerFilter.Is(Players.Target("opponent"))))), n(1))),
            targets = listOf(targetOpponent)),
    )

    // CR 611.3 (a static ability's condition), 702.12 (indestructible), 702.10 (haste)
    @Test
    fun crewLieutenant() = card(
        name = "Crew Lieutenant",
        cost = "{B}{R}{G}",
        type = "Creature — Human Warrior",
        pt = "4/2",
        text = """
            Haste
            This creature has indestructible as long as it entered this turn.
        """,
    ) parsesTo abilities(
        Keyword.Haste,
        static(has(Keyword.Indestructible), condition = Condition.ObjectMatches(Objects.Self, ObjectFilter.ThisTurn(
            EventPattern.ZoneChange(ObjectFilter.Is(Objects.Self), to = Zone.Battlefield)))),
    )

    // CR 602.5d, 113.6, 611.2 (until this card is cast from exile), 119.1
    @Test
    fun shatteredCherub() = card(
        name = "Shattered Cherub",
        cost = "{4}{W}{U}{B}",
        type = "Creature — Angel Rogue",
        pt = "4/4",
        text = """
            Flying
            When this creature enters, you gain 3 life.
            {2}, Exile this card from your hand: Target land gains "{T}: Add {W}, {U}, or {B}" until this card is cast from exile. You may cast this card for as long as it remains exiled.
        """,
    ) parsesTo abilities(
        Keyword.Flying,
        whenThisEnters(gainLife(3)),
        activated(cost(mana("{2}"), CostPart.Exile(CostObjects.Self, from = Zone.Hand, postBind = "exiled")),
            Step.Do(Effect.ApplyMechanics(listOf(Mechanic.GainAbilities(listOf(activated(cost(tap),
                Step.Do(Effect.AddMana(ManaProduction(ManaKind.Choose(n(1), listOf(ColorRef.Literal(Color.White), ColorRef.Literal(Color.Blue), ColorRef.Literal(Color.Black)))), Players.You)))))),
                Affected.Objects(target("land")), Duration.UntilCast(Objects.Bound("exiled")))),
            Step.Do(Effect.ApplyMechanics(listOf(Mechanic.MayPlay(Zone.Exile, PlayKind.Cast, holder = PlayerFilter.You)), Affected.Objects(Objects.Bound("exiled")),
                Duration.Indefinitely)),
            targets = listOf(slot("land", land))),
    )

    // CR 119.1, 603.6a
    @Test
    fun saloonWaiter() = card(
        name = "Saloon Waiter",
        cost = "{4}{W}",
        type = "Creature — Bird Citizen",
        pt = "3/3",
        text = """
            Flying
            When this creature enters, you gain 1 life for each other creature you control.
        """,
    ) parsesTo abilities(
        Keyword.Flying,
        whenThisEnters(doing(Effect.GainLife(Players.You, count(and(another, creature, you))))),
    )

    // CR 702.41a (affinity)
    @Test
    fun heavenlyOverseer() = card(
        name = "Heavenly Overseer",
        cost = "{5}{W}",
        type = "Creature — Angel Advisor",
        pt = "3/3",
        text = """
            Affinity for Citizens (This spell costs {1} less to cast for each Citizen you control.)
            Flying
        """,
    ) parsesTo abilities(
        Keyword.Affinity(sub("Citizen"), "Citizens"),
        Keyword.Flying,
    )

    // CR 701.20 (look), 401.4, 603.6c (dies)
    @Test
    fun brazenUpstart() = card(
        name = "Bold Upstart",
        cost = "{R}{G}{W}",
        type = "Creature — Elf Shaman",
        pt = "4/2",
        text = """
            Vigilance
            When this creature dies, look at the top five cards of your library. You may reveal a creature card from among them and put it into your hand. Put the rest on the bottom of your library in a random order.
        """,
    ) parsesTo abilities(
        Keyword.Vigilance,
        triggered(diesSelf,
            look(5),
            Step.Choose(Choice.Objects(creature, n(0), n(1), from = Objects.Bound("seen")), bind = "chosen", chooser = Players.You),
            doing(Effect.Reveal(Objects.Bound("chosen"))),
            move(Objects.Bound("chosen"), Zone.Hand),
            bottomRandom),
    )

    // CR 701.20 (look), 603.2 (upkeep trigger), 401.4
    @Test
    fun cabarettiAscent() = card(
        name = "Cabaretti Ascent",
        cost = "{R}{G}{W}",
        type = "Enchantment",
        text = """
            At the beginning of your upkeep, look at the top card of your library. If it's a creature or planeswalker card, you may reveal it and put it into your hand. If you don't put the card into your hand, you may put it on the bottom of your library.
        """,
    ) parsesTo abilities(
        triggered(stepBegins(TurnStep.Upkeep, PlayerFilter.You),
            Step.Do(Effect.LookAt(Objects.LibraryCards(Players.You, n(1), fromTop = true), Players.You, bind = "top")),
            Step.If(Condition.ObjectMatches(Objects.Bound("top"), or(creature, ObjectFilter.Type(CardType.Planeswalker))), then = listOf(
                Step.Pay(steps = listOf(Step.Do(Effect.Reveal(Objects.Bound("top"))), Step.Do(Effect.Move(Objects.Bound("top"), Destination(Zone.Hand)))),
                    payer = Players.You, bind = "kept"))),
            Step.If(Condition.Not(Condition.Did("kept")), then = listOf(
                Step.May(listOf(Step.Do(Effect.Move(Objects.Bound("top"), Destination(Zone.Library, libraryPosition = -1)))), Players.You)))),
    )

    // CR 701.20 (look), 111.1 (token)
    @Test
    fun warmReception() = card(
        name = "Warm Reception",
        cost = "{2}{G}",
        type = "Instant",
        text = "Look at the top five cards of your library. You may reveal a creature card from among them and put it into your hand. Put the rest on the bottom of your library in a random order. Create a 1/1 green and white Citizen creature token.",
    ) parsesTo abilities(
        spell(
            look(5),
            Step.Choose(Choice.Objects(creature, n(0), n(1), from = Objects.Bound("seen")), bind = "chosen", chooser = Players.You),
            doing(Effect.Reveal(Objects.Bound("chosen"))),
            move(Objects.Bound("chosen"), Zone.Hand),
            bottomRandom,
            create(citizen)),
    )

    // CR 121.1, 119.3, 119.1, 603.2
    @Test
    fun quezaLike() = card(
        name = "Quillo, Augur of Pain",
        cost = "{1}{W}{U}{B}",
        type = "Legendary Creature — Octopus Advisor",
        pt = "3/4",
        text = "Whenever you draw a card, target opponent loses 1 life and you gain 1 life.",
    ) parsesTo abilities(
        triggered(Trigger.On(EventPattern.Draw(PlayerFilter.You)), loseLife(1, Players.Target("opponent")), gainLife(1), targets = listOf(targetOpponent)),
    )

    // CR 613.4c (+1/+0 for other Citizens), 508.1, 111.1
    @Test
    fun darlingOfTheCrowds() = card(
        name = "Darling of the Crowds",
        cost = "{2}{G}{W}",
        type = "Creature — Elf Citizen",
        pt = "2/4",
        text = """
            Other Citizens you control get +1/+0.
            Whenever this creature attacks, create a 1/1 green and white Citizen creature token.
        """,
    ) parsesTo abilities(
        static(pt(1, 0), affects = Objects.Each(and(another, sub("Citizen"), you))),
        triggered(attacksSelf, create(citizen)),
    )

    // CR 401.4 (the owner chooses top or bottom), 400.3
    @Test
    fun endlessDetourLike() = card(
        name = "Endless Detour Test",
        cost = "{G}{W}{U}",
        type = "Instant",
        text = "The owner of target spell, nonland permanent, or card in a graveyard puts it on their choice of the top or bottom of their library.",
    ) parsesTo abilities(
        spell(
            Step.Choose(Choice.Option(listOf("top", "bottom")), bind = "position", chooser = Players.OwnerOf(target("card"))),
            Step.If(Condition.Chose("position", "top"),
                then = listOf(move(target("card"), Zone.Library, position = 0)),
                otherwise = listOf(move(target("card"), Zone.Library, position = -1))),
            targets = listOf(slot("card", or(spellFilter, and(nonland, permanentFilter), and(cardFilter, inZone(Zone.Graveyard))))),
        ),
    )

    // CR 603.6a, 400.7, 508.1 (can't attack or block), 611.2b (for as long as)
    @Test
    fun extractionExpert() = card(
        name = "Extraction Expert",
        cost = "{2}{W}",
        type = "Creature — Human Rogue",
        pt = "3/2",
        text = """
            Lifelink
            When this creature enters, return target creature card with mana value 2 or less from your graveyard to the battlefield. That creature can't attack or block for as long as you control this creature.
        """,
    ) parsesTo abilities(
        Keyword.Lifelink,
        triggered(entersSelf,
            move(target("card"), Zone.Battlefield, postBind = "moved-card"),
            doing(Effect.ApplyMechanics(listOf(Mechanic.CantAttack(), Mechanic.CantBlock(ObjectFilter.Any)), Affected.Objects(Objects.Bound("moved-card")),
                Duration.AsLongAs(Condition.ObjectMatches(Objects.Self, you)))),
            targets = listOf(slot("card", inYourGraveyard(creature, manaValue(Comparison.AtMost, 2))))),
    )

    // CR 700.2 (modes), 400.3, 701.5 (counter), 701.8 (destroy)
    @Test
    fun obscuraCharmLike() = card(
        name = "Obscura Charm Test",
        cost = "{W}{U}{B}",
        type = "Instant",
        text = """
            Choose one —
            • Return target multicolored permanent card with mana value 3 or less from your graveyard to the battlefield tapped.
            • Counter target instant or sorcery spell.
            • Destroy target creature or planeswalker with mana value 3 or less.
        """,
    ) parsesTo abilities(
        Ability.Spell(Instructions(modes = modes(
            mode(move(target("card"), Zone.Battlefield, tapped = true),
                targets = listOf(slot("card", and(ObjectFilter.Multicolored, permanentCard, manaValue(Comparison.AtMost, 3),
                    inZone(Zone.Graveyard), ObjectFilter.OwnedBy(PlayerFilter.You))))),
            mode(doing(Effect.Counter(target("spell"))),
                targets = listOf(slot("spell", and(or(instant, sorcery), spellFilter)))),
            mode(destroy(target("permanent")),
                targets = listOf(slot("permanent", and(or(creature, ObjectFilter.Type(CardType.Planeswalker)), manaValue(Comparison.AtMost, 3))))),
        ))),
    )
}
