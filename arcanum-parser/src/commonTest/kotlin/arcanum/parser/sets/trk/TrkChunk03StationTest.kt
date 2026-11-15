package arcanum.parser.sets.trk

import arcanum.api.*
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

/** TRK chunk 03: station Spacecraft (CR 702.184, 722.2) and the abilities around it. */
class TrkChunk03StationTest {
    private val officer = creatureToken("Officer", listOf(Color.Red), 1, 1)
    private val notCreature = Condition.Not(Condition.ObjectMatches(Objects.Self, creature))
    private val stationText = "Station (Tap another creature you control: Put charge counters equal to its power on this Spacecraft. Station only as a sorcery. It's an artifact creature at"
    private fun discover(count: Int): Step = Step.Do(Effect.Action(KeywordAction.Discover(n(count))))

    // CR 702.184, 722.2b: station symbol with P/T box
    @Test
    fun classGShuttlecraft() = card(
        name = "Class-G Shuttlecraft",
        cost = "{3}",
        type = "Artifact — Spacecraft",
        pt = "2/4",
        text = """
            {T}: Add one mana of any color.
            $stationText 6+.)
            6+ | Flying, vigilance
        """,
    ) parsesTo abilities(
        activated(cost(tap), doing(Effect.AddMana(ManaProduction(ManaKind.Choose(n(1))), Players.You))),
        Keyword.Station,
        station(6, Keyword.Flying, Keyword.Vigilance, pt = "2/4"),
    )

    // CR 722.2a: the station symbol's ability is a trigger; CR 603.7a delayed sacrifice
    @Test
    fun d8BattleCruiser() = card(
        name = "D-8 Battle Cruiser",
        cost = "{1}{R}",
        type = "Artifact — Spacecraft",
        pt = "3/3",
        text = """
            $stationText 8+.)
            3+ | At the beginning of combat on your turn, create a 2/1 red Klingon Warrior creature token with haste. Sacrifice it at the beginning of the next end step.
            8+ | Flying
        """,
    ) parsesTo abilities(
        Keyword.Station,
        station(3, triggered(beginningOfCombatOnYourTurn,
            create(creatureToken("Klingon Warrior", listOf(Color.Red), 2, 1, listOf(Keyword.Haste)), bind = "token"),
            doing(Effect.CreateDelayedTrigger(stepBegins(TurnStep.End, PlayerFilter.Any),
                Instructions(steps = listOf(doing(Effect.Sacrifice(Objects.Bound("token"), by = Players.You)))))))),
        station(8, Keyword.Flying, pt = "3/3"),
    )

    // CR 702.184, 701.21 (tap), 122.1g (stun counter)
    @Test
    fun enterpriseNx02() = card(
        name = "Enterprise NX-02",
        cost = "{3}{U}",
        type = "Legendary Artifact — Spacecraft",
        pt = "4/3",
        text = """
            When Enterprise NX-02 enters, tap target creature an opponent controls and put a stun counter on it. (If a permanent with a stun counter would become untapped, remove one from it instead.)
            $stationText 6+.)
            6+ | Flying, vigilance
        """,
    ) parsesTo abilities(
        triggered(entersSelf, tapIt(target("creature")), counters(target("creature"), 1, CounterKind.Stun),
            targets = listOf(slot("creature", creatureOpponentControls))),
        Keyword.Station,
        station(6, Keyword.Flying, Keyword.Vigilance, pt = "4/3"),
    )

    // CR 701.23 (search, reveal, shuffle)
    @Test
    fun oberthClassStarship() = card(
        name = "Oberth-Class Cruiser",
        cost = "{1}{W}",
        type = "Artifact — Spacecraft",
        pt = "2/2",
        text = """
            When this Spacecraft enters, search your library for a basic Plains card, reveal it, put it into your hand, then shuffle.
            $stationText 6+.)
            6+ | Flying
        """,
    ) parsesTo abilities(
        triggered(entersSelf, search(and(ObjectFilter.Super(Supertype.Basic), sub("Plains")), reveal = true), move(Objects.Bound("found"), Zone.Hand), shuffle),
        Keyword.Station,
        station(6, Keyword.Flying, pt = "2/2"),
    )

