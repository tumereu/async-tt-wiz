package arcanum.engine.resolution

import arcanum.engine.cards.Cards
import arcanum.engine.scenario.scenario
import kotlin.test.Test

/** Targets checked again as a spell or ability resolves (CR 608.2b). */
class TargetRecheckTest {
    private val idol = Cards.invent("Brass Idol", "{2}", "Artifact")

    // CR 608.2b: a target whose filter names another target ("in that player's graveyard") is still legal
    @Test
    fun aTargetDependingOnAnotherStaysLegal() = scenario {
        alice.battlefield("Goblin Solderer")
        bob.battlefield(idol)
        bob.graveyard("Lotus Frond")

        alice.activatesWithoutCosts("Goblin Solderer", targets = listOf(idol, "Lotus Frond"))
        resolve()

        expect {
            bob.graveyard has idol
            bob.battlefield has "Lotus Frond"
        }
    }
}
