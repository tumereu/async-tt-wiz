package arcanum.parser.sets.trk

import arcanum.api.*
import arcanum.parser.cube.abilities
import arcanum.parser.cube.anyOneColor
import arcanum.parser.cube.card
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

/**
 * TRK chunk 08: landfall (an ability word, CR 207.2c), mill (CR 701.17), Treasure tokens (CR 111.10a), New Planet land tokens (CR 111.4),
 * the first spell you've cast this game (CR 601.2i), a destruction replacement (CR 614.1a, 614.8) and a cast permission once each turn.
 */
class TrkChunk08LandfallMillTreasureTest {
    private val quest = CounterKind.Named("quest")
    private val treasures = sub("Treasure")
    private fun landfall(vararg steps: Step, targets: List<TargetSlot> = emptyList()) =
        triggered(arcanum.parser.sets.trk.landfall, *steps, targets = targets, abilityWord = "Landfall")
    /** "Create a tapped Planet land token named New Planet with "{T}: Add one mana of any color."" (CR 111.4, 205.3i) */
    private val newPlanet = Step.Do(Effect.CreateToken(TokenSpec.Defined(FaceDefinition("New Planet", colorIndicator = emptyList(),
        types = listOf(CardType.Land), subtypes = listOf(Subtype("Planet")),
        abilities = listOf(activated(cost(tap), doing(Effect.AddMana(ManaProduction(anyOneColor(1)), Players.You)))))),
        n(1), Players.You, tapped = true))
    /** "Mill four cards. You may put a permanent card from among them into your hand." (CR 701.17a, 110.4a) */
    private val millFourKeepPermanent = arrayOf(
        mill(4, postBind = "milled"),
        Step.Choose(Choice.Objects(permanentCard, min = n(0), max = n(1), from = Objects.Bound("milled")), bind = "chosen", chooser = Players.You),
        move(Objects.Bound("chosen"), Zone.Hand),
    )

    // ---- Landfall

    // CR 207.2c (landfall), 201.5c (first and last word), 120.3 (damage to each opponent), 603.4 ("the second time this ability has resolved this turn")
    @Test
    fun captainRheaTalvik() = card(
        name = "Captain Rhea Talvik",
        cost = "{2}{R}",
        type = "Legendary Creature — Human Officer",
        pt = "3/3",
        text = """
            Trample
            Landfall — Whenever a land you control enters, Captain Talvik deals 1 damage to each opponent. If this is the second time this ability has resolved this turn, put a +1/+1 counter on Captain Talvik.
        """,
    ) parsesTo abilities(
        Keyword.Trample,
        landfall(
            damage(1, eachOpponent),
            ifThen(Condition.Compare(Value.AbilityUsesThisTurn(resolved = true), Comparison.Equal, n(2)), counters(Objects.Self)),
        ),
    )

    // CR 111.4 (a named token), 205.3i (Planet is a land type), 602.5d (activate only as a sorcery)
    @Test
    fun saurianWayfinder() = card(
        name = "Saurian Wayfinder",
        cost = "{3}{G}",
        type = "Creature — Dinosaur Scout",
        pt = "4/3",
        text = """
            Landfall — Whenever a land you control enters, this creature gets +2/+2 until end of turn.
            {6}: Create a tapped Planet land token named New Planet with "{T}: Add one mana of any color." Activate only as a sorcery.
        """,
    ) parsesTo abilities(
        landfall(pump(Objects.Self, 2, 2)),
        activated(manaCost("{6}"), newPlanet, rules = asSorcery),
    )

