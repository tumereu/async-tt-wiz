package arcanum.engine.resolution

import arcanum.engine.cards.Cards
import arcanum.engine.change.Change
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.scenario.scenario
import kotlin.test.Test
import kotlin.test.assertEquals

/** "Each player may …" with several steps, done for all players together (CR 101.4, 608.2e). */
class EachPlayerMayTest {
    private val dart = Cards.invent("Grave Dart", "{1}{B}", "Instant", "Destroy target creature.")

    // CR 101.4: each player decides in turn order; then the searched lands enter in one event, before the shuffles
    @Test
    fun eachPlayerMaySearchThenAllShuffle() = scenario {
        alice.library("Forest", "Island")
        bob.library("Plains", "Mountain")
        alice.battlefield("Seasoned Pathfinder")
        alice.hand(dart)

        alice.castsWithoutCosts(dart, target = "Seasoned Pathfinder")
        resolve()
        resolve()
        assertEquals(alice.id, decision<Decision.May>().player)
        answer(Answer.YesNo(true))
        assertEquals(bob.id, decision<Decision.May>().player)
        answer(Answer.YesNo(true))
        alice.chooses("Forest")
        bob.chooses("Plains", "Mountain")

        expect {
            alice.battlefield has "Forest"
            bob.battlefield has "Plains"
            bob.battlefield has "Mountain"
            alice.library.names shouldBe listOf("Island")
        }
        // One zone change for all three lands
        assertEquals(1, applied.count { c -> c is Change.ZoneChange && c.moves.size == 3 })
    }
}
