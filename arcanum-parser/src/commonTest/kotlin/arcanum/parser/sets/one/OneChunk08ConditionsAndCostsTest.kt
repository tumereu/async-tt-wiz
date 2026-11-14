package arcanum.parser.sets.one

import arcanum.api.CardType
import arcanum.api.Color
import arcanum.api.ColorRef
import arcanum.api.Comparison
import arcanum.api.Condition
import arcanum.api.Cost
import arcanum.api.CostObjects
import arcanum.api.CostPart
import arcanum.api.CounterKind
import arcanum.api.Effect
import arcanum.api.EventPattern
import arcanum.api.Keyword
import arcanum.api.Measure
import arcanum.api.Mechanic
import arcanum.api.Modification
import arcanum.api.ObjectFilter
import arcanum.api.Objects
import arcanum.api.PlayerFilter
import arcanum.api.Players
import arcanum.api.Recipients
import arcanum.api.Step
import arcanum.api.Supertype
import arcanum.api.Timing
import arcanum.api.TurnStep
import arcanum.api.Value
import arcanum.api.Zone
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

/**
 * Phyrexia: All Will Be One chunk 08: conditions about a target's keyword ("if that creature has toxic", with "instead", CR 608.2c), lands
 * entering tapped unless you control few lands (CR 614.1c), "unless" costs and conditions (CR 118.12a), abilities of cards in all graveyards
 * (CR 613.1f), entering that doesn't trigger opponents' abilities (CR 603.2), colors and types added until end of turn (layers 4 and 5), a
 * cost removing counters from among permanents (CR 601.2h) and an activation condition looking back over the turn.
 */
class OneChunk08ConditionsAndCostsTest {
    private val oil = CounterKind.Named("oil")
    private val toxic = kw(Keyword.Toxic.Kind)
    private val toxicTarget = Condition.ObjectMatches(target("creature"), toxic)

    // CR 608.2c: the condition is checked as the spell resolves
    @Test
    fun toxinDevotionRite() = card(
        name = "Toxin Devotion Rite",
        cost = "{1}{W}",
        type = "Instant",
        text = "Target creature you control gets +2/+2 until end of turn. If that creature has toxic, draw a card.",
    ) parsesTo abilities(
        spell(pump(target("creature"), 2, 2), Step.If(toxicTarget, then = listOf(draw(1))), targets = listOf(slot("creature", creatureYouControl))),
    )

    // CR 608.2c: "instead": 4 damage rather than 2 when the creature has toxic
    @Test
    fun venomgiltSlash() = card(
        name = "Venomgilt Slash",
        cost = "{R}",
        type = "Instant",
        text = "Venomgilt Slash deals 2 damage to target creature. If that creature has toxic, Venomgilt Slash deals 4 damage to that creature instead.",
    ) parsesTo abilities(
        spell(Step.If(toxicTarget, then = listOf(damage(4, toObjects(target("creature")))), otherwise = listOf(damage(2, toObjects(target("creature"))))),
            targets = listOf(slot("creature", creature))),
    )

    // CR 608.2c: +2/+2 instead of +1/+1
    @Test
    fun ivoryZealotOfPlague() = card(
        name = "Ivory Zealot of Plague",
        cost = "{3}{W}",
        type = "Creature — Phyrexian Soldier",
        pt = "2/3",
        text = "At the beginning of combat on your turn, target creature you control gets +1/+1 until end of turn. If that creature has toxic, instead it gets +2/+2 until end of turn.",
    ) parsesTo abilities(
        triggered(beginningOfCombatOnYourTurn, Step.If(toxicTarget, then = listOf(pump(target("creature"), 2, 2)), otherwise = listOf(pump(target("creature"), 1, 1))),
            targets = listOf(slot("creature", creatureYouControl))),
    )

    // CR 113.6: the static ability works on the battlefield; CR 613.1f: the land cards' activated abilities, mana abilities too (CR 605.1a)
    @Test
    fun gildedHavenVault() = card(
        name = "Gilded Haven Vault",
        cost = "{3}",
        type = "Artifact",
        text = "As long as this artifact is on the battlefield, it has all activated abilities of all land cards in all graveyards.",
    ) parsesTo abilities(
        static(Mechanic.GainAbilitiesOf(Objects.Each(and(land, inZone(Zone.Graveyard)))),
            condition = Condition.ObjectMatches(Objects.Self, inZone(Zone.Battlefield))).let { it.copy(functionsIn = listOf(Zone.Battlefield)) },
    )

