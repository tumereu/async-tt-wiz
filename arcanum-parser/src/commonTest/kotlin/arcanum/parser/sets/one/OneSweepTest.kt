package arcanum.parser.sets.one

import arcanum.api.*
import arcanum.parser.cube.*
import kotlin.test.Test

/** ONE sweep: siblings of the parser bugs set work found, read in other wordings. */
class OneSweepTest {
    private val whileYouControlThis = Duration.AsLongAs(Condition.ObjectMatches(Objects.Self, ObjectFilter.ControlledBy(PlayerFilter.You)))

    // CR 115.1, 611.2b, 613.1b: one target creature, no stray slot left by a reading that gave up; the control change lasts for as long as you
    // control this creature
    @Test
    fun tidecrestUsurper() = card(
        name = "Tidecrest Usurper",
        cost = "{3}{U}{U}",
        type = "Creature — Elemental",
        pt = "3/2",
        text = "Landfall — Whenever a land you control enters, you may gain control of target creature for as long as you control this creature.",
    ) parsesTo abilities(
        triggered(landfall,
            Step.May(listOf(Step.Do(Effect.ApplyMechanics(listOf(Mechanic.ControlledBy(Players.You)), Affected.Objects(target("creature")),
                whileYouControlThis))), Players.You),
            targets = listOf(slot("creature", creature)), abilityWord = "Landfall"),
    )
}
