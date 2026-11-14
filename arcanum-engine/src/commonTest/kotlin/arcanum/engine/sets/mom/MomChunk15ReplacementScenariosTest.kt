package arcanum.engine.sets.mom

import arcanum.api.Color
import arcanum.engine.cards.Cards
import arcanum.engine.scenario.scenario
import arcanum.engine.sets.fin.resolveAll
import kotlin.test.Test

/** MOM chunk 15: a gain-land enters tapped (CR 614.1d), its trigger gains 1 life, and it taps for either colour once untapped. */
class MomChunk15ReplacementScenariosTest {
    private val hollow = Cards.invent("Ashmoor Hollow", null, "Land", """
        This land enters tapped.
        When this land enters, you gain 1 life.
        {T}: Add {B} or {R}.
    """.trimIndent())

    @Test
    fun gainLandEntersTappedAndGainsLife() = scenario {
        alice.hand(hollow)
        alice.plays(hollow)
        resolveAll()
        expect {
            hollow.tapped shouldBe true
            alice.life shouldBe 21
        }
    }
}
