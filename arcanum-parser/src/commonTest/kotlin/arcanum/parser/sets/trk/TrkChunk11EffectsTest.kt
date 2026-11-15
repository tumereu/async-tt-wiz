package arcanum.parser.sets.trk

import arcanum.api.*
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.parsesTo
import arcanum.parser.cube.whenThisEnters
import kotlin.test.Test

/**
 * TRK chunk 11: draws, life, looks, searches and reveals: a reminder text broken over two lines, cards shuffled into their owners' libraries
 * (CR 400.3), a token copy gaining haste (CR 707.2), an exiled card gaining an ability that works in exile (CR 113.6m), hexproof for a player
 * (CR 702.11c), sharing a card type (CR 607.2a), face-down exiles each player may play (CR 406.3), not losing for 0 life (CR 704.5a), life lost
 * this turn (CR 119.3), playing from outside the game (CR 400.11), a control change per opponent (CR 601.2c), returns to hand this turn
 * (CR 603.4), mandatory puts from among looked-at cards (CR 609.3), revealing until two lands (CR 701.20) and searches (CR 701.23).
 */
class TrkChunk11EffectsTest {
    private val officer = creatureToken("Officer", listOf(Color.Red), 1, 1)
    private val spacecraft = sub("Spacecraft")
    private val endStep = stepBegins(TurnStep.End, PlayerFilter.Any)

    // ---- Sagas

    // CR 714.2 (chapters), 400.3 (a card goes to its owner's library), 701.24a (each owner shuffles)
    @Test
    fun thoseElderTinkerers() = card(
        name = "Those Elder Tinkerers",
        cost = "{2}{U}",
        type = "Enchantment — Saga",
        text = """
            (As this Saga enters and after your draw
            step, add a lore counter. Sacrifice after III.)
            I — Draw two cards, then discard two cards.
            II — Return up to one target artifact, instant, or sorcery card from your graveyard to your hand.
            III — Choose up to two target cards in graveyards. The owners of those cards shuffle them into their libraries.
        """,
    ) parsesTo abilities(
        chapter(1, draw(2), discard(2)),
        chapter(2, move(target("cards"), Zone.Hand), targets = listOf(slot("cards", inYourGraveyard(or(artifact, instant, sorcery)), min = 0))),
        chapter(3,
            move(target("cards"), Zone.Library),
            Step.Do(Effect.Shuffle(Players.OwnerOf(target("cards")))),
            targets = listOf(slot("cards", and(cardFilter, inZone(Zone.Graveyard)), min = 0, max = 2)),
        ),
    )

    // CR 607.2a (the cards exiled with it, still in exile), 714.2; with no exiled card, none lacks a shared type (rulings register)
    @Test
    fun everyFineEnding() = card(
        name = "Every Fine Ending...",
        cost = "{3}{G}{U}",
        type = "Enchantment — Saga",
        text = """
            (As this Saga enters and after your draw step, add a lore counter. Sacrifice after IV.)
            I, II, III — Draw two cards, then exile a card from your hand.
            IV — If all cards exiled with this Saga share a card type, you gain 10 life.
        """,
    ) parsesTo abilities(
        chapters(listOf(1, 2, 3),
            draw(2),
            Step.Choose(Choice.Objects(and(inZone(Zone.Hand), ObjectFilter.OwnedBy(PlayerFilter.Is(Players.You))), min = n(1), max = n(1)), bind = "chosen", chooser = Players.You),
            Step.Do(Effect.Move(Objects.Bound("chosen"), Destination(Zone.Exile), postBind = "exiled")), Step.Do(Effect.Remember("exiled", Objects.Bound("exiled"))),
        ),
        chapter(4, ifThen(Condition.Compare(Value.MostSharing(exiledWith, SharedQuality.CardType), Comparison.Equal, Value.Count(exiledWith)), gainLife(10))),
    )
    private val exiledWith = Objects.Where(Objects.Remembered("exiled"), inZone(Zone.Exile))

    // ---- Copies and granted abilities

