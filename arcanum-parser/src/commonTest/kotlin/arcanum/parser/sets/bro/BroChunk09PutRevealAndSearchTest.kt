package arcanum.parser.sets.bro

import arcanum.api.Ability
import arcanum.api.Affected
import arcanum.api.Choice
import arcanum.api.Combine
import arcanum.api.Comparison
import arcanum.api.Condition
import arcanum.api.CostPart
import arcanum.api.CounterKind
import arcanum.api.Destination
import arcanum.api.Duration
import arcanum.api.Each
import arcanum.api.Effect
import arcanum.api.EventPattern
import arcanum.api.Keyword
import arcanum.api.Mechanic
import arcanum.api.NumericProperty
import arcanum.api.ObjectFilter
import arcanum.api.Objects
import arcanum.api.PlayKind
import arcanum.api.PlayerFilter
import arcanum.api.Players
import arcanum.api.Step
import arcanum.api.Trigger
import arcanum.api.TurnStep
import arcanum.api.Value
import arcanum.api.Zone
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

/** BRO chunk 09: playing from the top of the library, putting cards from graveyards and libraries, revealing and searching. */
class BroChunk09PutRevealAndSearchTest {
    private val yours = ObjectFilter.OwnedBy(PlayerFilter.You)
    private val fromAGraveyard = and(cardFilter, inZone(Zone.Graveyard))
    private val bottom = Destination(Zone.Library, libraryPosition = -1, randomOrder = true)

    // CR 601.2a, 305.1: permissions to play from the top of the library; CR 118.9: once a turn without paying, from either zone
    @Test
    fun unityOfAllLibraries() = card(
        name = "Unity of All Libraries",
        cost = "{6}{U}{U}",
        type = "Enchantment",
        text = """
            You may look at the top card of your library any time.
            You may play lands and cast spells from the top of your library.
            Once during each of your turns, you may cast a spell from your hand or the top of your library without paying its mana cost.
        """,
    ) parsesTo abilities(
        playerStatic(Mechanic.MayLookAt(Objects.LibraryCards(Players.You, n(1), fromTop = true))),
        static(Mechanic.MayPlay(Zone.Library, PlayKind.Play, topOnly = true, holder = PlayerFilter.You),
            affects = Objects.Each(and(land, inZone(Zone.Library), yours))),
        static(Mechanic.MayPlay(Zone.Library, PlayKind.Cast, topOnly = true, holder = PlayerFilter.You),
            affects = Objects.Each(and(nonland, inZone(Zone.Library), yours))),
        Ability.Static(
            listOf(
                Mechanic.MayPlay(Zone.Hand, PlayKind.Cast, withoutPaying = true, perTurn = 1, holder = PlayerFilter.You),
                Mechanic.MayPlay(Zone.Library, PlayKind.Cast, topOnly = true, withoutPaying = true, perTurn = 1, holder = PlayerFilter.You),
            ),
            affects = Affected.Objects(Objects.Each(and(cardFilter, or(inZone(Zone.Hand), inZone(Zone.Library)), yours))),
            condition = Condition.YourTurn,
        ),
    )

    // CR 400.7: "other" is this card, now in the graveyard; CR 115.1: up to one target
    @Test
    fun burrowingHoardweaver() = card(
        name = "Burrowing Hoardweaver",
        cost = "{3}{G}",
        type = "Creature — Spider",
        pt = "2/3",
        text = """
            Reach, deathtouch
            When this creature dies, put up to one other target card from a graveyard on the bottom of its owner's library.
        """,
    ) parsesTo abilities(
        Keyword.Reach,
        Keyword.Deathtouch,
        triggered(diesSelf, move(target("card"), Zone.Library, position = -1),
            targets = listOf(slot("card", and(not(ObjectFilter.Is(Objects.SelfAfter)), fromAGraveyard), min = 0))),
    )

    // CR 122.1d: stun counters; CR 115.1: up to two targets
    @Test
    fun enforcedStillnessRay() = card(
        name = "Enforced Stillness Ray",
        cost = "{3}{U}",
        type = "Sorcery",
        text = "Tap up to two target artifacts and/or creatures. Put two stun counters on each of them. (If a permanent with a stun counter would become untapped, remove one from it instead.)",
    ) parsesTo abilities(
        spell(
            tapIt(target("creature")),
            counters(target("creature"), 2, CounterKind.Stun),
            targets = listOf(slot("creature", or(artifact, creature), min = 0, max = 2)),
        ),
    )

