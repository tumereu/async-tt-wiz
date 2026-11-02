package arcanum.parser.sets.dgm

import arcanum.api.Ability
import arcanum.api.Affected
import arcanum.api.Color
import arcanum.api.ColorRef
import arcanum.api.Comparison
import arcanum.api.Condition
import arcanum.api.CostObjects
import arcanum.api.CostPart
import arcanum.api.Destination
import arcanum.api.Effect
import arcanum.api.EventPattern
import arcanum.api.Instructions
import arcanum.api.Keyword
import arcanum.api.ManaCost
import arcanum.api.ManaKind
import arcanum.api.ManaProduction
import arcanum.api.Mechanic
import arcanum.api.Modification
import arcanum.api.ObjectFilter
import arcanum.api.Objects
import arcanum.api.Players
import arcanum.api.SharedQuality
import arcanum.api.Step
import arcanum.api.Subtype
import arcanum.api.Value
import arcanum.api.Zone
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.parsesTo
import arcanum.parser.sets.gtc.anyTime
import arcanum.parser.sets.gtc.costOf
import arcanum.parser.sets.gtc.creature
import arcanum.parser.sets.gtc.creatureOpponentControls
import arcanum.parser.sets.gtc.creatureToken
import arcanum.parser.sets.gtc.entersSelf
import arcanum.parser.sets.gtc.gains
import arcanum.parser.sets.gtc.manaPart
import arcanum.parser.sets.gtc.n
import arcanum.parser.sets.gtc.pump
import arcanum.parser.sets.gtc.slot
import arcanum.parser.sets.gtc.steps
import arcanum.parser.sets.gtc.untilEndOfTurn
import arcanum.parser.sets.gtc.you
import kotlin.test.Test

/**
 * Dragon's Maze chunk 03: Gates and the Maze. Gatekeepers whose enters triggers check two or more Gates as they trigger
 * and resolve (CR 603.4), Maze creatures giving their keyword to multicoloured creatures (CR 105.2), a Cluestone, a
 * Guildgate, and a land that fetches a Gate and wins with ten Gates of different names (CR 104.2b, 201.2).
 */
class DgmChunk03GatesAndTheMazeTest {
    private val twoGates = Condition.Exists(ObjectFilter.And(listOf(ObjectFilter.Sub(Subtype("Gate")), you)), atLeast = 2)
    private fun gatekeeper(vararg steps: Step, targets: List<arcanum.api.TargetSlot> = emptyList()) =
        Ability.Triggered(entersSelf, Instructions(targets = targets, steps = steps.toList()), interveningIf = twoGates)
    private fun maze(keyword: Keyword) = Ability.Static(listOf(Mechanic.GainAbilities(listOf(keyword))),
        affects = Affected.Objects(Objects.Each(ObjectFilter.And(listOf(ObjectFilter.Multicolored, creature, you)))))
    private fun manaOf(a: Color, b: Color) = Ability.Activated(costOf(CostPart.TapSelf),
        steps(Step.Do(Effect.AddMana(ManaProduction(ManaKind.Choose(n(1), listOf(ColorRef.Literal(a), ColorRef.Literal(b)))), Players.You))), rules = anyTime)
    private val entersTapped = Ability.Static(listOf(Mechanic.Replacement(EventPattern.ZoneChange(ObjectFilter.Is(Objects.Self), to = Zone.Battlefield),
        modify = listOf(Modification.EntersTapped))), affects = Affected.Objects(Objects.Self))

    // CR 603.4: an intervening "if"
    @Test
    fun sunspireWardens() = card(
        name = "Sunspire Wardens",
        cost = "{3}{W}",
        type = "Creature — Human Soldier",
        pt = "2/4",
        text = "When this creature enters, if you control two or more Gates, create a 2/2 white Knight creature token with vigilance.",
    ) parsesTo abilities(gatekeeper(Step.Do(Effect.CreateToken(creatureToken("Knight", listOf(Color.White), 2, 2, listOf(Keyword.Vigilance)), n(1), Players.You))))

    @Test
    fun opalLakeWardens() = card(
        name = "Opal Lake Wardens",
        cost = "{3}{U}",
        type = "Creature — Vedalken Soldier",
        pt = "2/4",
        text = "When this creature enters, if you control two or more Gates, you may draw a card.",
    ) parsesTo abilities(gatekeeper(Step.May(listOf(Step.Do(Effect.Draw(Players.You, n(1)))), Players.You)))

    @Test
    fun ubulSarWardens() = card(
        name = "Ubul Sar Wardens",
        cost = "{3}{B}",
        type = "Creature — Zombie Soldier",
        pt = "2/4",
        text = "When this creature enters, if you control two or more Gates, target creature an opponent controls gets -2/-2 until end of turn.",
    ) parsesTo abilities(gatekeeper(pump(Objects.Target("creature"), -2, -2), targets = listOf(slot("creature", creatureOpponentControls))))