    // CR 613.4c ("other" excludes this object), 700.4 (dies), 603.4 (intervening if), 122.1 (charge counter on this object)
    @Test
    fun sarcophagusShipOfTheDeadTest() = card(
        name = "Ossuary, Ship of the Dead",
        cost = "{2}{R}",
        type = "Legendary Artifact — Spacecraft",
        pt = "4/4",
        text = """
            Other creatures you control get +1/+0.
            Whenever a creature dies, if Ossuary isn't a creature, put a charge counter on it.
            $stationText 10+.)
            10+ | Flying
        """,
    ) parsesTo abilities(
        static(pt(1, 0), affects = Objects.Each(and(another, creatureYouControl))),
        triggered(on(EventPattern.ZoneChange(creature, from = Zone.Battlefield, to = Zone.Graveyard)), counters(Objects.Self, 1, charge),
            interveningIf = notCreature),
        Keyword.Station,
        station(10, Keyword.Flying, pt = "4/4"),
    )

    @Test
    fun suurokClassScienceVessel() = card(
        name = "Sooroc-Class Vessel",
        cost = "{2}{U}",
        type = "Artifact — Spacecraft",
        pt = "3/2",
        text = """
            When this Spacecraft enters, draw a card.
            $stationText 5+.)
            5+ | Flying
        """,
    ) parsesTo abilities(
        triggered(entersSelf, draw(1)),
        Keyword.Station,
        station(5, Keyword.Flying, pt = "3/2"),
    )

    // CR 111.2: the trigger's controller creates the tokens
    @Test
    fun cerritosFollowUp() = card(
        name = "U.S.S. Ceres",
        cost = "{5}",
        type = "Legendary Artifact — Spacecraft",
        pt = "4/4",
        text = """
            When U.S.S. Ceres enters, create two 1/1 red Officer creature tokens.
            $stationText 6+.)
            6+ | Flying
        """,
    ) parsesTo abilities(
        triggered(entersSelf, create(officer, n(2))),
        Keyword.Station,
        station(6, Keyword.Flying, pt = "4/4"),
    )

    // CR 701.57a: discover; one trigger with two events
    @Test
    fun discoveryAtBlackAlert() = card(
        name = "U.S.S. Dawn, at Red Alert",
        cost = "{3}{G}{G}",
        type = "Legendary Artifact — Spacecraft",
        pt = "5/5",
        text = """
            Whenever U.S.S. Dawn enters or attacks, discover 5. (Exile cards from the top of your library until you exile a nonland card with mana value 5 or less. Cast it without paying its mana cost or put it into your hand. Put the rest on the bottom in a random order.)
            Station
            6+ | Flying
        """,
    ) parsesTo abilities(
        triggered(entersOrAttacksSelf, discover(5)),
        Keyword.Station,
        station(6, Keyword.Flying, pt = "5/5"),
    )

    // CR 702.21 (ward), 111.2
    @Test
    fun enterpriseA() = card(
        name = "U.S.S. Dusk, Free Spirit",
        cost = "{3}{W}{W}",
        type = "Legendary Artifact — Spacecraft",
        pt = "5/5",
        text = """
            Ward {2}
            When U.S.S. Dusk enters, create three 1/1 red Officer creature tokens.
            $stationText 6+.)
            6+ | Flying, lifelink
        """,
    ) parsesTo abilities(
        Keyword.Ward(manaCost("{2}")),
        triggered(entersSelf, create(officer, n(3))),
        Keyword.Station,
        station(6, Keyword.Flying, Keyword.Lifelink, pt = "5/5"),
    )

