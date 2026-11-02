package arcanum.engine.review

import arcanum.api.TurnStep
import arcanum.engine.Engine
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.input.Input
import arcanum.engine.scenario.scenario
import arcanum.engine.state.TargetRef
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** Review 1: priority and the Stack (CR 116, 117, 405, 603.3). */
class Review1PriorityTest {
    /** The caster keeps priority; a response resolves first and the first spell waits on the Stack. */
    // CR 117.3c, 117.4, 405.5
    @Test
    fun responseResolvesFirstAndTheFirstSpellWaits() = scenario {
        alice.battlefield("Mountain")
        alice.hand("Storm Spark")
        bob.battlefield("Mountain")
        bob.hand("Magma Needle")
        alice.casts("Storm Spark", target = "bob")
        assertEquals(alice.id, state.priority.holder)
        alice.passes()
        assertEquals(bob.id, state.priority.holder)
        bob.casts("Magma Needle", target = "alice")
        // CR 117.3c: bob keeps priority after casting
        assertEquals(bob.id, state.priority.holder)
        bob.passes()
        alice.passes()
        expect {
            alice.life shouldBe 19
            bob.life shouldBe 20
            stack.size shouldBe 1
        }
        // CR 117.3b: after a resolution the active player gets priority
        assertEquals(alice.id, state.priority.holder)
        allPass()
        expect { bob.life shouldBe 17 }
    }

    /** After a player acts, everyone must pass again in succession before the top resolves. */
    // CR 117.4
    @Test
    fun aPassBeforeAnActionDoesntCount() = scenario {
        alice.battlefield("Mountain", "Mountain")
        alice.hand("Storm Spark", "Magma Needle")
        alice.casts("Storm Spark", target = "bob")
        alice.passes()
        bob.passes()
        // Resolved: alice gets priority with an empty Stack
        expect { bob.life shouldBe 17 }
        alice.casts("Magma Needle", target = "bob")
        alice.passes()
        assertEquals(bob.id, state.priority.holder)
        expect { stack.size shouldBe 1 }
    }

    /** A spell's triggered ability goes on the Stack before anyone gets priority after the spell resolves. */
    // CR 117.5, 603.3
    @Test
    fun enteringTriggerIsOnTheStackBeforePriority() = scenario {
        alice.battlefield("Forest", "Forest")
        alice.hand("Elvish Seerling")
        alice.library("Mountain", "Island")
        alice.casts("Elvish Seerling")
        allPass()
        expect {
            alice.battlefield has "Elvish Seerling"
            stack.size shouldBe 1
            alice.hand.size shouldBe 0
        }
        assertEquals(alice.id, state.priority.holder)
        allPass()
        expect { alice.hand has "Mountain" }
    }

    /** With three players, the active player's triggers go on the Stack first, then the next player's in turn order,
     * so the last player's trigger resolves first. */
    // CR 101.4, 603.3b
    @Test
    fun apnapOrderWithThreePlayers() = scenario(players = 3) {
        alice.battlefield("Mountain")
        alice.hand("Storm Spark")
        alice.battlefield("Crimson Mourner")
        bob.battlefield("Crimson Mourner")
        carol.battlefield("Crimson Mourner")
        alice.casts("Storm Spark", target = "Crimson Mourner")
        allPass()
        // Each Mourner's controller targets a player as it goes on the Stack: answer "dave-less" first options
        var guard = 10
        while (pending is Decision.Targets && guard-- > 0) {
            val d = decision<Decision.Targets>()
            answer(Answer.Targets(listOf(TargetRef.Player(d.player))))
        }
        // alice's Mourner died with the trigger (it saw itself die), bob's and carol's saw it
        val items = state.stack.map { state.stackItems[it]!!.controller }
        assertEquals(listOf(alice.id, bob.id, carol.id), items, "APNAP: the active player's at the bottom")
    }

    /** A player who left the game isn't waited for: the remaining players passing in succession resolves the top. */
    // CR 117.4, 800.4
    @Test
    fun aPlayerWhoLostIsntWaitedFor() = scenario(players = 3) {
        alice.battlefield("Mountain")
        alice.hand("Storm Spark")
        state.players[2] = state.players[2].copy(lost = true)
        alice.casts("Storm Spark", target = "bob")
        alice.passes()
        bob.passes()
        expect { bob.life shouldBe 17 }
    }

    /** Every input the options list in a busy main phase is accepted by perform. */
    // CR 117.1
    @Test
    fun optionsInABusyMainPhaseAreAccepted() = scenario {
        alice.battlefield("Mountain", "Forest", "Island", "Swamp", "Plains", "Elvish Seer", "Lotus Frond")
        alice.hand("Storm Spark", "Plowshare Decree", "Rescind", "Warlord's Decree", "Doomfire",
            "Hollow Victory", "Bone Splinters", "Lingering Shades", "Forest")
        alice.graveyard("Cinder Bolt", "Magma Needle")
        bob.battlefield("Thicketwood Elves")
        expectEveryOptionAccepted()
    }

    /** With nothing to counter, a counterspell isn't offered (or is accepted). */
    // CR 601.2c, 115.1
    @Test
    fun counterspellWithNothingToCounterIsntListed() = scenario {
        alice.battlefield("Island", "Island")
        alice.hand("Denial")
        val listed = Engine.options(state, alice.id).filterIsInstance<Input.Cast>()
        assertTrue(listed.isEmpty() || Review1.refusedOptions(state).isEmpty(), "Denial listed with an empty Stack: $listed")
    }

    /** While a target is asked, every listed answer and Cancel are accepted. */
    // CR 601.2c
    @Test
    fun optionsWhileATargetIsAskedAreAccepted() = scenario {
        alice.battlefield("Mountain")
        alice.hand("Storm Spark")
        bob.battlefield("Thicketwood Elves")
        alice.casts("Storm Spark")
        assertIs<Decision.Targets>(pending)
        expectEveryOptionAccepted()
    }

    /** In the declare blockers step every listed declaration is accepted. */
    // CR 509.1
    @Test
    fun blockOptionsAreAccepted() = scenario {
        alice.battlefield("Thicketwood Elves", "Elvish Seer", "Gearwing Glider")
        bob.battlefield("Umbral Elves", "Wall of Portents", "Brash Pilferer")
        advanceTo(TurnStep.DeclareAttackers)
        expectEveryOptionAccepted()
        alice.attacks("Thicketwood Elves", "Elvish Seer")
        allPass()
        expectEveryOptionAccepted()
    }

    /** Priority passes skip nobody: in a 4-player game the next player in turn order gets it. */
    // CR 117.3d
    @Test
    fun priorityGoesAroundFourPlayers() = scenario(players = 4) {
        alice.battlefield("Mountain")
        alice.hand("Storm Spark")
        alice.casts("Storm Spark", target = "carol")
        alice.passes()
        assertEquals(bob.id, state.priority.holder)
        bob.passes()
        assertEquals(carol.id, state.priority.holder)
        carol.passes()
        assertEquals(dave.id, state.priority.holder)
        dave.passes()
        expect { carol.life shouldBe 17 }
    }

    /** The step ends only when all pass with an empty Stack; then the next step begins with the active player's
     * priority. */
    // CR 500.2, 117.3a
    @Test
    fun emptyStackPassesEndTheStep() = scenario {
        turn(number = 3, step = TurnStep.Upkeep)
        alice.library("Island", "Island")
        allPass()
        assertEquals(TurnStep.Draw, state.turn.step)
        assertEquals(alice.id, state.priority.holder)
        expect { alice.hand.size shouldBe 1 }
    }
}
