package arcanum.engine.review

import arcanum.api.TurnStep
import arcanum.engine.decision.Decision
import arcanum.engine.scenario.Scenario
import arcanum.engine.scenario.scenario
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Review 3: the end of the turn: another cleanup step after priority in one (CR 514.3a) and what it ends again
 * (CR 514.2); delayed triggers made during the step they wait for (CR 513.2, 603.7); "your next main phase" and "your
 * next upkeep".
 */
class Review3TurnTest {
    /** Plays to alice's cleanup discard in turn 1 with eight cards in hand and discards [discard]. */
    private fun Scenario.toCleanupDiscard(discard: String) {
        advanceTo(TurnStep.End)
        allPass()
        assertEquals(TurnStep.Cleanup, state.turn.step)
        alice.chooses(discard)
    }

    private fun Scenario.eightCards() = alice.hand("Island", "Island", "Island", "Island", "Island", "Island", "Island", "Forest")

    /** A discard in the cleanup step triggers Glint-Tusk Corsair: the trigger is put on the Stack, players get priority,
     * and then another cleanup step begins before the next turn. */
    // CR 514.3a, 514.1
    @Test
    fun aCleanupTriggerGivesPriorityAndAnotherCleanup() = scenario {
        alice.battlefield("Glint-Tusk Corsair")
        eightCards()
        alice.library("Swamp"); bob.library("Swamp", "Swamp")
        toCleanupDiscard("Forest")
        assertEquals(TurnStep.Cleanup, state.turn.step)
        assertEquals(alice.id, state.priority.holder)
        expect { stack.size shouldBe 1 }
        allPass()
        expect { bob.life shouldBe 19 }
        allPass()
        assertEquals(2, state.turn.number)
        expect { alice.hand.size shouldBe 7 }
    }

    /** An "until end of turn" effect made while players have priority in the cleanup step ends in the next cleanup step
     * (CR 514.2 again), before the next turn. */
    // CR 514.3a, 514.2
    @Test
    fun anEffectFromTheCleanupStepEndsInTheNextOne() = scenario {
        alice.battlefield("Glint-Tusk Corsair", "Sunning Mudlizard", "Forest", "Forest")
        eightCards()
        alice.library("Swamp"); bob.library("Swamp", "Swamp")
        toCleanupDiscard("Forest")
        allPass()
        alice.activates("Sunning Mudlizard")
        allPass()
        expect { "Sunning Mudlizard".power shouldBe 3 }
        allPass()
        assertEquals(2, state.turn.number)
        expect { "Sunning Mudlizard".power shouldBe 1 }
    }

    /** Damage dealt while players have priority in the cleanup step is removed in the next cleanup step. */
    // CR 514.3a, 514.2
    @Test
    fun damageFromTheCleanupStepIsRemovedInTheNextOne() = scenario {
        alice.battlefield("Glint-Tusk Corsair", "Mountain")
        alice.hand("Island", "Island", "Island", "Island", "Island", "Island", "Island", "Storm Spark")
        alice.library("Swamp"); bob.library("Swamp", "Swamp")
        bob.battlefield("Commanding Orator")
        toCleanupDiscard("Island")
        allPass()
        alice.casts("Storm Spark", target = "Commanding Orator")
        allPass()
        assertEquals(3, state.objOrThrow(card("Commanding Orator")).damage)
        allPass()
        assertEquals(2, state.turn.number)
        assertEquals(0, state.objOrThrow(card("Commanding Orator")).damage)
    }

    /** Seer's Trinket used in alice's upkeep: "draw a card at the beginning of the next turn's upkeep" is the next
     * turn's (bob's), not this one. */
    // CR 603.7, 513.2
    @Test
    fun nextTurnsUpkeepIsntThisOne() = scenario {
        turn(number = 3, step = TurnStep.Upkeep)
        alice.battlefield("Seer's Trinket")
        alice.library("Island", "Forest", "Swamp")
        bob.library("Swamp", "Swamp")
        alice.activates("Seer's Trinket", target = "alice")
        allPass()
        advanceTo(TurnStep.PrecombatMain)
        expect { alice.hand.size shouldBe 1 }
        advanceTo(TurnStep.Draw, turn = 4)
        expect { alice.hand.size shouldBe 2 }
    }

