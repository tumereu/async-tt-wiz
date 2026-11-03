package arcanum.parser.sets.sos

import arcanum.api.Ability
import arcanum.api.Choice
import arcanum.api.Combine
import arcanum.api.Comparison
import arcanum.api.Condition
import arcanum.api.CostObjects
import arcanum.api.CostPart
import arcanum.api.Destination
import arcanum.api.Each
import arcanum.api.Effect
import arcanum.api.Instructions
import arcanum.api.Keyword
import arcanum.api.ManaCost
import arcanum.api.ManaKind
import arcanum.api.ManaProduction
import arcanum.api.Mechanic
import arcanum.api.Mode
import arcanum.api.Modes
import arcanum.api.NumericProperty
import arcanum.api.ObjectFilter
import arcanum.api.Objects
import arcanum.api.PlayerFilter
import arcanum.api.Players
import arcanum.api.Step
import arcanum.api.TargetKind
import arcanum.api.TargetSlot
import arcanum.api.Value
import arcanum.api.Zone
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

/**
 * Secrets of Strixhaven chunk 12: libraries, hands and discards — looking at the top cards and keeping one or two (CR 401.4), revealing
 * cards from among them (CR 701.20), a land or a card with {X} in its mana cost, a cycling-like ability of a sorcery in hand (CR 113.6),
 * hand or graveyard by choice, discarded lands put onto the battlefield, "any number" discards and draws, half of life, hand and permanents
 * rounded down (CR 107.1a), the opponent exiling their greatest creature, flipping coins for skipped turns (CR 705, 500.7), and the usual
 * draw-discard spells.
 */
class SosChunk12LibrariesAndHandsTest {
    private val bottomAnyOrder = Destination(Zone.Library, libraryPosition = -1)
    private val bottomRandom = Destination(Zone.Library, libraryPosition = -1, randomOrder = true)
    private fun keep(n: Int) = listOf(
        Step.Choose(Choice.Objects(ObjectFilter.Any, n(n), n(n), from = Objects.Bound("looked")), bind = "chosen", chooser = Players.You),
        move(Objects.Bound("chosen"), Zone.Hand))
    /** "Look at the top [count] cards of your library. Put one of them into your hand and the rest [rest]." */
    private fun lookPut(count: Value, rest: Destination) = listOf(
        doing(Effect.LookAt(Objects.LibraryCards(Players.You, count, fromTop = true), Players.You, bind = "looked")),
        *keep(1).toTypedArray(),
        doing(Effect.Move(Objects.Except(Objects.Bound("looked"), Objects.Bound("chosen")), rest)))
    private fun handOf(player: Players) = and(inZone(Zone.Hand), ObjectFilter.OwnedBy(PlayerFilter.Is(player)))
    private fun discardYourHand(postBind: String? = null) =
        doing(Effect.Discard(Players.You, n(1), ObjectFilter.Any, objects = Objects.Each(handOf(Players.You)), postBind = postBind))
    private fun discardAnyDrawThatMany(player: Players, plusOne: Boolean) = listOf(
        Step.Choose(Choice.Objects(handOf(player), n(0), null), bind = "chosen", chooser = player),
        doing(Effect.Discard(player, n(1), ObjectFilter.Any, objects = Objects.Bound("chosen"), postBind = "discarded")),
        doing(Effect.Draw(player, if (plusOne) Value.Sum(listOf(Value.Count(Objects.Bound("discarded")), n(1))) else Value.Count(Objects.Bound("discarded")))))
    private val theirPermanents = and(permanentFilter, ObjectFilter.ControlledBy(PlayerFilter.Is(Players.Bound("player"))))
    private val halfTheirPermanents = Value.Half(Value.Count(Objects.Each(theirPermanents)), roundUp = false)

