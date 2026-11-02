package arcanum.parser.sets.dsk

import arcanum.api.Ability
import arcanum.api.Affected
import arcanum.api.CardType
import arcanum.api.Choice
import arcanum.api.Combine
import arcanum.api.Comparison
import arcanum.api.Condition
import arcanum.api.CounterKind
import arcanum.api.Destination
import arcanum.api.Duration
import arcanum.api.Effect
import arcanum.api.EventPattern
import arcanum.api.EventRole
import arcanum.api.Instructions
import arcanum.api.Keyword
import arcanum.api.Mechanic
import arcanum.api.NumericProperty
import arcanum.api.ObjectCategory
import arcanum.api.ObjectFilter
import arcanum.api.Objects
import arcanum.api.PlayerFilter
import arcanum.api.Players
import arcanum.api.SharedQuality
import arcanum.api.Step
import arcanum.api.Subtype
import arcanum.api.SubtypeRef
import arcanum.api.TargetKind
import arcanum.api.TargetSlot
import arcanum.api.Trigger
import arcanum.api.Value
import arcanum.api.Zone
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.parsesTo
import arcanum.parser.sets.dsk.Dsk.create
import arcanum.parser.sets.dsk.Dsk.creature
import arcanum.parser.sets.dsk.Dsk.glimmer
import arcanum.parser.sets.dsk.Dsk.manifestDread
import arcanum.parser.sets.dsk.Dsk.survival
import arcanum.parser.sets.dsk.Dsk.youControl
import kotlin.test.Test

/**
 * Duskmourn chunk 04: survival ("At the beginning of your second main phase, if this creature is tapped, …": the first
 * postcombat main phase of the turn, CR 505.1a–b, with an intervening "if", CR 603.4) and the other abilities of the
 * survivors: keyword counters (CR 122.1b), "this ability triggers only once", a choice of counter, putting a dead
 * creature's counters on another, cards exiled with a creature (CR 607.2a), revealing until a land, cards with different
 * powers from among revealed cards, a land becoming a creature.
 */
class DskChunk04SurvivalTest {
    private val plusOne = CounterKind.PowerToughness(1, 1)
    private fun counters(objects: Objects, n: Int, kind: CounterKind = plusOne) = Step.Do(Effect.PutCounters(objects, kind, Value.Fixed(n)))
    private fun slot(name: String, filter: ObjectFilter, min: Int = 1, max: Int = 1) =
        TargetSlot(name, TargetKind.Objects(filter), min = Value.Fixed(min), max = Value.Fixed(max))
    private val yourGraveyard = listOf(ObjectFilter.InZone(Zone.Graveyard), ObjectFilter.OwnedBy(PlayerFilter.You))
    private val flyingCounter = CounterKind.Keyword(Keyword.Flying)

    /** A flying counter is a keyword counter (CR 122.1b); "triggers only once" for as long as it exists. */
    @Test
    fun tumblingPepSquad() = card(
        name = "Tumbling Pep Squad",
        cost = "{1}{W}",
        type = "Creature — Human Survivor",
        pt = "2/2",
        text = "Survival — At the beginning of your second main phase, if this creature is tapped, put a flying counter on it. This ability triggers only once.",
    ) parsesTo abilities(
        survival(counters(Objects.Self, 1, flyingCounter), once = true),
    )

    /** "[Effect] if [condition]" and "If you don't control …": both checked as it resolves. */
    @Test
    fun glimmerHunter() = card(
        name = "Glimmer Hunter",
        cost = "{2}{W}",
        type = "Creature — Human Survivor",
        pt = "3/3",
        text = "Survival — At the beginning of your second main phase, if this creature is tapped, draw a card if you control a Glimmer creature. If you don't control a Glimmer creature, create a 1/1 white Glimmer enchantment creature token.",
    ) parsesTo abilities(
        survival(
            Step.If(
                condition = Condition.Exists(ObjectFilter.And(listOf(ObjectFilter.Sub(Subtype("Glimmer")), creature, youControl)), atLeast = 1),
                then = listOf(Step.Do(Effect.Draw(Players.You, Value.Fixed(1)))),
            ),
            Step.If(
                condition = Condition.Not(Condition.Exists(ObjectFilter.And(listOf(ObjectFilter.Sub(Subtype("Glimmer")), creature, youControl)), atLeast = 1)),
                then = listOf(create(glimmer)),
            ),
        ),
    )

