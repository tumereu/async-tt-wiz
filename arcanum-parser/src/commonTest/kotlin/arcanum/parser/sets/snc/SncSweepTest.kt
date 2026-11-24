package arcanum.parser.sets.snc

import arcanum.api.*
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

/** SNC bug-class sweep: parser siblings of SNC's fixes ("other target" after an earlier target of another kind). */
class SncSweepTest {
    private val yourCreature = ObjectFilter.And(listOf(creature, ObjectFilter.ControlledBy(PlayerFilter.You)))

    // CR 115.3: "other target creature" after a target creature you control is a different object from that target
    @Test
    fun otherTargetAfterANarrowerTarget() = card(
        name = "SncSweep Hostile Merger",
        cost = "{2}{U}{B}",
        type = "Sorcery",
        text = """
            Up to one target creature you control has base power and toughness 4/4 until end of turn. Up to one other target creature has base power and toughness 1/1 until end of turn.
        """,
    ) parsesTo abilities(
        spell(
            untilEndOfTurn(target("creature"), Mechanic.SetPowerToughness(n(4), n(4))),
            untilEndOfTurn(target("second creature"), Mechanic.SetPowerToughness(n(1), n(1))),
            targets = listOf(slot("creature", yourCreature, min = 0),
                TargetSlot("second creature", TargetKind.Objects(creature), min = n(0), max = n(1), distinctFrom = listOf("creature")))),
    )
}