    // CR 122.6 ("for the first time each turn"), 601.2a, 305.1: may play the exiled card this turn
    @Test
    fun enterpriseD() = card(
        name = "U.S.S. Moonrise, Galaxy-Class",
        cost = "{3}",
        type = "Legendary Artifact — Spacecraft",
        pt = "4/5",
        text = """
            Whenever one or more charge counters are put on U.S.S. Moonrise for the first time each turn, exile the top card of your library. You may play that card this turn.
            $stationText 7+.)
            7+ | Flying, vigilance
        """,
    ) parsesTo abilities(
        triggered(Trigger.On(listOf(EventPattern.CountersPut(ObjectFilter.Is(Objects.Self), charge)), oneOrMore = true, nthThisTurn = 1),
            Step.Do(Effect.Move(Objects.LibraryCards(Players.You, n(1), fromTop = true), Destination(Zone.Exile), postBind = "exiled")),
            Step.Do(Effect.ApplyMechanics(listOf(Mechanic.MayPlay(Zone.Exile, PlayKind.Play, holder = PlayerFilter.You)),
                Affected.Objects(Objects.Bound("exiled")), Duration.EndOfTurn))),
        Keyword.Station,
        station(7, Keyword.Flying, Keyword.Vigilance, pt = "4/5"),
    )

    @Test
    fun excelsior() = card(
        name = "U.S.S. Zenith, Cutting Edge",
        cost = "{3}{W}",
        type = "Legendary Artifact — Spacecraft",
        pt = "4/4",
        text = """
            When U.S.S. Zenith enters, destroy target tapped creature an opponent controls.
            $stationText 8+.)
            8+ | Flying
        """,
    ) parsesTo abilities(
        triggered(entersSelf, destroy(target("creature")), targets = listOf(slot("creature", and(tapped, creatureOpponentControls)))),
        Keyword.Station,
        station(8, Keyword.Flying, pt = "4/4"),
    )

    // CR 122.1h (finality counter), 603.12
    @Test
    fun reliant() = card(
        name = "U.S.S. Nadir, From Spite",
        cost = "{2}{B}{B}",
        type = "Legendary Artifact — Spacecraft",
        pt = "4/3",
        text = """
            Whenever U.S.S. Nadir enters or deals combat damage to a player, return target creature card with mana value 4 or less from your graveyard to the battlefield with a finality counter on it.
            $stationText 10+.)
            10+ | Flying
        """,
    ) parsesTo abilities(
        triggered(Trigger.On(listOf(EventPattern.ZoneChange(ObjectFilter.Is(Objects.Self), to = Zone.Battlefield),
            EventPattern.Damage(ObjectFilter.Is(Objects.Self), RecipientFilter(players = PlayerFilter.Any), combat = true))),
            Step.Do(Effect.Move(target("card"), Destination(Zone.Battlefield, counters = listOf(CountersToPut(CounterKind.Finality, n(1)))))),
            targets = listOf(slot("card", inYourGraveyard(creature, manaValue(Comparison.AtMost, 4))))),
        Keyword.Station,
        station(10, Keyword.Flying, pt = "4/3"),
    )

    // CR 702.188? no: CR 207.2c ability word; CR 604.1 static; hexproof only while this is not a creature (CR 700.7 types)
    @Test
    fun cloakingDevice() = card(
        name = "Romulan Raptor",
        cost = "{1}{U}",
        type = "Artifact — Spacecraft",
        pt = "4/4",
        text = """
            Cloaking Device — As long as this Spacecraft isn't a creature, it has hexproof.
            Whenever you cast a noncreature spell, if this Spacecraft isn't a creature, put two charge counters on it.
            $stationText 8+.)
            8+ | Flying
        """,
    ) parsesTo abilities(
        Ability.Static(listOf(Mechanic.GainAbilities(listOf(Keyword.Hexproof))), affects = Affected.Objects(Objects.Self), condition = notCreature, abilityWord = "Cloaking Device"),
        triggered(youCast(noncreatureSpell), counters(Objects.Self, 2, charge), interveningIf = notCreature),
        Keyword.Station,
        station(8, Keyword.Flying, pt = "4/4"),
    )

