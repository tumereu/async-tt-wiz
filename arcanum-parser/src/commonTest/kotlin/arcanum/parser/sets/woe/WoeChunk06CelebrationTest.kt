package arcanum.parser.sets.woe

import arcanum.api.*
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

/** WOE chunk 06: Celebration, an ability word (CR 207.2c): "two or more nonland permanents entered the battlefield under your control
 * this turn" counts permanents, so two entering at once count as two. */
class WoeChunk06CelebrationTest {
    private val celebration = Condition.HappenedThisTurn(
        event = EventPattern.ZoneChange(objects = and(nonland, permanentFilter, you), to = Zone.Battlefield),
        atLeast = 2,
        measure = Measure.Objects,
    )
    private val targetCreatureYouControl = listOf(slot("creature", creatureYouControl))

    // CR 207.2c, 611.3a (a static ability with a condition)
    @Test
    fun armoryMice() = card(
        name = "Larder Mice",
        cost = "{1}{W}",
        type = "Creature — Mouse",
        pt = "3/1",
        text = "Celebration — This creature gets +0/+2 as long as two or more nonland permanents entered the battlefield under your control this turn.",
    ) parsesTo abilities(
        static(pt(0, 2), condition = celebration).copy(abilityWord = "Celebration"),
    )

    // CR 603.4 (intervening "if")
    @Test
    fun ashPartyCrasher() = card(
        name = "Fenn, Gate Crasher",
        cost = "{R}{W}",
        type = "Legendary Creature — Human Peasant",
        pt = "2/2",
        text = """
            Haste
            Celebration — Whenever Fenn attacks, if two or more nonland permanents entered the battlefield under your control this turn, put a +1/+1 counter on Fenn.
        """,
    ) parsesTo abilities(
        Keyword.Haste,
        triggered(attacksSelf, counters(Objects.Self), interveningIf = celebration, abilityWord = "Celebration"),
    )

    // CR 603.4, 702.111b (menace)
    @Test
    fun belligerentOfTheBall() = card(
        name = "Brawler of the Gala",
        cost = "{2}{R}",
        type = "Creature — Ogre Warrior",
        pt = "3/3",
        text = "Celebration — At the beginning of combat on your turn, if two or more nonland permanents entered the battlefield under your control this turn, target creature you control gets +1/+0 and gains menace until end of turn. (It can't be blocked except by two or more creatures.)",
    ) parsesTo abilities(
        triggered(beginningOfCombatOnYourTurn, pump(target("creature"), 1, 0, has(Keyword.Menace)), targets = targetCreatureYouControl,
            interveningIf = celebration, abilityWord = "Celebration"),
    )

    // CR 603.4, 301.5c (an Equipment attached by an effect, not by equip)
    @Test
    fun bespokeBattlegarb() = card(
        name = "Tailored Warcoat",
        cost = "{1}{R}",
        type = "Artifact — Equipment",
        text = """
            Equipped creature gets +2/+0.
            Celebration — At the beginning of combat on your turn, if two or more nonland permanents entered the battlefield under your control this turn, attach this Equipment to up to one target creature you control.
            Equip {2} ({2}: Attach to target creature you control. Equip only as a sorcery.)
        """,
    ) parsesTo abilities(
        Ability.Static(listOf(pt(2, 0)), affects = Affected.Objects(Objects.AttachedTo(Objects.Self))),
        triggered(beginningOfCombatOnYourTurn, Step.Do(Effect.Attach(Objects.Self, toObjects(target("creature")))),
            targets = listOf(slot("creature", creatureYouControl, min = 0)), interveningIf = celebration, abilityWord = "Celebration"),
        Keyword.Equip(manaCost("{2}")),
    )

    // CR 611.3a, 702.4 (double strike)
    @Test
    fun gallantPieWielder() = card(
        name = "Valiant Tart-Thrower",
        cost = "{2}{W}",
        type = "Creature — Dwarf Knight",
        pt = "2/3",
        text = """
            First strike
            Celebration — This creature has double strike as long as two or more nonland permanents entered the battlefield under your control this turn.
        """,
    ) parsesTo abilities(
        Keyword.FirstStrike,
        static(has(Keyword.DoubleStrike), condition = celebration).copy(abilityWord = "Celebration"),
    )