    // CR 107.3a: up to X, the X paid for the spell
    @Test
    fun restorersRebuild() = card(
        name = "Restorer's Rebuild",
        cost = "{X}{W}{W}{W}",
        type = "Sorcery",
        text = "Look at the top seven cards of your library. Put up to X artifact and/or creature cards with mana value 3 or less from among them onto the battlefield. Put the rest on the bottom of your library in a random order.",
    ) parsesTo abilities(
        spell(
            doing(Effect.LookAt(Objects.LibraryCards(Players.You, n(7), fromTop = true), Players.You, bind = "looked")),
            Step.Choose(Choice.Objects(and(or(artifact, creature), manaValue(Comparison.AtMost, 3)), n(0), Value.Announced(name = "X"),
                from = Objects.Bound("looked")), bind = "picked", chooser = Players.You),
            move(Objects.Bound("picked"), Zone.Battlefield),
            doing(Effect.Move(Objects.Except(Objects.Bound("looked"), Objects.Bound("picked")), bottom)),
            announced = listOf("X"),
        ),
    )

    // CR 110.2a: under your control; CR 205.1b: a subtype in addition, for good (CR 611.2a)
    @Test
    fun gateOfGrimConquest() = card(
        name = "Gate of Grim Conquest",
        cost = "{9}",
        type = "Artifact",
        text = """
            When this artifact enters, each opponent sacrifices three creatures of their choice.
            At the beginning of your upkeep, put target creature card from a graveyard onto the battlefield under your control. It's a Phyrexian in addition to its other types.
        """,
    ) parsesTo abilities(
        triggered(
            entersSelf,
            Step.ForEach(Each.Players(Players.Each(PlayerFilter.Opponent)), "opponent", listOf(
                Step.Choose(Choice.Objects(and(creature, ObjectFilter.ControlledBy(PlayerFilter.Is(Players.Bound("opponent")))), n(3), n(3)),
                    bind = "sacrificed", chooser = Players.Bound("opponent")),
                doing(Effect.Sacrifice(Objects.Bound("sacrificed"))),
            ), together = true),
        ),
        triggered(
            stepBegins(TurnStep.Upkeep, PlayerFilter.You),
            move(target("card"), Zone.Battlefield, postBind = "returned", controller = Players.You),
            doing(Effect.ApplyMechanics(listOf(Mechanic.SetTypes(addSubtypes = listOf(subtype("Phyrexian")))), Affected.Objects(Objects.Bound("returned")),
                Duration.Indefinitely)),
            targets = listOf(slot("card", and(creature, inZone(Zone.Graveyard)))),
        ),
    )

    // CR 115.1: up to one target, any graveyard
    @Test
    fun quickcogWyvern() = card(
        name = "Quickcog Wyvern",
        cost = "{5}",
        type = "Artifact Creature — Drake",
        pt = "2/4",
        text = """
            Flying, haste
            When this creature enters, put up to one target card from a graveyard on the bottom of its owner's library.
        """,
    ) parsesTo abilities(
        Keyword.Flying,
        Keyword.Haste,
        triggered(entersSelf, move(target("card"), Zone.Library, position = -1), targets = listOf(slot("card", fromAGraveyard, min = 0))),
    )

    // CR 701.20a: reveal any number of them
    @Test
    fun castingTheKeel() = card(
        name = "Casting the Keel",
        cost = "{2}{U}",
        type = "Sorcery",
        text = "Look at the top five cards of your library. You may reveal any number of artifact cards from among them and put the revealed cards into your hand. Put the rest on the bottom of your library in a random order.",
    ) parsesTo abilities(
        spell(
            doing(Effect.LookAt(Objects.LibraryCards(Players.You, n(5), fromTop = true), Players.You, bind = "seen")),
            Step.Choose(Choice.Objects(artifact, n(0), null, from = Objects.Bound("seen")), bind = "revealed", chooser = Players.You),
            doing(Effect.Reveal(Objects.Bound("revealed"))),
            move(Objects.Bound("revealed"), Zone.Hand),
            doing(Effect.Move(Objects.Except(Objects.Bound("seen"), Objects.Bound("revealed")), bottom)),
        ),
    )