    // CR 401.4: the rest in any order
    @Test
    fun steadyCurrent() = card(
        name = "Steady Current",
        cost = "{1}{U}",
        type = "Sorcery",
        text = "Look at the top three cards of your library. Put one of them into your hand and the rest on the bottom of your library in any order. If there is an instant card and a sorcery card in your graveyard, instead put two of them into your hand and the rest on the bottom of your library in any order.",
    ) parsesTo abilities(
        spell(
            doing(Effect.LookAt(Objects.LibraryCards(Players.You, n(3), fromTop = true), Players.You, bind = "looked")),
            ifThen(Condition.And(listOf(Condition.Exists(inYourGraveyard(instant), atLeast = 1), Condition.Exists(inYourGraveyard(sorcery), atLeast = 1))),
                *keep(2).toTypedArray(), otherwise = keep(1)),
            doing(Effect.Move(Objects.Except(Objects.Bound("looked"), Objects.Bound("chosen")), bottomAnyOrder)),
        ),
    )

    // CR 701.20a: the revealed cards go where their types say
    @Test
    fun veskasTrialRun() = card(
        name = "Veska's Trial Run",
        cost = "{3}{G}",
        type = "Sorcery",
        text = "Look at the top five cards of your library. You may reveal up to two creature and/or land cards from among them, then put the rest on the bottom of your library in a random order. Put all land cards revealed this way onto the battlefield tapped and put all creature cards revealed this way into your hand.",
    ) parsesTo abilities(
        spell(
            doing(Effect.LookAt(Objects.LibraryCards(Players.You, n(5), fromTop = true), Players.You, bind = "looked")),
            Step.Choose(Choice.Objects(or(creature, land), n(0), n(2), from = Objects.Bound("looked")), bind = "chosen", chooser = Players.You),
            doing(Effect.Reveal(Objects.Bound("chosen"))),
            doing(Effect.Move(Objects.Except(Objects.Bound("looked"), Objects.Bound("chosen")), bottomRandom)),
            doing(Effect.Move(Objects.Where(Objects.Bound("chosen"), land), Destination(Zone.Battlefield, tapped = true))),
            move(Objects.Where(Objects.Bound("chosen"), creature), Zone.Hand),
        ),
    )

    // CR 107.3: a card with {X} in its mana cost
    @Test
    fun paradoxCartographer() = card(
        name = "Paradox Cartographer",
        cost = "{G}{G/U}{U}",
        type = "Creature — Elf Druid",
        pt = "3/3",
        text = """
            Reach
            When this creature enters, look at the top five cards of your library. You may reveal a land card or a card with {X} in its mana cost from among them and put it into your hand. Put the rest on the bottom of your library in a random order.
        """,
    ) parsesTo abilities(
        Keyword.Reach,
        triggered(entersSelf,
            doing(Effect.LookAt(Objects.LibraryCards(Players.You, n(5), fromTop = true), Players.You, bind = "looked")),
            Step.Choose(Choice.Objects(or(land, ObjectFilter.XInManaCost), n(0), n(1), from = Objects.Bound("looked")), bind = "chosen", chooser = Players.You),
            doing(Effect.Reveal(Objects.Bound("chosen"))),
            move(Objects.Bound("chosen"), Zone.Hand),
            doing(Effect.Move(Objects.Except(Objects.Bound("looked"), Objects.Bound("chosen")), bottomRandom))),
    )

    // CR 107.3c: X defined by the text, counted as it resolves
    @Test
    fun rousingHonorchanter() = card(
        name = "Rousing Honorchanter",
        cost = "{2}{W}{W/B}{B}",
        type = "Creature — Rhino Bard",
        pt = "4/5",
        text = "When this creature enters, look at the top X cards of your library, where X is the number of creatures you control. Put one of those cards into your hand and the rest into your graveyard.",
    ) parsesTo abilities(
        triggered(entersSelf, *lookPut(count(creatureYouControl), Destination(Zone.Graveyard)).toTypedArray()),
    )