    // CR 701.13 (exile), 400.7 (the cards are new objects), 603.1 (one ability, two trigger events), 205.2 (permanent cards by type)
    @Test
    fun starbaseTwelve() = card(
        name = "Starbase Twelve",
        cost = "{9}",
        type = "Legendary Artifact — Spacecraft",
        text = """
            When Starbase Twelve enters and at the beginning of your upkeep, exile the top four cards of your library. Put all permanent cards exiled this way onto the battlefield.
            Station (Tap another creature you control: Put charge counters equal to its power on this Spacecraft. Station only as a sorcery.)
            9+ | Creatures you control get +3/+3.
        """,
    ) parsesTo abilities(
        triggered(Trigger.On(listOf(EventPattern.ZoneChange(ObjectFilter.Is(Objects.Self), to = Zone.Battlefield), EventPattern.StepBegins(TurnStep.Upkeep, PlayerFilter.You))),
            Step.Do(Effect.Move(Objects.LibraryCards(Players.You, n(4), fromTop = true), Destination(Zone.Exile), postBind = "exiled")),
            Step.Do(Effect.Move(Objects.Where(Objects.Bound("exiled"), and(permanentCard, inZone(Zone.Exile))), Destination(Zone.Battlefield)))),
        Keyword.Station,
        station(9, static(pt(3, 3), affects = Objects.Each(creatureYouControl))),
    )

    // CR 500.7 (extra turn), 603.4: "haven't begun an extra turn this game" is a game-long condition on the player
    @Test
    fun hmsBoon() = card(
        name = "H.M.S. Boon, Unlikely Timeship",
        cost = "{4}{U}",
        type = "Legendary Artifact — Spacecraft",
        pt = "3/4",
        text = """
            When H.M.S. Boon enters, you may search your library for a Whale card, reveal it, put it into your hand, then shuffle.
            Station
            8+ | Flying
            Whenever H.M.S. Boon deals combat damage to a player, draw a card. If you control a Whale and haven't begun an extra turn this game, take an extra turn after this one.
        """,
    ) parsesTo abilities(
        triggered(entersSelf, Step.May(listOf(search(sub("Whale"), reveal = true), move(Objects.Bound("found"), Zone.Hand), shuffle), Players.You)),
        Keyword.Station,
        station(8, Keyword.Flying, pt = "3/4"),
        triggered(combatDamageToAPlayer, draw(1),
            ifThen(Condition.And(listOf(Condition.Exists(and(sub("Whale"), you), atLeast = 1), Condition.Not(Condition.BegunExtraTurnThisGame(PlayerFilter.You)))),
                doing(Effect.ExtraTurn(Players.You)))),
    )

    // CR 701.20 (reveal until), 400.7: the found cards are put onto the battlefield tapped, the rest on the bottom in a random order
    @Test
    fun uSsWayfarer() = card(
        name = "U.S.S. Wayfarer, Lone Wanderer",
        cost = "{3}{G}",
        type = "Legendary Artifact — Spacecraft",
        pt = "3/3",
        text = """
            When U.S.S. Wayfarer enters, reveal cards from the top of your library until you reveal two land cards. Put those land cards onto the battlefield tapped and the rest on the bottom of your library in a random order.
            Station (Tap another creature you control: Put charge counters equal to its power on this Spacecraft. Station only as a sorcery. It's an artifact creature at 8+.)
            8+ | Flying, vigilance
        """,
    ) parsesTo abilities(
        triggered(entersSelf,
            Step.Do(Effect.RevealUntil(land, library = Players.You, count = n(2), reveal = true, foundBind = "found", restBind = "rest")),
            Step.Do(Effect.Move(Objects.Bound("found"), Destination(Zone.Battlefield, tapped = true))),
            Step.Do(Effect.Move(Objects.Bound("rest"), Destination(Zone.Library, libraryPosition = -1, randomOrder = true)))),
        Keyword.Station,
        station(8, Keyword.Flying, Keyword.Vigilance, pt = "3/3"),
    )
}
