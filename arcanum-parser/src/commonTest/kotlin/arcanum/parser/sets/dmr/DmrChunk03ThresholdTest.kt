package arcanum.parser.sets.dmr

import arcanum.api.Ability
import arcanum.api.ActivationRules
import arcanum.api.Color
import arcanum.api.ColorRef
import arcanum.api.Keyword
import arcanum.api.ObjectFilter
import arcanum.api.Objects
import arcanum.api.PlayerFilter
import arcanum.api.Players
import arcanum.api.Step
import arcanum.api.Timing
import arcanum.api.Zone
import arcanum.api.CostObjects
import arcanum.api.CostPart
import arcanum.api.Effect
import arcanum.api.EventPattern
import arcanum.api.Mechanic
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

/** Dominaria Remastered chunk 03: threshold (CR 207.2c: an ability word, no rules meaning of its own; the conditions are the abilities' own). */
class DmrChunk03ThresholdTest {
    private val sevenOrMore = threshold
    private val opponentCasts = on(EventPattern.Cast(ObjectFilter.Any, PlayerFilter.Opponent))
    private val white = ObjectFilter.OfColor(ColorRef.Literal(Color.White))

    // CR 207.2c, 602.5b: "Activate only once each turn and only if …" restricts the activation; CR 401.4 for the bottom of the library
    @Test
    fun scroungerOfTheBattlefield() = card(
        name = "Scrounger of the Battlefield",
        cost = "{3}{G}{G}",
        type = "Creature — Centaur",
        pt = "3/3",
        text = "Threshold — Put three cards from your graveyard on the bottom of your library: This creature gets +3/+3 until end of turn. Activate only once each turn and only if there are seven or more cards in your graveyard.",
    ) parsesTo abilities(
        Ability.Activated(
            cost(CostPart.PutOnBottomOfLibrary(CostObjects.Choose(cardFilter, count = n(3)), from = Zone.Graveyard)),
            arcanum.api.Instructions(steps = listOf(pump(Objects.Self, 3, 3))),
            ActivationRules(timing = Timing.Any, activatedBy = PlayerFilter.You, perTurn = 1, condition = sevenOrMore),
            abilityWord = "Threshold",
        ),
    )

    // CR 613.4c: "an additional" is one more modification of the same layer
    @Test
    fun hallowedSacrament() = card(
        name = "Hallowed Sacrament",
        cost = "{1}{W}{W}",
        type = "Enchantment",
        text = """
            White creatures get +1/+1.
            Threshold — White creatures get an additional +1/+1 as long as there are seven or more cards in your graveyard.
        """,
    ) parsesTo abilities(
        static(pt(1, 1), affects = Objects.Each(and(white, creature))),
        static(pt(1, 1), affects = Objects.Each(and(white, creature)), condition = sevenOrMore).copy(abilityWord = "Threshold"),
    )

    // CR 115.1: "two target creatures" are exactly two targets
    @Test
    fun wanderingDecoy() = card(
        name = "Wandering Decoy",
        cost = "{2}{W}",
        type = "Creature — Human Nomad",
        pt = "1/2",
        text = """
            {W}, {T}: Tap target creature.
            Threshold — {W}{W}, {T}: Tap two target creatures. Activate only if there are seven or more cards in your graveyard.
        """,
    ) parsesTo abilities(
        activated(cost(mana("{W}"), tap), tapIt(target("creature")), targets = listOf(slot("creature", creature))),
        Ability.Activated(
            cost(mana("{W}{W}"), tap),
            arcanum.api.Instructions(targets = listOf(slot("creatures", creature, min = 2, max = 2)), steps = listOf(tapIt(target("creatures")))),
            ActivationRules(timing = Timing.Any, activatedBy = PlayerFilter.You, condition = sevenOrMore),
            abilityWord = "Threshold",
        ),
    )

    // CR 205.3m: "All Squirrels" is every Squirrel on the battlefield, whoever controls it
    @Test
    fun acornGatherer() = card(
        name = "Acorn Gatherer",
        cost = "{5}{G}",
        type = "Creature — Human Druid",
        pt = "1/1",
        text = """
            At the beginning of your upkeep, you may create a 1/1 green Squirrel creature token.
            Threshold — All Squirrels get +2/+2 as long as there are seven or more cards in your graveyard.
        """,
    ) parsesTo abilities(
        triggered(yourUpkeep, Step.May(listOf(create(creatureToken("Squirrel", listOf(Color.Green), 1, 1))), player = Players.You)),
        static(pt(2, 2), affects = Objects.Each(sub("Squirrel")), condition = sevenOrMore).copy(abilityWord = "Threshold"),
    )