    @Test
    fun anxiousSlumber() = card(
        name = "Anxious Slumber",
        cost = "{3}{U}{R}",
        type = "Instant",
        text = "Anxious Slumber deals 5 damage to up to one target creature. Look at the top two cards of your library. Put one of those cards into your hand and the other on the bottom of your library.",
    ) parsesTo abilities(
        spell(
            damage(5, toObjects(target("creature"))),
            *lookPut(n(2), bottomAnyOrder).toTypedArray(),
            targets = listOf(slot("creature", creature, min = 0)),
        ),
    )

    // CR 113.6m: an ability of a card in hand, its cost discarding it
    @Test
    fun seersWaltz() = card(
        name = "Seer's Waltz",
        cost = "{5}{U}{R}",
        type = "Sorcery",
        text = """
            Create two 3/3 blue and red Elemental creature tokens with flying.
            {2}, Discard this card: Look at the top two cards of your library. Put one of them into your hand and the other into your graveyard.
        """,
    ) parsesTo abilities(
        spell(create(elemental, n(2))),
        activated(cost(mana("{2}"), CostPart.Discard(CostObjects.Self)), *lookPut(n(2), Destination(Zone.Graveyard)).toTypedArray()),
    )

    // CR 608.2d: the choice is made as it resolves
    @Test
    fun mothersCounsel() = card(
        name = "Mother's Counsel",
        cost = "{1}{B}{G}",
        type = "Instant",
        text = "Search your library for a creature card, reveal it, put it into your hand or graveyard, then shuffle.",
    ) parsesTo abilities(
        spell(
            search(creature, reveal = true),
            Step.Choose(Choice.Option(listOf("hand", "graveyard")), bind = "where", chooser = Players.You),
            ifThen(Condition.Chose("where", "hand"), move(Objects.Bound("found"), Zone.Hand), otherwise = listOf(move(Objects.Bound("found"), Zone.Graveyard))),
            shuffle,
        ),
    )

    @Test
    fun thoughtTaproot() = card(
        name = "Thought Taproot",
        cost = "{1}{B}{G}",
        type = "Sorcery",
        text = "Target player discards two cards. Put up to one land card discarded this way onto the battlefield tapped under your control.",
    ) parsesTo abilities(
        spell(
            doing(Effect.Discard(Players.Target("player"), n(2), ObjectFilter.Any, postBind = "discarded")),
            Step.Choose(Choice.Objects(land, n(0), n(1), from = Objects.Bound("discarded")), bind = "chosen", chooser = Players.You),
            doing(Effect.Move(Objects.Bound("chosen"), Destination(Zone.Battlefield, tapped = true, controller = Players.You))),
            targets = listOf(targetPlayer),
        ),
    )

    @Test
    fun notionIntoSubstance() = card(
        name = "Notion into Substance",
        cost = "{X}{G}{U}",
        type = "Sorcery",
        text = "Draw X cards. Then you may put a permanent card with mana value X or less from your hand onto the battlefield tapped.",
    ) parsesTo abilities(
        spell(
            doing(Effect.Draw(Players.You, x)),
            Step.Choose(Choice.Objects(and(permanentCard, ObjectFilter.ManaValue(Comparison.AtMost, x), inZone(Zone.Hand), ObjectFilter.OwnedBy(PlayerFilter.You)), n(0), n(1)),
                bind = "chosen", chooser = Players.You),
            move(Objects.Bound("chosen"), Zone.Battlefield, tapped = true),
            announced = listOf("X"),
        ),
    )

    // "discard your hand": exactly the cards in it
    @Test
    fun lentWisdom() = card(
        name = "Lent Wisdom",
        cost = "{2}{R}{W}",
        type = "Sorcery",
        text = """
            Choose one —
            • Discard your hand, then draw cards equal to the number of cards in target opponent's hand.
            • Discard your hand, then draw cards equal to the number of cards discarded this way.
        """,
    ) parsesTo abilities(
        Ability.Spell(Instructions(modes = Modes(listOf(
            Mode(Instructions(targets = listOf(targetOpponent), steps = listOf(discardYourHand(), doing(Effect.Draw(Players.You, Value.HandSize(Players.Target("opponent"))))))),
            Mode(Instructions(steps = listOf(discardYourHand("discarded"), doing(Effect.Draw(Players.You, Value.Count(Objects.Bound("discarded"))))))),
        ), min = 1, max = 1, chooser = Players.You))),
    )