    /** Muster the Forebears cast in alice's upkeep: "exile those creatures at the beginning of your next upkeep" waits
     * for her next turn's upkeep, past bob's. */
    // CR 603.7, 513.2
    @Test
    fun yourNextUpkeepFromAnUpkeepIsNextTurns() = scenario {
        turn(number = 3, step = TurnStep.Upkeep)
        alice.battlefield("Plains", "Plains", "Plains")
        alice.graveyard("Elvish Seer")
        alice.hand("Muster the Forebears")
        alice.library("Island", "Island", "Island"); bob.library("Swamp", "Swamp", "Swamp")
        alice.casts("Muster the Forebears", x = 1)
        allPass()
        expect { alice.battlefield has "Elvish Seer" }
        advanceTo(TurnStep.Draw)
        expect { alice.battlefield has "Elvish Seer" }
        advanceTo(TurnStep.Draw, turn = 4)
        expect { alice.battlefield has "Elvish Seer" }
        advanceTo(TurnStep.Upkeep, turn = 5)
        allPass()
        expect { alice.exile has "Elvish Seer" }
    }

    /** Mana Siphon cast in alice's precombat main phase: "at the beginning of your next main phase" is her postcombat
     * main phase of the same turn. */
    // CR 505.1, 603.7
    @Test
    fun yourNextMainPhaseIsThePostcombatOne() = scenario {
        alice.battlefield("Forest", "Forest", "Forest", "Island", "Island")
        alice.hand("Elvish Hierophant", "Mana Siphon")
        alice.casts("Elvish Hierophant", paying = listOf("Forest", "Forest#1", "Forest#2"))
        alice.casts("Mana Siphon", target = "Elvish Hierophant")
        allPass()
        expect { alice.graveyard has "Elvish Hierophant" }
        advanceTo(TurnStep.PostcombatMain)
        if (state.stack.isNotEmpty()) allPass()
        expect { alice.pool.size shouldBe 3 }
    }

    /** A permanent with "at the beginning of your end step" that enters during the end step doesn't trigger until the
     * next end step (Morvek cast with flash through Verdant Hollowmere in alice's end step). */
    // CR 513.2
    @Test
    fun anEndStepAbilityEnteringInTheEndStepWaits() = scenario {
        alice.battlefield("Verdant Hollowmere")
        alice.hand("Morvek, Ghoulherd of Stensia")
        alice.library("Island"); bob.library("Swamp", "Swamp")
        advanceTo(TurnStep.End)
        val free = castOptions(alice, "Morvek, Ghoulherd of Stensia").first { it.way.permission != null }
        perform(free)
        allPass()
        expect { alice.battlefield has "Morvek, Ghoulherd of Stensia" }
        allPass()
        assertEquals(2, state.turn.number)
        expect { alice.battlefield.names.count { it == "Zombie Token" } shouldBe 0 }
    }

    /** The cleanup step's discard is the active player's only: bob, not active, keeps nine cards. */
    // CR 514.1
    @Test
    fun onlyTheActivePlayerDiscards() = scenario {
        alice.library("Island"); bob.library("Swamp", "Swamp")
        bob.hand("Swamp", "Swamp", "Swamp", "Swamp", "Swamp", "Swamp", "Swamp", "Swamp", "Swamp")
        advanceTo(TurnStep.End)
        allPass()
        assertEquals(2, state.turn.number)
        expect { bob.hand.size shouldBe 9 }
    }

    /** Unearth used in the precombat main phase: the creature is exiled at the beginning of this turn's end step. */
    // CR 702.84a, 603.7
    @Test
    fun unearthExilesAtThisTurnsEndStep() = scenario {
        alice.battlefield("Mountain", "Mountain")
        alice.graveyard("Junkyard Cur")
        alice.library("Island"); bob.library("Swamp", "Swamp")
        alice.activates("Junkyard Cur")
        allPass()
        answerMays(false)
        expect { alice.battlefield has "Junkyard Cur" }
        advanceTo(TurnStep.End)
        allPass()
        expect { alice.exile has "Junkyard Cur" }
        assertTrue(state.turn.number == 1 || state.turn.number == 2)
    }

    /** Priority in the cleanup step is given only when something happened (CR 514.3): an ordinary turn goes from the
     * end step to the next turn's upkeep with no input in the cleanup step. */
    // CR 514.3
    @Test
    fun noPriorityInAQuietCleanup() = scenario {
        alice.library("Island"); bob.library("Swamp", "Swamp")
        advanceTo(TurnStep.End)
        allPass()
        assertEquals(2, state.turn.number)
        var step: TurnStep? = null
        var grantsInCleanup = 0
        applied.forEach { c ->
            if (c is arcanum.engine.change.Change.Step) step = c.after.step
            if (c is arcanum.engine.change.Change.Priority && step == TurnStep.Cleanup &&
                arcanum.engine.change.Meaning.PriorityGranted in c.meanings) grantsInCleanup++
        }
        assertEquals(0, grantsInCleanup)
    }
}