    // CR 707.2 (a token copy), 611.2a (it keeps haste), 603.7 (a delayed trigger)
    @Test
    fun synapticLeechjelly() = card(
        name = "Synaptic Leechjelly",
        cost = "{3}{B}",
        type = "Creature — Jellyfish Leech",
        pt = "2/2",
        text = """
            Flying
            When this creature enters, exile target creature card with mana value 3 or less from your graveyard. Create a token that's a copy of it. That token gains haste. Sacrifice it at the beginning of the next end step.
            Whenever one or more cards leave your graveyard, each opponent loses 1 life and you gain 1 life.
        """,
    ) parsesTo abilities(
        Keyword.Flying,
        whenThisEnters(
            move(target("card"), Zone.Exile, postBind = "exiled"),
            create(TokenSpec.CopyOf(Objects.Bound("exiled")), bind = "tokens"),
            Step.Do(Effect.ApplyMechanics(listOf(has(Keyword.Haste)), Affected.Objects(Objects.Bound("tokens")), Duration.Indefinitely)),
            Step.Do(Effect.CreateDelayedTrigger(endStep as Trigger.On,
                Instructions(steps = listOf(Step.Do(Effect.Sacrifice(Objects.Bound("tokens"), by = Players.You)))))),
            targets = listOf(slot("card", inYourGraveyard(creature, manaValue(Comparison.AtMost, 3)))),
        ),
        triggered(Trigger.On(EventPattern.ZoneChange(and(cardFilter, ObjectFilter.OwnedBy(PlayerFilter.You)), from = Zone.Graveyard), oneOrMore = true),
            loseLife(1, Players.Each(PlayerFilter.Opponent)), gainLife(1)),
    )

    // CR 113.6m, 602.5b (an ability that works in exile, during your turn), 707.12 (a copy of the card, cast paying its costs), 400.7 (only
    // while that card stays exiled)
    @Test
    fun zedShufflingTheOdds() = card(
        name = "Zed, Shuffling the Odds",
        cost = "{2}{R}{R}",
        type = "Legendary Creature — Q",
        pt = "4/4",
        text = """When Zed enters, shuffle your library, then exile cards from the top of your library until you exile a nonland card. That nonland card gains "Exile a card from your hand: Create a copy of this card. You may cast the copy. Activate only during your turn and only if this card is exiled."""",
    ) parsesTo abilities(
        whenThisEnters(
            shuffle,
            Step.Do(Effect.RevealUntil(nonland, Players.You, n(1), reveal = false, into = Zone.Exile, foundBind = "found", restBind = "rest")),
            Step.Do(Effect.ApplyMechanics(listOf(has(
                activated(cost(CostPart.Exile(CostObjects.Choose(cardFilter, count = n(1)), from = Zone.Hand)),
                    Step.Do(Effect.CopyCard(Objects.Self, Players.You, bind = "copies")),
                    Step.ForEach(Each.Objects(Objects.Bound("copies")), "copy", listOf(
                        Step.May(listOf(Step.Do(Effect.Cast(Objects.Bound("copy"), PlayKind.Cast, caster = Players.You))), Players.You))),
                    rules = anyTime.copy(condition = Condition.And(listOf(Condition.YourTurn, Condition.ObjectMatches(Objects.Self, inZone(Zone.Exile))))),
                    functionsIn = listOf(Zone.Exile)),
            )), Affected.Objects(Objects.Bound("found")), Duration.Indefinitely)),
        ),
    )

    // ---- Life

    // CR 702.11b-c (hexproof for creatures and for a player), 615 (prevention), 118.8 (an additional cost)
    @Test
    fun theWeightOfTheFew() = card(
        name = "The Weight of the Few",
        cost = "{1}{W}",
        type = "Instant",
        text = """
            As an additional cost to cast this spell, sacrifice a creature.
            Creatures you control gain hexproof and indestructible until end of turn. You gain hexproof until end of turn. Prevent all damage that would be dealt to you this turn. Exile The Weight of the Few.
        """,
    ) parsesTo abilities(
        Ability.Static(listOf(Mechanic.AdditionalCost(cost = cost(CostPart.Sacrifice(CostObjects.Choose(creature, count = n(1)))))), affects = Affected.Objects(Objects.Self)),
        spell(
            gains(Objects.Each(creatureYouControl), Keyword.Hexproof, Keyword.Indestructible),
            Step.Do(Effect.ApplyMechanics(listOf(Mechanic.CantBeTargeted(by = ObjectFilter.ControlledBy(PlayerFilter.Opponent))), Affected.Players(Players.You), Duration.EndOfTurn)),
            Step.Do(Effect.ApplyMechanics(listOf(Mechanic.Prevention(EventPattern.Damage(ObjectFilter.Any, RecipientFilter(players = PlayerFilter.You)))), null, Duration.EndOfTurn)),
            move(Objects.Self, Zone.Exile),
        ),
    )

