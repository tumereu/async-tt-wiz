package arcanum.engine.review

import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.scenario.Scenario
import arcanum.engine.scenario.scenario
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Review 3: copies of spells (CR 707.10): what a copy copies (modes, targets, X, costs) and what it doesn't (mana
 * spent, being cast, where it was cast from); copies and cast triggers.
 */
class Review3CopiesTest {
    /** Resolves the Stack, declining every "you may" (new targets, more copies). */
    private fun Scenario.resolveDeclining() {
        var guard = 30
        while ((state.stack.isNotEmpty() || pending != null) && guard-- > 0) {
            when (pending) {
                is Decision.May -> no()
                null -> resolve()
                else -> return
            }
        }
    }

    /** A copy of a converge spell: mana isn't an object, so no colours were spent to cast the copy (CR 707.10, example
     * of Dawnglow Infusion). The copy of Spectral Finale aimed at a mana value 1 permanent exiles nothing; the original
     * (three colours) exiles the mana value 3 one. */
    // CR 707.10, 702.119a
    @Ignore("review3 #1: a copy of a spell resolves as its original card (cast facts, self-references)")
    @Test
    fun aCopyOfAConvergeSpellSpentNoColours() = scenario {
        alice.battlefield("Plains", "Island", "Mountain", "Mountain", "Mountain", "Mountain")
        alice.hand("Spectral Finale", "Flash of Mirroring")
        bob.battlefield("Elvish Hierophant", "Elvish Seer")
        alice.casts("Spectral Finale", x = 2, target = "Elvish Hierophant", paying = listOf("Plains", "Island", "Mountain"))
        alice.casts("Flash of Mirroring", target = "Spectral Finale", paying = listOf("Mountain#1", "Mountain#2", "Mountain#3"))
        allPass()
        yes()
        alice.answers(Answer.Targets(listOf(target("Elvish Seer"))))
        allPass()
        expect { bob.battlefield has "Elvish Seer" }
        allPass()
        expect { bob.exile has "Elvish Hierophant" }
    }

    /** A copy isn't cast, so it wasn't "cast from a graveyard": the copy of a flashed-back Warden's Reclamation returns
     * its card but offers no further copy; the original does. */
    // CR 707.10, 702.34a
    @Ignore("review3 #1: a copy of a spell resolves as its original card (cast facts, self-references)")
    @Test
    fun aCopyOfAFlashedBackSpellWasntCastFromAGraveyard() = scenario {
        alice.graveyard("Warden's Reclamation", "Tinkered Mite", "Gearwing Glider")
        alice.hand("Flash of Mirroring")
        alice.castsWithoutCosts("Warden's Reclamation", target = "Tinkered Mite")
        alice.castsWithoutCosts("Flash of Mirroring", target = "Warden's Reclamation")
        resolve()
        yes()
        alice.answers(Answer.Targets(listOf(target("Gearwing Glider"))))
        resolve()
        expect { alice.battlefield has "Gearwing Glider" }
        assertTrue(pending !is Decision.May, "the copy offers another copy: $pending")
        resolve()
        expect { alice.battlefield has "Tinkered Mite" }
        assertTrue(pending is Decision.May, "the flashed-back original may copy itself")
        no()
    }

    /** Mounting Retribution cast with flashback copies twice; a copy of it wasn't cast from a graveyard and copies once.
     * Storm Spark: the original, one copy from the copied Retribution, two from the original: 12 damage. */
    // CR 707.10, 702.34a
    @Ignore("review3 #1: a copy of a spell resolves as its original card (cast facts, self-references)")
    @Test
    fun aCopyOfAFlashedBackMountingRetributionCopiesOnce() = scenario {
        alice.hand("Storm Spark", "Flash of Mirroring")
        alice.graveyard("Mounting Retribution")
        alice.castsWithoutCosts("Storm Spark", target = "bob")
        alice.castsWithoutCosts("Mounting Retribution", target = "Storm Spark")
        alice.castsWithoutCosts("Flash of Mirroring", target = "Mounting Retribution")
        resolveDeclining()
        expect { bob.life shouldBe 8 }
    }

