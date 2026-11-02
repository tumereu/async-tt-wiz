package arcanum.engine.setup

import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.query.Evaluator
import arcanum.engine.scenario.scenario
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** Companions in one player's view of the game (D60): another player's companion check is theirs to make. */
class CompanionInAViewTest {
    private val lowCurve = cards(listOf("Plains", "Swamp", "Ghoulish Reveler", "Plains", "Swamp", "Thorough Study", "Plains"),
        "Swamp", "Ghoulish Reveler")
    private val mountains = cards(listOf("Mountain", "Glint-Tusk Corsair", "Mountain"), "Mountain", "Glint-Tusk Corsair")

    /** In Bob's view, Alice's sideboard and deck are hidden: Bob's state doesn't look at her library to check the
     * condition (nothing of it becomes known); it offers her card outside the game, and the one she reveals becomes
     * public (CR 103.2b, 702.139a). */
    // CR 103.2b, 702.139a
    @Test
    fun anotherPlayersCompanionIsCheckedInTheirView() = scenario(start = table(lowCurve, mountains,
        sideboards = mapOf(0 to listOf("Velmora of the Sleep-Den")))) {
        viewer(bob)
        startGame(StartOptions(startingPlayer = alice.id))
        val reveal = decision<Decision.ChooseObjects>()
        assertEquals(alice.id, reveal.player)
        val companion = state.player(alice.id).outside.single()
        assertEquals(listOf(companion), reveal.options)
        assertTrue(state.player(alice.id).library.all { state.objOrThrow(it).unknown }, "Alice's library stays unknown")
        answer(Answer.Objects(listOf(companion)))
        assertEquals("Velmora of the Sleep-Den", state.objOrThrow(companion).definition?.faces?.first()?.name)
        assertNotNull(state.objOrThrow(companion).memory[Evaluator.REVEALED_COMPANION])
        assertTrue(state.player(alice.id).library.all { state.objOrThrow(it).unknown }, "Alice's library stays unknown")
    }

    /** Declined in her view, it stays unknown in Bob's. */
    // CR 103.2b
    @Test
    fun aCompanionNotRevealedStaysHidden() = scenario(start = table(lowCurve, mountains,
        sideboards = mapOf(0 to listOf("Velmora of the Sleep-Den")))) {
        viewer(bob)
        startGame(StartOptions(startingPlayer = alice.id))
        answer(Answer.Objects(emptyList()))
        val companion = state.player(alice.id).outside.single()
        assertTrue(state.objOrThrow(companion).unknown)
    }
}
