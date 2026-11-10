package arcanum.engine.sets.inr

import arcanum.api.CounterKind
import arcanum.api.TurnStep
import arcanum.engine.cards.Cards
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.scenario.Scenario
import arcanum.engine.scenario.scenario
import arcanum.engine.sets.rtr.modes
import arcanum.engine.sets.rtr.resolveAll
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/** INR fix chunk: "any opponent may" with three players (CR 101.4) and a spell returned from the Stack to its owner's hand (CR 400.7).
 * Invented names carry the oracle text of the real cards. */
class InrFixesScenariosTest {
    private val fiend = Cards.invent("Blasphemous Fiend", "{2}{B}{B}", "Creature — Demon", """
        Flying
        At the beginning of each combat, any opponent may sacrifice a creature of their choice. If a player does, tap this creature and put a +1/+1 counter on it.
    """, "6/6")
    private val rat = Cards.invent("Mire Rat", "{B}", "Creature — Rat", "", "1/1")
    private val toad = Cards.invent("Fen Toad", "{G}", "Creature — Frog", "", "1/1")
    private val terror = Cards.invent("Deepwater Terror", "{5}{U}{U}", "Creature — Kraken Horror", """
        Flash
        This spell can't be countered.
        Whenever you cast a spell, choose up to one —
        • Return target spell you don't control to its owner's hand.
        • Return target nonland permanent to its owner's hand.
    """, "7/8")
    private val comfort = Cards.invent("Small Comfort", "{1}", "Instant", "You gain 1 life.")
    private val jolt = Cards.invent("Spiteful Jolt", "{R}", "Instant", "Spiteful Jolt deals 2 damage to target creature.", null)

    private val tithe = Cards.invent("Grim Tithe", "{1}{B}", "Sorcery", "Sacrifice a creature. If you do, you gain life equal to that creature's toughness.")

    private val Scenario.carol get() = players[2]

    private fun Scenario.fiendAtCombat() {
        alice.battlefield(fiend)
        bob.battlefield(rat)
        carol.battlefield(toad)
    }

    // CR 101.4, 118.12: "any opponent may" goes around the opponents in APNAP order; Bob declines, Carol sacrifices, so the Fiend is tapped and grows
    @Test
    fun fiendAsksBobThenCarol() = scenario(players = 3) {
        fiendAtCombat()
        advanceTo(TurnStep.BeginningOfCombat)
        resolveStack()
        assertEquals(bob.id, decision<Decision.May>().player)
        answer(Answer.YesNo(false))
        assertEquals(carol.id, decision<Decision.May>().player)
        answer(Answer.YesNo(true))
        resolveAll()
        expect {
            bob.battlefield has rat
            carol.graveyard has toad
            fiend.tapped shouldBe true
            fiend.counters(CounterKind.PowerToughness(1, 1)) shouldBe 1
        }
    }

    // CR 101.4: once Bob has sacrificed, Carol isn't asked; one counter only
    @Test
    fun fiendStopsAtTheFirstOpponentWhoSacrifices() = scenario(players = 3) {
        fiendAtCombat()
        advanceTo(TurnStep.BeginningOfCombat)
        resolveStack()
        assertEquals(bob.id, decision<Decision.May>().player)
        answer(Answer.YesNo(true))
        assertFalse(pending is Decision.May && pending?.player == carol.id, "Carol isn't asked after Bob sacrificed")
        resolveAll()
        expect {
            bob.graveyard has rat
            carol.battlefield has toad
            fiend.counters(CounterKind.PowerToughness(1, 1)) shouldBe 1
        }
    }

    // CR 101.4: on Carol's turn APNAP order starts with Carol, the active player, then Bob
    @Test
    fun fiendAsksTheActiveOpponentFirst() = scenario(players = 3) {
        fiendAtCombat()
        turn(number = 3, active = carol)
        advanceTo(TurnStep.BeginningOfCombat)
        resolveStack()
        assertEquals(carol.id, decision<Decision.May>().player)
        answer(Answer.YesNo(false))
        assertEquals(bob.id, decision<Decision.May>().player)
        answer(Answer.YesNo(false))
        resolveAll()
        expect {
            fiend.tapped shouldBe false
            fiend.counters(CounterKind.PowerToughness(1, 1)) shouldBe 0
        }
    }

    // CR 115.1, 700.2: Alice's own spell isn't a "spell you don't control"; Bob's Jolt goes back to his hand (CR 400.7: a new object there) and
    // never resolves, so the Terror isn't dealt damage
    @Test
    fun terrorReturnsTheOpponentsSpellToHand() = scenario {
        alice.battlefield(terror, "Island")
        alice.hand(comfort)
        bob.battlefield("Mountain")
        bob.hand(jolt)
        alice.passes()
        bob.casts(jolt, target = terror)
        bob.passes()
        alice.casts(comfort)
        resolveAll()
        modes(0)
        // The Jolt is the lone legal target, so it is chosen without asking: with Alice's own spell legal too she would be asked
        assertFalse(pending is Decision.Targets, "Alice's own spell can't be chosen")
        resolveAll()
        expect {
            bob.hand has jolt
            alice.graveyard has comfort
            alice.life shouldBe 21
            terror.view.obj?.damage shouldBe 0
        }
    }

    // CR 701.21a, 608.2h: the life gained is the sacrificed Rat's toughness as it last existed on the battlefield, its +1/+1 counter included
    @Test
    fun titheGainsTheSacrificedCreaturesLastToughness() = scenario {
        alice.battlefield(rat, counters = listOf(CounterKind.PowerToughness(1, 1) to 1))
        alice.battlefield("Swamp", "Swamp")
        alice.hand(tithe)
        turn(step = TurnStep.PrecombatMain)
        alice.casts(tithe)
        resolveAll()
        expect {
            alice.graveyard has rat
            alice.life shouldBe 22
        }
    }
}