    // CR 205.1b (a creature type replacing its other creature types), 613.1d, 613.4b, 613.1f (layers 4, 7b, 6), 611.3a
    @Test
    fun goddricCloakedReveler() = card(
        name = "Brannoc, Masked Reveler",
        cost = "{1}{R}{R}",
        type = "Legendary Creature — Human Noble",
        pt = "3/3",
        text = """
            Haste
            Celebration — As long as two or more nonland permanents entered the battlefield under your control this turn, Brannoc is a Dragon with base power and toughness 4/4, flying, and "{R}: Dragons you control get +1/+0 until end of turn." (He loses all other creature types.)
        """,
    ) parsesTo abilities(
        Keyword.Haste,
        static(
            Mechanic.SetTypes(addSubtypes = listOf(SubtypeRef.Literal(Subtype("Dragon"))), replaceSubtypes = true),
            Mechanic.SetPowerToughness(n(4), n(4)),
            has(Keyword.Flying, activated(manaCost("{R}"), pump(Objects.Each(and(sub("Dragon"), you)), 1, 0))),
            condition = celebration,
        ).copy(abilityWord = "Celebration"),
    )

    // CR 611.3a
    @Test
    fun grandBallGuest() = card(
        name = "Gala Night Guest",
        cost = "{1}{R}",
        type = "Creature — Human Peasant",
        pt = "2/2",
        text = "Celebration — This creature gets +1/+1 and has trample as long as two or more nonland permanents entered the battlefield under your control this turn.",
    ) parsesTo abilities(
        static(pt(1, 1), has(Keyword.Trample), condition = celebration).copy(abilityWord = "Celebration"),
    )

    // CR 603.4, 513.1a (the end step)
    @Test
    fun ladyOfLaughter() = card(
        name = "Lady of Merriment",
        cost = "{3}{W}{W}",
        type = "Creature — Faerie Noble",
        pt = "4/5",
        text = """
            Flying
            Celebration — At the beginning of your end step, if two or more nonland permanents entered the battlefield under your control this turn, draw a card.
        """,
    ) parsesTo abilities(
        Keyword.Flying,
        triggered(stepBegins(TurnStep.End, PlayerFilter.You), draw(1), interveningIf = celebration, abilityWord = "Celebration"),
    )

    // CR 603.4
    @Test
    fun pestsOfHonor() = card(
        name = "Vermin of Honor",
        cost = "{2}{W}",
        type = "Creature — Mouse",
        pt = "2/2",
        text = "Celebration — At the beginning of combat on your turn, if two or more nonland permanents entered the battlefield under your control this turn, put a +1/+1 counter on this creature.",
    ) parsesTo abilities(
        triggered(beginningOfCombatOnYourTurn, counters(Objects.Self), interveningIf = celebration, abilityWord = "Celebration"),
    )

    // CR 601.2f (cost reduction), 603.4
    @Test
    fun ragingBattleMouse() = card(
        name = "Furious Skirmish Mouse",
        cost = "{1}{R}",
        type = "Creature — Mouse",
        pt = "2/1",
        text = """
            The second spell you cast each turn costs {1} less to cast.
            Celebration — At the beginning of combat on your turn, if two or more nonland permanents entered the battlefield under your control this turn, target creature you control gets +1/+1 until end of turn.
        """,
    ) parsesTo abilities(
        Ability.Static(
            listOf(Mechanic.CostChange(and(ObjectFilter.NthSpellThisTurn(spells = ObjectFilter.Any, n = 2), you), CostChange.Less(ManaCost.parse("{1}")!!, n(1)), minimum = 0)),
            affects = Affected.Objects(Objects.Self),
        ),
        triggered(beginningOfCombatOnYourTurn, pump(target("creature"), 1, 1), targets = targetCreatureYouControl,
            interveningIf = celebration, abilityWord = "Celebration"),
    )

    // CR 611.3a, 702.15 (lifelink)
    @Test
    fun tuinvaleGuide() = card(
        name = "Feywood Guide",
        cost = "{3}{W}",
        type = "Creature — Faerie Scout",
        pt = "2/3",
        text = """
            Flying
            Celebration — This creature gets +1/+0 and has lifelink as long as two or more nonland permanents entered the battlefield under your control this turn.
        """,
    ) parsesTo abilities(
        Keyword.Flying,
        static(pt(1, 0), has(Keyword.Lifelink), condition = celebration).copy(abilityWord = "Celebration"),
    )
}