    // CR 113.3d, 603.2: the granted triggered ability works while the condition holds
    @Test
    fun huntingLodge() = card(
        name = "Hunting Lodge",
        cost = "{G}{W}",
        type = "Enchantment",
        text = "Threshold — As long as there are seven or more cards in your graveyard, this enchantment has \"Whenever an opponent casts a spell, you may put a creature card from your hand onto the battlefield.\"",
    ) parsesTo abilities(
        static(
            has(triggered(opponentCasts,
                Step.Choose(arcanum.api.Choice.Objects(and(creature, inZone(Zone.Hand), ObjectFilter.OwnedBy(PlayerFilter.You)), min = n(0), max = n(1)), bind = "chosen", chooser = Players.You),
                move(Objects.Bound("chosen"), Zone.Battlefield))),
            condition = sevenOrMore,
        ).copy(abilityWord = "Threshold"),
    )

    // CR 602.2: two untap abilities; the threshold one has up to three targets
    @Test
    fun marshRestorer() = card(
        name = "Marsh Restorer",
        cost = "{2}{G}",
        type = "Creature — Human Druid",
        pt = "1/2",
        text = """
            {T}: Untap target land.
            Threshold — {T}: Untap up to three target lands. Activate only if there are seven or more cards in your graveyard.
        """,
    ) parsesTo abilities(
        activated(cost(tap), untapIt(target("land")), targets = listOf(slot("land", land))),
        Ability.Activated(
            cost(tap),
            arcanum.api.Instructions(targets = listOf(slot("land", land, min = 0, max = 3)), steps = listOf(untapIt(target("land")))),
            ActivationRules(timing = Timing.Any, activatedBy = PlayerFilter.You, condition = sevenOrMore),
            abilityWord = "Threshold",
        ),
    )

    // CR 613.4c, 207.2c
    @Test
    fun zealotOfTheDesert() = card(
        name = "Zealot of the Desert",
        cost = "{3}{W}",
        type = "Creature — Human Nomad Mystic",
        pt = "2/4",
        text = "Threshold — As long as there are seven or more cards in your graveyard, this creature gets +1/+1 and has flying.",
    ) parsesTo abilities(static(pt(1, 1), has(Keyword.Flying), condition = sevenOrMore).copy(abilityWord = "Threshold"))

    // CR 205.1, 613.1d: the land stays a land and gains the creature type, colours and first strike until end of turn
    @Test
    fun cloisterOfTheMoss() = card(
        name = "Cloister of the Moss",
        cost = null,
        type = "Land",
        text = """
            {T}: Add {C}.
            Threshold — {G}{W}: This land becomes a 4/4 green and white Insect Monk creature with first strike until end of turn. It's still a land. Activate only if there are seven or more cards in your graveyard.
        """,
    ) parsesTo abilities(
        addMana("{C}"),
        Ability.Activated(cost(mana("{G}{W}")), arcanum.api.Instructions(steps = listOf(untilEndOfTurn(Objects.Self,
            Mechanic.SetTypes(add = listOf(arcanum.api.CardType.Creature), addSubtypes = listOf(arcanum.api.SubtypeRef.Literal(arcanum.api.Subtype("Insect")),
                arcanum.api.SubtypeRef.Literal(arcanum.api.Subtype("Monk"))), replaceSubtypes = true),
            Mechanic.SetColors(listOf(ColorRef.Literal(Color.Green), ColorRef.Literal(Color.White))),
            Mechanic.SetPowerToughness(n(4), n(4)), has(Keyword.FirstStrike)))),
            ActivationRules(timing = Timing.Any, activatedBy = PlayerFilter.You, condition = sevenOrMore), abilityWord = "Threshold"),
    )

    // CR 207.2c, 613.4c: "has" a quoted activated ability
    @Test
    fun sentryOfTheDesert() = card(
        name = "Sentry of the Desert",
        cost = "{1}{W}{W}",
        type = "Creature — Human Nomad",
        pt = "2/2",
        text = "Threshold — As long as there are seven or more cards in your graveyard, this creature gets +1/+1 and has \"{T}: Target attacking or blocking creature gets +3/+3 until end of turn.\"",
    ) parsesTo abilities(
        static(pt(1, 1), has(activated(cost(tap), pump(target("creature"), 3, 3), targets = listOf(slot("creature", and(or(attacking, ObjectFilter.HasStatus(arcanum.api.Status.Blocking)), creature))))),
            condition = sevenOrMore).copy(abilityWord = "Threshold"),
    )

    // CR 207.2c
    @Test
    fun forestBear() = card(
        name = "Forest Bear",
        cost = "{1}{G}",
        type = "Creature — Human Bear Druid",
        pt = "1/1",
        text = """
            {T}: Add {G}.
            Threshold — This creature gets +3/+3 as long as there are seven or more cards in your graveyard.
        """,
    ) parsesTo abilities(addMana("{G}"), static(pt(3, 3), condition = sevenOrMore).copy(abilityWord = "Threshold"))
}