    /** "Exile Muster the Forebears" in a copy means the copy (CR 707.10: the copy is a spell of its own; a copy that
     * leaves the Stack ceases to exist): the original card stays on the Stack and resolves later. */
    // CR 707.10, 707.10a, 608.2n
    @Ignore("review3 #1: a copy of a spell resolves as its original card (cast facts, self-references)")
    @Test
    fun aCopysSelfReferenceIsntTheOriginalCard() = scenario {
        alice.hand("Muster the Forebears", "Flash of Mirroring")
        alice.graveyard("Elvish Seer")
        alice.castsWithoutCosts("Muster the Forebears", x = 1)
        alice.castsWithoutCosts("Flash of Mirroring", target = "Muster the Forebears")
        resolve()
        if (pending is Decision.May) no()
        resolve()
        expect {
            alice.battlefield has "Elvish Seer"
            stack.names shouldBe listOf("Muster the Forebears")
        }
        resolve()
        expect { alice.exile has "Muster the Forebears" }
    }

    /** Copies aren't cast: Novice Flamecaller makes a token for Storm Spark and Flash of Mirroring, none for the copy. */
    // CR 707.10, 601.2i
    @Test
    fun aCopyTriggersNoCastTrigger() = scenario {
        alice.battlefield("Novice Flamecaller", "Mountain", "Mountain", "Mountain", "Mountain")
        alice.hand("Storm Spark", "Flash of Mirroring")
        alice.casts("Storm Spark", target = "bob")
        alice.casts("Flash of Mirroring", target = "Storm Spark")
        repeat(6) { if (pending is Decision.May) no() else if (state.stack.isNotEmpty()) allPass() }
        expect {
            bob.life shouldBe 14
            alice.battlefield.names.count { it == "Elemental Token" } shouldBe 2
        }
    }

    /** Encore Round: each instant or sorcery cast is copied once; the copy isn't cast, so it isn't copied again. */
    // CR 707.10, 603.2
    @Test
    fun encoreRoundCopiesOnce() = scenario {
        alice.battlefield("Mountain", "Mountain", "Mountain", "Mountain")
        alice.hand("Encore Round", "Storm Spark")
        alice.casts("Encore Round")
        allPass()
        alice.casts("Storm Spark", target = "bob")
        repeat(6) { if (pending is Decision.May) no() else if (state.stack.isNotEmpty()) allPass() }
        expect {
            bob.life shouldBe 14
            stack.isEmpty()
        }
    }

    /** A copy of a modal spell has the original's mode (CR 707.10): Grindstone Rasp's damage mode, copied with a new
     * target, kills both creatures. */
    // CR 707.10, 700.2
    @Test
    fun aCopyKeepsTheMode() = scenario {
        alice.hand("Grindstone Rasp", "Flash of Mirroring")
        bob.battlefield("Elvish Hierophant", "Canopy Visionary")
        alice.castsWithoutCosts("Grindstone Rasp", target = "Elvish Hierophant", modes = listOf(0))
        alice.castsWithoutCosts("Flash of Mirroring", target = "Grindstone Rasp")
        resolve()
        yes()
        alice.answers(Answer.Targets(listOf(target("Canopy Visionary"))))
        resolveDeclining()
        expect {
            bob.graveyard has "Elvish Hierophant"
            bob.graveyard has "Canopy Visionary"
        }
    }

    /** The copy is its own spell: countering the original doesn't touch the copy already on the Stack. */
    // CR 707.10
    @Test
    fun counteringTheOriginalLeavesTheCopy() = scenario {
        alice.hand("Storm Spark", "Flash of Mirroring")
        bob.hand("Denial")
        alice.castsWithoutCosts("Storm Spark", target = "bob")
        alice.castsWithoutCosts("Flash of Mirroring", target = "Storm Spark")
        resolve()
        no()
        bob.castsWithoutCosts("Denial", target = "Storm Spark")
        resolveDeclining()
        expect {
            bob.life shouldBe 17
            alice.graveyard has "Storm Spark"
        }
    }
}
