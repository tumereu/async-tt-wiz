package arcanum.engine.sets.mkm

import arcanum.api.TurnStep
import arcanum.engine.Engine
import arcanum.engine.cards.Cards
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.input.Input
import arcanum.engine.scenario.Scenario
import arcanum.engine.scenario.scenario
import arcanum.engine.sets.blb.resolveAll
import arcanum.engine.sets.tdm.Plain
import arcanum.engine.sets.tdm.pickTargets
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** MKM chunk 11: an Equipment with disguise attaching itself as it turns face up (CR 702.168, 301.5c), a creature that larger creatures can't block (CR 509.1b). */
class MkmChunk11DisguiseTwoScenariosTest {
    private val cudgel = Cards.invent("Veiled Cudgel", "{1}{R}", "Artifact — Equipment", """
        Equipped creature gets +3/+0.
        Disguise {2}{R} (You may cast this card face down for {3} as a 2/2 creature with ward {2}. Turn it face up any time for its disguise cost.)
        When this Equipment is turned face up, attach it to target creature you control.
        Equip {1}{R}
    """.trimIndent())
    private val clerk = Cards.invent("Exit Clerk", "{1}{U}", "Creature — Human Detective", """
        This creature can't be blocked by creatures with power 3 or greater.
        Disguise {1}{U} (You may cast this card face down for {3} as a 2/2 creature with ward {2}. Turn it face up any time for its disguise cost.)
        When this creature is turned face up, return another target creature to its owner's hand.
    """.trimIndent(), "2/1")

    private fun Scenario.turnUp(name: String) {
        val id = card(name)
        perform(Engine.options(state, alice.id).filterIsInstance<Input.Activate>().single { it.source == id })
        while (pending is Decision.ManaPayment) answer(Answer.ManaPayment(decision<Decision.ManaPayment>().suggestion))
    }

    // CR 702.168d, 708.8: turned face up, the Equipment is no longer a creature; its trigger attaches it (CR 301.5c) and its +3/+0 applies
    @Test
    fun faceDownCudgelTurnsUpAndAttachesItselfToACreature() = scenario {
        alice.battlefield(Plain.bear, "Mountain", "Mountain", "Mountain")
        alice.battlefield(cudgel, faceDown = true)
        expect { cudgel.power shouldBe 2 }
        turnUp(cudgel)
        pickTargets(Plain.bear)
        resolveAll()
        assertEquals(card(Plain.bear), state.objOrThrow(card(cudgel)).attachedTo?.let { (it as arcanum.engine.state.TargetRef.Object).id })
        expect { Plain.bear.power shouldBe 5 }
    }

    // CR 509.1b: a blocker with power 3 or greater can't block it, a smaller one can
    @Test
    fun clerkCantBeBlockedByPowerThreeOrGreater() = scenario {
        alice.battlefield(clerk)
        bob.battlefield(Plain.ogre, Plain.elf)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(clerk)
        advanceTo(TurnStep.DeclareBlockers)
        assertFailsWith<IllegalArgumentException> { bob.blocks(Plain.ogre to clerk) }
        bob.blocks(Plain.elf to clerk)
    }

    // CR 702.168d: the trigger returns another target creature to its owner's hand, never the Clerk itself
    @Test
    fun faceDownClerkTurnsUpAndReturnsAnotherCreature() = scenario {
        alice.battlefield("Island", "Island")
        alice.battlefield(clerk, faceDown = true)
        bob.battlefield(Plain.ogre, Plain.elf)
        turnUp(clerk)
        val targets = decision<Decision.Targets>()
        assertTrue(target(clerk) !in targets.options)
        assertTrue(target(Plain.ogre) in targets.options)
        answer(Answer.Targets(listOf(target(Plain.ogre))))
        resolveAll()
        expect {
            bob.hand has Plain.ogre
            bob.battlefield has Plain.elf
        }
    }
}