    // CR 704.5a (no loss for 0 or less life), 119.3
    @Test
    fun varlessaMirrorEmpress() = card(
        name = "Varlessa, Mirror Empress",
        cost = "{4}{B}{B}",
        type = "Legendary Creature — Human Rogue Noble",
        pt = "4/6",
        text = """
            You don't lose the game for having 0 or less life.
            At the beginning of your end step, you lose 3 life and draw a card. Then if your life total is 0 or less, create ten 1/1 red Officer creature tokens.
        """,
    ) parsesTo abilities(
        playerStatic(Mechanic.NoLossForZeroLife),
        triggered(stepBegins(TurnStep.End, PlayerFilter.You),
            loseLife(3), draw(1),
            ifThen(Condition.Compare(Value.LifeOf(Players.You), Comparison.AtMost, n(0)), create(officer, n(10)))),
    )

    // CR 119.3 (each opponent's own loss this turn), 608.2e (all at once), 608.2c (the life lost this way is all of it)
    @Test
    fun gulVarekOccupationPrefect() = card(
        name = "Gul Varek, Occupation Prefect",
        cost = "{4}{B}{B}{B}",
        type = "Legendary Creature — Cardassian Rogue",
        pt = "6/6",
        text = """
            Menace
            At the beginning of each end step, each opponent loses life equal to the life that player lost this turn. You gain life equal to the life lost this way. (Damage causes loss of life.)
        """,
    ) parsesTo abilities(
        Keyword.Menace,
        triggered(endStep,
            Step.ForEach(Each.Players(Players.Each(PlayerFilter.Opponent)), "player", listOf(Step.Do(Effect.LoseLife(Players.Bound("player"),
                Value.EventsThisTurn(EventPattern.LoseLife(PlayerFilter.Is(Players.Bound("player"))), Measure.Amount), bind = "lost"))), together = true),
            Step.Do(Effect.GainLife(Players.You, Value.Bound("lost")))),
    )

    // ---- Looks, plays and control

    // CR 406.3 (who may look at a face-down exiled card), 400.7 (only while it stays exiled), 609.4b (mana of any type)
    @Test
    fun psionicMindLink() = card(
        name = "Psionic Mind-Link",
        cost = "{U}",
        type = "Sorcery",
        text = "Target opponent looks at the top card of your library, then exiles it face down. You look at the top two cards of their library, then exile them face down. For each card exiled this way, the player who exiled it may play it for as long as it remains exiled. Mana of any type can be spent to cast spells this way.",
    ) parsesTo abilities(
        spell(
            Step.ForEach(Each.Players(Players.Target("opponent")), "opponent", together = true, steps =
                exileAndPermit(Players.You, 1, Players.Bound("opponent"), PlayerFilter.Is(Players.Bound("opponent")), "theirs") +
                    exileAndPermit(Players.Bound("opponent"), 2, Players.You, PlayerFilter.You, "yours")),
            targets = listOf(targetOpponent),
        ),
    )
    private fun exileAndPermit(library: Players, count: Int, looker: Players, holder: PlayerFilter, bind: String): List<Step> {
        val cards = Objects.LibraryCards(library, n(count), fromTop = true)
        return listOf(Step.Do(Effect.LookAt(cards, looker)),
            Step.Do(Effect.Move(cards, Destination(Zone.Exile, faceDown = true), postBind = bind)),
            Step.Do(Effect.ApplyMechanics(listOf(Mechanic.MayLookAt(Objects.Bound(bind))), Affected.Players(looker), Duration.Indefinitely)),
            Step.Do(Effect.ApplyMechanics(listOf(Mechanic.MayPlay(Zone.Exile, PlayKind.Play, holder = holder, manaAsAnyColor = true)),
                Affected.Objects(Objects.Bound(bind)), Duration.Indefinitely)))
    }

    // CR 400.11 (outside the game), 305.2 (a land played this way is the land play), 601.3 (one use each turn for both zones)
    @Test
    fun theSimulationDeck() = card(
        name = "The Simulation Deck",
        cost = "{7}",
        type = "Legendary Artifact",
        text = "Once during each of your turns, you may play a permanent card you own from outside the game or from your graveyard.",
    ) parsesTo abilities(
        Ability.Static(listOf(
            Mechanic.MayPlay(Zone.Outside, PlayKind.Play, perTurn = 1, holder = PlayerFilter.You),
            Mechanic.MayPlay(Zone.Graveyard, PlayKind.Play, perTurn = 1, holder = PlayerFilter.You)),
            condition = Condition.YourTurn,
            affects = Affected.Objects(Objects.Each(and(permanentCard, or(inZone(Zone.Outside), inZone(Zone.Graveyard)), ObjectFilter.OwnedBy(PlayerFilter.You))))),
    )

