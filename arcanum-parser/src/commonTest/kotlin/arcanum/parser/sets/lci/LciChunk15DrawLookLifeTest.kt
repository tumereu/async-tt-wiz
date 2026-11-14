package arcanum.parser.sets.lci

import arcanum.api.*
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

/** LCI chunk 15: draw, look at the top cards, life, minimum blockers, conditional pumps and moves between zones. */
class LciChunk15DrawLookLifeTest {
    private fun dest(zone: Zone, position: Int? = null, random: Boolean = false) =
        Destination(zone, libraryPosition = position, randomOrder = random)

    private fun lookTop(count: Int, bind: String) = Step.Do(Effect.LookAt(Objects.LibraryCards(Players.You, n(count), fromTop = true), Players.You, bind = bind))

    private fun pick(filter: ObjectFilter, from: String, bind: String, min: Int = 1) =
        Step.Choose(Choice.Objects(filter, min = n(min), max = n(1), from = Objects.Bound(from)), bind = bind, chooser = Players.You)

    private fun moveBound(name: String, to: Destination) = Step.Do(Effect.Move(Objects.Bound(name), to))

    private fun moveRest(all: String, minus: String, to: Destination) =
        Step.Do(Effect.Move(Objects.Except(Objects.Bound(all), Objects.Bound(minus)), to))

    /** "Look at the top N cards of your library. Put one of them into your hand and the rest into your graveyard." */
    private fun lookOneHandRestGraveyard(count: Int, bind: String, picked: String) = arrayOf(
        lookTop(count, bind),
        pick(ObjectFilter.Any, bind, picked),
        moveBound(picked, dest(Zone.Hand)),
        moveRest(bind, picked, dest(Zone.Graveyard)),
    )

    private fun timesResolved(times: Int) = Condition.Compare(Value.AbilityUsesThisTurn(resolved = true), Comparison.Equal, n(times))

    // CR 121.1: draw one card for each other Dinosaur you control
    @Test
    fun quakebackColossus() = card(
        name = "Quakeback Colossus",
        cost = "{4}{G}{G}",
        type = "Creature — Dinosaur",
        pt = "6/6",
        text = "Trample\nWhen this creature enters, draw a card for each other Dinosaur you control.",
    ) parsesTo abilities(
        Keyword.Trample,
        triggered(entersSelf, doing(Effect.Draw(Players.You, count(and(another, sub("Dinosaur"), you))))),
    )

    // CR 603.6a, 603.6c: enters or is put into a graveyard from the battlefield; draw a card and lose 1 life
    @Test
    fun fetidPhial() = card(
        name = "Fetid Phial",
        cost = "{1}{B}",
        type = "Artifact",
        text = "When this artifact enters or is put into a graveyard from the battlefield, you draw a card and you lose 1 life.",
    ) parsesTo abilities(
        triggered(
            Trigger.On(listOf(
                EventPattern.ZoneChange(ObjectFilter.Is(Objects.Self), to = Zone.Battlefield),
                EventPattern.ZoneChange(ObjectFilter.Is(Objects.Self), from = Zone.Battlefield, to = Zone.Graveyard),
            )),
            draw(1), loseLife(1),
        ),
    )

    // CR 702.11 (hexproof), 510.1c (assign combat damage by toughness)
    @Test
    fun boulderbackTortoise() = card(
        name = "Boulderback Tortoise",
        cost = "{3}{G}",
        type = "Creature — Turtle",
        pt = "0/6",
        text = "During your turn, creatures you control have hexproof.\nEach creature you control with toughness greater than its power assigns combat damage equal to its toughness rather than its power.",
    ) parsesTo abilities(
        static(has(Keyword.Hexproof), affects = Objects.Each(creatureYouControl), condition = Condition.YourTurn),
        static(
            Mechanic.AssignsDamageByToughness,
            affects = Objects.Each(and(creatureYouControl, ObjectFilter.Toughness(Comparison.Greater, power(Objects.Tested)))),
        ),
    )

    // CR 701.21 (sacrifice): the Nth resolution this turn picks the effect
    @Test
    fun vesperZealotOfAshgul() = card(
        name = "Vesper, Zealot of Ashgul",
        cost = "{2}{W}{B}",
        type = "Legendary Creature — Vampire Demon",
        pt = "4/4",
        text = "Flying\nWhenever you sacrifice another permanent, you gain 2 life if this is the first time this ability has resolved this turn. If it's the second time, each opponent loses 2 life. If it's the third time, create a 4/3 white and black Vampire Demon creature token with flying.",
    ) parsesTo abilities(
        Keyword.Flying,
        triggered(
            on(EventPattern.Sacrifice(and(another, you))),
            ifThen(timesResolved(1), gainLife(2)),
            ifThen(timesResolved(2), loseLife(2, Players.Each(PlayerFilter.Opponent))),
            ifThen(timesResolved(3), create(creatureToken("Vampire Demon", listOf(Color.White, Color.Black), 4, 3,
                abilities = listOf(Keyword.Flying), name = "Vampire Demon Token"))),
        ),
    )

