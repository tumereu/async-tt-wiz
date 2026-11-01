package arcanum.engine.trigger

import arcanum.engine.cards.Cards
import arcanum.engine.decision.Decision
import arcanum.engine.scenario.scenario
import arcanum.engine.state.TargetRef
import kotlin.test.Test
import kotlin.test.assertEquals

/** Choices made as a triggered ability is put on the Stack (CR 603.3d) that depend on its event or its source's new
 * object. */
class TriggerChoicesTest {
    private val dart = Cards.invent("Grave Dart", "{1}{B}", "Instant", "Destroy target creature.")
    private val shatter = Cards.invent("Shatter Shot", "{R}", "Instant", "Destroy target artifact.")
    private val idol = Cards.invent("Brass Idol", "{2}", "Artifact")

    // CR 400.7, 603.6e: "another target artifact card" leaves out the dead creature's own card in the graveyard
    @Test
    fun anotherLeavesOutItsOwnNewObject() = scenario {
        alice.battlefield("Myr Gleaner")
        alice.graveyard(idol, "Lotus Frond")
        alice.hand(dart)

        alice.castsWithoutCosts(dart, target = "Myr Gleaner")
        resolve()

        assertEquals(setOf(TargetRef.Object(card(idol)), TargetRef.Object(card("Lotus Frond"))),
            decision<Decision.Targets>().options.toSet())
    }

    // CR 603.3d: a target "with lesser mana value" compares with the artifact the event put into the graveyard
    @Test
    fun targetsComparedWithTheTriggeringObject() = scenario {
        alice.battlefield("Scrap Dredger", idol)
        alice.graveyard("Lotus Frond", "Myr Gleaner")
        alice.hand(shatter)

        alice.castsWithoutCosts(shatter, target = idol)
        resolve()
        // Lotus Frond (0) is less than Brass Idol (2); Myr Gleaner (2) isn't: the only target, chosen without asking
        expect { stack.names shouldBe listOf("ability of Scrap Dredger") }
        resolve()
        expect { alice.hand has "Lotus Frond" }
    }
}
