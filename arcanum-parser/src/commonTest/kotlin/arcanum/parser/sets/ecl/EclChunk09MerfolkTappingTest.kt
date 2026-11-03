package arcanum.parser.sets.ecl

import arcanum.api.Condition
import arcanum.api.CostObjects
import arcanum.api.CostPart
import arcanum.api.Comparison
import arcanum.api.EventPattern
import arcanum.api.Keyword
import arcanum.api.Measure
import arcanum.api.Mechanic
import arcanum.api.Objects
import arcanum.api.PlayerFilter
import arcanum.api.Zone
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

/**
 * Lorwyn Eclipsed chunk 09: Merfolk that become tapped and tap others. "Whenever this creature becomes tapped" (by attacking or any
 * other way, CR 701.26), tapping another untapped creature as a cost (CR 602.2b) or as a payment on resolution ("you may tap another
 * untapped Merfolk you control. When you do, …", CR 603.12), untapping at your end step, and "As long as you attacked with three or more
 * Merfolk this turn" (creatures declared as attackers this turn, CR 508.1).
 */
class EclChunk09MerfolkTappingTest {
    private fun tapAnother(filter: arcanum.api.ObjectFilter): CostPart = CostPart.TapObjects(CostObjects.Choose(and(another, filter), count = n(1)))

    @Test
    fun brookleapVaulter() = card(
        name = "Brookleap Vaulter",
        cost = "{2}{W}",
        type = "Creature — Merfolk Warrior",
        text = """
            Flying
            Whenever this creature becomes tapped, another target Merfolk you control gets +2/+0 until end of turn.
        """,
        pt = "1/3",
    ) parsesTo abilities(
        Keyword.Flying,
        triggered(becomesTapped, pump(target("Merfolk"), 2, 0), targets = listOf(slot("Merfolk", and(another, sub("Merfolk"), you)))),
    )

    // CR 701.26: attacking taps it too
    @Test
    fun driftbrineSermonist() = card(
        name = "Driftbrine Sermonist",
        cost = "{1}{W}",
        type = "Creature — Merfolk Cleric",
        text = "Whenever this creature becomes tapped, you gain 2 life.",
        pt = "2/2",
    ) parsesTo abilities(
        triggered(becomesTapped, gainLife(2)),
    )

    @Test
    fun harriedWellkeeper() = card(
        name = "Harried Wellkeeper",
        cost = "{3}{U}",
        type = "Creature — Merfolk Soldier",
        text = "Whenever this creature becomes tapped, create a 1/1 blue and black Faerie creature token with flying.",
        pt = "3/2",
    ) parsesTo abilities(
        triggered(becomesTapped, create(faerie)),
    )

    @Test
    fun silverscaleHawker() = card(
        name = "Silverscale Hawker",
        cost = "{2}{U}",
        type = "Creature — Merfolk Citizen",
        text = "Whenever this creature becomes tapped, draw a card, then discard a card.",
        pt = "2/3",
    ) parsesTo abilities(
        triggered(becomesTapped, draw(1), discard(1)),
    )

    @Test
    fun driftwineDiverter() = card(
        name = "Driftwine Diverter",
        cost = "{3}{U}",
        type = "Creature — Merfolk Wizard",
        text = "Whenever this creature becomes tapped, target creature an opponent controls gets -3/-0 until end of turn.",
        pt = "4/3",
    ) parsesTo abilities(
        triggered(becomesTapped, pump(target("creature"), -3, 0), targets = listOf(slot("creature", creatureOpponentControls))),
    )

    // CR 602.2b: another untapped creature tapped as part of the cost
    @Test
    fun driftbrineSnarer() = card(
        name = "Driftbrine Snarer",
        cost = "{W}",
        type = "Creature — Merfolk Scout",
        text = "{1}, {T}, Tap another untapped creature you control: Tap target creature an opponent controls.",
        pt = "2/1",
    ) parsesTo abilities(
        activated(cost(mana("{1}"), tap, tapAnother(creature)), tapIt(target("creature")), targets = listOf(slot("creature", creatureOpponentControls))),
    )

    // CR 603.12
    @Test
    fun oxbowPathfinder() = card(
        name = "Oxbow Pathfinder",
        cost = "{2}{W}",
        type = "Creature — Merfolk Scout",
        text = "Whenever this creature attacks, you may tap another untapped Merfolk you control. When you do, return target creature card with mana value 3 or less from your graveyard to the battlefield.",
        pt = "3/2",
    ) parsesTo abilities(
        triggered(attacksSelf, *mayPay(tapAnother(sub("Merfolk")), then = listOf(reflexive(move(target("card"), Zone.Battlefield),
            targets = listOf(slot("card", inYourGraveyard(creature, manaValue(Comparison.AtMost, 3))))))).toTypedArray()),
    )

    @Test
    fun pebblefinRascal() = card(
        name = "Pebblefin Rascal",
        cost = "{1}{U}",
        type = "Creature — Merfolk Rogue",
        text = """
            Vigilance
            Whenever this creature attacks, you may tap another untapped creature you control. If you do, this creature can't be blocked this turn.
        """,
        pt = "1/3",
    ) parsesTo abilities(
        Keyword.Vigilance,
        triggered(attacksSelf, *mayPay(tapAnother(creature), then = listOf(untilEndOfTurn(Objects.Self, Mechanic.CantBeBlocked))).toTypedArray()),
    )

    @Test
    fun deepcurrentFencer() = card(
        name = "Deepcurrent Fencer",
        cost = "{W}{U}",
        type = "Creature — Merfolk Soldier",
        text = """
            At the beginning of your end step, untap target Merfolk you control.
            Other Merfolk you control get +1/+1.
        """,
        pt = "2/2",
    ) parsesTo abilities(
        triggered(stepBegins(arcanum.api.TurnStep.End, PlayerFilter.You), untapIt(target("Merfolk")), targets = listOf(slot("Merfolk", and(sub("Merfolk"), you)))),
        static(pt(1, 1), affects = Objects.Each(and(another, sub("Merfolk"), you))),
    )

    // CR 508.1: the Merfolk you declared as attackers this turn
    @Test
    fun deeprunPilot() = card(
        name = "Deeprun Pilot",
        cost = "{W}{U}",
        type = "Creature — Merfolk Wizard",
        text = """
            Flash
            When this creature enters, untap each other Merfolk you control.
            As long as you attacked with three or more Merfolk this turn, Merfolk you control get +1/+0.
        """,
        pt = "2/2",
    ) parsesTo abilities(
        Keyword.Flash,
        triggered(entersSelf, untapIt(Objects.Each(and(another, sub("Merfolk"), you)))),
        static(pt(1, 0), affects = Objects.Each(and(sub("Merfolk"), you)),
            condition = Condition.HappenedThisTurn(EventPattern.Attack(and(sub("Merfolk"), you)), atLeast = 3, measure = Measure.Objects)),
    )
}