    // CR 603.4 (intervening if), 701.20e (look at the top cards)
    @Test
    fun ulmakTideReader() = card(
        name = "Ulmak, Tide Reader",
        cost = "{2}{U}",
        type = "Legendary Creature — Human Advisor",
        pt = "1/5",
        text = "At the beginning of each player's end step, if an artifact entered the battlefield under your control this turn, look at the top two cards of your library. Put one of them into your hand and the other into your graveyard.",
    ) parsesTo abilities(
        triggered(
            stepBegins(TurnStep.End, PlayerFilter.Any),
            *lookOneHandRestGraveyard(2, "looked", "chosen"),
            interveningIf = Condition.HappenedThisTurn(EventPattern.ZoneChange(and(artifact, you), to = Zone.Battlefield), atLeast = 1, measure = Measure.Events),
        ),
    )

    // CR 700.2 (modes), 701.20e, 118.12 (counter unless its controller pays)
    @Test
    fun bafflingConundrum() = card(
        name = "Baffling Conundrum",
        cost = "{2}{U}",
        type = "Instant",
        text = "Choose one —\n• Look at the top four cards of your library. Put one of them into your hand and the rest into your graveyard.\n• Counter target spell unless its controller pays {4}.",
    ) parsesTo abilities(
        Ability.Spell(Instructions(modes = modes(
            mode(*lookOneHandRestGraveyard(4, "looked", "picked")),
            mode(
                Step.Pay(Cost(listOf(mana("{4}"))), payer = Players.ControllerOf(target("spell")), bind = "paid"),
                ifThen(Condition.Not(Condition.Did("paid")), doing(Effect.Counter(target("spell")))),
                targets = listOf(slot("spell", spellFilter)),
            ),
        ))),
    )

    // CR 701.20e: look at the top two, one to hand, the other to graveyard
    @Test
    fun tumbledPuzzleBox() = card(
        name = "Tumbled Puzzle-Box",
        cost = "{U}",
        type = "Artifact",
        text = "{1}, {T}, Sacrifice this artifact: Look at the top two cards of your library. Put one of those cards into your hand and the other into your graveyard.",
    ) parsesTo abilities(
        activated(cost(mana("{1}"), tap, sacrificeSelf), *lookOneHandRestGraveyard(2, "looked", "chosen")),
    )

    // CR 701.20a, 701.20e, 401.4 (random order on the bottom)
    @Test
    fun steadfastDeckhand() = card(
        name = "Steadfast Deckhand",
        cost = "{1}{U}",
        type = "Creature — Human Pirate",
        pt = "2/1",
        text = "When this creature enters, look at the top four cards of your library. You may reveal an artifact or Pirate card from among them and put it into your hand. Put the rest on the bottom of your library in a random order.",
    ) parsesTo abilities(
        triggered(
            entersSelf,
            lookTop(4, "seen"),
            pick(or(artifact, sub("Pirate")), "seen", "chosen", min = 0),
            Step.Do(Effect.Reveal(Objects.Bound("chosen"))),
            moveBound("chosen", dest(Zone.Hand)),
            moveRest("seen", "chosen", dest(Zone.Library, position = -1, random = true)),
        ),
    )

    // CR 119.3, 118.3: sacrifice another creature or artifact as a cost; each opponent loses 1 life and you gain 1 life
    @Test
    fun votaryOfAshgul() = card(
        name = "Votary of Ashgul",
        cost = "{2}{B}",
        type = "Creature — Vampire Cleric",
        pt = "1/4",
        text = "{T}, Sacrifice another creature or artifact: Each opponent loses 1 life and you gain 1 life.",
    ) parsesTo abilities(
        activated(
            cost(tap, sacrificeA(and(another, or(creature, artifact)))),
            loseLife(1, Players.Each(PlayerFilter.Opponent)), gainLife(1),
        ),
    )