    // CR 111.4 (a token with a quoted ability), 611.2c (only the creatures you control as it resolves)
    @Test
    fun corwinDaceEmpath() = card(
        name = "Corwin Dace, Empath",
        cost = "{2}{R/G}{R/G}",
        type = "Legendary Creature — Kwejian Rogue",
        pt = "2/2",
        text = """
            Landfall — Whenever a land you control enters, create a 2/2 red and green Worm creature token with "This token can't block."
            {8}: Creatures you control get +2/+0 until end of turn.
        """,
    ) parsesTo abilities(
        landfall(create(creatureToken("Worm", listOf(Color.Red, Color.Green), 2, 2, listOf(static(Mechanic.CantBlock(ObjectFilter.Any)))))),
        activated(manaCost("{8}"), untilEndOfTurn(Objects.Each(and(creature, you)), pt(2, 0))),
    )

    // CR 605.1b (a landfall trigger that adds mana isn't a mana ability: it uses the Stack), 106.1
    @Test
    fun tavoRinnAugmentOrphan() = card(
        name = "Tavo Rinn, Augment Orphan",
        cost = "{1}{R}{G}",
        type = "Legendary Creature — Human Mutant Rogue",
        pt = "3/3",
        text = """
            Landfall — Whenever a land you control enters, add {R} or {G}.
            {3}, Sacrifice a land: Draw a card.
        """,
    ) parsesTo abilities(
        landfall(doing(Effect.AddMana(ManaProduction(ManaKind.Choose(n(1), colors = listOf(Color.Red, Color.Green).map { ColorRef.Literal(it) })), Players.You))),
        activated(cost(mana("{3}"), sacrificeA(land)), draw(1)),
    )

    // CR 122.1 (quest counters), 701.17-style "exile until" (CR 406.3), 305.1 ("play that card": a land too), 608.2c (the count at resolution)
    @Test
    fun surveyExpedition() = card(
        name = "Survey Expedition",
        cost = "{2}{R}{R}",
        type = "Enchantment",
        text = "Landfall — Whenever a land you control enters, put a quest counter on this enchantment. Then exile cards from the top of your library until you exile a nonland card. You may play that card this turn. If this enchantment has five or more quest counters on it, add {R}{R}.",
    ) parsesTo abilities(
        landfall(
            counters(Objects.Self, 1, quest),
            doing(Effect.RevealUntil(filter = not(land), library = Players.You, count = n(1), reveal = false, into = Zone.Exile, foundBind = "found", restBind = "rest")),
            untilEndOfTurn(Objects.Bound("found"), Mechanic.MayPlay(from = Zone.Exile, how = PlayKind.Play, holder = PlayerFilter.You)),
            ifThen(Condition.ObjectMatches(Objects.Self, ObjectFilter.HasCounter(quest, 5)),
                doing(Effect.AddMana(ManaProduction(ManaKind.Fixed(ManaCost.parse("{R}{R}")!!)), Players.You))),
        ),
    )

    // CR 207.2c, 122.1a (+1/+1 counters), 115.1 (target creature you control)
    @Test
    fun syndicateRepossessors() = card(
        name = "Syndicate Repossessors",
        cost = "{4}{G}",
        type = "Creature — Orion Pirate",
        pt = "4/4",
        text = """
            Trample
            Landfall — Whenever a land you control enters, put two +1/+1 counters on target creature you control.
        """,
    ) parsesTo abilities(
        Keyword.Trample,
        landfall(counters(target("creature"), 2), targets = listOf(slot("creature", and(creature, you)))),
    )

    // ---- Mill

    // CR 701.17a, 110.4a (permanent card), 601.2i (the first spell you've cast this game: judged as it is cast)
    @Test
    fun firstGreeting() = card(
        name = "First Greeting",
        cost = "{1}{G}",
        type = "Instant",
        text = "Mill four cards. You may put a permanent card from among them into your hand. If this spell is the first spell you've cast this game, you gain 2 life. (To mill four cards, put the top four cards of your library into your graveyard.)",
    ) parsesTo abilities(
        spell(
            *millFourKeepPermanent,
            ifThen(Condition.AsCast(Condition.Compare(Value.SpellsCastThisGame(Players.You), Comparison.Equal, n(1))), gainLife(2)),
        ),
    )

