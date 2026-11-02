package arcanum.engine.review

import arcanum.engine.scenario.scenario
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Review 2: whole games played by a seeded random policy ([LongGame]), with every invariant checked after every input,
 * and minimal scenarios for what they found. The long run (many games) is `Review2LongGamesJvmTest`, behind
 * `-Parcanum.longGames=N`.
 */
class Review2LongGamesTest {
    /** A few whole games from [arcanum.engine.Engine.newGame] to their end: no invariant breaks. */
    // CR 103, 104, D56, D58, D59
    @Test
    fun seededGamesKeepEveryInvariant() {
        SEEDS.forEach { seed -> LongGame(seed, jsonEvery = 10, skip = KNOWN).play() }
    }

    /** A whole game on each player's view (D60): the host fills cards in as they become visible and gives new ids
     * for shuffles; public cards are always known, cards hidden from the viewer never. */
    // D60, D25, D58, D59
    @Test
    fun aGameOnEachPlayersViewKeepsEveryInvariant() {
        listOf("alice", "bob").forEach { v ->
            LongGame(VIEWER_SEED, jsonEvery = 10, skip = KNOWN, viewer = arcanum.engine.state.PlayerId(v)).play()
        }
    }

    /** A spell that shuffles itself into its library as it resolves (Verdant Moon's Apex) leaves nothing behind on the
     * Stack once it is done. */
    // CR 608.2n, 400.7
    @Test
    fun aSpellShuffledIntoTheLibraryLeavesNoStackItem() = scenario {
        alice.battlefield("Forest", "Forest", "Forest", "Forest")
        alice.hand("Verdant Moon's Apex")
        alice.library("Canopy Visionary", "Island")

        alice.casts("Verdant Moon's Apex", x = 3)
        allPass()
        alice.chooses("Canopy Visionary")
        expect {
            alice.battlefield has "Canopy Visionary"
            alice.library has "Verdant Moon's Apex"
        }
        assertTrue(state.stackItems.keys.all { it in state.stack })
    }

    /** The state a host stores while that resolution waits for its second shuffle: the card is in the library already,
     * but its Stack item is still in [arcanum.engine.state.GameState.stackItems] though no zone holds it. A host that
     * shows the Stack from the items shows a spell that isn't there. */
    // CR 608.2n, 400.7, D58
    @Ignore("review2 #6: while a resolution waits after its spell left the Stack, a Stack item stays that no zone holds")
    @Test
    fun aWaitingResolutionKeepsNoStackItemForAMovedSpell() = scenario {
        alice.battlefield("Forest", "Forest", "Forest", "Forest")
        alice.hand("Verdant Moon's Apex")
        alice.library("Canopy Visionary", "Island", "Swamp")

        alice.casts("Verdant Moon's Apex", x = 3)
        allPass()
        val game = state.deepCopy()
        fun answer(a: arcanum.engine.decision.Answer) =
            arcanum.engine.Engine.perform(game, arcanum.engine.input.Input.Answer(game.pending!!.decision.id, a)).changes.forEach { game.apply(it) }
        answer(arcanum.engine.decision.Answer.Objects(listOf(card("Canopy Visionary"))))
        // The first shuffle (the library), then the spell goes into it and the second shuffle waits
        val first = game.pending!!.decision as arcanum.engine.decision.Decision.Random
        answer(arcanum.engine.decision.Answer.RandomObjects((first.what as arcanum.engine.decision.RandomKind.Objects).among))
        assertTrue(game.pending?.decision is arcanum.engine.decision.Decision.Random, "waiting for the second shuffle")
        assertTrue(game.stackItems.keys.all { it in game.stack }, "items no zone holds: ${game.stackItems.keys - game.stack.toSet()}")
    }

    companion object {
        /** Seeds that finish in a few seconds each. */
        private val SEEDS = listOf(7L, 9L)

        /** A seed played on each player's view. */
        private const val VIEWER_SEED = 7L

        /** Checks left out because of open findings (#6: Stack items of a spell that left the Stack mid-resolution). */
        val KNOWN = setOf("orphanStackItem")
    }
}
