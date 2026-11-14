package arcanum.parser.sets.lci

import arcanum.api.Ability
import arcanum.api.Affected
import arcanum.api.CardType
import arcanum.api.Choice
import arcanum.api.Comparison
import arcanum.api.Condition
import arcanum.api.Cost
import arcanum.api.CostObjects
import arcanum.api.CostPart
import arcanum.api.CounterKind
import arcanum.api.Combine
import arcanum.api.Destination
import arcanum.api.Duration
import arcanum.api.Effect
import arcanum.api.EventPattern
import arcanum.api.Instructions
import arcanum.api.Keyword
import arcanum.api.ManaKind
import arcanum.api.ManaProduction
import arcanum.api.Measure
import arcanum.api.Mechanic
import arcanum.api.NumericProperty
import arcanum.api.ObjectFilter
import arcanum.api.Objects
import arcanum.api.PlayerFilter
import arcanum.api.Players
import arcanum.api.SharedQuality
import arcanum.api.Step
import arcanum.api.Subtype
import arcanum.api.SubtypeRef
import arcanum.api.Supertype
import arcanum.api.Value
import arcanum.api.Zone
import arcanum.api.Color
import arcanum.api.ColorRef
import arcanum.api.Layout
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.cardFaces
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

/** LCI chunk 07: fathomless descent (the number of permanent cards in your graveyard), mill and scry. */
class LciChunk07FathomlessMillScryTest {
    private val fathomless = "Fathomless descent"
    /** "the number of permanent cards in your graveyard" (CR 110.4a, 404.1). */
    private val permanentCards = count(inYourGraveyard(permanentCard))
    private val minusX = Mechanic.ModifyPowerToughness(Value.Times(permanentCards, -1), Value.Times(permanentCards, -1))
    private val landmark = CounterKind.Named("landmark")
    /** CR 700.11: "you descended this turn": a permanent card was put into your graveyard from anywhere this turn. */
    private val descended = Condition.HappenedThisTurn(EventPattern.ZoneChange(and(permanentCard, cardFilter, ObjectFilter.OwnedBy(PlayerFilter.You)),
        to = Zone.Graveyard), atLeast = 1, measure = Measure.Objects)

    // CR 110.4a: only permanent types count (artifact, battle, creature, enchantment, land, planeswalker); CR 602.5b: an activation
    // restriction; CR 106.1b, 107.3c: X mana of one colour, X counted as the ability resolves
    @Test
    fun ixtalliTheDeepGate() = cardFaces(Layout.Transform,
        card(
            name = "Ixtalli, the Deep Gate",
            cost = "{3}",
            type = "Legendary Artifact",
            text = """
                {T}: Draw a card, then discard a card.
                {4}, {T}: Transform Ixtalli. Activate only if there are four or more permanent types among cards in your graveyard. (Artifact, battle, creature, enchantment, land, and planeswalker are permanent types.)
            """,
        ) to abilities(
            activated(cost(tap), draw(1), discard(1)),
            activated(cost(mana("{4}"), tap), transformSelf, rules = anyTime.copy(condition = Condition.Compare(
                Value.CountDistinct(Objects.Each(and(inZone(Zone.Graveyard), ObjectFilter.OwnedBy(PlayerFilter.You))), SharedQuality.PermanentType),
                Comparison.AtLeast, n(4)))),
        ),
        card(
            name = "The Hollow Heart",
            cost = null,
            type = "Legendary Land",
            text = """
                (Transforms from Ixtalli.)
                Fathomless descent — {T}: Add X mana of any one color, where X is the number of permanent cards in your graveyard.
            """,
        ) to abilities(
            activated(cost(tap), doing(Effect.AddMana(ManaProduction(ManaKind.Choose(permanentCards)), Players.You))).copy(abilityWord = fathomless),
        ),
    )

    // CR 115.1, 608.2b: the mana value is compared with the number as the spell is cast and as it resolves
    @Test
    fun wrigglingResurgence() = card(
        name = "Wriggling Resurgence",
        cost = "{1}{B}{G}",
        type = "Sorcery",
        text = "Fathomless descent — Return to the battlefield target nonland permanent card in your graveyard with mana value less than or equal to the number of permanent cards in your graveyard.",
    ) parsesTo abilities(
        spell(move(target("card"), Zone.Battlefield),
            targets = listOf(slot("card", inYourGraveyard(nonland, permanentCard, ObjectFilter.ManaValue(Comparison.AtMost, permanentCards))))
        ).copy(abilityWord = fathomless),
    )

