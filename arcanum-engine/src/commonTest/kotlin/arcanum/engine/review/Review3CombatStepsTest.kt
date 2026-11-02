package arcanum.engine.review

import arcanum.api.TurnStep
import arcanum.engine.Engine
import arcanum.engine.decision.Decision
import arcanum.engine.review.Review3.doubleStrikeBanner
import arcanum.engine.review.Review3.firstStrikeBanner
import arcanum.engine.review.Review3.golem
import arcanum.engine.scenario.Scenario
import arcanum.engine.scenario.scenario
import arcanum.engine.setup.GameResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Review 3: two combat damage steps (CR 510.4): first strike and double strike gained or lost between the steps,
 * creatures put onto the battlefield attacking after the first-strike step, a player losing between the steps.
 */
class Review3CombatStepsTest {
    /** To the first-strike combat damage step, its damage dealt, priority given. */
    private fun Scenario.toFirstStrikeStep() {
        advanceTo(TurnStep.CombatDamage)
        assertTrue(state.combat?.firstStrikeStep == true, "a first-strike combat damage step")
    }

    /** The Golem has first strike from Golem Grafter as the first step begins; the Grafter dies in that step, so the
     * Golem loses first strike, but it had it as the first step began: it deals no damage in the second step. */
    // CR 510.4
    @Test
    fun firstStrikeLostBetweenTheStepsDealsNoSecondDamage() = scenario {
        alice.battlefield("Golem Grafter", golem)
        bob.battlefield("Enamel Phalanxer")
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks("Golem Grafter", golem)
        allPass()
        bob.blocks("Enamel Phalanxer" to "Golem Grafter")
        toFirstStrikeStep()
        expect {
            alice.graveyard has "Golem Grafter"
            bob.life shouldBe 17
        }
        advanceTo(TurnStep.EndOfCombat)
        expect { bob.life shouldBe 17 }
    }

    /** A first striker given double strike after the first-strike step deals damage again in the second step: "the
     * remaining attackers and blockers that currently have double strike". */
    // CR 510.4, 702.4b
    @Test
    fun doubleStrikeGainedAfterTheFirstStepDealsAgain() = scenario {
        alice.battlefield("Enamel Phalanxer", doubleStrikeBanner)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks("Enamel Phalanxer")
        toFirstStrikeStep()
        expect { bob.life shouldBe 17 }
        alice.activates(doubleStrikeBanner, target = "Enamel Phalanxer")
        allPass()
        advanceTo(TurnStep.EndOfCombat)
        expect { bob.life shouldBe 14 }
    }

    /** A creature without first strike that gains double strike after the first-strike step deals damage once, in the
     * second step. */
    // CR 510.4
    @Test
    fun doubleStrikeGainedByARegularCreatureDealsOnce() = scenario {
        alice.battlefield("Enamel Phalanxer", "Tinkered Mite", doubleStrikeBanner)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks("Enamel Phalanxer", "Tinkered Mite")
        toFirstStrikeStep()
        expect { bob.life shouldBe 17 }
        alice.activates(doubleStrikeBanner, target = "Tinkered Mite")
        allPass()
        advanceTo(TurnStep.EndOfCombat)
        expect { bob.life shouldBe 16 }
    }

    /** A creature that gains first strike only after the first-strike step still deals its damage in the second step:
     * it had neither first strike nor double strike as the first step began. */
    // CR 510.4
    @Test
    fun firstStrikeGainedAfterTheFirstStepStillDealsDamage() = scenario {
        alice.battlefield("Enamel Phalanxer", "Tinkered Mite", firstStrikeBanner)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks("Enamel Phalanxer", "Tinkered Mite")
        toFirstStrikeStep()
        alice.activates(firstStrikeBanner, target = "Tinkered Mite")
        allPass()
        advanceTo(TurnStep.EndOfCombat)
        expect { bob.life shouldBe 16 }
    }

    /** First strike gained before the first combat damage step (in declare blockers) makes a first-strike step, in
     * which only that creature deals damage. */
    // CR 510.4
    @Test
    fun firstStrikeGainedBeforeDamageMakesTwoSteps() = scenario {
        alice.battlefield("Tinkered Mite", "Elvish Seer", firstStrikeBanner)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks("Tinkered Mite", "Elvish Seer")
        allPass()
        alice.activates(firstStrikeBanner, target = "Tinkered Mite")
        allPass()
        toFirstStrikeStep()
        expect { bob.life shouldBe 19 }
        advanceTo(TurnStep.EndOfCombat)
        expect { bob.life shouldBe 18 }
    }