    // CR 701.17a, 111.4 (a named Planet land token)
    @Test
    fun chronalRift() = card(
        name = "Chronal Rift",
        cost = null,
        type = "Land",
        text = """
            {T}: Add {C}.
            {3}{G}, {T}, Sacrifice this land: Mill four cards. You may put a permanent card from among them into your hand. Create a tapped Planet land token named New Planet with "{T}: Add one mana of any color."
        """,
    ) parsesTo abilities(
        tapFor("C"),
        activated(cost(mana("{3}{G}"), tap, sacrificeSelf), *millFourKeepPermanent, newPlanet),
    )

    // CR 701.17a, 608.2c ("If you can't": no land card in your graveyard to return)
    @Test
    fun chronalRaider() = card(
        name = "Chronal Raider",
        cost = "{2}{G}",
        type = "Creature — Na'kuhl Warrior",
        pt = "2/3",
        text = "When this creature enters, mill three cards. Then return a land card from your graveyard to your hand. If you can't, create a Treasure token. (To mill three cards, put the top three cards of your library into your graveyard.)",
    ) parsesTo abilities(
        triggered(entersSelf,
            mill(3),
            Step.Choose(Choice.Objects(inYourGraveyard(land), min = n(1), max = n(1)), bind = "chosen", chooser = Players.You),
            move(Objects.Bound("chosen"), Zone.Hand),
            ifThen(Condition.Not(Condition.NonEmpty("chosen")), createTreasure()),
        ),
    )

    // ---- Treasure

    // CR 701.21a (only a permanent's controller sacrifices it: "you can't sacrifice" = Treasure tokens you control can't be), 111.10a
    @Test
    fun brintHumbleProprietor() = card(
        name = "Brint, Humble Proprietor",
        cost = "{2}{R}",
        type = "Legendary Creature — Ferengi Rogue",
        pt = "1/2",
        text = """
            You can't sacrifice Treasure tokens.
            At the beginning of each upkeep, create a Treasure token. (It's an artifact with "{T}, Sacrifice this token: Add one mana of any color.")
        """,
    ) parsesTo abilities(
        static(Mechanic.CantBeSacrificed, affects = Objects.Each(and(treasures, token, you))),
        triggered(stepBegins(TurnStep.Upkeep, PlayerFilter.Any), createTreasure()),
    )

    // CR 714.2b (chapter abilities), 603.7b (a delayed trigger "whenever ... this turn" triggers each time), 120.4 ("that many": the damage dealt)
    @Test
    fun theQuietLife() = card(
        name = "The Quiet Life",
        cost = "{3}{G}{W}",
        type = "Enchantment — Saga",
        text = """
            (As this Saga enters and after your draw step, add a lore counter. Sacrifice after III.)
            I — Return target green or white creature card from your graveyard to the battlefield.
            II — Choose target creature. Whenever it deals combat damage to a player this turn, you draw that many cards.
            III — You gain life equal to the greatest power among creatures you control. Create a Treasure token.
        """,
    ) parsesTo abilities(
        chapter(1, move(target("card"), Zone.Battlefield),
            targets = listOf(slot("card", inYourGraveyard(or(ObjectFilter.OfColor(ColorRef.Literal(Color.Green)), ObjectFilter.OfColor(ColorRef.Literal(Color.White))), creature)))),
        chapter(2,
            doing(Effect.CreateDelayedTrigger(
                on(EventPattern.Damage(ObjectFilter.Is(target("creature")), RecipientFilter(players = PlayerFilter.Any), combat = true)),
                Instructions(steps = listOf(doing(Effect.Draw(Players.You, Value.EventAmount)))),
                duration = Duration.EndOfTurn, repeats = true,
            )),
            targets = listOf(slot("creature", creature)),
        ),
        chapter(3,
            doing(Effect.GainLife(Players.You, Value.Of(NumericProperty.Power, Objects.Each(and(creature, you)), Combine.Greatest))),
            createTreasure(),
        ),
    )

