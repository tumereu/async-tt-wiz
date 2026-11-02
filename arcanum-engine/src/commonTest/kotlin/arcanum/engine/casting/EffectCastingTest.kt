package arcanum.engine.casting

import arcanum.engine.Engine
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.input.Input
import arcanum.engine.scenario.Scenario
import arcanum.engine.scenario.scenario
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertTrue

/** Spells cast while another spell or ability resolves (CR 608.2g) go through the same casting steps as any spell. */
class EffectCastingTest {
    /** Alice casts Splintered Envoy ({1}{G}{U}, cascade) and its cascade trigger resolves up to "cast it?", answered yes. */
    private fun Scenario.cascadeInto(vararg library: String) {
        alice.battlefield("Forest", "Island", "Forest")
        alice.hand("Splintered Envoy")
        alice.library(*library)
        alice.casts("Splintered Envoy")
        allPass()
        answer(Answer.YesNo(true))
    }

    /** Without paying its mana cost, a spell with {X} is cast with X = 0. */
    // CR 107.3b, 702.85a
    @Test
    fun withoutPayingXIsZero() = scenario {
        cascadeInto("Doomfire")
        decision<Decision.Targets>()
        answer(Answer.Targets(listOf(target("bob"))))
        expect { stack.names shouldBe listOf("Splintered Envoy", "Doomfire") }
        allPass()
        expect { bob.life shouldBe 20 }
    }

    /** A modal spell cast by an effect asks its modes, then the chosen mode's target; a sole legal target is taken. */
    // CR 601.2b, 601.2c, 608.2g
    @Test
    fun modesAreChosen() = scenario {
        bob.battlefield("Thicketwood Elves")
        cascadeInto("Grindstone Rasp")
        decision<Decision.Modes>()
        answer(Answer.Modes(listOf(0)))
        expect { stack.names shouldBe listOf("Splintered Envoy", "Grindstone Rasp") }
        assertEquals(listOf(target("Thicketwood Elves")), state.stackItems.getValue(card("Grindstone Rasp")).targets.values.single())
        // CR 608.2g: nobody got priority while the cascade trigger resolved; now the active player does
        assertEquals(alice.id, state.priority.holder)
    }

    /** A mode whose target is missing can't be chosen; the options list only the mode that works. */
    // CR 601.2c, 700.2
    @Test
    fun aModeWithoutTargetsIsRefused() = scenario {
        bob.battlefield("Thicketwood Elves")
        cascadeInto("Grindstone Rasp")
        assertFails { answer(Answer.Modes(listOf(1))) }
        val answers = Engine.options(state, alice.id).filterIsInstance<Input.Answer>()
        assertEquals(listOf(Answer.Modes(listOf(0))), answers.map { it.answer })
    }

    /** With no legal target for any mode the spell isn't cast: it goes to the bottom with the other cards. */
    // CR 601.2c, 702.85a
    @Test
    fun noModeWithTargetsIsntCast() = scenario {
        cascadeInto("Island", "Grindstone Rasp", "Forest")
        expect {
            stack.names shouldBe listOf("Splintered Envoy")
            alice.library has "Grindstone Rasp"
            alice.library has "Island"
        }
        assertTrue(state.player(alice.id).library.takeLast(2).map { state.objOrThrow(it) }.any { nameOf(it) == "Grindstone Rasp" })
    }
}
