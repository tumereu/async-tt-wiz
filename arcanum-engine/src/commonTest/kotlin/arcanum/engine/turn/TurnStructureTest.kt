package arcanum.engine.turn

import arcanum.api.CounterKind
import arcanum.api.Duration
import arcanum.api.Mechanic
import arcanum.api.TurnStep
import arcanum.api.Value
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.input.Input
import arcanum.engine.scenario.scenario
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Steps, turns and their turn-based actions (CR 500–514). */
class TurnStructureTest {
    // CR 117.4, 500.2: all players pass with an empty Stack: the step ends
    @Test
    fun passingWithAnEmptyStackEndsTheStep() = scenario {
        alice.passes()
        bob.passes()
        assertEquals(TurnStep.BeginningOfCombat, state.turn.step)
        // CR 117.3a: the active player receives priority in the new step
        assertEquals(alice.id, state.priority.holder)
    }

    // CR 117.3d: passing gives priority to the next player in turn order; the step goes on
    @Test
    fun passingGivesPriorityToTheNextPlayer() = scenario {
        alice.passes()
        assertEquals(bob.id, state.priority.holder)
        assertEquals(TurnStep.PrecombatMain, state.turn.step)
    }

    // CR 117.3d: only the player with priority can pass it
    @Test
    fun onlyThePlayerWithPriorityPasses() = scenario {
        assertFailsWith<IllegalArgumentException> { bob.passes() }
    }

    // CR 500.1, 502.3, 504.1: the next turn is the next player's: their permanents untap, they draw
    @Test
    fun theNextTurnUntapsAndDraws() = scenario {
        alice.battlefield("Elvish Seer", tapped = true)
        bob.battlefield("Thicketwood Elves", tapped = true)
        bob.library("Forest", "Island")
        advanceTo(TurnStep.Upkeep, turn = 2)
        assertEquals(bob.id, state.turn.active)
        expect {
            "Thicketwood Elves".tapped shouldBe false
            "Elvish Seer".tapped shouldBe true
        }
        advanceTo(TurnStep.PrecombatMain, turn = 2)
        expect {
            bob.hand has "Forest"
            bob.library.size shouldBe 1
        }
    }

    // CR 502.4, 117.3a: nobody has priority in the untap step; the active player gets it in upkeep
    @Test
    fun activePlayerGetsPriorityInUpkeep() = scenario {
        advanceTo(TurnStep.Upkeep, turn = 2)
        assertEquals(bob.id, state.priority.holder)
        assertEquals(0, state.priority.passes)
    }

    // CR 103.8a: in a two-player game the starting player skips the draw step of their first turn
    @Test
    fun startingPlayerSkipsTheFirstDraw() = scenario {
        turn(1, step = TurnStep.Upkeep)
        alice.library("Forest")
        advanceTo(TurnStep.PrecombatMain)
        expect { alice.hand.size shouldBe 0 }
    }

    // CR 103.8c: in other multiplayer games nobody skips it (the host clears the flag)
    @Test
    fun withoutTheFlagTheFirstTurnDraws() = scenario(players = 3) {
        turn(1, step = TurnStep.Upkeep)
        state._skipFirstDraw = false
        alice.library("Forest")
        advanceTo(TurnStep.PrecombatMain)
        expect { alice.hand has "Forest" }
    }

    // CR 504.1: later turns of the starting player draw as usual
    @Test
    fun theStartingPlayersLaterTurnsDraw() = scenario {
        turn(3, step = TurnStep.Upkeep)
        alice.library("Forest")
        advanceTo(TurnStep.PrecombatMain)
        expect { alice.hand has "Forest" }
    }

    // CR 121.4, 704.5b: drawing from an empty library loses the game at the next check
    @Test
    fun drawingFromAnEmptyLibraryLoses() = scenario {
        advanceTo(TurnStep.Draw, turn = 2)
        assertTrue(state.player(bob.id).lost)
    }

