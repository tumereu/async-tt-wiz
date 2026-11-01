package arcanum.engine.review

import arcanum.api.CounterKind
import arcanum.api.Mechanic
import arcanum.api.TurnStep
import arcanum.api.Value
import arcanum.api.Zone
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.scenario.Scenario
import arcanum.engine.scenario.scenario
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Review 1: zone changes, new objects and last known information (CR 400.7, 603.10, 608.2h, 610.3). */
class Review1ZonesTest {
    /** Answers targets with [name] and "may" questions with yes until nothing is asked. */
    private fun Scenario.answerAll(vararg names: String) {
        var guard = 10
        while (pending != null && guard-- > 0) when (val d = pending) {
            is Decision.Targets -> answer(Answer.Targets(names.map { target(it) }.filter { it in d.options }.take(d.max ?: 1)
                .ifEmpty { d.options.take(1) }))
            is Decision.May -> answer(Answer.YesNo(true))
            else -> return
        }
    }

    /** Settles the Stack: everyone passes until it is empty, answering targets with [names]. */
    private fun Scenario.resolveAll(vararg names: String) {
        var guard = 20
        while ((state.stack.isNotEmpty() || pending != null) && guard-- > 0) {
            answerAll(*names)
            if (pending == null) allPass()
        }
    }

    /** A dies trigger uses the creature's last known power: Velo with two +1/+1 counters deals 3. */
    // CR 603.10a, 608.2h
    @Test
    fun diesTriggerUsesLastKnownPower() = scenario {
        alice.battlefield("Velo, Master of Ceremonies", counters = listOf(CounterKind.PowerToughness(1, 1) to 2))
        bob.battlefield("Mountain")
        bob.hand("Storm Spark")
        alice.passes()
        bob.casts("Storm Spark", target = "Velo, Master of Ceremonies")
        resolveAll("bob")
        expect { bob.life shouldBe 17 }
    }

    /** "X is this creature's power" read as it last existed, with an until-end-of-turn pump. */
    // CR 608.2h
    @Test
    fun diesTriggerCountsAPumpedPower() = scenario {
        alice.battlefield("Burrowed Shambler")
        effect(alice, Mechanic.ModifyPowerToughness(Value.Fixed(2), Value.Fixed(0)), on = listOf("Burrowed Shambler"))
        bob.battlefield("Mountain")
        bob.hand("Storm Spark")
        alice.passes()
        bob.casts("Storm Spark", target = "Burrowed Shambler")
        resolveAll()
        expect { alice.battlefield.names.count { it == "Squirrel Token" } shouldBe 3 }
    }

    /** "Exile … until this leaves the battlefield": if the source has left before it resolves, nothing is exiled. */
    // CR 610.3c
    @Test
    fun exileUntilLeavesDoesNothingIfTheSourceIsGone() = scenario {
        alice.battlefield("Plains")
        alice.hand("Pocket Rift")
        bob.battlefield("Mountain", "Forest", "Elvish Seer")
        bob.hand("Old Enmity")
        alice.casts("Pocket Rift")
        allPass()
        answerAll("Elvish Seer")
        expect { stack.size shouldBe 1 }
        alice.passes()
        bob.casts("Old Enmity", target = "Pocket Rift")
        resolveAll()
        expect {
            bob.battlefield has "Elvish Seer"
            alice.graveyard has "Pocket Rift"
        }
    }

    /** The classic: if the creature leaves before its "exile a card" resolves, the leaves trigger resolves first and
     * returns nothing; the card exiled later stays exiled. */
    // CR 603.10a, 607.2a
    @Test
    fun leavesTriggerBeforeEntersTriggerExilesForever() = scenario {
        alice.battlefield("Swamp", "Swamp")
        alice.hand("Hypnotic Fiend")
        bob.battlefield("Mountain")
        bob.hand("Storm Spark", "Denial")
        alice.casts("Hypnotic Fiend")
        allPass()
        answerAll("bob")
        alice.passes()
        bob.casts("Storm Spark", target = "Hypnotic Fiend")
        // Storm Spark resolves, the Fiend dies, its leaves trigger resolves first
        repeat(6) {
            if (pending is Decision.ChooseObjects) answer(Answer.Objects(listOf(card("Denial"))))
            else if (pending == null && state.stack.isNotEmpty()) allPass()
            answerAll("bob")
        }
        expect {
            bob.exile has "Denial"
            bob.hand lacks "Denial"
        }
    }