    // CR 601.2c (a target for each opponent among that player's creatures), 205.1b (a creature type in addition), 611.2c
    @Test
    fun korvathHiredSpy() = card(
        name = "Korvath, Hired Spy",
        cost = "{2}{R}",
        type = "Legendary Creature — Klingon Warrior Spy",
        pt = "3/2",
        text = "When Korvath enters, for each opponent, gain control of up to one target creature that player controls with mana value 3 or less until end of turn. Untap those creatures. Until end of turn, they become Warriors in addition to their other types and gain haste.",
    ) parsesTo abilities(
        whenThisEnters(
            untilEndOfTurn(target("creature"), Mechanic.ControlledBy(Players.You)),
            untapIt(target("creature")),
            untilEndOfTurn(target("creature"), Mechanic.SetTypes(addSubtypes = listOf(SubtypeRef.Literal(Subtype("Warrior")))), has(Keyword.Haste)),
            targets = listOf(TargetSlot("creature", TargetKind.Objects(and(creature, manaValue(Comparison.AtMost, 3),
                ObjectFilter.ControlledBy(PlayerFilter.Is(Players.SlotPlayer)))), min = n(0), max = n(1), perPlayer = PlayerFilter.Opponent)),
        ),
    )

    // CR 603.4 (an intervening if), 400.3 (a permanent goes to its owner's hand), 603.10a
    @Test
    fun tamsinRourkeRelayChief() = card(
        name = "Tamsin Rourke, Relay Chief",
        cost = "{2}{W}",
        type = "Legendary Creature — Human Artificer",
        pt = "3/3",
        text = """
            When Tamsin Rourke enters, return up to one other target creature or artifact you control to its owner's hand.
            At the beginning of each end step, if a permanent was returned to your hand from the battlefield this turn, draw a card.
        """,
    ) parsesTo abilities(
        whenThisEnters(move(target("permanent"), Zone.Hand), targets = listOf(slot("permanent", and(another, or(creature, artifact), you), min = 0))),
        triggered(endStep, draw(1), interveningIf = Condition.HappenedThisTurn(EventPattern.ZoneChange(and(permanentFilter, ObjectFilter.OwnedBy(PlayerFilter.You)),
            from = Zone.Battlefield, to = Zone.Hand), atLeast = 1, measure = Measure.Events)),
    )

    // CR 609.3 (a land if there is one), 401.4
    @Test
    fun punchItNow() = card(
        name = "Punch It Now!",
        cost = "{1}{R}{G}",
        type = "Enchantment",
        text = """
            Creatures you control have haste.
            When this enchantment enters, look at the top five cards of your library. Put a land card from among them onto the battlefield tapped and the rest into your graveyard.
        """,
    ) parsesTo abilities(
        static(has(Keyword.Haste), affects = Objects.Each(creatureYouControl)),
        whenThisEnters(
            Step.Do(Effect.LookAt(Objects.LibraryCards(Players.You, n(5), fromTop = true), Players.You, bind = "seen")),
            Step.Choose(Choice.Objects(land, min = n(1), max = n(1), from = Objects.Bound("seen")), bind = "kept", chooser = Players.You),
            move(Objects.Bound("kept"), Zone.Battlefield, tapped = true),
            move(Objects.Except(Objects.Bound("seen"), Objects.Bound("kept")), Zone.Graveyard),
        ),
    )

    // CR 122.1 (counters), 608.2c (the two counters instead of one at five quest counters)
    @Test
    fun reliefOperation() = card(
        name = "Relief Operation",
        cost = "{2}{G}",
        type = "Enchantment",
        text = "Whenever a creature you control enters, put a quest counter on this enchantment. Put a +1/+1 counter on that creature. If this enchantment has five or more quest counters on it, instead put two +1/+1 counters on that creature.",
    ) parsesTo abilities(
        triggered(enters(creatureYouControl),
            counters(Objects.Self, kind = CounterKind.Named("quest")),
            ifThen(Condition.ObjectMatches(Objects.Self, ObjectFilter.HasCounter(CounterKind.Named("quest"), atLeast = 5)),
                counters(Objects.EventObject(EventRole.After), 2),
                otherwise = listOf(counters(Objects.EventObject(EventRole.After), 1)))),
    )