    // CR 603.6c: a dies trigger; "that many" are the cards actually discarded
    @Test
    fun colossusOfTheGoreEra() = card(
        name = "Colossus of the Gore Era",
        cost = "{4}{R}{W}",
        type = "Artifact Creature — Construct",
        pt = "6/6",
        text = """
            When this creature enters, it deals 3 damage to each opponent and you gain 3 life.
            When this creature dies, discard any number of cards, then draw that many cards plus one.
        """,
    ) parsesTo abilities(
        triggered(entersSelf, damage(3, eachOpponent, source = Objects.Self), gainLife(3)),
        triggered(diesSelf, *discardAnyDrawThatMany(Players.You, plusOne = true).toTypedArray()),
    )

    // CR 700.2: one or both modes
    @Test
    fun upstageTheLecture() = card(
        name = "Upstage the Lecture",
        cost = "{2}{R}",
        type = "Sorcery",
        text = """
            Choose one or both —
            • Target player discards any number of cards, then draws that many cards.
            • Upstage the Lecture deals damage equal to the number of instant and sorcery cards in your graveyard to target creature or planeswalker.
        """,
    ) parsesTo abilities(
        Ability.Spell(Instructions(modes = Modes(listOf(
            Mode(Instructions(targets = listOf(targetPlayer), steps = discardAnyDrawThatMany(Players.Target("player"), plusOne = false))),
            Mode(Instructions(targets = listOf(slot("permanent", or(creature, ObjectFilter.Type(arcanum.api.CardType.Planeswalker)))), steps = listOf(
                damage(Value.Count(Objects.Each(and(instantOrSorcery, inZone(Zone.Graveyard), ObjectFilter.OwnedBy(PlayerFilter.You)))),
                    toObjects(target("permanent")))))),
        ), min = 1, max = 2, chooser = Players.You))),
    )

    // CR 107.1a: rounded down; CR 101.4: each step for all players together
    @Test
    fun blightOfHalves() = card(
        name = "Blight of Halves",
        cost = "{B}{B}{B}{B}{B}",
        type = "Sorcery",
        text = "Each player loses half their life, then discards half the cards in their hand, then sacrifices half the permanents they control of their choice. Round down each time.",
    ) parsesTo abilities(
        spell(Step.ForEach(Each.Players(Players.Each(PlayerFilter.Any)), "player", listOf(
            doing(Effect.LoseLife(Players.Bound("player"), Value.Half(Value.LifeOf(Players.Bound("player")), roundUp = false))),
            doing(Effect.Discard(Players.Bound("player"), Value.Half(Value.HandSize(Players.Bound("player")), roundUp = false), ObjectFilter.Any)),
            Step.Choose(Choice.Objects(theirPermanents, halfTheirPermanents, halfTheirPermanents), bind = "sacrificed", chooser = Players.Bound("player")),
            doing(Effect.Sacrifice(Objects.Bound("sacrificed")))), together = true)),
    )

    // CR 608.2d: that player chooses among the ones tied for greatest
    @Test
    fun closeOfTheChase() = card(
        name = "Close of the Chase",
        cost = "{1}{B}",
        type = "Sorcery",
        text = "Target opponent exiles a creature or planeswalker they control with the greatest mana value among creatures and planeswalkers they control.",
    ) parsesTo abilities(
        spell(
            Step.Choose(Choice.Objects(and(or(creature, ObjectFilter.Type(arcanum.api.CardType.Planeswalker)), ObjectFilter.ControlledBy(PlayerFilter.Is(Players.Target("opponent"))),
                ObjectFilter.ManaValue(Comparison.Equal, Value.Of(NumericProperty.ManaValue, Objects.Each(and(or(creature, ObjectFilter.Type(arcanum.api.CardType.Planeswalker)),
                    ObjectFilter.ControlledBy(PlayerFilter.Is(Players.Target("opponent"))))), Combine.Greatest))), n(1), n(1)), bind = "chosen", chooser = Players.Target("opponent")),
            move(Objects.Bound("chosen"), Zone.Exile),
            targets = listOf(targetOpponent),
        ),
    )

