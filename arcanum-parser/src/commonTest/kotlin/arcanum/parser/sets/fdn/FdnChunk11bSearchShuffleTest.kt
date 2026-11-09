package arcanum.parser.sets.fdn

import arcanum.api.Affected
import arcanum.api.Choice
import arcanum.api.Comparison
import arcanum.api.Combine
import arcanum.api.Condition
import arcanum.api.CounterKind
import arcanum.api.NumericProperty
import arcanum.api.CostObjects
import arcanum.api.CostPart
import arcanum.api.Destination
import arcanum.api.Duration
import arcanum.api.Effect
import arcanum.api.EventPattern
import arcanum.api.EventRole
import arcanum.api.Keyword
import arcanum.api.Mechanic
import arcanum.api.Modification
import arcanum.api.ObjectFilter
import arcanum.api.Objects
import arcanum.api.PlayerFilter
import arcanum.api.Players
import arcanum.api.Step
import arcanum.api.SubtypeRef
import arcanum.api.Subtype
import arcanum.api.Value
import arcanum.api.Zone
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

/** Chunk 11b of FDN: search / shuffle / sacrifice / choose wordings of eight cards. Invented names, the real oracle text. */
class FdnChunk11bSearchShuffleTest {
    private val enteringSelf = EventPattern.ZoneChange(ObjectFilter.Is(Objects.Self), to = Zone.Battlefield)
    private val yourGraveyardCards = Objects.Each(and(inZone(Zone.Graveyard), ObjectFilter.OwnedBy(PlayerFilter.You)))
    private val landController = Players.ControllerOf(target("land"))
    private val anyAmount = Value.Announced(name = "X")

    // CR 701.23a (search), 701.24 (shuffle), 400.7; "that land's controller" is the destroyed land's controller (CR 108.4) and searches first
    @Test
    fun demolitionField() = card(
        name = "Quarry Wasteland",
        cost = null,
        type = "Land",
        text = """
            {T}: Add {C}.
            {2}, {T}, Sacrifice this land: Destroy target nonbasic land an opponent controls. That land's controller may search their library for a basic land card, put it onto the battlefield, then shuffle. You may search your library for a basic land card, put it onto the battlefield, then shuffle.
        """,
    ) parsesTo abilities(
        activated(cost(tap), doing(Effect.AddMana(arcanum.api.ManaProduction(arcanum.api.ManaKind.Fixed(arcanum.api.ManaCost.parse("{C}")!!)), Players.You))),
        activated(cost(mana("{2}"), tap, sacrificeSelf),
            destroy(target("land")),
            Step.May(player = landController, steps = listOf(
                Step.Do(Effect.Search(landController, basicLand(), max = n(1), zones = listOf(Zone.Library), bind = "found", searcher = landController)),
                move(Objects.Bound("found"), Zone.Battlefield, controller = landController),
                Step.Do(Effect.Shuffle(landController)))),
            Step.May(player = Players.You, steps = listOf(search(basicLand()), move(Objects.Bound("found"), Zone.Battlefield), shuffle)),
            targets = listOf(slot("land", and(not(ObjectFilter.Super(arcanum.api.Supertype.Basic)), land, opponents)))),
    )

    // CR 701.23a, 406.1 (exile), 603.10a (leaves-the-battlefield: dies), 603.7?: the exiled card is remembered for the second ability
    @Test
    fun hoardingDragon() = card(
        name = "Cache Wyrm",
        cost = "{3}{R}{R}",
        type = "Creature — Dragon",
        pt = "4/4",
        text = """
            Flying
            When this creature enters, you may search your library for an artifact card, exile it, then shuffle.
            When this creature dies, you may put the exiled card into its owner's hand.
        """,
    ) parsesTo abilities(
        Keyword.Flying,
        triggered(entersSelf, Step.May(listOf(search(artifact), move(Objects.Bound("found"), Zone.Exile, postBind = "exiled"),
            doing(Effect.Remember("exiled", Objects.Bound("exiled"))), shuffle), player = Players.You)),
        triggered(diesSelf, Step.May(listOf(move(Objects.Remembered("exiled"), Zone.Hand)), player = Players.You)),
    )

    // CR 701.24a (shuffle a graveyard into a library: the cards move, then the library is shuffled), 400.3
    @Test
    fun feldonsCane() = card(
        name = "Ashen Reset",
        cost = "{1}",
        type = "Artifact",
        text = "{T}, Exile this artifact: Shuffle your graveyard into your library.",
    ) parsesTo abilities(
        activated(cost(tap, CostPart.Exile(CostObjects.Self, from = Zone.Battlefield)), move(yourGraveyardCards, Zone.Library), shuffle),
    )

    // CR 608.2e ("instead"), 107.3 (X), 701.26 (untap), 402.2 (no maximum hand size), 701.24
    @Test
    fun finaleOfRevelation() = card(
        name = "Tide of Revelation",
        cost = "{X}{U}{U}",
        type = "Sorcery",
        text = """
            Draw X cards. If X is 10 or more, instead shuffle your graveyard into your library, draw X cards, untap up to five lands, and you have no maximum hand size for the rest of the game.
            Exile Tide of Revelation.
        """,
    ) parsesTo abilities(
        spell(
            Step.If(Condition.Compare(anyAmount, Comparison.AtLeast, n(10)),
                then = listOf(
                    move(yourGraveyardCards, Zone.Library), shuffle,
                    Step.Do(Effect.Draw(Players.You, anyAmount)),
                    Step.Choose(Choice.Objects(land, n(0), n(5)), bind = "untapped", chooser = Players.You),
                    untapIt(Objects.Bound("untapped")),
                    Step.Do(Effect.ApplyMechanics(listOf(Mechanic.NoMaximumHandSize), Affected.Players(Players.You), Duration.Indefinitely))),
                otherwise = listOf(Step.Do(Effect.Draw(Players.You, anyAmount)))),
            move(Objects.Self, Zone.Exile),
            announced = listOf("X"),
        ),
    )

