package arcanum.engine.sets.inr

import arcanum.engine.decision.Answer
import arcanum.api.Subtype
import arcanum.engine.decision.Decision
import arcanum.engine.cards.Cards
import arcanum.engine.scenario.scenario
import kotlin.test.Test

/** INR chunk 11: a chosen-type creature that gives other creatures of that type an extra counter as they enter. */
class InrChunk11ReplacementScenariosTest {
    private val mimic = Cards.invent("Shifting Mimic", "{2}", "Artifact Creature — Shapeshifter", """
        As this creature enters, choose a creature type.
        This creature is the chosen type in addition to its other types.
        Each other creature you control of the chosen type enters with an additional +1/+1 counter on it.
    """, "2/1")
    private val elf = Cards.invent("Quiet Elf", "{G}", "Creature — Elf", "", "1/1")
    private val ox = Cards.invent("Plain Ox", "{1}{G}", "Creature — Ox", "", "2/2")

    // CR 614.1c, 614.12, 607.2d: only other creatures of the chosen type entering later get the counter
    @Test
    fun otherCreaturesOfTheChosenTypeEnterWithACounter() = scenario {
        alice.battlefield("Forest", "Forest", "Forest", "Forest", "Forest")
        alice.hand(mimic, elf, ox)
        alice.casts(mimic)
        allPass()
        decision<Decision.ChooseSubtype>()
        alice.answers(Answer.SubtypeChoice(Subtype("Elf")))
        alice.casts(elf)
        allPass()
        alice.casts(ox)
        allPass()
        expect {
            elf.power shouldBe 2
            ox.power shouldBe 2
            mimic.power shouldBe 2
        }
    }
}