    // CR 611.2c: the creatures affected are fixed as it resolves; CR 107.3c: X counted then
    @Test
    fun dreadwaterSurge() = card(
        name = "Dreadwater Surge",
        cost = "{2}{B}{B}",
        type = "Sorcery",
        text = "Fathomless descent — All creatures get -X/-X until end of turn, where X is the number of permanent cards in your graveyard.",
    ) parsesTo abilities(
        spell(doing(Effect.ApplyMechanics(listOf(minusX), Affected.Objects(Objects.Each(creature)), Duration.EndOfTurn))).copy(abilityWord = fathomless),
    )

    // CR 603.3d: X counted as the trigger resolves
    @Test
    fun gloomfangEcho() = card(
        name = "Gloomfang Echo",
        cost = "{2}{B}{B}",
        type = "Creature — Beast Horror Spirit",
        pt = "3/2",
        text = "Fathomless descent — When this creature enters, target creature an opponent controls gets -X/-X until end of turn, where X is the number of permanent cards in your graveyard.",
    ) parsesTo abilities(
        triggered(entersSelf, doing(Effect.ApplyMechanics(listOf(minusX), Affected.Objects(target("creature")), Duration.EndOfTurn)),
            targets = listOf(slot("creature", creatureOpponentControls)), abilityWord = fathomless),
    )

    // CR 601.2f: one of two additional costs; CR 604.3: its P/T is defined in all zones
    @Test
    fun remnantsOfTheFallen() = card(
        name = "Remnants of the Fallen",
        cost = "{1}{B}",
        type = "Creature — Spirit",
        pt = "*/1+*", // printed "*/*+1": the same value (see printedStarPlusN)
        text = """
            As an additional cost to cast this spell, discard a card or sacrifice a permanent.
            Fathomless descent — Remnants of the Fallen's power is equal to the number of permanent cards in your graveyard and its toughness is equal to that number plus 1.
        """,
    ) parsesTo abilities(
        Ability.Static(listOf(Mechanic.AdditionalCost(cost = Cost(listOf(CostPart.OneOf(listOf(
            Cost(listOf(CostPart.Discard(CostObjects.Choose(cardFilter, count = n(1))))),
            Cost(listOf(CostPart.Sacrifice(CostObjects.Choose(permanentFilter, count = n(1))))),
        )))))), affects = Affected.Objects(Objects.Self)),
        static(Mechanic.SetPowerToughness(permanentCards, Value.Sum(listOf(permanentCards, n(1)))), characteristicDefining = true).copy(abilityWord = fathomless),
    )

    // CR 208.2a: a printed toughness "*+1" is the star plus 1, the same as "1+*"
    @Test
    fun printedStarPlusN() = kotlin.test.assertEquals(arcanum.api.PrintedNumber.Star(1), arcanum.parser.PrintedNumbers.parse("*+1"))

    // CR 701.17a: mill; CR 115.10a: the creature cards are chosen as it resolves, not targeted (up to two: fewer or none)
    @Test
    fun secondBreath() = card(
        name = "Second Breath",
        cost = "{2}{B}",
        type = "Instant",
        text = "You may mill two cards. Then return up to two creature cards from your graveyard to your hand. (To mill two cards, put the top two cards of your library into your graveyard.)",
    ) parsesTo abilities(
        spell(Step.May(listOf(mill(2)), Players.You),
            Step.Choose(Choice.Objects(inYourGraveyard(creature), n(0), n(2)), bind = "chosen", chooser = Players.You),
            move(Objects.Bound("chosen"), Zone.Hand)),
    )