    // CR 613.4c (a P/T change counted continuously), 603.2h ("This ability triggers only once each turn")
    @Test
    fun grandNagusVosk() = card(
        name = "Grand Nagus Vosk",
        cost = "{2}{B}",
        type = "Legendary Creature — Ferengi Noble",
        pt = "1/1",
        text = """
            Grand Nagus Vosk gets +1/+1 for each Treasure you control.
            Whenever another creature dies, create a Treasure token. This ability triggers only once each turn. (It's an artifact with "{T}, Sacrifice this token: Add one mana of any color.")
        """,
    ) parsesTo abilities(
        static(Mechanic.ModifyPowerToughness(count(and(treasures, you)), count(and(treasures, you)))),
        triggered(on(EventPattern.ZoneChange(and(another, creature), from = Zone.Battlefield, to = Zone.Graveyard)), createTreasure(), perTurn = 1),
    )

    // CR 602.2b, 302.6 (both abilities tap it: one a turn), 605.1a (the first is a mana ability)
    @Test
    fun shrewdProfiteer() = card(
        name = "Shrewd Profiteer",
        cost = "{1}{R}",
        type = "Creature — Ferengi Rogue",
        pt = "2/1",
        text = """
            {T}: Add {R}.
            {3}, {T}: Create a Treasure token. (It's an artifact with "{T}, Sacrifice this token: Add one mana of any color.")
        """,
    ) parsesTo abilities(
        tapFor("R"),
        activated(cost(mana("{3}"), tap), createTreasure()),
    )

    // ---- Cast permission, replacement

    // CR 601.2a (cast from the graveyard by permission, once each of your turns), 400.7d (what the spell cast this way gets), 122.1h (a finality counter)
    @Test
    fun spilloverSubmatrix() = card(
        name = "Spillover Submatrix",
        cost = "{2}{B}",
        type = "Artifact Creature — Borg",
        pt = "3/3",
        text = "Once during each of your turns, you may cast an artifact creature spell from your graveyard. If you cast a spell this way, that artifact creature enters with a finality counter on it. (If a creature with a finality counter on it would die, exile it instead.)",
    ) parsesTo abilities(
        Ability.Static(listOf(Mechanic.MayPlay(Zone.Graveyard, PlayKind.Cast, perTurn = 1, holder = PlayerFilter.You, spellGets = listOf(
            Mechanic.Replacement(EventPattern.ZoneChange(ObjectFilter.Is(Objects.Affected), to = Zone.Battlefield),
                modify = listOf(Modification.EntersWithCounters(CounterKind.Finality, n(1))))))),
            affects = Affected.Objects(Objects.Each(and(artifact, creature, inZone(Zone.Graveyard), ObjectFilter.OwnedBy(PlayerFilter.You)))),
            condition = Condition.YourTurn),
    )

    // CR 614.1a, 614.8 (a destruction replacement that isn't regeneration), 120.6 (damage healed), 122.1a
    @Test
    fun drHalvorSurlySurgeon() = card(
        name = "Dr. Halvor, Surly Surgeon",
        cost = "{2}{W}{W}",
        type = "Legendary Creature — Human Doctor",
        pt = "2/2",
        text = """
            When Dr. Halvor enters, put a +1/+1 counter on another target creature you control.
            If another creature you control with a +1/+1 counter on it would be destroyed, instead remove all +1/+1 counters from it and all damage already dealt to it is healed.
        """,
    ) parsesTo abilities(
        triggered(entersSelf, counters(target("creature")), targets = listOf(slot("creature", and(another, creature, you)))),
        static(Mechanic.Replacement(EventPattern.Destroyed(and(another, creature, you, ObjectFilter.HasCounter(p1p1, 1))), instead = Instructions(steps = listOf(
            doing(Effect.RemoveCounters(Objects.EventObject(EventRole.Before), p1p1,
                Value.Of(NumericProperty.Counters(p1p1), Objects.EventObject(EventRole.Before), Combine.Single))),
            doing(Effect.RemoveAllDamage(Objects.EventObject(EventRole.Before))),
        )))),
    )
}