    /** A choice among three counter kinds; "those counters": each kind and number the creature had as it last existed
     * (CR 603.10a, 608.2h). */
    @Test
    fun hesitantMentor() = card(
        name = "Hesitant Mentor",
        cost = "{1}{W}",
        type = "Creature — Human Survivor",
        pt = "2/2",
        text = """
            Survival — At the beginning of your second main phase, if this creature is tapped, put a flying, lifelink, or +1/+1 counter on it.
            Whenever this creature or another creature you control dies, if it had counters on it, put those counters on up to one target creature.
        """,
    ) parsesTo abilities(
        survival(
            Step.Choose(Choice.Option(listOf("flying", "lifelink", "+1/+1")), bind = "counter", chooser = Players.You),
            Step.If(Condition.Chose("counter", "flying"), then = listOf(counters(Objects.Self, 1, flyingCounter))),
            Step.If(Condition.Chose("counter", "lifelink"), then = listOf(counters(Objects.Self, 1, CounterKind.Keyword(Keyword.Lifelink)))),
            Step.If(Condition.Chose("counter", "+1/+1"), then = listOf(counters(Objects.Self, 1))),
        ),
        Ability.Triggered(
            trigger = Trigger.On(EventPattern.ZoneChange(
                ObjectFilter.Or(listOf(
                    ObjectFilter.Is(Objects.Self),
                    ObjectFilter.And(listOf(ObjectFilter.Not(ObjectFilter.Is(Objects.Self)), creature, youControl)),
                )),
                from = Zone.Battlefield,
                to = Zone.Graveyard,
            )),
            instructions = Instructions(
                targets = listOf(slot("creature", creature, min = 0)),
                steps = listOf(Step.Do(Effect.PutCountersOf(Objects.Target("creature"), of = Objects.EventObject(EventRole.Before)))),
            ),
            interveningIf = Condition.ObjectMatches(Objects.EventObject(EventRole.Before), ObjectFilter.HasCounter(null, atLeast = 1)),
        ),
    )

    @Test
    fun guardianOfTheMeek() = card(
        name = "Guardian of the Meek",
        cost = "{3}{W}",
        type = "Creature — Kor Survivor",
        pt = "3/4",
        text = "Survival — At the beginning of your second main phase, if this creature is tapped, return target creature card with mana value 3 or less from your graveyard to your hand.",
    ) parsesTo abilities(
        survival(
            Step.Do(Effect.Move(Objects.Target("card"), Destination(Zone.Hand))),
            targets = listOf(slot("card", ObjectFilter.And(listOf(creature, ObjectFilter.ManaValue(Comparison.AtMost, Value.Fixed(3))) + yourGraveyard))),
        ),
    )

    /** "Cards exiled with this creature" are those its own ability exiled, still in exile (CR 607.2a). */
    @Test
    fun hardenedSurvivor() = card(
        name = "Hardened Survivor",
        cost = "{W}",
        type = "Creature — Human Survivor",
        pt = "2/1",
        text = """
            Survival — At the beginning of your second main phase, if this creature is tapped, exile up to one target card from a graveyard.
            As long as there are three or more cards exiled with this creature, it gets +3/+3 and has hexproof. (It can't be the target of spells or abilities your opponents control.)
        """,
    ) parsesTo abilities(
        survival(
            Step.Do(Effect.Move(Objects.Target("card"), Destination(Zone.Exile), postBind = "exiled")),
            Step.Do(Effect.Remember("exiled", Objects.Bound("exiled"))),
            targets = listOf(slot("card", ObjectFilter.And(listOf(ObjectFilter.Category(ObjectCategory.Card), ObjectFilter.InZone(Zone.Graveyard))), min = 0)),
        ),
        Ability.Static(
            mechanics = listOf(
                Mechanic.ModifyPowerToughness(Value.Fixed(3), Value.Fixed(3)),
                Mechanic.GainAbilities(listOf(Keyword.Hexproof)),
            ),
            affects = Affected.Objects(Objects.Self),
            condition = Condition.Compare(
                Value.Count(Objects.Where(Objects.Remembered("exiled"), ObjectFilter.InZone(Zone.Exile))),
                Comparison.AtLeast,
                Value.Fixed(3),
            ),
        ),
    )