    // CR 106.4, 500.4: mana empties from pools as a step ends
    @Test
    fun manaEmptiesBetweenSteps() = scenario {
        alice.battlefield("Lotus Frond")
        alice.activates("Lotus Frond")
        answer(Answer.ColorChoice(arcanum.api.Color.Red))
        assertEquals(1, state.player(alice.id).manaPool.size)
        allPass()
        assertEquals(TurnStep.BeginningOfCombat, state.turn.step)
        assertEquals(0, state.player(alice.id).manaPool.size)
    }

    // CR 514.1: the active player discards down to seven in the cleanup step
    @Test
    fun cleanupDiscardsToHandSize() = scenario {
        alice.hand("Forest", "Forest", "Forest", "Island", "Island", "Island", "Mountain", "Mountain", "Swamp")
        advanceTo(TurnStep.End)
        allPass()
        val d = assertIs<Decision.ChooseObjects>(pending)
        assertEquals(2, d.min)
        assertEquals(alice.id, d.player)
        alice.chooses("Swamp", "Mountain")
        expect {
            alice.hand.size shouldBe 7
            alice.graveyard has "Swamp"
            log mentions "Discarded"
        }
        // The turn goes on after the discard
        assertEquals(bob.id, state.turn.active)
        assertEquals(TurnStep.Upkeep, state.turn.step)
    }

    // CR 514.1: a wrong number of cards is refused
    @Test
    fun cleanupDiscardNeedsTheRightNumber() = scenario {
        alice.hand("Forest", "Forest", "Forest", "Island", "Island", "Island", "Mountain", "Mountain")
        advanceTo(TurnStep.End)
        allPass()
        assertFailsWith<IllegalArgumentException> { alice.chooses("Forest", "Island") }
    }

    // CR 402.2: no maximum hand size, no discard
    @Test
    fun noMaximumHandSizeKeepsTheCards() = scenario {
        effect(alice, Mechanic.NoMaximumHandSize, affects = arcanum.api.Affected.Players(arcanum.api.Players.You), duration = Duration.Indefinitely)
        alice.hand("Forest", "Forest", "Forest", "Island", "Island", "Island", "Mountain", "Mountain", "Swamp")
        advanceTo(TurnStep.Upkeep, turn = 2)
        expect { alice.hand.size shouldBe 9 }
    }

    // CR 514.2: damage is removed in the cleanup step
    @Test
    fun cleanupRemovesDamage() = scenario {
        alice.battlefield("Wall of Portents")
        change(arcanum.engine.change.Changes.damageMarked(state, card("Wall of Portents"), 2))
        advanceTo(TurnStep.Upkeep, turn = 2)
        assertEquals(0, state.objOrThrow(card("Wall of Portents")).damage)
    }

    // CR 514.2: "until end of turn" effects end in the cleanup step
    @Test
    fun endOfTurnEffectsEnd() = scenario {
        alice.battlefield("Elvish Seer")
        effect(alice, Mechanic.ModifyPowerToughness(Value.Fixed(2), Value.Fixed(2)), on = listOf("Elvish Seer"))
        expect { "Elvish Seer".power shouldBe 3 }
        advanceTo(TurnStep.End)
        expect { "Elvish Seer".power shouldBe 3 }
        advanceTo(TurnStep.Upkeep, turn = 2)
        expect { "Elvish Seer".power shouldBe 1 }
    }

    // CR 611.2b: "until your next turn" lasts through the opponent's turn and ends as yours begins
    @Test
    fun untilYourNextTurnEndsAtYourTurn() = scenario {
        players.forEach { it.library("Island", "Island", "Island") }
        alice.battlefield("Elvish Seer")
        effect(alice, Mechanic.ModifyPowerToughness(Value.Fixed(2), Value.Fixed(0)), on = listOf("Elvish Seer"), duration = Duration.UntilYourNextTurn)
        advanceTo(TurnStep.End, turn = 2)
        expect { "Elvish Seer".power shouldBe 3 }
        advanceTo(TurnStep.Upkeep, turn = 3)
        expect { "Elvish Seer".power shouldBe 1 }
    }