    // CR 603.2d: an additional time; CR 603.2: opponents' permanents' abilities don't trigger at all
    @Test
    fun primeMatronOfEngines() = card(
        name = "Prime Matron of Engines",
        cost = "{4}{W}",
        type = "Legendary Creature — Phyrexian Praetor",
        pt = "4/7",
        text = """
            Vigilance
            If a permanent entering causes a triggered ability of a permanent you control to trigger, that ability triggers an additional time.
            Permanents entering don't cause abilities of permanents your opponents control to trigger.
        """,
    ) parsesTo abilities(
        Keyword.Vigilance,
        static(Mechanic.TriggersAdditionalTime(EventPattern.ZoneChange(permanentFilter, to = Zone.Battlefield), and(permanentFilter, you))),
        static(Mechanic.EventDoesntTrigger(EventPattern.ZoneChange(permanentFilter, to = Zone.Battlefield), and(permanentFilter, opponents))),
    )

    // CR 118.12a: removing the counters is optional; if you don't, it returns
    @Test
    fun lavabornSprinter() = card(
        name = "Lavaborn Sprinter",
        cost = "{2}{R}",
        type = "Creature — Phyrexian Warrior",
        pt = "3/2",
        text = """
            Haste
            When this creature enters, put two oil counters on target artifact or creature you control.
            At the beginning of your end step, return this creature to its owner's hand unless you remove two oil counters from it.
        """,
    ) parsesTo abilities(
        Keyword.Haste,
        triggered(entersSelf, counters(target("permanent"), 2, oil), targets = listOf(slot("permanent", and(or(artifact, creature), you)))),
        triggered(stepBegins(TurnStep.End, PlayerFilter.You),
            Step.Pay(cost(CostPart.RemoveCounters(oil, n(2), CostObjects.Self)), payer = Players.You, bind = "paid"),
            Step.If(Condition.Not(Condition.Did("paid")), then = listOf(move(Objects.Self, Zone.Hand)))),
    )

    /** "This land enters tapped unless you control two or fewer other lands." (CR 614.1c) and its two colors. */
    private fun fastland(name: String, a: String, b: String) = card(
        name = name,
        cost = null,
        type = "Land",
        text = """
            This land enters tapped unless you control two or fewer other lands.
            {T}: Add {$a} or {$b}.
        """,
    ) parsesTo abilities(
        static(Mechanic.Replacement(EventPattern.ZoneChange(ObjectFilter.Is(Objects.Self), to = Zone.Battlefield), modify = listOf(Modification.EntersTapped)),
            condition = Condition.Not(Condition.Compare(count(and(another, land, you)), Comparison.AtMost, n(2)))),
        addEither(a, b),
    )

    // CR 614.1c
    @Test
    fun ashcleaveRidge() = fastland("Ashcleave Ridge", "B", "R")

    // CR 614.1c
    @Test
    fun copperveinGorge() = fastland("Coppervein Gorge", "R", "G")

    // CR 614.1c
    @Test
    fun inkslickStrand() = fastland("Inkslick Strand", "U", "B")

    // CR 614.1c
    @Test
    fun bladevergeCopse() = fastland("Bladeverge Copse", "G", "W")

    // CR 614.1c
    @Test
    fun seaglassShore() = fastland("Seaglass Shore", "W", "U")

    // CR 503.1a: each player's upkeep, the damage to that player unless they control two basic lands (checked as it resolves, CR 608.2c)
    @Test
    fun kilnChastiser() = card(
        name = "Kiln Chastiser",
        cost = "{2}{R}",
        type = "Creature — Phyrexian Warrior",
        pt = "3/3",
        text = """
            Menace
            At the beginning of each player's upkeep, this creature deals 2 damage to that player unless they control two or more basic lands.
        """,
    ) parsesTo abilities(
        Keyword.Menace,
        triggered(stepBegins(TurnStep.Upkeep, PlayerFilter.Any),
            Step.If(Condition.Not(Condition.Exists(and(ObjectFilter.Super(Supertype.Basic), land, ObjectFilter.ControlledBy(PlayerFilter.Is(Players.EventPlayer))), atLeast = 2)),
                then = listOf(damage(2, Recipients(players = Players.EventPlayer))))),
    )