    @Test
    fun jadedDrifter() = card(
        name = "Jaded Drifter",
        cost = "{1}{B}",
        type = "Creature — Human Survivor",
        pt = "3/1",
        text = """
            This creature can't be blocked by Glimmers.
            Survival — At the beginning of your second main phase, if this creature is tapped, you may search your library for a card, put it into your graveyard, then shuffle.
        """,
    ) parsesTo abilities(
        Ability.Static(listOf(Mechanic.CantBeBlockedBy(ObjectFilter.Sub(Subtype("Glimmer")))), affects = Affected.Objects(Objects.Self)),
        survival(Step.May(
            steps = listOf(
                Step.Do(Effect.Search(Players.You, ObjectFilter.Category(ObjectCategory.Card), max = Value.Fixed(1), zones = listOf(Zone.Library),
                    bind = "found", searcher = Players.You)),
                Step.Do(Effect.Move(Objects.Bound("found"), Destination(Zone.Graveyard))),
                Step.Do(Effect.Shuffle(Players.You)),
            ),
            player = Players.You,
        )),
    )

    @Test
    fun warySurvivor() = card(
        name = "Wary Survivor",
        cost = "{3}{G}",
        type = "Creature — Elf Survivor",
        pt = "4/4",
        text = "Survival — At the beginning of your second main phase, if this creature is tapped, you gain 2 life.",
    ) parsesTo abilities(
        survival(Step.Do(Effect.GainLife(Players.You, Value.Fixed(2)))),
    )

    @Test
    fun stubbornSurvivor() = card(
        name = "Stubborn Survivor",
        cost = "{2}{G}",
        type = "Creature — Human Survivor",
        pt = "3/2",
        text = "Survival — At the beginning of your second main phase, if this creature is tapped, manifest dread. (Look at the top two cards of your library. Put one onto the battlefield face down as a 2/2 creature and the other into your graveyard. Turn it face up any time for its mana cost if it's a creature card.)",
    ) parsesTo abilities(
        survival(manifestDread(bind = "manifested")),
    )

    /** Reveal until a land card (CR 701.20); the found card to hand, the rest to the bottom at random. */
    @Test
    fun manorMapmaker() = card(
        name = "Manor Mapmaker",
        cost = "{1}{G}",
        type = "Creature — Human Scout Survivor",
        pt = "2/2",
        text = "Survival — At the beginning of your second main phase, if this creature is tapped, reveal cards from the top of your library until you reveal a land card. Put that card into your hand and the rest on the bottom of your library in a random order.",
    ) parsesTo abilities(
        survival(
            Step.Do(Effect.RevealUntil(ObjectFilter.Type(CardType.Land), foundBind = "found", restBind = "rest", library = Players.You,
                count = Value.Fixed(1), reveal = true)),
            Step.Do(Effect.Move(Objects.Bound("found"), Destination(Zone.Hand))),
            Step.Do(Effect.Move(Objects.Bound("rest"), Destination(Zone.Library, libraryPosition = -1, randomOrder = true))),
        ),
    )

    /** A legendary creature's short name as its self name (CR 201.5c). */
    @Test
    fun koroRescuedBeastie() = card(
        name = "Koro, Rescued Beastie",
        cost = "{3}{G}",
        type = "Legendary Creature — Beast Survivor",
        pt = "4/3",
        text = "Survival — At the beginning of your second main phase, if Koro is tapped, you may put a permanent card from your hand onto the battlefield.",
    ) parsesTo abilities(
        survival(
            Step.Choose(
                choice = Choice.Objects(
                    filter = ObjectFilter.And(listOf(
                        ObjectFilter.Or(listOf(CardType.Artifact, CardType.Battle, CardType.Creature, CardType.Enchantment, CardType.Land,
                            CardType.Planeswalker).map { ObjectFilter.Type(it) }),
                        ObjectFilter.InZone(Zone.Hand),
                        ObjectFilter.OwnedBy(PlayerFilter.You),
                    )),
                    min = Value.Fixed(0),
                    max = Value.Fixed(1),
                ),
                bind = "chosen",
                chooser = Players.You,
            ),
            Step.Do(Effect.Move(Objects.Bound("chosen"), Destination(Zone.Battlefield))),
        ),
    )