    /** A Ninja put onto the battlefield attacking during the first-strike damage step (returning an attacker that
     * hasn't dealt damage) deals its damage in the second step. */
    // CR 510.4, 702.49a, 506.3
    @Test
    fun aNinjaAfterTheFirstStrikeStepDealsRegularDamage() = scenario {
        alice.battlefield("Enamel Phalanxer", "Tinkered Mite", "Island", "Island")
        alice.hand("Ninja of the Silent Hours")
        alice.library("Swamp")
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks("Enamel Phalanxer", "Tinkered Mite")
        toFirstStrikeStep()
        expect { bob.life shouldBe 17 }
        alice.activates("Ninja of the Silent Hours", choosing = listOf("Tinkered Mite"))
        allPass()
        advanceAnswering(TurnStep.EndOfCombat)
        expect {
            bob.life shouldBe 15
            alice.hand has "Tinkered Mite"
        }
    }

    /** Ninjutsu returning the first striker that already dealt damage: the Ninja is a new attacker and deals damage in
     * the second step. */
    // CR 510.4, 702.49a, 400.7
    @Test
    fun aNinjaReplacingTheFirstStrikerDealsRegularDamage() = scenario {
        alice.battlefield("Enamel Phalanxer", "Island", "Island")
        alice.hand("Ninja of the Silent Hours")
        alice.library("Swamp")
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks("Enamel Phalanxer")
        toFirstStrikeStep()
        alice.activates("Ninja of the Silent Hours", choosing = listOf("Enamel Phalanxer"))
        allPass()
        advanceAnswering(TurnStep.EndOfCombat)
        expect { bob.life shouldBe 15 }
    }

    /** A player brought to 0 life by first-strike damage loses before the regular combat damage step: the game ends
     * there and no regular damage is dealt. */
    // CR 510.4, 704.5a, 104.3b
    @Test
    fun aPlayerLosesBetweenTheTwoSteps() = scenario {
        alice.battlefield("Enamel Phalanxer", "Tinkered Mite")
        bob.life = 3
        bob.battlefield("Elvish Seer")
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks("Enamel Phalanxer", "Tinkered Mite")
        allPass()
        bob.blocks("Elvish Seer" to "Tinkered Mite")
        advanceTo(TurnStep.CombatDamage)
        assertEquals(GameResult.Won(alice.id), Engine.result(state))
        expect { alice.battlefield has "Tinkered Mite" }
    }

    /** A blocked first striker whose blocker died in the first step deals no damage in the second step without
     * trample; with double strike and trample it deals all of it to the player then. */
    // CR 510.4, 702.19e, 509.1h
    @Test
    fun doubleStrikeTrampleOverADeadBlocker() = scenario {
        alice.battlefield("Gravehorde Spellcaller", doubleStrikeBanner)
        bob.battlefield("Tinkered Mite")
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks("Gravehorde Spellcaller")
        answerMays(false)
        allPass()
        bob.blocks("Tinkered Mite" to "Gravehorde Spellcaller")
        alice.activates(doubleStrikeBanner, target = "Gravehorde Spellcaller")
        allPass()
        toFirstStrikeStep()
        if (pending is Decision.Divide) divideAsSuggested()
        advanceTo(TurnStep.EndOfCombat)
        expect {
            bob.graveyard has "Tinkered Mite"
            // 1 to the Mite in the first step; in the second, blocked with no blocker left, trample: 1 to bob
            bob.life shouldBe 19
        }
    }

    /** An attacker returned to its owner's hand between the steps deals no damage in the second step. */
    // CR 506.4, 510.4
    @Test
    fun aCreatureRemovedFromCombatBetweenTheStepsDealsNoDamage() = scenario {
        alice.battlefield("Enamel Phalanxer", "Tinkered Mite", "Island", "Island")
        alice.hand("Click Back")
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks("Enamel Phalanxer", "Tinkered Mite")
        toFirstStrikeStep()
        alice.casts("Click Back", target = "Tinkered Mite")
        allPass()
        if (pending is Decision.ChooseObjects) alice.chooses()
        advanceTo(TurnStep.EndOfCombat)
        expect { bob.life shouldBe 17 }
    }
}