    // CR 305.7 not applied: "It's still a land" keeps its land types (CR 205.1b); CR 508.1m: the attack trigger
    @Test
    fun brinehollowReef() = card(
        name = "Brinehollow Reef",
        cost = null,
        type = "Land",
        text = """
            This land enters tapped.
            {T}: Add {U} or {B}.
            {2}{U}{B}: Until end of turn, this land becomes a 4/4 blue and black Shark creature with deathtouch. It's still a land.
            Whenever this land attacks, target player mills four cards.
        """,
    ) parsesTo abilities(
        entersTapped,
        addEither("U", "B"),
        activated(cost(mana("{2}{U}{B}")), untilEndOfTurn(Objects.Self,
            Mechanic.SetTypes(add = listOf(CardType.Creature), addSubtypes = listOf(SubtypeRef.Literal(Subtype("Shark")))),
            Mechanic.SetColors(listOf(ColorRef.Literal(Color.Blue), ColorRef.Literal(Color.Black))),
            Mechanic.SetPowerToughness(n(4), n(4)),
            Mechanic.GainAbilities(listOf(Keyword.Deathtouch)))),
        triggered(attacksSelf, doing(Effect.Mill(Players.Target("player"), n(4))), targets = listOf(targetPlayer)),
    )

    // CR 700.2: one mode; CR 400.7, 603.10a: each creature that left under your control this turn counts once
    @Test
    fun tazcasOutrider() = card(
        name = "Tazca's Outrider",
        cost = "{2}{W}",
        type = "Creature — Cat Warrior",
        pt = "3/1",
        text = """
            Flash
            When this creature enters, choose one —
            • Put a +1/+1 counter on this creature for each creature that left the battlefield under your control this turn.
            • You gain 2 life and scry 2.
            • Exile target player's graveyard.
        """,
    ) parsesTo abilities(
        Keyword.Flash,
        Ability.Triggered(entersSelf, Instructions(modes = modes(
            mode(counters(Objects.Self, Value.EventsThisTurn(EventPattern.ZoneChange(and(creature, you), from = Zone.Battlefield), Measure.Objects))),
            mode(gainLife(2), scry(2)),
            mode(move(Objects.Each(and(inZone(Zone.Graveyard), ObjectFilter.OwnedBy(PlayerFilter.Is(Players.Target("player"))))), Zone.Exile),
                targets = listOf(targetPlayer)),
        ))),
    )

    // CR 700.11, 603.4: "if you descended this turn" checked as the end step begins and as it resolves
    @Test
    fun cryptDiverBat() = card(
        name = "Crypt-Diver Bat",
        cost = "{W}",
        type = "Creature — Bat",
        pt = "1/1",
        text = """
            Flying, lifelink
            At the beginning of your end step, if you descended this turn, scry 1. (You descended if a permanent card was put into your graveyard from anywhere.)
        """,
    ) parsesTo abilities(
        Keyword.Flying,
        Keyword.Lifelink,
        triggered(yourEndStep, scry(1), interveningIf = descended),
    )

    // CR 122.1, 701.28a: the third landmark counter removes them, transforms it and creates three Treasures
    @Test
    fun explorersChart() = cardFaces(Layout.Transform,
        card(
            name = "Explorer's Chart",
            cost = "{2}",
            type = "Artifact",
            text = """
                {1}, {T}: Scry 1. Put a landmark counter on this artifact. Then if there are three or more landmark counters on it, remove those counters, transform this artifact, and create three Treasure tokens. (They're artifacts with "{T}, Sacrifice this token: Add one mana of any color.")
            """,
        ) to abilities(
            activated(cost(mana("{1}"), tap), scry(1), counters(Objects.Self, 1, landmark),
                ifThen(Condition.ObjectMatches(Objects.Self, ObjectFilter.HasCounter(landmark, atLeast = 3)),
                    doing(Effect.RemoveCounters(Objects.Self, landmark, Value.Of(NumericProperty.Counters(landmark), Objects.Self, Combine.Single))),
                    transformSelf,
                    createTreasure(n(3)))),
        ),
        card(
            name = "Hidden Hoard Cove",
            cost = null,
            type = "Land",
            text = """
                (Transforms from Explorer's Chart.)
                {T}: Add {C}.
                {T}, Sacrifice a Treasure: Draw a card.
            """,
        ) to abilities(
            addOne("{C}"),
            activated(cost(tap, sacrificeA(sub("Treasure"))), draw(1)),
        ),
    )