    /** A creature flickered in response is a new object: the spell targeting it doesn't resolve. */
    // CR 400.7, 608.2b
    @Test
    fun flickeredTargetIsANewObject() = scenario {
        alice.battlefield("Mountain")
        alice.hand("Storm Spark")
        bob.battlefield("Plains", "Thicketwood Elves")
        bob.hand("Fleeting Recall")
        alice.casts("Storm Spark", target = "Thicketwood Elves")
        alice.passes()
        bob.casts("Fleeting Recall", target = "Thicketwood Elves")
        resolveAll()
        expect {
            bob.battlefield has "Thicketwood Elves"
            alice.graveyard has "Storm Spark"
            bob.exile has "Fleeting Recall"
        }
    }

    /** A flickered creature comes back without the pump it had and summoning sick. */
    // CR 400.7, 302.6
    @Test
    fun flickeredCreatureLosesItsPump() = scenario {
        alice.battlefield("Plains", "Thicketwood Elves")
        alice.hand("Fleeting Recall")
        effect(alice, Mechanic.ModifyPowerToughness(Value.Fixed(2), Value.Fixed(2)), on = listOf("Thicketwood Elves"))
        alice.casts("Fleeting Recall", target = "Thicketwood Elves")
        resolveAll()
        expect {
            "Thicketwood Elves".power shouldBe 1
            "Thicketwood Elves".view.summoningSick shouldBe true
        }
    }

    /** Persist returns it once with a -1/-1 counter; dying again with the counter, it stays in the graveyard. */
    // CR 702.79a, 400.7
    @Test
    fun persistOnlyOnce() = scenario {
        alice.battlefield("Festering Gremlin")
        bob.battlefield("Mountain", "Mountain")
        bob.hand("Storm Spark", "Magma Needle")
        alice.passes()
        bob.casts("Storm Spark", target = "Festering Gremlin", paying = listOf("Mountain"))
        resolveAll()
        expect { "Festering Gremlin".counters(CounterKind.PowerToughness(-1, -1)) shouldBe 1 }
        alice.passes()
        bob.casts("Magma Needle", target = "Festering Gremlin", paying = listOf("Mountain#1"))
        resolveAll()
        expect { alice.graveyard has "Festering Gremlin" }
    }

    /** A token that died triggers "whenever a creature dies" and then ceases to exist. */
    // CR 111.7, 704.5d
    @Test
    fun deadTokenTriggersAndVanishes() = scenario {
        alice.battlefield("Fertile Passing", "Plains", "Plains", "Plains")
        alice.hand("Lingering Shades")
        alice.library("Island", "Island")
        bob.battlefield("Mountain")
        bob.hand("Storm Spark")
        alice.casts("Lingering Shades")
        resolveAll()
        alice.passes()
        bob.casts("Storm Spark", target = "Spirit Token")
        resolveAll()
        expect {
            alice.battlefield.names.count { it == "Spirit Token" } shouldBe 1
            alice.graveyard lacks "Spirit Token"
            alice.hand has "Island"
        }
    }

    /** Brisa exiles a permanent and returns it under its owner's control at the next end step. */
    // CR 603.7, 610.3
    @Test
    fun brisaReturnsTheCardAtTheEndStep() = scenario {
        alice.battlefield("Brisa, Bounding Shepherd")
        alice.library("Island", "Island")
        bob.library("Island", "Island")
        bob.battlefield("Thicketwood Elves")
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks("Brisa, Bounding Shepherd")
        answerAll("Thicketwood Elves")
        allPass()
        expect { bob.exile has "Thicketwood Elves" }
        advanceTo(TurnStep.Upkeep, turn = 2)
        expect {
            bob.battlefield has "Thicketwood Elves"
            "Brisa, Bounding Shepherd".counters(CounterKind.PowerToughness(1, 1)) shouldBe 0
        }
    }