    /** A land becoming a 0/0 creature for good (layers 4 and 7b, CR 613.1d, 613.4b) and gaining haste until your next turn. */
    @Test
    fun deeprootSurvivor() = card(
        name = "Deeproot Survivor",
        cost = "{3}{G}{G}",
        type = "Creature — Human Survivor",
        pt = "3/4",
        text = """
            Haste
            Survival — At the beginning of your second main phase, if this creature is tapped, put three +1/+1 counters on up to one target land you control. That land becomes a 0/0 Elemental creature in addition to its other types. It gains haste until your next turn.
        """,
    ) parsesTo abilities(
        Keyword.Haste,
        survival(
            counters(Objects.Target("land"), 3),
            Step.Do(Effect.ApplyMechanics(
                mechanics = listOf(
                    Mechanic.SetTypes(add = listOf(CardType.Creature), addSubtypes = listOf(SubtypeRef.Literal(Subtype("Elemental")))),
                    Mechanic.SetPowerToughness(Value.Fixed(0), Value.Fixed(0)),
                ),
                affects = Affected.Objects(Objects.Target("land")),
                duration = Duration.Indefinitely,
            )),
            Step.Do(Effect.ApplyMechanics(
                mechanics = listOf(Mechanic.GainAbilities(listOf(Keyword.Haste))),
                affects = Affected.Objects(Objects.Target("land")),
                duration = Duration.UntilYourNextTurn,
            )),
            targets = listOf(slot("land", ObjectFilter.And(listOf(ObjectFilter.Type(CardType.Land), youControl)), min = 0)),
        ),
    )

    /** X is its power as the ability resolves (CR 107.3c, 608.2h); "with different powers" among the cards chosen. */
    @Test
    fun rookBroodStalker() = card(
        name = "Rook, Brood Stalker",
        cost = "{2}{G}{W}",
        type = "Legendary Creature — Human Survivor",
        pt = "4/4",
        text = "Survival — At the beginning of your second main phase, if Rook is tapped, reveal the top X cards of your library, where X is its power. Put any number of creature and/or Vehicle cards with different powers from among them into your hand. Put the rest on the bottom of your library in a random order.",
    ) parsesTo abilities(
        survival(
            Step.Do(Effect.Reveal(Objects.LibraryCards(Players.You, Value.Of(NumericProperty.Power, Objects.Self, Combine.Single), fromTop = true), bind = "seen")),
            Step.Choose(
                choice = Choice.Objects(
                    filter = ObjectFilter.Or(listOf(creature, ObjectFilter.Sub(Subtype("Vehicle")))),
                    min = Value.Fixed(0),
                    max = null,
                    from = Objects.Bound("seen"),
                    distinct = SharedQuality.Number(NumericProperty.Power),
                ),
                bind = "chosen",
                chooser = Players.You,
            ),
            Step.Do(Effect.Move(Objects.Bound("chosen"), Destination(Zone.Hand))),
            Step.Do(Effect.Move(Objects.Except(Objects.Bound("seen"), Objects.Bound("chosen")), Destination(Zone.Library, libraryPosition = -1, randomOrder = true))),
        ),
    )

    @Test
    fun cannyFabulist() = card(
        name = "Canny Fabulist",
        cost = "{1}{G}{W}",
        type = "Creature — Human Survivor",
        pt = "3/3",
        text = "Survival — At the beginning of your second main phase, if this creature is tapped, put a +1/+1 counter on target creature.",
    ) parsesTo abilities(
        survival(counters(Objects.Target("creature"), 1), targets = listOf(slot("creature", creature))),
    )
}
