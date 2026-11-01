package arcanum.engine.cube

import arcanum.api.CounterKind
import arcanum.api.Keyword
import arcanum.api.TurnStep
import arcanum.api.Zone
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.decision.ManaUse
import arcanum.engine.input.Input
import arcanum.engine.scenario.Scenario
import arcanum.engine.scenario.scenario
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** Chunk 1 in play: creatures with only keywords (CR 702): flying, first strike, persist, cascade, lifelink, and
 * Phyrexian and hybrid costs (CR 107.4). */
class Chunk01KeywordCreaturesScenariosTest {
    private fun Scenario.toCombat() {
        turn(1, step = TurnStep.BeginningOfCombat)
        advanceTo(TurnStep.DeclareAttackers)
    }

    /** A 1/1 for {0} with no abilities: cast for nothing, it attacks next turn. */
    @Test
    fun tinkeredMite() = scenario {
        alice.hand("Tinkered Mite")
        alice.battlefield("Tinkered Mite")

        perform(Input.Cast(alice.id, card("Tinkered Mite", 0)))
        allPass()
        expect { card("Tinkered Mite", 0).zone shouldBe Zone.Battlefield }

        advanceTo(TurnStep.DeclareAttackers)
        // CR 302.6: the new one can't attack yet
        assertFailsWith<IllegalArgumentException> {
            perform(Input.DeclareAttackers(alice.id, listOf(arcanum.engine.input.Attack(card("Tinkered Mite", 0), target("bob")))))
        }
        perform(Input.DeclareAttackers(alice.id, listOf(arcanum.engine.input.Attack(card("Tinkered Mite", 1), target("bob")))))
        advanceTo(TurnStep.EndOfCombat)

        expect { bob.life shouldBe 19 }
    }

    /** Flying (CR 702.9b): it can block a flyer; a creature without flying or reach can't block it. */
    @Test
    fun gearwingGlider() = scenario {
        alice.battlefield("Plumes of Eden")
        bob.battlefield("Gearwing Glider", "Tinkered Mite")
        toCombat()

        alice.attacks("Plumes of Eden")
        allPass()
        assertFailsWith<IllegalArgumentException> { bob.blocks("Tinkered Mite" to "Plumes of Eden") }
        bob.blocks("Gearwing Glider" to "Plumes of Eden")
        advanceTo(TurnStep.EndOfCombat)

        expect {
            bob.life shouldBe 20
            "Gearwing Glider" hasKeyword Keyword.Flying
            bob.battlefield has "Gearwing Glider"
        }
    }

    @Test
    fun gearwingGliderCantBeBlockedByGroundCreatures() = scenario {
        // CR 702.9b
        alice.battlefield("Gearwing Glider")
        bob.battlefield("Tinkered Mite")
        toCombat()

        alice.attacks("Gearwing Glider")
        allPass()

        // No possible blocker: the declaration is skipped (P5)
        assertEquals(TurnStep.DeclareBlockers, state.turn.step)
        assertEquals(alice.id, state.priority.holder)
    }

    /** {2}{W/P} (CR 107.4f: 2 life for the Phyrexian symbol) and first strike (CR 702.7b): it kills a 2/2 blocker
     * before that blocker deals damage. */
    @Test
    fun enamelPhalanxer() = scenario {
        alice.battlefield("Forest", "Forest")
        alice.hand("Enamel Phalanxer")

        perform(Input.Cast(alice.id, card("Enamel Phalanxer")))
        val pay = decision<Decision.ManaPayment>()
        // No white source: the suggestion pays the {W/P} with 2 life
        assertTrue(ManaUse.Life(1) in pay.suggestion)
        answer(Answer.ManaPayment(pay.suggestion))
        allPass()

        expect {
            alice.life shouldBe 18
            alice.battlefield has "Enamel Phalanxer"
        }
    }

    @Test
    fun enamelPhalanxerFirstStrike() = scenario {
        alice.battlefield("Enamel Phalanxer")
        bob.battlefield("Thicketwood Elves")
        toCombat()

        alice.attacks("Enamel Phalanxer")
        allPass()
        bob.blocks("Thicketwood Elves" to "Enamel Phalanxer")
        advanceTo(TurnStep.EndOfCombat)

        expect {
            bob.graveyard has "Thicketwood Elves"
            alice.battlefield has "Enamel Phalanxer"
            "Enamel Phalanxer" hasKeyword Keyword.FirstStrike
        }
    }