    // CR 101.4, 608.2e: every player at once, so each count is taken before anything enters
    @Test
    fun overflowingOnslaught() = card(
        name = "Overflowing Onslaught",
        cost = "{5}{R}{R}",
        type = "Sorcery",
        text = "Each player reveals a number of cards from the top of their library equal to the number of nonland permanents they control, puts all permanent cards they revealed this way onto the battlefield, and puts the rest into their graveyard.",
    ) parsesTo abilities(
        spell(
            Step.ForEach(Each.Players(Players.Each(PlayerFilter.Any)), "player", listOf(
                doing(Effect.Reveal(Objects.LibraryCards(Players.Bound("player"),
                    count(and(nonland, permanentFilter, ObjectFilter.ControlledBy(PlayerFilter.Is(Players.Bound("player"))))), fromTop = true), bind = "revealed")),
                Step.Bind(Objects.Where(Objects.Bound("revealed"), permanentCard), "put"),
                move(Objects.Bound("put"), Zone.Battlefield),
                move(Objects.Except(Objects.Bound("revealed"), Objects.Bound("put")), Zone.Graveyard),
            ), together = true),
        ),
    )

    // CR 701.23a: search for up to three, then shuffle
    @Test
    fun acolytesOfTheScrapheap() = card(
        name = "Acolytes of the Scrapheap",
        cost = "{4}{B}{B}",
        type = "Creature — Phyrexian Human",
        pt = "4/4",
        text = "When this creature enters, search your library for up to three artifact cards, put them into your graveyard, then shuffle.",
    ) parsesTo abilities(
        triggered(entersSelf, search(artifact, max = 3), move(Objects.Bound("found"), Zone.Graveyard), shuffle),
    )

    // CR 201.4: a chosen card name; CR 400.7: the cards that were in the hand are named before they're exiled
    @Test
    fun theGraniteMind() = card(
        name = "The Granite Mind",
        cost = "{2}",
        type = "Legendary Artifact",
        text = "{2}, {T}, Exile The Granite Mind: Choose a card name. Search target opponent's graveyard, hand, and library for up to four cards with that name and exile them. That player shuffles, then draws a card for each card exiled from their hand this way. Activate only as a sorcery.",
    ) parsesTo abilities(
        activated(
            cost(mana("{2}"), tap, CostPart.Exile(arcanum.api.CostObjects.Self, Zone.Battlefield)),
            Step.Choose(Choice.CardName(ObjectFilter.Any), bind = "name", chooser = Players.You),
            doing(Effect.Search(Players.Target("opponent"), ObjectFilter.ChosenName("name"), max = n(4),
                zones = listOf(Zone.Graveyard, Zone.Hand, Zone.Library), bind = "found", searcher = Players.You)),
            Step.Bind(Objects.Where(Objects.Bound("found"), inZone(Zone.Hand)), "exiled from hand"),
            move(Objects.Bound("found"), Zone.Exile),
            doing(Effect.Shuffle(Players.Target("opponent"))),
            doing(Effect.Draw(Players.Target("opponent"), Value.Count(Objects.Bound("exiled from hand")))),
            targets = listOf(targetOpponent),
            rules = asSorcery,
        ),
    )

    // CR 603.2: an enters trigger with a filter comparing the creature's own power and toughness
    @Test
    fun balanceLattice() = card(
        name = "Balance Lattice",
        cost = "{4}",
        type = "Artifact",
        text = "Whenever a creature you control with power equal to its toughness enters, you may pay {1}. If you do, draw a card.",
    ) parsesTo abilities(
        triggered(
            enters(and(creature, you, ObjectFilter.Power(Comparison.Equal, Value.Of(NumericProperty.Toughness, Objects.Tested, Combine.Single)))),
            Step.Pay(cost = cost(mana("{1}")), payer = Players.You, bind = "paid"),
            Step.If(Condition.Did("paid"), then = listOf(draw(1))),
        ),
    )

    // CR 700.4 (put into a graveyard from the battlefield), CR 406.3: or into exile from the battlefield; CR 111.10h: Powerstone
    @Test
    fun cinderrockFoundry() = card(
        name = "Cinderrock Foundry",
        cost = "{4}",
        type = "Artifact",
        text = "Whenever this artifact or another nontoken artifact you control is put into a graveyard from the battlefield or is put into exile from the battlefield, create a tapped Powerstone token. (It's an artifact with \"{T}: Add {C}. This mana can't be spent to cast a nonartifact spell.\")",
    ) parsesTo abilities(
        triggered(
            Trigger.On(listOf(Zone.Graveyard, Zone.Exile).map { to ->
                EventPattern.ZoneChange(or(isSelf, and(another, not(token), artifact, you)), from = Zone.Battlefield, to = to)
            }),
            createPowerstone(),
        ),
    )

}