    // CR 509.1b: can't be blocked except by three or more creatures
    @Test
    fun stompingCeratops() = card(
        name = "Stomping Ceratops",
        cost = "{4}{R}",
        type = "Creature — Dinosaur",
        pt = "5/4",
        text = "This creature can't be blocked except by three or more creatures.",
    ) parsesTo abilities(
        static(Mechanic.MinimumBlockers(3)),
    )

    // CR 611.3a: while you control an artifact, +1/+0 and haste
    @Test
    fun goblinVaultRaider() = card(
        name = "Goblin Vault Raider",
        cost = "{R}",
        type = "Creature — Goblin Pirate",
        pt = "1/2",
        text = "As long as you control an artifact, this creature gets +1/+0 and has haste.",
    ) parsesTo abilities(
        static(pt(1, 0), has(Keyword.Haste), condition = Condition.Exists(and(artifact, you), atLeast = 1)),
    )

    // CR 400.7: any number (zero allowed) of creature cards from hand to the battlefield
    @Test
    fun gharnStampedeBrute() = card(
        name = "Gharn, Stampede Brute",
        cost = "{5}{G}{G}{G}",
        type = "Legendary Creature — Elder Dinosaur",
        pt = "12/12",
        text = "Trample\nWhen Gharn enters, put any number of creature cards from your hand onto the battlefield.",
    ) parsesTo abilities(
        Keyword.Trample,
        triggered(
            entersSelf,
            Step.Choose(Choice.Objects(and(creature, inZone(Zone.Hand), ObjectFilter.OwnedBy(PlayerFilter.You)), min = n(0), max = null), bind = "chosen", chooser = Players.You),
            moveBound("chosen", dest(Zone.Battlefield)),
        ),
    )

    // CR 110.5b: a creature card returned to the battlefield tapped
    @Test
    fun lendingHand() = card(
        name = "Lending Hand",
        cost = "{W}",
        type = "Sorcery",
        text = "Return target creature card with mana value 3 or less from your graveyard to the battlefield tapped.",
    ) parsesTo abilities(
        spell(
            move(target("card"), Zone.Battlefield, tapped = true),
            targets = listOf(slot("card", inYourGraveyard(creature, manaValue(Comparison.AtMost, 3)))),
        ),
    )

    // CR 406.1 (exile): the targeted opponent chooses the card to exile from their hand
    @Test
    fun bonecapSlug() = card(
        name = "Bonecap Slug",
        cost = "{1}{B}",
        type = "Creature — Fungus Snail",
        pt = "1/1",
        text = "When this creature enters, target opponent exiles a card from their hand.",
    ) parsesTo abilities(
        triggered(
            entersSelf,
            Step.Choose(Choice.Objects(and(inZone(Zone.Hand), ObjectFilter.OwnedBy(PlayerFilter.Is(Players.Target("opponent")))), min = n(1), max = n(1)), bind = "chosen", chooser = Players.Target("opponent")),
            moveBound("chosen", dest(Zone.Exile)),
            targets = listOf(targetOpponent),
        ),
    )

    // CR 122.1h (finality counter), 602.5d (activate only as a sorcery)
    @Test
    fun spiritcoilAdder() = card(
        name = "Spiritcoil Adder",
        cost = "{2}{B}",
        type = "Creature — Snake",
        pt = "2/3",
        text = "{B}, {T}, Sacrifice this creature: Return target creature card from your graveyard to the battlefield with a finality counter on it. Activate only as a sorcery. (If a creature with a finality counter on it would die, exile it instead.)",
    ) parsesTo abilities(
        activated(
            cost(mana("{B}"), tap, sacrificeSelf),
            Step.Do(Effect.Move(target("card"), Destination(Zone.Battlefield, counters = listOf(CountersToPut(CounterKind.Finality, n(1)))))),
            targets = listOf(slot("card", inYourGraveyard(creature))),
            rules = asSorcery,
        ),
    )

    // CR 400.3, 401.4: the owner of the target chooses top or bottom
    @Test
    fun fatefulPlunge() = card(
        name = "Fateful Plunge",
        cost = "{3}{U}",
        type = "Instant",
        text = "Target artifact or creature's owner puts it on their choice of the top or bottom of their library.",
    ) parsesTo abilities(
        spell(
            Step.Choose(Choice.Option(listOf("top", "bottom")), bind = "position", chooser = Players.OwnerOf(target("permanent"))),
            ifThen(Condition.Chose("position", "top"), move(target("permanent"), Zone.Library, position = 0),
                otherwise = listOf(move(target("permanent"), Zone.Library, position = -1))),
            targets = listOf(slot("permanent", or(artifact, creature))),
        ),
    )
}