    // CR 603.10a (looks back: it as it last existed), 400.7, 122.1, 613.1d (layer 4: Demon added for good)
    @Test
    fun infernalVessel() = card(
        name = "Cinder Vessel",
        cost = "{2}{B}",
        type = "Creature — Human Cleric",
        pt = "2/1",
        text = "When this creature dies, if it wasn't a Demon, return it to the battlefield under its owner's control with two +1/+1 counters on it. It's a Demon in addition to its other types.",
    ) parsesTo abilities(
        triggered(diesSelf,
            Step.Do(Effect.Move(Objects.EventObject(EventRole.After), Destination(Zone.Battlefield, controller = Players.OwnerOf(Objects.EventObject(EventRole.After)),
                counters = listOf(arcanum.api.CountersToPut(p1p1, n(2)))), postBind = "returned")),
            Step.Do(Effect.ApplyMechanics(listOf(Mechanic.SetTypes(addSubtypes = listOf(SubtypeRef.Literal(Subtype("Demon"))))),
                Affected.Objects(Objects.Bound("returned")), Duration.Indefinitely)),
            interveningIf = Condition.Not(Condition.ObjectMatches(Objects.EventObject(EventRole.Before), sub("Demon")))),
    )

    // CR 614.12 (as it enters), 607.2d (chosen type), 122.1, 614.1c (enters with counters), 613.4c (+1/+1 for each counter)
    @Test
    fun bannerOfKinship() = card(
        name = "Standard of Kin",
        cost = "{5}",
        type = "Artifact",
        text = """
            As this artifact enters, choose a creature type. This artifact enters with a fellowship counter on it for each creature you control of the chosen type.
            Creatures you control of the chosen type get +1/+1 for each fellowship counter on this artifact.
        """,
    ) parsesTo abilities(
        static(Mechanic.Replacement(enteringSelf, modify = listOf(Modification.AsEnters(listOf(
            Step.Choose(Choice.CreatureType, bind = "type", remember = true, chooser = Players.You)))))),
        static(Mechanic.Replacement(enteringSelf, modify = listOf(Modification.EntersWithCounters(CounterKind.Named("fellowship"),
            count(and(creature, you, ObjectFilter.Sub(SubtypeRef.Remembered("type")))))))),
        static(Mechanic.ModifyPowerToughness(Value.Of(NumericProperty.Counters(CounterKind.Named("fellowship")), Objects.Self, Combine.Single),
            Value.Of(NumericProperty.Counters(CounterKind.Named("fellowship")), Objects.Self, Combine.Single)),
            affects = Objects.Each(and(creature, you, ObjectFilter.Sub(SubtypeRef.Remembered("type"))))),
    )

    // CR 701.21 (sacrifice: the player picks), 107.1 (a number may be any), 603.4? no: CR 118.9 (alternative cost) with a condition checked on cast
    @Test
    fun blasphemousEdict() = card(
        name = "Grim Ultimatum",
        cost = "{3}{B}{B}",
        type = "Sorcery",
        text = """
            You may pay {B} rather than pay this spell's mana cost if there are thirteen or more creatures on the battlefield.
            Each player sacrifices thirteen creatures of their choice.
        """,
    ) parsesTo abilities(
        static(Mechanic.AlternativeCost(cost = manaCost("{B}")), condition = Condition.Exists(creature, 13)),
        spell(Step.ForEach(arcanum.api.Each.Players(Players.Each(PlayerFilter.Any)), "player", together = true, steps = listOf(
            Step.Choose(Choice.Objects(and(creature, ObjectFilter.ControlledBy(PlayerFilter.Is(Players.Bound("player")))), n(13), n(13)), bind = "sacrificed", chooser = Players.Bound("player")),
            doing(Effect.Sacrifice(Objects.Bound("sacrificed")))))),
    )

    // CR 701.21, 608.2c (that creature: the one sacrificed, as it last existed, CR 608.2h), 119.1
    @Test
    fun tributeToHunger() = card(
        name = "Famine Toll",
        cost = "{2}{B}",
        type = "Instant",
        text = "Target opponent sacrifices a creature of their choice. You gain life equal to that creature's toughness.",
    ) parsesTo abilities(
        spell(
            Step.Choose(Choice.Objects(and(creature, ObjectFilter.ControlledBy(PlayerFilter.Is(Players.Target("opponent")))), n(1), n(1)), bind = "sacrificed", chooser = Players.Target("opponent")),
            doing(Effect.Sacrifice(Objects.Bound("sacrificed"))),
            gainLife(0).let { Step.Do(Effect.GainLife(Players.You, toughness(Objects.Bound("sacrificed")))) },
            targets = listOf(targetOpponent)),
    )
}