    @Test
    fun smeltWardWardens() = card(
        name = "Smelt-Ward Wardens",
        cost = "{3}{R}",
        type = "Creature — Human Warrior",
        pt = "2/4",
        text = "When this creature enters, if you control two or more Gates, gain control of target creature an opponent controls until end of turn. Untap that creature. It gains haste until end of turn.",
    ) parsesTo abilities(gatekeeper(
        untilEndOfTurn(Objects.Target("creature"), Mechanic.ControlledBy(Players.You)),
        Step.Do(Effect.Untap(Objects.Target("creature"))),
        gains(Objects.Target("creature"), Keyword.Haste),
        targets = listOf(slot("creature", creatureOpponentControls)),
    ))

    @Test
    fun saruliWardens() = card(
        name = "Saruli Wardens",
        cost = "{3}{G}",
        type = "Creature — Elf Warrior",
        pt = "2/4",
        text = "When this creature enters, if you control two or more Gates, you gain 7 life.",
    ) parsesTo abilities(gatekeeper(Step.Do(Effect.GainLife(Players.You, n(7)))))

    // CR 105.2: multicoloured creatures you control
    @Test
    fun mazeWatcher() = card(
        name = "Maze Watcher",
        cost = "{5}{W}",
        type = "Creature — Elemental",
        pt = "3/6",
        text = """
            Vigilance
            Multicolored creatures you control have vigilance.
        """,
    ) parsesTo abilities(Keyword.Vigilance, maze(Keyword.Vigilance))

    @Test
    fun mazeSkimmer() = card(
        name = "Maze Skimmer",
        cost = "{5}{U}",
        type = "Creature — Elemental",
        pt = "3/5",
        text = """
            Flying
            Multicolored creatures you control have flying.
        """,
    ) parsesTo abilities(Keyword.Flying, maze(Keyword.Flying))

    @Test
    fun mazeHorror() = card(
        name = "Maze Horror",
        cost = "{5}{B}",
        type = "Creature — Elemental",
        pt = "4/5",
        text = """
            Deathtouch (Any amount of damage this deals to a creature is enough to destroy it.)
            Multicolored creatures you control have deathtouch.
        """,
    ) parsesTo abilities(Keyword.Deathtouch, maze(Keyword.Deathtouch))

    @Test
    fun mazeCharger() = card(
        name = "Maze Charger",
        cost = "{5}{R}",
        type = "Creature — Elemental",
        pt = "6/3",
        text = """
            Haste
            Multicolored creatures you control have haste.
        """,
    ) parsesTo abilities(Keyword.Haste, maze(Keyword.Haste))

    @Test
    fun mazeColossus() = card(
        name = "Maze Colossus",
        cost = "{5}{G}",
        type = "Creature — Elemental",
        pt = "5/4",
        text = """
            Trample
            Multicolored creatures you control have trample.
        """,
    ) parsesTo abilities(Keyword.Trample, maze(Keyword.Trample))

    @Test
    fun azoriusCluegem() = card(
        name = "Azorius Cluegem",
        cost = "{3}",
        type = "Artifact",
        text = """
            {T}: Add {W} or {U}.
            {W}{U}, {T}, Sacrifice this artifact: Draw a card.
        """,
    ) parsesTo abilities(
        manaOf(Color.White, Color.Blue),
        Ability.Activated(costOf(manaPart("{W}{U}"), CostPart.TapSelf, CostPart.Sacrifice(CostObjects.Self)), steps(Step.Do(Effect.Draw(Players.You, n(1)))), rules = anyTime),
    )

    @Test
    fun azoriusGatehouse() = card(
        name = "Azorius Gatehouse",
        cost = null,
        type = "Land — Gate",
        text = """
            This land enters tapped.
            {T}: Add {W} or {U}.
        """,
    ) parsesTo abilities(entersTapped, manaOf(Color.White, Color.Blue))

    // CR 201.2: Gates with different names; CR 104.2b: winning the game
    @Test
    fun labyrinthsEnd() = card(
        name = "Labyrinth's End",
        cost = null,
        type = "Land",
        text = """
            This land enters tapped.
            {T}: Add {C}.
            {3}, {T}, Return this land to its owner's hand: Search your library for a Gate card, put it onto the battlefield, then shuffle. If you control ten or more Gates with different names, you win the game.
        """,
    ) parsesTo abilities(
        entersTapped,
        Ability.Activated(costOf(CostPart.TapSelf), steps(Step.Do(Effect.AddMana(ManaProduction(ManaKind.Fixed(ManaCost.parse("{C}")!!)), Players.You))), rules = anyTime),
        Ability.Activated(
            costOf(manaPart("{3}"), CostPart.TapSelf, CostPart.ReturnToHand(CostObjects.Self)),
            steps(
                Step.Do(Effect.Search(Players.You, ObjectFilter.Sub(Subtype("Gate")), max = n(1), zones = listOf(Zone.Library), bind = "found", searcher = Players.You)),
                Step.Do(Effect.Move(Objects.Bound("found"), Destination(Zone.Battlefield))),
                Step.Do(Effect.Shuffle(Players.You)),
                Step.If(Condition.Compare(Value.CountDistinct(Objects.Each(ObjectFilter.And(listOf(ObjectFilter.Sub(Subtype("Gate")), you))), SharedQuality.Name),
                    Comparison.AtLeast, n(10)), then = listOf(Step.Do(Effect.WinGame(Players.You)))),
            ),
            rules = anyTime,
        ),
    )
}