    // CR 122.1: counters of all kinds together; CR 613.1f: the abilities while it has them
    @Test
    fun sentinelOfTheUpperVault() = card(
        name = "Sentinel of the Upper Vault",
        cost = "{W}",
        type = "Creature — Human Soldier",
        pt = "1/2",
        text = """
            As long as this creature has three or more counters on it, it has flying and vigilance.
            Tap three untapped artifacts and/or creatures you control: Put a +1/+1 counter on this creature. Scry 1. Activate only as a sorcery.
        """,
    ) parsesTo abilities(
        static(has(Keyword.Flying, Keyword.Vigilance), condition = Condition.ObjectMatches(Objects.Self, ObjectFilter.HasCounter(null, atLeast = 3))),
        activated(cost(CostPart.TapObjects(CostObjects.Choose(or(artifact, creature), n(3)))), counters(Objects.Self), scry(1), rules = asSorcery),
    )

    // CR 701.26a: "becomes tapped" for any reason
    @Test
    fun watchfulScribeGnome() = card(
        name = "Watchful Scribe-Gnome",
        cost = "{1}{W}",
        type = "Artifact Creature — Gnome",
        pt = "2/2",
        text = "Whenever this creature becomes tapped, scry 1. (Look at the top card of your library. You may put that card on the bottom.)",
    ) parsesTo abilities(
        triggered(on(EventPattern.Tap(ObjectFilter.Is(Objects.Self))), scry(1)),
    )

    // CR 509.1b: a blocking restriction for the turn
    @Test
    fun grottoTrampler() = card(
        name = "Grotto Trampler",
        cost = "{4}{G}{G}",
        type = "Creature — Dinosaur",
        pt = "7/7",
        text = """
            When this creature enters, scry 2. (Look at the top two cards of your library, then put any number of them on the bottom and the rest on top in any order.)
            {3}{G}: This creature can't be blocked by creatures with power 2 or less this turn.
        """,
    ) parsesTo abilities(
        triggered(entersSelf, scry(2)),
        activated(cost(mana("{3}{G}")), untilEndOfTurn(Objects.Self, Mechanic.CantBeBlockedBy(and(creature, power(Comparison.AtMost, 2))))),
    )

    // CR 701.22a, 111.10a
    @Test
    fun graspingCorsair() = card(
        name = "Grasping Corsair",
        cost = "{B}",
        type = "Creature — Human Pirate",
        pt = "1/1",
        text = """
            When this creature dies, scry 1 and create a Treasure token. (To scry 1, look at the top card of your library. You may put that card on the bottom. A Treasure token is an artifact with "{T}, Sacrifice this token: Add one mana of any color.")
        """,
    ) parsesTo abilities(
        triggered(diesSelf, scry(1), createTreasure()),
    )

    // CR 700.2b: a mode needing a target can't be chosen without one
    @Test
    fun ruinsifterDelvers() = card(
        name = "Ruinsifter Delvers",
        cost = "{4}{W}",
        type = "Creature — Human Artificer Scout",
        pt = "4/4",
        text = """
            When this creature enters, choose one —
            • Return target artifact card from your graveyard to your hand.
            • Scry 3. (Look at the top three cards of your library, then put any number of them on the bottom and the rest on top in any order.)
        """,
    ) parsesTo abilities(
        Ability.Triggered(entersSelf, Instructions(modes = modes(
            mode(move(target("card"), Zone.Hand), targets = listOf(slot("card", inYourGraveyard(artifact)))),
            mode(scry(3)),
        ))),
    )

    // CR 205.4c: "nonbasic land" is a land without the basic supertype
    @Test
    fun beamOfUnmaking() = card(
        name = "Beam of Unmaking",
        cost = "{4}{B}",
        type = "Sorcery",
        text = "Exile target creature, Vehicle, or nonbasic land. Scry 1. (Look at the top card of your library. You may put that card on the bottom.)",
    ) parsesTo abilities(
        spell(move(target("permanent"), Zone.Exile), scry(1),
            targets = listOf(slot("permanent", or(creature, sub("Vehicle"), and(not(ObjectFilter.Super(Supertype.Basic)), land))))),
    )
}