    // CR 705: each heads is one more turn skipped (CR 500.7)
    @Test
    fun varekThornVisitingLecturer() = card(
        name = "Varek Thorn, Visiting Lecturer",
        cost = "{1}{B}{B}",
        type = "Legendary Planeswalker — Ral",
        loyalty = "3",
        text = """
            [+1]: Surveil 2.
            [−1]: Any number of target players each discard a card.
            [−2]: Return target creature card with mana value 3 or less from your graveyard to the battlefield.
            [−7]: Flip five coins. Target opponent skips their next X turns, where X is the number of coins that came up heads.
        """,
    ) parsesTo abilities(
        activated(cost(CostPart.Loyalty(n(1))), doing(Effect.Surveil(Players.You, n(2)))),
        activated(cost(CostPart.Loyalty(n(-1))), doing(Effect.Discard(Players.Target("players"), n(1), ObjectFilter.Any)),
            targets = listOf(TargetSlot("players", TargetKind.Players(PlayerFilter.Any), min = n(0), max = null))),
        activated(cost(CostPart.Loyalty(n(-2))), move(target("card"), Zone.Battlefield),
            targets = listOf(slot("card", inYourGraveyard(creature, manaValue(Comparison.AtMost, 3))))),
        activated(cost(CostPart.Loyalty(n(-7))), Step.Repeat(n(5), listOf(doing(Effect.FlipCoin(Players.You, bind = "flip")),
            ifThen(Condition.WonFlip("flip"), doing(Effect.SkipNextTurn(Players.Target("opponent")))))), targets = listOf(targetOpponent)),
    )

    // CR 701.20a: the hand revealed; you choose, that player discards
    @Test
    fun strikeDumb() = card(
        name = "Strike Dumb",
        cost = "{2}{W}{B}",
        type = "Sorcery",
        text = """
            Target opponent reveals their hand. You choose a nonland card from it. That player discards that card.
            Put two +1/+1 counters on up to one target creature.
        """,
    ) parsesTo abilities(
        spell(
            doing(Effect.Reveal(Objects.Each(and(inZone(Zone.Hand), ObjectFilter.OwnedBy(PlayerFilter.Is(Players.Target("opponent"))))), bind = "revealed")),
            Step.Choose(Choice.Objects(nonland, n(1), n(1), from = Objects.Bound("revealed")),
                bind = "chosen", chooser = Players.You),
            doing(Effect.Discard(Players.Target("opponent"), n(1), ObjectFilter.Any, objects = Objects.Bound("chosen"))),
            counters(target("creature"), 2),
            targets = listOf(targetOpponent, slot("creature", creature, min = 0)),
        ),
    )

    // CR 601.2f: an additional cost
    @Test
    fun grabTheLoot() = card(
        name = "Grab the Loot",
        cost = "{2}{R}",
        type = "Sorcery",
        text = """
            As an additional cost to cast this spell, discard a card.
            Draw two cards and create a Treasure token. (It's an artifact with "{T}, Sacrifice this token: Add one mana of any color.")
        """,
    ) parsesTo abilities(
        static(Mechanic.AdditionalCost(cost = cost(discardACard))),
        spell(draw(2), createTreasure()),
    )

    @Test
    fun ecstaticInstant() = card(
        name = "Ecstatic Instant",
        cost = "{4}{U}{R}",
        type = "Sorcery",
        text = "Draw three cards, then discard two cards. Add {U}{U}{R}{R}{R}.",
    ) parsesTo abilities(
        spell(draw(3), discard(2), doing(Effect.AddMana(ManaProduction(ManaKind.Fixed(ManaCost.parse("{U}{U}{R}{R}{R}")!!)), Players.You))),
    )