    // CR 701.20 (revealed until two lands), 603.7 (a delayed trigger at your next end step), 701.21a
    @Test
    fun jorinVaskAwayTeamLead() = card(
        name = "Jorin Vask, Away Team Lead",
        cost = "{3}{R}",
        type = "Legendary Creature — Human Officer",
        pt = "4/3",
        text = "At the beginning of combat on your turn, reveal cards from the top of your library until you reveal two land cards. Put those land cards onto the battlefield and the rest on the bottom of your library in a random order. Sacrifice those lands at the beginning of your next end step.",
    ) parsesTo abilities(
        triggered(beginningOfCombatOnYourTurn,
            Step.Do(Effect.RevealUntil(land, Players.You, n(2), reveal = true, foundBind = "found", restBind = "rest")),
            move(Objects.Bound("found"), Zone.Battlefield),
            Step.Do(Effect.Move(Objects.Bound("rest"), Destination(Zone.Library, libraryPosition = -1, randomOrder = true))),
            Step.Do(Effect.CreateDelayedTrigger(stepBegins(TurnStep.End, PlayerFilter.You) as Trigger.On,
                Instructions(steps = listOf(Step.Do(Effect.Sacrifice(Objects.Bound("found"), by = Players.You))))))),
    )

    // ---- Searches

    // CR 701.23a (a search), 608.2h (its controller as it last existed)
    @Test
    fun ninetyParsecsFromPort() = card(
        name = "Ninety Parsecs from Port",
        cost = "{1}{W}",
        type = "Sorcery",
        text = "Exile target creature or Spacecraft. Its controller may search their library for a basic land card, reveal it, put it into their hand, then shuffle.",
    ) parsesTo abilities(
        spell(
            move(target("permanent"), Zone.Exile),
            Step.May(listOf(
                Step.Do(Effect.Search(controller, basicLand(), max = n(1), reveal = true, zones = listOf(Zone.Library), bind = "found", searcher = controller)),
                move(Objects.Bound("found"), Zone.Hand),
                Step.Do(Effect.Shuffle(controller))), controller),
            targets = listOf(slot("permanent", or(creature, spacecraft))),
        ),
    )
    private val controller = Players.ControllerOf(target("permanent"))

    // CR 701.23a, d (any number of cards named so, as many as the library has), 201.2
    @Test
    fun galacticReplicantDrones() = card(
        name = "Galactic Replicant Drones",
        cost = "{2}{W}",
        type = "Artifact Creature — Android",
        pt = "3/1",
        text = "When this creature enters, you may search your library for any number of cards named Galactic Replicant Drones, reveal them, put them into your hand, then shuffle.",
    ) parsesTo abilities(
        whenThisEnters(Step.May(listOf(
            Step.Do(Effect.Search(Players.You, named, max = Value.Count(Objects.Each(and(named, inZone(Zone.Library), ObjectFilter.OwnedBy(PlayerFilter.You)))),
                reveal = true, zones = listOf(Zone.Library), bind = "found", searcher = Players.You)),
            move(Objects.Bound("found"), Zone.Hand),
            shuffle), Players.You)),
    )
    private val named = ObjectFilter.Named("Galactic Replicant Drones")

    // CR 122.6 (it enters with the counters), 701.23a, 702.184 (Spacecraft)
    @Test
    fun captainOrrinDelacroix() = card(
        name = "Captain Orrin Delacroix",
        cost = "{2}{W}",
        type = "Legendary Creature — Human Officer",
        pt = "2/3",
        text = """
            Other creatures you control get +1/+1.
            Whenever you cast a creature or Spacecraft spell, create a 1/1 red Officer creature token.
            Tap seven untapped Officers you control: Search your library for a Spacecraft card, put it onto the battlefield with seven charge counters on it, then shuffle.
        """,
    ) parsesTo abilities(
        static(pt(1, 1), affects = Objects.Each(and(another, creature, you))),
        triggered(youCast(or(creature, spacecraft)), create(officer)),
        activated(cost(CostPart.TapObjects(CostObjects.Choose(sub("Officer"), count = n(7)))),
            search(spacecraft),
            Step.Do(Effect.Move(Objects.Bound("found"), Destination(Zone.Battlefield, counters = listOf(CountersToPut(charge, n(7)))))),
            shuffle),
    )
}