    // CR 613.1d, e: an artifact (layer 4) and blue (layer 5) until end of turn; CR 107.4f: {U/P}
    @Test
    fun vedalkenGrandTinker() = card(
        name = "Vedalken Grand Tinker",
        cost = "{1}{U}{U}",
        type = "Legendary Artifact Creature — Phyrexian Vedalken",
        pt = "2/4",
        text = """
            Other blue creatures you control have "Whenever this creature becomes tapped, draw a card, then discard a card."
            Other artifact creatures you control get +1/+1.
            {U/P}: Until end of turn, target creature you control becomes a blue artifact in addition to its other colors and types. Activate only as a sorcery. ({U/P} can be paid with either {U} or 2 life.)
        """,
    ) parsesTo abilities(
        static(has(triggered(on(EventPattern.Tap(ObjectFilter.Is(Objects.Self))), draw(1), discard(1))),
            affects = Objects.Each(and(another, ObjectFilter.OfColor(ColorRef.Literal(Color.Blue)), creature, you))),
        static(pt(1, 1), affects = Objects.Each(and(another, artifact, creature, you))),
        activated(manaCost("{U/P}"), untilEndOfTurn(target("creature"), Mechanic.SetTypes(add = listOf(CardType.Artifact)),
            Mechanic.SetColors(listOf(ColorRef.Literal(Color.Blue)), add = true)), targets = listOf(slot("creature", creatureYouControl)), rules = asSorcery),
    )

    // CR 601.2h: ten oil counters from among your permanents, as you choose; CR 122.1
    @Test
    fun theWireSylex() = card(
        name = "The Wire Sylex",
        cost = "{2}",
        type = "Legendary Artifact",
        text = """
            {T}: Put an oil counter on The Wire Sylex.
            {T}, Sacrifice The Wire Sylex: Destroy each nonland permanent with mana value equal to the number of oil counters on The Wire Sylex.
            {T}, Remove ten oil counters from among permanents you control and sacrifice The Wire Sylex: It deals 10 damage to any target.
        """,
    ) parsesTo abilities(
        activated(cost(tap), counters(Objects.Self, 1, oil)),
        activated(cost(tap, sacrificeSelf), destroy(Objects.Each(and(nonland, permanentFilter,
            ObjectFilter.ManaValue(Comparison.Equal, Value.Of(arcanum.api.NumericProperty.Counters(oil), Objects.Self, arcanum.api.Combine.Single)))))),
        activated(cost(tap, CostPart.RemoveCounters(oil, n(10), CostObjects.Choose(and(permanentFilter, you), count = null), distributed = true), sacrificeSelf),
            damage(10, toAny), targets = listOf(anyTarget)),
    )

    // CR 602.5b: the activation condition looks back over the turn (an oil counter removed from your permanent, or a permanent with one put into
    // a graveyard, as it last existed, CR 603.10a)
    @Test
    fun roilingOilVat() = card(
        name = "Roiling Oil Vat",
        cost = "{R}",
        type = "Artifact",
        text = """
            At the beginning of your upkeep, put an oil counter on another target nontoken artifact or creature you control.
            {2}, {T}: Create a 1/1 red Phyrexian Goblin creature token. Activate only if an oil counter was removed from a permanent you controlled this turn or a permanent with an oil counter on it was put into a graveyard this turn.
        """,
    ) parsesTo abilities(
        triggered(stepBegins(TurnStep.Upkeep, PlayerFilter.You), counters(target("permanent"), 1, oil),
            targets = listOf(slot("permanent", and(another, not(token), or(artifact, creature), you)))),
        activated(cost(mana("{2}"), tap), create(creatureToken("Phyrexian Goblin", listOf(Color.Red), 1, 1)),
            rules = anyTime.copy(condition = Condition.Or(listOf(
                Condition.HappenedThisTurn(EventPattern.CountersRemoved(and(permanentFilter, you), oil), atLeast = 1, measure = Measure.Events),
                Condition.HappenedThisTurn(EventPattern.ZoneChange(and(permanentFilter, ObjectFilter.HasCounter(oil, atLeast = 1)), from = Zone.Battlefield,
                    to = Zone.Graveyard), atLeast = 1, measure = Measure.Events))))),
    )
}