    @Test
    fun acceptTheContradiction() = card(
        name = "Accept the Contradiction",
        cost = "{3}{G}{U}",
        type = "Instant",
        text = "Draw three cards. You may put a land card from your hand onto the battlefield tapped.",
    ) parsesTo abilities(
        spell(
            draw(3),
            Step.Choose(Choice.Objects(and(land, inZone(Zone.Hand), ObjectFilter.OwnedBy(PlayerFilter.You)), n(0), n(1)), bind = "chosen", chooser = Players.You),
            move(Objects.Bound("chosen"), Zone.Battlefield, tapped = true),
        ),
    )

    // CR 118.12: "you may draw a card. If you do" — a draw is always possible (CR 121.3)
    @Test
    fun arenaTidecaller() = card(
        name = "Arena Tidecaller",
        cost = "{2}{U}{R}",
        type = "Creature — Djinn Sorcerer",
        pt = "4/4",
        text = "Whenever this creature enters or attacks, you may draw a card. If you do, discard a card.",
    ) parsesTo abilities(
        triggered(entersOrAttacksSelf, Step.Pay(steps = listOf(draw(1)), payer = Players.You, bind = "paid"), ifThen(Condition.Did("paid"), discard(1))),
    )

    @Test
    fun rushingFeudknight() = card(
        name = "Rushing Feudknight",
        cost = "{2}{R}",
        type = "Creature — Spirit Knight",
        pt = "3/3",
        text = """
            Haste
            {T}, Discard a card: Draw a card.
        """,
    ) parsesTo abilities(
        Keyword.Haste, activated(cost(tap, discardACard), draw(1)),
    )

    @Test
    fun speedReading() = card(
        name = "Speed Reading",
        cost = "{2}{U}",
        type = "Instant",
        text = "Draw two cards.",
    ) parsesTo abilities(
        spell(draw(2)),
    )

    @Test
    fun musesUrging() = card(
        name = "Muse's Urging",
        cost = "{4}{U}",
        type = "Instant",
        text = """
            Create a 3/3 blue and red Elemental creature token with flying.
            Surveil 2. (Look at the top two cards of your library, then put any number of them into your graveyard and the rest on top of your library in any order.)
        """,
    ) parsesTo abilities(
        spell(create(elemental), doing(Effect.Surveil(Players.You, n(2)))),
    )

    @Test
    fun scarringReview() = card(
        name = "Scarring Review",
        cost = "{X}{U}{R}",
        type = "Instant",
        text = "Scarring Review deals X damage to any target. Draw two cards, then discard a card.",
    ) parsesTo abilities(
        spell(damage(x, toAny), draw(2), discard(1), targets = listOf(anyTarget), announced = listOf("X")),
    )

    // CR 701.21a: you choose the lands to sacrifice
    @Test
    fun landscapeReworking() = card(
        name = "Landscape Reworking",
        cost = "{3}{G}",
        type = "Sorcery",
        text = "Sacrifice two lands. Search your library for four basic land cards, put them onto the battlefield tapped, then shuffle.",
    ) parsesTo abilities(
        spell(
            Step.Choose(Choice.Objects(and(land, you), n(2), n(2)), bind = "sacrificed", chooser = Players.You),
            doing(Effect.Sacrifice(Objects.Bound("sacrificed"))),
            search(basicLand(), max = 4),
            move(Objects.Bound("found"), Zone.Battlefield, tapped = true),
            shuffle,
        ),
    )

    @Test
    fun ecologyResearcher() = card(
        name = "Ecology Researcher",
        cost = "{1}{G}",
        type = "Creature — Human Druid",
        pt = "2/2",
        text = "When this creature enters, you may search your library for a basic land card, reveal it, put it into your hand, then shuffle.",
    ) parsesTo abilities(
        triggered(entersSelf, Step.May(listOf(search(basicLand(), reveal = true), move(Objects.Bound("found"), Zone.Hand), shuffle), Players.You)),
    )
}
