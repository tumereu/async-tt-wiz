package arcanum.engine.review

import arcanum.api.Affected
import arcanum.api.Keyword
import arcanum.api.Mechanic
import arcanum.api.Objects
import arcanum.api.TurnStep
import arcanum.api.Zone
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.scenario.Scenario
import arcanum.engine.scenario.scenario
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertTrue

/** Review 1: effects that follow objects (CR 400.7, 607, 610.3), copies of copies (CR 707.3). */
class Review1LinkedTest {
    private fun Scenario.settleStack(vararg names: String) {
        var guard = 30
        while ((state.stack.isNotEmpty() || pending != null) && guard-- > 0) {
            when (val d = pending) {
                is Decision.Targets -> answer(Answer.Targets(names.map { target(it) }.filter { it in d.options }.take(d.max ?: 1)
                    .ifEmpty { d.options.take(d.min.coerceAtLeast(1)) }))
                is Decision.May -> answer(Answer.YesNo(true))
                is Decision.OrderTriggers -> answer(Answer.Order(d.triggers.indices.toList()))
                is Decision.ChooseObjects -> answer(Answer.Objects(d.options.take(d.min.coerceAtLeast(1))))
                null -> allPass()
                else -> return
            }
        }
    }

    /** A delayed "exile those creatures" loses track of one that was flickered in between: it's a new object. */
    // CR 400.7, 603.7c
    @Test
    fun delayedExileLosesAFlickeredCreature() = scenario {
        alice.battlefield("Plains", "Plains", "Plains", "Plains")
        alice.graveyard("Thicketwood Elves")
        alice.hand("Muster the Forebears", "Fleeting Recall")
        alice.library("Island", "Island")
        bob.library("Island", "Island")
        alice.casts("Muster the Forebears", x = 1, paying = listOf("Plains", "Plains#1", "Plains#2"))
        settleStack()
        alice.casts("Fleeting Recall", target = "Thicketwood Elves", paying = listOf("Plains#3"))
        settleStack()
        advanceTo(TurnStep.Upkeep, turn = 2)
        advanceTo(TurnStep.Upkeep, turn = 3)
        // Muster's exile and Fleeting Recall's rebound: rebound declined
        var guard = 20
        while ((state.stack.isNotEmpty() || pending != null) && guard-- > 0) when (val d = pending) {
            is Decision.OrderTriggers -> answer(Answer.Order(d.triggers.indices.toList()))
            is Decision.May -> answer(Answer.YesNo(false))
            null -> allPass()
            else -> kotlin.test.fail("Unexpected $d")
        }
        expect { alice.battlefield has "Thicketwood Elves" }
    }

    /** A copy of a copy copies the copy effect: the second artifact becomes the creature too. */
    // CR 707.3, 613.2
    @Test
    fun copyOfACopy() = scenario {
        alice.battlefield("Gearwing Glider", "Sun Band", "Verdant Bauble")
        effect(alice, Mechanic.CopyOf(Objects.Bound("g")), on = listOf("Sun Band"), names = mapOf("g" to "Gearwing Glider"))
        effect(alice, Mechanic.CopyOf(Objects.Bound("s")), on = listOf("Verdant Bauble"), names = mapOf("s" to "Sun Band"))
        expect {
            card("Sun Band").view.characteristics!!.name shouldBe "Gearwing Glider"
            card("Verdant Bauble").view.characteristics!!.name shouldBe "Gearwing Glider"
            "Verdant Bauble" hasKeyword Keyword.Flying
            "Verdant Bauble".toughness shouldBe 2
        }
    }

    /** Hideaway: the face-down exiled card goes to its owner's hand when the creature leaves. */
    // CR 702.75a, 607.2a
    @Test
    fun hideawayCardReturnsWhenItLeaves() = scenario {
        alice.battlefield("Island", "Island")
        alice.hand("Seer of Morrows")
        alice.library("Doomfire", "Island", "Island", "Island", "Mountain")
        bob.battlefield("Mountain")
        bob.hand("Storm Spark")
        alice.casts("Seer of Morrows")
        allPass()
        var guard = 10
        while (guard-- > 0 && (pending != null || state.stack.isNotEmpty())) {
            when (pending) {
                is Decision.ChooseObjects -> answer(Answer.Objects(listOf(card("Doomfire"))))
                null -> allPass()
                else -> settleStack()
            }
        }
        expect { alice.exile has "Doomfire" }
        alice.passes()
        bob.casts("Storm Spark", target = "Seer of Morrows")
        settleStack()
        expect { alice.hand has "Doomfire" }
    }

    /** Endless Return: a creature put into its owner's graveyard from the battlefield returns to the hand. */
    // CR 603.10a, 400.7
    @Test
    fun endlessReturnBringsItBack() = scenario {
        alice.battlefield("Endless Return", "Thicketwood Elves")
        bob.battlefield("Mountain")
        bob.hand("Storm Spark")
        alice.passes()
        bob.casts("Storm Spark", target = "Thicketwood Elves")
        settleStack()
        expect { alice.hand has "Thicketwood Elves" }
    }

    /** ...but not a card that has left the graveyard by then. */
    // CR 400.7, 608.2b
    @Test
    fun endlessReturnLosesACardThatMoved() = scenario {
        alice.battlefield("Endless Return", "Thicketwood Elves")
        bob.battlefield("Mountain")
        bob.hand("Storm Spark")
        alice.passes()
        bob.casts("Storm Spark", target = "Thicketwood Elves")
        allPass()
        expect { stack.size shouldBe 1 }
        move("Thicketwood Elves", Zone.Exile)
        settleStack()
        expect {
            alice.exile has "Thicketwood Elves"
            alice.hand lacks "Thicketwood Elves"
        }
    }

    /** "Exile … until this leaves": when it leaves, the exiled card returns as a new object under its owner's control. */
    // CR 610.3a
    @Test
    fun exiledUntilLeavesReturnsUnderItsOwner() = scenario {
        alice.battlefield("Plains")
        alice.hand("Pocket Rift")
        bob.battlefield("Elvish Seer", "Mountain", "Forest")
        bob.hand("Old Enmity")
        alice.casts("Pocket Rift")
        settleStack("Elvish Seer")
        expect { bob.exile has "Elvish Seer" }
        alice.passes()
        bob.casts("Old Enmity", target = "Pocket Rift")
        settleStack()
        expect {
            bob.battlefield has "Elvish Seer"
            "Elvish Seer".controller shouldBe bob.id
        }
    }

    /** "Can't block" from threshold: Rancid Imp with seven cards in the graveyard can't block. */
    // CR 509.1b
    @Test
    fun thresholdCantBlock() = scenario {
        alice.battlefield("Thicketwood Elves")
        bob.battlefield("Rancid Imp", "Wall of Portents")
        bob.graveyard("Island", "Island", "Island", "Island", "Island", "Island", "Island")
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks("Thicketwood Elves")
        allPass()
        kotlin.test.assertFailsWith<IllegalArgumentException> { bob.blocks("Rancid Imp" to "Thicketwood Elves") }
        expect { "Rancid Imp".power shouldBe 2 }
    }
}