    /** Brisa exiling its controller's own creature: it returns under alice's control, so Brisa gets a counter. */
    // CR 603.7, 607.2a
    @Test
    fun brisaGetsACounterForItsControllersCard() = scenario {
        alice.battlefield("Brisa, Bounding Shepherd", "Thicketwood Elves")
        alice.library("Island", "Island")
        bob.library("Island", "Island")
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks("Brisa, Bounding Shepherd")
        answerAll("Thicketwood Elves")
        allPass()
        advanceTo(TurnStep.Upkeep, turn = 2)
        expect {
            alice.battlefield has "Thicketwood Elves"
            "Brisa, Bounding Shepherd".counters(CounterKind.PowerToughness(1, 1)) shouldBe 1
        }
    }

    /** Cloudhaven Phantasm leaving gives the exiled card's owner an X/X Illusion, X its mana value. */
    // CR 607.2a, 608.2h
    @Test
    fun linkedLeavesTriggerMakesAnIllusion() = scenario {
        alice.battlefield("Plains", "Plains", "Plains")
        alice.hand("Cloudhaven Phantasm")
        bob.battlefield("Canopy Visionary", "Mountain")
        bob.hand("Storm Spark")
        alice.casts("Cloudhaven Phantasm")
        resolveAll("Canopy Visionary")
        expect { bob.exile has "Canopy Visionary" }
        alice.passes()
        bob.casts("Storm Spark", target = "Cloudhaven Phantasm")
        resolveAll()
        expect {
            bob.battlefield has "Illusion Token"
            "Illusion Token".power shouldBe 3
        }
    }

    /** A reflexive trigger: Undying Valet dies, its controller exiles it, and then returns a creature card. */
    // CR 603.12
    @Test
    fun reflexiveTriggerAfterADeath() = scenario {
        alice.battlefield("Undying Valet")
        alice.graveyard("Thicketwood Elves")
        bob.battlefield("Mountain")
        bob.hand("Storm Spark")
        alice.passes()
        bob.casts("Storm Spark", target = "Undying Valet")
        resolveAll("Thicketwood Elves")
        expect {
            alice.exile has "Undying Valet"
            alice.hand has "Thicketwood Elves"
        }
    }

    /** An Aura goes to the graveyard when the creature it enchants leaves. */
    // CR 704.5m
    @Test
    fun auraFallsOffWhenTheCreatureLeaves() = scenario {
        alice.battlefield("Plains", "Thicketwood Elves")
        alice.hand("Rapid Retrofit")
        bob.battlefield("Island", "Island")
        bob.hand("Rescind")
        alice.casts("Rapid Retrofit", target = "Thicketwood Elves")
        resolveAll()
        alice.passes()
        bob.casts("Rescind", target = "Thicketwood Elves", x = 1)
        resolveAll()
        expect {
            alice.graveyard has "Rapid Retrofit"
            alice.hand has "Thicketwood Elves"
        }
    }

    /** Equipment stays on the battlefield, unattached, when the creature dies; Bonevise's dies trigger draws two. */
    // CR 704.5n, 603.10a
    @Test
    fun equipmentStaysAndItsDiesTriggerWorks() = scenario {
        alice.battlefield("Bonevise", "Elvish Hierophant")
        attach("Bonevise", "Elvish Hierophant")
        alice.library("Island", "Island", "Island")
        bob.battlefield("Mountain")
        bob.hand("Magma Needle")
        alice.passes()
        bob.casts("Magma Needle", target = "Elvish Hierophant")
        resolveAll()
        expect {
            alice.battlefield has "Bonevise"
            alice.hand.size shouldBe 2
        }
        assertEquals(null, state.objOrThrow(card("Bonevise")).attachedTo)
    }

    /** A card put into a graveyard from anywhere triggers Mirebloat; a land milled counts. */
    // CR 603.2
    @Test
    fun landMilledTriggersMirebloat() = scenario {
        alice.battlefield("Mirebloat, the Undermuck", "Island")
        alice.hand("Thought Siphon")
        alice.library("Island", "Mountain", "Plains")
        alice.casts("Thought Siphon", target = "alice")
        resolveAll()
        expect { "Mirebloat, the Undermuck".counters(CounterKind.PowerToughness(1, 1)) shouldBe 2 }
    }
}