    // CR 511.2: "until end of combat" effects end with the combat phase
    @Test
    fun endOfCombatEffectsEnd() = scenario {
        alice.battlefield("Elvish Seer")
        effect(alice, Mechanic.ModifyPowerToughness(Value.Fixed(1), Value.Fixed(0)), on = listOf("Elvish Seer"), duration = Duration.EndOfCombat)
        advanceTo(TurnStep.EndOfCombat)
        expect { "Elvish Seer".power shouldBe 2 }
        advanceTo(TurnStep.PostcombatMain)
        expect { "Elvish Seer".power shouldBe 1 }
    }

    // CR 101.4, 500.1: a player who lost takes no turns
    @Test
    fun lostPlayersAreSkipped() = scenario(players = 3) {
        state.updatePlayer(bob.id) { it.copy(lost = true) }
        advanceTo(TurnStep.Upkeep, turn = 2)
        assertEquals(carol.id, state.turn.active)
    }

    // CR 117.3d: priority skips a player who lost; all remaining players passing ends the step
    @Test
    fun priorityGoesAroundThePlayersStillIn() = scenario(players = 3) {
        state.updatePlayer(bob.id) { it.copy(lost = true) }
        alice.passes()
        assertEquals(carol.id, state.priority.holder)
        carol.passes()
        assertEquals(TurnStep.BeginningOfCombat, state.turn.step)
    }

    // CR 101.4: three players take turns in turn order
    @Test
    fun threePlayersInTurnOrder() = scenario(players = 3) {
        players.forEach { it.library("Island", "Island", "Island") }
        advanceTo(TurnStep.Upkeep, turn = 2)
        assertEquals(bob.id, state.turn.active)
        advanceTo(TurnStep.Upkeep, turn = 3)
        assertEquals(carol.id, state.turn.active)
        advanceTo(TurnStep.Upkeep, turn = 4)
        assertEquals(alice.id, state.turn.active)
    }

    // CR 500.7: an extra turn comes right after this one
    @Test
    fun extraTurnComesNext() = scenario {
        players.forEach { it.library("Island", "Island", "Island") }
        state._turn = state.turn.copy(extraTurns = listOf(alice.id))
        advanceTo(TurnStep.Upkeep, turn = 2)
        assertEquals(alice.id, state.turn.active)
        advanceTo(TurnStep.Upkeep, turn = 3)
        assertEquals(bob.id, state.turn.active)
    }

    // CR 122.1d: a stun counter is removed instead of untapping
    @Test
    fun stunCounterKeepsItTapped() = scenario {
        bob.battlefield("Thicketwood Elves", tapped = true, counters = listOf(CounterKind.Stun to 1))
        advanceTo(TurnStep.Upkeep, turn = 2)
        expect {
            "Thicketwood Elves".tapped shouldBe true
            "Thicketwood Elves".counters(CounterKind.Stun) shouldBe 0
        }
    }

    // CR 502.3: a permanent that doesn't untap stays tapped
    @Test
    fun doesntUntapStaysTapped() = scenario {
        bob.battlefield("Thicketwood Elves", tapped = true)
        effect(bob, Mechanic.DoesntUntap, affects = arcanum.api.Affected.Objects(arcanum.api.Objects.Bound("it")),
            names = mapOf("it" to "Thicketwood Elves"), duration = Duration.Indefinitely)
        advanceTo(TurnStep.Upkeep, turn = 2)
        expect { "Thicketwood Elves".tapped shouldBe true }
    }