    /** Persist (CR 702.79a): the creature [name] dies, returns with a -1/-1 counter, and dies for good the next time. */
    private fun persists(name: String) = scenario {
        alice.battlefield(name)
        alice.hand("Storm Spark", "Storm Spark")
        alice.battlefield("Mountain", "Mountain")

        alice.casts("Storm Spark", target = name)
        allPass()
        expect { stack.names shouldBe listOf("ability of $name") }
        allPass()
        expect {
            alice.battlefield has name
            name.counters(CounterKind.PowerToughness(-1, -1)) shouldBe 1
            name.power shouldBe 1
        }

        perform(Input.Cast(alice.id, card("Storm Spark", 1)))
        answer(Answer.Targets(listOf(target(name))))
        answer(Answer.ManaPayment(decision<Decision.ManaPayment>().suggestion))
        allPass()

        expect {
            // With a -1/-1 counter on it, persist doesn't trigger
            stack.isEmpty()
            alice.graveyard has name
        }
    }

    /** Persist on a 2/2 for {1}{B}. */
    @Test
    fun festeringGremlin() = persists("Festering Gremlin")

    /** Persist on a 2/2 for {1}{G/W}. */
    @Test
    fun havenWarden() = persists("Haven Warden")

    /** {1}{G/W} (CR 107.4e): the hybrid symbol is paid with either colour. */
    @Test
    fun havenWardenHybridCost() = scenario {
        alice.battlefield("Plains", "Island")
        alice.hand("Haven Warden")

        alice.casts("Haven Warden")
        allPass()

        expect {
            alice.battlefield has "Haven Warden"
            "Plains".tapped shouldBe true
            "Island".tapped shouldBe true
        }
    }

    /** Cascade (CR 702.85a): cast for {1}{G}{U}, it exiles until a cheaper nonland card, which may be cast for free; the
     * rest go to the bottom in a random order. */
    @Test
    fun splinteredEnvoy() = scenario {
        alice.battlefield("Forest", "Island", "Island")
        alice.hand("Splintered Envoy")
        alice.library("Forest", "Splintered Envoy", "Tinkered Mite", "Island")

        alice.casts("Splintered Envoy")
        expect { stack.names shouldBe listOf("Splintered Envoy", "ability of Splintered Envoy") }
        allPass()
        // The Envoy in the library costs as much: it's passed over for the Mite
        alice.answers(Answer.YesNo(true))
        expect { stack.names shouldBe listOf("Splintered Envoy", "Tinkered Mite") }
        allPass()
        allPass()

        expect {
            alice.battlefield has "Tinkered Mite"
            card("Splintered Envoy", 0).zone shouldBe Zone.Battlefield
            alice.library.size shouldBe 3
            alice.library has "Island"
        }
        // The Island was never exiled; the Forest and the other Envoy went under it
        assertEquals(card("Island", 2), state.player(alice.id).library.first())
    }

    /** {1}{B/P}, flying and lifelink (CR 702.15b): its combat damage gains its controller that much life. */
    @Test
    fun cryptFlitter() = scenario {
        alice.battlefield("Crypt Flitter")
        bob.battlefield("Tinkered Mite")
        alice.life = 10
        toCombat()

        alice.attacks("Crypt Flitter")
        advanceTo(TurnStep.EndOfCombat)

        expect {
            bob.life shouldBe 19
            alice.life shouldBe 11
            "Crypt Flitter" hasKeyword Keyword.Lifelink
        }
    }

    @Test
    fun cryptFlitterCastWithLife() = scenario {
        alice.battlefield("Swamp")
        alice.hand("Crypt Flitter")

        perform(Input.Cast(alice.id, card("Crypt Flitter")))
        val pay = decision<Decision.ManaPayment>()
        // CR 107.4f: the Swamp pays {1}, 2 life pays {B/P}
        answer(Answer.ManaPayment(pay.options.filter { it is ManaUse.Ability && it.source == card("Swamp") } + ManaUse.Life(1)))

        expect {
            stack.names shouldBe listOf("Crypt Flitter")
            alice.life shouldBe 18
        }
    }
}