    // CR 502.1, 702.26a: phasing: the active player's permanents with phasing phase out, phased-out ones phase in
    @Test
    fun phasingInTheUntapStep() = scenario {
        Cards.invent("Flickering Wisp", "{1}{U}", "Creature — Spirit", "Phasing", "2/2")
        bob.battlefield("Flickering Wisp")
        alice.library("Island", "Island")
        bob.library("Island", "Island")
        advanceTo(TurnStep.Upkeep, turn = 2)
        assertTrue(state.objOrThrow(card("Flickering Wisp")).phasedOut)
        advanceTo(TurnStep.Upkeep, turn = 4)
        assertTrue(!state.objOrThrow(card("Flickering Wisp")).phasedOut)
    }

    // CR 508.8: without attackers the declare blockers and combat damage steps are skipped
    @Test
    fun noAttackersSkipsBlockersAndDamage() = scenario {
        val steps = mutableListOf<TurnStep>()
        while (state.turn.step != TurnStep.PostcombatMain) {
            if (state.priority.holder == null) declareNone() else passPriority()
            steps += state.turn.step
        }
        assertTrue(TurnStep.DeclareBlockers !in steps && TurnStep.CombatDamage !in steps, "$steps")
        assertTrue(TurnStep.EndOfCombat in steps)
    }

    // CR 508.1, P5: with nothing that can attack, no attackers are declared and the active player gets priority
    @Test
    fun nothingCanAttackDeclaresNone() = scenario {
        advanceTo(TurnStep.DeclareAttackers)
        assertEquals(alice.id, state.priority.holder)
        assertTrue(state.combat!!.attackers.isEmpty())
    }

    // CR 508.1: with a creature that could attack, the game waits for the declaration
    @Test
    fun waitsForAttackers() = scenario {
        alice.battlefield("Elvish Seer")
        advanceTo(TurnStep.BeginningOfCombat)
        allPass()
        assertEquals(TurnStep.DeclareAttackers, state.turn.step)
        assertNull(state.priority.holder)
        assertTrue(arcanum.engine.Engine.options(state, alice.id).any { it is Input.DeclareAttackers })
        assertFailsWith<IllegalArgumentException> { perform(Input.PassPriority(alice.id)) }
    }

    // CR 714.3b: as the precombat main phase begins, the active player puts a lore counter on each Saga they control
    @Test
    fun loreCounterAfterTheDrawStep() = scenario {
        alice.battlefield("Haruka's Age of Honor", counters = listOf(CounterKind.Lore to 1))
        bob.battlefield("Haruka's Age of Honor", counters = listOf(CounterKind.Lore to 1))
        turn(number = 1, step = TurnStep.Draw)
        allPass()
        assertEquals(TurnStep.PrecombatMain, state.turn.step)
        expect {
            card("Haruka's Age of Honor", 0).counters(CounterKind.Lore) shouldBe 2
            card("Haruka's Age of Honor", 1).counters(CounterKind.Lore) shouldBe 1
        }
    }

    // CR 714.3a: a Saga enters with a lore counter, and its chapter I triggers (CR 714.2b)
    @Test
    fun sagaEntersWithALoreCounter() = scenario {
        alice.battlefield("Plains", "Plains", "Elvish Seer")
        alice.hand("Haruka's Age of Honor")
        alice.casts("Haruka's Age of Honor")
        allPass()
        expect {
            "Haruka's Age of Honor".counters(CounterKind.Lore) shouldBe 1
            stack.names shouldBe listOf("ability of Haruka's Age of Honor")
        }
    }

    // CR 306.5b: a planeswalker enters with loyalty counters equal to its printed loyalty
    @Test
    fun planeswalkerEntersWithLoyalty() = scenario {
        alice.battlefield("Mountain", "Mountain", "Mountain")
        alice.hand("Ignia, Acolyte of Embers")
        alice.casts("Ignia, Acolyte of Embers")
        allPass()
        expect {
            alice.battlefield has "Ignia, Acolyte of Embers"
            "Ignia, Acolyte of Embers".counters(CounterKind.Loyalty) shouldBe 4
            "Ignia, Acolyte of Embers".loyalty shouldBe 4
        }
    }
}

private typealias Cards = arcanum.engine.cards.Cards
